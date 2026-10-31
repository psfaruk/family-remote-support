package com.family.remote.support

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.osmdroid.config.Configuration

/**
 * Application class (registered in AndroidManifest.xml).
 *
 * Its only job: track whether any activity is currently visible, so the
 * session-request notifier (SessionNotifier) can stay quiet while the in-app
 * permission card is on screen and only speak up while the app is backgrounded.
 *
 * Phase 22/M5 — one more job: osmdroid's global configuration. The OSMF tile
 * policy requires a proper User-Agent that identifies the app (never the
 * library default); the tile cache stays on osmdroid's conservative defaults
 * and is user-clearable via the system "clear storage" path. Family-scale
 * traffic (2–4 devices) is well within the OSM usage policy (architecture
 * doc §2).
 */
class App : Application() {

    /** Number of activities in the STARTED state; > 0 means the app is visible. */
    var startedActivities: Int = 0
        private set

    override fun onCreate() {
        super.onCreate()
        // Phase 22/M5 — osmdroid must identify itself to the OSM tile servers
        // with this app's own package id (tile policy compliance).
        Configuration.getInstance().apply {
            userAgentValue = packageName
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
