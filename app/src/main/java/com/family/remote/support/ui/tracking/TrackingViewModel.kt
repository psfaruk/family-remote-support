// ═══════════════════════════════════════════════════════════════════════
// Phase 22/M5 (v0.33.0) — Tracking tab ViewModel
// (docs/LIVE_LOCATION_ARCHITECTURE.md §3 data flow, docs/NAVIGATION_MAP.md
// §2 "tracking" subtree). Mirrors ui/home/SessionWatchViewModel.kt in
// shape: a StateFlow<UiState> owning the listeners, a loaded flag for the
// honest skeleton, and ROUTED listener errors (audit risk #5 — failures
// are surfaced, never swallowed) while the last data is always KEPT
// (v0.21.0 rule: a transient listener error must never unmount live UI).
//
// Data sources (both pre-existing patterns, zero new queries):
//   • watchRelationships — the same relationship listener the Phones/Home
//     screens use; from it we derive the AUTHORIZED counterpart devices
//     (status ACTIVE, guest window live, tracked device belongs to the
//     OTHER account — never this phone's own doc).
//   • watchLiveLocation (M5-A contract, LiveLocationRepo) — one listener
//     per counterpart device, rebuilt only when the set of authorized
//     devices changes; removed again on re-observe/onCleared.
//
// No history, no fabrication: each pin carries at most the device's LAST
// published position (master PART 6 — single mutable doc per device).
// ═══════════════════════════════════════════════════════════════════════
package com.family.remote.support.ui.tracking

import androidx.lifecycle.ViewModel
import com.family.remote.support.LiveLocationView
import com.family.remote.support.RelationshipView
import com.family.remote.support.readReceivedLocationAuthorization
import com.family.remote.support.watchLiveLocation
import com.family.remote.support.watchRelationships
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One authorized counterpart device plus its latest published position.
 * [location] is null while no doc exists yet (or after the owner stopped
 * sharing and the doc was cleared) — the UI then shows "Never shared",
 * never a fabricated position.
 */
data class CounterpartPin(
    val rel: RelationshipView,
    val location: LiveLocationView?
)

/** UiState for the Tracking tab watch: data + loaded flag + routed error. */
data class TrackingUiState(
    val counterparts: List<CounterpartPin> = emptyList(),
    // Phase 4 (v0.38.0) — ALL ACTIVE relationships (both directions), the
    // full pairing picture the sharing-setup wizard's recipient picker
    // needs. Same relationship listener, no new query — just exposed.
    val relationships: List<RelationshipView> = emptyList(),
    // Phase 9 (v0.40.0) — per-relationship "the sharer granted me journey
    // history" (the dashboard card's hist button gate, UX spec §2). Keyed
    // by relId; a missing key = not granted (honest absence).
    val historyGrants: Map<String, Boolean> = emptyMap(),
    val loaded: Boolean = false,
    val error: String? = null
)

/**
 * v0.37.0 (dedupe wave) — ONE counterpart-trackable predicate (was 2
 * drifting copies: TrackingViewModel.trackedCounterparts' filter below and
 * LocationDetailScreen's syncLocationWatch key). A device is trackable
 * when the relationship is ACTIVE, the guest window is live, the tracked
 * device belongs to the OTHER account (recipient side carries the device
 * identity), and it has a device id — verified identical in both copies.
 */
internal fun RelationshipView.isTrackableCounterpart(myUid: String?): Boolean =
    status == "ACTIVE" && guestLive && recipientUid != myUid && recipientDeviceId.isNotBlank()

/**
 * M5 (v0.33.0) — the Tracking tab's data owner: one relationship listener
 * plus a live-location listener per authorized counterpart device. The
 * composable reads locations ONLY through [state] (no direct
 * watchLiveLocation calls in TrackingTab).
 */
class TrackingViewModel : ViewModel() {

    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    private var stopWatch: (() -> Unit)? = null

    /** Location-listener handles keyed by (recipientUid, recipientDeviceId). */
    private val stopLocations = LinkedHashMap<Pair<String, String>, () -> Unit>()

    /** Last known position per watched device (kept across listener churn). */
    private val locations = HashMap<Pair<String, String>, LiveLocationView>()

    private var relationships: List<RelationshipView> = emptyList()

    // Phase 9 — the recipient-side authorization read (one per counterpart
    // device, diffed exactly like the location watches: re-read ONLY when
    // the counterpart SET changes, so a rel-listener churn never turns into
    // an authorization read storm). One-shot gets on a throwaway scope; a
    // failure leaves the member's grant simply un-read (no button — the
    // honest default), never a fabricated grant.
    private val historyGrants = HashMap<String, Boolean>()
    private val authzReadKeys = HashSet<Pair<String, String>>()
    private val authzScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Own uid — a relationship whose recipient is THIS account tracks this phone itself (owner side), never a counterpart pin. */
    private var myUid: String? = null

    /**
     * (Re)start the watches for [user]. Passing null resets to the
     * signed-out state (same teardown semantics as SessionWatchViewModel).
     * [describeError] maps exceptions to localized text — kept OUT of the
     * ViewModel so it stays context-free; the shell supplies
     * describeFirestoreError(context, e).
     */
    fun observe(user: FirebaseUser?, describeError: (Exception) -> String) {
        stopWatch?.invoke()
        stopWatch = null
        stopAllLocationWatches()
        locations.clear()
        relationships = emptyList()
        historyGrants.clear()
        authzReadKeys.clear()
        myUid = user?.uid
        if (user == null) {
            _state.value = TrackingUiState()
            return
        }
        stopWatch = watchRelationships(
            user,
            onUpdate = { rels ->
                relationships = rels
                rebuildLocationWatches(describeError)
                emit()
            },
            // v0.21.0 semantics: KEEP the last counterparts on a transient
            // listener error, but route the failure into the state so the
            // tab can show an honest error panel when no snapshot exists.
            onError = { e ->
                _state.value = _state.value.copy(error = describeError(e))
            }
        )
    }

    /**
     * The devices this account is authorized to see: relationship ACTIVE,
     * guest window live, and the tracked device belongs to the other
     * account (the recipient side carries the device identity). A
     * REVOKED/EXPIRED relationship or an expired guest window is NOT
     * authorized — those rows never become pins.
     */
    private fun trackedCounterparts(): List<RelationshipView> =
        relationships.filter { it.isTrackableCounterpart(myUid) }

    /**
     * Diff the wanted location listeners against the registered ones —
     * listeners for still-authorized devices are NEVER churned (their last
     * known position survives), dropped devices lose both listener and
     * cached position, new devices get a fresh listener.
     */
    private fun rebuildLocationWatches(describeError: (Exception) -> String) {
        val wanted = trackedCounterparts()
            .map { it.recipientUid to it.recipientDeviceId }
            .toHashSet()
        (stopLocations.keys - wanted).forEach { key ->
            stopLocations.remove(key)?.invoke()
            locations.remove(key)
        }
        (wanted - stopLocations.keys).forEach { key ->
            stopLocations[key] = watchLiveLocation(
                key.first,
                key.second,
                onChange = { view ->
                    // null = the doc is gone (owner stopped sharing) — no
                    // position is kept or invented for that device.
                    if (view == null) {
                        locations.remove(key)
                    } else {
                        locations[key] = view
                    }
                    emit()
                },
                onError = { e ->
                    // Routed like the relationship error above; the last
                    // pins stay on screen (v0.21.0 keep-last-data rule).
                    _state.value = _state.value.copy(error = describeError(e))
                }
            )
        }
        rebuildAuthzReads()
    }

    /**
     * Phase 9 — one authorization read per NEW counterpart device (the
     * diff keeps churn at zero for unchanged sets). The read itself is a
     * user-action-driven one-shot get (§75): opening the Tracking tab is
     * the action. Grants landing later than the first emission simply
     * re-emit.
     */
    private fun rebuildAuthzReads() {
        val wanted = trackedCounterparts()
        for (rel in wanted) {
            val key = rel.recipientUid to rel.recipientDeviceId
            if (key in authzReadKeys) continue
            authzReadKeys.add(key)
            authzScope.launch {
                try {
                    val authz = readReceivedLocationAuthorization(key.first, key.second)
                    val now = System.currentTimeMillis()
                    historyGrants[rel.id] =
                        authz != null && authz.activeAt(now) && authz.historyEnabled
                } catch (_: Exception) {
                    // Unreadable right now (offline): no button for this
                    // member — the honest default, retried next tab open.
                    historyGrants.remove(rel.id)
                }
                emit()
            }
        }
        // Relationships that disappeared drop their grants (stale ids must
        // never keep a button alive).
        val liveIds = wanted.map { it.id }.toSet()
        historyGrants.keys.retainAll(liveIds)
    }

    /** Rebuild the public state from the current relationships + positions. */
    private fun emit() {
        _state.value = TrackingUiState(
            counterparts = trackedCounterparts().map { rel ->
                CounterpartPin(
                    rel = rel,
                    location = locations[rel.recipientUid to rel.recipientDeviceId]
                )
            },
            // Phase 4 — the wizard's recipient candidates: every ACTIVE
            // relationship (helper-side AND recipient-side), not just the
            // counterpart-trackable subset above.
            relationships = relationships.filter { it.status == "ACTIVE" },
            historyGrants = HashMap(historyGrants),
            loaded = true
        )
    }

    private fun stopAllLocationWatches() {
        stopLocations.values.forEach { stop -> stop() }
        stopLocations.clear()
    }

    override fun onCleared() {
        stopWatch?.invoke()
        stopWatch = null
        stopAllLocationWatches()
    }
}
