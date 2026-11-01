package com.family.remote.support

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.osmdroid.config.Configuration

/**
 * Application class (registered in AndroidManifest.xml).
 *
 * Its jobs: track whether any activity is currently visible, so the
 * session-request notifier (SessionNotifier) can stay quiet while the
 * in-app permission card is on screen and only speak up while the app is
 * backgrounded — and own the osmdroid global configuration.
 *
 * Phase 22/M5 — osmdroid identifies itself to the OSM tile servers with
 * this app's own package id (tile policy compliance).
 *
 * v0.44.0 (smooth-app wave) — two more jobs:
 *   • The LOCAL crash journal installs first (the Owner's v0.43.0 device
 *     report "সেটিং ট্যাব এ ক্লিক করলে ক্র্যাশ করে" arrived with no stack
 *     trace and the app ships zero crash SDKs by policy — see
 *     CrashJournal.kt; purely local, zero telemetry).
 *   • osmdroid tile plumbing tuned for the Owner's "ম্যাপ কে আরো ফার্স্ট ও
 *     স্মুথ করতে হবে": parallel tile downloads, a real in-memory tile LRU
 *     (the stock default keeps only a handful of tiles, so every pan
 *     re-decoded from disk), and a generous but bounded disk cache. The
 *     family fleet's 2–4 devices stay far inside every tile provider's
 *     fair-use policy; the cache remains user-clearable via the system
 *     "clear storage" path.
 */
class App : Application() {

    /** Number of activities in the STARTED state; > 0 means the app is visible. */
    var startedActivities: Int = 0
        private set

    override fun onCreate() {
        super.onCreate()
        // v0.44.0 — capture any uncaught exception locally BEFORE the
        // system dialog appears (the system flow itself is unchanged).
        CrashJournal.install(this)
        // Phase 22/M5 — osmdroid must identify itself to the tile servers
        // with this app's own package id (tile policy compliance).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // v0.44.0 speed tuning — see the class KDoc.
            tileDownloadThreads = 6
            cacheMapTileCount = 48
            tileFileSystemCacheMaxBytes = 128L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 112L * 1024 * 1024
        }
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities += 1
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                    // not needed
                }

                override fun onActivityResumed(activity: Activity) {
                    // not needed
                }

                override fun onActivityPaused(activity: Activity) {
                    // not needed
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    // not needed
                }

                override fun onActivityDestroyed(activity: Activity) {
                    // not needed
                }
            }
        )
    }
}
