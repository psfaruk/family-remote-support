package com.family.remote.support

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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
 *
 * v0.9.9 (Phase 8.3) — the finger grew: SWIPE (one stroke from A to B, the
 * helper's real drag duration is carried along, clamped) and LONG-PRESS
 * (a held stroke at one point) joined the tap.
 *
 * v0.12.0 (Phase 8.3 completion) — the finger learned to TYPE. That one
 * capability needs window content (ACTION_SET_TEXT must find the focused
 * editable field), so canRetrieveWindowContent is now TRUE — with a
 * strictly narrowed purpose, documented in res/xml/assist_service_config.xml
 * and enforced by this file's shape: the ONLY consumer of window content is
 * the "find the focused editable box" search (findEditableInTree), which
 * reads capability flags but never node text, and the service exposes no
 * API that could return screen content to anyone. Nothing seen here is
 * logged, stored or transmitted — the diag log for a typing command
 * records the text LENGTH only, in the engine.
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
        val (x, y) = toRealPixels(nx, ny)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * v0.9.9 (Phase 8.3) — inject one swipe from A to B. [durationMs] is the
     * helper's REAL drag time (clamped): fast flicks scroll, slow drags pull
     * lists gently — the same muscle memory as a local finger.
     */
    private fun swipeNormalized(
        nx1: Double, ny1: Double, nx2: Double, ny2: Double, durationMs: Long
    ): Boolean {
        val (x1, y1) = toRealPixels(nx1, ny1)
        val (x2, y2) = toRealPixels(nx2, ny2)
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(
            path, 0L, durationMs.coerceIn(120L, 3_000L)
        )
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * v0.9.9 (Phase 8.3) — inject one long-press (context menu / selection).
     * A held stroke at one point; 800 ms clears Android's ~500 ms threshold
     * with margin on slow touch screens.
     */
    private fun longPressNormalized(nx: Double, ny: Double, durationMs: Long): Boolean {
        val (x, y) = toRealPixels(nx, ny)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path, 0L, durationMs.coerceIn(400L, 2_000L)
        )
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * v0.12.0 (Phase 8.3 completion) — TYPING. Places [text] into the focused
     * editable field via ACTION_SET_TEXT and reports the exact outcome as
     * "ok" | "assist-off" | "no-field" | "failed" so the engine can ack the
     * truth instead of a shrug.
     *
     * Note: SET_TEXT REPLACES the field's current content (there is no safe
     * append without reading screen text — which this service refuses to
     * do). The helper's typing dialog says so in plain Bengali.
     */
    fun typeTextStatus(text: String): String {
        if (text.isBlank()) return "failed"
        val target = findTypingTarget() ?: return "no-field"
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return try {
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                "ok"
            } else {
                "failed"
            }
        } catch (_: Exception) {
            "failed"
        }
    }

    /**
     * Where should the helper's text land? Fast path: the active window
     * (usually the app the recipient is in). But when the remote keyboard
     * is open the IME may BE the active window — and an IME has no editable
     * field — so the fallback walks every retrievable window, application
     * windows first, before honestly giving up ("no-field").
     */
    private fun findTypingTarget(): AccessibilityNodeInfo? {
        try {
            rootInActiveWindow?.let { root ->
                findEditableInTree(root, 400)?.let { return it }
            }
        } catch (_: Exception) {
        }
        val windows = try {
            this.windows
        } catch (_: Exception) {
            null
        } ?: return null
        val ordered = try {
            windows.sortedByDescending {
                try {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            return null
        }
        for (w in ordered) {
            try {
                w.root?.let { root ->
                    findEditableInTree(root, 400)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Depth-first search for the best target: a FOCUSED editable field
     * first (that is where the user just tapped), else the first editable
     * field anywhere in the tree. Capability flags only — node text is
     * never read. [budget] bounds the walk: accessibility trees can be
     * enormous and every node costs an IPC hop, so a bounded traversal
     * keeps the typing command inside the ack-latency budget and can never
     * hang the gesture thread.
     */
    private fun findEditableInTree(
        root: AccessibilityNodeInfo,
        budget: Int
    ): AccessibilityNodeInfo? {
        var firstEditable: AccessibilityNodeInfo? = null
        var visited = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty() && visited < budget) {
            val node = try {
                stack.removeLast()
            } catch (_: Exception) {
                continue
            }
            visited++
            val editable = try {
                node.isEditable
            } catch (_: Exception) {
                false
            }
            val focused = try {
                node.isFocused
            } catch (_: Exception) {
                false
            }
            if (editable && focused) return node
            if (editable && firstEditable == null) firstEditable = node
            try {
                for (i in 0 until node.childCount) {
                    try {
                        node.getChild(i)?.let { stack.addLast(it) }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
        return firstEditable
    }

    /** Normalized (0..1) of the REAL display → pixel point (v0.9.6 mapping). */
    private fun toRealPixels(nx: Double, ny: Double): Pair<Float, Float> {
        val size = realDisplaySize()
            ?: resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        val x = (nx.coerceIn(0.0, 1.0) * size.first).toFloat()
        val y = (ny.coerceIn(0.0, 1.0) * size.second).toFloat()
        return x to y
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

        /** v0.9.9 — one swipe A→B over [durationMs] (the helper's real drag time). */
        fun swipeNormalized(
            nx1: Double, ny1: Double, nx2: Double, ny2: Double, durationMs: Long
        ): Boolean =
            instance?.swipeNormalized(nx1, ny1, nx2, ny2, durationMs) ?: false

        /** v0.9.9 — one long-press (held stroke) at the point. */
        fun longPressNormalized(nx: Double, ny: Double, durationMs: Long): Boolean =
            instance?.longPressNormalized(nx, ny, durationMs) ?: false

        /**
         * v0.12.0 (Phase 8.3 completion) — place text into the focused
         * editable field. Returns "ok" | "assist-off" | "no-field" |
         * "failed" — the engine acks the exact outcome so the helper
         * always knows what happened (and what to do next).
         */
        fun typeTextStatus(text: String): String =
            instance?.typeTextStatus(text) ?: "assist-off"

        fun pressBack(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false

        fun pressHome(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false

        fun pressRecents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }
}
