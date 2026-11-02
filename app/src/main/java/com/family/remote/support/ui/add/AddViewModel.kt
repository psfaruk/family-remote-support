package com.family.remote.support.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.remote.support.InviteCode
import com.family.remote.support.RelationshipView
import com.family.remote.support.REL_KIND_FAMILY
import com.family.remote.support.cancelInvitation
import com.family.remote.support.createInvitation
import com.family.remote.support.redeemCode
import com.family.remote.support.watchInvitation
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ui/add/AddViewModel.kt — the ONE brain of the Add-Device subtree (W3;
 * FRONTEND_UI_PLAN_BN §2.3, FRONTEND_ARCHITECTURE §4: one ViewModel per
 * screen area owning StateFlow<UiState>).
 *
 * The pairing FSM (plan §1.3):
 *
 *   IDLE → CREATING → PENDING(QR, 5 min TTL) → PAIRED ✓
 *                                   ├→ CANCELLED (inviter pressed cancel)
 *                                   └→ EXPIRED  (derived in the UI: ServerClock
 *                                      passed expiresAtMillis — the invitation
 *                                      doc itself stays PENDING until TTL; the
 *                                      UI must not pretend otherwise)
 *
 * The INVITER side lives here: create → watch → cancel → reset. The HELPER
 * side (redeem) is a one-shot suspend — no listener, no state — exactly the
 * ConsentCard act{} pattern; [redeem] is a thin delegate so screens never
 * import the repo layer directly.
 *
 * Listener discipline (§75 quota rules): at most ONE watchInvitation
 * listener for the whole subtree, opened once per created invitation and
 * swapped deterministically — never per recomposition.
 */
class AddViewModel : ViewModel() {

    /** The pairing FSM's honest phases (EXPIRED is derived, not stored). */
    enum class PairPhase { IDLE, CREATING, PENDING, PAIRED, CANCELLED }

    /** Everything the inviter side renders. */
    data class PairingState(
        val phase: PairPhase = PairPhase.IDLE,
        /** family | guest — frozen at creation (rules + transaction honor it). */
        val kind: String = REL_KIND_FAMILY,
        /** The live invitation (code + payload + expiry) once created. */
        val invite: InviteCode? = null,
        /** Who redeemed (email) — arrives through the snapshot listener. */
        val claimedEmail: String? = null,
        /** Create/cancel failure, routed not swallowed (audit #5). */
        val error: Exception? = null,
        /** Listener failure — non-fatal: PENDING stays on screen + error row. */
        val watchError: Exception? = null,
    )

    private val _pairing = MutableStateFlow(PairingState())
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private var watch: ListenerRegistration? = null

    /**
     * Create a fresh 5-minute invitation of [kind] and start watching it.
     * The expiry comparison is UI-side against ServerClock — the FSM here
     * only knows PENDING → PAIRED / CANCELLED.
     */
    fun startPairing(user: FirebaseUser, deviceId: String, deviceModel: String, kind: String) {
        if (_pairing.value.phase == PairPhase.CREATING) return
        stopWatch()
        _pairing.value = PairingState(phase = PairPhase.CREATING, kind = kind)
        viewModelScope.launch {
            try {
                val invite = createInvitation(user, deviceId, deviceModel, kind) // repo-level (imported)
                _pairing.update {
                    it.copy(phase = PairPhase.PENDING, invite = invite, error = null)
                }
                startWatch(invite.invitationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pairing.update { it.copy(phase = PairPhase.IDLE, error = e) }
            }
        }
    }

    /**
     * Live status of the pending invitation: PENDING → PAIRED ✓ / CANCELLED.
     * A failed listener is a STATE row, not a screen failure (the invitation
     * still exists; the 5-min TTL backstops everything).
     */
    private fun startWatch(invitationId: String) {
        watch = watchInvitation(
            invitationId,
            onChange = { view ->
                _pairing.update { state ->
                    when (view?.status) {
                        "PAIRED" -> state.copy(
                            phase = PairPhase.PAIRED,
                            claimedEmail = view.claimedEmail ?: state.claimedEmail,
                            watchError = null,
                        )
                        "CANCELLED" -> state.copy(phase = PairPhase.CANCELLED, watchError = null)
                        else -> state.copy(
                            claimedEmail = view?.claimedEmail ?: state.claimedEmail,
                            watchError = null,
                        )
                    }
                }
            },
            onError = { e -> _pairing.update { it.copy(watchError = e) } },
        )
    }

    /** Inviter pressed "cancel code": stop the watch, write CANCELLED once. */
    fun cancelPending() {
        val invite = _pairing.value.invite ?: return
        stopWatch()
        _pairing.update { it.copy(phase = PairPhase.CREATING) } // busy while cancelling
        viewModelScope.launch {
            try {
                cancelInvitation(invite.invitationId)
                _pairing.update { it.copy(phase = PairPhase.CANCELLED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cancel write failed — put the invitation back on screen:
                // it is still PENDING (or expires honestly within 5 min).
                _pairing.update {
                    it.copy(phase = PairPhase.PENDING, error = e)
                }
            }
        }
    }

    /** Back to IDLE (after PAIRED/CANCELLED acknowledgement or a retry). */
    fun reset() {
        stopWatch()
        _pairing.value = PairingState()
    }

    /** Helper side: one-shot atomic redemption (the transaction owns truth). */
    suspend fun redeem(context: android.content.Context, user: FirebaseUser, rawCode: String): RelationshipView =
        redeemCode(context, user, rawCode)

    private fun stopWatch() {
        watch?.remove()
        watch = null
    }

    /** Release the Firestore listener when the subtree dies. */
    override fun onCleared() {
        stopWatch()
        super.onCleared()
    }
}
