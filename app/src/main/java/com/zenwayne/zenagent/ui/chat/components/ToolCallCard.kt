package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.data.ToolCall
import com.zenwayne.zenagent.data.ToolStatus
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-2.4 — tool event card: icon 30dp tinted by status, name + detail, status pill. */
@Composable
fun ToolCallCard(tool: ToolCall, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.Surface)
            .border(1.dp, ZenColors.Hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = TestTags.TOOL_CARD },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (iconBackground, iconTint) = when (tool.status) {
            ToolStatus.Running -> ZenColors.ToolRunningBackground to ZenColors.Accent
            ToolStatus.Done -> ZenColors.ToolDoneBackground to ZenColors.Success
            ToolStatus.Failed -> ZenColors.ToolFailureBackground to ZenColors.Danger
            ToolStatus.Pending -> ZenColors.StoppedPillBackground to ZenColors.TextSecondary
        }
        IconChip(
            icon = tool.icon.vector(),
            background = iconBackground,
            tint = iconTint,
            size = 30.dp,
            iconSize = 16.dp,
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.name,
                color = ZenColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
            )
            Text(tool.detail, color = ZenColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.size(8.dp))
        ToolStatusPill(tool)
    }
}

@Composable
private fun ToolStatusPill(tool: ToolCall) {
    when (tool.status) {
        ToolStatus.Running -> Pill("Running", ZenColors.Accent, ZenColors.ToolRunningBackground, loading = true)
        ToolStatus.Done -> Pill(
            text = tool.resultLabel ?: "Done",
            color = ZenColors.Success,
            background = ZenColors.ToolDoneBackground,
            leadingIcon = Icons.Filled.Check,
        )
        ToolStatus.Failed -> Pill(
            text = tool.resultLabel ?: "Failed",
            color = ZenColors.Danger,
            background = ZenColors.ToolFailureBackground,
            leadingIcon = Icons.Filled.Warning,
        )
        ToolStatus.Pending -> Pill(
            text = tool.resultLabel ?: "Pending",
            color = ZenColors.TextSecondary,
            background = ZenColors.StoppedPillBackground,
        )
    }
}
