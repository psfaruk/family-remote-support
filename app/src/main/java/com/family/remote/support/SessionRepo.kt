package com.family.remote.support

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.CancellationException

/**
 * Phase 6 — support session data layer.
 *
 * Pairing is only a record; it NEVER grants access. Every act of help needs
 * its own session, and every session needs a fresh, live "yes" from the
 * phone that is being helped:
 *
 *   helper: "Start session"  ->  supportSessions doc (REQUESTED, 2-min answer
 *   deadline)  ->  recipient taps Allow (status ACTIVE for 15 minutes) or
 *   Deny  ->  either side can End early; the session also auto-ends when the
 *   15 minutes pass.
 *
 * The state machine lives in TWO places that must stay in agreement:
 *  - here (client transactions: read -> validate -> write, never write-then-read)
 *  - firestore.rules (the server re-checks every transition and rejects
 *    anything else, including wrong callers, changed field sets and
 *    over-long sessions)
 *
 * Firestore collection: supportSessions
 */

class SessionException(message: String) : Exception(message)

internal const val SESSION_COLLECTION = "supportSessions"

/** How long the recipient has to answer an ask before it expires. */
const val SESSION_RESPONSE_TTL_MILLIS = 2 * 60 * 1000L
/** How long an ALLOWED session stays active before it auto-ends. */
const val SESSION_ACTIVE_TTL_MILLIS = 15 * 60 * 1000L

data class SessionView(
    val id: String,
    /** Relationship doc id == pairKey == helperUid_recipientUid. */
    val relId: String,
    val helperUid: String,
    val helperEmail: String,
    val recipientUid: String,
    val recipientEmail: String,
    val recipientDeviceModel: String,
    val status: String, // REQUESTED | ACTIVE | ENDED | DENIED | CANCELLED | EXPIRED
    val requestedAtMillis: Long,
    val respondDeadlineMillis: Long,
    /** 0 until the session is granted. */
    val sessionEndsAtMillis: Long,
    /** "helperEmail" | "recipientEmail" of the ender, or "timeout". */
    val endedBy: String?,
    /** Phase 10 — family | guest. A guest session can never outlive the
     *  guest link's window (enforced at grant time and by the rules). */
    val kind: String = "family"
)

private fun DocumentSnapshot.toSessionView(): SessionView? {
    val helperUid = getString("helperUid") ?: return null
    val recipientUid = getString("recipientUid") ?: return null
    return SessionView(
        id = id,
        relId = getString("relId") ?: "",
        helperUid = helperUid,
        helperEmail = getString("helperEmail") ?: "",
        recipientUid = recipientUid,
        recipientEmail = getString("recipientEmail") ?: "",
        recipientDeviceModel = getString("recipientDeviceModel") ?: "",
        status = getString("status") ?: "REQUESTED",
        requestedAtMillis = getTimestamp("requestedAt")?.toDate()?.time ?: 0L,
        respondDeadlineMillis = getTimestamp("respondDeadline")?.toDate()?.time ?: 0L,
        sessionEndsAtMillis = getTimestamp("sessionEndsAt")?.toDate()?.time ?: 0L,
        endedBy = getString("endedBy"),
        kind = getString("kind") ?: "family"
    )
}

/**
 * Helper side: ask permission to start a support session on the paired
 * recipient's phone. The duplicate-check runs as a plain query (the Android
 * Firestore SDK does not support query reads inside transactions); the write
 * itself is one atomic set whose conditions the security rules re-verify.
 * The UI keeps this action single-shot, so the check + write gap is not a
 * realistic race: only the helper's phone can start a session.
 */
suspend fun requestSession(user: FirebaseUser, rel: RelationshipView): SessionView {
    val db = Fb.db
    // ---------- duplicate-check: one open session per pair ----------
    // The query MUST constrain helperUid to the caller: the security rules can
    // only prove `list` permission for queries that pin helperUid/recipientUid
    // to request.auth.uid (a bare relId filter proves nothing to the rules
    // engine and would fail with PERMISSION_DENIED even with correct rules).
    val open = db.collection(SESSION_COLLECTION)
        .whereEqualTo("relId", rel.id)
        .whereEqualTo("helperUid", user.uid)
        .whereIn("status", listOf("REQUESTED", "ACTIVE"))
        .limit(1)
        .get()
        .awaitWithTimeout("checking for an open session")
    if (!open.isEmpty) {
        val existing = open.documents.first().toSessionView()
        throw SessionException(
            if (existing?.status == "REQUESTED") {
                "A request is already waiting for ${rel.recipientEmail}. " +
                    "Wait for an answer or cancel it."
            } else {
                "A support session is already active with ${rel.recipientEmail}."
            }
        )
    }

    // ---------- write ----------
    val deadline = Date(System.currentTimeMillis() + SESSION_RESPONSE_TTL_MILLIS)
    val ref = db.collection(SESSION_COLLECTION).document()
    ref.set(
        mapOf(
            "relId" to rel.id,
            "helperUid" to user.uid,
            "helperEmail" to (user.email ?: ""),
            "recipientUid" to rel.recipientUid,
            "recipientEmail" to rel.recipientEmail,
            "recipientDeviceModel" to rel.recipientDeviceModel,
            "status" to "REQUESTED",
            "kind" to rel.kind,
            "requestedAt" to FieldValue.serverTimestamp(),
            "respondDeadline" to Timestamp(deadline),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    ).awaitWithTimeout("asking for permission to help")
    return SessionView(
        id = ref.id,
        relId = rel.id,
        helperUid = user.uid,
        helperEmail = user.email ?: "",
        recipientUid = rel.recipientUid,
        recipientEmail = rel.recipientEmail,
        recipientDeviceModel = rel.recipientDeviceModel,
        status = "REQUESTED",
        requestedAtMillis = System.currentTimeMillis(),
        respondDeadlineMillis = deadline.time,
        sessionEndsAtMillis = 0L,
        endedBy = null,
        kind = rel.kind
    )
}

/**
 * Recipient side: allow the ask. One atomic transaction:
 *   read  - the session doc; caller must be the asked recipient, the status
 *           must still be REQUESTED and the 2-minute deadline not passed
 *   write - status ACTIVE with a sessionEndsAt lifetime
 *
 * Phase 10 — a GUEST session can never outlive its guest link's window:
 * when the session kind is guest, the relationship's guestExpiresAt is read
 * in the same transaction and the session end is capped at min(15 min,
 * guest window). The rules re-verify this cap server-side.
 */
suspend fun grantSession(sessionId: String, uid: String): SessionView {
    val db = Fb.db
    return db.runTransaction { tx ->
        // ---------- read ----------
        val ref = db.collection(SESSION_COLLECTION).document(sessionId)
        val snap = tx.get(ref)
        if (!snap.exists()) {
            throw SessionException("This request no longer exists.")
        }
        val session = snap.toSessionView()
            ?: throw SessionException("This request is unreadable — ask the helper to resend.")
        if (session.recipientUid != uid) {
            throw SessionException("Only the phone that was asked can allow this session.")
        }
        if (session.status != "REQUESTED") {
            throw SessionException("This request was already answered (${session.status}).")
        }
        // 5 s of grace for small clock skew; the rules re-check with server time.
        if (System.currentTimeMillis() > session.respondDeadlineMillis + 5000) {
            throw SessionException("This request already expired — ask the helper to send a new one.")
        }

        // ---------- lifetime: 15 min, capped by a live guest window ----------
        var endsAtMillis = System.currentTimeMillis() + SESSION_ACTIVE_TTL_MILLIS
        if (session.kind == "guest" && session.relId.isNotBlank()) {
            val relSnap = tx.get(
                db.collection(RELATIONSHIP_COLLECTION).document(session.relId)
            )
            val guestEnds = relSnap.getTimestamp("guestExpiresAt")?.toDate()?.time ?: 0L
            if (guestEnds in 1 until endsAtMillis) endsAtMillis = guestEnds
        }
        val endsAt = Timestamp(Date(endsAtMillis))

        // ---------- write ----------
        tx.update(
            ref,
            mapOf(
                "status" to "ACTIVE",
                "respondedAt" to FieldValue.serverTimestamp(),
                "grantedAt" to FieldValue.serverTimestamp(),
                "sessionEndsAt" to endsAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        session.copy(status = "ACTIVE", sessionEndsAtMillis = endsAt.toDate().time)
    }.awaitWithTimeout("allowing the session")
}

/** Recipient side: refuse the ask (REQUESTED -> DENIED). */
suspend fun denySession(sessionId: String, uid: String) {
    val db = Fb.db
    db.runTransaction { tx ->
        // ---------- read ----------
        val ref = db.collection(SESSION_COLLECTION).document(sessionId)
        val snap = tx.get(ref)
        if (!snap.exists()) {
            throw SessionException("This request no longer exists.")
        }
        val session = snap.toSessionView()
            ?: throw SessionException("This request is unreadable.")
        if (session.recipientUid != uid) {
            throw SessionException("Only the phone that was asked can deny this request.")
        }
        if (session.status != "REQUESTED") {
            throw SessionException("This request was already answered.")
        }

        // ---------- write ----------
        tx.update(
            ref,
            mapOf(
                "status" to "DENIED",
                "respondedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        Unit
    }.awaitWithTimeout("denying the request")
}

/** Helper side: take back their own unanswered ask (REQUESTED -> CANCELLED). */
suspend fun cancelSession(sessionId: String, uid: String) {
    val db = Fb.db
    db.runTransaction { tx ->
        // ---------- read ----------
        val ref = db.collection(SESSION_COLLECTION).document(sessionId)
        val snap = tx.get(ref)
        if (!snap.exists()) {
            throw SessionException("This request no longer exists.")
        }
        val session = snap.toSessionView()
            ?: throw SessionException("This request is unreadable.")
        if (session.helperUid != uid) {
            throw SessionException("Only the helper can cancel their own request.")
        }
        if (session.status != "REQUESTED") {
            throw SessionException("This request was already answered.")
        }

        // ---------- write ----------
        tx.update(
            ref,
            mapOf(
                "status" to "CANCELLED",
                "respondedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        Unit
    }.awaitWithTimeout("cancelling the request")
}

/**
 * Either side ends an active session (ACTIVE -> ENDED). With [byTimeout] the
 * client writes endedBy="timeout" — the lazy auto-end any participant's app
 * performs once the 15 minutes are over. Idempotent: ending an already
 * ended session does nothing.
 */
suspend fun endSession(sessionId: String, uid: String, byTimeout: Boolean = false) {
    val db = Fb.db
    db.runTransaction { tx ->
        // ---------- read ----------
        val ref = db.collection(SESSION_COLLECTION).document(sessionId)
        val snap = tx.get(ref)
        val session = if (snap.exists()) snap.toSessionView() else null

        // ---------- write (only when still active) ----------
        if (session != null && session.status == "ACTIVE") {
            tx.update(
                ref,
                mapOf(
                    "status" to "ENDED",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "endedBy" to if (byTimeout) "timeout" else uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        Unit
    }.awaitWithTimeout("ending the session")
}

/**
 * Lazy cleanup of a stale ask (REQUESTED past its deadline -> EXPIRED).
 * Best effort from whichever client sees the staleness first — the rules let
 * either participant perform exactly this transition, and a race with a real
 * answer simply fails here without harm.
 */
suspend fun expireSession(sessionId: String) {
    Fb.db
        .collection(SESSION_COLLECTION)
        .document(sessionId)
        .update(
            mapOf(
                "status" to "EXPIRED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .awaitWithTimeout("expiring the old request")
}

/**
 * One-shot read of one session's final state. Used by the UI when an open
 * session closes, so the "Last session: ended/denied…" line survives even
 * though the live watches below only track open sessions.
 */
suspend fun getSession(sessionId: String): SessionView? {
    val snap = Fb.db
        .collection(SESSION_COLLECTION)
        .document(sessionId)
        .get()
        .awaitWithTimeout("loading the session result")
    return if (snap.exists()) snap.toSessionView() else null
}

/**
 * Live list of this account's OPEN sessions (both roles), newest first.
 *
 * Deliberately equality-only — helperUid/recipientUid + status-in, no orderBy:
 *   - runs on single-field automatic indexes, so NO composite index is needed
 *     (an orderBy(requestedAt) query would require one and fail with
 *     FAILED_PRECONDITION until it exists),
 *   - and it is provable against the security rules, which only allow listing
 *     sessions whose helperUid/recipientUid is the caller.
 *
 * The result set is complete: every REQUESTED/ACTIVE session of this account
 * is returned, which is exactly what the consent UI needs. Finished sessions
 * are fetched one-shot when an open one closes (see getSession + MainActivity).
 */
fun watchSessions(
    user: FirebaseUser,
    onUpdate: (List<SessionView>) -> Unit,
    onError: (Exception) -> Unit = {}
): () -> Unit {
    val db = Fb.db
    var helperSide: List<SessionView> = emptyList()
    var recipientSide: List<SessionView> = emptyList()
    fun emit() = onUpdate(
        (helperSide + recipientSide)
            .distinctBy { it.id }
            .sortedByDescending { it.requestedAtMillis }
    )

    val regHelper = db.collection(SESSION_COLLECTION)
        .whereEqualTo("helperUid", user.uid)
        .whereIn("status", listOf("REQUESTED", "ACTIVE"))
        .limit(10)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            helperSide = snap?.documents?.mapNotNull { it.toSessionView() } ?: emptyList()
            emit()
        }
    val regRecipient = db.collection(SESSION_COLLECTION)
        .whereEqualTo("recipientUid", user.uid)
        .whereIn("status", listOf("REQUESTED", "ACTIVE"))
        .limit(10)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            recipientSide = snap?.documents?.mapNotNull { it.toSessionView() } ?: emptyList()
            emit()
        }
    return {
        regHelper.remove()
        regRecipient.remove()
    }
}
