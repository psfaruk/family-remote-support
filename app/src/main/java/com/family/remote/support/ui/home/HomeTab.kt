package com.family.remote.support.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.BuildConfig
import com.family.remote.support.PermChecklist
import com.family.remote.support.R
import com.family.remote.support.SessionException
import com.family.remote.support.SessionUiBus
import com.family.remote.support.SessionView
import com.family.remote.support.denySession
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.grantSession
import com.family.remote.support.localizedMessage
import com.family.remote.support.mmss
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.QuickActionTile
import com.family.remote.support.ui.components.StatusBadge
import com.family.remote.support.ui.components.TickingBlock
import com.family.remote.support.ui.components.WideOutlinedButton
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — the Home tab's own file (FRONTEND_ARCHITECTURE §3
// "ui/home/HomeTab.kt"). Moved VERBATIM out of MainActivity.kt:
// HomeTab (Phase 16 showcase), webClientAvailable, FeatureTile,
// HomeLiveHintCard, HomeConsentCard and HomeConsentDialog. No visual or
// behavioral change — only visibility (private → internal) and the
// package — EXCEPT the M3 "real states" additions at the top of HomeTab:
// a LoadingSkeleton while the session watch's first snapshot has not
// arrived, and a routed ErrorPanel (with retry) when the listener fails
// before any snapshot (NAVIGATION_MAP §5; SessionWatchViewModel).
// ═══════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════
// হোম ট্যাব (v0.14.0) — কম লেখা, বেশি কাঠামো:
//   ১. অনুমতির অনুরোধ এলে Allow/Deny কার্ড (একদম উপরে)
//   ২. ২×২ স্ট্যাটাস টাইল গ্রিড (পারমিশন · এই ফোন · অনলাইন · নোটিফিকেশন)
//   ৩. ব্যর্থ কিছু থাকলে একটাই কম্প্যাক্ট এরর-কার্ড
//   ৪. এই অ্যাকাউন্টের ফোন — লিস্ট-টাইল
//   ৫. দ্রুত অ্যাকশন — ২টি উষ্ণ টাইল
// বড় ব্যাখ্যা-প্যারাগ্রাফ নেই — বিস্তারিত ⓘ-পপআপে।
// ═══════════════════════════════════════════════════════════════════════
@Composable
internal fun HomeTab(
    signedInUser: FirebaseUser?,
    deviceStatus: String,
    presenceStatus: String,
    pushStatus: String,
    permSummary: PermChecklist,
    openSession: SessionView?,
    onOpenWizard: () -> Unit,
    onRetryRegistration: () -> Unit,
    onGoPairing: () -> Unit,
    busy: Boolean = false,
    onSignIn: () -> Unit = {},
    // M3 (v0.31.0) — real session-watch states (defaults keep the old
    // silent behavior for any other caller).
    sessionsError: String? = null,
    sessionsLoading: Boolean = false,
    onRetrySessions: () -> Unit = {},
    // M4 (v0.32.0) — nested-navigation entry points (NAVIGATION_MAP §2):
    // Family Overview (home/family) and Recent Activity (home/sessions)
    // live UNDER this tab as detail routes, never as extra tabs (C2).
    onOpenFamily: () -> Unit = {},
    onOpenActivity: () -> Unit = {}
) {
    // M3 (v0.31.0) — NAVIGATION_MAP §5 real states for the session watch.
    // While the first snapshot has not arrived the tab shows an honest
    // skeleton; if the listener failed BEFORE any snapshot, the routed
    // error becomes a real error panel with retry. After a snapshot
    // arrives the last list always stays (v0.21.0 semantics — transient
    // errors never unmount live cards; see SessionWatchViewModel).
    if (sessionsLoading) {
        LoadingSkeleton(rows = 2)
    }
    sessionsError?.let { err ->
        ErrorPanel(message = err, onRetry = onRetrySessions)
    }
    if (signedInUser != null && openSession != null) {
        if (openSession.status == "REQUESTED" &&
            openSession.recipientUid == signedInUser.uid
        ) {
            // The consent card leads the default tab — no hunting for it.
            HomeConsentCard(session = openSession, user = signedInUser)
        } else {
            HomeLiveHintCard(session = openSession, myUid = signedInUser.uid)
        }
    }
    // Phase 15 (v0.19.0) — the approval ask surfaces as a CENTERED modal at
    // the ROOT level (any tab), in front of the eyes (Owner report: popups
    // must never hide below the fold). Dismissing it decides nothing — the
    // card stays on home. Rendered app-wide in the root Box below.

    // ── Phase 16 — HOME REDESIGN (Owner: "হোম ট্যাব ভিজিট করলেই সে অ্যাপ
    // সম্পর্কে পুরোপুরি ধারণা নিয়ে নেবে… আকর্ষণীয় করেন… কিছু অপশন সেটিংসে
    // দিন")। The home tab is now an app SHOWCASE:
    //   ১. Hero welcome card (who you are + the one-line promise)
    //   ২. "What this app can do" feature grid (the app's real features)
    //   ৩. One compact setup strip (details moved to Settings)
    // The old 2×2 technical status grid, the long device list and the
    // quick-action tiles moved to Settings — home stays light and clear.

    Text(
        text = stringResource(R.string.home_tagline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // ১. Hero welcome card — who you are + the one-line promise.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_auth_hero),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    text = signedInUser?.let {
                        stringResource(
                            R.string.home_signed_in_as,
                            it.displayName ?: it.email ?: ""
                        )
                    } ?: stringResource(R.string.home_not_signed_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        if (signedInUser == null && webClientAvailable()) {
            BigButton(enabled = !busy, onClick = onSignIn) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.auth_btn_login))
                }
            }
        }
    }

    // ২. "What this app can do" — the feature grid. Each tile opens an
    // honest explainer; the pairing tiles also offer the phone tab.
    Text(
        text = stringResource(R.string.home_features),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.feat_screen_t),
                desc = stringResource(R.string.feat_screen_d),
                actionLabel = null
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.feat_touch_t),
                desc = stringResource(R.string.feat_touch_d),
                actionLabel = null
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.feat_family_t),
                desc = stringResource(R.string.feat_family_d),
                actionLabel = stringResource(R.string.nav_add_device),
                onAction = onGoPairing
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            FeatureTile(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.feat_guest_t),
                desc = stringResource(R.string.feat_guest_d),
                actionLabel = stringResource(R.string.nav_add_device),
                onAction = onGoPairing
            )
        }
    }

    // ৩. One compact setup strip (the technical 2×2 grid moved to Settings).
    val registered = deviceStatus.startsWith("Registered")
    val regFailed = deviceStatus.startsWith("Registration failed")
    val regBusy = deviceStatus.startsWith("Registering")
    val presenceOk = presenceStatus.startsWith("Online")
    val pushOk = pushStatus.startsWith("Push notifications ready")
    val allGood = signedInUser != null && permSummary.allDone &&
        registered && presenceOk && pushOk
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenWizard)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (allGood) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allGood) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Lock
                    },
                    contentDescription = null,
                    tint = if (allGood) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = if (allGood) {
                        stringResource(R.string.home_setup_ok)
                    } else {
                        stringResource(R.string.home_setup_pending, permSummary.doneCount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (allGood) {
                        stringResource(R.string.home_setup_ok_sub)
                    } else {
                        stringResource(R.string.home_setup_pending_sub)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // ৪. M4 — nested-navigation entries (NAVIGATION_MAP §2): the two
    // primary detail destinations under Home — the paired-phone Family
    // Overview and the Recent Activity list. QuickActionTile = the warm
    // CTA card of the design system (Cards.kt); real destinations only,
    // no fabricated badges.
    QuickActionTile(
        icon = Icons.Filled.Phone,
        title = stringResource(R.string.fam_title),
        subtitle = stringResource(R.string.set_dev_entry_d),
        onClick = onOpenFamily
    )
    QuickActionTile(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.actv_title),
        subtitle = stringResource(R.string.actv_tile_d),
        onClick = onOpenActivity
    )

    // Any real setup failure still gets its own honest compact card.
    val failures = buildList {
        if (regFailed) add(deviceStatus)
        if (presenceStatus.startsWith("Presence failed")) add(presenceStatus)
        if (pushStatus.startsWith("Push setup failed")) add(pushStatus)
    }
    if (failures.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_setup_problem),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                failures.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                WideOutlinedButton(onClick = onRetryRegistration) {
                    Text(stringResource(R.string.home_retry))
                }
            }
        }
    }

    Text(
        text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

/** True when the build actually has a Google Web Client ID for sign-in. */
internal fun webClientAvailable(): Boolean =
    try {
        com.family.remote.support.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    } catch (_: Exception) {
        false
    }

/**
 * Phase 16 — one feature tile of the home showcase grid: icon circle +
 * title + 2-line description, optional action button. Opens an honest
 * explainer dialog on tap (no fake navigation anywhere).
 */
@Composable
internal fun FeatureTile(
    icon: ImageVector,
    title: String,
    desc: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showInfo = true }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                WideOutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
            },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(desc) }
        )
    }
}

/** v0.14.0 — one-line live-session hint card (non-consent cases). */
@Composable
internal fun HomeLiveHintCard(session: SessionView, myUid: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = when {
                    session.status == "REQUESTED" ->
                        stringResource(R.string.hint_requested, session.recipientEmail)
                    session.helperUid == myUid ->
                        stringResource(R.string.hint_active, session.recipientEmail)
                    else ->
                        stringResource(
                            R.string.hint_helping,
                            session.helperEmail.ifBlank { stringResource(R.string.fallback_someone) }
                        )
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Phase 11 — the recipient-side consent card, now on the home tab. Same
 * Firestore actions as the pairing card's consent card (grant/deny); the
 * security rules re-verify everything either way.
 */
@Composable
internal fun HomeConsentCard(session: SessionView, user: FirebaseUser) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            // Phase 11.6 — the approval card is THE most prominent surface
            // on home: primaryContainer reads "action needed" instantly.
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // Phase 11.6 — screen readers announce this as ONE approval
                // card (docs §15), while Allow/Deny stay separate buttons.
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusBadge(
                        label = stringResource(R.string.cons_label),
                    kind = "info"
                )
            }
            Text(
                    text = stringResource(
                        R.string.cons_body,
                        session.helperEmail.ifBlank { stringResource(R.string.fallback_someone) }
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (session.kind == "guest") {
                Text(
                        text = stringResource(R.string.cons_guest_note),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            // M8 (v0.34.0) perf pass — the 2-minute answer countdown is the
            // ONLY time-dependent piece of the card; TickingBlock isolates the
            // per-second recomposition to this one Text (the card itself and
            // the rest of the Home tab no longer re-render every second).
            TickingBlock(key = session.id) { now ->
                val secondsLeft =
                    ((session.respondDeadlineMillis - now) / 1000).coerceAtLeast(0)
                Text(
                        text = stringResource(R.string.cons_expiry, mmss(secondsLeft)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("✓")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                grantSession(session.id, user.uid)
                                    message = context.getString(R.string.msg_allow_ok)
                            } catch (e: SessionException) {
                                message = e.localizedMessage(context)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                    message = context.getString(R.string.msg_allow_fail, describeFirestoreError(context, e))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                        Text(stringResource(R.string.btn_allow), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                denySession(session.id, user.uid)
                                    message = context.getString(R.string.msg_deny_ok)
                            } catch (e: SessionException) {
                                message = e.localizedMessage(context)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                    message = context.getString(R.string.msg_deny_fail, describeFirestoreError(context, e))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                        Text(stringResource(R.string.btn_deny), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Phase 15 (v0.19.0) — the approval ask now ALSO arrives as a CENTERED
 * modal dialog (Owner report: the top card is easy to miss; popups must
 * land in front of the eyes). Same Firestore grant/deny as the card — the
 * security rules re-verify everything either way. Shown once per session
 * id; dismissing it decides nothing, the card stays on home.
 */
@Composable
internal fun HomeConsentDialog(session: SessionView, user: FirebaseUser) {
    var show by remember(session.id) {
        // v0.21.0 (audit): durable one-per-ask dedup on the process-level bus —
        // a positional remember() re-prompted the SAME ask after rotation,
        // language change or a listener error/reconnect cycle.
        mutableStateOf(session.id !in SessionUiBus.shownAsks)
    }
    if (!show) return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = {
            SessionUiBus.shownAsks.add(session.id)
            show = false
        },
            title = { Text(stringResource(R.string.dlg_consent_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                        stringResource(
                            R.string.cons_body,
                            session.helperEmail.ifBlank { stringResource(R.string.fallback_someone) }
                        ),
                    fontWeight = FontWeight.SemiBold
                )
                if (session.kind == "guest") {
                    Text(
                            stringResource(R.string.dlg_guest_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                // M8 (v0.34.0) perf pass — same isolation as the consent
                // card: only this countdown Text recomposes per second.
                TickingBlock(key = session.id) { now ->
                    val secondsLeft =
                        ((session.respondDeadlineMillis - now) / 1000).coerceAtLeast(0)
                    Text(
                            stringResource(R.string.cons_expiry, mmss(secondsLeft)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("✓")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            grantSession(session.id, user.uid)
                                message = context.getString(R.string.msg_allow_ok)
                            SessionUiBus.shownAsks.add(session.id)
                            delay(600)
                            show = false
                        } catch (e: SessionException) {
                            message = e.localizedMessage(context)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                                message = context.getString(R.string.msg_allow_fail, describeFirestoreError(context, e))
                        } finally {
                            busy = false
                        }
                    }
                }
                ) { Text(stringResource(R.string.btn_allow), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            denySession(session.id, user.uid)
                                message = context.getString(R.string.msg_deny_ok)
                            SessionUiBus.shownAsks.add(session.id)
                            delay(600)
                            show = false
                        } catch (e: SessionException) {
                            message = e.localizedMessage(context)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                                message = context.getString(R.string.msg_deny_fail, describeFirestoreError(context, e))
                        } finally {
                            busy = false
                        }
                    }
                }
                ) { Text(stringResource(R.string.btn_deny), fontWeight = FontWeight.Bold) }
        }
    )
}
