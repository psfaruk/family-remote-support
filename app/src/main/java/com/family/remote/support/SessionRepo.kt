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

/**
 * v0.22.0 (Phase 18.1 — Owner bug: "রিবুটের পর family আর কানেক্ট হয় না") —
 * a REQUESTED ask whose 2-min answer window is over, or an ACTIVE session
 * past its 15-min lifetime (+4 s skew grace on both), is a ZOMBIE left
 * behind by a killed/rebooted phone. Any participant may perform the
 * matching cleanup (REQUESTED→EXPIRED / ACTIVE→ENDED("timeout") — both
 * transitions are already allowed by the security rules).
 */
fun SessionView.isStaleOpen(nowMillis: Long): Boolean = when (status) {
    "REQUESTED" ->
        respondDeadlineMillis > 0L && nowMillis > respondDeadlineMillis + STALE_GRACE_MILLIS
    "ACTIVE" ->
        sessionEndsAtMillis > 0L && nowMillis > sessionEndsAtMillis + STALE_GRACE_MILLIS
    else -> false
}

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
 * v0.22.0 (Phase 18.1 — Owner bug: "রিবুটের পর family আর কানেক্ট হয় না") —
 * a STALE open session no longer blocks a fresh ask. A reboot mid-session
 * (or an unanswered ask from before the reboot) leaves a zombie REQUESTED/
 * ACTIVE doc behind; the old code threw "already waiting/active" and the
 * Owner's only escape was tearing the pairing apart. Now the ask itself
 * performs the SAME validated cleanups the lazy UI expiry does — each is a
 * read-validate-write transaction the rules re-check server-side, so a
 * genuine grant/deny racing the cleanup simply wins and the loop reports
 * the honest "still open" error instead.
 *
 * Stale = REQUESTED past its 2-min deadline, or ACTIVE past its 15-min
 * lifetime (+4 s grace, matching the UI lazy-expiry's skew allowance).
 */
private const val STALE_GRACE_MILLIS = 4_000L

/** One cleanup attempt per stale doc, bounded so a hostile doc set cannot loop. */
private const val STALE_CLEANUP_ROUNDS = 4

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
    //
    // v0.22.0 (Phase 18.1): stale open docs are cleaned here (the same
    // transitions the rules already allow either participant to perform)
    // instead of blocking the ask forever.
    var blockedMessage: String? = null
    var round = 0
    while (round < STALE_CLEANUP_ROUNDS) {
        round += 1
        val open = db.collection(SESSION_COLLECTION)
            .whereEqualTo("relId", rel.id)
            .whereEqualTo("helperUid", user.uid)
            .whereIn("status", listOf("REQUESTED", "ACTIVE"))
            .limit(10)
            .get()
            .awaitWithTimeout("checking for an open session")
        if (open.isEmpty) {
            blockedMessage = null
            break // nothing open — proceed to the write below
        }

        val views = open.documents.mapNotNull { it.toSessionView() }
        if (views.isEmpty()) {
            // Docs exist but are unreadable — do NOT write a duplicate on top.
            blockedMessage =
                "An old session record is unreadable — please try again in a moment. " +
                    "(পুরনো সেশন রেকর্ড পড়া যাচ্ছে না — এক মুহূর্ত পরে আবার চেষ্টা করুন।)"
            break
        }
        val now = System.currentTimeMillis()
        val stale = views.filter { it.isStaleOpen(now) }
        if (stale.isEmpty) {
            // Genuinely open: a live ask is pending, or a live session runs.
            // Point the Owner at the escape hatch that always exists — the
            // session card's Cancel / End buttons.
            val existing = views.first()
            blockedMessage =
                if (existing.status == "REQUESTED") {
                    "A request is already waiting for ${rel.recipientEmail} — " +
                        "wait for the answer or cancel it from the card below. " +
                        "(অনুরোধ এখনও অপেক্ষমাণ — নিচের কার্ড থেকে বাতিল করা যায়।)"
                } else {
                    "A support session is already active with ${rel.recipientEmail} — " +
                        "end it from the card below. " +
                        "(সেশন চালু আছে — নিচের কার্ড থেকে শেষ করা যায়।)"
                }
            break
        }
        // Clean every stale doc in this batch, then re-check (a cleanup can
        // fail silently when a real grant/deny raced it — the next round's
        // re-query then sees the truth).
        blockedMessage = null
        stale.forEach { s ->
            try {
                if (s.status == "REQUESTED") expireSession(s.id)
                else endSession(s.id, user.uid, byTimeout = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort — the UI lazy expiry and the server sweep also
                // perform exactly these transitions
            }
        }
        if (round == STALE_CLEANUP_ROUNDS) {
            // Exhausted: cleanups kept failing (network/rules) — never write
            // a duplicate on top of a half-cleaned set; ask for a retry.
            blockedMessage =
                "An old session is still being cleaned up — please try again in a moment. " +
                    "(পুরনো সেশন পরিষ্কার হচ্ছে — এক মুহূর্ত পরে আবার চেষ্টা করুন।)"
        }
    }
    blockedMessage?.let { throw SessionException(it) }

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

        // ---------- write (only when still active AND we are a participant —
        // v0.21.0 (audit): the participant check is defense in depth; the
        // rules already enforce it server-side) ----------
        if (session != null && session.status == "ACTIVE" &&
            (session.helperUid == uid || session.recipientUid == uid)
        ) {
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
 *
 * v0.21.0 (audit): now a read-validate-write transaction — the old BLIND
 * unconditional update could race a just-granted session (helper-side lazy
 * expire flipping an ACTIVE session straight to EXPIRED seconds after
 * consent) if the rules were ever stale/loose.
 */
suspend fun expireSession(sessionId: String) {
    val db = Fb.db
    db.runTransaction { tx ->
        val ref = db.collection(SESSION_COLLECTION).document(sessionId)
        val snap = tx.get(ref)
        val session = if (snap.exists()) snap.toSessionView() else null
        val deadlineMillis = session?.respondDeadlineMillis ?: 0L
        if (session != null && session.status == "REQUESTED" &&
            deadlineMillis > 0 &&
            System.currentTimeMillis() > deadlineMillis + 2000
        ) {
            tx.update(
                ref,
                mapOf(
                    "status" to "EXPIRED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        Unit
    }.awaitWithTimeout("expiring the old request")
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
