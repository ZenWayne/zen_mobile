package com.zenwayne.zenagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = ZenColors.Accent,
    onPrimary = ZenColors.TextOnAccent,
    background = ZenColors.Canvas,
    onBackground = ZenColors.TextPrimary,
    surface = ZenColors.Surface,
    onSurface = ZenColors.TextPrimary,
    surfaceVariant = ZenColors.BubbleAgent,
    onSurfaceVariant = ZenColors.TextSecondary,
    outline = ZenColors.Hairline,
    error = ZenColors.Danger,
)

private val DarkScheme = darkColorScheme(
    primary = ZenColors.Accent,
    onPrimary = ZenColors.TextOnAccent,
    background = ZenColors.Ink,
    onBackground = ZenColors.TextOnDark,
    surface = ZenColors.InkElevated,
    onSurface = ZenColors.TextOnDark,
    surfaceVariant = ZenColors.InkElevated,
    onSurfaceVariant = ZenColors.TextSecondary,
    outline = ZenColors.InkHairline,
    error = ZenColors.Danger,
)

@Composable
fun ZenAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ZenTypography,
        content = content,
    )
}
