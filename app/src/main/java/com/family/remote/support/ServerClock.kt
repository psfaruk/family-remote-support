package com.family.remote.support

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * v0.45.0 (audit P7) — the server-time offset estimator.
 *
 * Every session deadline check used to compare Firestore SERVER timestamps
 * (respondDeadline, sessionEndsAt, requestedAt) against the PHONE's own
 * wall clock. A phone with "Automatic date & time" off — common on elderly
 * family devices — skews 5–10 minutes and then:
 *   • an ask shows "already expired" the moment it arrives, or
 *   • the ask's rules-checked WRITE itself fails (firestore.rules requires
 *     respondDeadline within (request.time, request.time + 5 m) of SERVER
 *     time, and a fast phone pushes the deadline outside that window).
 *
 * Fix: estimate `serverNow - localNow` once per process from any snapshot
 * the app ALREADY receives, and add it to every local "now" that is
 * compared against a server timestamp. The feeders are:
 *   • SessionNotifier — the ask doc's server-stamped createdAt vs the
 *     local clock at listener delivery;
 *   • PushTokenService — the FCM data message's server-stamped fields.
 *
 * v0.56.0 (worklog §103 — "সেশন আবার চালু করা যাচ্ছে না") — the feeders had
 * a bootstrap hole: at process start (before ANY snapshot arrived) the
 * offset is 0 = still the raw device clock, so the FIRST ask a skewed phone
 * sent could still fail its rules-checked deadline write, and the
 * stale-session cleanup (which compared server-written deadlines against
 * the device clock) could see a long-dead ask as "still open" forever.
 * Three permanent additions:
 *   • [attach] — restores the last known offset from local storage at app
 *     start (a phone's skew barely drifts over a day; far better than 0);
 *   • [syncServerClockBestEffort] — an EXPLICIT clock ping before every
 *     deadline-writing call (ask/grant): one rules-legal serverTimestamp
 *     write + one forced-server read on the user's own profile doc;
 *   • [syncFrom] persists every learned offset so the next process starts
 *     already corrected.
 */
object ServerClock {

    @Volatile
    private var offsetMillis: Long = 0L

    /** True once ANY feeder (or the explicit ping) corrected us this process. */
    @Volatile
    private var syncedThisProcess: Boolean = false

    @Volatile
    private var appContext: Context? = null

    /** Server-corrected "now" in System.currentTimeMillis() terms. */
    fun now(): Long = System.currentTimeMillis() + offsetMillis

    /** v0.56.0 — whether the offset is known-good for this process. */
    val hasSyncedThisProcess: Boolean
        get() = syncedThisProcess

    /**
     * v0.56.0 — call once from Application.onCreate(). Restores the persisted
     * offset when it is fresh (< [OFFSET_TTL_MILLIS]) and sane; the first
     * ask/grant still re-verifies it with an explicit ping.
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedOffset = prefs.getLong(KEY_OFFSET, Long.MIN_VALUE)
            val savedAt = prefs.getLong(KEY_SYNCED_AT, 0L)
            if (savedOffset != Long.MIN_VALUE &&
                kotlin.math.abs(savedOffset) <= MAX_ABS_OFFSET_MILLIS &&
                savedAt > 0L &&
                System.currentTimeMillis() - savedAt < OFFSET_TTL_MILLIS
            ) {
                offsetMillis = savedOffset
            }
        } catch (_: Exception) {
            // local storage unreadable — start at 0 like before, ping will fix
        }
    }

    /**
     * Fold one (serverTimestamp, localReceiveTime) observation into the
     * offset. Ignored when the delta is absurd (> ±1 day) or when it
     * agrees with the current estimate to within [TOLERANCE_MILLIS] (no
     * churn on every snapshot). Only moves when it genuinely learned
     * something new. v0.56.0: every accepted observation marks the process
     * as synced and is persisted for the next process start.
     */
    fun syncFrom(serverTimestamp: Timestamp?, localReceiveMillis: Long) {
        if (serverTimestamp == null) return
        val observed = serverTimestamp.toDate().time - localReceiveMillis
        if (kotlin.math.abs(observed) > MAX_ABS_OFFSET_MILLIS) return
        syncedThisProcess = true
        if (kotlin.math.abs(observed - offsetMillis) <= TOLERANCE_MILLIS) return
        offsetMillis = observed
        persist(observed)
    }

    private fun persist(offset: Long) {
        val ctx = appContext ?: return
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_OFFSET, offset)
                .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // best effort — the in-memory offset still works for this process
        }
    }

    private const val PREFS_NAME = "server_clock"
    private const val KEY_OFFSET = "offset_millis"
    private const val KEY_SYNCED_AT = "synced_at_local"

    private const val TOLERANCE_MILLIS = 2_000L

    /** A phone more than a day off has bigger problems than deadlines. */
    private const val MAX_ABS_OFFSET_MILLIS = 24 * 60 * 60 * 1000L

    /** A persisted offset older than a day is re-learned from scratch. */
    private const val OFFSET_TTL_MILLIS = 24 * 60 * 60 * 1000L
}

/**
 * v0.56.0 (§103) — the explicit clock ping. Before a call whose Firestore
 * write is rules-checked against SERVER time (the ask's respondDeadline, the
 * grant's sessionEndsAt), make sure [ServerClock.hasSyncedThisProcess]; when
 * no feeder has run yet, learn the offset directly:
 *
 *   1. merge-set `updatedAt = serverTimestamp()` on the caller's OWN profile
 *      doc (users/{uid} — a field the rules already allow the owner to write;
 *      creates the doc if it does not exist yet, all within hasOnly());
 *   2. read the doc back FORCING the server (Source.SERVER — never the local
 *      cache, whose updatedAt could predate this ping);
 *   3. fold the resolved server timestamp into [ServerClock.syncFrom].
 *
 * Two bounded round-trips, at most once per process, shared by every
 * concurrent caller through [clockSyncMutex]. Best effort by design: any
 * failure (offline, timeout, rules) leaves the offset as-is (persisted value
 * or 0) and the caller proceeds — the write may then fail honestly with a
 * localized error instead of us pretending to know the time.
 */
suspend fun syncServerClockBestEffort(uid: String) {
    if (uid.isBlank() || ServerClock.hasSyncedThisProcess) return
    clockSyncMutex.withLock {
        if (ServerClock.hasSyncedThisProcess) return
        try {
            val ref = Fb.db.collection("users").document(uid)
            ref.set(
                mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).awaitWithTimeout("learning the server clock (write)", 6_000L)
            val snap = ref.get(Source.SERVER)
                .awaitWithTimeout("learning the server clock (read)", 6_000L)
            ServerClock.syncFrom(
                snap.getTimestamp("updatedAt"),
                System.currentTimeMillis()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // best effort — offset stays whatever it currently is
        }
    }
}

private val clockSyncMutex = Mutex()
