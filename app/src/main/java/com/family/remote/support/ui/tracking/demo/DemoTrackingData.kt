// ═══════════════════════════════════════════════════════════════════════
// Phase 4 (v0.38.0, frontend mock wave) — the DEMO data source for the
// tracking surfaces. The demo exists so the Phase 5 review can see every
// new screen and EVERY sharing state without a second real account.
//
// Honesty rules baked in (this is the whole point of the demo design):
//   • Demo mode is EXPLICITLY entered (a labeled button) and obviously
//     labeled while active (DemoBanner: "DEMO DATA — not real locations");
//   • process-memory ONLY: DemoTrackingMode.active is a plain Compose
//     snapshot flag — never written to SharedPreferences, so process
//     death resets it and the app can never come back up "stuck in demo";
//   • ZERO Firestore traffic: no listener, no write, no permission launch
//     is ever driven from demo state (TrackingTab tears the real watches
//     down while the demo is active — see the LaunchedEffect keyed on
//     DemoTrackingMode.active there);
//   • fixtures are fictional Dhaka-area coordinates with timestamps
//     computed from System.currentTimeMillis(), so the LIVE member stays
//     live and freshness math degrades exactly like real data would.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.remote.support.LiveLocationView
import com.family.remote.support.MovementConfidence
import com.family.remote.support.MovementEstimate
import com.family.remote.support.MovementKind
import com.family.remote.support.R
import com.family.remote.support.ui.AppWarningColor
import com.family.remote.support.ui.tracking.LocationShareStatus
import com.family.remote.support.ui.tracking.RetentionKind
import com.family.remote.support.ui.tracking.UploadState
import com.family.remote.support.ui.tracking.nextLocalMidnightMillis
import java.util.Calendar

/**
 * Whether the demo dashboard is showing. Process-memory only — NEVER
 * persisted (process death exits the demo), and flipping it never touches
 * Firestore: TrackingTab's observe effect re-runs on this flag and calls
 * vm.observe(null) while it is true, so the real listeners are torn down
 * for the whole demo session.
 */
object DemoTrackingMode {
    var active by mutableStateOf(false)
}

/** Demo relationship ids all start with "demo-" (routing + guards key on this). */
internal fun isDemoRelId(relId: String): Boolean = relId.startsWith("demo-")

/** One movement estimate (always an estimate — the confidence says how weak). */
data class DemoMovement(
    val kind: MovementKind,
    val speedKmh: Double?,
    val observedAtMillis: Long,
    val confidence: MovementConfidence
)

/**
 * Phase 10 — the demo fixture → the classifier's shared vocabulary, so
 * MovementCard has ONE render path (real estimates and demo fixtures
 * map onto the same type; the v0.37.0 single-source discipline).
 */
fun DemoMovement.toMovementEstimate(): MovementEstimate = MovementEstimate(
    kind = kind,
    confidence = confidence,
    speedKmh = speedKmh,
    observedAtMillis = observedAtMillis,
    insufficientEvidence = false
)

/**
 * One fictional family member for the demo dashboard. [status],
 * [historyGranted], [retention] and [expiryMillis] are FIXTURES (the
 * states the Phase 5 review needs to see), not derivations — the demo
 * never pretends a real document produced them.
 */
data class DemoMember(
    val relId: String,
    val nameRes: Int,
    val email: String,
    val deviceModel: String,
    val status: LocationShareStatus,
    val location: LiveLocationView?,
    val historyGranted: Boolean,
    val retention: RetentionKind?,
    val movement: DemoMovement?,
    val areaRes: Int,
    /** Non-null only for the temporary-scope demo member. */
    val expiryMillis: Long?
)

/** One recorded journey point (demo history fixtures). The optional
 *  speed is the OS-provided sensor fact — present only on some fixes,
 *  exactly like real records (hasSpeed() is false without Doppler). */
data class DemoHistoryPoint(
    val timeMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedMetersPerSecond: Double? = null
)

/** One history day's summary: index (0 = yesterday, 1 = today), point count, coverage. */
data class DemoHistoryDay(
    val dayIndex: Int,
    val pointCount: Int,
    val coveragePercent: Int
)

/** One saved place with arrival/departure alert toggles and a quiet-period. */
data class DemoPlace(
    val nameRes: Int,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val arriveOn: Boolean,
    val departOn: Boolean,
    val cooldownMin: Int
)

/**
 * The demo data source: everything is computed from the CURRENT clock on
 * every access, so the LIVE fixture (updated 40 s ago) stays live and the
 * offline fixtures keep aging honestly while the demo is open.
 */
object DemoTrackingDataSource {

    // ── Own-card demo state (process-local; flipped only via the demo UI) ──

    /** The demo own-device status — starts PAUSED so the wizard has a reason to run. */
    var ownStatus by mutableStateOf(LocationShareStatus.SHARING_PAUSED)

    /** The demo own-device upload state — cycles through the four honest lines. */
    var ownUpload by mutableStateOf(UploadState.PENDING)

    // ── Members ────────────────────────────────────────────────────────────

    /**
     * The three fictional members. Timestamps come off the current clock:
     * demo-1 updated 40 s ago (LIVE), demo-2 3.5 h ago (OFFLINE — its
     * fixture status agrees), demo-3 45 min ago with a paused fixture
     * status (the honest freshness line still reads Offline; a paused
     * member's last position is old, never "live").
     */
    val members: List<DemoMember>
        get() {
            val nowMillis = System.currentTimeMillis()
            val ayeshaUpdated = nowMillis - 40_000L
            val kamalUpdated = nowMillis - (3_500_000L + 30_000L) // ~3.5 h
            val nusratUpdated = nowMillis - 45 * 60_000L
            return listOf(
                DemoMember(
                    relId = "demo-1",
                    nameRes = R.string.demo_member_1,
                    email = "ayesha.demo@example.com",
                    deviceModel = "Pixel 8",
                    status = LocationShareStatus.SHARING_LIVE,
                    location = LiveLocationView(
                        uid = "demo-uid-1",
                        deviceId = "demo-dev-1",
                        lat = 23.7461,
                        lng = 90.3760,
                        accuracyMeters = 25,
                        sharingEnabled = true,
                        updatedAtMillis = ayeshaUpdated
                    ),
                    historyGranted = true,
                    retention = RetentionKind.D7,
                    movement = DemoMovement(
                        kind = MovementKind.WALK,
                        speedKmh = 4.2,
                        observedAtMillis = ayeshaUpdated,
                        confidence = MovementConfidence.LIMITED
                    ),
                    areaRes = R.string.demo_area_1,
                    expiryMillis = null
                ),
                DemoMember(
                    relId = "demo-2",
                    nameRes = R.string.demo_member_2,
                    email = "kamal.demo@example.com",
                    deviceModel = "Galaxy A15",
                    status = LocationShareStatus.DEVICE_OFFLINE,
                    location = LiveLocationView(
                        uid = "demo-uid-2",
                        deviceId = "demo-dev-2",
                        lat = 23.7104,
                        lng = 90.4009,
                        accuracyMeters = 68,
                        sharingEnabled = true,
                        updatedAtMillis = kamalUpdated
                    ),
                    historyGranted = false,
                    retention = null,
                    movement = null,
                    areaRes = R.string.demo_area_2,
                    expiryMillis = null
                ),
                DemoMember(
                    relId = "demo-3",
                    nameRes = R.string.demo_member_3,
                    email = "nusrat.demo@example.com",
                    deviceModel = "Redmi Note 12",
                    status = LocationShareStatus.SHARING_PAUSED,
                    location = LiveLocationView(
                        uid = "demo-uid-3",
                        deviceId = "demo-dev-3",
                        lat = 23.7925,
                        lng = 90.4078,
                        accuracyMeters = 40,
                        sharingEnabled = true,
                        updatedAtMillis = nusratUpdated
                    ),
                    historyGranted = true,
                    retention = RetentionKind.H24,
                    movement = null,
                    areaRes = R.string.demo_area_3,
                    // Temporary-scope fixture: expires at today's 23:59:59 local
                    // (the same nextLocalMidnightMillis the wizard's midnight
                    // chip uses — one implementation, no drift).
                    expiryMillis = nextLocalMidnightMillis(nowMillis)
                )
            )
        }

    /** One demo member by relationship id (null for unknown/real ids). */
    fun member(relId: String): DemoMember? =
        members.firstOrNull { it.relId == relId }

    // ── History ────────────────────────────────────────────────────────────

    /** Day summaries per member: demo-1 has yesterday+today, demo-3 today only, demo-2 none.
     * Coverage mirrors the honest route math on the retimed Phase 10 fixtures
     * (yesterday: 5×9-min runs around the one 20-min gap → 82 %; today solid). */
    fun historyDays(relId: String): List<DemoHistoryDay> = when (relId) {
        "demo-1" -> listOf(
            DemoHistoryDay(dayIndex = 0, pointCount = 12, coveragePercent = 82),
            DemoHistoryDay(dayIndex = 1, pointCount = 6, coveragePercent = 100)
        )
        "demo-3" -> listOf(
            DemoHistoryDay(dayIndex = 1, pointCount = 5, coveragePercent = 100)
        )
        else -> emptyList()
    }

    /**
     * Journey points for one day, ascending by time. demo-1's yesterday
     * set carries a deliberate 20-minute GAP (11:50 → 12:10, nothing in
     * between) so the history screen's gap rendering has something true
     * to be honest about. Honesty note: "today's" fixtures are pinned to
     * fixed clock times (08:00–11:00); a review before 11:00 local sees
     * future-dated demo points — acceptable for clearly-labeled fictional
     * data, and deliberately NOT clamped, which would distort the gap and
     * coverage fixtures.
     */
    /**
     * Journey points for one day, ascending by time. demo-1's yesterday
     * set carries a deliberate 20-minute GAP (11:50 → 12:10, nothing in
     * between) so the history screen's gap rendering has something true
     * to dash out; every other pair sits at a ≤9-minute cadence so the
     * route renders as SOLID runs (the Phase 4 fixtures spaced every pair
     * 20–60 min apart, which the >10-min threshold dashed in full — found
     * and retimed in Phase 10). demo-1 today ends with a sensor-backed
     * speed on the last point (MODERATE-confidence movement demo);
     * demo-3's short Gulshan steps honestly read as "possibly stationary".
     */
    fun historyPoints(relId: String, dayIndex: Int): List<DemoHistoryPoint> = when (relId) {
        "demo-1" -> when (dayIndex) {
            0 -> listOf(
                // Yesterday 11:05 → 12:55, walking steps around Dhanmondi
                // drifting toward Lalbagh (with the 20-minute gap mid-route).
                DemoHistoryPoint(atClockTime(-1, 11, 5), 23.7461, 90.3760),
                DemoHistoryPoint(atClockTime(-1, 11, 14), 23.7440, 90.3785),
                DemoHistoryPoint(atClockTime(-1, 11, 23), 23.7410, 90.3810),
                DemoHistoryPoint(atClockTime(-1, 11, 32), 23.7380, 90.3835),
                DemoHistoryPoint(atClockTime(-1, 11, 41), 23.7355, 90.3850),
                DemoHistoryPoint(atClockTime(-1, 11, 50), 23.7320, 90.3870),
                DemoHistoryPoint(atClockTime(-1, 12, 10), 23.7290, 90.3890),
                DemoHistoryPoint(atClockTime(-1, 12, 19), 23.7255, 90.3915),
                DemoHistoryPoint(atClockTime(-1, 12, 28), 23.7225, 90.3935),
                DemoHistoryPoint(atClockTime(-1, 12, 37), 23.7200, 90.3950),
                DemoHistoryPoint(atClockTime(-1, 12, 46), 23.7230, 90.3970),
                DemoHistoryPoint(atClockTime(-1, 12, 55), 23.7260, 90.3990)
            )
            1 -> listOf(
                // Today 08:00 → 08:45, around Dhanmondi; the last fix
                // carries an OS-provided speed (the sensor-backed movement
                // evidence — MODERATE confidence on the card).
                DemoHistoryPoint(atClockTime(0, 8, 0), 23.7405, 90.3755),
                DemoHistoryPoint(atClockTime(0, 8, 9), 23.7430, 90.3770),
                DemoHistoryPoint(atClockTime(0, 8, 18), 23.7450, 90.3788),
                DemoHistoryPoint(atClockTime(0, 8, 27), 23.7465, 90.3805),
                DemoHistoryPoint(atClockTime(0, 8, 36), 23.7448, 90.3822),
                DemoHistoryPoint(atClockTime(0, 8, 45), 23.7425, 90.3835, speedMetersPerSecond = 1.4)
            )
            else -> emptyList()
        }
        "demo-3" -> when (dayIndex) {
            1 -> listOf(
                // Today 08:00 → 08:36, short steps around Gulshan (the
                // honest "possibly stationary" movement demo).
                DemoHistoryPoint(atClockTime(0, 8, 0), 23.7925, 90.4078),
                DemoHistoryPoint(atClockTime(0, 8, 9), 23.7935, 90.4090),
                DemoHistoryPoint(atClockTime(0, 8, 18), 23.7915, 90.4105),
                DemoHistoryPoint(atClockTime(0, 8, 27), 23.7905, 90.4085),
                DemoHistoryPoint(atClockTime(0, 8, 36), 23.7920, 90.4070)
            )
            else -> emptyList()
        }
        else -> emptyList()
    }

    // ── Places ─────────────────────────────────────────────────────────────

    /** The three fictional saved places (Home at demo-1's position, Market, Clinic). */
    val places: List<DemoPlace> = listOf(
        DemoPlace(
            nameRes = R.string.place_demo_home,
            lat = 23.7461,
            lng = 90.3760,
            radiusMeters = 200,
            arriveOn = true,
            departOn = false,
            cooldownMin = 10
        ),
        DemoPlace(
            nameRes = R.string.place_demo_market,
            lat = 23.7275,
            lng = 90.3915,
            radiusMeters = 100,
            arriveOn = true,
            departOn = true,
            cooldownMin = 10
        ),
        DemoPlace(
            nameRes = R.string.place_demo_clinic,
            lat = 23.7947,
            lng = 90.4143,
            radiusMeters = 500,
            arriveOn = false,
            departOn = false,
            cooldownMin = 5
        )
    )

    // ── time helpers ───────────────────────────────────────────────────────

    /**
     * Today's (or [dayOffset] days away from today's) local wall-clock
     * time as a millis timestamp. [dayOffset] is 0 for today, -1 for
     * yesterday (the history day-index convention above).
     */
    private fun atClockTime(dayOffset: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        return cal.timeInMillis
    }
}

/**
 * The persistent demo banner: warning-colored card with the honest
 * "DEMO DATA — not real locations" line and the ✕ exit (demo_exit
 * content description). Shown at the very top of the demo dashboard for
 * as long as the demo is active — the label never goes away.
 */
@Composable
internal fun DemoBanner(onExit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = AppWarningColor().copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = AppWarningColor(),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.demo_banner),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.demo_exit)
                )
            }
        }
    }
}
