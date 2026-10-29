package dev.lfm.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deliberately monochrome, independent of system wallpaper and light/dark mode.
private val Monochrome = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF303030), onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFF333333),
    secondary = Color(0xFFD0D0D0), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF292929), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF202020), onTertiaryContainer = Color(0xFFE8E8E8),
    background = Color(0xFF080808), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF101010), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF292929), onSurfaceVariant = Color(0xFFC7C7C7),
    surfaceTint = Color.White,
    inverseSurface = Color(0xFFE5E5E5), inverseOnSurface = Color(0xFF171717),
    error = Color.White, onError = Color.Black,
    errorContainer = Color(0xFF383838), onErrorContainer = Color.White,
    outline = Color(0xFF888888), outlineVariant = Color(0xFF454545),
    scrim = Color.Black,
    surfaceBright = Color(0xFF393939), surfaceDim = Color(0xFF080808),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF151515),
    surfaceContainer = Color(0xFF1D1D1D), surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF303030),
)

@Composable
fun LocalAgentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Monochrome, content = content)
}
