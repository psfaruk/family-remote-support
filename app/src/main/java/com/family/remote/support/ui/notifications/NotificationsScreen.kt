package com.family.remote.support.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.R
import com.family.remote.support.SessionView
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard

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
// This list is intentionally NOT a stored history: it lives in memory and
// resets when the app restarts (a persisted events store is an M8 design
// decision, tracked in IMPLEMENTATION_STATUS). The ⓘ popup says exactly
// that, in every language.
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

        Text(
            text = stringResource(R.string.notif_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
