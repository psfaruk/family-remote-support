package com.family.remote.support.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.NotifEvent
import com.family.remote.support.NotificationsLog
import com.family.remote.support.R
import com.family.remote.support.SessionView
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
// M8 (v0.34.0) — the M8 decision landed (G2): the "Recent events" section
// below joins the live ones, backed by the persisted LOCAL events log
// (NotificationsLog — JSON in filesDir). It records only transitions the
// app already observes, stays on this device (most recent 100, never
// uploaded), and is cleared at sign-out. The ⓘ popup says exactly that,
// in every language.
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
 */
@Composable
fun NotificationsScreen(
    liveSessions: List<SessionView>,
    meUid: String?,
    hasPendingAsk: Boolean,
    permissionDoneCount: Int,
    permissionTotal: Int,
    setupProblemCount: Int,
    onOpenHome: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val permGap = permissionDoneCount < permissionTotal
    val hasAttention = hasPendingAsk || permGap || setupProblemCount > 0
    // M8 (v0.34.0) — the persisted LOCAL events log (G2). The singleton's
    // StateFlow is the ONLY source for the "Recent events" section below:
    // no context, no listener, no extra read. The clock format is locale-
    // aware via java.text (no new string keys).
    val recentEvents by NotificationsLog.events.collectAsState()
    val eventTimeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

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
                recentEvents.forEach { e ->
                    AppListTile(
                        title = recentEventTitle(e),
                        subtitle = eventTimeFormat.format(Date(e.at)),
                        icon = Icons.Filled.Notifications
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.notif_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    "sharing_started" -> stringResource(R.string.notif_ev_sharing_on)
    "sharing_stopped" -> stringResource(R.string.notif_ev_sharing_off)
    else -> e.type
}
