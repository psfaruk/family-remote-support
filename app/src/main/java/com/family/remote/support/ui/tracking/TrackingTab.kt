// ═══════════════════════════════════════════════════════════════════════
// Phase 22/M5 (v0.33.0) — the TRACKING tab becomes real
// (docs/LIVE_LOCATION_ARCHITECTURE.md §3 flow / §5 permission plan / §6
// freshness, docs/NAVIGATION_MAP.md §2 "tracking" = map + authorized
// markers + device selector sheet + freshness + sharing-status banner +
// loading/permission-denied/error/empty states).
//
// Layout of this file:
//   • shared freshness helpers (LocationFreshness, chip/text colors,
//     "last known" line, device labels) — internal, reused by
//     LocationDetailScreen.kt in this same package;
//   • OwnSharingCard — the own-device sharing banner (consent sheet →
//     runtime FINE/COARSE permission → M5-A start/stopLocationSharing);
//     internal so the Location Detail screen can show the same owner
//     control in its defensive own-device branch;
//   • TrackingTab — the tab itself (canonical states + the map section);
//   • CounterpartMapSection — the osmdroid MapView wrapped in AndroidView
//     (lifecycle-correct resume/pause/detach), one Marker per counterpart
//     device that has actually published a position, camera fitted to the
//     pinned devices, and the device-selector ModalBottomSheet.
//
// Honesty rules baked in: positions come ONLY from TrackingViewModel state
// (never a direct watchLiveLocation call here); an old coordinate is never
// labeled live (freshness is computed from updatedAt vs the reader's
// clock, and the "Last known — HH:mm ±N m" line is ALWAYS shown); no
// history/track polylines — a single marker per device (master PART 6);
// the OSM attribution (osmdroid CopyrightOverlay — "© OpenStreetMap
// contributors") is added once and never removed (Apache-2.0/OSMF policy).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.LiveLocationView
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.R
import com.family.remote.support.RelationshipView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.hasAnyLocationPermission
import com.family.remote.support.startLocationSharing
import com.family.remote.support.stopLocationSharing
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.AppListTile
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.PermissionDeniedPanel
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.TickingBlock
import com.family.remote.support.ui.components.WideOutlinedButton
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

// ── Freshness (LIVE_LOCATION_ARCHITECTURE §3/§6, reader's clock) ──────────

/**
 * Freshness bucket for a published position, computed from updatedAt vs
 * the reader's clock (generous: LIVE < 2 min, STALE < 5 min, else
 * OFFLINE). An old coordinate is NEVER labeled live.
 */
internal enum class LocationFreshness { LIVE, STALE, OFFLINE }

/** Bucket for a live-location doc; a missing/zero timestamp counts as OFFLINE. */
internal fun locationFreshness(location: LiveLocationView, nowMillis: Long): LocationFreshness {
    if (location.updatedAtMillis <= 0L) return LocationFreshness.OFFLINE
    val ageMillis = nowMillis - location.updatedAtMillis
    return when {
        ageMillis < 120_000L -> LocationFreshness.LIVE
        ageMillis < 300_000L -> LocationFreshness.STALE
        else -> LocationFreshness.OFFLINE
    }
}

/** Localized freshness word (Live / Stale / Offline). */
@Composable
internal fun freshnessLabel(freshness: LocationFreshness): String = when (freshness) {
    LocationFreshness.LIVE -> stringResource(R.string.loc_fresh_live)
    LocationFreshness.STALE -> stringResource(R.string.loc_fresh_stale)
    LocationFreshness.OFFLINE -> stringResource(R.string.loc_fresh_offline)
}

/** Theme-aware container/content pair for a freshness chip (PairTile color language). */
@Composable
internal fun freshnessChipColors(freshness: LocationFreshness): Pair<Color, Color> = when (freshness) {
    LocationFreshness.LIVE -> Pair(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    LocationFreshness.STALE -> Pair(
        AppWarningColor().copy(alpha = 0.18f),
        AppWarningColor()
    )
    LocationFreshness.OFFLINE -> Pair(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Theme-aware text color for a freshness line (PresenceBadge color language). */
@Composable
internal fun freshnessTextColors(freshness: LocationFreshness): Color = when (freshness) {
    LocationFreshness.LIVE -> MaterialTheme.colorScheme.tertiary
    LocationFreshness.STALE -> AppWarningColor()
    LocationFreshness.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "HH:mm" for a timestamp — the honest last-known clock line. */
internal fun locationTimeString(updatedAtMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAtMillis))

/**
 * The always-visible position line: "Last known — HH:mm · ±N m" when a
 * doc exists, "Never shared" when it does not. Never a fabricated time.
 */
@Composable
internal fun lastKnownLine(location: LiveLocationView?): String {
    return if (location == null) {
        stringResource(R.string.loc_never)
    } else {
        stringResource(R.string.loc_last_known, locationTimeString(location.updatedAtMillis)) +
            " · " + stringResource(R.string.loc_acc_m, location.accuracyMeters)
    }
}

/** Counterpart email with honest fallbacks (never a hardcoded "(no email)" literal). */
internal fun counterpartEmail(rel: RelationshipView): String =
    rel.recipientEmail
        .ifBlank { rel.recipientDeviceModel }
        .ifBlank { rel.recipientUid }

/** Marker/tile label for a counterpart device: model first, email as fallback. */
internal fun deviceLabel(rel: RelationshipView): String =
    rel.recipientDeviceModel.ifBlank { counterpartEmail(rel) }

// ── Own-device sharing banner (§5 permission plan: consent → permission → FGS) ──

// v0.37.0 (dedupe wave) — hasAnyLocationPermission ("fine || coarse") now
// lives in LiveLocationRepo.kt as ONE app-wide check; this file's private
// copy was one of the drifting duplicates.

/** Hand-off to this app's system settings page (PermissionDeniedPanel's action). */
private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        } catch (_: Exception) {
            // no settings activity on this device — nothing else we can do
        }
    }
}

/**
 * The own-device sharing card: ON/OFF banner (persisted flag, refreshed on
 * resume so a stop from the notification or the Settings screen is
 * reflected honestly), the one-tap Stop, and the consent sheet that must
 * precede every Start. On confirm, the runtime FINE+COARSE permission
 * launcher runs; granted → startLocationSharing, denied → the canonical
 * PermissionDeniedPanel with the system-settings hand-off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnSharingCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var sharingOn by remember {
        mutableStateOf(LocationSharingPrefs.isSharingEnabled(context))
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var showConsentSheet by remember { mutableStateOf(false) }

    // Refresh on resume: the pref can change outside this composition
    // (stop from the sharing notification, the Settings section, or a
    // fresh install restore). While sharing is WANTED but the permission
    // is missing (revoked in system settings), the denied panel shows —
    // the app never pretends a permission was granted.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharingOn = LocationSharingPrefs.isSharingEnabled(context)
                permissionDenied = sharingOn && !hasAnyLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Consent → system dialog. Requesting BOTH while-in-use location
    // permissions at once; "Approximate" (coarse only) is still enough to
    // share a position, so EITHER grant starts sharing — anything less
    // would dishonestly deny a user who deliberately chose approximate.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            permissionDenied = false
            // v0.37.0 (dedupe wave) — startLocationSharing persists the
            // consent pref itself (LiveLocationRepo records it FIRST); the
            // redundant double write here is gone.
            startLocationSharing(context)
            sharingOn = true
        } else {
            permissionDenied = true
        }
    }

    if (permissionDenied) {
        PermissionDeniedPanel(
            title = stringResource(R.string.loc_denied_title),
            explanation = stringResource(R.string.loc_denied_body),
            onOpenSettings = { openAppSettings(context) }
        )
    }

    SectionCard(
        title = stringResource(
            if (sharingOn) R.string.loc_share_on else R.string.loc_share_off
        ),
        icon = Icons.Filled.LocationOn,
        infoText = stringResource(R.string.set_loc_now)
    ) {
        Text(
            text = stringResource(
                if (sharingOn) R.string.loc_banner_on else R.string.loc_banner_off
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sharingOn) {
            // Stop is deliberately ONE tap (the consent sheet is only for
            // starting) — the owner can always end sharing immediately.
            BigButton(
                onClick = {
                    // v0.37.0 (dedupe wave) — stopLocationSharing flips the
                    // pref itself before stopping the service; the extra
                    // setSharingEnabled(false) write here was redundant.
                    stopLocationSharing(context)
                    sharingOn = false
                }
            ) {
                Text(stringResource(R.string.loc_share_stop))
            }
        } else {
            BigButton(onClick = { showConsentSheet = true }) {
                Text(stringResource(R.string.loc_share_start))
            }
        }
    }

    if (showConsentSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showConsentSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.loc_share_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.loc_share_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BigButton(
                    onClick = {
                        showConsentSheet = false
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.loc_share_start))
                }
            }
        }
    }
}

// ── The tab ──────────────────────────────────────────────────────────────

/**
 * M5 (v0.33.0) — NAVIGATION_MAP §2 "tracking": the map tab. Locations are
 * read ONLY through TrackingViewModel state.
 *
 * @param user         the signed-in account (null → the honest signed-out
 *                     empty state, same convention PairingCard uses).
 * @param onOpenMember navigates to tracking/{relId} for a counterpart.
 */
@Composable
internal fun TrackingTab(user: FirebaseUser?, onOpenMember: (String) -> Unit) {
    val context = LocalContext.current
    if (user == null) {
        SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.phone_signin_first),
                hint = stringResource(R.string.phone_signin_hint)
            )
        }
        return
    }

    val vm: TrackingViewModel = viewModel()
    var retryKey by remember { mutableStateOf(0) }
    DisposableEffect(user.uid, retryKey) {
        vm.observe(user) { e -> describeFirestoreError(context, e) }
        onDispose { }
    }
    val state by vm.state.collectAsState()

    OwnSharingCard()

    // Canonical states in priority order (NAVIGATION_MAP §5): first
    // snapshot not arrived → skeleton; routed error with no loaded data →
    // retryable error panel; after a snapshot the last data always stays
    // (the transient error is not re-shown, exactly like HomeTab). No
    // paired devices → the honest empty state (no CTA button: this tab
    // has no pairing callback — pairing lives on the Add Device tab).
    val routedError = state.error?.takeIf { !state.loaded }
    when {
        routedError != null -> ErrorPanel(message = routedError, onRetry = { retryKey++ })
        !state.loaded -> LoadingSkeleton(rows = 3)
        state.counterparts.isEmpty() -> EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.loc_empty_title),
            hint = stringResource(R.string.loc_empty_body)
        )
        else ->
            // v0.37.0 (dedupe wave) — the 30 s freshness clock (the
            // PresenceBadge cadence) now lives in TickingBlock instead of a
            // screen-level while(true) loop: only the map section reads it,
            // so only that subtree recomposes per tick while
            // LIVE→STALE→OFFLINE transitions still happen on screen.
            TickingBlock(intervalMillis = 30_000) { nowMillis ->
                CounterpartMapSection(
                    counterparts = state.counterparts,
                    nowMillis = nowMillis,
                    onOpenMember = onOpenMember
                )
            }
    }
}

// ── Map section + device selector sheet ─────────────────────────────────

/** Sane "world view" starting zoom — nothing is implied about any device. */
private const val WORLD_ZOOM = 3.0

/** Street-level zoom when the camera centers on a single pinned device. */
private const val FOCUS_ZOOM = 16.0

/** Pure marker data (computed in composition so the update lambda stays context-free). */
private data class MarkerVisual(
    val relId: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val snippet: String
)

/**
 * Per-composition map sync bookkeeping: markers by relationship id plus
 * the last camera-fit key. Deliberately NOT snapshot state — it is only
 * touched inside AndroidView.update, and it must die with its MapView
 * (a file-level map would leak markers from a detached map into a fresh
 * one after tab switches or configuration changes).
 */
private class MapSyncState {
    val markers = HashMap<String, Marker>()
    var fittedKey: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartMapSection(
    counterparts: List<CounterpartPin>,
    nowMillis: Long,
    onOpenMember: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedRelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeviceSheet by remember { mutableStateOf(false) }

    // One MarkerVisual per device that has ACTUALLY published a position
    // (no marker is invented for a device that never shared).
    val pinnedVisuals = counterparts.mapNotNull { pin ->
        val location = pin.location ?: return@mapNotNull null
        MarkerVisual(
            relId = pin.rel.id,
            lat = location.lat,
            lng = location.lng,
            title = deviceLabel(pin.rel),
            snippet = lastKnownLine(location)
        )
    }

    // The MapView is remembered (one instance across recompositions) and
    // wired to the host lifecycle: ON_RESUME/ON_PAUSE forward to the
    // osmdroid calls, disposal detaches the view and frees its overlays.
    // Tile source MAPNIK (OSM) + multi-touch pan/zoom. The OSM copyright
    // overlay is added once, before any marker, and never removed.
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(WORLD_ZOOM)
            overlays.add(CopyrightOverlay(context))
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    val sync = remember { MapSyncState() }

    SectionCard(title = stringResource(R.string.loc_title), icon = Icons.Filled.LocationOn) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(14.dp)),
            factory = { mapView },
            update = { mv ->
                // 1. drop markers whose device stopped publishing a position
                val pinnedIds = pinnedVisuals.map { it.relId }.toHashSet()
                sync.markers.keys.filter { it !in pinnedIds }.forEach { relId ->
                    val marker = sync.markers.remove(relId)
                    if (marker != null) {
                        val index = mv.overlays.indexOf(marker)
                        if (index >= 0) {
                            mv.overlays.removeAt(index)
                        }
                    }
                }
                // 2. add new markers, move/relabel existing ones
                pinnedVisuals.forEach { visual ->
                    val point = GeoPoint(visual.lat, visual.lng)
                    val existing = sync.markers[visual.relId]
                    if (existing == null) {
                        val marker = Marker(mv)
                        marker.setPosition(point)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.setTitle(visual.title)
                        marker.setSnippet(visual.snippet)
                        mv.overlays.add(marker)
                        sync.markers[visual.relId] = marker
                    } else {
                        existing.setPosition(point)
                        existing.setTitle(visual.title)
                        existing.setSnippet(visual.snippet)
                    }
                }
                // 3. camera follows the SET of pinned devices (a new fix for
                // an already-pinned device must not yank the camera)
                val fitKey = pinnedVisuals.joinToString("|") { it.relId }
                if (fitKey != sync.fittedKey) {
                    sync.fittedKey = fitKey
                    if (pinnedVisuals.size == 1) {
                        mv.controller.setZoom(FOCUS_ZOOM)
                        mv.controller.setCenter(
                            GeoPoint(pinnedVisuals[0].lat, pinnedVisuals[0].lng)
                        )
                    } else if (pinnedVisuals.size > 1) {
                        mv.zoomToBoundingBox(
                            BoundingBox.fromGeoPoints(
                                pinnedVisuals.map { GeoPoint(it.lat, it.lng) }
                            ),
                            false,
                            80
                        )
                    } else {
                        // no pinned device left → back to the world view
                        mv.controller.setZoom(WORLD_ZOOM)
                    }
                }
                mv.invalidate()
            }
        )

        // At-a-glance freshness for the focused device (sheet pick or first
        // pinned device), with the always-visible last-known line.
        val focused = counterparts.firstOrNull { it.rel.id == selectedRelId }
            ?: counterparts.firstOrNull { it.location != null }
        if (focused == null) {
            Text(
                text = stringResource(R.string.loc_no_fix),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val location = focused.location
                    if (location == null) {
                        Text(
                            text = stringResource(R.string.loc_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val freshness = locationFreshness(location, nowMillis)
                        Text(
                            text = freshnessLabel(freshness),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = freshnessTextColors(freshness)
                        )
                        Text(
                            text = lastKnownLine(location),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { onOpenMember(focused.rel.id) }) {
                    Text(stringResource(R.string.loc_details))
                }
            }
        }

        WideOutlinedButton(onClick = { showDeviceSheet = true }) {
            Text(stringResource(R.string.loc_list_title))
        }
    }

    if (showDeviceSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDeviceSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.loc_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                counterparts.forEach { pin ->
                    DevicePickerRow(
                        pin = pin,
                        nowMillis = nowMillis,
                        onPick = {
                            selectedRelId = pin.rel.id
                            pin.location?.let { location ->
                                mapView.controller.animateTo(
                                    GeoPoint(location.lat, location.lng)
                                )
                            }
                            showDeviceSheet = false
                        },
                        onOpenDetails = {
                            showDeviceSheet = false
                            onOpenMember(pin.rel.id)
                        }
                    )
                }
            }
        }
    }
}

/**
 * One counterpart device in the selector sheet: model/email tile with the
 * freshness chip, the always-visible last-known line, and the route into
 * tracking/{relId} (loc_details). Tapping the tile selects the device and
 * centers the map when a position exists.
 */
@Composable
private fun DevicePickerRow(
    pin: CounterpartPin,
    nowMillis: Long,
    onPick: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val rel = pin.rel
    val email = counterpartEmail(rel)
    val freshness = pin.location?.let { locationFreshness(it, nowMillis) }
    val chipColors = freshness?.let { freshnessChipColors(it) }
    Column {
        AppListTile(
            title = rel.recipientDeviceModel.ifBlank { email },
            subtitle = email,
            icon = Icons.Filled.Phone,
            chip = freshness?.let { freshnessLabel(it) },
            chipContainer = chipColors?.first
                ?: MaterialTheme.colorScheme.secondaryContainer,
            chipContent = chipColors?.second
                ?: MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onPick
        )
        Row(
            modifier = Modifier.padding(start = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lastKnownLine(pin.location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(R.string.loc_details))
            }
        }
    }
}
