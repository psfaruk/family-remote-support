package com.family.remote.support.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ui/Theme.kt — the master palette (FRONTEND_ARCHITECTURE §3).
 *
 * v0.48.0 (worklog §92) — the Owner-directed emerald identity: green
 * primary family, amber secondary for FAMILY chips, teal tertiary for
 * presence, warm-grey emerald-tinted neutrals. Every screen draws through
 * `MaterialTheme.colorScheme.*` ONLY — hardcoded colors on screens are
 * forbidden by the design-system master rule.
 *
 * Type is the senior scale (v0.45 "senior type scale"): comfortably large
 * defaults that still wrap Bengali/Arabic/Urdu long words at 1.3× font
 * scale (fixed heights are forbidden; text wraps).
 *
 * Shapes: control 10 · card 16 · featureCard 20 · dialog 22.
 *
 * `warning` is NOT a Material3 role — it ships as a CompositionLocal so
 * permission-attention panels get the amber without per-screen hex values.
 */

// ── Palette ───────────────────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = Color(0xFF8F4C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    error = Color(0xFFC83C45),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDE),
    onErrorContainer = Color(0xFF4A0C10),
    background = Color(0xFFF6F9F7),
    onBackground = Color(0xFF1A2420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2420),
    surfaceVariant = Color(0xFFDFE7E2),
    onSurfaceVariant = Color(0xFF5D6B64),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F7F4),
    surfaceContainer = Color(0xFFEDF2EF),
    surfaceContainerHigh = Color(0xFFE7EDE9),
    surfaceContainerHighest = Color(0xFFE1E8E3),
    outline = Color(0xFF6F7D75),
    outlineVariant = Color(0xFFDFE7E2),
    inverseSurface = Color(0xFF2F3A35),
    inverseOnSurface = Color(0xFFF0F5F1),
    inversePrimary = Color(0xFF6EE7B7),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4E2800),
    secondaryContainer = Color(0xFF5A3A1E),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF0F766E),
    onTertiaryContainer = Color(0xFFB2F5EC),
    error = Color(0xFFFF8A93),
    onError = Color(0xFF4C0007),
    errorContainer = Color(0xFF6E1A1F),
    onErrorContainer = Color(0xFFFFDADD),
    background = Color(0xFF0D1512),
    onBackground = Color(0xFFEDF3EF),
    surface = Color(0xFF141D19),
    onSurface = Color(0xFFEDF3EF),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFA9B7AF),
    surfaceContainerLowest = Color(0xFF0B120F),
    surfaceContainerLow = Color(0xFF121A16),
    surfaceContainer = Color(0xFF161F1A),
    surfaceContainerHigh = Color(0xFF1C2721),
    surfaceContainerHighest = Color(0xFF222D27),
    outline = Color(0xFF74837B),
    outlineVariant = Color(0xFF26302B),
    inverseSurface = Color(0xFFEDF3EF),
    inverseOnSurface = Color(0xFF121A16),
    inversePrimary = Color(0xFF047857),
    scrim = Color(0xFF000000),
)

/** Amber attention color (permission panels) — light variant. */
private val LightWarning = Color(0xFFC78313)

/** Amber attention color — dark variant. */
private val DarkWarning = Color(0xFFE8A83E)

/**
 * The warning tone, provided by [AppTheme]. Screens read it as
 * `LocalWarningColor.current` — never a hex literal.
 */
val LocalWarningColor = staticCompositionLocalOf { LightWarning }

// ── Type (senior scale) ───────────────────────────────────────────────────

/**
 * SeniorTypography — every role a step larger than stock M3 so the family's
 * elders read comfortably; line heights leave wrap room for বাংলা/हिन्दी/
 * العربية long words at 1.3× font scale.
 */
val SeniorTypography = Typography(
    // Hero / screen titles.
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    // Card + section headers.
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    // Row titles.
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    // Lead body copy.
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    // Default body copy.
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    // Secondary lines.
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    // Buttons + prominent labels.
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Chips + badges.
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

// ── Shape ─────────────────────────────────────────────────────────────────

/** control 10 · card 16 · featureCard 20 · dialog 22 (badge 8). */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

// ── Theme preference ──────────────────────────────────────────────────────

/**
 * Remembered day/night choice (set_theme_info's contract: "System follows
 * the phone's setting… Your choice is remembered."). The Settings rows that
 * write this arrive with the Settings wave; the shell already honors the
 * stored value on every launch.
 */
object ThemePrefBus {
    enum class Mode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "theme_pref"
    private const val KEY_MODE = "mode"

    private val _mode = MutableStateFlow(Mode.SYSTEM)

    /** The live choice (SYSTEM until the Settings rows exist). */
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** The persisted choice (defensive: an unknown stored value → SYSTEM). */
    fun current(context: Context): Mode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null) ?: Mode.SYSTEM.name
        val parsed = Mode.entries.firstOrNull { it.name == stored } ?: Mode.SYSTEM
        _mode.value = parsed
        return parsed
    }

    /** Remember the choice (Settings → Preferences → Theme rows). */
    fun persist(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }
}

// ── The theme ─────────────────────────────────────────────────────────────

/**
 * The one theme every screen composes inside. Resolves the persisted
 * theme mode against the system setting, then installs the emerald
 * palette, the senior type scale, the shape ladder and the warning tone.
 *
 * W6 (v0.51.1): the mode is collected from [ThemePrefBus]'s live flow
 * (seeded from the persisted choice), so a Settings theme row applies
 * IMMEDIATELY — not only on the next launch. Explicit [mode] callers
 * (previews) still win.
 */
@Composable
fun AppTheme(
    mode: ThemePrefBus.Mode? = null,
    content: @Composable () -> Unit,
) {
    val resolved = mode ?: run {
        val context = androidx.compose.ui.platform.LocalContext.current
        remember(context) { ThemePrefBus.current(context) } // seed the flow from prefs
        ThemePrefBus.mode.collectAsState().value
    }
    val dark = when (resolved) {
        ThemePrefBus.Mode.SYSTEM -> isSystemInDarkTheme()
        ThemePrefBus.Mode.LIGHT -> false
        ThemePrefBus.Mode.DARK -> true
    }
    CompositionLocalProvider(
        LocalWarningColor provides if (dark) DarkWarning else LightWarning
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = SeniorTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
