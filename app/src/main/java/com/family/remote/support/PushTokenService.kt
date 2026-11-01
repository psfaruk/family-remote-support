package com.family.remote.support

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * v0.21.0 (audit D-F1) — FCM token-rotation listener.
 *
 * The audit found the app registered its FCM token exactly ONCE (nested in
 * the device-registration success path at app open) and had NO onNewToken
 * handler anywhere. When FCM rotates the token while the process lives (or
 * reinstalls/app-data clears happen), the registry keeps the STALE token,
 * the kill-push sweep sends into the void, FCM answers UNREGISTERED and the
 * sweep DELETES the doc — killed-app notifications then silently never fire
 * again until the next app open. This service rewrites the registry doc the
 * moment Firebase hands us a fresh token.
 *
 * v0.45.0 (audit P1) — DATA messages are now handled HERE. The Worker's
 * /notify tier (event-triggered by the writing app, replacing every polling
 * sweep) sends data-only HIGH-priority messages so THIS method builds the
 * notification in the app's own language with Allow/Deny actions and a
 * deep link — identical to the alive-process listener path because both
 * delegate to AskNotifier. High-priority data messages are delivered to a
 * killed app (Android starts the process for onMessageReceived); the one
 * state nothing can reach is Force-stopped — that is an Android platform
 * rule, not an app bug.
 */
class PushTokenService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        // Signed out: nothing to register — the next sign-in re-registers
        // (FirebaseMessaging.getToken() returns this same fresh token).
        val deviceId = stableDeviceId(applicationContext)
        writeFcmTokenDoc(user.uid, deviceId, token)
            .addOnFailureListener {
                // Best-effort: the next app open re-runs registerFcmToken,
                // which fetches the CURRENT token and upserts the same doc.
                android.util.Log.w(
                    "PushTokenService",
                    "token registry refresh failed: ${it.message}"
                )
            }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            // The recipient's incoming ask (helper just wrote it + told the
            // Worker). deadlineMillis comes server-stamped so the tray entry
            // can time itself out even when the phone clock is off.
            "session_request" -> {
                val sessionId = data["sessionId"] ?: return
                val deadline = data["deadlineMillis"]?.toLongOrNull() ?: 0L
                ServerClock.syncFrom(
                    com.google.firebase.Timestamp(
                        java.util.Date(
                            (data["sentAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis())
                        )
                    ),
                    System.currentTimeMillis()
                )
                AskNotifier.postAsk(
                    context = applicationContext,
                    sessionId = sessionId,
                    relId = data["relId"],
                    helperEmail = data["helperEmail"].orEmpty(),
                    deadlineMillis = if (deadline > 0) {
                        deadline
                    } else {
                        ServerClock.now() + SESSION_RESPONSE_TTL_MILLIS
                    },
                    logEvent = true // the killed-app path owns the log entry
                )
            }
            // The helper's answer receipt (the recipient just granted/denied,
            // or the helper's own cancellation mirrored to the recipient).
            "session_answered" -> {
                val sessionId = data["sessionId"] ?: return
                val answer = data["answer"] ?: return
                AskNotifier.postAnswer(
                    context = applicationContext,
                    sessionId = sessionId,
                    relId = data["relId"],
                    answer = answer,
                    counterpartEmail = data["counterpartEmail"].orEmpty()
                )
            }
            else -> {
                // Unknown/absent type: no fabricated behaviour — the FCM SDK
                // still auto-displays any NOTIFICATION message itself.
            }
        }
    }
}
