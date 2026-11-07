package com.family.remote.support.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.family.remote.support.AssistSetup
import com.family.remote.support.R
import com.family.remote.support.ScreenShareEngine
import com.family.remote.support.ScreenShareService
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.ShareState
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.localizedMessage
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.TextActionButton
import com.family.remote.support.ui.components.WideOutlinedButton
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * ui/home/SessionViewer.kt — W4 (v0.52.0): the LIVE session surfaces that
 * the W1–W7 rebuild left unwired. The engine (ScreenShareEngine, Phase 7/8)
 * and the foreground service (ScreenShareService) have been complete all
 * along — but NOTHING called startHelper/startRecipient from the rebuilt
 * UI, so "video share and touch control did not work" (the Owner's report)
 * was simply the missing front door. This file is that door:
 *
 *   • [HelperViewerSection]   — home/sessions/{id} while ACTIVE+helper:
 *     "Watch screen" → engine.startHelper → the incoming track renders on
 *     a SurfaceViewRenderer (letterboxed to the real frame aspect); the
 *     transparent overlay converts taps / long-presses / drags into
 *     NORMALIZED coordinates (engine contract: 0..1 of the video rect,
 *     resolution-independent) and sends them as assist commands; the nav
 *     row injects Back / Home / Recents; the type box sends text; data
 *     saver flips the bitrate ceiling; the engine's own status / network /
 *     diagnostics lines render verbatim ("a screenshot IS the logcat").
 *   • [RecipientShareCard]   — Home (right under the live hint) and the
 *     session detail while ACTIVE+recipient: "Share screen" → the system
 *     MediaProjection consent (the one gate Android requires per session)
 *     → ScreenShareService.start(...) → engine.startRecipient inside the
 *     running mediaProjection FGS (Android 14 ordering, already owned by
 *     the service). While sharing: live status, the self-preview surface,
 *     the assist on/off truth, the "helper is tapping but assist is off"
 *     banner with the one-tap setup guide, and Stop sharing.
 *
 * Every string is an existing resource (the old frontend's full ss_/sk_/
 * sw_/td_ kit, translated ×15 locales since v0.28) — zero new keys.
 *
 * Threading/ownership: the engine is a process-singleton StateMachine —
 * these composables only OBSERVE its flows and call its public API, so
 * hosting the cards from two routes can never double-start anything
 * (one route is composed at a time; the state itself is idempotent).
 */
// ═══════════════════════════════════════════════════════════════════

// ── the HELPER side (viewer + touch) ───────────────────────────────────────

/**
 * The helper's live view of the recipient's screen + the touch controls.
 * Embedded by SessionDetailScreen while the session is ACTIVE and the
 * signed-in user is the helper. Stops itself when the session stops
 * being ACTIVE (either side ended / timed out) — engine.stopHelper posts
 * no "bye", the recipient keeps sharing (v0.8.1 contract).
 */
@Composable
fun HelperViewerSection(
    session: SessionView,
    user: FirebaseUser,
    vm: HomeViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engineState by ScreenShareEngine.state.collectAsState()
    val info by ScreenShareEngine.info.collectAsState()
    val netInfo by ScreenShareEngine.netInfo.collectAsState()
    val frameSize by ScreenShareEngine.frameSize.collectAsState()
    val assistStatus by ScreenShareEngine.assistStatus.collectAsState()
    val frameStats by ScreenShareEngine.frameStats.collectAsState()
    val diag by ScreenShareEngine.diag.collectAsState()

    val watching = engineState !in setOf(ShareState.IDLE, ShareState.ENDED)
    var stopAsk by rememberSaveable { mutableStateOf(false) }
    var endBusy by remember { mutableStateOf(false) }
    var endError by remember { mutableStateOf<String?>(null) }

    // The session ended (either side / timeout) → close our viewer once.
    LaunchedEffect(session.id, session.status) {
        if (session.status != "ACTIVE" &&
            ScreenShareEngine.state.value != ShareState.IDLE
        ) {
            ScreenShareEngine.stopHelper()
        }
    }

    SectionCard(
        title = stringResource(R.string.lh_live_title),
        icon = Icons.Filled.Call,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── status line: the engine's own localized words ──
            Text(
                text = info ?: stringResource(R.string.ss_info_preparing_viewer),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (engineState == ShareState.LIVE) {
                Text(
                    text = netInfo ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!watching) {
                // The door: ask the recipient's phone for its screen.
                BigButton(
                    text = stringResource(R.string.btn_watch),
                    onClick = {
                        ScreenShareEngine.startHelper(
                            context = context.applicationContext,
                            sessionIdArg = session.id,
                            uid = user.uid,
                            peerUidArg = session.recipientUid,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.ss_touch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // ── the video surface + the transparent touch overlay ──
                VideoSurfaceWithTouch(frameSize = frameSize)

                // ── per-command feedback ("Tap delivered ✓" / errors) ──
                if (assistStatus != null) {
                    Text(
                        text = assistStatus!!,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // ── nav injection: the three global actions ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("back") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.sk_back)) }
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("home") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.sk_home)) }
                    OutlinedButton(
                        onClick = { ScreenShareEngine.sendAssistNav("recents") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.sk_recents)) }
                }

                // ── the type box (500-char engine budget, honest hint) ──
                TypeBox()

                // ── data saver toggle ──
                var dataSaver by rememberSaveable { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ss_data_saver),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextActionButton(
                        text = stringResource(
                            if (dataSaver) R.string.sg_on else R.string.sg_off
                        ),
                        onClick = {
                            dataSaver = !dataSaver
                            ScreenShareEngine.sendAssistDataSaver(dataSaver)
                        },
                    )
                }
                Text(
                    text = stringResource(
                        if (dataSaver) R.string.ss_ds_on else R.string.ss_ds_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── frame health + the rolling diagnostics log ──
                if (frameStats != null) {
                    Text(
                        text = frameStats!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (diag.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            diag.takeLast(6).forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── stop watching (NO bye — the recipient keeps sharing) ──
                WideOutlinedButton(
                    text = stringResource(R.string.sk_stop),
                    danger = true,
                    onClick = { stopAsk = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── End session — ALWAYS visible while ACTIVE (plan §2.7) ──
            if (endError != null) {
                Text(
                    text = endError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BigButton(
                text = stringResource(R.string.btn_end_session),
                danger = true,
                loading = endBusy,
                onClick = {
                    scope.launch {
                        endBusy = true
                        endError = null
                        try {
                            vm.end(session.id)
                        } catch (e: SessionException) {
                            endError = e.localizedMessage(context)
                        } catch (e: Exception) {
                            endError = describeFirestoreError(context, e)
                        } finally {
                            endBusy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (stopAsk) {
        AlertDialog(
            onDismissRequest = { stopAsk = false },
            title = { Text(stringResource(R.string.sw_title)) },
            text = { Text(stringResource(R.string.sw_body)) },
            confirmButton = {
                TextButton(onClick = {
                    stopAsk = false
                    ScreenShareEngine.stopHelper()
                }) {
                    Text(
                        text = stringResource(R.string.btn_yes_stop),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { stopAsk = false }) {
                    Text(stringResource(R.string.btn_no_continue))
                }
            },
        )
    }
}

/**
 * The black video box: the WebRTC [SurfaceViewRenderer] under a fully
 * transparent Compose overlay that OWNS the gestures (a SurfaceView could
 * otherwise swallow touches). The overlay converts pointer positions into
 * NORMALIZED coordinates inside the letterboxed video rect — the engine's
 * touch-space contract (v0.9.6 #17: the recipient maps 0..1 onto the REAL
 * display, so resolution never leaks). Gestures are armed only while the
 * engine is LIVE (re-keyed pointerInput), so a black/reconnecting box can
 * never fire phantom taps.
 */
@Composable
private fun VideoSurfaceWithTouch(frameSize: Pair<Int, Int>?) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    // Drag bookkeeping: start → last position → real elapsed time (a slow
    // finger drags slowly on their phone too — v0.9.9 contract).
    var dragFrom by remember { mutableStateOf(Offset.Zero) }
    var dragTo by remember { mutableStateOf(Offset.Zero) }
    var dragStartedAt by remember { mutableStateOf(0L) }
    val live = ScreenShareEngine.state.collectAsState().value == ShareState.LIVE

    // The engine's EGL factory is created INSIDE beginHelperWatch (after
    // the ≤5 s TURN/quality fetch) while state already says WAITING_PEER —
    // so the context can still be null the moment this composes. It is
    // created exactly once per process and never released, so a short
    // poll until it exists is race-free; until then the honest black box
    // (with the engine's own status line above) is all there is to see.
    var eglReady by remember { mutableStateOf(ScreenShareEngine.eglBaseContext != null) }
    LaunchedEffect(Unit) {
        while (!eglReady) {
            delay(250)
            eglReady = ScreenShareEngine.eglBaseContext != null
        }
    }

    val aspect = if (frameSize != null && frameSize.first > 0 && frameSize.second > 0) {
        frameSize.first.toFloat() / frameSize.second.toFloat()
    } else {
        9f / 16f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black),
    ) {
        if (eglReady) {
            AndroidView(
                factory = { ctx: Context ->
                    SurfaceViewRenderer(ctx).also { view ->
                        // The engine's shared EGL context — guaranteed
                        // non-null here by the eglReady gate above.
                        ScreenShareEngine.eglBaseContext?.let { view.init(it, null) }
                        view.setEnableHardwareScaler(true)
                        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        renderer = view
                        ScreenShareEngine.attachRenderer(view)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The gesture overlay — ABOVE the SurfaceView, transparent, and the
        // only thing that consumes touches (per-command acks come from the
        // engine's assistStatus line rendered by the section above).
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(live, frameSize) {
                    if (!live) return@pointerInput
                    detectTapGestures(
                        onTap = { off ->
                            val (nx, ny) = normInVideo(off, size, frameSize)
                            ScreenShareEngine.sendAssistTap(nx, ny)
                        },
                        onLongPress = { off ->
                            val (nx, ny) = normInVideo(off, size, frameSize)
                            ScreenShareEngine.sendAssistLongPress(nx, ny)
                        },
                    )
                }
                .pointerInput(live, frameSize) {
                    if (!live) return@pointerInput
                    detectDragGestures(
                        onDragStart = { off ->
                            dragFrom = off
                            dragTo = off
                            dragStartedAt = System.currentTimeMillis()
                        },
                        onDrag = { change, _ ->
                            dragTo = change.position
                        },
                        onDragEnd = {
                            val dur = (System.currentTimeMillis() - dragStartedAt)
                                .coerceIn(80L, 2_000L)
                            val (nx1, ny1) = normInVideo(dragFrom, size, frameSize)
                            val (nx2, ny2) = normInVideo(dragTo, size, frameSize)
                            ScreenShareEngine.sendAssistSwipe(nx1, ny1, nx2, ny2, dur)
                        },
                    )
                },
        )

        // LIVE badge (top-start): never color alone — the word is there.
        if (live) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_fresh_live),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }

    // v0.9.2 #14 discipline: detach BEFORE release, exactly once — the
    // engine's proxy sink then simply has no target (frames keep flowing
    // safely; a released surface can never receive one).
    DisposableEffect(Unit) {
        onDispose {
            ScreenShareEngine.detachRenderer()
            renderer?.release()
            renderer = null
        }
    }
}

/**
 * Pointer offset → normalized (0..1) coordinates inside the letterboxed
 * video rect ([boxSize] = the composable's pixels, [frameSize] = the real
 * decoded frame; before the first frame arrives the box already carries
 * the last-known aspect, so the math degenerates to the full box).
 */
private fun normInVideo(
    off: Offset,
    box: IntSize,
    frameSize: Pair<Int, Int>?,
): Pair<Double, Double> {
    val bw = box.width.toFloat()
    val bh = box.height.toFloat()
    if (bw <= 0f || bh <= 0f) return 0.5 to 0.5
    val videoAspect = if (frameSize != null && frameSize.first > 0 && frameSize.second > 0) {
        frameSize.first.toFloat() / frameSize.second.toFloat()
    } else {
        bw / bh
    }
    var w = bw
    var h = bh
    var ox = 0f
    var oy = 0f
    val boxAspect = bw / bh
    if (boxAspect > videoAspect) {
        w = bh * videoAspect
        ox = (bw - w) / 2f
    } else if (boxAspect < videoAspect) {
        h = bw / videoAspect
        oy = (bh - h) / 2f
    }
    val nx = ((off.x - ox) / w).coerceIn(0f, 1f)
    val ny = ((off.y - oy) / h).coerceIn(0f, 1f)
    return nx.toDouble() to ny.toDouble()
}

/** The type box: send text into the recipient's focused editable field. */
@Composable
private fun TypeBox() {
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.td_title),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            placeholder = { Text(stringResource(R.string.td_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.td_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WideOutlinedButton(
            text = stringResource(R.string.btn_send),
            enabled = text.isNotBlank(),
            onClick = {
                ScreenShareEngine.sendAssistText(text)
                text = ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── the RECIPIENT side (share this screen) ────────────────────────────────

/**
 * The recipient's share card: the MediaProjection consent (Android's
 * per-session, un-pre-grantable gate — the wizard says so honestly),
 * then the service+engine start, then the live status with the honest
 * self-preview, assist truth, the dropped-taps banner, and Stop sharing.
 * Auto-stops when the session stops being ACTIVE.
 */
@Composable
fun RecipientShareCard(session: SessionView, user: FirebaseUser) {
    val context = LocalContext.current

    val engineState by ScreenShareEngine.state.collectAsState()
    val info by ScreenShareEngine.info.collectAsState()
    val frameStats by ScreenShareEngine.frameStats.collectAsState()
    val assistDropped by ScreenShareEngine.assistDropped.collectAsState()

    val sharing = engineState !in setOf(ShareState.IDLE, ShareState.ENDED)

    // Assist truth refreshes the moment the user returns from Settings.
    var assistReady by remember { mutableStateOf(AssistSetup.isAssistEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                assistReady = AssistSetup.isAssistEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The ONE gate: the system projection consent, launched from the
    // button (user-initiated, exactly as Android requires).
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenShareService.start(
                context = context.applicationContext,
                sessionId = session.id,
                uid = user.uid,
                peerUid = session.helperUid,
                resultCode = result.resultCode,
                data = result.data!!,
            )
        }
    }

    // Session ended (either side / timeout) → stop the capture once. The
    // service's onDestroy owns the engine teardown (bye included).
    LaunchedEffect(session.id, session.status) {
        if (session.status != "ACTIVE" &&
            ScreenShareEngine.state.value != ShareState.IDLE
        ) {
            ScreenShareService.stop(context.applicationContext)
        }
    }

    SectionCard(
        title = stringResource(R.string.btn_share_screen),
        icon = Icons.Filled.Call,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.ss_share_warn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!sharing) {
                BigButton(
                    text = stringResource(R.string.btn_share_screen),
                    onClick = {
                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as? MediaProjectionManager ?: return@BigButton
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // ── live sharing status ──
                Text(
                    text = info ?: stringResource(R.string.ss_info_live_share),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // ── the honest self-preview ("this is what they see") ──
                Text(
                    text = stringResource(R.string.ss_preview_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                SelfPreview()

                if (frameStats != null) {
                    Text(
                        text = frameStats!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── assist truth + the one-tap escape ──
                Text(
                    text = stringResource(
                        if (assistReady) R.string.ss_assist_on else R.string.ss_assist_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (assistReady) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!assistReady && assistDropped > 0) {
                    AssistOffBanner()
                }

                BigButton(
                    text = stringResource(R.string.btn_stop_sharing),
                    danger = true,
                    onClick = { ScreenShareService.stop(context.applicationContext) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Small local-preview surface (the sharer's own picture-in-picture).
 * v0.53.0 — module-visible: the full-screen session flow's LIVE window
 * reuses the EXACT same surface (one variant, ever).
 */
@Composable
internal fun SelfPreview() {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    // Same EGL-readiness gate as the helper's viewer (the engine creates
    // its factory after the ≤5 s config fetch, while state is PREPARING).
    var eglReady by remember { mutableStateOf(ScreenShareEngine.eglBaseContext != null) }
    LaunchedEffect(Unit) {
        while (!eglReady) {
            delay(250)
            eglReady = ScreenShareEngine.eglBaseContext != null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        if (eglReady) {
            AndroidView(
                factory = { ctx: Context ->
                    SurfaceViewRenderer(ctx).also { view ->
                        ScreenShareEngine.eglBaseContext?.let { view.init(it, null) }
                        view.setEnableHardwareScaler(true)
                        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        renderer = view
                        ScreenShareEngine.attachLocalPreview(view)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            ScreenShareEngine.detachLocalPreview()
            renderer?.release()
            renderer = null
        }
    }
}

/**
 * The v0.9.5 banner: the helper is tapping RIGHT NOW and nothing lands.
 * One tap opens the step-by-step Bengali guide (AssistSetupGuide — the
 * SAME dialog the permission wizard uses; one variant, ever).
 */
@Composable
private fun AssistOffBanner() {
    var guideOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ss_tap_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            BigButton(
                text = stringResource(R.string.btn_turn_on_assist),
                onClick = { guideOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.ss_setup_once),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    if (guideOpen) {
        com.family.remote.support.AssistSetupGuide(onDismiss = { guideOpen = false })
    }
}
