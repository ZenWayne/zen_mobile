package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.zenwayne.zenagent.data.SubAgentSummary
import com.zenwayne.zenagent.data.ToolCall
import com.zenwayne.zenagent.data.ToolStatus
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-2.5 — sub-agent delegation card: violet surface, header, goal, summary, tool pills. */
@Composable
fun SubAgentCard(summary: SubAgentSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.SubAgentBackground)
            .border(1.dp, ZenColors.SubAgentStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = TestTags.SUBAGENT_CARD },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                background = ZenColors.SubAgent,
                size = 30.dp,
                iconSize = 16.dp,
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    summary.label,
                    color = ZenColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                )
                Text(summary.subtitle, color = ZenColors.TextSubtle, fontSize = 11.sp, lineHeight = 15.sp)
            }
            SubAgentStatusPill(summary.status)
        }
        Text(summary.goal, color = ZenColors.TextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
        summary.summary?.let {
            Text(it, color = ZenColors.TextSubtle, fontSize = 12.sp, lineHeight = 18.sp)
        }
        summary.toolCalls.filter { it.isNested }.forEach { NestedToolRow(it) }
        val pills = summary.toolCalls.filter { !it.isNested }
        if (pills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pills.forEach { SubAgentToolPill(it) }
            }
        }
    }
}

@Composable
private fun SubAgentStatusPill(status: ToolStatus) {
    when (status) {
        ToolStatus.Running -> Pill("Running", ZenColors.Accent, ZenColors.ToolRunningBackground, loading = true)
        ToolStatus.Done -> Pill("Done", ZenColors.Success, ZenColors.ToolDoneBackground, leadingIcon = Icons.Filled.Check)
        ToolStatus.Failed -> Pill("Failed", ZenColors.Danger, ZenColors.ToolFailureBackground, leadingIcon = Icons.Filled.Warning)
        ToolStatus.Pending -> Pill("Pending", ZenColors.TextSecondary, ZenColors.StoppedPillBackground)
    }
}

/** Compact tool pill: white, radius 8, stroke #ECE8F8, icon 12 + text 11/600. */
@Composable
private fun SubAgentToolPill(tool: ToolCall) {
    val pending = tool.status == ToolStatus.Pending
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (pending) ZenColors.ApprovalBackground else ZenColors.Surface)
            .border(
                1.dp,
                if (pending) ZenColors.ApprovalStroke else ZenColors.NestedStroke,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (tool.status) {
            ToolStatus.Done -> Icon(Icons.Filled.Check, contentDescription = null, tint = ZenColors.Success, modifier = Modifier.size(12.dp))
            ToolStatus.Running -> LoaderDot(ZenColors.Accent, size = 10.dp)
            ToolStatus.Pending -> Icon(Icons.Filled.GppMaybe, contentDescription = null, tint = ZenColors.Warning, modifier = Modifier.size(12.dp))
            ToolStatus.Failed -> Icon(Icons.Filled.Warning, contentDescription = null, tint = ZenColors.Danger, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(
            if (pending) "${tool.name} · awaiting confirm" else tool.name,
            color = ZenColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (pending) {
            Spacer(Modifier.size(8.dp))
            Text("Confirm", color = ZenColors.Warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** FR-2.5.1 — nested tool row: indent 40dp, 22dp chip, name 12/600, result pill. */
@Composable
private fun NestedToolRow(tool: ToolCall) {
    Row(
        modifier = Modifier
            .padding(start = 40.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZenColors.Surface)
            .border(1.dp, ZenColors.NestedStroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (chipBackground, chipTint) = when (tool.status) {
            ToolStatus.Running -> ZenColors.ToolRunningBackground to ZenColors.Accent
            ToolStatus.Done -> ZenColors.ToolDoneBackground to ZenColors.Success
            else -> ZenColors.StoppedPillBackground to ZenColors.TextSecondary
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(chipBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon.vector(), contentDescription = null, tint = chipTint, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(
            tool.name,
            color = ZenColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        tool.resultLabel?.let {
            Spacer(Modifier.size(8.dp))
            Pill(it, ZenColors.Success, ZenColors.ToolDoneBackground)
        }
    }
}
