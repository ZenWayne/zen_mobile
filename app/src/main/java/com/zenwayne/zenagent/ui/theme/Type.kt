package com.zenwayne.zenagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The design uses Inter. We map it to the platform sans-serif so the project
 * builds without bundling font files; drop Inter .ttf into res/font and swap
 * [appFontFamily] to switch to the exact typeface.
 */
private val appFontFamily = FontFamily.SansSerif

val ZenTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = appFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp,
    ),
)
