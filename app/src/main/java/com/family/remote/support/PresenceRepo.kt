package com.family.remote.support

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * Phase 5 — device presence + FCM token registry.
 *
 * Presence = a tiny heartbeat document per device. It answers exactly one
 * question: "was this device around recently?" — nothing more.
 *
 * v0.36.0 (Owner quota directive — "read/write only when the user acts,
 * auto-updates off, requests only from the app"): the heartbeat is
 * FOREGROUND-ONLY now. A heartbeat is written when the app opens / signs in,
 * every 5 minutes while the activity is RESUMED, and once more on ON_STOP
 * (the user leaving IS a user action). The 15-minute WorkManager worker —
 * the app's only Firebase write that ran with the app fully closed — is
 * retired; [cancelPeriodicHeartbeat] purges the periodic work persisted by
 * older installs (WorkManager survives app updates and reboots).
 *
 * The badge shown in the UI is computed from the READER's clock, so the
 * windows are deliberately generous (a few minutes of clock skew between
 * family phones is normal and harmless here). "Online" therefore decays
 * ~20 min after the counterpart last actually used their phone — an honest
 * "around recently" signal, by design.
 */

private const val PRESENCE_COLLECTION = "devicePresence"
private const val TOKEN_COLLECTION = "fcmTokens"

/** A heartbeat newer than this shows as "Online".
 *
 * v0.21.0 (audit): 5 → 20 minutes. The heartbeat cadence is WorkManager's
 * 15-minute floor (often deferred further in Doze), so with a 5-minute
 * window an actively-used phone flipped to "Last seen…" between worker
 * ticks — the badge mathematically could not stay Online. 20 min covers
 * the worst-case worker interval. */
const val PRESENCE_ONLINE_MILLIS = 20 * 60 * 1000L
/** A heartbeat older than this (but under 1 h) shows as "Last seen … ago". */
const val PRESENCE_STALE_MILLIS = 60 * 60 * 1000L

data class PresenceView(
    val uid: String,
    val deviceId: String,
    val model: String,
    /** 0 when the document exists but has no heartbeat yet. */
    val heartbeatAtMillis: Long
)

fun presenceDocId(uid: String, deviceId: String): String = "${uid}_$deviceId"

/** One heartbeat write for this device (merge — the document is upserted). */
suspend fun sendHeartbeat(uid: String, deviceId: String, model: String) {
    Fb.db.collection(PRESENCE_COLLECTION)
        .document(presenceDocId(uid, deviceId))
        .set(
            mapOf(
                "uid" to uid,
                "deviceId" to deviceId,
                "model" to model,
                "heartbeatAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        .awaitWithTimeout("updating this device's presence")
}

/** Watch one device's presence (e.g. the paired recipient's device). */
fun watchDevicePresence(
    uid: String,
    deviceId: String,
    onChange: (PresenceView?) -> Unit,
    onError: (Exception) -> Unit = {}
): () -> Unit {
    val registration = Fb.db.collection(PRESENCE_COLLECTION)
        .document(presenceDocId(uid, deviceId))
        .addSnapshotListener { snap: DocumentSnapshot?, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                onChange(null)
                return@addSnapshotListener
            }
            onChange(
                PresenceView(
                    uid = snap.getString("uid") ?: uid,
                    deviceId = snap.getString("deviceId") ?: deviceId,
                    model = snap.getString("model") ?: "",
                    heartbeatAtMillis = snap.getTimestamp("heartbeatAt")?.toDate()?.time ?: 0L
                )
            )
        }
    return { registration.remove() }
}

/**
 * Save this device's FCM registration token (SHA-256-hash document id, so the
 * raw token is the doc content but the id never leaks it in lists). Sending
 * notifications is a later phase — this only registers the address.
 *
 * v0.21.0 (audit): split into [writeFcmTokenDoc] so PushTokenService
 * (onNewToken, no coroutine context) can refresh the registry the moment
 * FCM rotates the token — previously a rotation left a stale doc until the
 * next app open, and the sweep then deleted it as UNREGISTERED (push dead).
 */
suspend fun registerFcmToken(uid: String, deviceId: String) {
    val token = FirebaseMessaging.getInstance().token
        .awaitWithTimeout("fetching the push-notification token")
    writeFcmTokenDoc(uid, deviceId, token)
        .awaitWithTimeout("saving the push-notification token")
}

/** Fire-and-forget registry write usable from a FirebaseMessagingService. */
fun writeFcmTokenDoc(uid: String, deviceId: String, token: String): com.google.android.gms.tasks.Task<Void> {
    val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)
    return Fb.db.collection(TOKEN_COLLECTION).document(hash)
        .set(
            mapOf(
                "uid" to uid,
                "deviceId" to deviceId,
                "token" to token,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
}

/**
 * Phase 13 — sign-out hygiene. Removes this device's traces from Firestore
 * while the user is still authenticated (rules require signedIn for both):
 *   1. devicePresence/{uid}_{deviceId} — paired phones stop seeing this
 *      device as "Online" immediately instead of waiting out the TTL.
 *   2. fcmTokens documents owned by this uid on THIS deviceId — after
 *      sign-out the kill-push sweep finds no token, so a signed-out phone
 *      no longer receives session-request notifications.
 * Both deletes are exactly what the published rules already permit
 * (own-uid only) — no rules change is involved.
 *
 * Best-effort by design: a network failure here must never trap the user
 * inside a signed-in state. Every step degrades independently; the caller
 * proceeds with signOut() regardless and shows an honest notice when
 * anything failed.
 *
 * @return true when every trace was removed, false when any step failed.
 */
suspend fun deleteOwnPresenceAndToken(uid: String, deviceId: String): Boolean {
    var clean = true
    try {
        Fb.db.collection(PRESENCE_COLLECTION)
            .document(presenceDocId(uid, deviceId))
            .delete()
            .awaitWithTimeout("removing this device's presence")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        clean = false
    }
    // Token document ids are SHA-256 hashes of the raw FCM token — they
    // cannot be derived without the token itself, so list this user's
    // token docs (rules: list only ever returns uid == mine) and delete
    // the ones registered to this device.
    try {
        val mine = Fb.db.collection(TOKEN_COLLECTION)
            .whereEqualTo("uid", uid)
            .get()
            .awaitWithTimeout("listing this device's push tokens")
        for (doc in mine.documents) {
            if (doc.getString("deviceId") != deviceId) continue
            try {
                doc.reference.delete()
                    .awaitWithTimeout("removing a push token")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                clean = false
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        clean = false
    }
    return clean
}

/**
 * v0.36.0 (Owner quota directive): RETIRED as a writer. Kept ONLY so the
 * periodic work persisted by older installs terminates itself cleanly —
 * [Result.failure] is terminal for periodic work (no further runs), and
 * [cancelPeriodicHeartbeat] removes even that on the first v0.36.0 open.
 * Do NOT re-schedule this worker without re-reading worklog §75 (the
 * foreground-only ticker in MainActivity owns presence now).
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.failure()
}

/**
 * v0.36.0 — cancels any "device-heartbeat" periodic work persisted by older
 * installs (WorkManager state survives app updates and reboots, so simply
 * stopping the enqueue would leave old phones heartbeating for weeks).
 * Idempotent; called once per app open.
 */
fun cancelPeriodicHeartbeat(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("device-heartbeat")
}
