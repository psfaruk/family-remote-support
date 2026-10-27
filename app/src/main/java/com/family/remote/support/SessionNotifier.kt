package com.family.remote.support

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

/**
 * Phase 6 — notify the RECIPIENT about an incoming support-session request
 * while the app is NOT on screen.
 *
 * Transport today: a Firestore snapshot listener owned by the app PROCESS.
 * It therefore works while the app is backgrounded but still alive (very
 * common right after "home" was pressed). When Android kills the process,
 * delivery stops — true push-when-killed arrives with the Phase 7 server,
 * which will send FCM messages to the tokens already registered in
 * Phase 5 (fcmTokens).
 *
 * Behaviour:
 *  - app visible          -> no notification (the in-app permission card shows)
 *  - app background/alive -> one high-importance notification per fresh ask
 *  - no POST_NOTIFICATIONS permission (Android 13+) -> silently nothing;
 *    the UI shows an "Enable notifications" button in the session section
 */
object SessionNotifier {

    private const val CHANNEL_ID = "support_session_requests"

    private var initialized = false
    private var registration: ListenerRegistration? = null
    private var seenRequestIds: MutableSet<String> = mutableSetOf()

    /** Call once per process (idempotent). */
    fun start(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Support session requests",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Re-bind the listener whenever the signed-in account changes.
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                registration?.remove()
                registration = null
                seenRequestIds.clear()
            } else {
                watch(appContext, user.uid)
            }
        }
    }

    private fun watch(context: Context, uid: String) {
        registration?.remove()
        registration = Fb.db
            .collection(SESSION_COLLECTION)
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("status", "REQUESTED")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val app = context.applicationContext as App
                // The in-app card already shows the ask while the app is visible.
                if (app.startedActivities > 0) return@addSnapshotListener
                val now = System.currentTimeMillis()
                for (doc in snap.documents) {
                    seenRequestIds.add(doc.id)
                    val deadline = doc.getTimestamp("respondDeadline")?.toDate()?.time ?: 0L
                    // Only fresh asks ring; stale ones are handled as EXPIRED.
                    if (deadline > now) post(context, doc)
                }
                if (seenRequestIds.size > 50) {
                    seenRequestIds = seenRequestIds.toList().takeLast(20).toMutableSet()
                }
            }
    }

    private fun post(context: Context, doc: DocumentSnapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val helperEmail = doc.getString("helperEmail") ?: "A family member"
        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            doc.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Help requested")
            .setContentText("$helperEmail wants to help with this phone — open to allow or deny.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(doc.id.hashCode(), notification)
    }
}
