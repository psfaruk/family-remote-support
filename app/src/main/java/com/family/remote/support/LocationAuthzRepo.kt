package com.family.remote.support

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Location master plan Phase 8 (v0.39.0) — the CLIENT side of the
 * `locationAuthorizations` entity: the authoritative consent documents the
 * 6-step wizard creates (one per selected recipient), the revocation writes
 * every stop path performs, and the honest offline queue that retriggers
 * both on the next user action. Design: docs/location/
 * LOCATION_DATA_AND_SECURITY_DESIGN.md §2.1/§4.1/§5.
 *
 * Doc id = `{recipientUid}_{ownerUid}_{deviceId}` — "owner" is always the
 * SHARER (this phone's signed-in user), mirroring the rules' binding so the
 * server re-verifies every field on every write. The owner alone may create,
 * update (field-fenced) and revoke; the recipient may READ their own grant
 * and nothing else — enforced server-side, this file merely attempts the
 * writes and reports failures honestly through the pending queue.
 *
 * D9 (Phase 8, recorded in LOCATION_IMPLEMENTATION_PROGRESS.md): the
 * design's `enabledFeatures` map is shipped FLATTENED as `liveEnabled` /
 * `historyEnabled` — Firestore rules can enforce a document-level key set
 * (`hasOnly`) but not a nested map's key set, and Phase 8's whole point is
 * server-verifiable field fencing. Collection is new in v0.39.0, so no
 * migration. `recipientKind` ('family' | 'guest') is likewise carried on the
 * doc so the rules can keep requiring a live pairing for guests after their
 * window lapses (family: the authorization IS the consent — design §4.4).
 *
 * §75 discipline: every Firestore write here happens inside a user action
 * (wizard confirm, sharing stop / sign-out teardown, Tracking-tab resume
 * flush). There is no timer, no listener and no background sweep anywhere
 * in this file.
 */
private const val LOCATION_AUTHZ_COLLECTION = "locationAuthorizations"

/** The ONCE scope's authorization grace: the single fix is written and the
 * live doc deleted immediately, but the grant itself stays valid this long
 * so a momentarily-late recipient read (or the flush queue) is not racing a
 * grant that expires before it landed. Generous by design, harmless in
 * effect — the live doc is already gone. */
private const val ONCE_GRACE_MILLIS = 15 * 60_000L

/** Doc id for one sharer→recipient grant (mirrors the rules' binding). */
fun locationAuthorizationDocId(recipientUid: String, ownerUid: String, deviceId: String): String =
    "${recipientUid}_${ownerUid}_$deviceId"

// ── Location master plan Phase 9 (v0.40.0) — the RECIPIENT-side read ─────

/**
 * The grant THIS account received from a sharer (one doc, read-only for
 * the recipient — the rules allow exactly own-grant gets). The UI gates
 * history/section-E rendering on these values; `revoked` and a past
 * `expiresAtMillis` both mean "nothing granted right now" — an honest
 * absence, never a stale card.
 */
data class ReceivedLocationAuthorization(
    val liveEnabled: Boolean,
    val historyEnabled: Boolean,
    val scope: String,
    val expiresAtMillis: Long,
    val retentionPolicy: String,
    val revoked: Boolean
) {
    /** Server-granted and still live at [nowMillis] (0 expiry = no bound). */
    fun activeAt(nowMillis: Long): Boolean =
        !revoked && (expiresAtMillis == 0L || expiresAtMillis > nowMillis)
}

/**
 * Phase 9 — read the authorization this account RECEIVED from the sharer
 * `{ownerUid}` on device `{ownerDeviceId}` (the detail/history screens'
 * one-shot gate read; user action only, §75). Returns null when no grant
 * doc exists (never shared / re-paired since) — an honest absence.
 */
suspend fun readReceivedLocationAuthorization(
    ownerUid: String,
    ownerDeviceId: String
): ReceivedLocationAuthorization? {
    val user = FirebaseAuth.getInstance().currentUser ?: return null
    val snap = Fb.db.collection(LOCATION_AUTHZ_COLLECTION)
        .document(locationAuthorizationDocId(user.uid, ownerUid, ownerDeviceId))
        .get()
        .awaitWithTimeout("reading the received location authorization")
    if (!snap.exists()) return null
    return ReceivedLocationAuthorization(
        liveEnabled = snap.getBoolean("liveEnabled") == true,
        historyEnabled = snap.getBoolean("historyEnabled") == true,
        scope = snap.getString("scope") ?: "UNTIL_STOP",
        expiresAtMillis = snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
        retentionPolicy = snap.getString("retentionPolicy") ?: "NONE",
        revoked = snap.contains("revokedAt")
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Phase 7 — the engine↔UI upload bridge (no Compose import on purpose: the
// ENGINE owns this vocabulary, the UI maps it 1:1 onto its own UploadState).
// ─────────────────────────────────────────────────────────────────────────

/** Last live-doc write attempt's outcome, engine-side (silent when OK). */
enum class LocationUploadStatus { OK, PENDING, FAILED, UNAVAILABLE }

/**
 * Process-local reporter for the own-device upload line (UX spec §8): the
 * service reports PENDING before each write, OK/FAILED after it, and
 * UNAVAILABLE when no provider could even be registered. Compose observes
 * via [listen] + a local mutableStateOf — the engine stays Compose-free.
 */
object LocationUploadReporter {
    @Volatile
    private var status = LocationUploadStatus.OK

    private val listeners = CopyOnWriteArrayList<(LocationUploadStatus) -> Unit>()

    /** The service's only write path; also replays to the new listener. */
    fun report(s: LocationUploadStatus) {
        status = s
        listeners.forEach { it(s) }
    }

    fun current(): LocationUploadStatus = status

    /** Register + immediate replay (so a freshly composed card is truthful). */
    fun listen(listener: (LocationUploadStatus) -> Unit) {
        listeners.add(listener)
        listener(status)
    }

    fun unlisten(listener: (LocationUploadStatus) -> Unit) {
        listeners.remove(listener)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Grant / revoke / flush
// ─────────────────────────────────────────────────────────────────────────

/** The fields an owner may change after creation (the rules' update fence). */
private val AUTHZ_UPDATE_FIELDS = listOf(
    "liveEnabled", "historyEnabled", "scope", "expiresAt", "retentionPolicy", "updatedAt", "revokedAt"
)

/**
 * The wizard confirmed: record the recipient set locally and create/refresh
 * one authorization doc per recipient. Runs on a throwaway scope (the wizard
 * confirm is a UI event); any failure lands in the honest pending queue and
 * is retried on the next user action — the wizard never reports success of
 * a write it did not verify.
 *
 * @param recipients     uid→kind pairs of the selected recipients (kind:
 *                       'family' | 'guest' — guests keep needing a live
 *                       pairing server-side).
 * @param scopeKind      ScopeKind name (ONCE / TEMPORARY / UNTIL_STOP / WITH_HISTORY).
 * @param tempMillis     the TEMPORARY expiry (0 when the scope is not temporary).
 * @param retentionKind  RetentionKind name (NONE unless WITH_HISTORY was picked).
 */
fun grantLocationAuthorizations(
    context: Context,
    recipients: List<Pair<String, String>>,
    scopeKind: String,
    tempMillis: Long,
    retentionKind: String
) {
    val historyEnabled = scopeKind == LocationSharingPrefs.SCOPE_WITH_HISTORY &&
        retentionKind != LocationSharingPrefs.RETENTION_NONE
    val expiresAt = when (scopeKind) {
        LocationSharingPrefs.SCOPE_TEMPORARY -> tempMillis
        LocationSharingPrefs.SCOPE_ONCE -> System.currentTimeMillis() + ONCE_GRACE_MILLIS
        else -> 0L
    }
    LocationSharingPrefs.setAuthzRecipients(context, recipients)
    LocationSharingPrefs.setPendingGrant(context, false) // superseded by this attempt
    val deviceId = stableDeviceId(context.applicationContext)
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        var failed = false
        for ((recipientUid, kind) in recipients) {
            try {
                upsertLocationAuthorization(
                    deviceId, recipientUid, kind, scopeKind, expiresAt, retentionKind, historyEnabled
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
        }
        if (failed) LocationSharingPrefs.setPendingGrant(context, true)
    }
}

/**
 * Every stop path (owner toggle, scope auto-stop, sign-out teardown) calls
 * this: revoke each stored grant by writing `revokedAt` (field-fenced,
 * owner-only — the rules freeze the doc afterwards). Best-effort with the
 * same honest queue; the server-side `expiresAt` (TEMPORARY/ONCE) and the
 * already-deleted live doc keep failing revocations from over-granting.
 */
fun revokeLocationAuthorizationsOnStop(context: Context) {
    val recipients = LocationSharingPrefs.authzRecipients(context)
    if (recipients.isEmpty()) return
    val deviceId = stableDeviceId(context.applicationContext)
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        val stillToRevoke = mutableListOf<String>()
        for ((recipientUid, _) in recipients) {
            try {
                revokeLocationAuthorization(recipientUid, deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                stillToRevoke.add(recipientUid)
            }
        }
        if (stillToRevoke.isNotEmpty()) {
            LocationSharingPrefs.setPendingRevoke(context, stillToRevoke)
        } else {
            LocationSharingPrefs.setPendingRevoke(context, emptyList())
        }
    }
}

/**
 * The user opened the Tracking tab (a user action, §75-safe): retry whatever
 * the queue still holds. An expired grant attempt is dropped as moot — the
 * live doc is long gone and the rules deny reads past `expiresAt` anyway.
 */
fun flushPendingLocationAuthz(context: Context) {
    if (inFlightFlush) return
    val pendingGrant = LocationSharingPrefs.pendingGrant(context)
    val pendingRevoke = LocationSharingPrefs.pendingRevoke(context)
    if (!pendingGrant && pendingRevoke.isEmpty()) return
    if (FirebaseAuth.getInstance().currentUser == null) return
    val deviceId = stableDeviceId(context.applicationContext)
    inFlightFlush = true
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            if (pendingGrant) {
                val recipients = LocationSharingPrefs.authzRecipients(context)
                val scope = LocationSharingPrefs.scopeKind(context)
                var expiresAt = LocationSharingPrefs.scopeExpiresAt(context)
                if (scope == LocationSharingPrefs.SCOPE_ONCE) {
                    expiresAt = 0L // grace long gone — recreate as plain grant
                }
                val expired = expiresAt != 0L &&
                    System.currentTimeMillis() >= expiresAt
                if (!expired && recipients.isNotEmpty()) {
                    val retention = LocationSharingPrefs.retentionKind(context)
                    val historyEnabled = scope == LocationSharingPrefs.SCOPE_WITH_HISTORY &&
                        retention != LocationSharingPrefs.RETENTION_NONE
                    var failed = false
                    for ((recipientUid, kind) in recipients) {
                        try {
                            upsertLocationAuthorization(
                                deviceId, recipientUid, kind, scope, expiresAt, retention, historyEnabled
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                    if (!failed) LocationSharingPrefs.setPendingGrant(context, false)
                } else {
                    // Moot (expired or recipientless): drop the stale attempt.
                    LocationSharingPrefs.setPendingGrant(context, false)
                }
            }
            if (pendingRevoke.isNotEmpty()) {
                val stillToRevoke = mutableListOf<String>()
                for (recipientUid in pendingRevoke) {
                    try {
                        revokeLocationAuthorization(recipientUid, deviceId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        stillToRevoke.add(recipientUid)
                    }
                }
                LocationSharingPrefs.setPendingRevoke(context, stillToRevoke)
            }
        } finally {
            inFlightFlush = false
        }
    }
}

@Volatile
private var inFlightFlush = false

/**
 * One recipient's grant, brought to the target state through the rules'
 * three legal paths: create (doc absent), fenced update (doc alive), or
 * delete+recreate (a previous grant was revoked — revocation is terminal,
 * so re-granting starts from a clean document). The one read per call is
 * what makes the path choice honest instead of guess-and-catch.
 */
private suspend fun upsertLocationAuthorization(
    deviceId: String,
    recipientUid: String,
    recipientKind: String,
    scopeKind: String,
    expiresAtMillis: Long,
    retentionKind: String,
    historyEnabled: Boolean
) {
    val user = FirebaseAuth.getInstance().currentUser
        ?: return // signed out mid-flight: the queue retries on next action
    val ownerUid = user.uid
    val docRef = Fb.db.collection(LOCATION_AUTHZ_COLLECTION)
        .document(locationAuthorizationDocId(recipientUid, ownerUid, deviceId))

    val snapshot = docRef.get().awaitWithTimeout("reading the existing location authorization")
    if (!snapshot.exists() || snapshot.contains("revokedAt")) {
        if (snapshot.exists()) {
            // Terminal revoke: the only legal re-grant is delete + create.
            docRef.delete().awaitWithTimeout("clearing the revoked location authorization")
        }
        val createFields = mutableMapOf<String, Any?>(
            "ownerUserId" to ownerUid,
            "recipientUserId" to recipientUid,
            "deviceId" to deviceId,
            "recipientKind" to recipientKind,
            "liveEnabled" to true,
            "historyEnabled" to historyEnabled,
            "scope" to scopeKind,
            "retentionPolicy" to retentionKind,
            "authorizationVersion" to 1,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (expiresAtMillis > 0L) createFields["expiresAt"] = Date(expiresAtMillis)
        docRef.set(createFields).awaitWithTimeout("creating the location authorization")
    } else {
        // Alive: field-fenced refresh (immutable identity fields untouched).
        val updateFields = mutableMapOf<String, Any?>(
            "liveEnabled" to true,
            "historyEnabled" to historyEnabled,
            "scope" to scopeKind,
            "retentionPolicy" to retentionKind,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        updateFields["expiresAt"] =
            if (expiresAtMillis > 0L) Date(expiresAtMillis) else FieldValue.delete()
        docRef.set(updateFields, SetOptions.mergeFields(AUTHZ_UPDATE_FIELDS.minus("revokedAt")))
            .awaitWithTimeout("updating the location authorization")
    }
}

/**
 * Mark one recipient's grant revoked (owner-only, field-fenced). Returns
 * normally when there is nothing left to revoke (absent or already-revoked
 * doc) so the queue can stop retrying a finished job.
 */
private suspend fun revokeLocationAuthorization(recipientUid: String, deviceId: String) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val ownerUid = user.uid
    val docRef = Fb.db.collection(LOCATION_AUTHZ_COLLECTION)
        .document(locationAuthorizationDocId(recipientUid, ownerUid, deviceId))
    val snapshot = docRef.get().awaitWithTimeout("reading the location authorization before revoke")
    if (!snapshot.exists() || snapshot.contains("revokedAt")) return
    docRef.set(
        mapOf(
            "revokedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.mergeFields(listOf("revokedAt", "updatedAt"))
    ).awaitWithTimeout("revoking the location authorization")
}
