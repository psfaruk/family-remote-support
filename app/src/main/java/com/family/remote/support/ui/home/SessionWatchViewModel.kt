package com.family.remote.support.ui.home

import androidx.lifecycle.ViewModel
import com.family.remote.support.SessionView
import com.family.remote.support.watchSessions
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ═══════════════════════════════════════════════════════════════════════
// M3 (v0.31.0) — the first ViewModel of the master-plan redesign
// (FRONTEND_ARCHITECTURE §4): the session watch moves out of the
// composable into a StateFlow<UiState> owned by this ViewModel
// (lifecycle-viewmodel-compose added in this milestone).
//
// Scope decision (recorded honestly): the ViewModel is instantiated at the
// ROOT of HomeScreen — ABOVE the NavHost — so its owner is the always-
// composed shell, not the Home tab's back-stack entry. That preserves the
// v0.21.0 root-level guarantees byte-for-byte: the zombie-session sweep
// and the "no open session → stop share/viewer" effect keep running on
// EVERY tab (a Home-tab-scoped ViewModel would have regressed that W1-gate
// behavior). Only the LISTENER ownership and the error routing changed.
//
// Audit fix (risk #5, "swallowed listener errors"): the old root listener
// passed onError = { } — listener failures were invisible. They are now
// ROUTED into [SessionsUiState.error]; the last known list is always kept
// (the v0.21.0 rule: a transient listener error must never unmount a live
// consent card or kill a running share).
// ═══════════════════════════════════════════════════════════════════════

/** UiState for the capped session watch: data + loaded flag + routed error. */
data class SessionsUiState(
    val sessions: List<SessionView> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null
)

class SessionWatchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private var stopWatch: (() -> Unit)? = null

    /**
     * (Re)start the capped session listener for [user]. Passing null resets
     * to the signed-out state (same as the old DisposableEffect teardown).
     * [describeError] maps exceptions to localized text — kept OUT of the
     * ViewModel so it stays context-free; the shell supplies
     * describeFirestoreError(context, e).
     */
    fun observe(user: FirebaseUser?, describeError: (Exception) -> String) {
        stopWatch?.invoke()
        stopWatch = null
        if (user == null) {
            _state.value = SessionsUiState()
            return
        }
        stopWatch = watchSessions(
            user,
            onUpdate = { list ->
                _state.value = SessionsUiState(sessions = list, loaded = true)
            },
            // v0.21.0 semantics: KEEP the last list on a transient listener
            // error — clearing it used to unmount the consent modal mid-ask
            // and re-prompt the same session after reconnect. But the
            // failure is no longer swallowed: it is routed into the state.
            onError = { e ->
                _state.value = _state.value.copy(error = describeError(e))
            }
        )
    }

    override fun onCleared() {
        stopWatch?.invoke()
        stopWatch = null
    }
}
