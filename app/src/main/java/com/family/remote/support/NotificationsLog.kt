package com.family.remote.support

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ═══════════════════════════════════════════════════════════════════════
// M8 (v0.34.0) — the LOCAL notifications events log (G2 of
// FAMILY_GUEST_AUTHORIZATION.md): the M8 decision turned the notifications
// surface's in-memory list into a persisted history of REAL events.
//
// Persistence is one JSON file in context.filesDir — NO Firestore, no new
// listeners, ZERO extra network/cloud reads (quota discipline; the Phase 20
// free-tier incident is the standing counter-example). The honesty rule
// (NAVIGATION_MAP §2) still governs WHAT is recorded — only transitions the
// app ALREADY observes:
//   • session_asked    a REQUESTED ask appears addressed to this account
//   • session_started  REQUESTED → ACTIVE involving this account
//   • session_ended    an ACTIVE session left the open session watch
//   • sharing_started / sharing_stopped  the location FGS actually began /
//                      actually stopped (never any coordinates — §9 of
//                      LIVE_LOCATION_ARCHITECTURE.md, lock-screen safe)
// An event carries at most a counterpart email as payload, never coordinates
// or other sensitive values. The log is capped at the most recent 100 and
// cleared at sign-out (counterpart emails must not survive the account).
// File IO is fire-and-forget on a private IO scope: a failed persist never
// crashes or blocks the UI — the in-memory list stays authoritative.
// ═══════════════════════════════════════════════════════════════════════

/** One row of the local events log (top-level like PresenceView). */
data class NotifEvent(
    val id: Long,
    /** One of: session_asked | session_started | session_ended |
     * sharing_started | sharing_stopped. */
    val type: String,
    /** System-epoch millis (System.currentTimeMillis) at record time. */
    val at: Long,
    /** Counterpart email (or null) — never coordinates or payloads. */
    val arg: String? = null,
    /** Second label (or null). Currently unused; kept for future events. */
    val arg2: String? = null
)

object NotificationsLog {

    private const val FILE_NAME = "notifications_log.json"

    /** Cap of the persisted + displayed history (M8 decision). */
    private const val MAX_EVENTS = 100

    private val _events = MutableStateFlow<List<NotifEvent>>(emptyList())

    /** Newest-first. The notifications screen collects this — nothing else. */
    val events: StateFlow<List<NotifEvent>> = _events.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Chained write job: each persist JOINS the previous one, so writes land
     * strictly in call order even though Dispatchers.IO is a pool (the last
     * write always carries the newest snapshot — no out-of-order clobber).
     */
    private var persistJob: Job? = null

    private var nextId = 1L

    /**
     * Read the file once at startup (plain call from MainActivity.onCreate,
     * BEFORE composition — the "Recent events" section renders from the very
     * first frame). Defensive: a missing or corrupt file starts empty.
     */
    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val text = try {
            if (!file.exists()) return
            file.readText()
        } catch (_: Exception) {
            return // unreadable — start empty
        }
        val loaded = mutableListOf<NotifEvent>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = o.optString("type")
                if (type.isBlank()) continue
                loaded += NotifEvent(
                    id = o.optLong("id", 0L),
                    type = type,
                    at = o.optLong("at", 0L),
                    arg = o.optStringOrNull("arg"),
                    arg2 = o.optStringOrNull("arg2")
                )
            }
        } catch (_: Exception) {
            // Corrupt file — start empty, never crash.
            loaded.clear()
        }
        // Written newest-first, but never trust the file's order.
        loaded.sortByDescending { it.id }
        _events.value = loaded
        nextId = (loaded.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    /**
     * Prepend one real event, cap at [MAX_EVENTS], persist asynchronously.
     * Fire-and-forget: called from the main thread (UI effects and service
     * lifecycle callbacks); the file write hops to [ioScope].
     */
    fun record(context: Context, type: String, arg: String? = null, arg2: String? = null) {
        append(context, listOf(newEvent(type, arg, arg2)))
    }

    /**
     * M8 (v0.34.0) — diff helper for the shell's session watch: compares the
     * previous open-session snapshot with the current one and records the
     * three transitions the app ALREADY observes (no new listener, no extra
     * read):
     *   • a REQUESTED ask TO this account → "session_asked" (helperEmail)
     *   • REQUESTED → ACTIVE involving me  → "session_started" (counterpart)
     *   • ACTIVE → left the open watch     → "session_ended" (counterpart)
     * The capped watch only ever returns this account's sessions, so
     * "involving me" is structural; the explicit uid checks keep that
     * guarantee local and honest.
     *
     * [prev] is the caller's remembered map (session id → last observed
     * view) and is REWRITTEN here to the current snapshot, which keeps the
     * call site a single line. A session that vanishes while ACTIVE can only
     * have ended (the state machine has no other ACTIVE exit), but the
     * terminal doc has left the watch — endedBy is NOT observable without an
     * extra Firestore read, so it is deliberately not recorded rather than
     * guessed. A REQUESTED ask that vanishes (denied / cancelled / expired)
     * is not an M8 event and is simply dropped from the map.
     */
    fun recordSessionTransitions(
        context: Context,
        meUid: String?,
        prev: MutableMap<String, SessionView>,
        sessions: List<SessionView>
    ) {
        if (meUid == null) {
            // Signed out (or loading) — never record from a stale snapshot.
            prev.clear()
            return
        }
        val currentById = sessions.associateBy { it.id }
        val batch = mutableListOf<NotifEvent>()
        for (s in sessions) {
            val before = prev[s.id]
            if (before == null) {
                if (s.status == "REQUESTED" && s.recipientUid == meUid) {
                    batch += newEvent("session_asked", s.helperEmail)
                }
            } else if (before.status == "REQUESTED" && s.status == "ACTIVE" &&
                (s.helperUid == meUid || s.recipientUid == meUid)
            ) {
                val counterpart = if (s.recipientUid == meUid) s.helperEmail else s.recipientEmail
                batch += newEvent("session_started", counterpart)
            }
        }
        for ((id, before) in prev) {
            if (before.status == "ACTIVE" && currentById[id] == null) {
                val counterpart =
                    if (before.recipientUid == meUid) before.helperEmail else before.recipientEmail
                batch += newEvent("session_ended", counterpart, null)
            }
        }
        prev.clear()
        sessions.forEach { prev[it.id] = it }
        if (batch.isNotEmpty()) append(context, batch)
    }

    /** Sign-out hygiene: events hold counterpart emails — wipe memory + file. */
    fun clear(context: Context) {
        _events.value = emptyList()
        val appContext = context.applicationContext
        val previous = persistJob
        persistJob = ioScope.launch {
            previous?.join()
            try {
                File(appContext.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {
                // best effort — a failed delete only leaves stale rows on disk
            }
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private fun newEvent(type: String, arg: String?, arg2: String? = null): NotifEvent =
        NotifEvent(id = nextId++, type = type, at = System.currentTimeMillis(), arg = arg, arg2 = arg2)

    private fun append(context: Context, added: List<NotifEvent>) {
        _events.value = (added + _events.value).take(MAX_EVENTS)
        persist(context)
    }

    private fun persist(context: Context) {
        val appContext = context.applicationContext
        val snapshot = _events.value
        val previous = persistJob
        persistJob = ioScope.launch {
            previous?.join()
            try {
                val arr = JSONArray()
                for (e in snapshot) {
                    val o = JSONObject()
                        .put("id", e.id)
                        .put("type", e.type)
                        .put("at", e.at)
                    e.arg?.let { o.put("arg", it) }
                    e.arg2?.let { o.put("arg2", it) }
                    arr.put(o)
                }
                File(appContext.filesDir, FILE_NAME).writeText(arr.toString())
            } catch (_: Exception) {
                // A failed persist must NEVER crash or block the UI — the
                // in-memory list stays authoritative and the next record
                // rewrites the full state anyway.
            }
        }
    }

    /** null only when the key is absent or explicitly JSON null. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
