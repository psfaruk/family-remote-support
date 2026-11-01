package com.family.remote.support

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.family.remote.support.ui.LangPrefBus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// v0.45.0 (audit P4/P8) — ONE notification factory for session events +
// the app's deep-link router.
//
// Before this, four places built session/place/share notifications with
// bare `Intent(context, MainActivity::class.java)` PendingIntents: no
// extras, no deep link, no launchMode, no onNewIntent — every tap just
// dropped you on Home, expired asks stayed in the tray forever, and there
// was no way to answer from the notification. AskNotifier centralizes:
//   • localized channel + text (LangPrefBus-wrapped context),
//   • deep-link content intents (DeepLinks.EXTRA_ROUTE),
//   • Allow / Deny ACTION buttons on the ask (SessionActionReceiver),
//   • setTimeoutAfter so a dead ask removes itself,
//   • NotificationsLog recording at the REAL event time.
// SessionNotifier (listener path) and PushTokenService (FCM data path)
// both delegate here, so the killed-app notification and the
// backgrounded-app notification are byte-identical.
// ═══════════════════════════════════════════════════════════════════════

/**
 * The app's tiny deep-link bus. Notification taps land on MainActivity
 * (singleTop); [fromIntent] parks the wanted route here, and the shell's
 * navigation effect (MainActivity.HomeScreen) consumes it via [pending].
 * Routes are plain AppRoutes strings — "home", "tracking",
 * "home/sessions/{id}" — no URI scheme, nothing exported.
 */
object DeepLinks {
    const val EXTRA_ROUTE = "frs.route"

    private val _pending = MutableStateFlow<String?>(null)

    /** The route a notification asked for, or null. Cleared by the shell. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Park the route from a notification/content intent (blank = ignore). */
    fun fromIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() } ?: return
        _pending.value = route
    }

    /** The shell navigated — clear the request. */
    fun consume() {
        _pending.value = null
    }
}

/**
 * The ONE builder/poster for support-session notifications. Notification
 * IDs are stable per (kind, sessionId) so a re-post REPLACES instead of
 * stacking — the ask keeps `sessionId.hashCode()` (the id SessionNotifier
 * already used, so old and new paths dedupe against each other).
 */
object AskNotifier {

    const val CHANNEL_ID = "support_session_requests"

    /** Distinct id for the helper-side answer notification. */
    private fun answerId(sessionId: String): Int = ("answer:$sessionId").hashCode()

    // ── Channel ────────────────────────────────────────────────────────

    /** Idempotent; localized channel name (same channel id the FCM SDK's
     *  manifest default points at, so kill-push tray entries group here). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val lc = LangPrefBus.wrap(context)
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                lc.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    // ── PendingIntents ─────────────────────────────────────────────────

    /** Content tap → MainActivity (singleTop) with the deep-link route. */
    private fun contentIntent(context: Context, requestCode: Int, route: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(DeepLinks.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Notification action tap → the broadcast receiver below. */
    private fun actionIntent(context: Context, sessionId: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ("$action:$sessionId").hashCode(),
            Intent(context, SessionActionReceiver::class.java)
                .putExtra(SessionActionReceiver.EXTRA_SESSION_ID, sessionId)
                .putExtra(SessionActionReceiver.EXTRA_ACTION, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Notifications ──────────────────────────────────────────────────

    /**
     * The recipient's incoming ask. [deadlineMillis] drives setTimeoutAfter
     * (the tray entry removes itself when the ask dies) and must be
     * SERVER-corrected by the caller when it comes from a server timestamp.
     *
     * @param relId the pair's relationship id — powers the local NICKNAME
     *        (audit P13): a saved "আব্বু" replaces the raw helper email.
     * @param logEvent true ONLY on the FCM data path (killed app): the
     *        alive-app path already records through
     *        NotificationsLog.recordSessionTransitions, and the log's
     *        (type, arg2=sessionId) dedupe would otherwise double-write.
     */
    fun postAsk(
        context: Context,
        sessionId: String,
        relId: String?,
        helperEmail: String,
        deadlineMillis: Long,
        logEvent: Boolean
    ) {
        if (!isNotifGranted(context)) return // honest absence, never a crash
        // Same rule as SessionNotifier: while the app is visible the
        // in-app consent card owns the UX; no tray noise on top of it.
        val app = context.applicationContext as? App
        if (app != null && app.startedActivities > 0) return
        ensureChannel(context)
        val lc = LangPrefBus.wrap(context)
        val helper = Nicknames.displayName(
            context, relId, helperEmail, lc.getString(R.string.notif_fallback_helper)
        )
        // v0.47.0 (audit B1) — SERVER-corrected clock for the timeout math.
        // [deadlineMillis] is always server-stamped (Firestore
        // respondDeadline / the Worker's server-side re-read); the old raw
        // local clock made a manual-clock phone either skip the self-removal
        // entirely (local clock ahead → a dead ask sat in the tray until
        // tapped) or kill a live ask early/late (local clock behind).
        val now = ServerClock.now()
        val builder = NotificationCompat.Builder(lc, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(lc.getString(R.string.notif_help_requested_title))
            .setContentText(lc.getString(R.string.notif_help_requested_body, helper))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context, sessionId.hashCode(), "home"))
            // v0.45.0 (audit P4): answer WITHOUT opening the app.
            .addAction(0, lc.getString(R.string.notif_allow), actionIntent(context, sessionId, "allow"))
            .addAction(0, lc.getString(R.string.notif_deny), actionIntent(context, sessionId, "deny"))
        if (deadlineMillis > now) {
            // A dead ask must not sit in the tray until tapped.
            builder.setTimeoutAfter(deadlineMillis - now)
        }
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(sessionId.hashCode(), builder.build())
        if (logEvent) {
            NotificationsLog.recordAt(
                context, "session_asked", now, helper, sessionId
            )
        }
    }

    /**
     * The helper's answer notification (FCM data path only — the helper's
     * alive-app UX is the session card). [answer] is the session status
     * after the fact: ACTIVE | DENIED | CANCELLED. [relId] powers the local
     * nickname (audit P13) just like on the ask side.
     */
    fun postAnswer(
        context: Context,
        sessionId: String,
        relId: String?,
        answer: String,
        counterpartEmail: String
    ) {
        if (!isNotifGranted(context)) return
        val app = context.applicationContext as? App
        if (app != null && app.startedActivities > 0) return
        ensureChannel(context)
        val lc = LangPrefBus.wrap(context)
        val counterpart = Nicknames.displayName(
            context, relId, counterpartEmail, lc.getString(R.string.notif_fallback_helper)
        )
        val (title, body, logType) = when (answer) {
            "ACTIVE" -> Triple(
                lc.getString(R.string.notif_answered_allowed_title, counterpart),
                lc.getString(R.string.notif_answered_allowed_body),
                "session_started"
            )
            "DENIED" -> Triple(
                lc.getString(R.string.notif_answered_denied_title, counterpart),
                lc.getString(R.string.notif_answered_denied_body),
                "session_denied"
            )
            else -> Triple(
                lc.getString(R.string.notif_answered_cancelled_title),
                lc.getString(R.string.notif_answered_cancelled_body, counterpart),
                "session_cancelled"
            )
        }
        val notification = NotificationCompat.Builder(lc, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(
                contentIntent(context, answerId(sessionId), "home/sessions/$sessionId")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(answerId(sessionId), notification)
        NotificationsLog.recordAt(
            context, logType, System.currentTimeMillis(), counterpart, sessionId
        )
    }

    /**
     * Replaces the ask's tray entry after an Allow/Deny ACTION tap: the
     * user answered from the shade and deserves closed-loop feedback.
     * [error] null = success (and [allowed] says which action landed).
     */
    fun postResult(context: Context, sessionId: String, allowed: Boolean, error: String? = null) {
        ensureChannel(context)
        val lc = LangPrefBus.wrap(context)
        val (title, body) = if (error != null) {
            lc.getString(R.string.notif_result_failed_title) to
                lc.getString(R.string.notif_result_failed_body, error)
        } else if (allowed) {
            lc.getString(R.string.notif_result_allowed_title) to
                lc.getString(R.string.notif_result_allowed_body)
        } else {
            lc.getString(R.string.notif_result_denied_title) to
                lc.getString(R.string.notif_result_denied_body)
        }
        val notification = NotificationCompat.Builder(lc, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(
                contentIntent(context, sessionId.hashCode(), "home/sessions/$sessionId")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(sessionId.hashCode(), notification)
    }
}

/**
 * v0.45.0 (audit P4) — Allow/Deny straight from the notification shade.
 *
 * A manifest-declared receiver: Android starts the app PROCESS for the
 * broadcast even when the app was killed, so the buttons work in the same
 * states the ask notification itself does (except Force-stopped, which
 * Android blocks entirely). The grant/deny run as the SAME rules-checked
 * transactions the in-app cards use (grantSession/denySession) with the
 * persisted FirebaseAuth session — never any shortcut write.
 */
class SessionActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val appContext = context.applicationContext
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            AskNotifier.postResult(
                appContext, sessionId, allowed = false,
                error = appContext.getString(R.string.notif_result_open_app)
            )
            return
        }
        val uid = user.uid
        val pendingResult = goAsync()
        scope.launch {
            try {
                // v0.47.0 (audit B2) — the goAsync window is bounded now. A
                // manifest receiver's process stays foreground-priority only
                // ~10 s; the old unbounded coroutine could legally run up to
                // RPC_TIMEOUT_MILLIS (30 s) on a slow network, silently
                // losing the OS's backing (or worse). 9 s leaves headroom
                // under the broadcast window; on timeout the write keeps
                // running in the cached process (its result still lands —
                // Firestore transactions are server-atomic), but the USER
                // immediately gets the honest "open the app" result instead
                // of a shade entry that answers nothing.
                val allowed: Boolean = withTimeoutOrNull(ACTION_DEADLINE_MILLIS) {
                    if (action == "allow") {
                        grantSession(sessionId, uid)
                        true
                    } else {
                        denySession(sessionId, uid)
                        false
                    }
                } ?: run {
                    AskNotifier.postResult(
                        appContext, sessionId, allowed = false,
                        error = appContext.getString(R.string.notif_result_open_app)
                    )
                    return@launch
                }
                AskNotifier.postResult(appContext, sessionId, allowed)
                // NOTE: no extra SessionPush call here — grantSession/
                // denySession themselves fire the /notify that reaches the
                // helper's closed app (one call per answer, Worker-side
                // claim dedupes any race).
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is SessionException) {
                    e.localizedMessage(appContext)
                } else {
                    e.message ?: appContext.getString(R.string.notif_result_open_app)
                }
                AskNotifier.postResult(appContext, sessionId, allowed = false, error = message)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "frs.sessionId"
        const val EXTRA_ACTION = "frs.action" // "allow" | "deny"

        /** v0.47.0 (audit B2) — the goAsync ceiling: ~10 s OS window minus headroom. */
        private const val ACTION_DEADLINE_MILLIS = 9_000L
    }
}
