package sk.menureader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp

/** True in large-text ("grandpa") mode, for layouts that should stack instead of sitting side by side. */
val LocalLargeText = staticCompositionLocalOf { false }

/** Extra text scale on top of the system font size in large-text mode. */
private const val LARGE_FONT_SCALE = 1.4f

// Upbeat palette: paprika orange, mint, mustard, on a warm cream background.
private val appLight = lightColorScheme(
    primary = Color(0xFFC2410C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC), onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF1F7A5C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0DF), onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFF8A6A00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A), onTertiaryContainer = Color(0xFF271D00),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFFE1DC), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F3), onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF5DED3), onSurfaceVariant = Color(0xFF53433C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFCEBDF), surfaceContainerHigh = Color(0xFFF8E4D7),
    surfaceContainerHighest = Color(0xFFF3DED0),
    outline = Color(0xFF85736B), outlineVariant = Color(0xFFD8C2B8),
)

private val appDark = darkColorScheme(
    primary = Color(0xFFFFB59A), onPrimary = Color(0xFF5A1A00),
    primaryContainer = Color(0xFF7F2B00), onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF8FD5B8), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513D), onSecondaryContainer = Color(0xFFC8F0DF),
    tertiary = Color(0xFFF2C14E), onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4400), onTertiaryContainer = Color(0xFFFFE08A),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5C1712), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120F), onBackground = Color(0xFFF1DFD7),
    surface = Color(0xFF1A120F), onSurface = Color(0xFFF1DFD7),
    surfaceVariant = Color(0xFF53433C), onSurfaceVariant = Color(0xFFD8C2B8),
    surfaceContainerLowest = Color(0xFF140D0A), surfaceContainerLow = Color(0xFF221A16),
    surfaceContainer = Color(0xFF271E1A), surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D332E),
    outline = Color(0xFFA08D85), outlineVariant = Color(0xFF53433C),
)

private val roundShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MenuTheme(large: Boolean = false, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        large -> if (dark) highContrastDark else highContrastLight
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> appDark
        else -> appLight
    }
    val typography = if (large) boldTypography(Typography()) else Typography()
    val density = LocalDensity.current
    val scaled = if (large) Density(density.density, density.fontScale * LARGE_FONT_SCALE) else density
    CompositionLocalProvider(LocalDensity provides scaled, LocalLargeText provides large) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = roundShapes, content = content)
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
        headlineSmall = t.headlineSmall.heavier(FontWeight.Bold),
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
    errorContainer = Color(0xFFFFD6D6), onErrorContainer = Color.Black,
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
    errorContainer = Color(0xFF4A0000), onErrorContainer = Color.White,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Color.White,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A), surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color.White, outlineVariant = Color(0xFFBBBBBB),
)
