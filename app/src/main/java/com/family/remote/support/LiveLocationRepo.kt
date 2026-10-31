package com.family.remote.support

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
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
    val updatedAtMillis: Long
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
                    updatedAtMillis = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

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
 * path then deletes the liveLocations document. When the service is already
 * dead (process killed, phone rebooted — §5 deliberately ships no
 * auto-restart) no onDestroy will ever fire, so this stop path clears the
 * document itself, exactly once: the running check happens before
 * stopService, when it is still truthful.
 */
fun stopLocationSharing(context: Context) {
    LocationSharingPrefs.setSharingEnabled(context, false)
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
