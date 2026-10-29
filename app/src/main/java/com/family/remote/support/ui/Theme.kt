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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════
// Phase 11.6 (v0.15.0) — the single design-system source of truth.
//
// Per docs/UI_UX_DESIGN_SYSTEM.md:
//   • ONE system (not five): Modern & Minimal blue identity + a warm
//     Family amber accent + Professional trust semantics + a real dark
//     theme + restrained playful moments.
//   • Every screen reads colors from HERE via MaterialTheme — no new
//     Color(0x…) literals anywhere else in the app.
//   • Senior-first type scale kept (sp-based, wraps — no fixed heights).
//   • Light/Dark/System preference persisted in SharedPreferences.
//
// Pure presentation: zero logic lives here.
// ═══════════════════════════════════════════════════════════════════════

/** Light theme — blue primary identity on a calm cool-neutral canvas. */
val LightColors = lightColorScheme(
    primary = Color(0xFF246BFD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF2FF),
    onPrimaryContainer = Color(0xFF0F3A99),
    secondary = Color(0xFF8F4C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF311300),
    tertiary = Color(0xFF16845B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3E4),
    onTertiaryContainer = Color(0xFF0B4A31),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF172033),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF687386),
    outline = Color(0xFFB9C2D0),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFC83C45),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4E6),
    onErrorContainer = Color(0xFF5C1116),
    inverseSurface = Color(0xFF2A3547),
    inverseOnSurface = Color(0xFFF0F4FB),
    inversePrimary = Color(0xFFA8C4FF),
    surfaceDim = Color(0xFFDCE3EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF2F5FA),
    surfaceContainerHigh = Color(0xFFECF0F6),
    surfaceContainerHighest = Color(0xFFE6EBF2),
    surfaceTint = Color(0xFF246BFD)
)

/** Dark theme — designed dark surfaces, not an inverted light theme. */
val DarkColors = darkColorScheme(
    primary = Color(0xFF6C9BFF),
    onPrimary = Color(0xFF0A2A6B),
    primaryContainer = Color(0xFF17415E),
    onPrimaryContainer = Color(0xFFD3E2FF),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4A2500),
    secondaryContainer = Color(0xFF5A3A1E),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF4CC38A),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF17503A),
    onTertiaryContainer = Color(0xFFB8F0D4),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF1F5FB),
    surface = Color(0xFF121C2B),
    onSurface = Color(0xFFF1F5FB),
    surfaceVariant = Color(0xFF1E2A3D),
    onSurfaceVariant = Color(0xFFAAB6C8),
    outline = Color(0xFF3D4A5F),
    outlineVariant = Color(0xFF263244),
    error = Color(0xFFFF8A93),
    onError = Color(0xFF5C1116),
    errorContainer = Color(0xFF5C1116),
    onErrorContainer = Color(0xFFFFD9DC),
    inverseSurface = Color(0xFFF1F5FB),
    inverseOnSurface = Color(0xFF121C2B),
    inversePrimary = Color(0xFF246BFD),
    surfaceDim = Color(0xFF0B1220),
    surfaceBright = Color(0xFF1E2A3D),
    surfaceContainerLowest = Color(0xFF0E1726),
    surfaceContainerLow = Color(0xFF121C2B),
    surfaceContainer = Color(0xFF16202F),
    surfaceContainerHigh = Color(0xFF1A2638),
    surfaceContainerHighest = Color(0xFF1E2A3D),
    surfaceTint = Color(0xFF6C9BFF)
)

/**
 * Senior-friendly type scale (Phase 9 concept, kept and formalized):
 * one notch larger body, semi-bold titles — hierarchy via size+weight,
 * never via hardcoded Bold on every Text. All sp, all wrapping.
 */
val SeniorTypography: Typography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold
        ),
        titleSmall = base.titleSmall.copy(
            fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold
        ),
        labelMedium = base.labelMedium.copy(
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium
        )
    )
}

/** Corner-radius scale — one place to tune every shape in the app. */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

/** Spacing scale (docs §6) — new code should prefer these tokens. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** Literal theme swatches used ONLY by the Settings appearance previews. */
object ThemePreview {
    val lightBg = Color(0xFFF7F9FC)
    val lightAccent = Color(0xFF246BFD)
    val darkBg = Color(0xFF0B1220)
    val darkAccent = Color(0xFF6C9BFF)
}

/** Warning semantic color — theme-aware (no dedicated M3 slot exists). */
@Composable
fun AppWarningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFE8A83E)
    } else {
        Color(0xFFC78313)
    }

// ── Theme preference (Light / Dark / System), persisted ──────────────

/**
 * Holds the user's appearance choice as observable state so both the root
 * theme wrapper AND the Settings section recompose the moment it changes.
 * The value survives restarts via SharedPreferences — a REAL setting, not
 * a fake toggle.
 */
object ThemePrefBus {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    private const val PREFS = "ui_prefs"
    private const val KEY = "theme_pref"

    var pref: String by mutableStateOf(SYSTEM)
        private set

    /** Call once in Activity.onCreate BEFORE setContent — no first-frame flash. */
    fun loadSync(context: Context) {
        pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM
    }

    /** Set + persist. Call from the Settings appearance rows. */
    fun set(context: Context, value: String) {
        pref = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
    }
}

/**
 * Root theme wrapper: resolves Light / Dark / System into a MaterialTheme.
 * Both schemes are complete (surfaces, containers, status colors), so every
 * card/dialog/sheet/nav-bar is theme-aware without per-screen conditionals.
 */
@Composable
fun FamilyRemoteSupportTheme(
    themePref: String = ThemePrefBus.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themePref) {
        ThemePrefBus.DARK -> true
        ThemePrefBus.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = SeniorTypography,
        shapes = AppShapes,
        content = content
    )
}
