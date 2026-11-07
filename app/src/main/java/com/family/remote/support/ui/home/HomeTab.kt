package com.family.remote.support.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.Nicknames
import com.family.remote.support.PermChecklist
import com.family.remote.support.PresenceView
import com.family.remote.support.R
import com.family.remote.support.REL_KIND_GUEST
import com.family.remote.support.RelationshipView
import com.family.remote.support.ServerClock
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.readPermChecklist
import com.family.remote.support.localizedMessage
import com.family.remote.support.ui.LocalWarningColor
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.Fmt
import com.family.remote.support.ui.components.IconStatusTile
import com.family.remote.support.ui.components.KindChip
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.QuickActionTile
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.StatusTone
import com.family.remote.support.ui.components.TextActionButton
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.components.appLocale
import com.family.remote.support.ui.components.presenceMeta
import com.family.remote.support.ui.components.relTime
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.components.timeGreetingRes
import com.family.remote.support.ui.nav.AppRoutes
import com.family.remote.support.ScreenShareEngine
import com.family.remote.support.ShareState
import com.google.firebase.auth.FirebaseUser
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * ui/home/HomeTab.kt — the Home dashboard (W2; FRONTEND_UI_PLAN_BN §2.2,
 * rebuilt 1:1 from the interactive prototype).
 *
 * Order (top → bottom): greeting + signed-in line → consent card (an
 * incoming REQUESTED ask — the hero, per the plan) → live hint / awaiting
 * card → the 2×2 status grid → the two quick actions → linked phones
 * (top 3) → session activity (open + just-ended). Every countdown ticks
 * ONLY inside its own card (M8 ticking-block isolation), every deadline
 * compares against ServerClock (audit P7), every string is a resource.
 */
// ═══════════════════════════════════════════════════════════════════

@Composable
fun HomeTab(
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current

    // Local device facts — cheap reads, refreshed on every ON_RESUME
    // (audit P12: RESUMED-only permission polling; also covers returning
    // from system settings and from the Tracking tab's sharing sheet).
    var perms by remember { mutableStateOf(readPermChecklist(context)) }
    var sharingOn by remember { mutableStateOf(isSharingEnabledCompat(context)) }
    var wizardOpen by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = readPermChecklist(context)
                sharingOn = isSharingEnabledCompat(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val incomingAsk = ui.sessions.firstOrNull {
        it.status == "REQUESTED" && it.recipientUid == user.uid
    }
    val outgoingAsk = ui.sessions.firstOrNull {
        it.status == "REQUESTED" && it.helperUid == user.uid
    }
    val active = ui.sessions.firstOrNull { it.status == "ACTIVE" }

    // v0.53.0 — the engine's own truth decides which recipient surface
    // Home hosts: the flow LAUNCHER (not sharing yet) or the live card.
    val engineState by ScreenShareEngine.state.collectAsState()
    val sharingNow = engineState !in setOf(ShareState.IDLE, ShareState.ENDED)

    when {
        ui.loading && ui.rels.isEmpty() && ui.sessions.isEmpty() ->
            LoadingSkeleton(rows = 4, modifier = Modifier.fillMaxSize())
        ui.error != null && ui.rels.isEmpty() && ui.sessions.isEmpty() ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                ErrorPanel(
                    message = describeFirestoreError(context, ui.error!!),
                    onRetry = vm::retry,
                )
            }
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── greeting (§92: time-aware) ──
            val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val who = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email ?: stringResource(R.string.home_not_signed_in)
            Text(
                text = stringResource(timeGreetingRes(hour)) + ", " + who,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.home_signed_in_as, user.email ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── non-fatal listener error (data still on screen) ──
            ui.error?.let {
                ErrorPanel(
                    message = describeFirestoreError(context, it),
                    onRetry = vm::retry,
                )
            }

            // ── the consent hero ──
            // v0.53.0 — Allow no longer grants in-place: it opens the
            // SEQUENTIAL FULL-SCREEN flow (consent → each missing permission
            // as its own window → share → live). The card stays as the
            // compact preview of the ask; Deny remains a one-tap refusal.
            if (incomingAsk != null) ConsentCard(incomingAsk, vm, onOpenRoute)

            // ── live session hint / awaiting own ask ──
            if (active != null) {
                LiveHintCard(active, user.uid, vm, onOpenRoute)
                // W4 (v0.52.0): the recipient's share surface — in the SAME
                // tab/screen where they allowed the ask (the Owner's per-tab
                // sequence directive); the helper's viewer lives in the
                // session detail the live card opens.
                // v0.53.0 — while NOT yet sharing, the entry is the flow
                // LAUNCHER (one button → the state machine re-enters exactly
                // where it stands, granted windows skipped); once sharing,
                // the live status card renders as before.
                if (active.recipientUid == user.uid) {
                    if (sharingNow) {
                        RecipientShareCard(session = active, user = user)
                    } else {
                        SessionFlowLauncher(
                            onOpen = { onOpenRoute(AppRoutes.sessionFlow(active.id)) },
                        )
                    }
                }
            } else if (outgoingAsk != null) {
                AwaitingCard(outgoingAsk, vm)
            }

            // ── 2×2 status grid ──
            StatusGridCard(
                perms = perms,
                sharingOn = sharingOn,
                rels = ui.rels,
                myUid = user.uid,
                onOpenFamily = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                onOpenTracking = { onOpenRoute(AppRoutes.TRACKING) },
                onOpenWizard = { wizardOpen = true },
            )

            // ── quick actions ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionTile(
                    icon = Icons.Filled.Call,
                    title = stringResource(R.string.qa_get_help),
                    hint = stringResource(R.string.qa_get_help_h),
                    onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                    modifier = Modifier.weight(1f),
                )
                QuickActionTile(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.qa_give_help),
                    hint = stringResource(R.string.qa_give_help_h),
                    onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── linked phones (top 3) ──
            DevicesCard(user.uid, ui, onOpenRoute)

            // ── session activity ──
            ActivityCard(user.uid, ui, onOpenRoute)
        }
    }

    if (wizardOpen) {
        com.family.remote.support.PermissionWizard(
            visible = true,
            onDismiss = { wizardOpen = false },
        )
    }
}

/** Locale-proof indirection (kept tiny so the intent stays obvious). */
private fun isSharingEnabledCompat(context: android.content.Context): Boolean =
    LocationSharingPrefs.isSharingEnabled(context)

// ── ConsentCard ───────────────────────────────────────────────────────────

/**
 * The incoming REQUESTED ask — this card IS the consent hero (the event
 * banner deliberately excludes session_asked). 1-second countdown chip,
 * honest error row, [btn_deny] at ≥48 dp; v0.53.0 — the ALLOW action is
 * [sf_consent_open] and it OPENS the full-screen sequential flow (the
 * grant itself happens in the flow's window 1, one place, ever).
 */
@Composable
private fun ConsentCard(session: SessionView, vm: HomeViewModel, onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()
    val now = tickingNow() // 1 s — the answer window
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val helperName =
        Nicknames.displayName(context, session.relId, session.helperEmail, session.helperEmail)
    val left = session.respondDeadlineMillis - now

    // Past the deadline (+2 s grace): the honest lazy EXPIRED write, once.
    if (left < -2_000L) {
        LaunchedEffect(session.id) { vm.expireIfStale(session) }
    }

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

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cons_body, helperName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = session.helperEmail +
                            (if (session.recipientDeviceModel.isNotBlank()) {
                                " · " + session.recipientDeviceModel
                            } else {
                                ""
                            }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = LocalWarningColor.current.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "⏳ " + Fmt.mmss(left, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalWarningColor.current,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.cons_expiry, Fmt.mmss(left, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )
            if (session.kind == REL_KIND_GUEST) {
                Text(
                    text = stringResource(R.string.cons_guest_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    text = stringResource(R.string.sf_consent_open),
                    onClick = {
                        onOpenRoute(AppRoutes.sessionFlow(session.id))
                    },
                    modifier = Modifier.weight(1f),
                )
                WideOutlinedButton(
                    text = stringResource(R.string.btn_deny),
                    onClick = { act { vm.deny(session.id) } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── LiveHintCard / AwaitingCard ───────────────────────────────────────────

/** An ACTIVE session, either role: what is happening + how to get back/end. */
@Composable
private fun LiveHintCard(
    session: SessionView,
    myUid: String,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()
    val now = tickingNow() // 1 s — the 15-min TTL
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isHelper = session.helperUid == myUid
    val counterpartEmail = if (isHelper) session.recipientEmail else session.helperEmail
    val counterpartName =
        Nicknames.displayName(context, session.relId, counterpartEmail, counterpartEmail)
    val left = session.sessionEndsAtMillis - now

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    text = if (isHelper) {
                        stringResource(R.string.lh_live_title)
                    } else {
                        stringResource(R.string.feed_active_mine, counterpartName)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.lh_ends_at,
                        Fmt.clockTime(session.sessionEndsAtMillis, locale),
                        Fmt.mmss(left, locale),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = if (isHelper) {
                    stringResource(R.string.lh_helper_note, counterpartName, session.recipientDeviceModel)
                } else {
                    stringResource(R.string.lh_recipient_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isHelper) {
                    BigButton(
                        text = stringResource(R.string.btn_return_session),
                        onClick = {
                            // W2: the live card on Home is the session's home;
                            // W4 replaces this with the full-screen viewer.
                            onOpenRoute(AppRoutes.homeSession(session.id))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                WideOutlinedButton(
                    text = stringResource(R.string.btn_end_session),
                    danger = true,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                vm.end(session.id)
                            } catch (e: SessionException) {
                                error = e.localizedMessage(context)
                            } catch (e: Exception) {
                                error = describeFirestoreError(context, e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The helper's own unanswered ask: countdown + the take-back action. */
@Composable
private fun AwaitingCard(session: SessionView, vm: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = appLocale()
    val now = tickingNow()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val recipientEmail = session.recipientEmail
    val name = Nicknames.displayName(context, session.relId, recipientEmail, recipientEmail)
    val left = session.respondDeadlineMillis - now
    if (left < -2_000L) {
        LaunchedEffect(session.id) { vm.expireIfStale(session) }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = LocalWarningColor.current.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, LocalWarningColor.current.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.aw_title, name, Fmt.mmss(left, locale)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.aw_sent_at,
                    Fmt.clockTime(session.requestedAtMillis, locale),
                ) + " · " + stringResource(R.string.aw_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextActionButton(
                text = stringResource(R.string.btn_cancel_request),
                error = true,
                onClick = {
                    if (busy) return@TextActionButton
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            vm.cancelAsk(session.id)
                        } catch (e: SessionException) {
                            error = e.localizedMessage(context)
                        } catch (e: Exception) {
                            error = describeFirestoreError(context, e)
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

// ── Status grid ───────────────────────────────────────────────────────────

/** The 2×2 grid (1×4 under 360 dp): setup · linked · share · security. */
@Composable
private fun StatusGridCard(
    perms: PermChecklist,
    sharingOn: Boolean,
    rels: List<RelationshipView>,
    myUid: String,
    onOpenFamily: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenWizard: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_features),
        icon = Icons.Filled.Phone,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tileSetup: @Composable androidx.compose.foundation.layout.RowScope.(Modifier) -> Unit = { m ->
                IconStatusTile(
                    icon = if (perms.allDone) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    label = stringResource(R.string.sg_setup),
                    value = if (perms.allDone) {
                        stringResource(R.string.sg_setup_done)
                    } else {
                        stringResource(R.string.sg_setup_count, perms.doneCount)
                    },
                    tone = if (perms.allDone) StatusTone.SUCCESS else StatusTone.WARNING,
                    onClick = onOpenWizard,
                    modifier = m,
                )
            }
            val tileLinked: @Composable androidx.compose.foundation.layout.RowScope.(Modifier) -> Unit = { m ->
                IconStatusTile(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.devices_title),
                    value = stringResource(R.string.sg_linked_count, activeRelsOf(rels, myUid).size),
                    tone = if (activeRelsOf(rels, myUid).isEmpty()) StatusTone.NEUTRAL else StatusTone.SUCCESS,
                    onClick = onOpenFamily,
                    modifier = m,
                )
            }
            val tileShare: @Composable (Modifier) -> Unit = { m ->
                IconStatusTile(
                    icon = Icons.Filled.LocationOn,
                    label = stringResource(R.string.sg_share),
                    value = stringResource(
                        if (sharingOn) R.string.sg_on else R.string.sg_off
                    ),
                    tone = if (sharingOn) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                    onClick = onOpenTracking,
                    modifier = m,
                )
            }
            val tileSecurity: @Composable (Modifier) -> Unit = { m ->
                SecurityTile(rels, myUid, onOpenFamily, m)
            }

            if (maxWidth >= 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        tileSetup(Modifier.weight(1f))
                        tileLinked(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        tileShare(Modifier.weight(1f))
                        tileSecurity(Modifier.weight(1f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { tileSetup(Modifier.fillMaxWidth()) }
                    Row { tileLinked(Modifier.fillMaxWidth()) }
                    Row { tileShare(Modifier.fillMaxWidth()) }
                    Row { tileSecurity(Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/**
 * The security tile owns its own 30 s tick (guest windows move slowly) —
 * recomposition stays inside this tile, not the whole grid.
 */
@Composable
private fun SecurityTile(
    rels: List<RelationshipView>,
    myUid: String,
    onOpenFamily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = tickingNow(30_000L)
    val guestSoon = activeRelsOf(rels, myUid).any {
        it.kind == REL_KIND_GUEST && it.guestExpiresAtMillis in 1..(now + 15 * 60_000L)
    }
    IconStatusTile(
        icon = if (guestSoon) Icons.Filled.Warning else Icons.Filled.Lock,
        label = stringResource(R.string.sg_security),
        value = stringResource(
            if (guestSoon) R.string.sg_guest_soon else R.string.sg_all_ok
        ),
        tone = if (guestSoon) StatusTone.WARNING else StatusTone.SUCCESS,
        onClick = onOpenFamily,
        modifier = modifier,
    )
}

private fun activeRelsOf(rels: List<RelationshipView>, myUid: String): List<RelationshipView> =
    rels.filter { it.status == "ACTIVE" && it.guestLive }

// ── Linked phones ─────────────────────────────────────────────────────────

/** The dashboard's linked-phones card: top-3 rows + the see-all action. */
@Composable
private fun DevicesCard(
    myUid: String,
    ui: HomeViewModel.UiState,
    onOpenRoute: (String) -> Unit,
) {
    val active = activeRelsOf(ui.rels, myUid)
    SectionCard(
        title = stringResource(R.string.devices_title),
        icon = Icons.Filled.Person,
        infoText = stringResource(R.string.devices_info),
        trailing = if (active.size > 3) {
            {
                TextActionButton(
                    text = stringResource(R.string.home_see_all),
                    onClick = { onOpenRoute(AppRoutes.HOME_FAMILY) },
                )
            }
        } else {
            null
        },
    ) {
        if (active.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Phone,
                title = stringResource(R.string.devices_empty_title),
                body = stringResource(R.string.devices_empty_hint),
                actionLabel = stringResource(R.string.devices_add_cta),
                onAction = { onOpenRoute(AppRoutes.ADD) },
            )
        } else {
            Column {
                active.take(3).forEach { rel ->
                    RelRow(myUid, rel, ui.presence[rel.id], onOpenRoute)
                }
            }
        }
    }
}

/** One linked-phone row: avatar, name, presence label, kind chip. */
@Composable
internal fun RelRow(
    myUid: String,
    rel: RelationshipView,
    presence: PresenceView?,
    onOpenRoute: (String) -> Unit,
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    // Guest windows need a slow tick; family rows never re-tick.
    val now = if (rel.kind == REL_KIND_GUEST) {
        tickingNow(30_000L)
    } else {
        remember { ServerClock.now() }
    }
    val counterpart = if (rel.helperUid == myUid) rel.recipientEmail else rel.helperEmail
    val name = Nicknames.displayName(context, rel.id, counterpart, counterpart)
    val presenceLabel = presenceMeta(presence, now).second
    AppListTile(
        avatarText = name,
        title = name,
        subtitle = presenceLabel,
        onClick = { onOpenRoute(AppRoutes.homeFamily(rel.id)) },
        trailing = {
            KindChip(
                kind = rel.kind,
                guestExpiresAtMillis = rel.guestExpiresAtMillis,
                now = now,
            )
        },
    )
}

// ── Session activity ──────────────────────────────────────────────────────

/** Open + just-ended sessions — proven facts only (feed_info). */
@Composable
internal fun ActivityCard(
    myUid: String,
    ui: HomeViewModel.UiState,
    onOpenRoute: (String) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.feed_title),
        icon = Icons.Filled.List,
        infoText = stringResource(R.string.feed_info),
        trailing = {
            TextActionButton(
                text = stringResource(R.string.home_see_all),
                onClick = { onOpenRoute(AppRoutes.HOME_SESSIONS) },
            )
        },
    ) {
        if (ui.sessions.isEmpty() && ui.finished.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.List,
                title = stringResource(R.string.feed_empty_title),
                body = stringResource(R.string.feed_empty_hint),
            )
        } else {
            Column {
                ui.sessions.forEach { session ->
                    OpenSessionRow(myUid, session, onOpenRoute)
                }
                if (ui.finished.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.feed_last_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    ui.finished.take(3).forEach { session ->
                        FinishedSessionRow(myUid, session, onOpenRoute)
                    }
                }
            }
        }
    }
}

@Composable
internal fun OpenSessionRow(
    myUid: String,
    session: SessionView,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val locale = appLocale()
    val counterpartEmail =
        if (session.helperUid == myUid) session.recipientEmail else session.helperEmail
    val name = Nicknames.displayName(context, session.relId, counterpartEmail, counterpartEmail)
    val isHelper = session.helperUid == myUid
    // Rows with a live deadline tick at 1 s; ACTIVE-with-viewer rows do not.
    val now = if (session.status == "REQUESTED") tickingNow() else remember { ServerClock.now() }

    val title = when {
        session.status == "ACTIVE" && isHelper ->
            stringResource(R.string.feed_active_theirs, name)
        session.status == "ACTIVE" ->
            stringResource(R.string.feed_active_mine, name)
        !isHelper -> stringResource(R.string.feed_req_received, name)
        else -> stringResource(R.string.feed_req_sent, name)
    }
    val subtitle = when {
        session.status == "ACTIVE" ->
            // v0.52.0: feed_in_progress said "video in the 'Add Device' tab"
            // — a stale pointer from the pre-rebuild navigation; the live
            // view is the session detail this very row opens. Composed from
            // existing localized keys (sess_ends_at + clock), zero new strings.
            stringResource(R.string.sess_ends_at) + " " +
                Fmt.clockTime(session.sessionEndsAtMillis, locale)
        !isHelper -> stringResource(
            R.string.feed_need_decision,
            Fmt.mmss(session.respondDeadlineMillis - now, locale),
        )
        else -> stringResource(
            R.string.feed_awaiting,
            Fmt.mmss(session.respondDeadlineMillis - now, locale),
        )
    }
    AppListTile(
        avatarText = name,
        title = title,
        subtitle = subtitle,
        onClick = { onOpenRoute(AppRoutes.homeSession(session.id)) },
        trailing = { StatusBadge(session.status) },
    )
}

@Composable
internal fun FinishedSessionRow(
    myUid: String,
    session: SessionView,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val locale = appLocale()
    val counterpartEmail =
        if (session.helperUid == myUid) session.recipientEmail else session.helperEmail
    val name = Nicknames.displayName(context, session.relId, counterpartEmail, counterpartEmail)
    val isHelper = session.helperUid == myUid
    val now = remember { ServerClock.now() }
    // DENIED/CANCELLED never got a sessionEndsAt — the deadline is when
    // they resolved; render THAT, never a 1970 timestamp.
    val resolvedAt =
        if (session.sessionEndsAtMillis > 0) session.sessionEndsAtMillis
        else session.respondDeadlineMillis

    val enderSuffix = when (session.endedBy) {
        "timeout" -> stringResource(R.string.ended_auto)
        session.helperUid -> stringResource(R.string.ended_by_helper)
        session.recipientUid -> stringResource(R.string.ended_by_recipient)
        else -> ""
    }
    AppListTile(
        avatarText = name,
        title = stringResource(
            R.string.feed_last_sub,
            name,
            stringResource(if (isHelper) R.string.sess_you else R.string.sess_this_phone),
        ),
        subtitle = Fmt.clockTime(resolvedAt, locale) + " · " + relTime(resolvedAt, now) + enderSuffix,
        onClick = { onOpenRoute(AppRoutes.homeSession(session.id)) },
        trailing = { StatusBadge(session.status) },
    )
}
