package com.family.remote.support

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * DeviceId.kt — the stable per-device identity (W1 rebuild; the original
 * helper lived with the never-committed frontend).
 *
 * Who needs it: the FCM registry ([PresenceRepo.writeFcmTokenDoc] — the
 * `deviceId` field, rules-capped at 128 chars), presence docs
 * (`devicePresence/{uid}_{deviceId}`), sign-out hygiene
 * ([PresenceRepo.deleteOwnPresenceAndToken] — must compute the SAME id
 * again), the location-grant engine (LocationAuthzRepo) and the location
 * upload path (LiveLocationRepo / LocationShareService).
 *
 * Choice: Settings.Secure.ANDROID_ID, the platform's own per-device id —
 * on Android 8+ (minSdk 26) it is stable for (device, user, signing key),
 * so a phone keeps its identity across app updates and reinstalls while
 * different signing keys (debug vs release) never collide. The rare
 * null/blank (some emulators, corrupted images) falls back to a UUID
 * persisted once in SharedPreferences — still stable across restarts,
 * which is the whole contract. No permissions required either way.
 *
 * v0.51.2 (crash-fix wave) — THE FORMAT CONTRACT. Every device-scoped
 * Firestore path in the LIVE rules pins the deviceId to exactly 24
 * lowercase hex characters:
 *   • devicePresence:  docId matches uid + '_[0-9a-f]{24}'
 *   • liveLocations:   docId matches uid + '_[0-9a-f]{24}'
 *   • locationAuthorizations: data.deviceId matches '[0-9a-f]{24}'
 *   • locationHistory: recordId matches '[0-9a-f]{24}_[0-9]+'
 * ANDROID_ID is only 16 hex chars and the UUID fallback is 36 chars with
 * dashes — the W1 rewrite returned those raw, so EVERY one of those
 * writes was PERMISSION_DENIED (the Owner's device report: the app
 * closes a few seconds after sign-in — the unguarded heartbeat then
 * killed the process). [deriveDeviceId] now folds the raw seed through
 * SHA-256 and takes the first 24 hex chars: deterministic (same seed →
 * same id, stable across restarts and reinstalls), always lowercase hex,
 * and exactly the length every rules regex demands. Hashing keeps the
 * derived id from leaking the raw ANDROID_ID into document ids too.
 */
fun stableDeviceId(context: Context): String {
    val app = context.applicationContext
    val androidId = Settings.Secure.getString(
        app.contentResolver,
        Settings.Secure.ANDROID_ID,
    )?.takeIf { it.isNotBlank() }
    val seed: String = androidId ?: run {
        // Fallback path only — a one-time UUID, remembered forever after.
        val prefs = app.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
    return deriveDeviceId(seed)
}

/**
 * The rules-contract derivation, pure and JVM-testable: SHA-256 → the
 * first 24 lowercase hex characters. Distinct seeds give distinct ids
 * (collision odds ~2^-96 over the family fleet); the same seed always
 * gives the same id.
 */
fun deriveDeviceId(seed: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(DEVICE_ID_HEX_LEN)

/** The exact length every device-scoped rules regex requires. */
const val DEVICE_ID_HEX_LEN = 24
