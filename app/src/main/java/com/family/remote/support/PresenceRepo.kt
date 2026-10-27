package com.family.remote.support

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Phase 5 — device presence + FCM token registry.
 *
 * Presence = a tiny heartbeat document per device. It answers exactly one
 * question: "was this device around recently?" — nothing more. A heartbeat is
 * written immediately whenever the app comes to the foreground, plus every
 * 15 minutes from WorkManager while the device is online.
 *
 * The badge shown in the UI is computed from the READER's clock, so the
 * windows are deliberately generous (a few minutes of clock skew between
 * family phones is normal and harmless here).
 */

private const val PRESENCE_COLLECTION = "devicePresence"
private const val TOKEN_COLLECTION = "fcmTokens"

/** A heartbeat newer than this shows as "Online". */
const val PRESENCE_ONLINE_MILLIS = 5 * 60 * 1000L
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
 */
suspend fun registerFcmToken(uid: String, deviceId: String) {
    val token = FirebaseMessaging.getInstance().token
        .awaitWithTimeout("fetching the push-notification token")
    val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)
    Fb.db.collection(TOKEN_COLLECTION).document(hash)
        .set(
            mapOf(
                "uid" to uid,
                "deviceId" to deviceId,
                "token" to token,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        .awaitWithTimeout("saving the push-notification token")
}

/**
 * Background heartbeat worker. WorkManager's periodic floor is 15 minutes;
 * the app also heartbeats on every foreground, which keeps the badge fresh
 * during real use.
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        return try {
            sendHeartbeat(user.uid, stableDeviceId(applicationContext), Build.MODEL)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Network hiccups are routine — let WorkManager back off and retry.
            Result.retry()
        }
    }
}

/** Idempotent: schedules the periodic heartbeat (KEEP — no duplicates). */
fun schedulePeriodicHeartbeat(context: Context) {
    val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "device-heartbeat",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
