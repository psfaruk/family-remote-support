package com.family.remote.support

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.family.remote.support.ui.LangPrefBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 7 — foreground service that keeps the MediaProjection alive while the
 * recipient's screen is being shared with the helper.
 *
 * Android 14 ordering requirement honored here: startForeground(…, type =
 * mediaProjection) runs BEFORE the engine touches MediaProjection (the
 * consent extras are simply passed through to the engine).
 *
 * The service owns nothing WebRTC-related itself — ScreenShareEngine is the
 * singleton that does the work; stopping the service tears the engine down.
 */
class ScreenShareService : Service() {

    // v0.21.0 (audit): stops the service when the engine goes idle — the
    // "Screen sharing active" foreground notification used to linger until
    // the session TTL or a manual stop even after the engine had ENDED.
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val uid = intent.getStringExtra(EXTRA_UID)
        val peerUid = intent.getStringExtra(EXTRA_PEER_UID)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        startInForeground()
        if (sessionId.isNullOrBlank() || uid.isNullOrBlank() || peerUid.isNullOrBlank() ||
            projectionData == null
        ) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        ScreenShareEngine.startRecipient(
            applicationContext,
            sessionId,
            uid,
            peerUid,
            projectionData
        )
        // v0.21.0 (audit): watch the engine — when it reaches IDLE (including
        // the 4 s grace after ENDED) the service stops itself, so the
        // foreground notification can never outlive the actual capture.
        serviceScope.launch {
            ScreenShareEngine.state.collect { s ->
                if (s == ShareState.IDLE) stopSelf()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // v0.21.0 (audit): ROLE-GUARDED stop — this service only owns the
        // RECIPIENT round. The old unconditional stopRecipient() tore down
        // the HELPER session (and sent "bye" to the wrong peer) if the engine
        // had switched roles while a leftover service instance died.
        ScreenShareEngine.stopRecipientFromService()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // M8 (v0.34.0) — channel name/description + title/body resolve through
        // LangPrefBus (the SessionNotifier pattern), so a non-English user
        // reads the ongoing share notification in their app language.
        val lc = LangPrefBus.wrap(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    lc.getString(R.string.share_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = lc.getString(R.string.share_channel_desc)
                }
            )
        }
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(lc, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(lc.getString(R.string.share_notif_title))
            .setContentText(lc.getString(R.string.share_notif_body))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "screen_share"
        private const val NOTIFICATION_ID = 71

        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_UID = "uid"
        const val EXTRA_PEER_UID = "peerUid"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        /** Start the service with the fresh MediaProjection consent result. */
        fun start(context: Context, sessionId: String, uid: String, peerUid: String, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenShareService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_UID, uid)
                .putExtra(EXTRA_PEER_UID, peerUid)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenShareService::class.java))
        }
    }
}
