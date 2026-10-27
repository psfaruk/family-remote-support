package com.family.remote.support

import android.graphics.Bitmap
import android.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import kotlinx.coroutines.tasks.await

/**
 * Phase 4 — Pairing data layer.
 *
 * A pairing code is split into two parts:
 *  - invitationId : 6 characters, acts as the public lookup key (Firestore doc id)
 *  - token        : 20 hex chars (80 bits of SecureRandom), shared out-of-band
 *
 * Firestore stores ONLY the SHA-256 hash of the token — never the raw token —
 * and redemption happens inside a single Firestore transaction (atomic:
 * PENDING -> PAIRED + relationship created), enforced again by the security
 * rules on the server side.
 */

/** Thrown when a pairing code cannot be redeemed (user-facing message inside). */
class RedemptionException(message: String) : Exception(message)

data class InviteCode(
    val invitationId: String,
    val token: String
) {
    /** The string the user sees, types or reads aloud: ABC123-1a2b3c… */
    val code: String get() = "$invitationId-$token"

    /**
     * What will be encoded into the QR image. Same shape as the plan's
     * https://<app-domain>/pair#inv=…&tok=… — scanning is handled in Phase 5,
     * for now the QR is a visual aid next to the human-readable code.
     */
    val qrPayload: String get() =
        "https://family-remote-support.app/pair#inv=$invitationId&tok=$token"
}

data class InvitationView(
    val id: String,
    val status: String,           // PENDING | PAIRED | CANCELLED
    val expiresAtMillis: Long,
    val claimedEmail: String?
)

data class RelationshipView(
    val id: String,
    val helperUid: String,
    val helperEmail: String,
    val recipientUid: String,
    val recipientEmail: String,
    val recipientDeviceModel: String,
    val status: String            // ACTIVE | REVOKED
)

private const val INVITATION_COLLECTION = "pairingInvitations"
private const val RELATIONSHIP_COLLECTION = "trustedDeviceRelationships"

/** Invitation validity window (must stay consistent with firestore.rules). */
private const val INVITE_TTL_MILLIS = 5 * 60 * 1000L

// No I, L, O, 0 or 1 — characters that are easy to misread over a phone call.
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private const val INVITE_ID_LENGTH = 6
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
 * "ABC123-1a2b…", "abc123 1a2b…", "ABC1231a2b…" are all the same code.
 */
fun parsePairingCode(raw: String): InviteCode {
    val cleaned = raw.replace(" ", "").replace("-", "").trim().uppercase()
    val expected = INVITE_ID_LENGTH + TOKEN_BYTES * 2
    if (cleaned.length != expected) {
        throw RedemptionException(
            "A pairing code has $expected characters (6 + 20). This one has ${cleaned.length}."
        )
    }
    return InviteCode(
        invitationId = cleaned.substring(0, INVITE_ID_LENGTH),
        token = cleaned.substring(INVITE_ID_LENGTH).lowercase()
    )
}

/** Create a fresh 5-minute invitation owned by [user] for their device. */
suspend fun createInvitation(
    user: FirebaseUser,
    deviceId: String,
    deviceModel: String
): InviteCode {
    val db = FirebaseFirestore.getInstance()
    repeat(3) {
        val invite = generateInviteCode()
        val ref = db.collection(INVITATION_COLLECTION).document(invite.invitationId)
        val exists = try {
            ref.get().await().exists()
        } catch (_: Exception) {
            false
        }
        if (exists) return@repeat // astronomically unlikely; try another id

        val doc = hashMapOf(
            "inviterUid" to user.uid,
            "inviterEmail" to (user.email ?: ""),
            "recipientDeviceId" to deviceId,
            "recipientDeviceModel" to deviceModel,
            "status" to "PENDING",
            "tokenHash" to sha256Hex(invite.token),
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + INVITE_TTL_MILLIS))
        )
        ref.set(doc).await()
        return invite
    }
    throw IllegalStateException("Could not generate a unique code — please try again.")
}

/** Live status of one invitation (waiting → paired / cancelled / expired). */
fun watchInvitation(
    invId: String,
    onChange: (InvitationView?) -> Unit
): ListenerRegistration =
    FirebaseFirestore.getInstance()
        .collection(INVITATION_COLLECTION)
        .document(invId)
        .addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) {
                onChange(null)
                return@addSnapshotListener
            }
            onChange(
                InvitationView(
                    id = snap.id,
                    status = snap.getString("status") ?: "PENDING",
                    expiresAtMillis = snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                    claimedEmail = snap.getString("claimedEmail")
                )
            )
        }

/** Inviter cancels their own pending invitation. */
suspend fun cancelInvitation(invId: String) {
    FirebaseFirestore.getInstance()
        .collection(INVITATION_COLLECTION)
        .document(invId)
        .update(
            mapOf(
                "status" to "CANCELLED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .await()
}

/**
 * Redeem a code as the helper. One atomic transaction:
 * verify PENDING + expiry + token hash → invitation PENDING → PAIRED →
 * trusted relationship created (or re-activated if the pair re-invited).
 */
suspend fun redeemCode(user: FirebaseUser, rawCode: String): RelationshipView {
    val code = parsePairingCode(rawCode)
    val tokenHash = sha256Hex(code.token)
    val db = FirebaseFirestore.getInstance()
    return db.runTransaction { tx ->
        val invRef = db.collection(INVITATION_COLLECTION).document(code.invitationId)
        val snap = tx.get(invRef)
        if (!snap.exists()) {
            throw RedemptionException("Code not found — check the first 6 characters.")
        }
        val inviterUid = snap.getString("inviterUid") ?: ""
        if (inviterUid == user.uid) {
            throw RedemptionException("This is your own code — the family member who needs help must read it to you.")
        }
        if (snap.getString("status") != "PENDING") {
            throw RedemptionException("This code was already used or cancelled. Ask for a new one.")
        }
        val expiresAt = snap.getTimestamp("expiresAt")
        if (expiresAt != null && expiresAt.toDate().before(Date())) {
            throw RedemptionException("This code has expired. Ask for a fresh 5-minute code.")
        }
        if (snap.getString("tokenHash") != tokenHash) {
            throw RedemptionException("Wrong code — compare every character with the other phone.")
        }

        tx.update(
            invRef,
            mapOf(
                "status" to "PAIRED",
                "claimedBy" to user.uid,
                "claimedEmail" to (user.email ?: ""),
                "claimedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val recipientEmail = snap.getString("inviterEmail") ?: ""
        val recipientDeviceId = snap.getString("recipientDeviceId") ?: ""
        val recipientDeviceModel = snap.getString("recipientDeviceModel") ?: ""
        val relRef = db.collection(RELATIONSHIP_COLLECTION)
            .document("${user.uid}_$inviterUid")
        val existing = tx.get(relRef)
        if (existing.exists()) {
            tx.update(
                relRef,
                mapOf(
                    "status" to "ACTIVE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            tx.set(
                relRef,
                mapOf(
                    "helperUid" to user.uid,
                    "helperEmail" to (user.email ?: ""),
                    "recipientUid" to inviterUid,
                    "recipientEmail" to recipientEmail,
                    "recipientDeviceId" to recipientDeviceId,
                    "recipientDeviceModel" to recipientDeviceModel,
                    "pairKey" to "${user.uid}_$inviterUid",
                    "status" to "ACTIVE",
                    "createdByInvitation" to code.invitationId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        RelationshipView(
            id = relRef.id,
            helperUid = user.uid,
            helperEmail = user.email ?: "",
            recipientUid = inviterUid,
            recipientEmail = recipientEmail,
            recipientDeviceModel = recipientDeviceModel,
            status = "ACTIVE"
        )
    }.await()
}

/** Live list of relationships where [user] is helper or recipient. */
fun watchRelationships(
    user: FirebaseUser,
    onUpdate: (List<RelationshipView>) -> Unit
): () -> Unit {
    val db = FirebaseFirestore.getInstance()
    var helperSide: List<RelationshipView> = emptyList()
    var recipientSide: List<RelationshipView> = emptyList()
    fun emit() = onUpdate(helperSide + recipientSide)

    val regHelper = db.collection(RELATIONSHIP_COLLECTION)
        .whereEqualTo("helperUid", user.uid)
        .addSnapshotListener { snap, _ ->
            helperSide = snap?.documents?.mapNotNull { it.toRelationshipView() } ?: emptyList()
            emit()
        }
    val regRecipient = db.collection(RELATIONSHIP_COLLECTION)
        .whereEqualTo("recipientUid", user.uid)
        .addSnapshotListener { snap, _ ->
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
    FirebaseFirestore.getInstance()
        .collection(RELATIONSHIP_COLLECTION)
        .document(relId)
        .update(
            mapOf(
                "status" to "REVOKED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        .await()
}

private fun QueryDocumentSnapshot.toRelationshipView(): RelationshipView? {
    val helperUid = getString("helperUid") ?: return null
    val recipientUid = getString("recipientUid") ?: return null
    return RelationshipView(
        id = id,
        helperUid = helperUid,
        helperEmail = getString("helperEmail") ?: "",
        recipientUid = recipientUid,
        recipientEmail = getString("recipientEmail") ?: "",
        recipientDeviceModel = getString("recipientDeviceModel") ?: "",
        status = getString("status") ?: "ACTIVE"
    )
}

/**
 * Render [content] as a QR bitmap (black on white). Returns null if the
 * payload cannot be encoded — the text code is always shown next to it.
 */
fun qrBitmap(content: String, size: Int = 480): Bitmap? = try {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    bitmap
} catch (_: Exception) {
    null
}
