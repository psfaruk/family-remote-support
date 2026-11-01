package com.family.remote.support

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.family.remote.support.ui.LangPrefBus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Location master plan Phase 11 (v0.43.0) — the CLIENT side of "My places
 * & alerts": the Firestore CRUD (design §2.3, D3 — Firestore-backed so the
 * places survive reinstall), the LOCAL store the location FGS reads at
 * cold start (offline-honest: monitoring never needs the network), the
 * process-wide registry the service reads per fix, and the local
 * notification poster. Rules (published ruleset 2fc2e1da…): owner-ONLY in
 * every direction — every other identity gets 403 on any read or write of
 * `placeAlerts`.
 *
 * Doc shape (hasOnly-enforced): `ownerUserId`, `name` (1..64 chars),
 * `lat`/`lng` (range-validated), `radiusMeters` (int 50..1000 — the UI
 * chips 100/200/500 are a subset), `arrivalEnabled`/`departureEnabled`
 * (bool), `cooldownMinutes` (int 5..120), `createdAt`/`updatedAt`
 * (serverTimestamp, ±10-min rules window), `appVersionCode`. docId =
 * `{ownerUid}_{16 hex}` — millis-derived, strictly monotonic per device
 * (same convention as locationHistory record ids); updates are
 * field-fenced by the rules (diff on the 8 config fields only —
 * `ownerUserId` and `createdAt` are immutable).
 *
 * §75 discipline: every Firestore read/write here happens inside a user
 * action (opening the places screen, adding/editing/removing a place).
 * The SERVICE never touches this file's Firestore paths — it reads the
 * local store once per lifetime and the in-memory registry per fix, so
 * the engine's per-fix cost stays pure arithmetic (DR-P2 realized).
 */
private const val PLACE_ALERTS_COLLECTION = "placeAlerts"

/**
 * D13 — the client-side cap: the add form refuses beyond this many places
 * (one bounded page of docs; the rules cannot count documents, so the
 * client polices its own surface — same posture as the cadence coercion).
 */
const val MAX_SAVED_PLACES = 20

/**
 * One saved place, full persistence shape (the engine consumes the
 * [PlaceRule] projection — `toRule`).
 */
data class SavedPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    val arriveOn: Boolean,
    val departOn: Boolean,
    val cooldownMinutes: Int
) {
    /** The pure-engine projection (no persistence concern crosses over). */
    fun toRule(): PlaceRule = PlaceRule(
        id = id,
        name = name,
        lat = lat,
        lng = lng,
        radiusMeters = radiusMeters,
        arriveOn = arriveOn,
        departOn = departOn,
        cooldownMinutes = cooldownMinutes
    )
}

/** Doc id for a new place: millis-derived 16 hex, strictly monotonic per
 * process — two saves in the same millisecond cannot collide. (A
 * cross-device same-owner same-millisecond collision would set()-overwrite
 * one doc — a bounded, honest edge recorded with D13.) */
private fun newPlaceDocId(uid: String): String {
    val millis = LAST_DOC_MILLIS.updateAndGet { prev ->
        val now = System.currentTimeMillis()
        if (now > prev) now else prev + 1
    }
    return uid + "_" + "%016x".format(millis)
}

private val LAST_DOC_MILLIS = AtomicLong(0L)

// ── The repo: user-action-driven Firestore CRUD ─────────────────────────

/**
 * All places of the signed-in owner (one bounded read per screen open —
 * a user action, §75). Offline/signed-out → throws honestly; the screen
 * routes it. The service's monitoring is NOT affected — it reads the
 * local store, never this path.
 */
suspend fun readSavedPlaces(): List<SavedPlace> {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    val snap = Fb.db.collection(PLACE_ALERTS_COLLECTION)
        .whereEqualTo("ownerUserId", user.uid)
        .limit(MAX_SAVED_PLACES.toLong())
        .get()
        .awaitWithTimeout("reading saved places")
    return snap.documents.mapNotNull { doc -> doc.toSavedPlace(doc.id) }
}

/**
 * Publish a successfully loaded list (screen open): mirror to the local
 * store + refresh the runtime registry so a running share picks the
 * server-truth list up on its next fix.
 */
fun publishLoadedPlaces(context: Context, places: List<SavedPlace>) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    PlaceAlertStore.save(context, user.uid, places)
    PlaceAlertRuntime.publish(places)
}

/**
 * Create a place (draft with a blank id; the doc id is minted here and
 * the persisted [SavedPlace] — id filled in — is returned). On success
 * the store + registry are refreshed from [current] + the new place, so
 * the running share picks it up on its NEXT fix (no listener, no push).
 */
suspend fun createSavedPlace(
    context: Context,
    current: List<SavedPlace>,
    draft: SavedPlace
): SavedPlace {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    val docId = newPlaceDocId(user.uid)
    Fb.db.collection(PLACE_ALERTS_COLLECTION)
        .document(docId)
        .set(
            mapOf(
                "ownerUserId" to user.uid,
                "name" to draft.name,
                "lat" to draft.lat,
                "lng" to draft.lng,
                "radiusMeters" to draft.radiusMeters,
                "arrivalEnabled" to draft.arriveOn,
                "departureEnabled" to draft.departOn,
                "cooldownMinutes" to draft.cooldownMinutes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "appVersionCode" to BuildConfig.VERSION_CODE
            )
        )
        .awaitWithTimeout("creating a saved place")
    val saved = draft.copy(id = docId)
    publishLoadedPlaces(context, current + saved)
    return saved
}

/**
 * Update a place's alert config (name/position/radius/toggles/cooldown).
 * The rules field-fence updates — `ownerUserId`/`createdAt` are immutable;
 * the merge below sends exactly the 8 fence-allowed fields.
 */
suspend fun updateSavedPlace(context: Context, current: List<SavedPlace>, place: SavedPlace) {
    FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    Fb.db.collection(PLACE_ALERTS_COLLECTION)
        .document(place.id)
        .set(
            mapOf(
                "name" to place.name,
                "lat" to place.lat,
                "lng" to place.lng,
                "radiusMeters" to place.radiusMeters,
                "arrivalEnabled" to place.arriveOn,
                "departureEnabled" to place.departOn,
                "cooldownMinutes" to place.cooldownMinutes,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        .awaitWithTimeout("updating a saved place")
    publishLoadedPlaces(context, current.map { if (it.id == place.id) place else it })
}

/** Delete a place (owner-only per rules). Store + registry drop it. */
suspend fun deleteSavedPlace(context: Context, current: List<SavedPlace>, placeId: String) {
    FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("signed out")
    Fb.db.collection(PLACE_ALERTS_COLLECTION)
        .document(placeId)
        .delete()
        .awaitWithTimeout("deleting a saved place")
    publishLoadedPlaces(context, current.filterNot { it.id == placeId })
}

/** Document → [SavedPlace]; a malformed doc is skipped honestly (never
 * crashes the list over one bad doc — same posture as the history read). */
private fun com.google.firebase.firestore.DocumentSnapshot.toSavedPlace(
    fallbackId: String
): SavedPlace? {
    val name = getString("name")?.trim() ?: return null
    if (name.isEmpty()) return null
    val lat = getDouble("lat") ?: return null
    val lng = getDouble("lng") ?: return null
    val radius = getLong("radiusMeters")?.toInt() ?: return null
    val cooldown = getLong("cooldownMinutes")?.toInt() ?: return null
    return SavedPlace(
        id = fallbackId,
        name = name,
        lat = lat,
        lng = lng,
        radiusMeters = radius,
        arriveOn = getBoolean("arrivalEnabled") == true,
        departOn = getBoolean("departureEnabled") == true,
        cooldownMinutes = cooldown
    )
}

// ── The local store: the SERVICE's read path (zero network) ─────────────

/**
 * D13 — the SharedPreferences mirror of the owner's places. Written
 * through on every successful Firestore op and every successful screen
 * load; read ONCE per service lifetime (the FGS may start offline —
 * monitoring must not depend on the network, because self-alerts are
 * entirely local). Carries the owning uid so a different account signing
 * in on the same phone never monitors the previous user's places (the
 * registry RESETS to empty when the mirror does not belong to the current
 * user — see [PlaceAlertRuntime.refreshFromStore]).
 */
private object PlaceAlertStore {
    private const val PREFS_NAME = "place_alerts_store"
    private const val KEY_UID = "owner_uid"
    private const val KEY_PLACES = "places_json"

    /** TRUE when the mirror belongs to [uid] (possibly an empty list —
     * "all places deleted" is a legitimate mirror for this uid). */
    fun holds(context: Context, uid: String): Boolean =
        prefs(context).getString(KEY_UID, "") == uid

    fun load(context: Context, uid: String): List<SavedPlace> {
        if (!holds(context, uid)) return emptyList()
        val raw = prefs(context).getString(KEY_PLACES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                SavedPlace(
                    id = id,
                    name = name,
                    lat = o.optDouble("lat"),
                    lng = o.optDouble("lng"),
                    radiusMeters = o.optInt("radiusMeters", 200),
                    arriveOn = o.optBoolean("arriveOn", true),
                    departOn = o.optBoolean("departOn", false),
                    cooldownMinutes = o.optInt("cooldownMinutes", 10)
                )
            }
        } catch (_: Exception) {
            emptyList() // corrupt mirror: honest empty, the screen load refills it
        }
    }

    fun save(context: Context, uid: String, places: List<SavedPlace>) {
        val arr = JSONArray()
        places.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("lat", p.lat)
                    .put("lng", p.lng)
                    .put("radiusMeters", p.radiusMeters)
                    .put("arriveOn", p.arriveOn)
                    .put("departOn", p.departOn)
                    .put("cooldownMinutes", p.cooldownMinutes)
            )
        }
        prefs(context).edit()
            .putString(KEY_UID, uid)
            .putString(KEY_PLACES, arr.toString())
            .apply()
    }

    /** Remove saved-place names and coordinates from this device on sign-out. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// ── The runtime registry: what the engine reads per fix ─────────────────

/**
 * D13 — the process-wide place list. The service reads [current] on every
 * accuracy-passing fix (an @Volatile immutable snapshot — no lock, no
 * allocation); the repo refreshes it after every successful load/op.
 * Because the places screen and the FGS share one process, an edit made
 * while sharing is live is picked up by the very NEXT fix — no listener,
 * no timer, no background sweep (§75 by construction).
 */
object PlaceAlertRuntime {
    @Volatile
    private var places: List<PlaceRule> = emptyList()

    /** The engine's current place list (immutable snapshot). */
    fun current(): List<PlaceRule> = places

    /** Repo path: replace the whole list (store mirror already written). */
    fun publish(places: List<SavedPlace>) {
        this.places = places.map { it.toRule() }
    }

    /**
     * The service's cold-start path (user action: starting sharing). If
     * the local mirror belongs to the CURRENT user, it becomes the
     * engine's list (offline-honest); if it does not (or there is none),
     * the registry RESETS to empty — a different account on the same
     * phone must never monitor the previous user's fences.
     */
    fun refreshFromStore(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            places = emptyList()
            return
        }
        places = PlaceAlertStore.load(context, user.uid).map { it.toRule() }
    }

    /** Clear process memory as well as the local mirror during sign-out. */
    fun clear() {
        places = emptyList()
    }
}

/** Sign-out cleanup for locally cached saved-place names and coordinates. */
fun clearSavedPlacesLocalCache(context: Context) {
    PlaceAlertStore.clear(context)
    PlaceAlertRuntime.clear()
}

// ── The notifier: local, coordinate-free, language-honest ───────────────

/**
 * D13 — posts self-alert notifications. UX spec §7: name only, never
 * coordinates; the channel is its OWN ("place_alerts", DEFAULT
 * importance) so the user can tune place alerts separately from the
 * sharing tray notification (LOW) and the support-session asks (HIGH) in
 * system settings. No POST_NOTIFICATIONS grant (Android 13+) → silently
 * nothing, same contract as SessionNotifier.
 */
object PlaceAlertNotifier {
    private const val CHANNEL_ID = "place_alerts"

    /** Tray-id base — far from the sharing FGS's 72 and every other
     * notification; a stable per-(place, kind) offset makes a repeat
     * arrival REPLACE the earlier arrival of the same place instead of
     * stacking. */
    private const val BASE_NOTIFICATION_ID = 80

    fun post(context: Context, events: List<PlaceAlertEvent>) {
        if (events.isEmpty()) return
        // v0.45.0 (audit P8): place alerts finally LAND in the events log —
        // BEFORE the notification-permission gate (the log is the
        // Notification Center's memory; a user with tray notifications off
        // still gets the history), at the alert's real fire time with the
        // place name as arg (never coordinates — §9).
        for (event in events) {
            NotificationsLog.recordAt(
                context,
                "place_alert",
                event.atMillis,
                event.placeName,
                event.placeId
            )
        }
        if (!isNotifGranted(context)) return // honest absence, never a crash
        val lc = LangPrefBus.wrap(context)
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    lc.getString(R.string.place_notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        for (event in events) {
            // v0.45.0 (audit P4): tapping lands on the Tracking tab (the
            // map with the place's member pin), not a bare Home drop.
            val openIntent = PendingIntent.getActivity(
                context,
                (event.placeId + event.kind.name).hashCode(),
                Intent(context, MainActivity::class.java)
                    .putExtra(DeepLinks.EXTRA_ROUTE, "tracking")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(lc, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(
                    lc.getString(
                        if (event.kind == PlaceAlertKind.ARRIVAL) {
                            R.string.place_notif_title_arrive
                        } else {
                            R.string.place_notif_title_depart
                        }
                    )
                )
                .setContentText(
                    lc.getString(
                        if (event.kind == PlaceAlertKind.ARRIVAL) {
                            R.string.place_notif_arrive
                        } else {
                            R.string.place_notif_depart
                        },
                        event.placeName
                    )
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(openIntent)
                .build()
            manager.notify(notificationId(event.placeId, event.kind), notification)
        }
    }

    private fun notificationId(placeId: String, kind: PlaceAlertKind): Int =
        BASE_NOTIFICATION_ID +
            Math.floorMod((placeId + kind.name).hashCode(), 1_000_000)
}
