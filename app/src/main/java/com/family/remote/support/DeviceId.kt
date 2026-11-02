package com.family.remote.support

import android.content.Context
import android.provider.Settings
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
 */
fun stableDeviceId(context: Context): String {
    val app = context.applicationContext
    val androidId = Settings.Secure.getString(
        app.contentResolver,
        Settings.Secure.ANDROID_ID,
    )?.takeIf { it.isNotBlank() }
    if (androidId != null) return androidId

    // Fallback path only — a one-time UUID, remembered forever after.
    val prefs = app.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
    prefs.getString("id", null)?.let { return it }
    val generated = UUID.randomUUID().toString()
    prefs.edit().putString("id", generated).apply()
    return generated
}
