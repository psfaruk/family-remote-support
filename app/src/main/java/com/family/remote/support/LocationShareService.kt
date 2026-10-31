package com.family.remote.support

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 22/M5 (v0.33.0) — live location ENGINE: the location foreground
 * service that turns provider fixes into gated Firestore writes. Design:
 * docs/LIVE_LOCATION_ARCHITECTURE.md (§2 stack, §3 flow, §6 update strategy).
 *
 * A plain started Service, started via ContextCompat.startForegroundService
 * while the app is visible (the Android 14 while-in-use rule) and declared
 * with the location FGS type. The persistent low-importance notification is
 * the anti-stealth guarantee of §5: sharing is always owner-visible, and it
 * never contains coordinates (§9, lock-screen safe). Deliberately NO
 * WorkManager and no auto-restart after reboot — the owner re-toggles.
 *
 * Fix acceptance is the whole quota story (§3/§7): a fix reaches Firestore
 * only when ALL gates pass — elapsed since the last ACCEPTED write ≥ the
 * configured interval, movement ≥ 25 m since the last accepted fix, and
 * accuracy ≤ 100 m. A stationary device backs the request interval off ×2
 * up to 5 min (§6); any accepted movement resets the cadence. Rejected
 * fixes cost nothing — no write, no marker change.
 *
 * Providers: FusedLocationProviderClient at balanced power accuracy is the
 * primary path (§2); if Google Play services is unusable — the classes can
 * fail to load at all on a de-Googled phone — every Fused call is wrapped so
 * the service reroutes to android.location.LocationManager for GPS and
 * network with the same gates. All fix delivery lands on the main thread,
 * which is why the gate state below needs no synchronization; only the
 * Firestore writes hop to the service scope.
 */
class LocationShareService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Provider wiring (exactly one path active at a time) ──────────────

    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var platformListener: LocationListener? = null

    /** Once the platform fallback is in charge it stays in charge: a missing
     * Play-services stack does not reappear mid-run, and ping-ponging
     * between providers would churn registrations for nothing. */
    private var usingPlatformFallback = false

    /** M8 (v0.34.0) — one "sharing_started" log per service instance: a
     * re-start command delivered to the LIVE service (the interval-change
     * re-arm) must not re-log "turned on", and an instance that never
     * passed the acceptance gates must not log a matching "turned off"
     * either — real events only. */
    private var shareStartLogged = false

    // ── Gate + back-off state (§3/§6) — main-thread only ─────────────────

    private var configuredIntervalMillis = LocationSharingPrefs.INTERVAL_1M
    private var effectiveIntervalMillis = LocationSharingPrefs.INTERVAL_1M

    /** Last STATIONARY_FIX_WINDOW raw fixes, accepted or not — §6 counts
     * fixes, not writes, because back-off must react while the distance
     * gate is busy rejecting everything. */
    private val recentFixes = ArrayDeque<Fix>()
    private var lastAccepted: Fix? = null
    private var lastAcceptedElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY: a killed service stays dead — sharing never
        // resumes after swipe-away or reboot without the owner re-toggling
        // (§5, deliberate decision).
        val wanted = intent != null &&
            LocationSharingPrefs.isSharingEnabled(this) &&
            FirebaseAuth.getInstance().currentUser != null &&
            hasLocationPermission()
        if (!wanted) {
            // Defensive backstop, not the happy path (M5-D checks all three
            // before starting the service): consent flipped off mid-flight,
            // signed out, or the while-in-use permission is gone. Stop
            // instead of crashing or running silently.
            //
            // v0.37.0 (crash wave): ContextCompat.startForegroundService() was
            // used to start THIS instance, so Android's 5-second contract
            // requires a startForeground() call even on this stop path — the
            // old code skipped it when the location permission was gone
            // (ForegroundServiceDidNotStartInTimeException = crash). A
            // location-type startForeground would itself throw on Android 14
            // without the permission, so the notification goes up with NO
            // foreground-service type (type 0), which is always legal, and
            // the service then stops immediately — the tray entry vanishes
            // with the stop, nothing lingers.
            if (intent != null) {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification(),
                        0 // no FGS type: satisfies the 5 s contract, no SecurityException
                    )
                } catch (_: Exception) {
                    // even the type-0 call can fail on exotic OEM states —
                    // stopSelf() below still ends the instance honestly
                }
            }
            stopSelf()
            return Service.START_NOT_STICKY
        }
        startInForeground()
        // Re-read the owner's cadence on every start command: a re-start
        // while already running doubles as the "Settings changed the
        // interval" re-request path, and a fresh cadence re-arms the
        // stationary back-off.
        configuredIntervalMillis = LocationSharingPrefs.intervalMillis(this)
        effectiveIntervalMillis = configuredIntervalMillis
        restartLocationRequests()
        // M8 (v0.34.0) — the acceptance gates passed and the engine began:
        // record the REAL start (no coordinates — the events log never
        // carries them, same §9 rule as the tray notification).
        if (!shareStartLogged) {
            shareStartLogged = true
            NotificationsLog.record(this, "sharing_started")
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        // M8 (v0.34.0) — every stop path funnels here: the owner's toggle,
        // the sign-out teardown, and the defensive stopSelf branches
        // (consent off / permission lost / signed out mid-run). Logged only
        // when this instance really started, and only while still signed
        // in — the sign-out flow wipes the log after signOut(), and a record
        // landing behind that wipe would outlive its account.
        if (shareStartLogged && FirebaseAuth.getInstance().currentUser != null) {
            NotificationsLog.record(this, "sharing_stopped")
        }
        stopLocationRequests()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Best-effort document cleanup (§3 owner-stop flow): the service
        // only writes while sharing, so the document is simply deleted.
        // Rules allow exactly the signed-in owner's delete, so a null user
        // is skipped silently. The delete runs on its own throwaway scope on
        // purpose — cancelling serviceScope above must not kill it — and
        // stays best-effort: if the process dies before it lands, the
        // counterpart's freshness math shows STALE then OFFLINE, never live.
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val uid = user.uid
            val deviceId = stableDeviceId(applicationContext)
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    deleteOwnLocation(uid, deviceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Offline during shutdown: freshness handles the rest.
                }
            }
        }
        super.onDestroy()
    }

    // ── Foreground notification (mirrors ScreenShareService's pattern) ───

    private fun startInForeground() {
        // v0.37.0 (crash wave): the location-type startForeground can throw
        // SecurityException on Android 14 when the while-in-use permission is
        // revoked between the check and this call (millisecond TOCTOU); fall
        // back to the type-0 form so the FGS contract still holds.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (_: SecurityException) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), 0)
        }
    }

    // v0.37.0 (dedupe wave) — extracted so the defensive no-permission path
    // can satisfy the startForegroundService contract with a type-0 call.
    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Location sharing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shown while this phone's location is being shared with your family."
                }
            )
        }
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Localized by the M5-D wave for all 15 languages; no coordinates
        // ever appear here (§9: the tray is lock-screen visible).
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.loc_notif_title))
            .setContentText(getString(R.string.loc_notif_body))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // While-in-use is the v1 posture (§5): background location is never
        // requested, and the service only runs while the FGS is alive.
        return fine || coarse
    }

    // ── Location providers ────────────────────────────────────────────────

    /**
     * Preferred provider (§2): FusedLocationProviderClient, balanced power
     * accuracy, requested at the effective interval. Everything GMS-related
     * sits inside try/catch(Throwable) because a device without usable Play
     * services fails here with NoClassDefFoundError or IllegalStateException
     * rather than with a Task — and even when registration returns a Task it
     * can fail asynchronously, so the failure listener reroutes to the
     * platform fallback too. The min update interval equals the interval on
     * purpose: the §3 time gate would reject faster deliveries anyway, so
     * asking for them only burns battery.
     */
    private fun startLocationRequests() {
        val interval = effectiveIntervalMillis
        try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                interval
            )
                .setMinUpdateIntervalMillis(interval)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val fix = result.lastLocation ?: return
                    onFix(fix.latitude, fix.longitude, fix.accuracy.toInt())
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { startPlatformFallback() }
            fusedClient = client
            fusedCallback = callback
        } catch (_: Throwable) {
            startPlatformFallback()
        }
    }

    /**
     * §2 fallback: plain android.location.LocationManager for GPS and
     * network, the same acceptance gates — onFix is provider-agnostic. The
     * classic four-argument requestLocationUpdates delivers callbacks on the
     * calling thread's Looper; every registration here runs on the main
     * thread, so fixes land where the gate state lives.
     */
    private fun startPlatformFallback() {
        if (!running || platformListener != null) return
        usingPlatformFallback = true
        val interval = effectiveIntervalMillis
        try {
            val manager =
                applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onFix(location.latitude, location.longitude, location.accuracy.toInt())
                }
            }
            // Register BOTH providers even when disabled: a registration on
            // a currently-disabled provider simply starts delivering once
            // the owner turns location back on — the honest §6 "no fix, no
            // writes" behavior without a provider-state listener.
            var revoked = false
            var registered = false
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )
            for (provider in providers) {
                try {
                    manager.requestLocationUpdates(provider, interval, 0f, listener)
                    registered = true
                } catch (_: IllegalArgumentException) {
                    // Bogus provider name on a broken ROM — try the other.
                } catch (_: SecurityException) {
                    revoked = true
                }
            }
            if (registered) {
                locationManager = manager
                platformListener = listener
            } else if (revoked) {
                // While-in-use permission revoked mid-run: nothing this
                // service may legitimately do anymore — stop rather than
                // keep running silently.
                stopSelf()
            }
        } catch (_: Throwable) {
            // Even the platform stack is unavailable: nothing registers, no
            // fix ever arrives, the notification stays honest (§6 offline
            // row — no writes, the document keeps its last state).
        }
    }

    /** Re-request updates at the current effective interval. Providers only
     * honor an interval at registration time, so changing the cadence (§6
     * back-off doubling or movement reset) means re-registering. */
    private fun restartLocationRequests() {
        stopLocationRequests()
        if (usingPlatformFallback) startPlatformFallback() else startLocationRequests()
    }

    private fun stopLocationRequests() {
        fusedCallback?.let { callback ->
            try {
                fusedClient?.removeLocationUpdates(callback)
            } catch (_: Throwable) {
                // GMS already gone — nothing registered to remove.
            }
        }
        fusedClient = null
        fusedCallback = null
        platformListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {
                // Manager already gone — nothing to remove.
            }
        }
        locationManager = null
        platformListener = null
    }

    // ── Acceptance gates + stationary back-off (§3/§6) ────────────────────

    /**
     * One incoming fix from whichever provider is active. All three §3 gates
     * must pass before a write: elapsed since the last ACCEPTED write ≥ the
     * configured interval (first fix: no previous write, so it passes),
     * distance from the last accepted fix ≥ 25 m, accuracy ≤ 100 m.
     */
    private fun onFix(lat: Double, lng: Double, accuracyMeters: Int) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()

        // Stationary back-off bookkeeping first (§6): the window holds raw
        // fixes, so back-off keeps working while the gates below are busy
        // rejecting everything a stationary phone produces.
        recentFixes.addLast(Fix(lat, lng))
        while (recentFixes.size > STATIONARY_FIX_WINDOW) recentFixes.removeFirst()
        if (allRecentFixesStationary()) {
            val backedOff = (effectiveIntervalMillis * 2)
                .coerceAtMost(LocationSharingPrefs.INTERVAL_5M)
            if (backedOff != effectiveIntervalMillis) {
                effectiveIntervalMillis = backedOff
                restartLocationRequests()
            }
        }

        if (accuracyMeters > MAX_ACCURACY_METERS) return
        val previous = lastAccepted
        if (previous != null) {
            if (now - lastAcceptedElapsed < configuredIntervalMillis) return
            if (distanceMeters(previous.lat, previous.lng, lat, lng) < MIN_MOVE_METERS) return
        }

        // Accepted. Remember the fix, then reset the back-off — an accepted
        // fix always moved ≥ 25 m (the gate above), which is exactly §6's
        // "any accepted movement resets the cadence" rule.
        lastAccepted = Fix(lat, lng)
        lastAcceptedElapsed = now
        if (effectiveIntervalMillis != configuredIntervalMillis) {
            effectiveIntervalMillis = configuredIntervalMillis
            restartLocationRequests()
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Signed out mid-run (rules would deny the write anyway): stop
            // honestly instead of spinning on rejected writes.
            stopSelf()
            return
        }
        val uid = user.uid
        val deviceId = stableDeviceId(applicationContext)
        serviceScope.launch {
            try {
                writeLocationFix(uid, deviceId, lat, lng, accuracyMeters)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline at write time (§6): no write — the document keeps
                // the last accepted state and freshness handles the rest.
            }
        }
    }

    /**
     * §6 stationary test: the last [STATIONARY_FIX_WINDOW] fixes all within
     * [MIN_MOVE_METERS] of EACH OTHER (all three pairs, not just adjacent
     * ones). Strictly less-than on purpose — a move of exactly 25 m already
     * passes the §3 movement gate, so counting it as stationary too would
     * fight the reset rule.
     */
    private fun allRecentFixesStationary(): Boolean {
        if (recentFixes.size < STATIONARY_FIX_WINDOW) return false
        val a = recentFixes[0]
        val b = recentFixes[1]
        val c = recentFixes[2]
        return distanceMeters(a.lat, a.lng, b.lat, b.lng) < MIN_MOVE_METERS &&
            distanceMeters(b.lat, b.lng, c.lat, c.lng) < MIN_MOVE_METERS &&
            distanceMeters(a.lat, a.lng, c.lat, c.lng) < MIN_MOVE_METERS
    }

    /** Great-circle distance in meters (haversine): at a 25 m gate the
     * family-scale distances need no local-flatness approximation, just
     * honest trigonometry. */
    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2.0) * sin(dLng / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1.0 - a))
    }

    /** Last raw provider fix: feeds the §6 stationary window and the §3
     * distance gate. */
    private data class Fix(val lat: Double, val lng: Double)

    companion object {
        private const val CHANNEL_ID = "location_sharing"

        /** Distinct from ScreenShareService's 71 — one notification per
         * foreground service, so stopping either clears only its own. */
        const val NOTIFICATION_ID = 72

        /** §3: fixes worse than this are rejected outright. */
        private const val MAX_ACCURACY_METERS = 100

        /** §3/§6: movement gate and stationary threshold, meters. */
        private const val MIN_MOVE_METERS = 25.0

        /** §6: stationary window size — fixes, not accepted writes. */
        private const val STATIONARY_FIX_WINDOW = 3

        private const val EARTH_RADIUS_METERS = 6_371_000.0

        @Volatile
        private var running = false

        /** Process-local running flag backing isLocationSharingRunning() —
         * set in onCreate, cleared in onDestroy. Volatile because
         * isLocationSharingRunning() is called from arbitrary threads. */
        val isRunning: Boolean get() = running

        // v0.37.0 (dead-code sweep) — cancelNotification(context) removed:
        // defined here but never called anywhere in app/src (verified by
        // grep); the service's own stop path already clears the tray entry
        // via stopForeground(STOP_FOREGROUND_REMOVE).
    }
}
