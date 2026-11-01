package com.family.remote.support

import com.google.firebase.auth.FirebaseAuth
import java.io.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

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
// ═══════════════════════════════════════════════════════════════════════

object SessionPush {

    private const val TAG = "SessionPush"

    /** Fire-and-forget scope: a push hiccup must never fail the session op. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val CONFIG_CACHE_MILLIS = 10 * 60_000L

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedUrlAt = 0L

    /**
     * Best-effort notify — call AFTER the session write landed.
     * [event] is "asked" (helper, after requestSession) or "answered"
     * (either side, after grant/deny/cancel). Never throws.
     */
    fun notify(sessionId: String, event: String) {
        scope.launch {
            try {
                notifyBlocking(sessionId, event)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The Worker may be unconfigured (config/notify missing),
                // offline, or down — the alive-app listener path and the
                // in-app cards still cover the visible cases.
            }
        }
    }

    private suspend fun notifyBlocking(sessionId: String, event: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val url = workerUrl() ?: return // feature not configured
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
                android.util.Log.w(TAG, "worker /notify answered HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Cached read of `config/notify` { url } — null when off/missing. */
    private suspend fun workerUrl(): String? {
        val now = System.currentTimeMillis()
        val cached = cachedUrl
        if (cached != null && now - cachedUrlAt < CONFIG_CACHE_MILLIS) return cached
        // Negative results cache too: while the doc is missing this costs
        // ONE bounded read per 10 minutes per process, not one per ask.
        if (now - cachedUrlAt < CONFIG_CACHE_MILLIS) return null
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
            url
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline / rules hiccup: treat as not-configured for 10 min.
            cachedUrl = null
            cachedUrlAt = now
            null
        }
    }
}
