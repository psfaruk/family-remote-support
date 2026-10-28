package com.family.remote.support

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Phase 8 (v0.9.0) — REMOTE ASSIST.
 *
 * This is the service that turns "the helper can SEE the screen" (Phase 7)
 * into "the helper can TAP on the screen" (Phase 8). It is an Android
 * accessibility service whose ONLY capability is performing synthetic
 * gestures (taps) and the three global navigation actions (Back, Home,
 * Recents) — deliberately nothing else.
 *
 * Trust model (documented for the Owner, enforced by Android + the app):
 *  - The GRANTOR must switch this service on themselves, once, in system
 *    Settings → Accessibility ("Family Remote Support assist"). Android shows
 *    a warning screen before allowing it; the app also explains what it does
 *    and offers the settings page from the share card. Turning it off at any
 *    time instantly makes the helper view-only again.
 *  - canRetrieveWindowContent is FALSE (see res/xml/assist_service_config.xml)
 *    — this service READS NOTHING from the screen. It cannot see passwords,
 *    messages or anything else; it can only press (it is a "finger", not an
 *    "eye").
 *  - Commands arrive through the same Firestore signal queue as the video
 *    signaling: only the other participant of an ACTIVE support session can
 *    write there (server-enforced by the rules), and every command must carry
 *    the CURRENT negotiation epoch, which only a live helper knows. When the
 *    session ends, the engine stops applying commands automatically.
 *
 * The system may start this service only after the user enables it; it stays
 * registered in [Companion.instance] while connected, so the engine can ask
 * "is assist available?" and perform gestures without any binding calls.
 */
class AssistService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    // No event handling by design — this service is a pure gesture injector.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Inject one tap at normalized (0..1) screen coordinates. */
    private fun tapNormalized(nx: Double, ny: Double): Boolean {
        val m = resources.displayMetrics
        val x = (nx.coerceIn(0.0, 1.0) * m.widthPixels).toFloat()
        val y = (ny.coerceIn(0.0, 1.0) * m.heightPixels).toFloat()
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    companion object {
        @Volatile
        private var instance: AssistService? = null

        /** True while the grantor has enabled the service in system settings. */
        val isReady: Boolean get() = instance != null

        fun tapNormalized(nx: Double, ny: Double): Boolean =
            instance?.tapNormalized(nx, ny) ?: false

        fun pressBack(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false

        fun pressHome(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false

        fun pressRecents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }
}
