package com.family.remote.support.ui.notifications

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.NotifEvent
import com.family.remote.support.NotificationsLog
import com.family.remote.support.NotifFilter
import com.family.remote.support.R
import com.family.remote.support.SessionView
import com.family.remote.support.applyNotifFilter
import com.family.remote.support.notifRouteFor
import com.family.remote.support.notifUnreadCount
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date

// ═══════════════════════════════════════════════════════════════════════
// Phase 21/M2 (v0.30.0) — the dedicated notifications surface (the bell's
// target; previously the bell just jumped to Settings — audit finding #4).
//
// HONESTY RULE (NAVIGATION_MAP §2): real events only, nothing fabricated.
// Everything shown here is DERIVED FROM DATA THE APP ALREADY LISTENS TO —
// zero extra Firestore reads, zero new listeners:
//   • pending consent ask      → the existing session watch (HomeScreen)
//   • permission checklist gap → the existing local `readPermChecklist` poll
//   • failed setup steps       → the registration outcome flags (M2)
//   • live sessions            → the existing REQUESTED/ACTIVE watch
// M8 (v0.34.0) — the "Recent events" section joins the live ones, backed
// by the persisted LOCAL events log (NotificationsLog — JSON in filesDir).
// It records only transitions the app already observes, stays on this
// device (most recent 100, never uploaded), and is cleared at sign-out.
// The ⓘ popup says exactly that, in every language.
//
// v0.46.0 (external-audit G1) — the section grows into a real center:
//   • read/unread state (NotifEvent.readAt, schema v2) with an "Unread"
//     filter, tap-to-mark-read and "Mark all as read";
//   • category chips (All / Connection / Location / Security) mapped in
//     ONE pure place (NotifCenter.notifCategoryOf) — an unknown future
//     event type stays visible under All, never mis-filed;
//   • every row is tappable and lands on the screen where that event's
//     facts live (NotifCenter.notifRouteFor — DeepLinks' own route strings,
//     dispatched through the same AppNav.goRoute the notification router
//     uses, so an in-app tap and a system-notification tap are identical);
//   • a user-facing "Clear history" behind a confirmation dialog (the
//     sign-out wipe stays as account hygiene — two intents, two paths);
//   • the event timestamp gained its DATE half (getDateTimeInstance) — a
//     100-row history spans days; a time-only stamp would read as "today".
// All local-only: zero new reads/listeners, rules untouched (§75).
// ═══════════════════════════════════════════════════════════════════════

/**
 * @param liveSessions  REQUESTED/ACTIVE sessions already watched by the
 *                      shell (no new query is opened for this screen).
 * @param meUid         this account's uid, for helper/recipient labeling.
 * @param hasPendingAsk true while a consent ask waits for THIS device.
 * @param permissionDoneCount / permissionTotal — the local permission
 *                      checklist state (real on-device checks only).
 * @param setupProblemCount   how many registration steps (device record /
 *                      presence heartbeat / push token) failed on this
 *                      device — real outcome flags, never parsed strings.
 * @param onOpenHome    attention rows send the user to Home, where the
 *                      actionable cards (consent, status, retry) live.
 * @param onOpenWizard  opens the permission checklist wizard.
 * @param onOpenRoute   v0.46.0 (G1) — a Recent-events row tap lands on the
 *                      route NotifCenter.notifRouteFor maps the event to
 *                      (the caller passes the shared AppNav.goRoute
 *                      dispatcher — the exact DeepLinks semantics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    liveSessions: List<SessionView>,
    meUid: String?,
    hasPendingAsk: Boolean,
    permissionDoneCount: Int,
    permissionTotal: Int,
    setupProblemCount: Int,
    onOpenHome: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    val permGap = permissionDoneCount < permissionTotal
    val hasAttention = hasPendingAsk || permGap || setupProblemCount > 0
    // M8 (v0.34.0) — the persisted LOCAL events log (G2). The singleton's
    // StateFlow is the ONLY source for the "Recent events" section below:
    // no context, no listener, no extra read. The clock format is locale-
    // aware via java.text (no new string keys).
    val recentEvents by NotificationsLog.events.collectAsState()
    // v0.46.0 (G1) — date + time: the history spans days, so a time-only
    // stamp would misread old rows as today's.
    val eventTimeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    // G1 — the chip selection survives recomposition AND process death,
    // like every other piece of shell state (NAVIGATION_MAP §4).
    var filter by rememberSaveable { mutableStateOf(NotifFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }
    val unreadCount = recentEvents.notifUnreadCount()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(
            title = stringResource(R.string.notif_title),
            icon = Icons.Filled.Notifications,
            infoText = stringResource(R.string.notif_hint)
        ) {
            if (!hasAttention && liveSessions.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.notif_empty_title),
                    hint = stringResource(R.string.notif_empty_body)
                )
            }
        }

        if (hasAttention) {
            SectionCard(
                title = stringResource(R.string.notif_section_attention),
                icon = Icons.Filled.Warning
            ) {
                if (hasPendingAsk) {
                    AppListTile(
                        title = stringResource(R.string.notif_item_ask),
                        subtitle = stringResource(R.string.notif_open_home),
                        icon = Icons.Filled.Notifications,
                        onClick = onOpenHome,
                        trailingIcon = Icons.Filled.Warning
                    )
                }
                if (permGap) {
                    AppListTile(
                        title = stringResource(
                            R.string.notif_item_perms, permissionDoneCount, permissionTotal
                        ),
                        subtitle = stringResource(R.string.notif_open_wizard),
                        icon = Icons.Filled.Warning,
                        onClick = onOpenWizard
                    )
                }
                if (setupProblemCount > 0) {
                    AppListTile(
                        title = stringResource(R.string.notif_item_setup),
                        subtitle = stringResource(R.string.notif_open_home),
                        icon = Icons.Filled.Warning,
                        onClick = onOpenHome
                    )
                }
            }
        }

        if (liveSessions.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.notif_section_live),
                icon = Icons.Filled.Notifications
            ) {
                liveSessions.forEach { s ->
                    // Role-honest labels: an ask TO this device reads
                    // "waiting for your answer"; an ask FROM this device
                    // names the recipient; an ACTIVE session names who
                    // helps whom (same facts the Home cards show).
                    val headline = when {
                        s.status == "REQUESTED" && s.recipientUid == meUid ->
                            stringResource(R.string.notif_item_ask)
                        s.status == "REQUESTED" ->
                            stringResource(R.string.notif_sess_requested, s.recipientEmail)
                        s.helperUid == meUid ->
                            stringResource(R.string.notif_sess_active_you, s.recipientEmail)
                        else ->
                            stringResource(R.string.notif_sess_active_other, s.helperEmail)
                    }
                    AppListTile(
                        title = headline,
                        subtitle = stringResource(R.string.notif_open_home),
                        icon = Icons.Filled.Notifications,
                        onClick = onOpenHome
                    )
                }
            }
        }

        if (recentEvents.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.notif_section_recent),
                icon = Icons.Filled.Notifications
            ) {
                // G1 item 3 — the filter chips. Horizontally scrollable:
                // five localized labels do not fit every 320 dp family
                // phone at the senior-first type scale.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    NotifFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(notifFilterLabel(f))) }
                        )
                    }
                }
                // G1 items 2+5 — the section's actions, right-aligned:
                // "Mark all as read" only while something IS unread (an
                // action that can do nothing never clutters the UI), and
                // "Clear history" always available behind its dialog.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (unreadCount > 0) {
                        TextButton(onClick = { NotificationsLog.markAllRead(context) }) {
                            Text(stringResource(R.string.notif_action_mark_all))
                        }
                    }
                    TextButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.notif_action_clear))
                    }
                }
                val visible = recentEvents.applyNotifFilter(filter)
                if (visible.isEmpty()) {
                    // Honest per-filter empty state — the chips stay, so
                    // switching to another view is one tap away.
                    EmptyState(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.notif_empty_filter_title),
                        hint = stringResource(R.string.notif_empty_filter_body)
                    )
                } else {
                    visible.forEach { e ->
                        AppListTile(
                            title = recentEventTitle(e),
                            subtitle = eventTimeFormat.format(Date(e.at)),
                            icon = Icons.Filled.Notifications,
                            // G1 items 2+4 — one tap reads the row AND lands
                            // on the screen where the event's facts live.
                            onClick = {
                                NotificationsLog.markRead(context, e.id)
                                onOpenRoute(notifRouteFor(e.type, e.arg2))
                            },
                            // G1 — the unread pill; read rows show nothing
                            // extra (a quiet list is the goal, not a badge
                            // wall).
                            chip = if (e.readAt == null) {
                                stringResource(R.string.notif_chip_unread)
                            } else null
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.notif_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // G1 item 5 — Clear history is irreversible on this device, so it sits
    // behind the same confirmation-dialog pattern as every destructive
    // action in the app (e.g. member removal).
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.notif_clear_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.notif_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        NotificationsLog.clearHistory(context)
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.notif_clear_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.notif_clear_cancel))
                }
            }
        )
    }
}

/** G1 — the label key of one filter chip (single mapping, no drift). */
private fun notifFilterLabel(f: NotifFilter): Int = when (f) {
    NotifFilter.ALL -> R.string.notif_filter_all
    NotifFilter.UNREAD -> R.string.notif_filter_unread
    NotifFilter.CONNECTION -> R.string.notif_filter_connection
    NotifFilter.LOCATION -> R.string.notif_filter_location
    NotifFilter.SECURITY -> R.string.notif_filter_security
}

/**
 * M8 (v0.34.0) — localized text for one recorded event: the type maps to a
 * format string, the recorded arg (a counterpart email) fills it. An
 * unknown future type falls back to its raw token — honest, never a crash,
 * never a fabricated label.
 */
@Composable
private fun recentEventTitle(e: NotifEvent): String = when (e.type) {
    "session_asked" -> stringResource(R.string.notif_ev_asked, e.arg.orEmpty())
    "session_started" -> stringResource(R.string.notif_ev_started, e.arg.orEmpty())
    "session_ended" -> stringResource(R.string.notif_ev_ended, e.arg.orEmpty())
    "session_denied" -> stringResource(R.string.notif_ev_denied, e.arg.orEmpty())
    "session_cancelled" -> stringResource(R.string.notif_ev_cancelled, e.arg.orEmpty())
    "sharing_started" -> stringResource(R.string.notif_ev_sharing_on)
    "sharing_stopped" -> stringResource(R.string.notif_ev_sharing_off)
    "place_alert" -> stringResource(R.string.notif_ev_place, e.arg.orEmpty())
    else -> e.type
}
