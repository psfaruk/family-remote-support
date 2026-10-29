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
 * Data messages (RemoteMessage#data) need no handling here: session-request
 * pushes arrive as NOTIFICATION messages, which the Firebase SDK itself
 * displays when the process is dead, and SessionNotifier's Firestore
 * listener covers the backgrounded-but-alive case.
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
        // Intentionally empty: notification messages are display-automatically;
        // the Firestore listener in SessionNotifier owns the alive-process UX.
    }
}
