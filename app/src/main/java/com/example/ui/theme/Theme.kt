package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackGoldColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = PitchBlack,
    primaryContainer = DeepGoldContainer,
    onPrimaryContainer = BrightGold,
    secondary = SignalEmerald,
    onSecondary = PitchBlack,
    secondaryContainer = SignalEmeraldContainer,
    onSecondaryContainer = SignalEmerald,
    tertiary = BrightGold,
    onTertiary = PitchBlack,
    tertiaryContainer = DeepGoldContainer,
    onTertiaryContainer = RoyalGold,
    error = AlertCoral,
    onError = Color.White,
    errorContainer = AlertCoralContainer,
    onErrorContainer = AlertCoral,
    background = PitchBlack,
    onBackground = WarmWhite,
    surface = ObsidianSurface,
    onSurface = WarmWhite,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = MutedWarmGray,
    outline = GoldBorderSubtle,
)

@Composable
fun MikrotikTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BlackGoldColorScheme,
        typography = AppTypography,
        content = content,
    )
}
