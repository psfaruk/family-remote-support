package com.family.remote.support

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════
// v0.45.0 (audit P1) — the app→Worker push trigger.
//
// The core P1 bug: with every server sweep tier parked (quota directive,
// wrangler.toml crons = []), an ask whose recipient app process is dead
// produced NO notification at all — the Firestore listener needs a live
// process, and the 2-minute ask died unseen. The fix is event-driven, not
// polling: the moment an ask (or an answer) is WRITTEN by this app, the
// app itself tells the Cloudflare Worker, which verifies a fresh Firebase
// ID token, re-reads the session server-side, and sends ONE high-priority
// FCM data message to the counterpart's registered tokens. Cost: ~2–3
// Firestore reads per ask (vs ~69,000/day for the old polling tiers).
//
// Auth posture: the Authorization header carries the user's short-lived
// Firebase ID token (NOT a shared secret baked into the APK — the M7
// rejection was about a permanent bearer token in the APK; an ID token is
// per-user, per-hour, and verifiable server-side). The Worker checks
// token.sub against the session's helperUid/recipientUid before sending.
//
// Config: the Worker URL lives in `config/notify` { url } — the same
// owner-controlled runtime-config pattern as config/turn. Missing doc =
// feature off, silently and safely (the old behavior). The URL is cached
// for 10 minutes (hit AND miss) so the one bounded read per 10 minutes
// is the whole cost while enabled.
//
// v0.47.0 (audit B3 — offline reliability): the fire-and-forget call was
// ONE attempt with every failure swallowed. A helper on a flaky network
// could write the ask and lose the push entirely — the recipient's dead
// app then never heard it and the ask died unseen (exactly what P1 was
// built to prevent). Now: the first attempt stays inline (fast path), but
// a TRANSIENT failure (offline, timeout, non-2xx) enqueues a
// network-constrained WorkManager retry — persisted across process death,
// exponential backoff, ≤3 attempts, well inside the ask's 4-minute TTL.
// A feature-OFF verdict (config/notify absent without a valid URL) never
// retries — the Owner has not set the Worker up (Settings shows that
// state honestly now, see [PushSetup]).
// ═══════════════════════════════════════════════════════════════════════

/** The honest dead-app-push posture, surfaced in Settings (audit BE5). */
enum class PushSetup { UNKNOWN, ON, OFF }

object SessionPush {

    private const val TAG = "SessionPush"

    /** Fire-and-forget scope: a push hiccup must never fail the session op. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val CONFIG_CACHE_MILLIS = 10 * 60_000L

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedUrlAt = 0L

    /** v0.47.0 (audit BE5) — the setup state the Settings row reads. */
    private val _setup = MutableStateFlow(PushSetup.UNKNOWN)

    /** ON = config/notify carries a valid https URL; OFF = doc absent/invalid. */
    val setup: StateFlow<PushSetup> = _setup.asStateFlow()

    /** v0.47.0 (audit B3) — the WorkManager enqueue needs an app context. */
    @Volatile
    private var appContext: Context? = null

    /** Called from App.onCreate — before any notify() can fire. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Best-effort notify — call AFTER the session write landed.
     * [event] is "asked" (helper, after requestSession) or "answered"
     * (either side, after grant/deny/cancel). Never throws.
     * v0.47.0 (audit B3): a transient failure now enqueues the retry
     * worker (bounded, network-constrained, persisted across death).
     */
    fun notify(sessionId: String, event: String) {
        scope.launch {
            val delivered = try {
                notifyBlocking(sessionId, event)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!delivered) enqueueRetry(sessionId, event)
        }
    }

    /**
     * @return true  — the Worker accepted the request (2xx).
     *         false — the feature is OFF (config/notify absent/invalid);
     *                 NOT retryable by design.
     * @throws Exception — a TRANSIENT failure (offline, timeout, non-2xx):
     *                 the caller (the retry worker) may try again.
     */
    internal suspend fun notifyBlocking(
        sessionId: String,
        event: String,
        forceConfigRefresh: Boolean = false
    ): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return true
        // Signed out mid-flight: not an error, and not deliverable — treat
        // as "done" so no pointless retry chain starts.
        val url = workerUrl(forceConfigRefresh)
        if (url == null) return false // feature not configured — no retry
        val idToken = user.getIdToken(false)
            .awaitWithTimeout("getting the ID token for push", 10_000L)
        val body = JSONObject()
            .put("sessionId", sessionId)
            .put("event", event)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                // v0.47.0 (audit B3) — a non-2xx is now a RETRYABLE failure:
                // throw so the worker re-attempts (Worker-side claim
                // idempotency makes a duplicate delivery harmless).
                throw java.io.IOException("worker /notify answered HTTP $code")
            }
            return true
        } finally {
            conn.disconnect()
        }
    }

    /**
     * v0.47.0 (audit B3) — the bounded retry: one unique work name per
     * (sessionId, event) so a re-notify REPLACES its pending retry (no
     * stacking), CONNECTED-constrained (the whole point is the network
     * coming back), exponential backoff from 10 s, ≤3 attempts — the last
     * attempt lands well inside the 4-minute ask TTL.
     */
    private fun enqueueRetry(sessionId: String, event: String) {
        val app = appContext ?: return
        val request = OneTimeWorkRequestBuilder<SessionPushWorker>()
            .setInputData(
                workDataOf(
                    SessionPushWorker.KEY_SESSION_ID to sessionId,
                    SessionPushWorker.KEY_EVENT to event
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10_000L, TimeUnit.MILLISECONDS)
            .build()
        try {
            WorkManager.getInstance(app).enqueueUniqueWork(
                "session-push-$sessionId-$event",
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (_: Exception) {
            // WorkManager unavailable (exotic OEM) — the inline attempt was
            // the whole effort; the alive-app listener path still covers
            // the visible cases. Honest absence, never a crash.
        }
    }

    /**
     * v0.47.0 (audit BE5) — Settings' honest state probe: one bounded read
     * of config/notify (respecting the 10-minute cache). ON/OFF is a FACT
     * about the Owner's setup, not a guess; UNKNOWN stays until the first
     * successful read (offline = can't tell yet).
     */
    suspend fun probeSetup(): PushSetup {
        workerUrl(forceConfigRefresh = true)
        return setup.value
    }

    /** Cached read of `config/notify` { url } — null when off/missing. */
    private suspend fun workerUrl(forceConfigRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceConfigRefresh) {
            val cached = cachedUrl
            if (cached != null && now - cachedUrlAt < CONFIG_CACHE_MILLIS) return cached
            // Negative results cache too: while the doc is missing this costs
            // ONE bounded read per 10 minutes per process, not one per ask.
            if (now - cachedUrlAt < CONFIG_CACHE_MILLIS) return null
        }
        return try {
            val snap = Fb.db.collection("config")
                .document("notify")
                .get()
                .awaitWithTimeout("reading the push worker config", 5_000L)
            val url = snap.getString("url")
                ?.trim()
                ?.takeIf { it.startsWith("https://") }
            cachedUrl = url
            cachedUrlAt = now
            _setup.value = if (url != null) PushSetup.ON else PushSetup.OFF
            url
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline / rules hiccup: treat as not-configured for 10 min.
            // The setup state stays UNKNOWN — an offline read is not a
            // verdict about the Owner's configuration (BE5 honesty).
            cachedUrl = null
            cachedUrlAt = now
            null
        }
    }
}

/**
 * v0.47.0 (audit B3) — the persisted retry for a transient /notify failure.
 * Runs even if the app process died in between (WorkManager restarts it),
 * stops after 3 attempts, and forces a config re-read on retries (the
 * original failure may have been the offline config read that poisoned
 * the 10-minute negative cache).
 */
class SessionPushWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val event = inputData.getString(KEY_EVENT) ?: return Result.failure()
        return try {
            if (notifyBlocking(sessionId, event, forceConfigRefresh = runAttemptCount > 0)) {
                Result.success()
            } else {
                // Feature OFF (config/notify absent/invalid): retrying cannot
                // help — fail fast, honestly.
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "frs.push.sessionId"
        const val KEY_EVENT = "frs.push.event"

        /** 1 inline attempt + up to 3 worker attempts, backoff permitting. */
        const val MAX_ATTEMPTS = 3
    }
}
