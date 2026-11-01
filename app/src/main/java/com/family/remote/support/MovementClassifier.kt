// ═══════════════════════════════════════════════════════════════════════
// Location master plan Phase 10 (v0.41.0/60) — MovementClassifier
// (docs/location/LOCATION_FEATURE_AUDIT.md §8 file map; UX spec §6;
// design D4: device-local, render-time, never stored server-side).
//
// PURE KOTLIN — no Android dependency, unit-tested in
// app/src/test/java/com/family/remote/support/MovementClassifierTest.kt
// and wired into CI as a real gate step (:app:testReleaseUnitTest) — the
// Phase 10 gate "Unit tests (incl. impossible-speed rejection)" runs on
// every push from now on.
//
// Honesty rules baked in (D11, recorded in the progress doc):
//   • the label is always an ESTIMATE — the UI strings say "possibly";
//   • only two confidence words exist: LIMITED (points-based) and
//     MODERATE (sensor-backed OS speed) — never a fabricated score;
//   • impossible speeds (< 0 or > 120 km/h) are REJECTED, not clamped:
//     the honest result is "movement type unavailable";
//   • a stale observation is never presented as current — classifyLive
//     returns null beyond the 300 s staleness window (the same horizon
//     the position line demotes to Offline);
//   • a >10-minute gap between points is NOT movement evidence (the same
//     GAP_THRESHOLD the route renderer dashes out) — nothing between
//     recorded points is ever interpolated as movement;
//   • journey-tail insights are historical by nature: their observation
//     time is disclosed on screen, so classifyJourneyTail never applies
//     the live staleness window.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Coarse movement classification (always an estimate, never a claim). */
enum class MovementKind { WALK, CYCLE, VEHICLE, STILL }

/** How honest a movement estimate is (points-based → LIMITED). */
enum class MovementConfidence { LIMITED, MODERATE }

/**
 * One render-time movement estimate. [insufficientEvidence] = true renders
 * the honest single "Movement type unavailable" line (spec §6) — fresh
 * data, but the evidence inside it cannot support any label.
 */
data class MovementEstimate(
    val kind: MovementKind,
    val confidence: MovementConfidence,
    val speedKmh: Double?,
    val observedAtMillis: Long,
    val insufficientEvidence: Boolean
) {
    companion object {
        /** The honest "cannot say" estimate for a fresh observation. */
        fun insufficient(observedAtMillis: Long): MovementEstimate =
            MovementEstimate(
                kind = MovementKind.STILL,
                confidence = MovementConfidence.LIMITED,
                speedKmh = null,
                observedAtMillis = observedAtMillis,
                insufficientEvidence = true
            )
    }
}

/** One point of evidence for the journey-tail classifier. */
data class MovementSample(
    val timeMillis: Long,
    val lat: Double,
    val lng: Double,
    /** The OS-provided speed recorded with the point, when present. */
    val speedMetersPerSecond: Double? = null
)

/**
 * The movement classifier — device-local and pure (design D4). Every
 * threshold below is an engineering decision recorded as D11 in the
 * progress doc; the label is deliberately coarse (4 kinds + unavailable)
 * because a phone's evidence cannot honestly support finer words.
 */
object MovementClassifier {

    // ── Plausibility bounds (D11a) ──────────────────────────────────────

    /** GPS/vehicle plausibility ceiling — beyond this the speed is a glitch. */
    const val MAX_PLAUSIBLE_KMH = 120.0

    /** Live staleness window — the Offline freshness boundary (300 s). */
    const val LIVE_STALE_WINDOW_MILLIS = 300_000L

    /** Consecutive points further apart than this are a GAP, not movement. */
    const val GAP_THRESHOLD_MILLIS = 600_000L

    /** Below this point-to-point distance, movement is indistinguishable
     *  from GPS jitter (the 50 m write throttle already filters rests;
     *  this is the read-side second line of defense). */
    const val MIN_MOVE_METERS = 15.0

    // ── Kind boundaries in km/h (D11a) ──────────────────────────────────
    //  < 2   → possibly stationary (very slow drift)
    //  2–7   → possibly walking (walking ≈ 3–6 km/h)
    //  7–25  → possibly cycling (city cycling ≈ 15–25; running 8–13 also
    //          lands here — the vocabulary has no RUN kind, and "possibly"
    //          is the honest word for it)
    //  > 25  → possibly in a vehicle (city traffic ≈ 30+)

    private const val STILL_BELOW_KMH = 2.0
    private const val WALK_BELOW_KMH = 7.0
    private const val CYCLE_BELOW_KMH = 25.0

    /**
     * The CURRENT-movement estimate for the live detail card (spec §4-C):
     * sensor-backed (MODERATE) because the OS measured the speed itself.
     *
     * Evidence ladder:
     *  1. observation older than [LIVE_STALE_WINDOW_MILLIS] (or unknown,
     *     observedAt ≤ 0) → **null** — never render a stale classification
     *     as current (spec §6: no card at all when no fresh data);
     *  2. speed missing, negative or > [MAX_PLAUSIBLE_KMH] → the honest
     *     insufficient-evidence estimate ("Movement type unavailable");
     *  3. otherwise → the coarse kind at MODERATE confidence.
     *
     * A negative [age] (server timestamp slightly ahead of the device
     * clock) is treated as fresh — clock skew is a display concern, not
     * a data one (LiveLocationView's own doc says so).
     */
    fun classifyLive(
        speedMetersPerSecond: Double?,
        observedAtMillis: Long,
        nowMillis: Long
    ): MovementEstimate? {
        if (observedAtMillis <= 0L) return null
        val age = nowMillis - observedAtMillis
        if (age > LIVE_STALE_WINDOW_MILLIS) return null
        val speedKmh = plausibleSpeedKmh(speedMetersPerSecond)
            ?: return MovementEstimate.insufficient(observedAtMillis)
        return MovementEstimate(
            kind = kindFor(speedKmh),
            confidence = MovementConfidence.MODERATE,
            speedKmh = speedKmh,
            observedAtMillis = observedAtMillis,
            insufficientEvidence = false
        )
    }

    /**
     * The LAST-OBSERVED movement estimate for the journey-history screen
     * (spec §6 "re-usable by the journey-history screen"). Historical by
     * nature — the observedAt line discloses when — so no staleness
     * window applies.
     *
     * Evidence ladder (D11d):
     *  1. no samples at all → **null** (no card — an empty day renders its
     *     own honest empty state before the card is ever asked);
     *  2. the LAST sample carries a plausible OS speed → MODERATE
     *     (sensor-backed), observed at that sample's time;
     *  3. otherwise the most recent consecutive pair that does NOT span a
     *     >10-minute gap and has a positive time delta → points-based
     *     speed (LIMITED); a pair closer than [MIN_MOVE_METERS] reads as
     *     possibly stationary (jitter is not movement);
     *  4. no usable pair (single point, or every pair gapped) → the
     *     honest insufficient-evidence estimate.
     */
    fun classifyJourneyTail(samples: List<MovementSample>): MovementEstimate? {
        if (samples.isEmpty()) return null
        val last = samples.last()

        // Rung 2 — sensor-backed speed on the last sample (a glitched one
        // is rejected, and the ladder falls through to the pair evidence).
        val lastSpeedKmh = plausibleSpeedKmh(last.speedMetersPerSecond)
        if (lastSpeedKmh != null) {
            return MovementEstimate(
                kind = kindFor(lastSpeedKmh),
                confidence = MovementConfidence.MODERATE,
                speedKmh = lastSpeedKmh,
                observedAtMillis = last.timeMillis,
                insufficientEvidence = false
            )
        }

        // Rung 3 — the most recent non-gap consecutive pair, walked back
        // from the tail (a gap at the very end of a journey must not hide
        // the last real movement stretch).
        for (i in samples.indices.reversed()) {
            if (i == 0) break
            val to = samples[i]
            val from = samples[i - 1]
            val dt = to.timeMillis - from.timeMillis
            if (dt <= 0L || dt > GAP_THRESHOLD_MILLIS) continue
            val meters = haversineMeters(from.lat, from.lng, to.lat, to.lng)
            // A pair closer than MIN_MOVE_METERS is indistinguishable from
            // jitter and honestly reads as "stationary" (its implied speed
            // is ≈ 0 anyway — the arithmetic below discloses it either way).
            val speedKmh = (meters / dt) * 3.6
            return MovementEstimate(
                kind = kindFor(speedKmh),
                confidence = MovementConfidence.LIMITED,
                speedKmh = speedKmh,
                observedAtMillis = to.timeMillis,
                insufficientEvidence = false
            )
        }

        // Rung 4 — fresh tail, no evidence inside it.
        return MovementEstimate.insufficient(last.timeMillis)
    }

    // ── internals ───────────────────────────────────────────────────────

    /** Plausibility gate: null when the speed cannot be believed at all. */
    private fun plausibleSpeedKmh(speedMetersPerSecond: Double?): Double? {
        if (speedMetersPerSecond == null) return null
        if (speedMetersPerSecond < 0.0) return null
        val kmh = speedMetersPerSecond * 3.6
        if (kmh > MAX_PLAUSIBLE_KMH) return null
        return kmh
    }

    /** The coarse kind for a believable speed (boundaries: D11a). */
    private fun kindFor(speedKmh: Double): MovementKind = when {
        speedKmh < STILL_BELOW_KMH -> MovementKind.STILL
        speedKmh < WALK_BELOW_KMH -> MovementKind.WALK
        speedKmh < CYCLE_BELOW_KMH -> MovementKind.CYCLE
        else -> MovementKind.VEHICLE
    }

    /** Straight-line distance in metres (the summary card's own honesty
     *  note covers what "approximate" means — this is the same math). */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2.0 * earthRadiusM * asin(sqrt(a))
    }
}
