package com.family.remote.support

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
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

    private var initialized = false
    private var registration: ListenerRegistration? = null
    private var seenRequestIds: MutableSet<String> = mutableSetOf()

    /** Call once per process (idempotent). */
    fun start(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext

        // v0.45.0 (audit P4): the channel + every session notification now
        // come from AskNotifier — ONE factory (deep link, actions, timeout,
        // log) for both the listener path and the FCM data path.
        AskNotifier.ensureChannel(appContext)

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
            // v0.36.0 (quota hardening): cap the listener query like
            // watchSessions does — a runaway accumulation of stale REQUESTED
            // docs could bill an unbounded initial snapshot every attach.
            .limit(10)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                // v0.37.0 — safe cast: a hard `as App` crashes under any replaced
                // application class; degrade to "skip this event" instead.
                val app = context.applicationContext as? App ?: return@addSnapshotListener
                // The in-app card already shows the ask while the app is visible.
                if (app.startedActivities > 0) return@addSnapshotListener
                val now = System.currentTimeMillis()
                for (doc in snap.documents) {
                    // v0.21.0 (audit): the dedupe set was populated but never
                    // CONSULTED — every listener event re-alerted ALL pending
                    // asks (repeat sound/vibration/heads-up spam within the
                    // answer window). Consult it first.
                    if (doc.id in seenRequestIds) continue
                    seenRequestIds.add(doc.id)
                    // v0.45.0 (audit P7): fold the server-stamped createdAt
                    // (vs the local clock) into ServerClock — a phone with
                    // manual date/time gets its deadline math corrected
                    // before any comparison below.
                    ServerClock.syncFrom(doc.getTimestamp("createdAt"), now)
                    val deadline = doc.getTimestamp("respondDeadline")?.toDate()?.time ?: 0L
                    // Only fresh asks ring; stale ones are handled as EXPIRED.
                    // v0.45.0 (audit P7): SERVER-corrected comparison.
                    if (deadline > ServerClock.now()) {
                        AskNotifier.postAsk(
                            context = context,
                            sessionId = doc.id,
                            relId = doc.getString("relId"),
                            helperEmail = doc.getString("helperEmail").orEmpty(),
                            deadlineMillis = deadline,
                            logEvent = false // recordSessionTransitions owns it
                        )
                    }
                }
                if (seenRequestIds.size > 50) {
                    seenRequestIds = seenRequestIds.toList().takeLast(20).toMutableSet()
                }
            }
    }
}

