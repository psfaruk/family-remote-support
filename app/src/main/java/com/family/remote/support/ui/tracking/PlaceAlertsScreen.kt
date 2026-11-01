// ═══════════════════════════════════════════════════════════════════════
// "My places & alerts" screen, route tracking/places (OWN device, v1
// self-alerts scope — docs/location/LOCATION_UX_SPECIFICATION.md §7).
//
// Phase 4 (v0.38.0/57) shipped this as an in-memory UI preview. Phase 11
// (v0.43.0/63) wires the REAL data path: the list loads from Firestore
// (`placeAlerts`, owner-only rules) on every screen open, the add form /
// toggles / remove persist through the repo (which also refreshes the
// local store + the runtime registry — a running share picks config
// changes up on its next fix), and the location FGS evaluates the fences
// (PlaceAlertEngine) and posts local notifications (name only, never
// coordinates). The honest scope lines stay: monitoring happens only
// while sharing is on (DR-P2 — no background location, no GeofencingClient,
// zero new permissions), and recipient-facing alerts remain a future
// extension.
//
// DEMO mode keeps the Phase 4 contract verbatim: fixtures from
// DemoTrackingDataSource, an in-memory list that never touches the repo,
// the persistent DEMO banner, seeding captured ONCE at composition.
//
// The pick-on-map form centers a small osmdroid map (same lifecycle
// pattern as TrackingTab's CounterpartMapSection) and saves the map
// CENTER as the place's position — deliberately no "current position"
// wiring (no new permission flow); the user pans to the place instead.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.MAX_SAVED_PLACES
import com.family.remote.support.R
import com.family.remote.support.SavedPlace
import com.family.remote.support.createSavedPlace
import com.family.remote.support.deleteSavedPlace
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.publishLoadedPlaces
import com.family.remote.support.readSavedPlaces
import com.family.remote.support.updateSavedPlace
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.family.remote.support.ui.tracking.demo.DemoTrackingMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/**
 * One place as the SHARED card/form visual sees it — both branches (real
 * [SavedPlace], demo fixture) map into it, so the visuals exist once.
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

private fun SavedPlace.toItem() = PlaceItem(
    name = name,
    lat = lat,
    lng = lng,
    radiusMeters = radiusMeters,
    arriveOn = arriveOn,
    departOn = departOn,
    cooldownMin = cooldownMinutes
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
 * §7 — the own-device places & alerts screen. Demo mode (captured ONCE
 * at composition, the Phase 4 contract) renders fixtures; the real side
 * loads the signed-in owner's places from Firestore and persists every
 * change through the repo.
 *
 * @param user   the signed-in account (null → the honest signed-out state).
 * @param onBack pops the route (back row + demo banner ✕).
 */
@Composable
internal fun PlaceAlertsScreen(user: FirebaseUser?, onBack: () -> Unit) {
    val demo = remember { DemoTrackingMode.active }
    if (demo) {
        DemoPlacesSection(onBack = onBack)
        return
    }
    if (user == null) {
        BackTitleRow(title = stringResource(R.string.place_my_title), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.phone_signin_first),
            hint = stringResource(R.string.phone_signin_hint)
        )
        return
    }

    val context = LocalContext.current
    val vm: PlacesViewModel = viewModel()
    var retryKey by remember { mutableStateOf(0) }
    DisposableEffect(user.uid, retryKey) {
        vm.reload(context) { e -> describeFirestoreError(context, e) }
        onDispose { }
    }
    val state by vm.state.collectAsState()

    BackTitleRow(title = stringResource(R.string.place_my_title), onBack = onBack)
    val routedError = state.error?.takeIf { !state.loaded }
    when {
        routedError != null -> ErrorPanel(message = routedError, onRetry = { retryKey++ })
        !state.loaded -> LoadingSkeleton(rows = 3)
        else -> {
            var showForm by remember { mutableStateOf(false) }
            val atCap = state.places.size >= MAX_SAVED_PLACES
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.opError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.places.isEmpty() && !showForm) {
                    EmptyState(
                        icon = Icons.Filled.Place,
                        title = stringResource(R.string.place_none),
                        hint = stringResource(R.string.place_status)
                    )
                } else {
                    state.places.forEach { place ->
                        PlaceCard(
                            item = place.toItem(),
                            enabled = !state.opBusy,
                            onArriveChange = { checked ->
                                vm.updatePlace(context, place.copy(arriveOn = checked)) { e ->
                                    describeFirestoreError(context, e)
                                }
                            },
                            onDepartChange = { checked ->
                                vm.updatePlace(context, place.copy(departOn = checked)) { e ->
                                    describeFirestoreError(context, e)
                                }
                            },
                            onRemove = {
                                vm.removePlace(context, place.id) { e ->
                                    describeFirestoreError(context, e)
                                }
                            }
                        )
                    }
                }
                when {
                    showForm -> AddPlaceForm(
                        initialCenterLat = state.places.firstOrNull()?.lat ?: PICKER_FALLBACK_LAT,
                        initialCenterLng = state.places.firstOrNull()?.lng ?: PICKER_FALLBACK_LNG,
                        // The form itself never closes on Save — the CALLER
                        // decides (real: only after the server confirms, via
                        // the VM's success callback; demo: immediately).
                        // A failed save keeps it open with the op-error
                        // line visible above (nothing is lost).
                        onSave = { item ->
                            vm.createPlace(
                                context,
                                SavedPlace(
                                    id = "",
                                    name = item.name,
                                    lat = item.lat,
                                    lng = item.lng,
                                    radiusMeters = item.radiusMeters,
                                    arriveOn = item.arriveOn,
                                    departOn = item.departOn,
                                    cooldownMinutes = item.cooldownMin
                                ),
                                { e -> describeFirestoreError(context, e) },
                                { showForm = false }
                            )
                        }
                    )
                    atCap -> Text(
                        text = stringResource(R.string.place_limit, MAX_SAVED_PLACES),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> BigButton(
                        enabled = !state.opBusy,
                        onClick = { showForm = true }
                    ) {
                        Text(stringResource(R.string.place_add))
                    }
                }
                HonestScopeFooters()
            }
        }
    }
}

// ── The real side's state holder (the HistoryViewModel pattern) ─────────

private data class PlacesUiState(
    val loaded: Boolean = false,
    val places: List<SavedPlace> = emptyList(),
    /** Load failure (routed to the full ErrorPanel while nothing loaded). */
    val error: String? = null,
    /** CRUD failure (inline line — the loaded list stays visible). */
    val opError: String? = null,
    /** One write in flight: further op attempts are dropped (no queue). */
    val opBusy: Boolean = false
)

/**
 * Phase 11 — the places screen's ViewModel. Every Firestore call here is
 * a user action (screen open / add / toggle / remove — §75); the service
 * NEVER reads this state (it reads the local store + registry the repo
 * refreshes on success).
 */
private class PlacesViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null
    private var opJob: Job? = null

    /** One bounded read per screen open / retry (user action). */
    fun reload(context: android.content.Context, describeError: (Exception) -> String) {
        loadJob?.cancel()
        _state.value = _state.value.copy(error = null)
        loadJob = scope.launch {
            try {
                val places = readSavedPlaces()
                publishLoadedPlaces(context, places)
                _state.value = PlacesUiState(loaded = true, places = places)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = describeError(e))
            }
        }
    }

    /** Create; [onSaved] fires only on success (closes the form). */
    fun createPlace(
        context: android.content.Context,
        draft: SavedPlace,
        describeError: (Exception) -> String,
        onSaved: () -> Unit
    ) {
        if (_state.value.opBusy) return
        _state.value = _state.value.copy(opBusy = true, opError = null)
        opJob = scope.launch {
            try {
                val saved = createSavedPlace(context, _state.value.places, draft)
                _state.value = _state.value.copy(
                    opBusy = false,
                    places = _state.value.places + saved
                )
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(opBusy = false, opError = describeError(e))
            }
        }
    }

    fun updatePlace(
        context: android.content.Context,
        place: SavedPlace,
        describeError: (Exception) -> String
    ) {
        if (_state.value.opBusy) return
        _state.value = _state.value.copy(opBusy = true, opError = null)
        opJob = scope.launch {
            try {
                updateSavedPlace(context, _state.value.places, place)
                _state.value = _state.value.copy(
                    opBusy = false,
                    places = _state.value.places.map { if (it.id == place.id) place else it }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(opBusy = false, opError = describeError(e))
            }
        }
    }

    fun removePlace(
        context: android.content.Context,
        placeId: String,
        describeError: (Exception) -> String
    ) {
        if (_state.value.opBusy) return
        _state.value = _state.value.copy(opBusy = true, opError = null)
        opJob = scope.launch {
            try {
                deleteSavedPlace(context, _state.value.places, placeId)
                _state.value = _state.value.copy(
                    opBusy = false,
                    places = _state.value.places.filterNot { it.id == placeId }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(opBusy = false, opError = describeError(e))
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        opJob?.cancel()
        scope.cancel()
    }
}

// ── The demo side — the Phase 4 contract, verbatim ──────────────────────

/**
 * DEMO places: fixtures seeded ONCE at composition (toggling demo mode
 * elsewhere later does not retroactively change this list), an in-memory
 * list that never touches the repo, the persistent banner, and the same
 * shared card/form visuals. The real monitoring paths are untouched.
 */
@Composable
private fun DemoPlacesSection(onBack: () -> Unit) {
    val context = LocalContext.current
    BackTitleRow(title = stringResource(R.string.place_my_title), onBack = onBack)
    DemoBanner(onExit = onBack)

    val places = remember {
        mutableStateListOf<PlaceItem>().apply {
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
                    item = place,
                    enabled = true,
                    onArriveChange = { checked ->
                        places[index] = place.copy(arriveOn = checked)
                    },
                    onDepartChange = { checked ->
                        places[index] = place.copy(departOn = checked)
                    },
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
        HonestScopeFooters()
    }
}

// ── Shared visuals ───────────────────────────────────────────────────────

/** The two honest scope footers (§7): monitoring scope + future note. */
@Composable
private fun HonestScopeFooters() {
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

/**
 * One saved place as a card: radius, arrival/departure toggles, cooldown,
 * status, remove. [enabled] false while a real write is in flight — the
 * controlled checkbox then simply does not flip (pessimistic by design:
 * nothing changes until the server confirms).
 */
@Composable
private fun PlaceCard(
    item: PlaceItem,
    enabled: Boolean,
    onArriveChange: (Boolean) -> Unit,
    onDepartChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    SectionCard(title = item.name, icon = Icons.Filled.Place) {
        // Coordinates-free summary (§7: name + approximate radius).
        Text(
            text = stringResource(R.string.place_radius) + " · " +
                stringResource(R.string.place_radius_fmt, item.radiusMeters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_arrive),
            checked = item.arriveOn,
            enabled = enabled,
            onCheckedChange = onArriveChange
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_depart),
            checked = item.departOn,
            enabled = enabled,
            onCheckedChange = onDepartChange
        )
        Text(
            text = stringResource(R.string.place_cooldown) + " · " +
                stringResource(R.string.place_cooldown_min, item.cooldownMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.place_status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRemove, enabled = enabled) {
            Text(stringResource(R.string.place_remove))
        }
    }
}

/**
 * The inline add form: name + pick-on-map (map CENTER captured on save) +
 * radius chips + arrival/departure toggles + cooldown chips. The form
 * NEVER closes itself on Save — the caller's [onSave] decides (the real
 * branch closes only after the server confirms; the demo immediately).
 */
@Composable
private fun AddPlaceForm(
    initialCenterLat: Double,
    initialCenterLng: Double,
    onSave: (PlaceItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(DEFAULT_RADIUS_M) }
    var arrive by remember { mutableStateOf(true) }
    var depart by remember { mutableStateOf(false) }
    var cooldown by remember { mutableStateOf(DEFAULT_COOLDOWN_MIN) }

    // The picker MapView — the SAME lifecycle pattern as TrackingTab's
    // CounterpartMapSection (remembered instance, multi-touch, copyright
    // overlay once, ON_RESUME/ON_PAUSE, onDetach on dispose). v0.44.0: the
    // tile source follows the SAME fast style registry (the persisted
    // choice; CARTO Voyager 2D default) — MAPNIK is gone from the app.
    // Nothing syncs INTO it: the user pans freely and the center is read
    // out only when Save is tapped.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            MapStyles.apply(this, MapStyles.current(context))
            setMultiTouchControls(true)
            controller.setZoom(PICKER_ZOOM)
            controller.setCenter(GeoPoint(initialCenterLat, initialCenterLng))
            overlays.add(
                CopyrightOverlay(context).apply {
                    setCopyrightNotice(MapStyles.attribution(MapStyles.current(context)))
                }
            )
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
            enabled = true,
            onCheckedChange = { arrive = it }
        )
        PlaceToggleRow(
            label = stringResource(R.string.place_depart),
            checked = depart,
            enabled = true,
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
 * checkbox + label move together for screen readers). [enabled] false
 * suppresses the change (a write is in flight — the value stays the
 * server's truth); guarded INSIDE onValueChange so the call shape stays
 * the version-portable 3-arg toggleable.
 */
@Composable
private fun PlaceToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
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
                onValueChange = { if (enabled) onCheckedChange(it) }
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
