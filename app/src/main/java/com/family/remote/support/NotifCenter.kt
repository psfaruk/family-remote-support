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
//                pairing_invited / pairing_approved
//                presence_online / presence_offline
//                (the support-session lifecycle + who joined/left)
//   SECURITY   : session_denied / session_cancelled
//                family_revoked / guest_removed / rel_revoked
//                guest_expiring_soon / guest_expired
//                (access refused, withdrawn or running out — the outcomes a
//                privacy-conscious user wants to find in one tap)
//   LOCATION   : sharing_started / sharing_stopped / place_alert
//                sharing_perm_lost
//                (the location-sharing facts)
//
// v0.47.0 (audit Gap-1..5 — the event-matrix fill): the pairing lifecycle,
// relationship revocation/expiry, mid-share permission loss and counterpart
// presence transitions joined the fixed map. They are recorded from
// listeners/ticks the app ALREADY runs (zero extra Firestore reads — §75
// quota posture), each carrying a stable subject id as arg2 (relId /
// invitationId / uid_deviceId) so the (type, arg2) 10-minute dedupe holds.
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
    "session_asked", "session_started", "session_ended",
        "pairing_invited", "pairing_approved",
        "presence_online", "presence_offline" -> NotifCategory.CONNECTION
    "session_denied", "session_cancelled",
        "family_revoked", "guest_removed", "rel_revoked",
        "guest_expiring_soon", "guest_expired" -> NotifCategory.SECURITY
    "sharing_started", "sharing_stopped", "place_alert",
        "sharing_perm_lost" -> NotifCategory.LOCATION
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
 *   sharing_perm_lost        → tracking (the sharing controls live there)
 *   place_alert              → tracking/places
 *   pairing_invited /
 *   pairing_approved         → home/family (the pairing/relationship hub)
 *   family_revoked / guest_removed / rel_revoked /
 *   guest_expiring_soon / guest_expired
 *                           → home/family/{arg2} when the relId survived,
 *                              else home/family
 *   presence_online / offline→ home/family/{arg2} likewise (the badge lives
 *                              on the member rows)
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
    "sharing_started", "sharing_stopped", "sharing_perm_lost" -> AppRoutes.TRACKING
    "place_alert" -> AppRoutes.TRACKING_PLACES
    "pairing_invited", "pairing_approved",
        "family_revoked", "guest_removed", "rel_revoked",
        "guest_expiring_soon", "guest_expired",
        "presence_online", "presence_offline" ->
        if (arg2.isNullOrBlank()) {
            AppRoutes.HOME_FAMILY
        } else {
            AppRoutes.HOME_FAMILY_ARG.replace("{relId}", arg2)
        }
    else -> AppRoutes.HOME
}
