package sk.menureader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

private val Green = Color(0xFF2E6B4F)

/** True in large-text ("grandpa") mode, for layouts that should stack instead of sitting side by side. */
val LocalLargeText = staticCompositionLocalOf { false }

/** Extra text scale on top of the system font size in large-text ("grandpa") mode. */
private const val LARGE_FONT_SCALE = 1.4f

@Composable
fun MenuTheme(large: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        large -> if (dark) highContrastDark else highContrastLight
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF8FD3AE))
        else -> lightColorScheme(primary = Green)
    }
    val typography = if (large) boldTypography(Typography()) else Typography()
    val density = LocalDensity.current
    val scaled = if (large) Density(density.density, density.fontScale * LARGE_FONT_SCALE) else density
    CompositionLocalProvider(LocalDensity provides scaled, LocalLargeText provides large) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Body and label text one weight heavier, so thin strokes don't disappear. */
private fun boldTypography(t: Typography): Typography {
    fun TextStyle.heavier(w: FontWeight) = copy(fontWeight = w)
    return t.copy(
        bodyLarge = t.bodyLarge.heavier(FontWeight.Medium),
        bodyMedium = t.bodyMedium.heavier(FontWeight.Medium),
        bodySmall = t.bodySmall.heavier(FontWeight.Medium),
        labelLarge = t.labelLarge.heavier(FontWeight.Bold),
        labelMedium = t.labelMedium.heavier(FontWeight.Bold),
        labelSmall = t.labelSmall.heavier(FontWeight.Bold),
        titleMedium = t.titleMedium.heavier(FontWeight.Bold),
        titleSmall = t.titleSmall.heavier(FontWeight.Bold),
        titleLarge = t.titleLarge.heavier(FontWeight.Bold),
    )
}

// High-contrast schemes: pure black/white text, strong accent, light grey cards so they stay visible.
private val highContrastLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF00391F), onPrimary = Color.White,
    primaryContainer = Color(0xFF00391F), onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF00391F), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF4A2C00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE97A), onTertiaryContainer = Color.Black,
    error = Color(0xFFA00000), onError = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color.Black,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFEDEDED), surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = Color.Black, outlineVariant = Color(0xFF444444),
)

private val highContrastDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFE14D), onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFE14D), onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFFFFE14D), onSecondaryContainer = Color.Black,
    tertiary = Color(0xFFFFD0A0), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFFFE97A), onTertiaryContainer = Color.Black,
    error = Color(0xFFFF8A80), onError = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Color.White,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A), surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color.White, outlineVariant = Color(0xFFBBBBBB),
)
