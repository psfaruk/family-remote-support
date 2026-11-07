package com.family.remote.support.ui.home

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.family.remote.support.AssistSetup
import com.family.remote.support.Nicknames
import com.family.remote.support.R
import com.family.remote.support.ScreenShareEngine
import com.family.remote.support.ScreenShareService
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.ShareState
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.getSession
import com.family.remote.support.isNotifGranted
import com.family.remote.support.localizedMessage
import com.family.remote.support.readPermChecklist
import com.family.remote.support.requestBatteryExemption
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.Fmt
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.tickingNow
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ui/home/SessionFlowScreen.kt — v0.53.0 (worklog §100): the SEQUENTIAL
 * FULL-SCREEN session flow — the Owner's architecture directive, verbatim:
 *
 *   "আমি চাই প্রত্যেকটি পারমিশন এর জন্য আলাদা আলাদা উইন্ডো থাকবে।
 *    প্রত্যেকটি উইন্ডো স্ক্রিন এ ফিট থাকবে।"
 *   ("I want a separate window for EACH permission. Every window fits
 *    the screen.")
 *
 * What was wrong (the 7 Nov session): the recipient's path was a pile of
 * cards crammed onto Home + a deep link that landed on the session DETAIL
 * screen (which had NO allow button at all) → the user was forced to press
 * Back, find the consent card on page 1, grant, then navigate forward to
 * the share step again. Permissions also surfaced at the WRONG moment
 * (the wizard fired at pairing time / from status cards, not at the step
 * that actually needs them).
 *
 * This screen is one state machine rendered as ONE WINDOW AT A TIME, each
 * window filling the whole screen (the route is immersive — AppNav hides
 * the top and bottom bars). The fixed frame mirrors the Owner's mental
 * model of "pages 1..5":
 *
 *   Window 1  CONSENT     — the ask itself (Allow & set up / Deny)
 *   Window 2  NOTIFICATIONS — only if missing (auto-skipped otherwise)
 *   Window 3  ASSIST      — the touch-control permission, only if missing
 *   Window 4  BATTERY     — only if missing
 *   Window 5  SHARE / LIVE — the projection consent, then the live status
 *
 * Rules that make it feel right:
 *   • A granted permission NEVER shows its window again (auto-skip).
 *   • Each window re-checks its own permission live (ON_RESUME + a 1.2 s
 *     poll while open — the proven PermissionWizard cadence) and advances
 *     BY ITSELF the moment it turns green. The user never navigates back
 *     to re-grant anything; Back/✕ only ever EXITS the flow.
 *   • Exiting mid-flow loses nothing: the session stays ACTIVE and the
 *     Home tab + session detail host a "Continue setup" launcher that
 *     re-enters exactly where the state machine now stands.
 *
 * Threading/ownership: reads [HomeViewModel]'s ONE listener set (same
 * contract as SessionDetailScreen, incl. the cold-deep-link one-shot
 * [getSession] fill); observes the engine's StateFlows (never starts
 * anything itself except through the same public API the cards used).
 */
// ═══════════════════════════════════════════════════════════════════

/** The five fixed windows — the Owner's "pages 1..5". */
private enum class FlowWin(val stepNo: Int) {
    CONSENT(1),
    NOTIF(2),
    ASSIST(3),
    BATTERY(4),
    SHARE(5),
    LIVE(5),
}

/**
 * The flow host. Hosted by AppNav at the immersive route
 * `home/session-flow/{id}`; [onExit] pops back to wherever it was opened
 * from (Home, session detail, or a notification deep link).
 */
@Composable
fun SessionFlowScreen(
    sessionId: String,
    user: FirebaseUser,
    vm: HomeViewModel,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()

    // Live sets first; a cold deep link falls back to a one-shot read
    // (same contract as SessionDetailScreen — a notification tap can land
    // here before any listener ever delivered this id).
    var fetched by remember(sessionId) { mutableStateOf<SessionView?>(null) }
    var fetching by remember(sessionId) { mutableStateOf(false) }
    val session = ui.sessions.firstOrNull { it.id == sessionId }
        ?: ui.finished.firstOrNull { it.id == sessionId }
        ?: fetched
    if (session == null && !fetching) {
        fetching = true
        LaunchedEffect(sessionId) {
            try {
                fetched = getSession(sessionId)
            } catch (_: Exception) {
                fetched = null
            }
        }
    }

    // ── live permission state: ON_RESUME + poll while the flow is open ──
    var perms by remember { mutableStateOf(readPermChecklist(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = readPermChecklist(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            // Binder-backed reads hop to IO (the v0.44.0 wizard pattern).
            perms = withContext(Dispatchers.IO) { readPermChecklist(context) }
            delay(1_200)
        }
    }

    // ── the engine's own truth drives SHARE ↔ LIVE ──
    val engineState by ScreenShareEngine.state.collectAsState()
    val info by ScreenShareEngine.info.collectAsState()
    val frameStats by ScreenShareEngine.frameStats.collectAsState()
    val sharing = engineState !in setOf(ShareState.IDLE, ShareState.ENDED)

    // Assist may be deliberately skipped this round (the honest escape —
    // the helper can still WATCH, and the LIVE window keeps offering it).
    var assistSkipped by rememberSaveable(sessionId) { mutableStateOf(false) }

    // ── the state machine: which window is on screen RIGHT NOW ──
    val status = session?.status
    val win: FlowWin? = when {
        session == null -> null // still loading below
        status == "REQUESTED" -> FlowWin.CONSENT
        status == "ACTIVE" -> when {
            sharing -> FlowWin.LIVE
            !isNotifGranted(context) -> FlowWin.NOTIF
            !perms.assist && !assistSkipped -> FlowWin.ASSIST
            !perms.battery -> FlowWin.BATTERY
            else -> FlowWin.SHARE
        }
        else -> null // DENIED / EXPIRED / ENDED / CANCELLED → exit below
    }

    // The session is gone (answered elsewhere, ended, timed out) → leave
    // the flow once; Home shows the honest follow-up state.
    LaunchedEffect(sessionId, status) {
        if (session != null && status != "REQUESTED" && status != "ACTIVE") {
            onExit()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            session == null && fetching -> FlowChrome(stepNo = null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            session == null || win == null -> FlowChrome(stepNo = null) {
                FlowMessageBody(
                    title = stringResource(R.string.sess_not_found),
                    body = stringResource(R.string.sess_not_found_h),
                ) {
                    BigButton(
                        text = stringResource(R.string.cmn_ok),
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> FlowChrome(
                stepNo = win.stepNo,
                onClose = onExit,
            ) {
                when (win) {
                    FlowWin.CONSENT -> ConsentWindow(
                        session = session,
                        vm = vm,
                        onExit = onExit,
                    )
                    FlowWin.NOTIF -> NotifWindow(
                        onGranted = {
                            // The 1.2 s poll flips the window on its own;
                            // nothing to do here but wait honestly.
                        },
                    )
                    FlowWin.ASSIST -> AssistWindow(
                        onSkip = { assistSkipped = true },
                    )
                    FlowWin.BATTERY -> BatteryWindow()
                    FlowWin.SHARE -> ShareWindow(session = session, user = user)
                    FlowWin.LIVE -> LiveWindow(
                        session = session,
                        vm = vm,
                        info = info,
                        frameStats = frameStats,
                        onExit = onExit,
                    )
                }
            }
        }
    }
}

// ── the flow chrome (step chip + optional close) ──────────────────────────

/**
 * Every window shares this frame: a thin top row with the fixed-position
 * step chip ("Step 3 of 5" — granted windows are simply never shown, the
 * number never lies about where you are) and the ✕ that EXITS the flow
 * (Back is never required to PROCEED — only ever to leave).
 */
@Composable
private fun FlowChrome(
    stepNo: Int?,
    onClose: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (stepNo != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.sf_step_of, stepNo, 5),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cmn_ok),
                    )
                }
            }
        }
        content()
    }
}

/** A centered, scrollable body shared by the simple message windows. */
@Composable
private fun FlowMessageBody(
    title: String,
    body: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions()
            }
        }
    }
}

// ── Window 1: CONSENT — the ask itself, full screen ───────────────────────

/**
 * The incoming REQUESTED ask as its OWN window: who asks, the answer
 * window countdown, the guest note, and the two honest buttons. "Allow &
 * set up" grants AND the state machine walks on to the permission windows
 * — one continuous forward path, never a detour back to page 1.
 */
@Composable
private fun ConsentWindow(
    session: SessionView,
    vm: HomeViewModel,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()
    val now = tickingNow() // 1 s — the answer window

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val helperName =
        Nicknames.displayName(context, session.relId, session.helperEmail, session.helperEmail)
    val left = session.respondDeadlineMillis - now

    fun act(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: SessionException) {
                error = e.localizedMessage(context)
            } catch (e: Exception) {
                error = describeFirestoreError(context, e)
            } finally {
                busy = false
            }
        }
    }

    FlowMessageBody(
        title = stringResource(R.string.cons_label),
        body = stringResource(R.string.cons_body, helperName),
    ) {
        Text(
            text = session.helperEmail +
                (if (session.recipientDeviceModel.isNotBlank()) {
                    " · " + session.recipientDeviceModel
                } else {
                    ""
                }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.cons_expiry, Fmt.mmss(left, locale)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (session.kind == com.family.remote.support.REL_KIND_GUEST) {
            Text(
                text = stringResource(R.string.cons_guest_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        BigButton(
            text = stringResource(R.string.sf_consent_open),
            onClick = { act { vm.allow(session.id) } },
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        WideOutlinedButton(
            text = stringResource(R.string.btn_deny),
            onClick = { act { vm.deny(session.id) } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Window 2: NOTIFICATIONS — own window, only when missing ───────────────

/**
 * Why here and not at pairing time: the ask that JUST arrived is the
 * moment the user understands what notifications are FOR. One tap; the
 * poll sees the grant and the window walks on by itself.
 */
@Composable
private fun NotifWindow(onGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> onGranted() }

    FlowMessageBody(
        title = stringResource(R.string.pw_perm_notif),
        body = stringResource(R.string.pw_perm_notif_desc) + "\n\n" +
            stringResource(R.string.sf_why_now),
    ) {
        BigButton(
            text = stringResource(R.string.pw_allow_btn),
            onClick = {
                // SDK < 33 is granted by definition — this window only
                // exists when isNotifGranted() said otherwise (33+).
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Window 3: ASSIST — the touch-control permission, own window ───────────

/**
 * The accessibility gate as its OWN full-screen window: the shared
 * Bengali step guide (the SAME [com.family.remote.support.AssistSetupGuide]
 * the wizard uses — one variant, ever), the honest skip ("the helper can
 * only watch"), and the auto-advance the moment the user returns from
 * Settings with the service on. NO back-and-forth: the window itself is
 * the place the permission belongs.
 */
@Composable
private fun AssistWindow(onSkip: () -> Unit) {
    val context = LocalContext.current
    var guideOpen by remember { mutableStateOf(false) }

    FlowMessageBody(
        title = stringResource(R.string.pw_perm_assist_title),
        body = stringResource(R.string.pw_perm_assist_desc) + "\n\n" +
            stringResource(R.string.sf_why_now),
    ) {
        BigButton(
            text = stringResource(R.string.btn_turn_on_assist),
            onClick = { guideOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        WideOutlinedButton(
            text = stringResource(R.string.sf_assist_later),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (guideOpen) {
        com.family.remote.support.AssistSetupGuide(
            onDismiss = {
                guideOpen = false
                // Returning from Settings re-checks via ON_RESUME + poll;
                // assist on ⇒ this window is skipped from now on.
                com.family.remote.support.AssistSetup.isAssistEnabled(context)
            },
        )
    }
}

// ── Window 4: BATTERY — own window, only when missing ─────────────────────

/** The heartbeats/alerts exemption — same auto-advance contract. */
@Composable
private fun BatteryWindow() {
    val context = LocalContext.current
    FlowMessageBody(
        title = stringResource(R.string.pw_perm_battery),
        body = stringResource(R.string.pw_perm_battery_desc) + "\n\n" +
            stringResource(R.string.sf_why_now),
    ) {
        BigButton(
            text = stringResource(R.string.pw_allow_btn),
            onClick = { requestBatteryExemption(context) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Window 5a: SHARE — the projection consent, at the step it belongs ─────

/**
 * The one gate Android mandates per session (it can never be
 * pre-granted — the wizard has always said so honestly). It now lives
 * HERE — at window 5, the moment the earlier windows have made it
 * meaningful — not as a button buried mid-scroll on Home.
 */
@Composable
private fun ShareWindow(session: SessionView, user: FirebaseUser) {
    val context = LocalContext.current
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
            // The engine flips to PREPARING → sharing=true → LIVE window.
        }
    }

    FlowMessageBody(
        title = stringResource(R.string.sf_share_title),
        body = stringResource(R.string.ss_share_warn),
    ) {
        BigButton(
            text = stringResource(R.string.btn_share_screen),
            onClick = {
                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as? MediaProjectionManager ?: return@BigButton
                projectionLauncher.launch(mpm.createScreenCaptureIntent())
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Window 5b: LIVE — the live sharing status, full screen ────────────────

/**
 * While sharing: the engine's own status line, the honest self-preview
 * (the SAME [SelfPreview] surface the share card uses), the assist truth
 * with its one-tap fix, the frame stats, Stop sharing (back to window 5a
 * — the state machine does it) and End session.
 */
@Composable
private fun LiveWindow(
    session: SessionView,
    vm: HomeViewModel,
    info: String?,
    frameStats: String?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    val helperName =
        Nicknames.displayName(context, session.relId, session.helperEmail, session.helperEmail)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
            Text(
                text = stringResource(R.string.loc_fresh_live),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            text = stringResource(R.string.feed_active_mine, helperName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = info ?: stringResource(R.string.ss_info_live_share),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.ss_preview_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        SelfPreview()

        if (frameStats != null) {
            Text(
                text = frameStats,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── assist truth + the one-tap escape (always visible here —
        // the ASSIST window was skippable, the truth is not) ──
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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!assistReady) {
            var guideOpen by remember { mutableStateOf(false) }
            WideOutlinedButton(
                text = stringResource(R.string.btn_turn_on_assist),
                onClick = { guideOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
            if (guideOpen) {
                com.family.remote.support.AssistSetupGuide(
                    onDismiss = {
                        guideOpen = false
                        assistReady = AssistSetup.isAssistEnabled(context)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.sf_live_next),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        BigButton(
            text = stringResource(R.string.btn_stop_sharing),
            danger = true,
            onClick = { ScreenShareService.stop(context.applicationContext) },
            modifier = Modifier.fillMaxWidth(),
        )
        WideOutlinedButton(
            text = stringResource(R.string.btn_end_session),
            danger = true,
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        vm.end(session.id)
                        onExit()
                    } catch (e: SessionException) {
                        error = e.localizedMessage(context)
                    } catch (e: Exception) {
                        error = describeFirestoreError(context, e)
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── the launcher card (Home + session detail, when NOT sharing) ───────────

/**
 * v0.53.0 — the re-entry door after the flow was exited (or after an ask
 * was allowed the old way): a compact card whose single button walks back
 * INTO the state machine exactly where it stands — granted windows are
 * skipped, so this usually lands straight on the Share window.
 */
@Composable
fun SessionFlowLauncher(onOpen: () -> Unit) {
    com.family.remote.support.ui.components.SectionCard(
        title = stringResource(R.string.sf_continue_title),
        icon = Icons.Filled.Call,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.sf_continue_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BigButton(
                text = stringResource(R.string.sf_continue_btn),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
