package com.family.remote.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Phase 4 — Pairing data layer.
 *
 * A pairing code is split into two parts:
 *  - invitationId : 12 characters since v0.25.0 (was 6), acts as the public
 *    lookup key (Firestore doc id). Legacy 6-char ids are still ACCEPTED at
 *    redemption so mixed-version pairs keep working during the transition.
 *  - token        : 20 hex chars (80 bits of SecureRandom), shared out-of-band
 *
 * Firestore stores ONLY the SHA-256 hash of the token — never the raw token —
 * and redemption happens inside a single Firestore transaction (atomic:
 * PENDING -> PAIRED + relationship created), enforced again by the security
 * rules on the server side.
 */

/** Thrown when a pairing code cannot be redeemed (user-facing message inside). */
class RedemptionException(message: String) : Exception(message)

/**
 * Thrown when a Firestore call cannot finish within [RPC_TIMEOUT_MILLIS].
 * Firestore writes/reads have NO built-in timeout: when the app cannot reach
 * the Firestore backend (offline, database never created, network blocking
 * gRPC) the Task stays pending forever and the UI would spin endlessly.
 * Every Firestore call therefore goes through [awaitWithTimeout].
 */
class FirestoreUnreachableException(what: String, waitedMillis: Long = RPC_TIMEOUT_MILLIS) : Exception(
    "Could not reach Firestore while $what (waited ${waitedMillis / 1000} s). " +
        "Check the internet connection, and in the Firebase console verify that the " +
        "Firestore Database was actually created (Build → Firestore Database must show " +
        "data, not a \"Create database\" button)."
)

/** Hard ceiling for every single Firestore round-trip. */
private const val RPC_TIMEOUT_MILLIS = 30_000L

/**
 * Await a Firestore Task with a hard timeout so the UI can never hang forever.
 * v0.9.2 — [timeoutMillis] lets optional, non-blocking reads (relay config)
 * use a much shorter ceiling than the mandatory session calls.
 */
suspend fun <T> Task<T>.awaitWithTimeout(
    what: String,
    timeoutMillis: Long = RPC_TIMEOUT_MILLIS
): T =
    try {
        withTimeout(timeoutMillis) { this@awaitWithTimeout.await() }
    } catch (e: TimeoutCancellationException) {
        throw FirestoreUnreachableException(what, timeoutMillis)
    }

/**
 * Translate a Firestore failure into a short, actionable user-facing message.
 * Distinguishes the three realistic root causes: rules, missing database,
 * unreachable network.
 */
fun describeFirestoreError(context: Context, e: Exception): String = when (e) {
    is FirestoreUnreachableException -> e.message ?: "Could not reach Firestore."
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            context.getString(R.string.err_perm_denied)
        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        FirebaseFirestoreException.Code.NOT_FOUND ->
            context.getString(R.string.err_db_missing)
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.UNKNOWN ->
            context.getString(R.string.err_unreachable)
        else -> context.getString(R.string.err_generic, e.code, e.message ?: "unknown")
    }
    else -> e.message ?: "Unknown error"
}

data class InviteCode(
    val invitationId: String,
    val token: String,
    /** When this invitation expires (client clock); 0 until created in Firestore. */
    val expiresAtMillis: Long = 0L,
    /** Phase 12 — the role this code creates, frozen at creation time. */
    val kind: String = REL_KIND_FAMILY
) {
    /** The string the user sees, types or reads aloud: ABC123-1a2b3c… */
    val code: String get() = "$invitationId-$token"

    /**
     * What will be encoded into the QR image. Phase 12 — the payload now
     * carries the role marker (&k=fam / &k=gst) so the SCANNING phone can
     * show a role-preview sheet BEFORE anything is redeemed. The marker is
     * cosmetic only: the final kind is always re-read from the Firestore
     * invitation document inside the redemption transaction, so a doctored
     * QR can never flip a guest code into family (or vice versa).
     */
    val qrPayload: String get() =
        "https://family-remote-support.app/pair#inv=$invitationId&tok=$token" +
            if (kind == REL_KIND_GUEST) "&k=gst" else "&k=fam"
}

data class InvitationView(
    val id: String,
    val status: String,           // PENDING | PAIRED | CANCELLED
    val expiresAtMillis: Long,
    val claimedEmail: String?,
    val kind: String = "family"   // family | guest (Phase 10)
)

data class RelationshipView(
    val id: String,
    val helperUid: String,
    val helperEmail: String,
    val recipientUid: String,
    val recipientEmail: String,
    val recipientDeviceId: String,
    val recipientDeviceModel: String,
    val status: String,           // ACTIVE | REVOKED | EXPIRED
    val kind: String = "family",  // family | guest (Phase 10)
    /** 0 for family links; live-until for guest links (Phase 10). */
    val guestExpiresAtMillis: Long = 0L
) {
    /** True while a guest window is live (family links are always live). */
    val guestLive: Boolean
        get() = kind != "guest" ||
            (guestExpiresAtMillis > 0L && System.currentTimeMillis() < guestExpiresAtMillis)
}

private const val INVITATION_COLLECTION = "pairingInvitations"
// internal: SessionRepo also reads the relationship doc to cap a guest
// session's lifetime at the guest window (Phase 10).
internal const val RELATIONSHIP_COLLECTION = "trustedDeviceRelationships"

/** Invitation validity window (must stay consistent with firestore.rules). */
private const val INVITE_TTL_MILLIS = 5 * 60 * 1000L

/**
 * Phase 10 — how long a GUEST relationship stays usable after redemption.
 * Family links are permanent (until revoked); a guest link is a short,
 * server-bounded window for one-time outside help. The rules re-bound this
 * to request.time + 3 h so no client can mint a longer-lived guest.
 */
const val GUEST_RELATIONSHIP_TTL_MILLIS = 2 * 60 * 60 * 1000L

/** Relationship doc kinds (Phase 10) — "guest" can never become "family". */
const val REL_KIND_FAMILY = "family"
const val REL_KIND_GUEST = "guest"

/**
 * Phase 12 — the small identity badge drawn in the MIDDLE of a generated
 * QR code, so anyone can tell a Family code from a Guest code at a glance:
 *  - FAMILY → 👪 (or "F") — permanent, trusted, for family only.
 *  - GUEST  → ⏳ (or "G") — temporary 2-hour window, for outside help.
 * The badge sits on a white rounded plate and the QR is encoded with
 * error-correction level H (~30% damage recovery) so the covered modules
 * stay decodable on every camera.
 */
// v0.27.0 (Phase 19.1): labelBn removed — legend labels now come from the
// localized string resources (qr_badge_family / qr_badge_guest) in QrScanner;
// glyph + fallback stay language-neutral (drawn inside the QR bitmap).
enum class QrBadge(val glyph: String, val fallback: String) {
    FAMILY("\uD83D\uDC6A", "F"),
    GUEST("⏳", "G");

    companion object {
        /** Maps a relationship/invitation kind string to its badge. */
        fun ofKind(kind: String): QrBadge =
            if (kind == REL_KIND_GUEST) GUEST else FAMILY
    }
}

// No I, L, O, 0 or 1 — characters that are easy to misread over a phone call.
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/**
 * v0.25.0 (Phase 18.6 audit — the §56 deferred finding, app-side half): the
 * invitation doc id is fetchable by ANY signed-in account (rules: get if
 * signedIn), so the id IS the entire brute-force space. 31^6 ≈ 8.9×10^8
 * (~30 bits) was enumerable — a hostile signed-in account hammering fresh
 * 5-char-format guesses would burn Firestore reads (404 GETs still bill) and
 * had a small but real chance to land a live PENDING invite before its 5-min
 * expiry. 31^12 ≈ 7.9×10^17 (~59.5 bits) makes id enumeration computationally
 * dead while staying typeable/readable-aloud (no ambiguous glyphs, same
 * alphabet). QR payloads carry any length already; only the manual-typed
 * extraction regex in QrScanner.kt needed widening.
 *
 * Pairing stays version-locked like every past APK bump: a NEW recipient's
 * 12-char code needs a NEW helper app to redeem (an old helper's length check
 * rejects it with an honest message), while a NEW helper can still redeem an
 * OLD recipient's 6-char code (legacy acceptance below). Existing
 * relationships are untouched — pairing codes only matter at pair time.
 */
private const val INVITE_ID_LENGTH = 12

/** v0.25.0 (Phase 18.6): ids minted by older APKs — still redeemable. */
private const val LEGACY_INVITE_ID_LENGTH = 6
private const val TOKEN_BYTES = 10 // -> 20 hex chars (80-bit one-time token)

fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun generateInviteCode(): InviteCode {
    val random = SecureRandom()
    val id = StringBuilder(INVITE_ID_LENGTH)
    repeat(INVITE_ID_LENGTH) {
        id.append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)])
    }
    val tokenBytes = ByteArray(TOKEN_BYTES)
    random.nextBytes(tokenBytes)
    val token = tokenBytes.joinToString("") { "%02x".format(it) }
    return InviteCode(id.toString(), token)
}

/**
 * Accepts the code with or without dashes/spaces and in any letter case:
 * "ABCD2345XY67-1a2b…", "abcd2345xy67 1a2b…", "ABCD2345XY671a2b…" are the same
 * code. v0.25.0 (Phase 18.6): a 32-char NEW code (12+20) or a 26-char legacy
 * code (6+20, minted by older APKs) — both redeem; anything else is rejected
 * with an honest message that names both formats.
 */
fun parsePairingCode(context: Context, raw: String): InviteCode {
    val cleaned = raw.replace(" ", "").replace("-", "").trim().uppercase()
    val tokenChars = TOKEN_BYTES * 2
    val idLen = when (cleaned.length) {
        INVITE_ID_LENGTH + tokenChars -> INVITE_ID_LENGTH
        LEGACY_INVITE_ID_LENGTH + tokenChars -> LEGACY_INVITE_ID_LENGTH
        else -> throw RedemptionException(
            context.getString(R.string.err_code_length,
                INVITE_ID_LENGTH + tokenChars, INVITE_ID_LENGTH, tokenChars,
                LEGACY_INVITE_ID_LENGTH + tokenChars, cleaned.length)
        )
    }
    return InviteCode(
        invitationId = cleaned.substring(0, idLen),
        token = cleaned.substring(idLen).lowercase()
    )
}

/**
 * Create a fresh 5-minute invitation owned by [user] for their device.
 * Phase 10 — [kind] picks the pairing mode: "family" (permanent trusted
 * link) or "guest" (redeems into a temporary 2-hour link). The kind is
 * frozen into the invitation and cannot be changed afterwards.
 */
suspend fun createInvitation(
    user: FirebaseUser,
    deviceId: String,
    deviceModel: String,
    kind: String = REL_KIND_FAMILY
): InviteCode {
    val db = Fb.db
    repeat(3) {
        val invite = generateInviteCode()
        val ref = db.collection(INVITATION_COLLECTION).document(invite.invitationId)
        val exists = try {
            ref.get().awaitWithTimeout("checking the new code").exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreUnreachableException) {
            throw e // surface connectivity problems immediately, do not swallow
        } catch (_: Exception) {
            false // id collision is astronomically unlikely; try another id
        }
        if (exists) return@repeat

        val expiryMillis = System.currentTimeMillis() + INVITE_TTL_MILLIS
        val doc = hashMapOf(
            "inviterUid" to user.uid,
            "inviterEmail" to (user.email ?: ""),
            "recipientDeviceId" to deviceId,
            "recipientDeviceModel" to deviceModel,
            "status" to "PENDING",
            "kind" to kind,
            "tokenHash" to sha256Hex(invite.token),
            // v0.21.0 (audit — pairing token was decorative): the RAW token
            // is now stored alongside its hash. The security rules compare the
            // claimer's submitted claimToken against THIS field server-side,
            // so a patched/hostile client can no longer redeem an invitation
            // it only enumerated (the old rules checked nothing about the
            // token; only honest clients did). The field is unreadable to
            // anyone who cannot already read the invitation doc.
            "token" to invite.token,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(expiryMillis))
        )
        ref.set(doc).awaitWithTimeout("creating the pairing code")
        return invite.copy(expiresAtMillis = expiryMillis, kind = kind)
    }
    throw IllegalStateException("Could not generate a unique code — please try again.")
}

/** Live status of one invitation (waiting → paired / cancelled / expired). */
fun watchInvitation(
    invId: String,
    onChange: (InvitationView?) -> Unit,
    onError: (Exception) -> Unit = {}
): ListenerRegistration =
    Fb.db
        .collection(INVITATION_COLLECTION)
        .document(invId)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                onChange(null)
                return@addSnapshotListener
            }
            onChange(
                InvitationView(
                    id = snap.id,
                    status = snap.getString("status") ?: "PENDING",
                    expiresAtMillis = snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                    claimedEmail = snap.getString("claimedEmail"),
                    kind = snap.getString("kind") ?: REL_KIND_FAMILY
                )
            )
        }

/** Inviter cancels their own pending invitation. */
suspend fun cancelInvitation(invId: String) {
    Fb.db
        .collection(INVITATION_COLLECTION)
        .document(invId)
        .update(
            mapOf(
                "status" to "CANCELLED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .awaitWithTimeout("cancelling the code")
}

/**
 * Redeem a code as the helper. One atomic transaction:
 * verify PENDING + expiry + token hash → invitation PENDING → PAIRED →
 * trusted relationship created (or re-activated if the pair re-invited).
 *
 * Phase 10 — the invitation's kind decides the resulting link:
 *  - family invitation → permanent link (unchanged behavior)
 *  - guest invitation  → temporary link with a bounded guestExpiresAt
 *    window; a guest code can NEVER attach to (or convert) an existing
 *    family link, and a family code can NEVER resurrect a guest link —
 *    guest != family is enforced here AND again by the security rules.
 *
 * Firestore transactions require ALL reads to happen before ANY write
 * (v0.6.1: the relationship read used to sit after the invitation update,
 * which made every redemption fail with INVALID_ARGUMENT). So: read both
 * documents first, run every validation, and only then write.
 */
suspend fun redeemCode(context: Context, user: FirebaseUser, rawCode: String): RelationshipView {
    val code = parsePairingCode(context, rawCode)
    val tokenHash = sha256Hex(code.token)
    val db = Fb.db
    return db.runTransaction { tx ->
        // ---------- reads (must all come before any write) ----------
        val invRef = db.collection(INVITATION_COLLECTION).document(code.invitationId)
        val snap = tx.get(invRef)
        if (!snap.exists()) {
            throw RedemptionException(
                context.getString(R.string.err_code_not_found, code.invitationId.length)
            )
        }
        val inviterUid = snap.getString("inviterUid") ?: ""
        if (inviterUid == user.uid) {
            throw RedemptionException(
                context.getString(R.string.err_own_code)
            )
        }
        // v0.21.0 (audit): RESUME path — if THIS user already claimed the
        // invitation (a previous attempt committed the claim but died before
        // the relationship write), skip the re-claim (the rules would refuse
        // the PENDING→PAIRED transition) and just finish the relationship.
        val alreadyMine = snap.getString("status") == "PAIRED" &&
            snap.getString("claimedBy") == user.uid
        if (!alreadyMine) {
            if (snap.getString("status") != "PENDING") {
                throw RedemptionException(context.getString(R.string.err_already_used))
            }
            val expiresAt = snap.getTimestamp("expiresAt")
            if (expiresAt != null && expiresAt.toDate().before(Date())) {
                throw RedemptionException(context.getString(R.string.err_expired))
            }
            if (snap.getString("tokenHash") != tokenHash) {
                throw RedemptionException(context.getString(R.string.err_wrong_code))
            }
        }

        val recipientEmail = snap.getString("inviterEmail") ?: ""
        val recipientDeviceId = snap.getString("recipientDeviceId") ?: ""
        val recipientDeviceModel = snap.getString("recipientDeviceModel") ?: ""
        val inviteKind = snap.getString("kind") ?: REL_KIND_FAMILY
        val relRef = db.collection(RELATIONSHIP_COLLECTION)
            .document("${user.uid}_$inviterUid")
        val existing = tx.get(relRef)
        val existingKind = existing.getString("kind") ?: REL_KIND_FAMILY

        // ---------- Phase 10: guest != family (client-side fence) ----------
        if (existing.exists() && inviteKind == REL_KIND_GUEST && existingKind != REL_KIND_GUEST) {
            throw RedemptionException(
                context.getString(R.string.err_pair_family_exists)
            )
        }
        if (existing.exists() && inviteKind != REL_KIND_GUEST && existingKind == REL_KIND_GUEST) {
            throw RedemptionException(
                context.getString(R.string.err_pair_guest_exists)
            )
        }
        val guestEndsMillis = System.currentTimeMillis() + GUEST_RELATIONSHIP_TTL_MILLIS

        // ---------- writes (only after every read above) ----------
        if (!alreadyMine) {
            tx.update(
                invRef,
                mapOf(
                    "status" to "PAIRED",
                    "claimedBy" to user.uid,
                    "claimedEmail" to (user.email ?: ""),
                    "claimedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    // v0.21.0 (audit): the claim now carries the RAW token so
                    // the SECURITY RULES can verify token knowledge
                    // SERVER-SIDE (claimToken == the stored token field).
                    // Client-side checks remain for friendly error messages.
                    "claimToken" to code.token
                )
            )
        }

        if (existing.exists()) {
            // Rules (Phase 4/10) only allow re-activation to change status
            // (+ the guest window refresh for guest links) — identity fields
            // are frozen after first pairing, and the kind can never flip.
            if (inviteKind == REL_KIND_GUEST) {
                tx.update(
                    relRef,
                    mapOf(
                        "status" to "ACTIVE",
                        "guestExpiresAt" to Timestamp(Date(guestEndsMillis)),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    relRef,
                    mapOf(
                        "status" to "ACTIVE",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        } else {
            val relDoc = hashMapOf(
                "helperUid" to user.uid,
                "helperEmail" to (user.email ?: ""),
                "recipientUid" to inviterUid,
                "recipientEmail" to recipientEmail,
                "recipientDeviceId" to recipientDeviceId,
                "recipientDeviceModel" to recipientDeviceModel,
                "pairKey" to "${user.uid}_$inviterUid",
                "status" to "ACTIVE",
                "kind" to inviteKind,
                "createdByInvitation" to code.invitationId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (inviteKind == REL_KIND_GUEST) {
                relDoc["guestExpiresAt"] = Timestamp(Date(guestEndsMillis))
            }
            tx.set(relRef, relDoc, SetOptions.merge())
        }

        RelationshipView(
            id = relRef.id,
            helperUid = user.uid,
            helperEmail = user.email ?: "",
            recipientUid = inviterUid,
            recipientEmail = recipientEmail,
            recipientDeviceId = recipientDeviceId,
            recipientDeviceModel = recipientDeviceModel,
            status = "ACTIVE",
            kind = inviteKind,
            guestExpiresAtMillis = if (inviteKind == REL_KIND_GUEST) guestEndsMillis else 0L
        )
    }.awaitWithTimeout("pairing the two devices")
}

/** Live list of relationships where [user] is helper or recipient. */
fun watchRelationships(
    user: FirebaseUser,
    onUpdate: (List<RelationshipView>) -> Unit,
    onError: (Exception) -> Unit = {}
): () -> Unit {
    val db = Fb.db
    var helperSide: List<RelationshipView> = emptyList()
    var recipientSide: List<RelationshipView> = emptyList()
    fun emit() = onUpdate(helperSide + recipientSide)

    val regHelper = db.collection(RELATIONSHIP_COLLECTION)
        .whereEqualTo("helperUid", user.uid)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            helperSide = snap?.documents?.mapNotNull { it.toRelationshipView() } ?: emptyList()
            emit()
        }
    val regRecipient = db.collection(RELATIONSHIP_COLLECTION)
        .whereEqualTo("recipientUid", user.uid)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            recipientSide = snap?.documents?.mapNotNull { it.toRelationshipView() } ?: emptyList()
            emit()
        }
    return {
        regHelper.remove()
        regRecipient.remove()
    }
}

/** Either participant may revoke the relationship (rules enforce this too). */
suspend fun revokeRelationship(relId: String) {
    Fb.db
        .collection(RELATIONSHIP_COLLECTION)
        .document(relId)
        .update(
            mapOf(
                "status" to "REVOKED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .awaitWithTimeout("revoking the pair")
}

/**
 * Phase 10 — lazy cleanup of an over-time guest link (ACTIVE -> EXPIRED).
 * Best effort from whichever app notices first; the rules only allow this
 * exact transition on a guest doc whose guestExpiresAt has passed.
 */
suspend fun expireGuestRelationship(relId: String) {
    Fb.db
        .collection(RELATIONSHIP_COLLECTION)
        .document(relId)
        .update(
            mapOf(
                "status" to "EXPIRED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .awaitWithTimeout("expiring the guest link")
}

/**
 * Phase 10 — remove a GUEST link entirely (delete the doc). This is the
 * documented, non-automatic path out of a guest pair: only afterwards can
 * the same pair be re-paired as family. The rules allow deletes for guest
 * docs only — a family link can never be deleted this way.
 */
suspend fun removeGuestRelationship(relId: String) {
    Fb.db
        .collection(RELATIONSHIP_COLLECTION)
        .document(relId)
        .delete()
        .awaitWithTimeout("removing the guest link")
}

private fun DocumentSnapshot.toRelationshipView(): RelationshipView? {
    val helperUid = getString("helperUid") ?: return null
    val recipientUid = getString("recipientUid") ?: return null
    val kind = getString("kind") ?: REL_KIND_FAMILY
    return RelationshipView(
        id = id,
        helperUid = helperUid,
        helperEmail = getString("helperEmail") ?: "",
        recipientUid = recipientUid,
        recipientEmail = getString("recipientEmail") ?: "",
        recipientDeviceId = getString("recipientDeviceId") ?: "",
        recipientDeviceModel = getString("recipientDeviceModel") ?: "",
        status = getString("status") ?: "ACTIVE",
        kind = kind,
        guestExpiresAtMillis = if (kind == REL_KIND_GUEST) {
            getTimestamp("guestExpiresAt")?.toDate()?.time ?: 0L
        } else {
            0L
        }
    )
}

/**
 * Render [content] as a QR bitmap (black on white). Returns null if the
 * payload cannot be encoded — the text code is always shown next to it.
 *
 * Phase 12 — when [badge] is given, a small white rounded plate with the
 * role glyph is drawn in the CENTER of the code (👪 Family / ⏳ Guest).
 * The QR is always encoded with error-correction level H so the covered
 * modules stay decodable; the plate covers well under the recoverable
 * area of the code.
 */
fun qrBitmap(content: String, size: Int = 560, badge: QrBadge? = null): Bitmap? = try {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    if (badge != null) drawQrCenterBadge(bitmap, badge)
    bitmap
} catch (_: Exception) {
    null
}

/**
 * Phase 12 — paint the role badge in the middle of an already-rendered QR
 * bitmap: a white rounded plate with a thin outline, and the emoji glyph
 * (falling back to a plain letter when the emoji font is missing).
 */
private fun drawQrCenterBadge(bitmap: Bitmap, badge: QrBadge) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val plateSide = w * 0.24f
    val left = (w - plateSide) / 2f
    val top = (h - plateSide) / 2f
    val rect = RectF(left, top, left + plateSide, top + plateSide)
    val radius = plateSide * 0.22f

    val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(226, 232, 240) // #E2E8F0 — the design-system outlineVariant
        style = Paint.Style.STROKE
        strokeWidth = plateSide * 0.04f
    }
    canvas.drawRoundRect(rect, radius, radius, platePaint)
    canvas.drawRoundRect(rect, radius, radius, outlinePaint)

    val glyph = badge.glyph
    val glyphSize = plateSide * 0.52f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = glyphSize
        textAlign = Paint.Align.CENTER
        color = Color.rgb(23, 32, 51) // #172033 — onSurface
    }
    val hasEmoji = Build.VERSION.SDK_INT < 23 || paint.hasGlyph(glyph)
    val drawn = if (hasEmoji) glyph else badge.fallback
    if (!hasEmoji) {
        paint.isFakeBoldText = true
    }
    val fm = paint.fontMetrics
    val baseline = (rect.top + rect.bottom) / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(drawn, rect.centerX(), baseline, paint)
}
