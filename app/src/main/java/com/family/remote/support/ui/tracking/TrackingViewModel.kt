package com.family.remote.support.ui.tracking

import androidx.lifecycle.ViewModel
import com.family.remote.support.PRESENCE_ONLINE_MILLIS
import com.family.remote.support.PRESENCE_STALE_MILLIS
import com.family.remote.support.PresenceView
import com.family.remote.support.RelationshipView
import com.family.remote.support.readPermChecklist
import com.family.remote.support.watchDevicePresence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ui/tracking/TrackingViewModel.kt — state holder for the Tracking tab.
 *
 * Holds partner relationships and presence. The rels list is injected from
 * outside (e.g. from HomeViewModel's shared state) so this VM never opens its
 * own listener — quota discipline (§75).
 */
class TrackingViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val rels: List<RelationshipView> = emptyList(),
        val presence: Map<String, PresenceView?> = emptyMap(),
        val error: Exception? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val stopPresence = mutableMapOf<String, () -> Unit>()

    /** Inject relationship list from the caller's shared state. */
    fun setRels(rels: List<RelationshipView>) {
        _ui.update { it.copy(rels = rels) }
        refreshPartnerWatches(rels)
    }

    /** Install/uninstall per-partner presence watches as ACTIVE rels change. */
    private fun refreshPartnerWatches(rels: List<RelationshipView>) {
        val newIds = rels.filter { it.status == "ACTIVE" }.map { it.id }.toSet()
        val seen = stopPresence.keys.toSet()
        (newIds - seen).forEach { relId ->
            val rel = rels.find { it.id == relId } ?: return@forEach
            stopPresence[relId]?.invoke()
            stopPresence[relId] = watchDevicePresence(
                rel.recipientUid, rel.recipientDeviceId
            ) { view ->
                _ui.update {
                    val presence = it.presence.toMutableMap()
                    presence[relId] = view
                    it.copy(presence = presence)
                }
            }
        }
        (seen - newIds).forEach { relId ->
            stopPresence[relId]?.invoke()
            stopPresence[relId] = null
        }
    }

    /**
     * Human freshness label for a member card: LIVE while heartbeat fresh,
     * Stale within window, Offline otherwise.
     */
    fun partnerFreshness(relId: String, now: Long): String {
        val presence = _ui.value.presence[relId]
        if (presence == null || presence.heartbeatAtMillis <= 0L) {
            return "Offline"
        }
        val age = now - presence.heartbeatAtMillis
        return when {
            age < PRESENCE_ONLINE_MILLIS -> "Live"
            age < PRESENCE_STALE_MILLIS -> "Stale"
            else -> "Offline"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPresence.values.forEach { it.invoke() }
    }
}

/** Human freshness label (LIVE / Stale / Offline). */
fun partnerFreshnessLabel(presence: PresenceView?, now: Long): String {
    if (presence == null || presence.heartbeatAtMillis <= 0L) {
        return "Offline"
    }
    val age = now - presence.heartbeatAtMillis
    return when {
        age < PRESENCE_ONLINE_MILLIS -> "Live"
        age < PRESENCE_STALE_MILLIS -> "Stale"
        else -> "Offline"
    }
}
