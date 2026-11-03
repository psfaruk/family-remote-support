package com.family.remote.support.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.Nicknames
import com.family.remote.support.PermChecklist
import com.family.remote.support.PresenceView
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.readPermChecklist
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.LocationSharingControls
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.home.HomeViewModel
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser

/**
 * ui/tracking/TrackingTab.kt — the Live Location tab (W5; v0.52.0 per-tab
 * sequence wave). Two sections:
 *   1) OwnSharingCard — the owner's own sharing state, interval picker,
 *      permission checklist and the start/stop flow (stop asks first) —
 *      now through the ONE shared [LocationSharingControls] (the same
 *      sequence Settings hosts: status → start/stop → interval → the
 *      IN-FLOW location permission ask; never a jump into another tab's
 *      permission screen — the Owner's report);
 *   2) PartnerMarkers — one row per linked partner with their presence
 *      freshness (LIVE / SEEN / OFFLINE through the canonical
 *      [PresenceBadge]); each row opens tracking/{relId} — the REAL
 *      Location Details screen since v0.52.0 (was a WIP placeholder).
 *
 * Quota discipline (§75): the tab renders from the ONE shared
 * [HomeViewModel] listener set (rels + presence) exactly like the home
 * subtree's screens — it never opens a listener of its own. The sharing
 * toggle/interval write only when the user acts (v0.36 directive).
 */
@Composable
fun TrackingTab(
    user: FirebaseUser,
    vm: HomeViewModel,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()

    var perms by remember { mutableStateOf(readPermChecklist(context)) }
    var wizardOpen by rememberSaveable { mutableStateOf(false) }

    if (ui.loading && ui.rels.isEmpty()) {
        LoadingSkeleton(rows = 3, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OwnSharingCard(
            perms = perms,
            onOpenWizard = { wizardOpen = true },
        )
        PartnerMarkers(user = user, ui = ui, onOpenRoute = onOpenRoute)
    }

    // The permission wizard, MODAL in this tab (NAVIGATION_MAP §3: Back
    // dismisses without leaving the flow — never a route jump again).
    if (wizardOpen) {
        com.family.remote.support.PermissionWizard(
            visible = true,
            onDismiss = { wizardOpen = false },
        )
    }
}

@Composable
private fun OwnSharingCard(
    perms: PermChecklist,
    onOpenWizard: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.loc_share_title),
        icon = Icons.Filled.LocationOn,
        infoText = stringResource(R.string.loc_share_body),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The shared sequence: status → start/stop → interval →
            // the in-flow location permission ask.
            LocationSharingControls()

            // The setup checklist — the wizard opens right here now.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWizard() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sg_setup),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (perms.allDone) {
                            stringResource(R.string.sg_setup_done)
                        } else {
                            stringResource(R.string.sg_setup_count, perms.doneCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerMarkers(
    user: FirebaseUser,
    ui: HomeViewModel.UiState,
    onOpenRoute: (String) -> Unit,
) {
    val now = tickingNow(30_000L)

    SectionCard(title = stringResource(R.string.loc_list_title)) {
        if (ui.rels.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.loc_empty_title),
                body = stringResource(R.string.loc_empty_body),
            )
        } else {
            Column {
                ui.rels.forEach { rel ->
                    MemberMarkerRow(
                        rel = rel,
                        presence = ui.presence[rel.id],
                        myUid = user.uid,
                        now = now,
                        onOpenRoute = onOpenRoute,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberMarkerRow(
    rel: RelationshipView,
    presence: PresenceView?,
    myUid: String,
    now: Long,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val counterpart = if (rel.helperUid == myUid) rel.recipientEmail else rel.helperEmail
    val name = Nicknames.displayName(context, rel.id, counterpart, counterpart)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenRoute(AppRoutes.trackingDetail(rel.id)) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PresenceBadge(presence = presence, now = now)
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
