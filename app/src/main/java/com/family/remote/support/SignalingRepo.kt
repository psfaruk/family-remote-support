package com.family.remote.support

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

/**
 * Phase 7 — Firestore-based WebRTC signaling.
 *
 * There is NO signaling server in this project (zero-cost, phone-only Owner).
 * The realtime database we already trust does the job: WebRTC offers, answers
 * and ICE candidates travel as short-lived documents under the session:
 *
 *     supportSessions/{sessionId}/signal/{autoId}
 *         aud      = uid of the INTENDED READER (queryable by that reader)
 *         from     = uid of the writer
 *         type     = "offer" | "answer" | "ice" | "bye"
 *         payload  = JSON-encoded SDP / ICE candidate
 *         createdAt
 *
 * The security rules re-check every field server-side: only participants of
 * an ACTIVE session may write, a writer may only address the OTHER
 * participant, and a reader may only read documents addressed to them.
 * Signal docs carry no long-lived secrets — SDP/ICE are per-session anyway —
 * and both sides delete their own queue as they start and finish.
 */
internal object SignalingRepo {

    private const val SIGNAL = "signal"

    /** Post one signal document addressed to [audUid]. */
    suspend fun send(
        sessionId: String,
        fromUid: String,
        audUid: String,
        type: String,
        payload: String
    ) {
        Fb.db.collection(SESSION_COLLECTION)
            .document(sessionId)
            .collection(SIGNAL)
            .add(
                mapOf(
                    "aud" to audUid,
                    "from" to fromUid,
                    "type" to type,
                    "payload" to payload,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .awaitWithTimeout("sending the $type signal")
    }

    /**
     * Live watch of this reader's signal queue. Every ADDED document is
     * delivered exactly once per registration (Firestore guarantees the
     * initial snapshot + change feed).
     */
    fun watch(
        sessionId: String,
        audUid: String,
        onSignal: (type: String, payload: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ): () -> Unit {
        val reg: ListenerRegistration = Fb.db.collection(SESSION_COLLECTION)
            .document(sessionId)
            .collection(SIGNAL)
            .whereEqualTo("aud", audUid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                for (change in snap?.documentChanges ?: emptyList()) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val type = change.document.getString("type") ?: continue
                    val payload = change.document.getString("payload") ?: ""
                    onSignal(type, payload)
                }
            }
        return { reg.remove() }
    }

    /**
     * Best-effort wipe of the documents addressed to [audUid] — called when a
     * side starts watching (clear stale rounds) and when it stops. Failures
     * are harmless: docs are tiny and per-session only.
     */
    fun clearAsync(sessionId: String, audUid: String) {
        Fb.db.collection(SESSION_COLLECTION)
            .document(sessionId)
            .collection(SIGNAL)
            .whereEqualTo("aud", audUid)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    doc.reference.delete()
                }
            }
    }
}
