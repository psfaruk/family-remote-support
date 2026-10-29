package com.family.remote.support.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════
// Phase 16 (v0.20.0) — App language engine.
//
// Owner direction: the app must OPEN IN ENGLISH for a fresh user, and offer
// ~15 popular languages from Settings (বাংলা included, obviously).
//
// How it works (no appcompat dependency, no per-app-locale API needed):
//   • The chosen language code is persisted in SharedPreferences ("ui_prefs").
//   • MainActivity.attachBaseContext() wraps the base context with a
//     configuration carrying that Locale — EVERY stringResource() in the
//     composition then resolves from the matching values-XX/strings.xml.
//   • Changing the language from Settings persists + recreates the Activity,
//     so the whole UI repaints in the new language immediately (same trick
//     the theme preference uses — a REAL setting, not a fake toggle).
//   • Default (no preference saved, fresh install) = ENGLISH, per Owner.
//
// Pure preference/state code: zero business logic lives here.
// ═══════════════════════════════════════════════════════════════════════

object LangPrefBus {

    /** Language codes shipped in res/values-XX/strings.xml (15 total). */
    const val EN = "en"
    const val BN = "bn"
    const val HI = "hi"
    const val AR = "ar"
    const val ES = "es"
    const val FR = "fr"
    const val PT = "pt"
    const val RU = "ru"
    const val ID = "id"
    const val UR = "ur"
    const val TR = "tr"
    const val DE = "de"
    const val ZH = "zh"
    const val JA = "ja"
    const val NE = "ne"

    private const val PREFS = "ui_prefs"
    private const val KEY = "lang_pref"

    /** All supported languages: code + its OWN native name (never translated). */
    val supported: List<Pair<String, String>> = listOf(
        EN to "English",
        BN to "বাংলা",
        HI to "हिन्दी",
        AR to "العربية",
        ES to "Español",
        FR to "Français",
        PT to "Português",
        RU to "Русский",
        ID to "Bahasa Indonesia",
        UR to "اردو",
        TR to "Türkçe",
        DE to "Deutsch",
        ZH to "中文",
        JA to "日本語",
        NE to "नेपाली"
    )

    /** Current language code — observable so the Settings rows recompose. */
    var code: String by mutableStateOf(EN)
        private set

    /** Synchronous read for attachBaseContext — BEFORE any composition. */
    fun loadSync(context: Context) {
        code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, EN) ?: EN
    }

    /**
     * Wrap a base context so its resources resolve the saved language.
     * Called from Activity.attachBaseContext (before onCreate/setContent).
     *
     * v0.21.0 (audit): ALWAYS wrap, English included. The old early-return
     * for EN meant a fresh install on a Bengali/French/… system device
     * opened in the SYSTEM language (contradicting the Owner requirement
     * "app opens in English"), and after bn→en switching every
     * default-locale format kept rendering Bengali digits inside the English
     * UI (Locale.setDefault was never reset).
     */
    fun wrap(context: Context): Context {
        loadSync(context)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return ContextWrapper(context.createConfigurationContext(config))
    }

    /** Persist + repaint: recreates the host Activity when possible. */
    fun set(context: Context, value: String) {
        code = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
        // v0.21.0 (audit): reset the process default locale for EVERY value
        // (English included) so locale-sensitive formatting follows the app
        // language, not the previous selection.
        Locale.setDefault(Locale(value))
        // Walk out of any ContextWrapper to the Activity and recreate it.
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                ctx.recreate()
                return
            }
            ctx = ctx.baseContext
        }
    }
}
