package com.family.remote.support.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ui/Lang.kt — LangPrefBus, the app's in-app language override.
 *
 * The app ships 15 locales (set_lang_info: "Bangla is the default app
 * language on a fresh install; your choice is remembered on this
 * phone."). Android would otherwise follow the system language, which is
 * wrong for this family fleet — the elders' phones often sit in English
 * while the app must speak বাংলা. So the choice lives here:
 *
 *  • [wrap] hands any caller (notifiers, services, the activity itself)
 *    a Context whose resources resolve in the chosen language — every
 *    notification the tray ever shows is localized, killed-app FCM
 *    fallback included.
 *  • [current]/[persist] read and remember the choice (plain
 *    SharedPreferences — survives process death; zero Firestore).
 *  • MainActivity passes the base context through [wrap] in
 *    attachBaseContext, so every `stringResource` in composition already
 *    resolves correctly.
 *
 * The Settings language picker (Preferences → one row → capped dialog)
 * writes through [persist] and recreates the activity; notifiers pick the
 * change up on their next [wrap] with no restart.
 */
object LangPrefBus {

    private const val PREFS = "lang_pref"
    private const val KEY_TAG = "tag"

    /**
     * Fresh-install default (the Owner's directive — set_lang_info):
     * Bengali, not the system language.
     */
    const val DEFAULT_TAG = "bn"

    /** The 15 shipped locales (mirrors the res/values-xx folders). */
    val availableTags = listOf(
        "en", "bn", "hi", "ar", "es", "fr", "pt", "ru",
        "id", "ur", "tr", "de", "zh", "ja", "ne",
    )

    private val _tag = MutableStateFlow(DEFAULT_TAG)

    /** The live choice; updates the moment [persist] writes. */
    val tag: StateFlow<String> = _tag.asStateFlow()

    /**
     * The persisted BCP-47 tag, defaulting to [DEFAULT_TAG] on a fresh
     * install. Also refreshes [tag] so composition can react.
     */
    fun current(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TAG
        _tag.value = stored
        return stored
    }

    /** Remember the choice (Settings → Preferences → Language dialog). */
    fun persist(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TAG, tag).apply()
        _tag.value = tag
    }

    /**
     * A Context whose resources resolve in the chosen language. Safe to
     * call on any context, any number of times, from any thread that may
     * hold a context (callers use it right before string lookups — see
     * AskNotifier / PlaceAlertRepo / ScreenShareEngine). A device that
     * never opened the language dialog gets Bengali, per the directive.
     */
    fun wrap(context: Context): Context {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TAG
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
