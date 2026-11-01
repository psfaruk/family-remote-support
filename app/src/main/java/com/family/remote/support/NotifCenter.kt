package com.family.remote.support

import com.family.remote.support.ui.nav.AppRoutes

// ═══════════════════════════════════════════════════════════════════════
// v0.46.0 (external-audit G1) — the notification center's PURE decision
// layer: category mapping, the filter chips' set algebra, the read-state
// transitions and the row-tap route mapping — all plain Kotlin over
// List<NotifEvent> with ZERO Android imports, so the whole G1 decision
// surface is provable in the repo's CI unit-test gate
// (NotifCenterTest, JUnit4 — same pattern as MovementClassifierTest /
// PlaceAlertEngineTest) instead of only on the Owner's device.
//
// The honesty rule (NAVIGATION_MAP §2) governs here too — never a
// fabricated label: an unknown FUTURE event type maps to null and stays
// visible under "All" only, never silently mis-filed into a category.
//
// Category mapping (FIXED here, once, for every type the log can hold):
//   CONNECTION : session_asked / session_started / session_ended
//                (the support-session lifecycle)
//   SECURITY   : session_denied / session_cancelled
//                (access refused or withdrawn — the outcomes a
//                privacy-conscious user wants to find in one tap)
//   LOCATION   : sharing_started / sharing_stopped / place_alert
//                (the location-sharing facts)
//
// The read-state transitions are non-destructive by construction: an
// already-read row keeps its ORIGINAL readAt (re-marking never re-stamps
// a newer read time), and the list order is never touched.
// ═══════════════════════════════════════════════════════════════════════

/**
 * G1 item 2+3 — the Recent-events filter chips: All, Unread, and the three
 * event categories. UNREAD is orthogonal to the categories (an unread row
 * of ANY category shows under it).
 */
enum class NotifFilter { ALL, UNREAD, CONNECTION, LOCATION, SECURITY }

/** The three event categories offered as chips. */
enum class NotifCategory { CONNECTION, LOCATION, SECURITY }

/**
 * The category of a logged event type, or null for an unknown/future type
 * (such a row remains visible under "All" and "Unread" — never hidden,
 * never mis-filed).
 */
fun notifCategoryOf(type: String): NotifCategory? = when (type) {
    "session_asked", "session_started", "session_ended" -> NotifCategory.CONNECTION
    "session_denied", "session_cancelled" -> NotifCategory.SECURITY
    "sharing_started", "sharing_stopped", "place_alert" -> NotifCategory.LOCATION
    else -> null
}

/**
 * G1 item 2+3 — the rows one chip shows. Order (newest-first) is always
 * preserved; filtering never re-sorts, so the section never jumps.
 */
fun List<NotifEvent>.applyNotifFilter(filter: NotifFilter): List<NotifEvent> =
    when (filter) {
        NotifFilter.ALL -> this
        NotifFilter.UNREAD -> filter { it.readAt == null }
        NotifFilter.CONNECTION -> filter { notifCategoryOf(it.type) == NotifCategory.CONNECTION }
        NotifFilter.LOCATION -> filter { notifCategoryOf(it.type) == NotifCategory.LOCATION }
        NotifFilter.SECURITY -> filter { notifCategoryOf(it.type) == NotifCategory.SECURITY }
    }

/**
 * G1 item 2 — the read-state transition for ONE row (its tap). An
 * already-read row keeps its original readAt; the id order is untouched.
 */
fun List<NotifEvent>.withOneMarkedRead(id: Long, nowMillis: Long): List<NotifEvent> =
    map { e -> if (e.id == id && e.readAt == null) e.copy(readAt = nowMillis) else e }

/**
 * G1 item 2 — the transition for "Mark all as read": every unread row is
 * stamped [nowMillis]; previously-read rows keep their original stamps.
 */
fun List<NotifEvent>.withAllMarkedRead(nowMillis: Long): List<NotifEvent> =
    map { e -> if (e.readAt == null) e.copy(readAt = nowMillis) else e }

/**
 * G1 item 2 — the rows still awaiting their first read: the bell badge's
 * number and the "Unread" chip's membership, one definition for both.
 */
fun List<NotifEvent>.notifUnreadCount(): Int = count { it.readAt == null }

/**
 * G1 item 4 — the route a row tap opens. These are the DeepLinks router's
 * OWN route strings (AppRoutes), re-used verbatim, so a tap inside the
 * center and a tap on a system notification land in exactly the same place:
 *   session_asked            → home (the consent card at the top of Home —
 *                              the Owner-approved QR model pre-approves
 *                              pairing, so Home is where the answer lives)
 *   session_started / ended /
 *   denied / cancelled       → home/sessions/{arg2} when the stable subject
 *                              id survived (it is the dedupe key), else home
 *   sharing_started / stopped→ tracking
 *   place_alert              → tracking/places
 *   unknown future type      → home (never a dead end)
 */
fun notifRouteFor(type: String, arg2: String?): String = when (type) {
    "session_asked" -> AppRoutes.HOME
    "session_started", "session_ended", "session_denied", "session_cancelled" ->
        if (arg2.isNullOrBlank()) {
            AppRoutes.HOME
        } else {
            AppRoutes.HOME_SESSION_ARG.replace("{id}", arg2)
        }
    "sharing_started", "sharing_stopped" -> AppRoutes.TRACKING
    "place_alert" -> AppRoutes.TRACKING_PLACES
    else -> AppRoutes.HOME
}
