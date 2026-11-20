package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = CyberSlateDeep,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = SignalEmerald,
    onSecondary = CyberSlateDeep,
    secondaryContainer = SignalEmeraldContainer,
    onSecondaryContainer = SignalEmerald,
    tertiary = PortalAmber,
    onTertiary = CyberSlateDeep,
    tertiaryContainer = PortalAmberContainer,
    onTertiaryContainer = PortalAmber,
    error = AlertCoral,
    onError = Color.White,
    errorContainer = AlertCoralContainer,
    onErrorContainer = AlertCoral,
    background = CyberSlateDeep,
    onBackground = TextPrimaryDark,
    surface = CyberSlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CyberSlateElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = CyberSlateBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightPrimary,
    secondary = SignalEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = PortalAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    error = AlertCoral,
    onError = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
)

@Composable
fun MikrotikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
