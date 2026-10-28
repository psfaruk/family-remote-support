package com.family.remote.support

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * v0.9.5 — Guided Remote-assist setup helpers.
 *
 * The 28 Oct 2026 test session proved the touch pipeline itself is fine:
 * the helper's taps ARRIVED at the recipient (the share screen logged
 * "assist tap → assist-off" for every tap), but the recipient had enabled
 * the WRONG switch — Android's built-in "Accessibility menu shortcut" —
 * instead of this app's "Family Remote Support assist" service. The old
 * button dropped them into the generic accessibility list with zero hints,
 * and that list is a maze for a first-time senior user.
 *
 *  - [isAssistEnabled] reads the secure settings list directly, so the UI
 *    can show the true state even before the service binds (and on devices
 *    where the system unbinds it when the app is force-stopped).
 *  - [openAssistSettings] opens the accessibility screen passing our
 *    service name as EXTRA_FRAGMENT_ARG_KEY — stock Android and most OEM
 *    settings apps scroll to / highlight the matching row, so the right
 *    switch is the one thing they see.
 */
object AssistSetup {

    private fun componentName(context: Context): ComponentName =
        ComponentName(context, AssistService::class.java)

    /** "pkg/cls" as Settings writes it in the long form. */
    fun flatString(context: Context): String = componentName(context).flattenToString()

    /** "pkg/.Cls" short form some OEMs store instead. */
    private fun shortString(context: Context): String =
        componentName(context).flattenShortString()

    /** True when the grantor enabled our assist service in system settings. */
    fun isAssistEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { entry ->
            val e = entry.trim()
            e.equals(flatString(context), ignoreCase = true) ||
                e.equals(shortString(context), ignoreCase = true)
        }
    }

    /**
     * One-stop service state: a connected instance OR the settings list says
     * enabled (covers the moment right after the user flips the switch,
     * before Android re-binds the service).
     */
    fun isAssistReady(context: Context): Boolean =
        AssistService.isReady || isAssistEnabled(context)

    /**
     * Open system Accessibility settings with OUR row highlighted (public
     * EXTRA_FRAGMENT_ARG_KEY mechanism). Falls back to the plain list when
     * an OEM settings app ignores the extra.
     */
    fun openAssistSettings(context: Context) {
        try {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            i.putExtra(Settings.EXTRA_FRAGMENT_ARG_KEY, flatString(context))
            context.startActivity(i)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                // nothing else we can do from here
            }
        }
    }
}
