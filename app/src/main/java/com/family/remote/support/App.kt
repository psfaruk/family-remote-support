package com.family.remote.support

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Application class (registered in AndroidManifest.xml).
 *
 * Its only job: track whether any activity is currently visible, so the
 * session-request notifier (SessionNotifier) can stay quiet while the in-app
 * permission card is on screen and only speak up while the app is backgrounded.
 */
class App : Application() {

    /** Number of activities in the STARTED state; > 0 means the app is visible. */
    var startedActivities: Int = 0
        private set

    override fun onCreate() {
        super.onCreate()
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
