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
import com.family.remote.support.ui.LangPrefBus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

/**
 * Phase 6 — notify the RECIPIENT about an incoming support-session request
 * while the app is NOT on screen.
 *
 * Two transports now cover every state (v0.9.8, Phase 8.2):
 *  1. THIS listener (Firestore snapshot owned by the app PROCESS) — covers
 *     app visible (quiet; the in-app card shows) and app backgrounded but
 *     alive (high-importance notification).
 *  2. FCM kill-push — when Android has killed the process, a sweep service
 *     (sandbox mini-service + GitHub Actions backstop, both running
 *     scripts/fcm-kill-push.mjs) sends a high-priority FCM notification
 *     message to the recipient's registered fcmTokens; the OS itself
 *     displays it (manifest meta-data picks this same channel), and tapping
 *     it cold-starts the app, whose listener then shows the in-app card.
 *
 * Behaviour:
 *  - app visible          -> no notification (the in-app permission card shows)
 *  - app background/alive -> one high-importance notification per fresh ask
 *  - app killed           -> FCM kill-push notification (outside this process)
 *  - no POST_NOTIFICATIONS permission (Android 13+) -> silently nothing;
 *    the UI shows an "Enable notifications" button in the session section
 *
 * v0.26.0 (Phase 18.7, UI/i18n audit): channel name + notification title/body
 * were hardcoded ENGLISH and used the raw context — they bypassed the app's
 * 15-language system (LangPrefBus) entirely. All user-facing text now resolves
 * through LangPrefBus.wrap() from values-XX/strings.xml, so a Japanese/Arabic
 * user gets the ask notification in their chosen app language.
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
                LangPrefBus.wrap(appContext).getString(R.string.notif_channel_name),
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
                    // v0.21.0 (audit): the dedupe set was populated but never
                    // CONSULTED — every listener event re-alerted ALL pending
                    // asks (repeat sound/vibration/heads-up spam within the
                    // 2-minute window). Consult it first.
                    if (doc.id in seenRequestIds) continue
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
        // v0.26.0 (Phase 18.7): resolve text from the app-language context.
        val lc = LangPrefBus.wrap(context)
        val helperEmail = doc.getString("helperEmail")?.takeIf { it.isNotBlank() }
            ?: lc.getString(R.string.notif_fallback_helper)
        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            doc.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(lc, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(lc.getString(R.string.notif_help_requested_title))
            .setContentText(lc.getString(R.string.notif_help_requested_body, helperEmail))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // v0.21.0 (audit): never re-sound the same ask
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(doc.id.hashCode(), notification)
    }
}
