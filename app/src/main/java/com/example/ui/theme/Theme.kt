package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = CrispWhite,
    primaryContainer = SkySoft,
    onPrimaryContainer = NavyDeep,
    secondary = RoyalBlue,
    onSecondary = CrispWhite,
    secondaryContainer = SkyTint,
    onSecondaryContainer = NavyDeep,
    tertiary = SkyAccent,
    onTertiary = CrispWhite,
    background = IceBackground,
    onBackground = TextDark,
    surface = CrispWhite,
    onSurface = TextDark,
    surfaceVariant = SkySoft,
    onSurfaceVariant = NavyLight,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
    error = AbsentRed,
    onError = CrispWhite,
    errorContainer = AbsentRedBg,
    onErrorContainer = AbsentRed
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyTint,
    onPrimary = NavyDeep,
    primaryContainer = NavyLight,
    onPrimaryContainer = SkySoft,
    secondary = RoyalBlue,
    onSecondary = CrispWhite,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = SkyTint,
    tertiary = SkyAccent,
    onTertiary = CrispWhite,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SkyTint,
    outline = NavyLight,
    error = AbsentRed,
    onError = CrispWhite
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BLSchoolHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
