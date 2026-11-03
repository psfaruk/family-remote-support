package com.family.remote.support.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.R
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing

/**
 * ui/components/LocationSharingControls.kt — v0.52.0 (per-tab sequence wave).
 *
 * The ONE owner of this phone's live-location sharing toggle sequence:
 * status line → Start/Stop (with the stop confirmation) → interval picker
 * → the in-flow LOCATION permission ask. Previously this sequence existed
 * only inside the Tracking tab, while Settings' Location tile just JUMPED
 * to the Tracking tab (a cross-tab hand-off — exactly what the Owner
 * reported: "sequences must run inside the tab that started them"). Both
 * tabs now host these controls; the sequence never leaves the screen it
 * started on.
 *
 * The permission ask is IN-FLOW (v0.52.0): a purpose-explaining dialog
 * first ([loc_share_body]), then the system FINE/COARSE prompt, then the
 * service start — never a route jump into another tab's permission
 * screen. Denial renders the honest [loc_denied_*] line.
 */
@Composable
fun LocationSharingControls(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var sharingOn by remember {
        mutableStateOf(LocationSharingPrefs.isSharingEnabled(context))
    }
    var permAskOpen by remember { mutableStateOf(false) }
    var permDenied by remember { mutableStateOf(false) }
    var stopAskOpen by remember { mutableStateOf(false) }
    var intervalOpen by remember { mutableStateOf(false) }

    // ── the in-flow location permission ask (FINE+COARSE together) ──
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            permDenied = false
            actuallyStart(context) { sharingOn = it }
        } else {
            permDenied = true
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
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

        if (permDenied) {
            Text(
                text = stringResource(R.string.loc_denied_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (sharingOn) {
            BigButton(
                text = stringResource(R.string.loc_share_stop),
                onClick = { stopAskOpen = true },
                danger = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BigButton(
                text = stringResource(R.string.loc_share_start),
                onClick = {
                    if (hasAnyLocationPermission(context)) {
                        actuallyStart(context) { sharingOn = it }
                    } else {
                        // Purpose BEFORE the system dialog (LIVE_LOCATION
                        // §5 consent contract) — and it all stays here.
                        permAskOpen = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedButton(
            onClick = { intervalOpen = true },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.loc_interval_label))
        }
    }

    // ── the purpose-first permission dialog ──
    if (permAskOpen) {
        AlertDialog(
            onDismissRequest = { permAskOpen = false },
            title = { Text(stringResource(R.string.loc_share_title)) },
            text = { Text(stringResource(R.string.loc_share_body)) },
            confirmButton = {
                TextButton(onClick = {
                    permAskOpen = false
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }) {
                    Text(stringResource(R.string.loc_share_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { permAskOpen = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    // ── the stop confirmation ──
    if (stopAskOpen) {
        AlertDialog(
            onDismissRequest = { stopAskOpen = false },
            title = { Text(stringResource(R.string.loc_stop_confirm_title)) },
            text = { Text(stringResource(R.string.loc_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    LocationSharingPrefs.setSharingEnabled(context, false)
                    stopLocationSharing(context)
                    sharingOn = false
                    stopAskOpen = false
                }) {
                    Text(stringResource(R.string.loc_share_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { stopAskOpen = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    // ── the interval picker ──
    if (intervalOpen) {
        AlertDialog(
            onDismissRequest = { intervalOpen = false },
            title = { Text(stringResource(R.string.loc_interval_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        LocationSharingPrefs.INTERVAL_30S to R.string.loc_interval_30s,
                        LocationSharingPrefs.INTERVAL_1M to R.string.loc_interval_1m,
                        LocationSharingPrefs.INTERVAL_5M to R.string.loc_interval_5m,
                    ).forEach { (ms, res) ->
                        IntervalRow(
                            selected = LocationSharingPrefs.intervalMillis(context) == ms,
                            label = stringResource(res),
                            onSelect = {
                                LocationSharingPrefs.setIntervalMillis(context, ms)
                                intervalOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { intervalOpen = false }) {
                    Text(stringResource(R.string.cmn_ok))
                }
            },
        )
    }
}

/** Start sharing: prefs first (a late start can never resurrect it), then the service. */
private fun actuallyStart(context: android.content.Context, onStarted: (Boolean) -> Unit) {
    LocationSharingPrefs.setSharingEnabled(context, true)
    try {
        startLocationSharing(context)
    } catch (_: Exception) {
        // The service re-checks permissions/prefs and stops itself.
    }
    onStarted(true)
}

@Composable
private fun IntervalRow(
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
        Checkbox(checked = selected, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
