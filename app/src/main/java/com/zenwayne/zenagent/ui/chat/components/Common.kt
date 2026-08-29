package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.ui.theme.ZenColors

/** Rounded-square icon chip (30dp, radius 8) used across tool / sub-agent cards. */
@Composable
fun IconChip(
    icon: ImageVector,
    background: Color,
    tint: Color = Color.White,
    size: Dp = 30.dp,
    iconSize: Dp = 16.dp,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Compact status pill: optional leading icon or indeterminate loader. */
@Composable
fun Pill(
    text: String,
    color: Color,
    background: Color,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
    textSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            LoaderDot(color, size = 9.dp)
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(text, color = color, fontSize = textSize, fontWeight = fontWeight)
    }
}

/** 40dp-high action button: outlined (borderColor set) or filled (background color). */
@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    textColor: Color,
    background: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(iconSize))
        Spacer(Modifier.size(6.dp))
        Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 36dp circular icon touch target. */
@Composable
fun IconCircle(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    background: Color = Color.Transparent,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (background != Color.Transparent) Modifier.background(background) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Tiny indeterminate spinner used as a "loader" indicator. */
@Composable
fun LoaderDot(color: Color, size: Dp = 8.dp) {
    CircularProgressIndicator(modifier = Modifier.size(size), color = color, strokeWidth = 1.dp)
}

/** Labeled detail row: fixed-width label + free-form content. */
@Composable
fun DetailRow(label: String, labelWidth: Dp = 44.dp, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = ZenColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(labelWidth),
        )
        content()
    }
}
