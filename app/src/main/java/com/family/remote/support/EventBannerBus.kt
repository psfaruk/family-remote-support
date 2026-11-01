package com.family.remote.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ═══════════════════════════════════════════════════════════════════════
// v0.47.0 (audit §4 proposal — the in-app event banner bus).
//
// The gap this closes: while the app is FOREGROUND, a new event (session
// ended/denied, sharing stopped, place alert, a pairing completed, a guest
// link expiring, a counterpart going offline…) only ever showed inside the
// screen that owns it — the user had to KNOW where to look. This bus is
// the missing attention layer: every freshly-recorded NotificationsLog
// row (except session_asked, which the centered consent dialog already
// owns as the hero) is offered here, and the shell renders ONE quiet,
// auto-dismissing top banner with a "view" tap that routes exactly where
// the notification-center row would (notifRouteFor — one mapping).
//
// Cost: ZERO Firestore reads — it only re-publishes events the log already
// recorded (§75 quota posture). StateFlow, not compose state, so the store
// layer stays UI-free (same pattern as DeepLinks / NotificationsLog).
// ═══════════════════════════════════════════════════════════════════════

/**
 * One banner offer: the event's stable identity + its pre-resolved display
 * name ([arg] — the nickname rule is applied at RECORD time, so the banner
 * never re-derives it) + the route a "view" tap opens.
 */
data class EventBanner(
    /** The NotificationsLog row id — the dismiss/dedupe key. */
    val id: Long,
    /** The event type (NotifEvent.type). */
    val type: String,
    /** Pre-resolved counterpart name (nickname > email > fallback). */
    val arg: String?,
    /** Where "view" lands (notifRouteFor already applied). */
    val route: String,
    /** When the event happened — the host drops stale offers. */
    val atMillis: Long
)

object EventBannerBus {

    private val _current = MutableStateFlow<EventBanner?>(null)

    /** The pending banner offer, or null. The shell renders at most ONE. */
    val current: StateFlow<EventBanner?> = _current.asStateFlow()

    /**
     * Offer a banner for a FRESHLY recorded event. The newest offer wins
     * (a second event within the display window simply replaces the first —
     * banners are an attention hint, not a queue the user must drain).
     */
    fun offer(banner: EventBanner) {
        _current.value = banner
    }

    /** The user dismissed it, tapped it, or the auto-dismiss fired. */
    fun dismiss() {
        _current.value = null
    }
}
