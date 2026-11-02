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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.Nicknames
import com.family.remote.support.PermChecklist
import com.family.remote.support.PresenceView
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.readPermChecklist
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PresenceBadge
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.home.HomeViewModel
import com.family.remote.support.ui.nav.AppRoutes
import com.google.firebase.auth.FirebaseUser

/**
 * ui/tracking/TrackingTab.kt — the Live Location tab (W5). Two sections:
 *   1) OwnSharingCard — the owner's own sharing state, interval picker,
 *      permission checklist and the start/stop flow (stop asks first);
 *   2) PartnerMarkers — one row per linked partner with their presence
 *      freshness (LIVE / SEEN / OFFLINE through the canonical
 *      [PresenceBadge]). The OSM map surface arrives with its own wave;
 *      rows are the honest list until then — no fabricated markers.
 *
 * Quota discipline (§75): the tab renders from the ONE shared
 * [HomeViewModel] listener set (rels + presence) exactly like the home
 * subtree's screens — it never opens a listener of its own. The sharing
 * toggle/interval write only when the user acts (v0.36 directive); the
 * permission gate is real (no location permission → the wizard route).
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
    var sharingOn by remember { mutableStateOf(LocationSharingPrefs.isSharingEnabled(context)) }
    var intervalOpen by remember { mutableStateOf(false) }
    var stopDialogOpen by remember { mutableStateOf(false) }

    val onShareStart: () -> Unit = {
        if (hasAnyLocationPermission(context)) {
            LocationSharingPrefs.setSharingEnabled(context, true)
            try {
                startLocationSharing(context)
            } catch (_: Exception) {
                // The service re-checks permissions/prefs and stops itself.
            }
            sharingOn = true
        } else {
            onOpenRoute(AppRoutes.ADD_PERMISSIONS)
        }
    }
    val onShareStop: () -> Unit = {
        LocationSharingPrefs.setSharingEnabled(context, false)
        stopLocationSharing(context)
        sharingOn = false
        stopDialogOpen = false
    }

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
            sharingOn = sharingOn,
            onShareStart = onShareStart,
            onConfirmStop = { stopDialogOpen = true },
            onOpenInterval = { intervalOpen = true },
            onOpenRoute = onOpenRoute,
        )
        PartnerMarkers(user = user, ui = ui, onOpenRoute = onOpenRoute)
    }

    if (intervalOpen) {
        IntervalPicker(
            current = LocationSharingPrefs.intervalMillis(context),
            onDismiss = { intervalOpen = false },
            onSelect = { newInterval ->
                LocationSharingPrefs.setIntervalMillis(context, newInterval)
                intervalOpen = false
            },
        )
    }

    if (stopDialogOpen) {
        AlertDialog(
            onDismissRequest = { stopDialogOpen = false },
            title = { Text(stringResource(R.string.loc_stop_confirm_title)) },
            text = { Text(stringResource(R.string.loc_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onShareStop) {
                    Text(stringResource(R.string.loc_share_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { stopDialogOpen = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }
}

@Composable
private fun OwnSharingCard(
    perms: PermChecklist,
    sharingOn: Boolean,
    onShareStart: () -> Unit,
    onConfirmStop: () -> Unit,
    onOpenInterval: () -> Unit,
    onOpenRoute: (String) -> Unit,
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
            Text(
                text = if (sharingOn) stringResource(R.string.loc_share_on)
                else stringResource(R.string.loc_share_off),
                style = MaterialTheme.typography.bodySmall,
                color = if (sharingOn) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (sharingOn) {
                BigButton(
                    text = stringResource(R.string.loc_share_stop),
                    onClick = onConfirmStop,
                    modifier = Modifier.fillMaxWidth(),
                    danger = true,
                )
            } else {
                BigButton(
                    text = stringResource(R.string.loc_share_start),
                    onClick = onShareStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedButton(
                onClick = onOpenInterval,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.loc_interval_label))
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRoute(AppRoutes.ADD_PERMISSIONS) },
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
private fun IntervalPicker(
    current: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loc_interval_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    LocationSharingPrefs.INTERVAL_30S to R.string.loc_interval_30s,
                    LocationSharingPrefs.INTERVAL_1M to R.string.loc_interval_1m,
                    LocationSharingPrefs.INTERVAL_5M to R.string.loc_interval_5m,
                ).forEach { (ms, res) ->
                    RadioOptionRow(
                        selected = current == ms,
                        label = stringResource(res),
                        onSelect = { onSelect(ms) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cmn_ok))
            }
        },
    )
}

@Composable
private fun RadioOptionRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
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
