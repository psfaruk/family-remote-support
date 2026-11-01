// ═══════════════════════════════════════════════════════════════════════
// Location master plan Phase 11 (v0.43.0/63) — PlaceAlertEngine unit
// tests. This IS part of the phase gate (audit §9: "Self-alert geofencing
// + config UI" — the geofence DECISIONS are pure JVM, so they are proven
// here; the on-device notification ride is the Owner's device test).
// Pure JUnit4, no Android dependency, runs in CI via
// :app:testReleaseUnitTest on every push.
//
// Coordinate fixtures: same convention as MovementClassifierTest — at the
// equator 1° latitude ≈ 111.2 km (R·π/180 with R = 6 371 000 m), so
// 0.00045° ≈ 50 m, 0.00089° ≈ 99.0 m, 0.001125° ≈ 125.1 m. Every
// threshold case probes BOTH sides of its boundary (0.00089 enters at a
// 100 m fence, 0.00091 does not) — float haversine never has to be exact,
// only clearly on one side.
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceAlertEngineTest {

    private val now = 1_000_000_000_000L // fixed wall clock for every case

    /** A place at the equator on the prime meridian, 100 m fence. */
    private val home = PlaceRule(
        id = "uid_0000000000000001",
        name = "Home",
        lat = 0.0,
        lng = 0.0,
        radiusMeters = 100,
        arriveOn = true,
        departOn = true,
        cooldownMinutes = 10
    )

    private fun engine() = PlaceAlertEngine()

    // ── D13 rule 1 — silent initial state ───────────────────────────────

    @Test
    fun `first sighting inside is silent - state initialized`() {
        val e = engine()
        // ~50 m from the center = inside the 100 m fence.
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now).isEmpty())
        // A later fix still inside → still silent (state was initialized
        // to inside, no transition happened).
        assertTrue(e.evaluate(listOf(home), 0.0004, 0.0, now + 60_000L).isEmpty())
    }

    @Test
    fun `first sighting outside is silent - later true arrival fires`() {
        val e = engine()
        // ~500 m away = outside.
        assertTrue(e.evaluate(listOf(home), 0.0045, 0.0, now).isEmpty())
        val events = e.evaluate(listOf(home), 0.00045, 0.0, now + 5 * 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.ARRIVAL, events[0].kind)
        assertEquals("Home", events[0].placeName)
        assertEquals(now + 5 * 60_000L, events[0].atMillis)
    }

    // ── D13 rule 2 — enter/exit boundaries (probed both sides) ──────────

    @Test
    fun `enter fires just inside the radius`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside
        // 0.00089° ≈ 99.0 m — clearly inside the 100 m fence.
        val events = e.evaluate(listOf(home), 0.00089, 0.0, now + 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.ARRIVAL, events[0].kind)
    }

    @Test
    fun `no enter just outside the radius`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside
        // 0.00091° ≈ 101.3 m — past the fence but far short of the exit
        // line; an OUTSIDE place stays outside.
        assertTrue(e.evaluate(listOf(home), 0.00091, 0.0, now + 60_000L).isEmpty())
    }

    @Test
    fun `exit fires just past the hysteresis line`() {
        val e = engine()
        e.evaluate(listOf(home), 0.00045, 0.0, now) // inside
        // 0.001125° ≈ 125.1 m — at/just past radius + 25 m: exit is ≥.
        val events = e.evaluate(listOf(home), 0.001125, 0.0, now + 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }

    @Test
    fun `no exit just inside the hysteresis line`() {
        val e = engine()
        e.evaluate(listOf(home), 0.00045, 0.0, now) // inside
        // 0.001116° ≈ 124.1 m — past the radius but short of radius + 25 m.
        assertTrue(e.evaluate(listOf(home), 0.001116, 0.0, now + 60_000L).isEmpty())
    }

    @Test
    fun `boundary jitter inside the sticky band flips nothing`() {
        val e = engine()
        e.evaluate(listOf(home), 0.00045, 0.0, now) // inside
        // Jitter: ~110 m → ~90 m → ~115 m → ~105 m — all within the sticky
        // band (100 m..125 m on the inside side); the fence never flips.
        listOf(0.00099, 0.00081, 0.001035, 0.000945).forEach { lat ->
            assertTrue(e.evaluate(listOf(home), lat, 0.0, now + 60_000L).isEmpty())
        }
    }

    @Test
    fun `boundary jitter outside the sticky band flips nothing`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside
        // ~105 m → ~120 m → ~102 m — past the radius, short of the exit
        // line; an OUTSIDE place stays outside (enter needs ≤ radius).
        listOf(0.000945, 0.00108, 0.000918).forEach { lat ->
            assertTrue(e.evaluate(listOf(home), lat, 0.0, now + 60_000L).isEmpty())
        }
    }

    // ── D13 rule 3 — per-place cooldown gates EMISSION, not state ───────

    @Test
    fun `arrival then departure inside the cooldown is suppressed`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(home), 0.00045, 0.0, now).size) // ARRIVAL fires
        // 3 min later (10-min cooldown): a real exit — suppressed.
        assertTrue(e.evaluate(listOf(home), 0.0045, 0.0, now + 3 * 60_000L).isEmpty())
        // ...but the STATE flipped (that fix left the fence): a return
        // 4 min later is still inside the cooldown → also suppressed.
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now + 4 * 60_000L).isEmpty())
    }

    @Test
    fun `cooldown boundary is inclusive - the repeat fires at exactly cooldown`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(home), 0.00045, 0.0, now).size) // ARRIVAL at t0
        // 9 min later: a real exit — still inside the 10-min cooldown.
        assertTrue(e.evaluate(listOf(home), 0.0045, 0.0, now + 9 * 60_000L).isEmpty())
        // Back inside at EXACTLY t0 + 10 min: "repeat alert after 10 min"
        // repeats AT 10 min (suppression is strictly-less).
        val events = e.evaluate(listOf(home), 0.00045, 0.0, now + 10 * 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.ARRIVAL, events[0].kind)
    }

    @Test
    fun `shorter cooldown shortens suppression`() {
        val market = home.copy(
            id = "uid_0000000000000002",
            name = "Market",
            cooldownMinutes = 5
        )
        val e = engine()
        e.evaluate(listOf(market), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(market), 0.00045, 0.0, now).size) // ARRIVAL
        // 6 min later: past the 5-min cooldown → the DEPARTURE fires.
        val events = e.evaluate(listOf(market), 0.0045, 0.0, now + 6 * 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }

    // ── D13 rule 4 — toggles gate emission, not state ───────────────────

    @Test
    fun `arriveOn false suppresses arrival but keeps state honest`() {
        val quiet = home.copy(arriveOn = false)
        val e = engine()
        e.evaluate(listOf(quiet), 0.0045, 0.0, now) // outside, silent
        assertTrue(e.evaluate(listOf(quiet), 0.00045, 0.0, now + 60_000L).isEmpty()) // no ARRIVAL
        // The inside-state WAS established by the suppressed transition,
        // and the departure 19 min later fires — the engine never lags
        // behind reality.
        val events = e.evaluate(listOf(quiet), 0.0045, 0.0, now + 20 * 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }

    @Test
    fun `departOn false suppresses departure but keeps state honest`() {
        val arriveOnly = home.copy(departOn = false)
        val e = engine()
        e.evaluate(listOf(arriveOnly), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(arriveOnly), 0.00045, 0.0, now + 60_000L).size) // ARRIVAL
        assertTrue(e.evaluate(listOf(arriveOnly), 0.0045, 0.0, now + 60_000L).isEmpty()) // no DEPARTURE
        // The suppressed exit still flipped the state to outside: a later
        // arrival (past the cooldown) fires again.
        val events = e.evaluate(listOf(arriveOnly), 0.00045, 0.0, now + 30 * 60_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.ARRIVAL, events[0].kind)
    }

    @Test
    fun `both toggles off never emits`() {
        val dormant = home.copy(arriveOn = false, departOn = false)
        val e = engine()
        assertTrue(e.evaluate(listOf(dormant), 0.0045, 0.0, now).isEmpty())
        assertTrue(e.evaluate(listOf(dormant), 0.00045, 0.0, now + 60_000L).isEmpty())
        assertTrue(e.evaluate(listOf(dormant), 0.0045, 0.0, now + 30 * 60_000L).isEmpty())
    }

    // ── D13 rule 5 — list pruning / deleted-then-re-added places ────────

    @Test
    fun `deleted place drops state - re-added place re-initializes silently`() {
        val e = engine()
        e.evaluate(listOf(home), 0.00045, 0.0, now) // inside, silent init
        // Place deleted: state pruned.
        assertTrue(e.evaluate(emptyList(), 0.00045, 0.0, now + 60_000L).isEmpty())
        // Place re-added while still inside → SILENT re-init (no arrival
        // alert for a place the user never left).
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now + 2 * 60_000L).isEmpty())
    }

    @Test
    fun `deleted place drops cooldown - the re-added lifetime owes nothing`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(home), 0.00045, 0.0, now).size) // ARRIVAL fires
        // Deleted 1 min into the cooldown, re-added still inside (silent
        // re-init), then a real exit 1 s later: the new place lifetime has
        // NO cooldown debt to the deleted one — the DEPARTURE fires.
        assertTrue(e.evaluate(emptyList(), 0.00045, 0.0, now + 60_000L).isEmpty())
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now + 60_000L).isEmpty())
        val events = e.evaluate(listOf(home), 0.0045, 0.0, now + 61_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }

    // ── Multi-place behavior ─────────────────────────────────────────────

    @Test
    fun `two places crossing in one fix emit in list order`() {
        val market = PlaceRule(
            id = "uid_0000000000000002",
            name = "Market",
            lat = 0.0045, // ~500 m north of Home
            lng = 0.0,
            radiusMeters = 100,
            arriveOn = true,
            departOn = true,
            cooldownMinutes = 10
        )
        val rules = listOf(home, market)
        val e = engine()
        // Start between the two (inside Home's fence only) — Home silent
        // init inside, Market silent init outside (~450 m away).
        assertTrue(e.evaluate(rules, 0.00045, 0.0, now).isEmpty())
        // Walk north to Market's center: exit Home (~500 m ≥ 125 m) AND
        // arrive Market (0 m) — both in one fix, in list order.
        val events = e.evaluate(rules, 0.0045, 0.0, now + 30 * 60_000L)
        assertEquals(2, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind) // Home first
        assertEquals(PlaceAlertKind.ARRIVAL, events[1].kind) // Market second
    }

    @Test
    fun `cooldowns are independent per place`() {
        val market = PlaceRule(
            id = "uid_0000000000000002",
            name = "Market",
            lat = 0.0045,
            lng = 0.0,
            radiusMeters = 100,
            arriveOn = true,
            departOn = true,
            cooldownMinutes = 5 // shorter than Home's 10
        )
        val rules = listOf(home, market)
        val e = engine()
        e.evaluate(rules, 0.00045, 0.0, now) // inside Home, outside Market
        // Both fire at t0 (Home DEPARTURE + Market ARRIVAL in one fix).
        assertEquals(2, e.evaluate(rules, 0.0045, 0.0, now).size)
        // Stationary at Market's center: no transitions, no events.
        assertTrue(e.evaluate(rules, 0.0045, 0.0, now + 6 * 60_000L).isEmpty())
        // Walk back to Home at t0+6 min: Market's 5-min cooldown has
        // elapsed (its ~450 m exit fires); Home's 10-min has not (its
        // arrival is suppressed) — exactly one event, and it is Market's.
        val events = e.evaluate(rules, 0.00045, 0.0, now + 6 * 60_000L)
        assertEquals(1, events.size)
        assertEquals("Market", events[0].placeName)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }

    // ── D13 rule 6 — defensive input rejection ──────────────────────────

    @Test
    fun `non-finite coordinates change nothing`() {
        val e = engine()
        assertTrue(e.evaluate(listOf(home), Double.NaN, 0.0, now).isEmpty())
        assertTrue(e.evaluate(listOf(home), 0.0, Double.POSITIVE_INFINITY, now).isEmpty())
        // Neither glitch initialized state: the first REAL fix inside is
        // a silent first sighting, not an arrival.
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now + 60_000L).isEmpty())
    }

    // ── reset — the service-lifetime boundary ───────────────────────────

    @Test
    fun `reset forgets state and cooldowns`() {
        val e = engine()
        e.evaluate(listOf(home), 0.0045, 0.0, now) // outside, silent
        assertEquals(1, e.evaluate(listOf(home), 0.00045, 0.0, now).size) // ARRIVAL
        e.reset()
        // After reset the same position is a silent FIRST sighting again.
        assertTrue(e.evaluate(listOf(home), 0.00045, 0.0, now + 60_000L).isEmpty())
        // And the cooldown bookkeeping is gone: the immediate exit fires.
        val events = e.evaluate(listOf(home), 0.0045, 0.0, now + 61_000L)
        assertEquals(1, events.size)
        assertEquals(PlaceAlertKind.DEPARTURE, events[0].kind)
    }
}
