package com.family.remote.support.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.remote.support.ConfigRepo
import com.family.remote.support.PresenceView
import com.family.remote.support.RelationshipView
import com.family.remote.support.SessionView
import com.family.remote.support.cancelSession
import com.family.remote.support.denySession
import com.family.remote.support.endSession
import com.family.remote.support.expireSession
import com.family.remote.support.getSession
import com.family.remote.support.grantSession
import com.family.remote.support.requestSession
import com.family.remote.support.revokeRelationship
import com.family.remote.support.removeGuestRelationship
import com.family.remote.support.watchDevicePresence
import com.family.remote.support.watchRelationships
import com.family.remote.support.watchSessions
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ui/home/HomeViewModel.kt — the ONE watcher of the home subtree
 * (FRONTEND_ARCHITECTURE §4: one ViewModel per screen area owning
 * StateFlow&lt;UiState&gt;).
 *
 * Quota discipline (§75) drove the shape: the dashboard, the family list,
 * the member detail and the session screens all read THIS state — the
 * Firestore listeners (sessions ×2, relationships ×2, one presence doc
 * per live link) open exactly ONCE for the whole subtree, in
 * [viewModelScope], and are swapped deterministically when the signed-in
 * user changes. Screens never open their own listeners.
 *
 * Finished sessions are fetched ONE-SHOT, only when a session that was
 * open a moment ago disappears from the live watch (the documented
 * SessionRepo contract) — there is deliberately no "history query".
 */
class HomeViewModel : ViewModel() {

    /** Everything the home subtree renders. */
    data class UiState(
        val loading: Boolean = true,
        /** Live OPEN sessions (both roles), newest first (watchSessions). */
        val sessions: List<SessionView> = emptyList(),
        /** All relationships (both roles), unsorted. */
        val rels: List<RelationshipView> = emptyList(),
        /**
         * Sessions that were open and have since closed — one-shot reads,
         * kept newest-first, bounded (the honest "just ended" list).
         */
        val finished: List<SessionView> = emptyList(),
        /** Linked-phone presence by relationship id (null = unavailable). */
        val presence: Map<String, PresenceView?> = emptyMap(),
        /** Primary-listener failure (routed, never swallowed — audit #5). */
        val error: Exception? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var user: FirebaseUser? = null
    private var stopSessions: (() -> Unit)? = null
    private var stopRels: (() -> Unit)? = null
    private val stopPresence = mutableMapOf<String, () -> Unit>()
    private var seenOpenIds: Set<String> = emptySet()

    /** v0.53.0 — session ids already config-prewarmed (the latency fix). */
    private val prewarmedIds = mutableSetOf<String>()

    /** The signed-in user changed (sign-in, sign-out) — swap every listener. */
    fun setUser(newUser: FirebaseUser?) {
        if (newUser?.uid == user?.uid) return
        user = newUser
        restart()
    }

    /** Re-run after a transient failure (the ErrorPanel retry button). */
    fun retry() = restart()

    private fun restart() {
        stopAll()
        val u = user
        if (u == null) {
            _ui.value = UiState()
            return
        }
        _ui.value = UiState(loading = true)

        stopSessions = watchSessions(
            u,
            onUpdate = { open ->
                prewarmSessionConfig(open)
                trackJustClosed(open)
                _ui.update { it.copy(sessions = open, loading = false, error = null) }
            },
            onError = { e -> _ui.update { it.copy(loading = false, error = e) } },
        )
        stopRels = watchRelationships(
            u,
            onUpdate = { rels ->
                reconcilePresence(rels)
                _ui.update { it.copy(rels = rels, loading = false, error = null) }
            },
            onError = { e -> _ui.update { it.copy(loading = false, error = e) } },
        )
    }

    /**
     * v0.53.0 (worklog §100 — the "স্লো করে কাজ করছে, late এ কাজ করছে"
     * wave) — CONFIG PREWARM. The engine used to fetch config/turn +
     * config/quality (each with a 5 s cold-cache ceiling) at the very
     * moment a share/watch starts — the helper's "Watch screen" tap could
     * stall up to 5 s BEFORE its "watch" signal, and the recipient's offer
     * another 5 s after that, on every cold session. This listener sees a
     * REQUESTED/ACTIVE session the moment it exists — seconds or minutes
     * before anyone taps anything — so both fetches run there, once per
     * session id, best-effort. By the time the engine calls
     * ConfigRepo.fetchTurnServers()/fetchQuality() they hit the warm
     * 10-min/60-s caches and return instantly. Both fetches swallow their
     * own failures (documented ConfigRepo contract) — a config hiccup
     * still never blocks a session.
     */
    private fun prewarmSessionConfig(open: List<SessionView>) {
        val fresh = open.asSequence()
            .filter { it.status == "REQUESTED" || it.status == "ACTIVE" }
            .map { it.id }
            .filter { it !in prewarmedIds }
            .toList()
        if (fresh.isEmpty()) return
        prewarmedIds += fresh
        viewModelScope.launch {
            try {
                ConfigRepo.fetchTurnServers()
                ConfigRepo.fetchQuality()
            } catch (_: Exception) {
                // best-effort only; the engine re-fetches on its own anyway
            }
        }
    }

    /**
     * A session id that was in the open set and no longer is has just
     * finished — one-shot [getSession] keeps its final state for the
     * "just ended" list (SessionRepo's documented pattern).
     */
    private fun trackJustClosed(open: List<SessionView>) {
        val openIds = open.map { it.id }.toSet()
        val justClosed = seenOpenIds - openIds
        seenOpenIds = openIds
        justClosed.forEach { id ->
            if (_ui.value.finished.any { it.id == id }) return@forEach
            viewModelScope.launch {
                try {
                    val final = getSession(id) ?: return@launch
                    _ui.update { s ->
                        s.copy(
                            finished = (listOf(final) + s.finished.filter { it.id != id })
                                .sortedByDescending { it.sessionEndsAtMillis }
                                .take(FINISHED_KEEP)
                        )
                    }
                } catch (_: Exception) {
                    // one-shot best effort; the row simply stays absent
                }
            }
        }
    }

    /**
     * One presence watcher per LIVE link (the linked phone = the
     * recipient device — readable by both sides under the rules). Past
     * (REVOKED/EXPIRED/dead-guest) links are NOT watched: the rules would
     * refuse the read, and the honest label is "presence unavailable".
     */
    private fun reconcilePresence(rels: List<RelationshipView>) {
        val live = rels.filter {
            it.status == "ACTIVE" &&
                it.guestLive &&
                it.recipientUid.isNotBlank() &&
                it.recipientDeviceId.isNotBlank()
        }.distinctBy { it.id }
        val wanted = live.map { it.id }.toSet()

        // stop watchers for links that went away / died
        stopPresence.keys.filter { it !in wanted }.forEach { key ->
            stopPresence.remove(key)?.invoke()
        }
        // start watchers for new links
        live.forEach { rel ->
            if (rel.id in stopPresence) return@forEach
            val stop = watchDevicePresence(
                rel.recipientUid,
                rel.recipientDeviceId,
                onChange = { p ->
                    _ui.update { s ->
                        s.copy(presence = s.presence + (rel.id to p))
                    }
                },
                onError = { _ ->
                    // A presence read that cannot be satisfied is a STATE,
                    // not a screen failure: render "presence unavailable".
                    _ui.update { s ->
                        if (s.presence.containsKey(rel.id)) s
                        else s.copy(presence = s.presence + (rel.id to null))
                    }
                },
            )
            stopPresence[rel.id] = stop
        }
    }

    private fun stopAll() {
        stopSessions?.invoke()
        stopSessions = null
        stopRels?.invoke()
        stopRels = null
        stopPresence.values.forEach { it() }
        stopPresence.clear()
        seenOpenIds = emptySet()
        prewarmedIds.clear()
    }

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }

    // ── Consent-flow actions (W2's dry-run gate) ─────────────────────────

    /** Recipient allows an ask. Throws the localized SessionException on failure. */
    suspend fun allow(sessionId: String) {
        val uid = user?.uid ?: throw IllegalStateException("not signed in")
        grantSession(sessionId, uid)
    }

    /** Recipient refuses an ask. */
    suspend fun deny(sessionId: String) {
        val uid = user?.uid ?: throw IllegalStateException("not signed in")
        denySession(sessionId, uid)
    }

    /** Helper takes back their own unanswered ask (REQUESTED → CANCELLED). */
    suspend fun cancelAsk(sessionId: String) {
        val uid = user?.uid ?: throw IllegalStateException("not signed in")
        cancelSession(sessionId, uid)
    }

    /** Either side ends the live session. */
    suspend fun end(sessionId: String) {
        val uid = user?.uid ?: throw IllegalStateException("not signed in")
        endSession(sessionId, uid)
    }

    /** Lazy expiry of a stale ask (the UI fires this once, best effort). */
    fun expireIfStale(session: SessionView) {
        viewModelScope.launch {
            try {
                expireSession(session.id)
            } catch (_: Exception) {
                // best effort — the server sweep also owns this transition
            }
        }
    }

    /** Helper asks a linked member for a session. Returns the REQUESTED view. */
    suspend fun ask(rel: RelationshipView): SessionView {
        val u = user ?: throw IllegalStateException("not signed in")
        return requestSession(u, rel)
    }

    /** Either side revokes the family link (also kills open sessions). */
    suspend fun revoke(relId: String) {
        val uid = user?.uid ?: throw IllegalStateException("not signed in")
        revokeRelationship(relId, uid)
    }

    /** Removes a guest link entirely (the documented re-pair path). */
    suspend fun removeGuest(relId: String) {
        removeGuestRelationship(relId)
    }

    private companion object {
        /** The "just ended" list stays short — recent truth, not history. */
        const val FINISHED_KEEP = 6
    }
}
