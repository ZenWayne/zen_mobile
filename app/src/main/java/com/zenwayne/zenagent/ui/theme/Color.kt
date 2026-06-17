package com.zenwayne.zenagent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette extracted from the ZenAgent Pencil design (zenagent.lib.pen).
 * iOS-styled surfaces: dark chrome (#1C1C1E), light chat canvas (#F5F5F5),
 * white cards, and an accent blue for the user's own messages / primary CTAs.
 */
object ZenColors {
    val Ink = Color(0xFF1C1C1E)          // dark chrome: sidebar, settings, headers
    val InkElevated = Color(0xFF2C2C2E)  // dark cards / list rows
    val InkHairline = Color(0xFF3A3A3C)  // dividers on dark

    val Canvas = Color(0xFFF5F5F5)       // chat background
    val Surface = Color(0xFFFFFFFF)      // white cards / input bar
    val Hairline = Color(0xFFE5E5EA)     // light borders / dividers

    val Accent = Color(0xFF0A84FF)       // primary blue (user bubble, CTA)
    val AccentPressed = Color(0xFF0060DF)

    val TextPrimary = Color(0xFF1C1C1E)
    val TextOnDark = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93) // captions, subtitles, timestamps
    val TextOnAccent = Color(0xFFFFFFFF)

    val BubbleAgent = Color(0xFFE9E9EB)  // gray agent bubble on light canvas

    // Semantic status colors for the agent run lifecycle.
    val Success = Color(0xFF34C759)
    val Running = Color(0xFF0A84FF)
    val Warning = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF3B30)

    // Tool / agent avatar accents.
    val Violet = Color(0xFF5E5CE6)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Pink = Color(0xFFFF2D55)
    val Red = Color(0xFFFF3B30)
}
