package com.family.remote.support

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase 22/M5 (v0.33.0) — live location ENGINE: the Firestore repository,
 * the sharing preferences and the service start/stop plumbing for
 * consent-based family location sharing. Design:
 * docs/LIVE_LOCATION_ARCHITECTURE.md (§2 stack, §3 flow, §4 data model).
 *
 * The data model deliberately clones devicePresence (PresenceRepo.kt): ONE
 * mutable document per device — liveLocations with id {uid}_{deviceId} —
 * upserted in place while sharing runs and deleted on stop. NO history, no
 * append-only feed (master plan PART 6): retention is the last position
 * only, so the doc count is bounded by the number of sharing devices and
 * the §7 free-tier budget holds by construction, not by luck.
 *
 * This file owns no location-provider code. The acceptance gates, the
 * stationary back-off and the Play-services fallback live in
 * [LocationShareService], next to the fixes they judge; here everything
 * Firestore-facing goes through Fb.db and the shared awaitWithTimeout
 * helper (PairingRepo.kt), so an offline phone can never hang a caller
 * forever.
 */

private const val LIVE_LOCATION_COLLECTION = "liveLocations"

/**
 * One device's shared position, as the Tracking tab renders it. This is the
 * READER's projection of the single liveLocations document — the writer side
 * is [LocationShareService]. [updatedAtMillis] comes from the server
 * timestamp of the last accepted write (0 when the document predates that
 * field), and the reader — never the writer — turns it into the LIVE /
 * STALE / OFFLINE freshness states of §3, so clock skew stays a display
 * concern instead of a data one.
 */
data class LiveLocationView(
    val uid: String,
    val deviceId: String,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Int,
    val sharingEnabled: Boolean,
    val updatedAtMillis: Long,
    /** Phase 10 — the OS-provided speed of the last accepted fix, when
     *  the writer had one (optional in the doc AND in the rules' hasOnly;
     *  null for docs written without a sensor speed). Render-time input
     *  for MovementClassifier.classifyLive — never stored classified. */
    val speedMetersPerSecond: Double? = null
)

/** Document id for one device's live location (mirrors [presenceDocId]). */
fun liveLocationDocId(uid: String, deviceId: String): String = "${uid}_$deviceId"

/**
 * Upsert this device's live position (merge — created on the first accepted
 * fix, overwritten in place afterwards; §4 "no history"). The service is the
 * only writer and only runs while sharing is on, so sharingEnabled is pinned
 * true here: the STOP path deletes the document instead of flipping the
 * flag, so a counterpart can never read a live-looking document that was
 * actually abandoned (§3 owner-stop flow).
 *
 * Field set is exactly the §4 contract (the published rules use hasOnly on
 * it): uid, deviceId, lat, lng, accuracyMeters, sharingEnabled, updatedAt,
 * appVersionCode. updatedAt is a server timestamp so freshness never depends
 * on the sender's clock.
 */
suspend fun writeLocationFix(
    uid: String,
    deviceId: String,
    lat: Double,
    lng: Double,
    accuracyMeters: Int
) {
    Fb.db.collection(LIVE_LOCATION_COLLECTION)
        .document(liveLocationDocId(uid, deviceId))
        .set(
            mapOf(
                "uid" to uid,
                "deviceId" to deviceId,
                "lat" to lat,
                "lng" to lng,
                "accuracyMeters" to accuracyMeters,
                "sharingEnabled" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
                "appVersionCode" to BuildConfig.VERSION_CODE
            ),
            SetOptions.merge()
        )
        .awaitWithTimeout("updating this device's live location")
}

/**
 * Watch one device's shared position (e.g. the counterpart's). Emits null
 * while the document is absent — never shared, stopped, or cleaned up —
 * which the Tracking tab must render as an honest empty state rather than
 * aging a stale coordinate into a live marker (§5). Single-document
 * listener, never a query: counterpart reads scale with the owner's writes
 * and stay inside the §7 read budget.
 */
fun watchLiveLocation(
    uid: String,
    deviceId: String,
    onChange: (LiveLocationView?) -> Unit,
    onError: (Exception) -> Unit = {}
): () -> Unit {
    val registration = Fb.db.collection(LIVE_LOCATION_COLLECTION)
        .document(liveLocationDocId(uid, deviceId))
        .addSnapshotListener { snap: DocumentSnapshot?, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                onChange(null)
                return@addSnapshotListener
            }
            onChange(
                LiveLocationView(
                    uid = snap.getString("uid") ?: uid,
                    deviceId = snap.getString("deviceId") ?: deviceId,
                    lat = snap.getDouble("lat") ?: 0.0,
                    lng = snap.getDouble("lng") ?: 0.0,
                    accuracyMeters = (snap.getLong("accuracyMeters") ?: 0L).toInt(),
                    sharingEnabled = snap.getBoolean("sharingEnabled") ?: false,
                    updatedAtMillis = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
                    speedMetersPerSecond = snap.getDouble("speedMetersPerSecond")
                )
            )
        }
    return { registration.remove() }
}

/**
 * Remove this device's live-location document (stop, unpair, sign-out
 * cleanup — §4 "delete: owner"). The published rules allow exactly the
 * signed-in owner's own delete. Failures propagate to the caller: every
 * call site in this milestone is best-effort and catches, mirroring
 * deleteOwnPresenceAndToken's cleanup posture.
 */
suspend fun deleteOwnLocation(uid: String, deviceId: String) {
    Fb.db.collection(LIVE_LOCATION_COLLECTION)
        .document(liveLocationDocId(uid, deviceId))
        .delete()
        .awaitWithTimeout("removing this device's live location")
}

// ── Location master plan Phase 7 (v0.39.0) — journey history writes ──────

private const val LOCATION_HISTORY_PARENT = "users"
private const val LOCATION_HISTORY_COLLECTION = "locationHistory"

/**
 * Phase 7 — append ONE retained journey point under
 * `users/{ownerUid}/locationHistory/{deviceId}_{epochMillis}` (design §2.2,
 * owner-scoped path so the recipient-side rules evaluate deterministically
 * per query). Called by the engine ONLY when the local consent record says
 * WITH_HISTORY + retention ≠ NONE and the fix already passed every §3 gate
 * plus the 50 m history throttle (history is coarser than live by design —
 * the stationary back-off compounds it further).
 *
 * Speed/bearing/altitude are stored only when the OS provided them
 * (hasSpeed()/hasBearing()/hasAltitude()) — never computed-then-presented-
 * as-measured (design §3); the classifier label stays client-side UI state.
 * `receivedAt` is the server timestamp and `retentionExpiresAt` is
 * recordedAt + policy, both enforced by the Phase 8 rules on every read.
 * Append-only: records are never updated; deletion is owner-only.
 */
suspend fun writeHistoryRecord(
    uid: String,
    deviceId: String,
    lat: Double,
    lng: Double,
    accuracyMeters: Int,
    speedMetersPerSecond: Double?,
    bearingDegrees: Double?,
    altitudeMeters: Double?,
    provider: String,
    recordedAtMillis: Long,
    retentionMillis: Long
) {
    val fields = mutableMapOf<String, Any?>(
        "ownerUserId" to uid,
        "deviceId" to deviceId,
        "lat" to lat,
        "lng" to lng,
        "accuracyMeters" to accuracyMeters,
        "recordedAt" to Date(recordedAtMillis),
        "receivedAt" to FieldValue.serverTimestamp(),
        "provider" to provider,
        "retentionExpiresAt" to Date(recordedAtMillis + retentionMillis),
        "authorizationVersion" to 1,
        "appVersionCode" to BuildConfig.VERSION_CODE
    )
    if (speedMetersPerSecond != null) fields["speedMetersPerSecond"] = speedMetersPerSecond
    if (bearingDegrees != null) fields["bearingDegrees"] = bearingDegrees
    if (altitudeMeters != null) fields["altitudeMeters"] = altitudeMeters
    Fb.db.collection(LOCATION_HISTORY_PARENT)
        .document(uid)
        .collection(LOCATION_HISTORY_COLLECTION)
        .document("${deviceId}_$recordedAtMillis")
        .set(fields)
        .awaitWithTimeout("appending this device's journey history")
}

/**
 * The owner's sharing toggle and cadence choice, kept in plain
 * SharedPreferences: a local consent record needs no Firestore round-trip.
 * The toggle is the CONSENT record — sharing runs only while it is on, and
 * the service re-checks it on every start command, so a stop racing a late
 * start can never resurrect sharing behind the owner's back.
 */
object LocationSharingPrefs {

    /** §6 cadence choices offered in Settings; also the back-off ceiling. */
    const val INTERVAL_30S = 30_000L
    const val INTERVAL_1M = 60_000L
    const val INTERVAL_5M = 300_000L

    // ── Phase 7/8 (v0.39.0) — the scope vocabulary as plain strings so this
    // repo file stays free of ui-package imports (the wizard's enums and the
    // engine agree on these exact names).
    const val SCOPE_ONCE = "ONCE"
    const val SCOPE_TEMPORARY = "TEMPORARY"
    const val SCOPE_UNTIL_STOP = "UNTIL_STOP"
    const val SCOPE_WITH_HISTORY = "WITH_HISTORY"

    const val RETENTION_NONE = "NONE"
    const val RETENTION_H24 = "H24"
    const val RETENTION_D7 = "D7"
    const val RETENTION_D30 = "D30"

    /** Retention window in milliseconds (0 when history is not kept). */
    fun retentionMillis(kind: String): Long = when (kind) {
        RETENTION_H24 -> 24 * 3_600_000L
        RETENTION_D7 -> 7 * 24 * 3_600_000L
        RETENTION_D30 -> 30 * 24 * 3_600_000L
        else -> 0L
    }

    private const val PREFS_NAME = "location_sharing"
    private const val KEY_SHARING_ENABLED = "sharing_enabled"
    private const val KEY_INTERVAL_MILLIS = "interval_millis"

    fun isSharingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHARING_ENABLED, false)

    fun setSharingEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARING_ENABLED, on).apply()
    }

    fun intervalMillis(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL_MILLIS, INTERVAL_1M)

    /**
     * The Settings surface offers exactly the three §6 cadences; anything
     * else a future caller passes is snapped to the nearest allowed one, so
     * the client-side write cadence stays bounded no matter what (rules
     * cannot rate-limit — Agent 2-b — the client must police itself).
     */
    fun setIntervalMillis(context: Context, millis: Long) {
        val coerced = when {
            millis <= (INTERVAL_30S + INTERVAL_1M) / 2 -> INTERVAL_30S
            millis <= (INTERVAL_1M + INTERVAL_5M) / 2 -> INTERVAL_1M
            else -> INTERVAL_5M
        }
        prefs(context).edit().putLong(KEY_INTERVAL_MILLIS, coerced).apply()
    }

    // ── Phase 4 (v0.38.0) — the setup wizard's LOCAL consent record ────────
    // Scope/retention keys written by SharingSetupSheet's confirm step.
    // These are a LOCAL record only: the authoritative authorization
    // document (locationAuthorizations) is created in Phase 8, and these
    // prefs NEVER gate the engine in v0.38.0 — the service still runs on
    // KEY_SHARING_ENABLED alone. Values are the ScopeKind/RetentionKind
    // enum names from ui.tracking, stored as plain strings so this repo
    // file stays free of ui-package imports (Phase 8 parses them).

    private const val KEY_SCOPE_KIND = "scope_kind"
    private const val KEY_SCOPE_EXPIRES_AT = "scope_expires_at_millis"
    private const val KEY_RETENTION_KIND = "retention_kind"

    /** The last chosen sharing scope: "ONCE" / "TEMPORARY" / "UNTIL_STOP" / "WITH_HISTORY" (default UNTIL_STOP, the wizard's default). */
    fun scopeKind(context: Context): String =
        prefs(context).getString(KEY_SCOPE_KIND, "UNTIL_STOP") ?: "UNTIL_STOP"

    fun setScopeKind(context: Context, kind: String) {
        prefs(context).edit().putString(KEY_SCOPE_KIND, kind).apply()
    }

    /** 0 = no expiry (the scope is not temporary). Local consent record only — Phase 8 enforces the real expiry. */
    fun scopeExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_SCOPE_EXPIRES_AT, 0L)

    fun setScopeExpiresAt(context: Context, expiresAtMillis: Long) {
        prefs(context).edit().putLong(KEY_SCOPE_EXPIRES_AT, expiresAtMillis).apply()
    }

    /** The last chosen journey-history retention: "NONE" / "H24" / "D7" / "D30" (default NONE). */
    fun retentionKind(context: Context): String =
        prefs(context).getString(KEY_RETENTION_KIND, "NONE") ?: "NONE"

    fun setRetentionKind(context: Context, kind: String) {
        prefs(context).edit().putString(KEY_RETENTION_KIND, kind).apply()
    }

    // ── Phase 8 (v0.39.0) — the authorization queue: the LAST wizard
    // selection (uid:kind pairs) + the honest offline flags. Grants are
    // (re)written by user actions only; revocations queue when a stop
    // happens offline and retry on the next user action.

    private const val KEY_AUTHZ_RECIPIENTS = "authz_recipients"
    private const val KEY_PENDING_GRANT = "authz_pending_grant"
    private const val KEY_PENDING_REVOKE = "authz_pending_revoke"

    /** The last confirmed recipients as uid:kind pairs (kind family|guest). */
    fun authzRecipients(context: Context): List<Pair<String, String>> {
        val raw = prefs(context).getString(KEY_AUTHZ_RECIPIENTS, "") ?: ""
        return raw.split(',')
            .filter { it.contains(':') }
            .mapNotNull { entry ->
                val uid = entry.substringBefore(':')
                val kind = entry.substringAfter(':', "family")
                if (uid.isNotEmpty()) uid to kind else null
            }
    }

    fun setAuthzRecipients(context: Context, recipients: List<Pair<String, String>>) {
        val raw = recipients.joinToString(",") { (uid, kind) -> "$uid:$kind" }
        prefs(context).edit().putString(KEY_AUTHZ_RECIPIENTS, raw).apply()
    }

    fun pendingGrant(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_GRANT, false)

    fun setPendingGrant(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_PENDING_GRANT, pending).apply()
    }

    fun pendingRevoke(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_PENDING_REVOKE, "") ?: ""
        return raw.split(',').filter { it.isNotEmpty() }
    }

    fun setPendingRevoke(context: Context, uids: List<String>) {
        prefs(context).edit().putString(KEY_PENDING_REVOKE, uids.joinToString(",")).apply()
    }

    // ── Phase 9 (v0.40.0) — the offline user-initiated deletion queue.
    // A delete that could not finish offline stays queued here and is
    // retried on the next user action (same flush hook as the authz
    // queue). "Today" scope carries its own day-start bound so a queued
    // run flushed after midnight deletes the day the OWNER asked for,
    // never the shifted one.

    private const val KEY_PENDING_DELETE_ALL = "pending_delete_all"
    private const val KEY_PENDING_DELETE_DAY_START = "pending_delete_day_start"

    fun pendingDeleteAll(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_DELETE_ALL, false)

    fun setPendingDeleteAll(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_PENDING_DELETE_ALL, pending).apply()
    }

    /** The queued "delete today" run's day-start bound (0 = nothing queued). */
    fun pendingDeleteDayStart(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_DELETE_DAY_START, 0L)

    fun setPendingDeleteDayStart(context: Context, dayStartMillis: Long) {
        prefs(context).edit().putLong(KEY_PENDING_DELETE_DAY_START, dayStartMillis).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * v0.37.0 (dedupe wave) — ONE "fine || coarse" check (was 3 copies:
 * TrackingTab's private helper, SettingsTab's inline sharing-toggle gate,
 * and the service-side re-check). Either while-in-use grant is enough to
 * share a position — §5 deliberately counts an "Approximate" (coarse
 * only) grant as a start.
 */
fun hasAnyLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Owner turned sharing ON. Consent is recorded FIRST (a crash between the
 * two lines still leaves an honest preference), then the location foreground
 * service comes up. Callers must already hold while-in-use FINE or COARSE
 * permission — §5 puts the runtime prompt before this call — and the service
 * re-checks and stops itself otherwise, so a missing grant can never turn
 * into a crash or silent running.
 */
fun startLocationSharing(context: Context) {
    LocationSharingPrefs.setSharingEnabled(context, true)
    ContextCompat.startForegroundService(
        context,
        Intent(context, LocationShareService::class.java)
    )
}

/**
 * Owner turned sharing OFF. The preference flips BEFORE the service stops so
 * a late start command can never resurrect sharing; the service's own stop
 * path then deletes the liveLocations document, and — Phase 8 — every
 * stored authorization is marked revoked (best-effort; failures queue and
 * retry on the next user action while the server-side expiresAt keeps
 * expiring grants on its own). When the service is already dead (process
 * killed, phone rebooted — §5 deliberately ships no auto-restart) no
 * onDestroy will ever fire, so this stop path clears the document itself,
 * exactly once: the running check happens before stopService, when it is
 * still truthful.
 */
fun stopLocationSharing(context: Context) {
    LocationSharingPrefs.setSharingEnabled(context, false)
    // Phase 8 — revoke the authorizations from every stop path (toggle,
    // scope auto-stop, sign-out teardown — they all funnel through here).
    revokeLocationAuthorizationsOnStop(context)
    val serviceWasRunning = isLocationSharingRunning(context)
    context.stopService(Intent(context, LocationShareService::class.java))
    if (serviceWasRunning) return
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val uid = user.uid
    val deviceId = stableDeviceId(context.applicationContext)
    // Throwaway scope on purpose, independent of any service scope: called
    // from the UI, the process stays alive long enough for the one delete.
    // Best-effort (§3): if the process dies first, the counterpart's
    // freshness math degrades the marker to STALE and then OFFLINE — never
    // "live".
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            deleteOwnLocation(uid, deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline during stop: the document keeps its last state and
            // freshness handles the rest.
        }
    }
}

/**
 * Whether the sharing service is alive right now. Implemented as the
 * process-local volatile flag set in LocationShareService.onCreate and
 * cleared in onDestroy — chosen over ActivityManager.getRunningServices,
 * which is deprecated since API 26 and was never reliable cross-app. A
 * killed process resets the flag to false, which is the honest answer: a
 * dead process is not sharing. The persisted preference keeps the owner's
 * last choice, so the Settings UI can reconcile the two on next open. The
 * context parameter keeps the call symmetric with the two toggles above.
 */
fun isLocationSharingRunning(context: Context): Boolean =
    LocationShareService.isRunning

// ── Location master plan Phase 9 (v0.40.0) — history READS, DELETES and
// the owner-side retention cleanup (design §4.3/§6, UX spec §9/§10).
// Everything here is one-shot and user-action-driven: no listeners, no
// timers, no background sweeps (§75 posture — "history is always one-shot
// get/list", design §8). ─────────────────────────────────────────────

/** The clock-skew buffer every history list query carries (design §4.3 —
 * the rule allows retentionExpiresAt > request.time − 5 min, so a client
 * filtering at now + 5 min keeps a ~10-minute safe margin for a slow
 * client clock; probe H-series locked this shape). */
private const val HISTORY_SKEW_MILLIS = 5L * 60_000L

/** One day-query's read ceiling (dense day ≈ a few hundred points at the
 * 50 m throttle; 1000 is the honest cap beyond which the route renders
 * from the first 1000 ascending points — recorded in the progress doc). */
private const val HISTORY_DAY_QUERY_LIMIT = 1000L

/** The opportunistic cleanup's per-run delete ceiling (design §6.2's
 * bounded batch — repeats on the next user action until exhausted). */
private const val HISTORY_CLEANUP_BATCH = 200

/** Local write-log capacity — D30 at ~100 retained points/day stays well
 * inside 4000; the oldest entries drop first (their records are either
 * deleted by cleanup or invisible via query-time expiry by then). */
private const val HISTORY_LOG_MAX_ENTRIES = 4000

/**
 * One retained journey point as a reader sees it (repo projection of
 * `users/{ownerUid}/locationHistory/{recordId}`; the UI maps it onto its
 * render-time `HistoryPoint`). docId travels along because deletion is
 * per-record and append-only records are never updated.
 */
data class LocationHistoryRecord(
    val docId: String,
    val lat: Double,
    val lng: Double,
    val recordedAtMillis: Long,
    val retentionExpiresAtMillis: Long,
    val accuracyMeters: Int,
    val speedMetersPerSecond: Double?
)

/**
 * Phase 9 — read one day's retained records under
 * `users/{ownerUid}/locationHistory` (ownerUid = the SHARER; the caller
 * may be the owner reading their own data or a recipient the rules
 * authorize). The query shape is exactly the rules' contract: deviceId
 * equality (so the per-record authorization get() binds deterministically)
 * plus the mandatory retentionExpiresAt lower bound plus the recordedAt
 * day range — served by the composite index
 * (deviceId, retentionExpiresAt, recordedAt). Ascending composite order
 * is re-sorted by recordedAt in memory. Expired records can never be
 * returned: the retention filter is IN the query (rules are not filters).
 */
suspend fun readHistoryDay(
    ownerUid: String,
    deviceId: String,
    dayStartMillis: Long,
    dayEndMillis: Long
): List<LocationHistoryRecord> {
    val skewFloor = Date(System.currentTimeMillis() + HISTORY_SKEW_MILLIS)
    val snap = Fb.db.collection(LOCATION_HISTORY_PARENT)
        .document(ownerUid)
        .collection(LOCATION_HISTORY_COLLECTION)
        .whereEqualTo("deviceId", deviceId)
        .whereGreaterThan("retentionExpiresAt", skewFloor)
        .whereGreaterThanOrEqualTo("recordedAt", Date(dayStartMillis))
        .whereLessThan("recordedAt", Date(dayEndMillis))
        .orderBy("retentionExpiresAt")
        .orderBy("recordedAt")
        .limit(HISTORY_DAY_QUERY_LIMIT)
        .get()
        .awaitWithTimeout("reading this day's journey records")
    return snap.documents.mapNotNull { doc ->
        val recordedAt = doc.getTimestamp("recordedAt")?.toDate()?.time ?: return@mapNotNull null
        LocationHistoryRecord(
            docId = doc.id,
            lat = doc.getDouble("lat") ?: 0.0,
            lng = doc.getDouble("lng") ?: 0.0,
            recordedAtMillis = recordedAt,
            retentionExpiresAtMillis = doc.getTimestamp("retentionExpiresAt")?.toDate()?.time
                ?: recordedAt,
            accuracyMeters = (doc.getLong("accuracyMeters") ?: 0L).toInt(),
            speedMetersPerSecond = doc.getDouble("speedMetersPerSecond")
        )
    }.sortedBy { it.recordedAtMillis }
}

/**
 * Blind per-record deletes (owner-only path per the rules; no read is
 * needed — the docIds come from a query the caller already ran or from
 * the local write log). Batched at Firestore's 500-op WriteBatch ceiling
 * minus headroom. Failures propagate: callers queue honestly.
 */
suspend fun deleteHistoryRecords(ownerUid: String, docIds: List<String>) {
    docIds.chunked(200).forEach { chunk ->
        if (chunk.isEmpty()) return@forEach
        val batch = Fb.db.batch()
        chunk.forEach { id ->
            batch.delete(
                Fb.db.collection(LOCATION_HISTORY_PARENT)
                    .document(ownerUid)
                    .collection(LOCATION_HISTORY_COLLECTION)
                    .document(id)
            )
        }
        batch.commit().awaitWithTimeout("deleting journey history records")
    }
}

/**
 * Enumerate the caller's OWN unexpired records in bounded ascending
 * batches — the OWNER path only (the rule's owner branch is path-derived,
 * so no deviceId filter is needed and the single-field automatic index
 * serves this without any composite). Used by delete-all and its
 * verification re-query; the hard total cap keeps a pathological loop
 * from reading forever.
 */
private suspend fun enumerateUnexpiredOwnHistory(
    ownerUid: String,
    maxTotal: Int = 20_000
): List<LocationHistoryRecord> {
    val out = mutableListOf<LocationHistoryRecord>()
    var after: DocumentSnapshot? = null
    while (out.size < maxTotal) {
        var query = Fb.db.collection(LOCATION_HISTORY_PARENT)
            .document(ownerUid)
            .collection(LOCATION_HISTORY_COLLECTION)
            .whereGreaterThan(
                "retentionExpiresAt",
                Date(System.currentTimeMillis() + HISTORY_SKEW_MILLIS)
            )
            .orderBy("retentionExpiresAt")
            .limit(HISTORY_CLEANUP_BATCH.toLong())
        if (after != null) query = query.startAfter(after)
        val snap = query.get().awaitWithTimeout("enumerating retained journey records")
        if (snap.isEmpty) break
        snap.documents.forEach { doc ->
            val recordedAt = doc.getTimestamp("recordedAt")?.toDate()?.time ?: 0L
            out.add(
                LocationHistoryRecord(
                    docId = doc.id,
                    lat = doc.getDouble("lat") ?: 0.0,
                    lng = doc.getDouble("lng") ?: 0.0,
                    recordedAtMillis = recordedAt,
                    retentionExpiresAtMillis = doc.getTimestamp("retentionExpiresAt")?.toDate()?.time
                        ?: recordedAt,
                    accuracyMeters = (doc.getLong("accuracyMeters") ?: 0L).toInt(),
                    speedMetersPerSecond = doc.getDouble("speedMetersPerSecond")
                )
            )
        }
        if (snap.documents.size < HISTORY_CLEANUP_BATCH) break
        after = snap.documents.last()
    }
    return out
}

/**
 * Phase 9 — "Delete my history: today" (UX spec §10). This device's
 * records for the local day starting at [dayStartMillis] are blind-deleted
 * from the LOCAL write log (the log is this device's own write record —
 * in v1 a user's history has exactly one writer, this device). Returns
 * the number of records deleted so the UI can report honestly.
 */
suspend fun deleteOwnHistoryForDay(context: Context, dayStartMillis: Long): Int {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    val entries = LocationHistoryLog.entries(context)
        .filter { it.recordedAtMillis >= dayStartMillis }
    if (entries.isNotEmpty()) {
        deleteHistoryRecords(user.uid, entries.map { it.docId })
        LocationHistoryLog.removeAll(context, entries.map { it.docId }.toSet())
    }
    return entries.size
}

/**
 * Phase 9 — "Delete my history: everything" (UX spec §10 + design §6.3):
 * enumerate every unexpired own record (bounded batches), delete them,
 * blind-delete every LOCAL write-log id (expired ones included — the log
 * knows ids the rules will never return), then VERIFY by re-running the
 * enumeration, which must come back empty. Returns the deleted count.
 * Offline failures propagate to the caller for the honest queue.
 */
suspend fun deleteAllOwnHistory(context: Context): Int {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    var deleted = 0
    while (true) {
        val batch = enumerateUnexpiredOwnHistory(user.uid, maxTotal = HISTORY_CLEANUP_BATCH)
        if (batch.isEmpty()) break
        deleteHistoryRecords(user.uid, batch.map { it.docId })
        deleted += batch.size
        if (batch.size < HISTORY_CLEANUP_BATCH) break
    }
    val logged = LocationHistoryLog.entries(context)
    if (logged.isNotEmpty()) {
        deleteHistoryRecords(user.uid, logged.map { it.docId })
        deleted += logged.size
        LocationHistoryLog.removeAll(context, logged.map { it.docId }.toSet())
    }
    // Verification re-query (design §6.3): must be empty when we are done.
    val verify = enumerateUnexpiredOwnHistory(user.uid, maxTotal = 1)
    if (verify.isNotEmpty()) {
        throw IllegalStateException("history deletion could not be verified empty")
    }
    return deleted
}

/**
 * Phase 9 / design §6.2 — the owner-device opportunistic cleanup, fired
 * from user actions only (Tracking resume, the delete dialogs): every
 * LOCAL write-log entry whose retention has passed is blind-deleted in
 * one bounded batch (≤ 200 — "repeats per open until exhausted"). The
 * published rules deny LISTING expired records (retention floor on every
 * read — they are already invisible), so the local log is the only
 * discovery mechanism; blind deletes need no read. Failed batches stay
 * logged for the next user action — records are invisible either way,
 * so nothing is overstated.
 */
fun cleanupExpiredOwnHistory(context: Context) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val now = System.currentTimeMillis()
    val expired = LocationHistoryLog.entries(context)
        .filter { it.retentionExpiresAtMillis <= now }
        .take(HISTORY_CLEANUP_BATCH)
    if (expired.isEmpty()) return
    val uid = user.uid
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            deleteHistoryRecords(uid, expired.map { it.docId })
            LocationHistoryLog.removeAll(context, expired.map { it.docId }.toSet())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline: entries stay for the next user action (§6.2's
            // "repeats per open until exhausted").
        }
    }
}

/**
 * Phase 9 — retry a user-initiated deletion that went offline (UX spec
 * §10 `del_offline`): the next user action (Tracking resume) re-runs it.
 * The queued "today" scope re-uses the stored day-start bound so a
 * midnight-crossing retry deletes the day the owner actually chose.
 */
fun flushPendingHistoryDeletion(context: Context) {
    if (FirebaseAuth.getInstance().currentUser == null) return
    val deleteAll = LocationSharingPrefs.pendingDeleteAll(context)
    val dayStart = LocationSharingPrefs.pendingDeleteDayStart(context)
    if (!deleteAll && dayStart == 0L) return
    if (inFlightHistoryFlush) return
    inFlightHistoryFlush = true
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            if (deleteAll) {
                deleteAllOwnHistory(context)
                LocationSharingPrefs.setPendingDeleteAll(context, false)
            }
            if (dayStart != 0L) {
                deleteOwnHistoryForDay(context, dayStart)
                LocationSharingPrefs.setPendingDeleteDayStart(context, 0L)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Still offline — flags stay queued, honestly.
        } finally {
            inFlightHistoryFlush = false
        }
    }
}

@Volatile
private var inFlightHistoryFlush = false

/** True when the exception looks like a connectivity failure (the delete
 * dialogs use this to pick the honest `del_offline` wording over a plain
 * failure — never a false success). */
fun isFirestoreUnreachable(e: Exception): Boolean =
    e is FirebaseFirestoreException && (
        e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        )

/**
 * Local-day start (midnight of the zone's day containing [nowMillis],
 * shifted back [daysBack] days). Private on purpose — the UI package has
 * its own Calendar helpers; repo files stay ui-free.
 */
internal fun localDayStartMillis(nowMillis: Long, daysBack: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, -daysBack)
    return cal.timeInMillis
}

/**
 * Phase 9 — the owner device's LOCAL write log: one line per retained
 * record this device wrote (docId + recordedAt + retentionExpiresAt).
 * Purpose-built for the two things the published rules cannot give back:
 * discovering EXPIRED records (invisible to every query — §6.2's cleanup
 * enumerates them blind from here) and today-scoped deletion without an
 * enumeration read. Bounded; the engine appends after each successful
 * history write (LocationShareService), prunes long-gone entries, and no
 * entry ever leaves the device.
 */
object LocationHistoryLog {

    private const val PREFS_NAME = "location_history_log"
    private const val KEY_ENTRIES = "entries"

    data class Entry(val docId: String, val recordedAtMillis: Long, val retentionExpiresAtMillis: Long)

    /** Record a successful history write (the engine's only call here). */
    fun append(context: Context, deviceId: String, recordedAtMillis: Long, retentionMillis: Long) {
        val expires = recordedAtMillis + retentionMillis
        val entry = "${deviceId}_$recordedAtMillis $recordedAtMillis $expires"
        val current = rawEntries(context).toMutableList()
        current.add(entry)
        // Prune entries whose retention passed > 48 h ago: physically the
        // records are long-expired (invisible), and a cleanup either
        // deleted them already or never will — 48 h of retry headroom is
        // plenty for §6.2's per-open batches.
        val cutoff = System.currentTimeMillis() - 48L * 3_600_000L
        val pruned = current.filter { line ->
            thirdOf(line) > cutoff
        }
        val capped = if (pruned.size > HISTORY_LOG_MAX_ENTRIES) {
            pruned.takeLast(HISTORY_LOG_MAX_ENTRIES)
        } else {
            pruned
        }
        prefs(context).edit().putString(KEY_ENTRIES, capped.joinToString("\n")).apply()
    }

    /** All live log entries (oldest first — the append order). */
    fun entries(context: Context): List<Entry> =
        rawEntries(context).mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size != 3) return@mapNotNull null
            val recorded = parts[1].toLongOrNull() ?: return@mapNotNull null
            val expires = parts[2].toLongOrNull() ?: return@mapNotNull null
            Entry(docId = parts[0], recordedAtMillis = recorded, retentionExpiresAtMillis = expires)
        }

    /** Drop the given docIds from the log (deleted — nothing left to track). */
    fun removeAll(context: Context, docIds: Set<String>) {
        if (docIds.isEmpty()) return
        val kept = rawEntries(context).filter { line -> line.substringBefore(' ') !in docIds }
        prefs(context).edit().putString(KEY_ENTRIES, kept.joinToString("\n")).apply()
    }

    private fun rawEntries(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ENTRIES, "") ?: ""
        return raw.split('\n').filter { it.isNotBlank() }
    }

    private fun thirdOf(line: String): Long =
        line.split(' ').getOrNull(2)?.toLongOrNull() ?: 0L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
