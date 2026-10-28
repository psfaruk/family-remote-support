package com.family.remote.support

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
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
 *
 * v0.9.6 — PRECISION FIX: taps are mapped onto the REAL display size (system
 * bars included). The remote video shows the whole screen — status bar and
 * navigation bar — but resources.displayMetrics only measures the app-window
 * area, which is smaller by exactly those two bars. Scaling the video's
 * normalized coordinates by the app-window height therefore pulled every
 * tap UP the screen by roughly one keyboard row (the 28 Oct report:
 * "pressed h, u fired"). maximumWindowMetrics/getRealSize fixes the map.
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

    /**
     * Inject one tap at normalized (0..1) coordinates of the REAL display
     * (v0.9.6 — the same space the shared video covers, system bars included).
     */
    private fun tapNormalized(nx: Double, ny: Double): Boolean {
        val size = realDisplaySize()
            ?: resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        val x = (nx.coerceIn(0.0, 1.0) * size.first).toFloat()
        val y = (ny.coerceIn(0.0, 1.0) * size.second).toFloat()
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * v0.9.6 — the FULL display size in pixels, exactly what MediaProjection
     * captures. Falls back to the app-window metrics only if the system call
     * fails (then precision degrades to the old behaviour, never crashes).
     */
    @Suppress("DEPRECATION")
    private fun realDisplaySize(): Pair<Int, Int>? {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.maximumWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val p = Point()
                wm.defaultDisplay.getRealSize(p)
                p.x to p.y
            }
        } catch (_: Exception) {
            null
        }
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
