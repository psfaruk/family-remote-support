package com.family.remote.support.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.family.remote.support.R
import com.family.remote.support.SessionException
import com.family.remote.support.SessionView
import com.family.remote.support.describeFirestoreError
import com.family.remote.support.denySession
import com.family.remote.support.grantSession
import com.family.remote.support.localizedMessage
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// v0.37.0 (dedupe wave) — ONE grant/deny "answerSession" implementation.
//
// The audit (duplicate cluster #1) found the SAME Firestore answer action
// in THREE drifting copies: HomeConsentCard + HomeConsentDialog (HomeTab)
// and AddDeviceTab's tab-local answerSession. Each carried the identical
// busy/message/SessionException/describeFirestoreError skeleton but
// DIFFERENT strings (msg_allow_ok/msg_deny_ok vs ph_allowed_15m/
// ph_denied_short/err_answer_failed). All consent surfaces now share this
// holder; the canonical strings are msg_allow_ok / msg_deny_ok (success)
// and msg_allow_fail / msg_deny_fail (unexpected Firestore failures).
//
// AddDeviceTab's copy turned out to be dead code — its consent case
// renders HomeConsentCard, which owns its answer state — so it was
// deleted outright rather than rewired: the drift source is gone.
// ═══════════════════════════════════════════════════════════════════════

/**
 * State + actions for answering (allow / deny) an incoming session request.
 *
 * Call sites render [busy] on their buttons' enabled state and [message]
 * wherever they already showed the result line — the holder owns NO UI.
 * [allow]/[deny] launch on the call site's own coroutine scope
 * (rememberCoroutineScope), so busy/message reset together with the caller,
 * exactly like the previous local copies did.
 */
internal class AnswerSessionState(
    private val scope: CoroutineScope,
    private val context: Context,
    private val user: FirebaseUser
) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * Allow the request ([grantSession]). [onSuccess] runs INSIDE the same
     * coroutine, right after the success message is set — the consent
     * dialog uses it to mark the ask shown on SessionUiBus, hold the
     * result for 600 ms, and only then close.
     */
    fun allow(session: SessionView, onSuccess: suspend () -> Unit = {}) =
        answer(session, allow = true, onSuccess = onSuccess)

    /** Deny the request ([denySession]); [onSuccess] as in [allow]. */
    fun deny(session: SessionView, onSuccess: suspend () -> Unit = {}) =
        answer(session, allow = false, onSuccess = onSuccess)

    private fun answer(session: SessionView, allow: Boolean, onSuccess: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try {
                if (allow) {
                    grantSession(session.id, user.uid)
                    message = context.getString(R.string.msg_allow_ok)
                } else {
                    denySession(session.id, user.uid)
                    message = context.getString(R.string.msg_deny_ok)
                }
                onSuccess()
            } catch (e: SessionException) {
                message = e.localizedMessage(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = context.getString(
                    if (allow) R.string.msg_allow_fail else R.string.msg_deny_fail,
                    describeFirestoreError(context, e)
                )
            } finally {
                busy = false
            }
        }
    }
}

/**
 * v0.37.0 (dedupe wave) — remembers the ONE answerSession holder for the
 * signed-in [user] (was 3 drifting copies: HomeConsentCard,
 * HomeConsentDialog, AddDeviceTab — audit duplicate cluster #1).
 */
@Composable
internal fun rememberAnswerSession(user: FirebaseUser): AnswerSessionState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(scope, context, user) {
        AnswerSessionState(scope, context, user)
    }
}
