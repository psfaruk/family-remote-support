package com.family.remote.support

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Location master plan Phase 11 (v0.43.0) — the self-alert geofence engine.
 *
 * Pure Kotlin, zero Android dependency, stateful per [evaluate] call — the
 * same testability posture as [MovementClassifier] (Phase 10). The service
 * feeds it every accuracy-passing fix plus the CURRENT saved-place list
 * (from the in-memory [PlaceAlertRuntime] registry), and it decides which
 * ARRIVAL/DEPARTURE events fire. It never touches the network, disk or
 * notifications — the caller posts whatever it returns.
 *
 * Locked semantics (D13, recorded in LOCATION_IMPLEMENTATION_PROGRESS.md):
 *
 *  1. **Silent initial state.** The first sighting of a place establishes
 *     inside/outside WITHOUT an event — "you were already home when
 *     sharing started" is not an alert. A deleted-then-re-added place
 *     re-initializes silently the same way.
 *  2. **Enter** = distance ≤ radius (inclusive). **Exit** = distance
 *     ≥ radius + [EXIT_HYSTERESIS_METERS] (25 m — the app's own movement
 *     noise floor, the same constant the §3/§6 live gates use): movement
 *     smaller than the app's own movement gate cannot constitute a
 *     departure. The band between the two boundaries is STICKY — it
 *     belongs to whichever side the previous fix was on, so GPS jitter
 *     at the fence line cannot flip-flop the state.
 *  3. **Cooldown is per-place and gates EVENT EMISSION only** — state
 *     transitions always happen, so the engine's inside/outside view
 *     never goes stale; after any fired alert for a place, further
 *     events for that SAME place are suppressed until its
 *     `cooldownMinutes` elapsed (the UX spec §7 anti-flap control; the
 *     boundary is inclusive — "repeat alert after 10 min" repeats AT
 *     10 min).
 *  4. **Toggles are per-event-kind gates, not state gates** — a place
 *     with `arriveOn == false` still flips its inside state, it just
 *     never emits ARRIVAL.
 *  5. Places absent from the current list have their state pruned (the
 *     maps stay bounded by the live list, never by history).
 *  6. Non-finite inputs (NaN/∞ lat or lng) change nothing — the caller
 *     pre-filters accuracy, this is the defensive backstop.
 *
 * DR-P2 realized: this class is the WHOLE geofencing story of v1 — it
 * runs inside the already-running location FGS on fixes that are already
 * flowing; there is no GeofencingClient, no BroadcastReceiver, no new
 * permission, and (by construction) zero additional Firestore traffic.
 */
class PlaceAlertEngine {

    /** inside (true) / outside (false); absent = never seen (silent init). */
    private val insideByPlace = HashMap<String, Boolean>()

    /** Wall-clock millis of the last EMITTED event per place. */
    private val lastFiredByPlace = HashMap<String, Long>()

    /**
     * Evaluate one fix against the current place list. Returns the events
     * that fire (possibly empty, never null); the list order follows the
     * input rules' order (stable for the UI's per-place notification
     * posting).
     *
     * @param rules      the CURRENT saved places (id/name/position/config).
     * @param lat        fix latitude.
     * @param lng        fix longitude.
     * @param nowMillis  wall clock for cooldown bookkeeping — the SAME
     *                   clock the caller's notifications will be judged
     *                   against later (System.currentTimeMillis()).
     */
    fun evaluate(
        rules: List<PlaceRule>,
        lat: Double,
        lng: Double,
        nowMillis: Long
    ): List<PlaceAlertEvent> {
        // Defensive: a glitched coordinate is not evidence of anything.
        if (!lat.isFinite() || !lng.isFinite()) return emptyList()

        // Prune state of places no longer in the list (D13 rule 5) — the
        // maps stay exactly as large as the live list.
        val liveIds = rules.mapTo(HashSet()) { it.id }
        insideByPlace.keys.retainAll(liveIds)
        lastFiredByPlace.keys.retainAll(liveIds)

        val events = ArrayList<PlaceAlertEvent>(rules.size)
        for (rule in rules) {
            val d = distanceMeters(rule.lat, rule.lng, lat, lng)
            val prior = insideByPlace[rule.id]
            if (prior == null) {
                // D13 rule 1 — silent initial state.
                insideByPlace[rule.id] = d <= rule.radiusMeters
                continue
            }
            var becameInside = false
            var becameOutside = false
            if (!prior && d <= rule.radiusMeters) {
                becameInside = true
                insideByPlace[rule.id] = true
            } else if (prior && d >= rule.radiusMeters + EXIT_HYSTERESIS_METERS) {
                becameOutside = true
                insideByPlace[rule.id] = false
            }
            if (!becameInside && !becameOutside) continue

            // D13 rule 3 — the per-place cooldown gates emission only.
            val last = lastFiredByPlace[rule.id]
            if (last != null &&
                nowMillis - last < rule.cooldownMinutes * 60_000L
            ) {
                continue
            }
            val kind = if (becameInside) PlaceAlertKind.ARRIVAL else PlaceAlertKind.DEPARTURE
            val allowed = if (becameInside) rule.arriveOn else rule.departOn
            if (!allowed) continue // D13 rule 4 — toggle gates emission, not state.
            lastFiredByPlace[rule.id] = nowMillis
            events.add(
                PlaceAlertEvent(
                    placeId = rule.id,
                    placeName = rule.name,
                    kind = kind,
                    atMillis = nowMillis
                )
            )
        }
        return events
    }

    /** Fresh state (a brand-new service lifetime starts with no memory). */
    fun reset() {
        insideByPlace.clear()
        lastFiredByPlace.clear()
    }
}

/**
 * One saved place as the ENGINE sees it — the immutable config half of
 * `SavedPlace` (no timestamps, no Firestore concern). Constructed from the
 * runtime registry; the pure engine never learns where the list came from.
 */
data class PlaceRule(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val arriveOn: Boolean,
    val departOn: Boolean,
    val cooldownMinutes: Int
)

enum class PlaceAlertKind { ARRIVAL, DEPARTURE }

/** One alert the engine decided to fire (name only — never coordinates). */
data class PlaceAlertEvent(
    val placeId: String,
    val placeName: String,
    val kind: PlaceAlertKind,
    val atMillis: Long
)

/**
 * D13 rule 2 — the exit hysteresis in meters. Deliberately the SAME value
 * as the §3/§6 movement gate's noise floor (LocationShareService's
 * MIN_MOVE_METERS): a displacement smaller than what the app already
 * treats as "not moving" cannot constitute a departure, so the fence line
 * is sticky by the app's own definition of movement.
 */
internal const val EXIT_HYSTERESIS_METERS = 25.0

/** Great-circle distance in meters (haversine) — a deliberate private
 * copy of LocationShareService.distanceMeters (the service file is not
 * this agent's to refactor; promotion into a shared math home belongs to
 * a later dedupe wave, same note as the ChoiceChip dedupe in §80). */
internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2.0) * sin(dLng / 2.0)
    return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1.0 - a))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
