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

    // Chat run-lifecycle surfaces (FRD §6.1).
    val SubAgent = Color(0xFF5856D6)          // sub-agent brand violet (chip)
    val SubAgentBackground = Color(0xFFF2F0FB)
    val SubAgentStroke = Color(0xFFE2DCF6)
    val ApprovalBackground = Color(0xFFFFF8EC)
    val ApprovalStroke = Color(0xFFFFE0A8)
    val FailureBackground = Color(0xFFFDEEEE)
    val FailureStroke = Color(0xFFF5C6C6)
    val StoppedBackground = Color(0xFFF0F0F2)
    val StoppedStroke = Color(0xFFDEDEE3)
    val ErrorEventStroke = Color(0xFFD6E4FF)

    // Tool status tinted surfaces.
    val ToolRunningBackground = Color(0xFFE9F1FF)
    val ToolDoneBackground = Color(0xFFE9F9EF)
    val ToolFailureBackground = Color(0xFFFDE7E7)
    val StoppedPillBackground = Color(0xFFEDEDF0)
    val NestedStroke = Color(0xFFECE8F8)

    // Input / icon surfaces.
    val FieldBackground = Color(0xFFF2F2F7)
    val AddButtonBackground = Color(0xFFE8E8EA)

    // Fine-grained text tones.
    val TextSubtle = Color(0xFF6C6C70)        // sub-agent subtitle / summary
    val TextDim = Color(0xFF636366)           // muted buttons / stopped banner text
    val TextDisabled = Color(0xFFC7C7CC)      // placeholders, collapsed chevron
    val TextMuted = Color(0xFFB0B0B5)         // degraded / partial note rows
    val WarningText = Color(0xFF9A7B3A)       // approval note text
    val FailureText = Color(0xFF9A3A3A)       // failure banner message text

    // Error-state screens (FR-6.4 T5 / FR-6.5 T6).
    val Cta = Color(0xFF007AFF)          // primary CTA fill
    val Amber = Color(0xFFFF9500)        // amber hero / warning icons
    val OrangeSoft = Color(0xFFFFF1E0)   // amber hero / issue-1 chip
    val VioletSoft = Color(0xFFEFEAFB)   // violet hero / kind pill
    val AmberText = Color(0xFF8E6A2B)    // data-loss warning text
    val CardBorder = Color(0xFFEAEAEC)   // error card stroke
    val DividerSoft = Color(0xFFEEEEF0)  // issue row separator
    val ButtonBorder = Color(0xFFE0E0E3) // secondary button stroke
}
