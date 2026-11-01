// ═══════════════════════════════════════════════════════════════════════
// Phase 4 of the Location master plan (v0.38.0/57) — journey history
// screen, route tracking/{relId}/history
// (docs/location/LOCATION_UX_SPECIFICATION.md §9, NAVIGATION_MAP §2).
//
// Real relationship ids have NO history backend in Phase 4 (it arrives
// with Phases 8/9 — server-side retention + reads): the screen renders
// the honest empty state, never a fabricated route. Demo member ids
// ("demo-" prefix) render the fixture route from DemoTrackingDataSource:
// a day chip row, an osmdroid route map (solid polylines per contiguous
// run of recorded points, DASHED polylines across >10-minute gaps plus
// a hist_gap chip), green ▶ start / ■ end markers with times, and the
// approximation-honest summary card.
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.family.remote.support.R
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.components.BackTitleRow
import com.family.remote.support.ui.components.EmptyState
import com.family.remote.support.ui.components.SectionCard
import com.family.remote.support.ui.tracking.demo.DemoBanner
import com.family.remote.support.ui.tracking.demo.DemoHistoryDay
import com.family.remote.support.ui.tracking.demo.DemoHistoryPoint
import com.family.remote.support.ui.tracking.demo.DemoTrackingDataSource
import com.family.remote.support.ui.tracking.demo.isDemoRelId
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ── Pure route math (no Android deps) ────────────────────────────────────

/** A >10-minute hole between consecutive recorded points (spec §9 "gap"). */
private const val GAP_THRESHOLD_MILLIS = 10L * 60_000L

/** One consecutive-pair segment of the day's route, dashed when it spans a gap. */
private data class RouteSegment(
    val from: DemoHistoryPoint,
    val to: DemoHistoryPoint,
    val isGap: Boolean
)

/** One >10-minute hole (dashed line + hist_gap chip under the map). */
private data class RouteGap(val fromMillis: Long, val toMillis: Long)

/**
 * Split the day's ascending points into consecutive-pair segments; the
 * pairs that span a gap (>10 min) are the dashed placeholders — nothing
 * between recorded points is ever interpolated as movement.
 */
private fun routeSegments(points: List<DemoHistoryPoint>): List<RouteSegment> {
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

private fun routeGaps(points: List<DemoHistoryPoint>): List<RouteGap> =
    routeSegments(points)
        .filter { it.isGap }
        .map { RouteGap(it.from.timeMillis, it.to.timeMillis) }

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

private fun routeDistanceKm(points: List<DemoHistoryPoint>): Double {
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

// ── The screen ───────────────────────────────────────────────────────────

/**
 * §9 — the journey history screen for one member.
 *
 * @param relId  the member id from the route argument (real id → honest
 *               empty; "demo-" id → the fixture route).
 * @param onBack pops the route (back row + demo banner ✕).
 */
@Composable
internal fun HistoryScreen(relId: String, onBack: () -> Unit) {
    if (!isDemoRelId(relId)) {
        // Real relationship: Phase 4 has no history backend (retention +
        // reads arrive with Phases 8/9), so the honest answer is the
        // empty state — no route is fabricated from the live position.
        // The approximation note is a harmless, honest hint here.
        BackTitleRow(title = stringResource(R.string.hist_title), onBack = onBack)
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.hist_none),
            hint = stringResource(R.string.hist_sum_note)
        )
        return
    }

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
                    label = historyDayLabel(relId, day.dayIndex),
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
            HistoryDaySection(relId = relId, day = selected)
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
 * Chip label for a recorded day. The fixtures use the last two days
 * (dayIndex 1 = today, 0 = yesterday); any other index (none exist in the
 * fixture set) falls back to the day's first recorded time — never a
 * fabricated relative word.
 */
@Composable
private fun historyDayLabel(relId: String, dayIndex: Int): String = when (dayIndex) {
    1 -> stringResource(R.string.hist_today)
    0 -> stringResource(R.string.hist_yesterday)
    else -> DemoTrackingDataSource.historyPoints(relId, dayIndex)
        .firstOrNull()?.let { locationTimeString(it.timeMillis) }
        ?: ""
}

/** The selected day: route map + legend + gap chips + summary card. */
@Composable
private fun HistoryDaySection(relId: String, day: DemoHistoryDay) {
    // Ascending-by-time fixture points for this day.
    val points = remember(relId, day.dayIndex) {
        DemoTrackingDataSource.historyPoints(relId, day.dayIndex)
    }
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

    HistoryRouteMap(relId = relId, dayIndex = day.dayIndex, points = points)

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
            text = stringResource(R.string.hist_sum_cov, day.coveragePercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.hist_sum_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
private fun HistoryRouteMap(
    relId: String,
    dayIndex: Int,
    points: List<DemoHistoryPoint>
) {
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
            val key = "$relId:$dayIndex:${points.size}"
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
