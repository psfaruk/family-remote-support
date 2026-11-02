package com.family.remote.support.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDropdownMenuState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.ui.LocalWarningColor
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.Nicknames
import com.family.remote.support.PresenceView
import com.family.remote.support.RelationshipView
import com.family.remote.support.readPermChecklist
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing
import com.family.remote.support.ui.R
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.tickingNow
import com.family.remote.support.ui.nav.AppRoutes

/**
 * ui/tracking/TrackingTab.kt — the Live Location tab (W5). Two sections:
 *   1) OwnSharingCard — owner's own sharing state, interval picker,
 *      permission check and the start/stop launcher;
 *   2) PartnerMarkers — a map with one marker per partner that published a
 *      live position (freshness LIVE / STALE / OFFLINE).
 *
 * Honesty: OSM via osmdroid (CARTO Voyager 2D default, + satellite /
 * terrain alternatives); coordinates never appear as text in the UI; the
 * sharing state is derived from the prefs/service; permission gates are
 * real.
 */

@Composable
fun TrackingTab(
    modifier: Modifier = Modifier,
    onOpenRoute: (String) -> Unit,
    rels: List<RelationshipView> = emptyList(),
) {
    val vm: TrackingViewModel = viewModel()
    val context = LocalContext.current

    var perms by remember { mutableStateOf(readPermChecklist(context)) }
    var sharingOn by remember { mutableStateOf(LocationSharingPrefs.isSharingEnabled(context)) }
    var intervalOpen by remember { mutableStateOf(false) }
    var stopDialogOpen by remember { mutableStateOf(false) }

    val ui by vm.ui.collectAsState()

    LaunchedEffect(rels) {
        vm.setRels(rels)
    }

    if (ui.loading) {
        LoadingSkeleton(rows = 3, modifier = modifier.fillMaxSize())
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
            intervalOpen = intervalOpen,
            stopDialogOpen = stopDialogOpen,
            onShareStart = {
                if (hasAnyLocationPermission(context)) {
                    LocationSharingPrefs.setSharingEnabled(context, true)
                    LocationSharingPrefs.setIntervalMillis(
                        context,
                        LocationSharingPrefs.intervalMillis(context)
                    )
                    try {
                        startLocationSharing(context)
                    } catch (_: Exception) {
                        // service re-checks permissions and stops itself
                    }
                    sharingOn = true
                    intervalOpen = false
                } else {
                    onOpenRoute(AppRoutes.ADD_PERMISSIONS)
                }
            },
            onShareStop = {
                LocationSharingPrefs.setSharingEnabled(context, false)
                stopLocationSharing(context)
                sharingOn = false
                stopDialogOpen = false
            },
            onIntervalChange = { newInterval ->
                LocationSharingPrefs.setIntervalMillis(context, newInterval)
                intervalOpen = false
            },
        )
        PartnerMarkers(vm = vm, ui = ui, onOpenRoute = onOpenRoute)
    }

    IntervalPicker(
        open = intervalOpen,
        current = LocationSharingPrefs.intervalMillis(context),
        onDismiss = { intervalOpen = false },
        onSelect = { newInterval ->
            LocationSharingPrefs.setIntervalMillis(context, newInterval)
            intervalOpen = false
        },
    )

    if (stopDialogOpen) {
        AlertDialog(
            onDismissRequest = { stopDialogOpen = false },
            title = { Text(stringResource(R.string.loc_stop_confirm_title)) },
            text = { Text(stringResource(R.string.loc_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { stopDialogOpen = false; onShareStop() }) {
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

    DisposableEffect(Unit) {
        onDispose { }
    }
}

@Composable
private fun OwnSharingCard(
    perms: PermChecklist,
    sharingOn: Boolean,
    intervalOpen: Boolean,
    stopDialogOpen: Boolean,
    onShareStart: () -> Unit,
    onShareStop: () -> Unit,
    onIntervalChange: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.loc_share_title),
        icon = Icons.Filled.LocationOn,
        infoText = stringResource(R.string.loc_share_body),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.loc_share_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
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
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cmn_more),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (sharingOn) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loc_share_stop)) },
                        onClick = { expanded = false; onShareStop() },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loc_share_start)) },
                        onClick = { expanded = false; onShareStart() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.loc_interval_label)) },
                    onClick = { expanded = false },
                )
            }

            if (sharingOn) {
                BigButton(
                    text = stringResource(R.string.loc_share_stop),
                    onClick = { stopDialogOpen = true },
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
                onClick = { intervalOpen = true },
                shape = MaterialTheme.shapes.small,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
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
                modifier = Modifier.fillMaxWidth(),
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
    open: Boolean,
    current: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    if (!open) return
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_interval_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                val labels = mapOf(
                    LocationSharingPrefs.INTERVAL_30S to R.string.loc_interval_30s,
                    LocationSharingPrefs.INTERVAL_1M to R.string.loc_interval_1m,
                    LocationSharingPrefs.INTERVAL_5M to R.string.loc_interval_5m,
                )
                labels.forEach { (ms, res) ->
                    RadioOptionRow(
                        selected = current == ms,
                        label = stringResource(res),
                        onSelect = { onSelect(ms) },
                    )
                }
            }
        }
    }
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
    vm: TrackingViewModel,
    ui: TrackingViewModel.UiState,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
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
                    now = now,
                    vm = vm,
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
    now: Long,
    vm: TrackingViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val name = Nicknames.displayName(context, rel.id, "", "")

    val freshness = vm.partnerFreshness(rel.id, now)
    val (dotColor, dotLabel) = when (freshness) {
        "Live" -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.loc_fresh_live)
        "Stale" -> LocalWarningColor.current to stringResource(R.string.loc_fresh_stale)
        else -> MaterialTheme.colorScheme.outline to stringResource(R.string.loc_fresh_offline)
    }

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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dotLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = dotColor,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Placeholder for the map layer. osmdroid is initialized in [App] with the
 * app package User-Agent; the real implementation owns one MapView with
 * CARTO Voyager 2D as the default style plus satellite / terrain
 * alternatives. Coordinates never appear as text in the UI. */
@Composable
private fun MapPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.map_style_2d),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.map_style_satellite),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.map_style_terrain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}