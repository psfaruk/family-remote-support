// ═══════════════════════════════════════════════════════════════════════
// Location master plan Phase 4 (v0.38.0/57) + Phase 9 (v0.40.0/59) —
// journey history screen, route tracking/{relId}/history
// (docs/location/LOCATION_UX_SPECIFICATION.md §9, NAVIGATION_MAP §2).
//
// Phase 9 wires the REAL data path on top of the Phase 8 backend: one
// relationship listener resolves the member, a one-shot authorization
// read gates the screen (no grant → honest absence, never a teaser), and
// each selected day loads through ONE bounded composite-index query
// (deviceId equality + the mandatory retentionExpiresAt floor + the
// recordedAt day range — the rules' exact contract; rules are not
// filters). PERMISSION_DENIED on a day renders the honest empty wording
// (records that were there have expired); FAILED_PRECONDITION (the
// project's one-time composite index missing) renders the honest
// setup-hint instead of a fake error storm.
//
// The rendering (map with solid runs / dashed >10-min gaps, start ▶ /
// end ■ markers, approximation-honest summary card) is ONE code path
// shared by the real and the demo worlds through `HistoryPoint` — the
// demo fixtures map onto it, zero Firestore traffic, banner intact.
//
// Honesty rules baked in: the "approximate distance" is a straight-line
// haversine sum between consecutive recorded points — never the exact
// traveled path (hist_sum_note says so on screen); gap spans are drawn
// dashed and labeled, never interpolated as movement; the OSM copyright
// overlay is added once and never removed (Apache-2.0/OSMF policy).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import android.graphics.DashPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.remote.support.LocationHistoryRecord
import com.family.remote.support.LocationSharingPrefs
import com.family.remote.support.MovementClassifier
import com.family.remote.support.MovementSample
import com.family.remote.support.ReceivedLocationAuthorization
import com.family.remote.support.RelationshipView
import com.family.remote.support.deleteAllOwnHistory
import com.family.remote.support.deleteOwnHistoryForDay
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.isFirestoreUnreachable
import com.family.remote.support.localDayStartMillis
import com.family.remote.support.readHistoryDay
import com.family.remote.support.readReceivedLocationAuthorization
import com.family.remote.support.watchRelationships
import com.family.remote.support.R
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.BigButton
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.ErrorPanel
import com.family.remote.support.ui.components.LoadingSkeleton
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.components.WideOutlinedButton
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoHistoryDay
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.family.remote.support.ui.tracking.demo.isDemoRelId
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ── Pure route math (no Android deps) ────────────────────────────────────

/** A >10-minute hole between consecutive recorded points (spec §9 "gap"). */
private const val GAP_THRESHOLD_MILLIS = 10L * 60_000L

/**
 * Phase 9 — ONE render-time route point vocabulary: real Firestore records
 * and the demo fixtures both map onto it, so the map/summary rendering has
 * a single code path (the v0.37.0 single-source discipline). Phase 10 adds
 * the optional OS-provided speed — the sensor-backed movement fact the
 * classifier prefers over point deltas (MODERATE vs LIMITED confidence).
 */
internal data class HistoryPoint(
    val timeMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedMetersPerSecond: Double? = null
)

/** One consecutive-pair segment of the day's route, dashed when it spans a gap. */
private data class RouteSegment(
    val from: HistoryPoint,
    val to: HistoryPoint,
    val isGap: Boolean
)

/** One >10-minute hole (dashed line + hist_gap chip under the map). */
private data class RouteGap(val fromMillis: Long, val toMillis: Long)

/**
 * Split the day's ascending points into consecutive-pair segments; the
 * pairs that span a gap (>10 min) are the dashed placeholders — nothing
 * between recorded points is ever interpolated as movement.
 */
private fun routeSegments(points: List<HistoryPoint>): List<RouteSegment> {
    val segments = ArrayList<RouteSegment>(points.size.coerceAtLeast(1))
    for (i in 1 until points.size) {
        val from = points[i - 1]
        val to = points[i]
        segments.add(
            RouteSegment(
                from = from,
                to = to,
                isGap = to.timeMillis - from.timeMillis > GAP_THRESHOLD_MILLIS
            )
        )
    }
    return segments
}

private fun routeGaps(points: List<HistoryPoint>): List<RouteGap> =
    routeSegments(points)
        .filter { it.isGap }
        .map { RouteGap(it.from.timeMillis, it.to.timeMillis) }

/**
 * Data coverage for the summary card: the share of the day's period that
 * is NOT a >10-minute gap (consecutive spans ≤10 min count as covered;
 * each gap span counts as zero — a gap is a gap, never "partly covered").
 */
private fun routeCoveragePercent(points: List<HistoryPoint>): Int {
    if (points.size < 2) return 100
    val period = points.last().timeMillis - points.first().timeMillis
    if (period <= 0L) return 100
    var covered = 0L
    for (i in 1 until points.size) {
        val dt = points[i].timeMillis - points[i - 1].timeMillis
        if (dt <= GAP_THRESHOLD_MILLIS) covered += dt
    }
    return ((covered * 100L) / period).toInt().coerceIn(0, 100)
}

/**
 * Haversine distance in kilometres between two recorded points. The
 * summary's "approximate distance" is the straight-line sum between
 * consecutive recorded points (gap spans included) — never the exact
 * traveled path; hist_sum_note states that on screen.
 */
private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusKm = 6_371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 2.0 * earthRadiusKm * asin(sqrt(a))
}

private fun routeDistanceKm(points: List<HistoryPoint>): Double {
    var totalKm = 0.0
    for (i in 1 until points.size) {
        val from = points[i - 1]
        val to = points[i]
        totalKm += haversineKm(from.lat, from.lng, to.lat, to.lng)
    }
    return totalKm
}

// ── Map bookkeeping (TrackingTab's MapSyncState pattern) ─────────────────

/**
 * Per-composition map sync bookkeeping: the day's overlay set is rebuilt
 * only when its data key changes, and the camera is fitted once per day
 * selection. Deliberately NOT snapshot state — it is only touched inside
 * AndroidView.update, and it must die with its MapView (a file-level map
 * would leak a detached day's polylines into a fresh MapView).
 */
private class HistoryMapSync {
    var overlaysKey: String? = null
    var fittedKey: String? = null
}

/** Neutral "world view" starting zoom before the first camera fit. */
private const val HISTORY_WORLD_ZOOM = 3.0

/** Street-level fallback when a day holds a single recorded point. */
private const val HISTORY_FOCUS_ZOOM = 16.0

/** Camera bounding-box padding (TrackingTab's CounterpartMapSection value). */
private const val CAMERA_BOUNDING_PADDING = 80

/** Solid recorded-route stroke width, in px (density-scaled by osmdroid). */
private const val ROUTE_STROKE_PX = 8f

/** Dashed no-data placeholder stroke width, in px. */
private const val GAP_STROKE_PX = 5f

// ── The real screen's data owner ─────────────────────────────────────────

/** UiState for the history watch: resolved relationship + grant + one day. */
private data class HistoryUiState(
    val rel: RelationshipView? = null,
    val authz: ReceivedLocationAuthorization? = null,
    /** True once the one-shot grant read finished (doc or no doc) — the
     * gate between the honest skeleton and any absence wording. */
    val authzReadDone: Boolean = false,
    val selectedDayBack: Int = 0,
    val dayLoading: Boolean = false,
    val dayPoints: List<LocationHistoryRecord> = emptyList(),
    val dayError: Exception? = null,
    val loaded: Boolean = false,
    val error: String? = null
)

/**
 * Phase 9 — the history screen's private ViewModel (the
 * LocationDetailViewModel shape): one relationship listener resolving
 * [relId], ONE one-shot authorization read per resolved counterpart, and
 * one cancellable day-query per selection. All Firestore access is
 * user-action-driven one-shot work (§75); the relationship listener is
 * the same one every family screen uses.
 */
private class HistoryViewModel : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var stopWatch: (() -> Unit)? = null
    private var relationships: List<RelationshipView> = emptyList()
    private var relId: String = ""
    private var myUid: String? = null
    private var authzKey: Pair<String, String>? = null
    private var lastDayBack = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dayJob: Job? = null

    fun observe(user: FirebaseUser?, relId: String, describeError: (Exception) -> String) {
        stopWatch?.invoke()
        stopWatch = null
        dayJob?.cancel()
        dayJob = null
        relationships = emptyList()
        authzKey = null
        lastDayBack = 0
        this.relId = relId
        myUid = user?.uid
        _state.value = HistoryUiState()
        if (user == null) return
        stopWatch = watchRelationships(
            user,
            onUpdate = { rels ->
                relationships = rels
                syncAuthzRead()
                emit()
            },
            onError = { e ->
                _state.value = _state.value.copy(error = describeError(e))
            }
        )
    }

    private fun currentRel(): RelationshipView? =
        relationships.firstOrNull { it.id == relId }

    /**
     * ONE grant read per resolved (still-authorized) counterpart — the
     * authzKey diff keeps rel-listener churn from re-reading. The day's
     * records load right after the gate lands.
     */
    private fun syncAuthzRead() {
        val rel = currentRel() ?: return
        val uid = myUid ?: return
        if (!rel.isTrackableCounterpart(uid)) return
        val key = rel.recipientUid to rel.recipientDeviceId
        if (key == authzKey) return
        authzKey = key
        scope.launch {
            try {
                val authz = readReceivedLocationAuthorization(key.first, key.second)
                _state.value = _state.value.copy(authz = authz, authzReadDone = true)
                loadDay(lastDayBack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(authzReadDone = true, dayError = e)
            }
        }
    }

    fun selectDay(daysBack: Int) {
        lastDayBack = daysBack
        _state.value = _state.value.copy(selectedDayBack = daysBack)
        loadDay(daysBack)
    }

    fun retryDay() {
        if (_state.value.authz == null) {
            // The gate read itself failed — retry the gate (the key reset
            // forces a fresh one-shot read; nothing else is re-fetched).
            authzKey = null
            syncAuthzRead()
            return
        }
        loadDay(lastDayBack)
    }

    private fun loadDay(daysBack: Int) {
        val rel = currentRel() ?: return
        val authz = _state.value.authz ?: return
        if (!authz.activeAt(System.currentTimeMillis()) || !authz.historyEnabled) return
        dayJob?.cancel()
        _state.value = _state.value.copy(dayLoading = true, dayError = null)
        dayJob = scope.launch {
            try {
                val now = System.currentTimeMillis()
                val dayStart = localDayStartMillis(now, daysBack)
                val dayEnd = localDayStartMillis(now, daysBack - 1)
                val points = readHistoryDay(rel.recipientUid, rel.recipientDeviceId, dayStart, dayEnd)
                _state.value = _state.value.copy(dayPoints = points, dayLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(dayPoints = emptyList(), dayLoading = false, dayError = e)
            }
        }
    }

    private fun emit() {
        _state.value = _state.value.copy(rel = currentRel(), loaded = true)
    }

    override fun onCleared() {
        stopWatch?.invoke()
        stopWatch = null
        dayJob?.cancel()
        dayJob = null
        scope.cancel()
    }
}

// ── The screen ───────────────────────────────────────────────────────────

/**
 * §9 — the journey history screen for one member.
 *
 * @param user   the signed-in account (null → the honest signed-out state).
 * @param relId  the member id from the route argument (real id → the
 *               Phase 9 wired data path; "demo-" id → the fixture route).
 * @param onBack pops the route (back row + demo banner ✕).
 */
@Composable
internal fun HistoryScreen(user: FirebaseUser?, relId: String, onBack: () -> Unit) {
    if (isDemoRelId(relId)) {
        DemoHistorySection(relId = relId, onBack = onBack)
        return
    }

    val context = LocalContext.current
    if (user == null) {
        BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.phone_signin_first),
            hint = stringResource(R.string.phone_signin_hint)
        )
        return
    }

    val vm: HistoryViewModel = viewModel()
    var retryKey by remember { mutableStateOf(0) }
    DisposableEffect(user.uid, relId, retryKey) {
        vm.observe(user, relId) { e -> describeFirestoreError(context, e) }
        onDispose { }
    }
    val state by vm.state.collectAsState()

    val routedError = state.error?.takeIf { !state.loaded }
    when {
        routedError != null -> {
            BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
            ErrorPanel(message = routedError, onRetry = { retryKey++ })
        }
        !state.loaded -> {
            BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
            LoadingSkeleton(rows = 3)
        }
        else -> {
            val rel = state.rel
            if (rel == null || rel.status != "ACTIVE" || !rel.guestLive || rel.recipientUid == user.uid) {
                // Honest not-found: revoked/expired/removed from the other
                // side (or a stale route argument, or the own device —
                // v1 has no owner-side history browser).
                BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
                EmptyState(
                    icon = Icons.Filled.Phone,
                    title = stringResource(R.string.fam_not_found),
                    hint = stringResource(R.string.fam_not_found_h)
                )
                WideOutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.cd_back))
                }
            } else {
                RealHistoryBody(
                    rel = rel,
                    state = state,
                    onSelectDay = { vm.selectDay(it) },
                    onRetryDay = { vm.retryDay() },
                    onBack = onBack
                )
            }
        }
    }
}

/** The granted member's history: gate → day chips → day body + retention line. */
@Composable
private fun RealHistoryBody(
    rel: RelationshipView,
    state: HistoryUiState,
    onSelectDay: (Int) -> Unit,
    onRetryDay: () -> Unit,
    onBack: () -> Unit
) {
    val authz = state.authz
    val nowMillis = remember { System.currentTimeMillis() }

    BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
    Text(
        text = counterpartEmail(rel),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    if (!state.authzReadDone || authz == null) {
        if (!state.authzReadDone) {
            // The gate read is in flight — the honest skeleton, never a
            // premature "no records" verdict.
            LoadingSkeleton(rows = 2)
        } else if (state.dayError != null) {
            // The gate read itself failed (network) — an honest retryable
            // error, never a false "not granted" verdict.
            DayErrorBody(error = state.dayError!!, onRetry = onRetryDay)
        } else {
            // Read finished, no grant doc: the honest absence — no
            // locked/teaser cards (spec §4-B "else nothing").
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.hist_none),
                hint = stringResource(R.string.hist_sum_note)
            )
        }
        return
    }

    val nowActive = authz.activeAt(nowMillis)
    when {
        authz.revoked -> EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.hist_none),
            hint = stringResource(R.string.hist_access_end)
        )
        !nowActive -> EmptyState(
            // Expired TEMPORARY/ONCE grant: the honest "not kept" wording.
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.hist_none),
            hint = stringResource(R.string.hist_sum_note)
        )
        !authz.historyEnabled -> EmptyState(
            // Granted live-only: history was never part of this grant.
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.hist_none),
            hint = stringResource(R.string.hist_sum_note)
        )
        else -> {
            val retentionDays = when (authz.retentionPolicy) {
                "D7" -> 7
                "D30" -> 30
                else -> 1 // H24 (the rules never pair NONE with historyEnabled)
            }
            // Day chips across the grant's retention window (the spec's
            // "days where records exist" needs a server-side metadata doc
            // the published rules do not carry — window days + honest
            // per-day empties is the quota-safe v1 reading; recorded in
            // the progress doc).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (daysBack in 0 until retentionDays) {
                    HistoryDayChip(
                        label = dayChipLabel(daysBack, nowMillis),
                        selected = daysBack == state.selectedDayBack,
                        onClick = { onSelectDay(daysBack) }
                    )
                }
            }

            when {
                state.dayLoading -> LoadingSkeleton(rows = 2)
                state.dayError != null -> DayErrorBody(
                    error = state.dayError!!,
                    onRetry = onRetryDay
                )
                else -> {
                    val points = state.dayPoints.map {
                        HistoryPoint(
                            timeMillis = it.recordedAtMillis,
                            lat = it.lat,
                            lng = it.lng,
                            speedMetersPerSecond = it.speedMetersPerSecond
                        )
                    }
                    if (points.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.LocationOn,
                            title = stringResource(R.string.hist_none),
                            hint = stringResource(R.string.hist_sum_note)
                        )
                    } else {
                        HistoryDaySection(
                            points = points,
                            coveragePercent = routeCoveragePercent(points)
                        )
                    }
                }
            }

            // Retention in force — always shown (spec §9's closing line).
            Text(
                text = stringResource(
                    R.string.hist_ret,
                    retentionLabel(retentionKindFromPolicy(authz.retentionPolicy))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The day error ladder: denied → honest empty; precondition → index hint. */
@Composable
private fun DayErrorBody(error: Exception, onRetry: () -> Unit) {
    val context = LocalContext.current
    when {
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            // The rules refused the day's query (expired records inside the
            // range, or a grant that ended between gate and query): the
            // honest wording is "not kept" — never a fabricated route.
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.hist_none),
                hint = stringResource(R.string.hist_sum_note)
            )
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            // The project's one-time composite index is missing — an
            // honest setup hint, not a generic error.
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.hist_index_hint),
                hint = stringResource(R.string.hist_sum_note)
            )
        else -> ErrorPanel(
            message = describeFirestoreError(context, error),
            onRetry = onRetry
        )
    }
}

/** Chip label for a day offset back from today (never a fabricated word). */
@Composable
private fun dayChipLabel(daysBack: Int, nowMillis: Long): String = when (daysBack) {
    0 -> stringResource(R.string.hist_today)
    1 -> stringResource(R.string.hist_yesterday)
    else -> SimpleDateFormat("d MMM", Locale.getDefault())
        .format(Date(localDayStartMillis(nowMillis, daysBack)))
}

// ── The demo world (Phase 4 fixtures — zero Firestore traffic) ───────────

/**
 * The demo member's history (tracking/{demo-relId} while demo mode is on):
 * the SAME rendering path as the real screen, fed by fixtures. The banner
 * stays on top; the ✕ leaves this screen (onBack).
 */
@Composable
private fun DemoHistorySection(relId: String, onBack: () -> Unit) {
    val member = DemoTrackingDataSource.member(relId)
    if (member == null) {
        // Unknown demo id (stale route argument) — the honest not-found
        // state, same wording as the detail screen's branch.
        BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.Phone,
            title = stringResource(R.string.fam_not_found),
            hint = stringResource(R.string.fam_not_found_h)
        )
        return
    }

    BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
    Text(
        text = stringResource(member.nameRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    // The banner's ✕ leaves this screen (onBack, back to the member
    // detail); leaving demo mode happens from the Tracking dashboard.
    DemoBanner(onExit = onBack)

    // Only days that actually have fixture records become chips (§9:
    // "last N days where records exist"), latest day selected by default.
    val days = remember(relId) { DemoTrackingDataSource.historyDays(relId) }
    var selectedDay by remember(relId) { mutableStateOf(days.maxOfOrNull { it.dayIndex } ?: 0) }

    if (days.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.hist_none),
            hint = stringResource(R.string.hist_sum_note)
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day ->
                HistoryDayChip(
                    label = demoDayLabel(relId, day.dayIndex),
                    selected = day.dayIndex == selectedDay,
                    onClick = { selectedDay = day.dayIndex }
                )
            }
        }
        val selected = days.firstOrNull { it.dayIndex == selectedDay }
            // Unreachable defensive fallback (chips only pick existing
            // days; the default is the latest existing day).
            ?: days.maxByOrNull { it.dayIndex }
        if (selected != null) {
            DemoHistoryDaySection(relId = relId, day = selected)
        }
    }

    // Retention in force — always shown, null retention maps to the
    // honest "don't save history" word via retentionLabel.
    Text(
        text = stringResource(R.string.hist_ret, retentionLabel(member.retention)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Chip label for a fixture day (dayIndex 1 = today, 0 = yesterday; any
 * other index falls back to the day's first recorded time — never a
 * fabricated relative word).
 */
@Composable
private fun demoDayLabel(relId: String, dayIndex: Int): String = when (dayIndex) {
    1 -> stringResource(R.string.hist_today)
    0 -> stringResource(R.string.hist_yesterday)
    else -> DemoTrackingDataSource.historyPoints(relId, dayIndex)
        .firstOrNull()?.let { locationTimeString(it.timeMillis) }
        ?: ""
}

/** The selected fixture day, mapped onto the shared rendering path. */
@Composable
private fun DemoHistoryDaySection(relId: String, day: DemoHistoryDay) {
    val points = remember(relId, day.dayIndex) {
        DemoTrackingDataSource.historyPoints(relId, day.dayIndex)
            .map {
                HistoryPoint(
                    timeMillis = it.timeMillis,
                    lat = it.lat,
                    lng = it.lng,
                    speedMetersPerSecond = it.speedMetersPerSecond
                )
            }
    }
    if (points.isEmpty()) {
        SectionCard(
            title = stringResource(R.string.sec_b_note),
            icon = Icons.Filled.DateRange
        ) {
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.hist_none),
                hint = stringResource(R.string.hist_sum_note)
            )
        }
        return
    }
    HistoryDaySection(points = points, coveragePercent = day.coveragePercent)
}

// ── The shared day rendering (real + demo) ───────────────────────────────

/** The selected day: route map + legend + gap chips + summary card. */
@Composable
private fun HistoryDaySection(points: List<HistoryPoint>, coveragePercent: Int) {
    if (points.isEmpty()) {
        // Empty day — hist_none inside the card area: "either sharing was
        // off or history wasn't enabled" (§9's honest empty wording).
        SectionCard(
            title = stringResource(R.string.sec_b_note),
            icon = Icons.Filled.DateRange
        ) {
            EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.hist_none),
                hint = stringResource(R.string.hist_sum_note)
            )
        }
        return
    }

    val gaps = remember(points) { routeGaps(points) }

    // Phase 10 — the journey-tail movement estimate, classified at RENDER
    // time from the records this screen already holds (zero extra Firestore
    // reads; design D4: never stored server-side). The observedAt line on
    // the card discloses when the estimate was observed.
    val movement = remember(points) {
        MovementClassifier.classifyJourneyTail(
            points.map { MovementSample(it.timeMillis, it.lat, it.lng, it.speedMetersPerSecond) }
        )
    }

    HistoryRouteMap(points = points)

    // Legend + per-gap chips under the map (the dashed spans' times).
    Text(
        text = stringResource(R.string.hist_legend),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (gaps.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            gaps.forEach { gap ->
                GapChip(
                    text = stringResource(
                        R.string.hist_gap,
                        locationTimeString(gap.fromMillis),
                        locationTimeString(gap.toMillis)
                    )
                )
            }
        }
    }

    // Summary card (§9, only with data — the empty branch returned above).
    SectionCard(
        title = stringResource(R.string.sec_b_note),
        icon = Icons.Filled.DateRange
    ) {
        val totalKm = remember(points) { routeDistanceKm(points) }
        Text(
            text = stringResource(R.string.hist_sum_points, points.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.hist_sum_dist,
                String.format(Locale.getDefault(), "%.1f km", totalKm)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.hist_sum_time,
                locationTimeString(points.first().timeMillis),
                locationTimeString(points.last().timeMillis)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.hist_sum_cov, coveragePercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.hist_sum_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Phase 10 — the movement insights card (spec §6 "re-usable by the
    // journey-history screen"), under the summary: the honest estimate
    // words, confidence word only, observation time disclosed.
    MovementCard(estimate = movement)
}

/**
 * The day's route on an osmdroid MapView — the SAME lifecycle pattern as
 * TrackingTab's CounterpartMapSection: the MapView is remembered (one
 * instance across recompositions) and wired to the host lifecycle
 * (ON_RESUME/ON_PAUSE forward to osmdroid, disposal detaches the view),
 * MAPNIK tiles, multi-touch, and the OSM copyright overlay added once
 * (index 0) and never removed.
 */
@Composable
private fun HistoryRouteMap(points: List<HistoryPoint>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val segments = remember(points) { routeSegments(points) }

    // Resolved in composition so the (non-composable) update lambda only
    // arranges already-localized strings and data.
    val startLabel = stringResource(R.string.hist_start)
    val endLabel = stringResource(R.string.hist_end)
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val gapColor = MaterialTheme.colorScheme.outline.toArgb()

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(HISTORY_WORLD_ZOOM)
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
    val sync = remember { HistoryMapSync() }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(14.dp)),
        factory = { mapView },
        update = { mv ->
            val key = "day:${points.size}:${points.firstOrNull()?.timeMillis ?: 0}"
            if (key != sync.overlaysKey) {
                sync.overlaysKey = key
                // Drop the previous day's polylines/markers; the copyright
                // overlay was added first and is never removed.
                while (mv.overlays.size > 1) {
                    mv.overlays.removeAt(mv.overlays.size - 1)
                }
                // One polyline per consecutive pair: solid inside a
                // contiguous run, dashed across a >10-minute gap.
                segments.forEach { segment ->
                    val line = Polyline(mv)
                    line.setPoints(
                        listOf(
                            GeoPoint(segment.from.lat, segment.from.lng),
                            GeoPoint(segment.to.lat, segment.to.lng)
                        )
                    )
                    line.outlinePaint.color = if (segment.isGap) gapColor else routeColor
                    line.outlinePaint.strokeWidth =
                        if (segment.isGap) GAP_STROKE_PX else ROUTE_STROKE_PX
                    if (segment.isGap) {
                        line.outlinePaint.pathEffect =
                            DashPathEffect(floatArrayOf(20f, 12f), 0f)
                    }
                    mv.overlays.add(line)
                }
                // Start (green ▶) / end (■) markers with their times —
                // the tiny dedicated drawables; tap shows the title.
                val first = points.first()
                val last = points.last()
                ContextCompat.getDrawable(context, R.drawable.ic_hist_start)?.let { icon ->
                    val marker = Marker(mv)
                    marker.setPosition(GeoPoint(first.lat, first.lng))
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.setIcon(icon)
                    marker.setTitle(startLabel + " " + locationTimeString(first.timeMillis))
                    mv.overlays.add(marker)
                }
                ContextCompat.getDrawable(context, R.drawable.ic_hist_end)?.let { icon ->
                    val marker = Marker(mv)
                    marker.setPosition(GeoPoint(last.lat, last.lng))
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.setIcon(icon)
                    marker.setTitle(endLabel + " " + locationTimeString(last.timeMillis))
                    mv.overlays.add(marker)
                }
            }
            // Camera fitted ONCE per day selection (MapSyncState fit-key).
            if (sync.fittedKey != key) {
                sync.fittedKey = key
                if (points.size == 1) {
                    mv.controller.setZoom(HISTORY_FOCUS_ZOOM)
                    mv.controller.setCenter(GeoPoint(points[0].lat, points[0].lng))
                } else {
                    mv.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(
                            points.map { GeoPoint(it.lat, it.lng) }
                        ),
                        false,
                        CAMERA_BOUNDING_PADDING
                    )
                }
            }
            mv.invalidate()
        }
    )
}

/**
 * Selectable day chip (≥48dp touch target, Phase 5 a11y). Same pill
 * language as AppListTile's trailing chip; deliberately a private copy —
 * Core.kt is not this agent's file, so the shared selectable pill belongs
 * to a later dedupe wave (PlaceAlertsScreen carries the same ChoiceChip).
 */
@Composable
private fun HistoryDayChip(
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

/**
 * "No data HH:mm – HH:mm" pill — the STALE freshness color language
 * (freshnessChipColors' warning pair), the codebase's warning convention.
 */
@Composable
private fun GapChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = AppWarningColor(),
        modifier = Modifier
            .background(
                AppWarningColor().copy(alpha = 0.18f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ── Phase 9 — "Delete my history" (UX spec §9/§10: wizard confirm & Settings) ──

/** The delete dialog's phases. */
private const val DEL_PHASE_CHOOSE = 0
private const val DEL_PHASE_RUNNING = 1
private const val DEL_PHASE_DONE = 2
private const val DEL_PHASE_FAILED = 3
private const val DEL_PHASE_OFFLINE = 4

/**
 * The owner-side "Delete my journey history" dialog (ONE implementation —
 * Settings' row and the wizard's confirm step both render it). Scope
 * choice (today / everything) → bounded deletion → verified-empty done
 * state; offline failures queue honestly (`del_offline` — flushed on the
 * next user action, never a false success).
 */
@Composable
internal fun DeleteHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(DEL_PHASE_CHOOSE) }

    fun runDelete(deleteEverything: Boolean) {
        phase = DEL_PHASE_RUNNING
        val todayStart = localDayStartMillis(System.currentTimeMillis(), 0)
        scope.launch {
            try {
                if (deleteEverything) {
                    deleteAllOwnHistory(context)
                } else {
                    deleteOwnHistoryForDay(context, todayStart)
                }
                phase = DEL_PHASE_DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFirestoreUnreachable(e)) {
                    // Offline: queue for the next user action (the flush
                    // hook retries it on Tracking resume).
                    if (deleteEverything) {
                        LocationSharingPrefs.setPendingDeleteAll(context, true)
                    } else {
                        LocationSharingPrefs.setPendingDeleteDayStart(context, todayStart)
                    }
                    phase = DEL_PHASE_OFFLINE
                } else {
                    phase = DEL_PHASE_FAILED
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (phase != DEL_PHASE_RUNNING) onDismiss() },
        title = { Text(stringResource(R.string.del_title)) },
        text = {
            when (phase) {
                DEL_PHASE_CHOOSE -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.del_confirm_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    WideOutlinedButton(onClick = { runDelete(false) }) {
                        Text(stringResource(R.string.del_scope_today))
                    }
                    BigButton(onClick = { runDelete(true) }) {
                        Text(stringResource(R.string.del_scope_all))
                    }
                }
                DEL_PHASE_RUNNING -> Text(stringResource(R.string.del_progress))
                DEL_PHASE_DONE -> Text(stringResource(R.string.del_done))
                DEL_PHASE_FAILED -> Text(stringResource(R.string.del_failed))
                else -> Text(stringResource(R.string.del_offline))
            }
        },
        confirmButton = {
            when (phase) {
                DEL_PHASE_RUNNING -> TextButton(enabled = false, onClick = {}) {
                    Text(stringResource(R.string.del_progress))
                }
                DEL_PHASE_CHOOSE -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.setup_cancel))
                }
                else -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.del_close))
                }
            }
        }
    )
}
