// ═══════════════════════════════════════════════════════════════════════
// Location master plan Phase 10 (v0.41.0/60) — MovementClassifier unit
// tests. This IS the phase gate ("Unit tests incl. impossible-speed
// rejection" — audit §9 phase table): pure JVM tests, no Android
// dependency, wired into CI before assemble (:app:testReleaseUnitTest)
// so every push runs them from now on.
//
// Coordinate fixtures use 1° latitude ≈ 111 km at the equator: lat
// 0.0009 ≈ 100 m, 0.0045 ≈ 500 m, 0.00005 ≈ 5.5 m (inside the jitter
// floor) — small enough that the haversine error never crosses a
// threshold boundary.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementClassifierTest {

    private val now = 1_000_000_000_000L // fixed clock for every live test

    // ── classifyLive: the sensor-backed current-movement path ──────────

    @Test
    fun `live - zero speed reads as possibly stationary`() {
        val e = MovementClassifier.classifyLive(0.0, now - 1_000L, now)!!
        assertEquals(MovementKind.STILL, e.kind)
        assertEquals(MovementConfidence.MODERATE, e.confidence)
        assertEquals(0.0, e.speedKmh!!, 0.001)
        assertTrue(!e.insufficientEvidence)
    }

    @Test
    fun `live - walking threshold`() {
        val e = MovementClassifier.classifyLive(1.0, now - 1_000L, now)!! // 3.6 km/h
        assertEquals(MovementKind.WALK, e.kind)
        assertEquals(3.6, e.speedKmh!!, 0.001)
    }

    @Test
    fun `live - cycling threshold`() {
        val e = MovementClassifier.classifyLive(3.0, now - 1_000L, now)!! // 10.8 km/h
        assertEquals(MovementKind.CYCLE, e.kind)
    }

    @Test
    fun `live - vehicle threshold`() {
        val e = MovementClassifier.classifyLive(9.0, now - 1_000L, now)!! // 32.4 km/h
        assertEquals(MovementKind.VEHICLE, e.kind)
    }

    @Test
    fun `live - kind boundaries are exclusive at the bottom`() {
        // 0.55 m/s = 1.98 km/h → STILL; 0.56 m/s = 2.016 km/h → WALK.
        assertEquals(
            MovementKind.STILL,
            MovementClassifier.classifyLive(0.55, now, now)!!.kind
        )
        assertEquals(
            MovementKind.WALK,
            MovementClassifier.classifyLive(0.56, now, now)!!.kind
        )
    }

    @Test
    fun `live - missing speed is honest insufficient evidence, not a guess`() {
        val e = MovementClassifier.classifyLive(null, now - 1_000L, now)!!
        assertTrue(e.insufficientEvidence)
        assertEquals(now - 1_000L, e.observedAtMillis)
    }

    @Test
    fun `live - impossible negative speed is rejected`() {
        val e = MovementClassifier.classifyLive(-0.5, now - 1_000L, now)!!
        assertTrue(e.insufficientEvidence)
    }

    @Test
    fun `live - impossible overspeed is rejected`() {
        // 40 m/s = 144 km/h — beyond the 120 km/h plausibility ceiling.
        val e = MovementClassifier.classifyLive(40.0, now - 1_000L, now)!!
        assertTrue(e.insufficientEvidence)
    }

    @Test
    fun `live - speed at the ceiling itself is still believable`() {
        // 33.33 m/s ≈ 119.99 km/h — the boundary is inclusive.
        val e = MovementClassifier.classifyLive(33.33, now - 1_000L, now)!!
        assertEquals(MovementKind.VEHICLE, e.kind)
    }

    @Test
    fun `live - a stale observation never presents as current`() {
        // 10 minutes old — beyond the 300 s staleness window → NO card.
        assertNull(MovementClassifier.classifyLive(1.0, now - 600_000L, now))
    }

    @Test
    fun `live - inside the staleness window still classifies`() {
        assertNotNull(MovementClassifier.classifyLive(1.0, now - 240_000L, now))
    }

    @Test
    fun `live - an unknown observation time is treated as stale`() {
        assertNull(MovementClassifier.classifyLive(1.0, 0L, now))
    }

    @Test
    fun `live - a slightly future timestamp is clock skew, not stale`() {
        assertNotNull(MovementClassifier.classifyLive(1.0, now + 5_000L, now))
    }

    // ── classifyJourneyTail: the history-screen path ───────────────────

    @Test
    fun `journey - no samples means no card`() {
        assertNull(MovementClassifier.classifyJourneyTail(emptyList()))
    }

    @Test
    fun `journey - a sensor-backed last point wins at moderate confidence`() {
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.0009, 0.0, speedMetersPerSecond = 1.5)
            )
        )!!
        assertEquals(MovementKind.WALK, e.kind) // 5.4 km/h
        assertEquals(MovementConfidence.MODERATE, e.confidence)
        assertEquals(5.4, e.speedKmh!!, 0.01)
        assertEquals(60_000L, e.observedAtMillis)
    }

    @Test
    fun `journey - a single point with a speed classifies from it`() {
        val e = MovementClassifier.classifyJourneyTail(
            listOf(MovementSample(5_000L, 0.0, 0.0, speedMetersPerSecond = 8.0))
        )!!
        assertEquals(MovementKind.VEHICLE, e.kind) // 28.8 km/h
        assertEquals(MovementConfidence.MODERATE, e.confidence)
    }

    @Test
    fun `journey - a single point without a speed is insufficient`() {
        val e = MovementClassifier.classifyJourneyTail(
            listOf(MovementSample(5_000L, 0.0, 0.0))
        )!!
        assertTrue(e.insufficientEvidence)
        assertEquals(5_000L, e.observedAtMillis)
    }

    @Test
    fun `journey - a plain point pair is points-based limited evidence`() {
        // 100 m in 60 s = 6 km/h → possibly walking.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.0009, 0.0)
            )
        )!!
        assertEquals(MovementKind.WALK, e.kind)
        assertEquals(MovementConfidence.LIMITED, e.confidence)
        assertEquals(6.0, e.speedKmh!!, 0.1)
    }

    @Test
    fun `journey - a vehicle-paced pair classifies as vehicle`() {
        // 500 m in 60 s ≈ 30 km/h.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.0045, 0.0)
            )
        )!!
        assertEquals(MovementKind.VEHICLE, e.kind)
        assertEquals(MovementConfidence.LIMITED, e.confidence)
    }

    @Test
    fun `journey - sub-jitter distances read as stationary`() {
        // 5.5 m in 60 s — inside the 15 m jitter floor: not movement.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.00005, 0.0)
            )
        )!!
        assertEquals(MovementKind.STILL, e.kind)
        assertEquals(MovementConfidence.LIMITED, e.confidence)
    }

    @Test
    fun `journey - a gapped pair is never delta evidence`() {
        // Two points 20 minutes apart: the honest answer is "unavailable".
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(1_200_000L, 0.0009, 0.0)
            )
        )!!
        assertTrue(e.insufficientEvidence)
    }

    @Test
    fun `journey - a ten minute pair is exactly at the boundary and counts`() {
        // 600 s == GAP_THRESHOLD (strictly-greater rule): still evidence.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(600_000L, 0.0009, 0.0)
            )
        )!!
        assertTrue(!e.insufficientEvidence)
        assertEquals(MovementKind.STILL, e.kind) // 0.6 km/h
    }

    @Test
    fun `journey - a glitched sensor speed falls back to the pair`() {
        // Last point claims 50 m/s (180 km/h — impossible): rejected, and
        // the valid pair below it still speaks, at LIMITED confidence.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.0009, 0.0, speedMetersPerSecond = 50.0)
            )
        )!!
        assertEquals(MovementKind.WALK, e.kind)
        assertEquals(MovementConfidence.LIMITED, e.confidence)
    }

    @Test
    fun `journey - a gap at the tail walks back to the last real pair`() {
        // The final pair spans 24 min (a gap); the earlier valid pair is
        // the last honest evidence, observed at ITS time.
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(60_000L, 0.0009, 0.0),
                MovementSample(1_500_000L, 0.0018, 0.0)
            )
        )!!
        assertEquals(MovementKind.WALK, e.kind)
        assertEquals(60_000L, e.observedAtMillis)
    }

    @Test
    fun `journey - a zero-duration pair is skipped not divided`() {
        // Same-timestamp points (defensive): the pair is skipped, and with
        // no other evidence the honest result is "unavailable".
        val e = MovementClassifier.classifyJourneyTail(
            listOf(
                MovementSample(0L, 0.0, 0.0),
                MovementSample(0L, 0.0009, 0.0)
            )
        )!!
        assertTrue(e.insufficientEvidence)
    }
}
