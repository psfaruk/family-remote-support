// ═══════════════════════════════════════════════════════════════════════
// Phase 4 of the Location master plan (v0.38.0/57) — "My places & alerts"
// screen, route tracking/places (OWN device, v1 self-alerts scope)
// (docs/location/LOCATION_UX_SPECIFICATION.md §7).
//
// Phase 4 scope, stated honestly on the screen itself: this is the UI
// preview of §7 — an IN-MEMORY place list (never written to disk), NO
// geofencing, NO notifications, NO background location. Place
// persistence + geofence monitoring arrive with later phases; until
// then place_status says plainly that monitoring would only happen while
// sharing is on, and place_future_note says recipient-facing alerts are
// a future extension. In demo mode the list is seeded once from
// DemoTrackingDataSource fixtures; the real side starts empty.
//
// The pick-on-map form centers a small osmdroid map (same lifecycle
// pattern as TrackingTab's CounterpartMapSection) and saves the map
// CENTER as the place's position — Phase 4 deliberately wires no
// "current position" into the picker (that would need a new permission
// flow); the user pans to the place instead.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.family.remote.support.R
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.family.remote.support.ui.tracking.demo.DemoTrackingMode
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/**
 * One saved place. IN-MEMORY ONLY in Phase 4 — never written to disk
 * (persistence, geofencing and notifications arrive with later phases).
 */
private data class PlaceItem(
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val arriveOn: Boolean,
    val departOn: Boolean,
    val cooldownMin: Int
)

/** Radius choices for the add form (spec §7: radius chips 100/200/500 m). */
private val RADIUS_CHOICES_M = listOf(100, 200, 500)

/** Cooldown choices for the add form (spec §7: default 10 min, min 5). */
private val COOLDOWN_CHOICES_MIN = listOf(5, 10, 15)

/** Default radius until the user picks a chip. */
private const val DEFAULT_RADIUS_M = 200

/** Default cooldown until the user picks a chip (spec §7 default). */
private const val DEFAULT_COOLDOWN_MIN = 10

/**
 * Neutral fallback center for the pick-on-map form when nothing better
 * exists: the demo-fixture area (Dhaka). A real "current position"
 * center needs the sharing flow's permission wiring — later phase; the
 * user simply pans, so the start point claims nothing.
 */
private const val PICKER_FALLBACK_LAT = 23.8103
private const val PICKER_FALLBACK_LNG = 90.4125

/** Picker starting zoom — close enough to place a pin by panning. */
private const val PICKER_ZOOM = 13.0

/**
 * §7 — the own-device places & alerts screen (v1: self-alerts preview).
 *
 * @param onBack pops the route (back row + demo banner ✕).
 */
@Composable
internal fun PlaceAlertsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackTitleRow(title = stringResource(R.string.place_my_title), onBack = onBack)

    // Demo seeding decision captured ONCE at composition: toggling demo
    // mode elsewhere later does not retroactively change this list.
    val seeded = remember { DemoTrackingMode.active }
    if (seeded) {
        // The banner's ✕ leaves this screen (onBack, back to wherever the
        // places entry lives); leaving demo mode happens from the
        // Tracking dashboard.
        DemoBanner(onExit = onBack)
    }

    // In-memory list, seeded once from the demo fixtures when the screen
    // was opened in demo mode; the real side starts empty (nothing is
    // persisted in Phase 4 — persistence arrives with later phases).
    val places = remember(seeded) {
        mutableStateListOf<PlaceItem>().apply {
            if (seeded) {
                DemoTrackingDataSource.places.forEach { place ->
                    add(
                        PlaceItem(
                            name = context.getString(place.nameRes),
                            lat = place.lat,
                            lng = place.lng,
                            radiusMeters = place.radiusMeters,
                            arriveOn = place.arriveOn,
                            departOn = place.departOn,
                            cooldownMin = place.cooldownMin
                        )
                    )
                }
            }
        }
    }
    var showForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (places.isEmpty() && !showForm) {
            EmptyState(
                icon = Icons.Filled.Place,
                title = stringResource(R.string.place_none),
                hint = stringResource(R.string.place_status)
            )
        } else {
            places.forEachIndexed { index, place ->
                PlaceCard(
                    place = place,
                    onChange = { updated -> places[index] = updated },
                    onRemove = { places.removeAt(index) }
                )
            }
        }
        if (!showForm) {
            BigButton(onClick = { showForm = true }) {
                Text(stringResource(R.string.place_add))
            }
        } else {
            AddPlaceForm(
                initialCenterLat = places.firstOrNull()?.lat ?: PICKER_FALLBACK_LAT,
                initialCenterLng = places.firstOrNull()?.lng ?: PICKER_FALLBACK_LNG,
                onSave = { item ->
                    places.add(item)
                    showForm = false
                }
            )
        }
        // Honest scope footers (§7): monitoring only while the location
        // FGS runs, and recipient-facing alerts are a future extension.
        Text(
            text = stringResource(R.string.place_status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.place_future_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One saved place as a card: radius, arrival/departure toggles, cooldown, status, remove. */
@Composable
private fun PlaceCard(
    place: PlaceItem,
    onChange: (PlaceItem) -> Unit,
    onRemove: () -> Unit
) {
    SectionCard(title = place.name, icon = Icons.Filled.Place) {
        // Coordinates-free summary (§7: name + approximate radius).
        Text(
            text = stringResource(R.string.place_radius) + " · " +
                stringResource(R.string.place_radius_fmt, place.radiusMeters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_arrive),
            checked = place.arriveOn,
            onCheckedChange = { checked -> onChange(place.copy(arriveOn = checked)) }
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_depart),
            checked = place.departOn,
            onCheckedChange = { checked -> onChange(place.copy(departOn = checked)) }
        )
        Text(
            text = stringResource(R.string.place_cooldown) + " · " +
                stringResource(R.string.place_cooldown_min, place.cooldownMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.place_status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRemove) {
            Text(stringResource(R.string.place_remove))
        }
    }
}

/**
 * The inline add form: name + pick-on-map (map CENTER captured on save) +
 * radius chips + arrival/departure toggles + cooldown chips. The place is
 * appended to the in-memory list only — never stored, never monitored in
 * Phase 4.
 */
@Composable
private fun AddPlaceForm(
    initialCenterLat: Double,
    initialCenterLng: Double,
    onSave: (PlaceItem) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(DEFAULT_RADIUS_M) }
    var arrive by remember { mutableStateOf(true) }
    var depart by remember { mutableStateOf(false) }
    var cooldown by remember { mutableStateOf(DEFAULT_COOLDOWN_MIN) }

    // The picker MapView — the SAME lifecycle pattern as TrackingTab's
    // CounterpartMapSection (remembered instance, MAPNIK, multi-touch,
    // copyright overlay once, ON_RESUME/ON_PAUSE, onDetach on dispose).
    // Nothing syncs INTO it: the user pans freely and the center is read
    // out only when Save is tapped.
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(PICKER_ZOOM)
            controller.setCenter(GeoPoint(initialCenterLat, initialCenterLng))
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

    SectionCard(
        title = stringResource(R.string.place_add),
        icon = Icons.Filled.Place
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.place_name)) },
            placeholder = { Text(stringResource(R.string.place_name_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.place_pick),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp)),
            factory = { mapView },
            update = { mv -> mv.invalidate() }
        )
        Text(
            text = stringResource(R.string.place_radius),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADIUS_CHOICES_M.forEach { choice ->
                ChoiceChip(
                    label = stringResource(R.string.place_radius_fmt, choice),
                    selected = radius == choice,
                    onClick = { radius = choice }
                )
            }
        }
        PlaceToggleRow(
            label = stringResource(R.string.place_arrive),
            checked = arrive,
            onCheckedChange = { arrive = it }
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_depart),
            checked = depart,
            onCheckedChange = { depart = it }
        )
        Text(
            text = stringResource(R.string.place_cooldown),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COOLDOWN_CHOICES_MIN.forEach { choice ->
                ChoiceChip(
                    label = stringResource(R.string.place_cooldown_min, choice),
                    selected = cooldown == choice,
                    onClick = { cooldown = choice }
                )
            }
        }
        // Blank name → disabled: a place without a name cannot be told
        // apart in the alert text, so it is not saved half-formed.
        BigButton(
            enabled = name.isNotBlank(),
            onClick = {
                onSave(
                    PlaceItem(
                        name = name.trim(),
                        // The position IS the map center the user left
                        // the picker at — nothing is geocoded or tracked.
                        lat = mapView.mapCenter.latitude,
                        lng = mapView.mapCenter.longitude,
                        radiusMeters = radius,
                        arriveOn = arrive,
                        departOn = depart,
                        cooldownMin = cooldown
                    )
                )
            }
        ) {
            Text(stringResource(R.string.place_save))
        }
    }
}

/**
 * One full-width toggle row (≥48dp touch target, whole row toggles —
 * checkbox + label move together for screen readers).
 */
@Composable
private fun PlaceToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null // the row owns the toggle semantics
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Selectable value chip (≥48dp touch target). Same pill language as
 * HistoryScreen's HistoryDayChip — deliberately a private copy in each
 * file (Core.kt is not this agent's file); promotion into the design
 * system belongs to a later dedupe wave.
 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
