package com.family.remote.support

import android.content.Context
import com.google.firebase.Timestamp
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
 *
 * v0.47.0 (audit B4 — the background-transition hole): the snapshot handler
 * used to return EARLY while the app was visible, BEFORE remembering the
 * docs. An ask that arrived (or existed) while visible was therefore never
 * posted — and because Firestore only re-fires on a NEW write, pressing
 * Home left the pending ask WITHOUT a tray notification until some
 * unrelated snapshot arrived. The fix: every snapshot's docs are ALWAYS
 * cached (zero extra reads), the dedupe/deliver loop runs only while
 * backgrounded, and App.onActivityStopped(→0) now calls [onAppBackgrounded]
 * so the exact moment the app leaves the screen re-delivers every cached
 * pending ask. The visible path is unchanged: the consent card owns the UX.
 */
object SessionNotifier {

    private var initialized = false
    private var registration: ListenerRegistration? = null
    private var seenRequestIds: MutableSet<String> = mutableSetOf()

    /**
     * v0.47.0 (audit B4) — the cached pending asks of the LAST snapshot.
     * Populated on every listener event regardless of visibility, so the
     * fg→bg sweep always has the truth without a re-query.
     */
    private var cachedPendingAsks: List<CachedAsk> = emptyList()

    /** The minimal ask fields the sweep needs (parsed off the DocumentSnapshot). */
    private class CachedAsk(
        val id: String,
        val relId: String?,
        val helperEmail: String,
        val deadlineMillis: Long,
        val createdAt: Timestamp?
    )

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
                cachedPendingAsks = emptyList()
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
                val now = System.currentTimeMillis()
                // v0.47.0 (audit B4) — ALWAYS cache the pending asks first:
                // the fg→bg sweep re-uses exactly these, with no re-query.
                cachedPendingAsks = snap.documents.map { doc ->
                    CachedAsk(
                        id = doc.id,
                        relId = doc.getString("relId"),
                        helperEmail = doc.getString("helperEmail").orEmpty(),
                        deadlineMillis = doc.getTimestamp("respondDeadline")?.toDate()?.time ?: 0L,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                // The in-app card already shows the ask while the app is
                // visible — but the cache above is now fresh, so the moment
                // the app backgrounds ([onAppBackgrounded]) re-delivers it.
                if (app.startedActivities > 0) return@addSnapshotListener
                deliverPendingAsks(context, now)
            }
    }

    /**
     * v0.47.0 (audit B4) — called by App.onActivityStopped when the LAST
     * activity left the screen: a pending ask that was visible in-app must
     * become a tray notification NOW (the user may never see the card
     * again before the ask dies). Uses only the cached snapshot — zero
     * extra Firestore reads, and the per-doc dedupe set keeps it quiet.
     */
    fun onAppBackgrounded(context: Context) {
        val app = context.applicationContext as? App ?: return
        if (app.startedActivities > 0) return // still visible — nothing to do
        if (registration == null) return // signed out / not watching
        if (cachedPendingAsks.isEmpty()) return
        deliverPendingAsks(context, System.currentTimeMillis())
    }

    /** The shared dedupe/deliver loop (listener events + the bg sweep). */
    private fun deliverPendingAsks(context: Context, now: Long) {
        for (ask in cachedPendingAsks) {
            // v0.21.0 (audit): the dedupe set was populated but never
            // CONSULTED — every listener event re-alerted ALL pending
            // asks (repeat sound/vibration/heads-up spam within the
            // answer window). Consult it first.
            if (ask.id in seenRequestIds) continue
            seenRequestIds.add(ask.id)
            // v0.45.0 (audit P7): fold the server-stamped createdAt
            // (vs the local clock) into ServerClock — a phone with
            // manual date/time gets its deadline math corrected
            // before any comparison below.
            ServerClock.syncFrom(ask.createdAt, now)
            // Only fresh asks ring; stale ones are handled as EXPIRED.
            // v0.45.0 (audit P7): SERVER-corrected comparison.
            if (ask.deadlineMillis > ServerClock.now()) {
                AskNotifier.postAsk(
                    context = context,
                    sessionId = ask.id,
                    relId = ask.relId,
                    helperEmail = ask.helperEmail,
                    deadlineMillis = ask.deadlineMillis,
                    logEvent = false // recordSessionTransitions owns it
                )
            }
        }
        if (seenRequestIds.size > 50) {
            seenRequestIds = seenRequestIds.toList().takeLast(20).toMutableSet()
        }
    }
}
