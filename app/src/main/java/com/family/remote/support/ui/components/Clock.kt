package com.family.remote.support.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════
// M8 (v0.34.0) — TickingBlock, the shared per-second clock (agent-ownership
// "ui/components/Clock.kt", FRONTEND_ARCHITECTURE §6).
//
// Recorded perf issue: six screens (HomeTab consent card + dialog,
// ActivityScreens list + detail, AddDeviceTab invite + session cards) held
// `var nowMillis by remember { ... }` + `LaunchedEffect { while (true) {
// nowMillis = …; delay(1000) } }` at SCREEN level — so the WHOLE screen
// recomposed every second while a countdown was visible. TickingBlock owns
// the clock internally and hands the fresh timestamp only to its [content],
// so everything outside the block stops recomposing per second. Same shape
// the presence badges (Badges.kt) already use, promoted into a component.
//
// Reduced-motion neutrality (the M4 nested-route motion rule): a one-second
// TEXT refresh is not an animation — the system Animator duration scale does
// not apply to it, and nothing here animates; the block simply re-resolves a
// number. Users who disabled animations keep live countdowns.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Re-composes [content] once per second with the current wall-clock time.
 *
 * @param key restarts the clock (and resets the timestamp) when the identity
 *           of the thing being counted down changes — session id, invite id,
 *           … — mirroring the old `LaunchedEffect(session.id)` keys so a new
 *           countdown never starts from a stale instant.
 * @param content the time-dependent block; receives `nowMillis` and should
 *           stay as small as possible (wrap ONLY the composable(s) that read
 *           the time — siblings must keep their one-second peace).
 */
@Composable
fun TickingBlock(key: Any? = null, content: @Composable (nowMillis: Long) -> Unit) {
    var nowMillis by remember(key) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(key) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    content(nowMillis)
}
