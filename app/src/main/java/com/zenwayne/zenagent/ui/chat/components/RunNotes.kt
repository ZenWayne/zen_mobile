package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
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
import com.zenwayne.zenagent.data.RunNote
import com.zenwayne.zenagent.data.RunNoteType
import com.zenwayne.zenagent.data.ToolCall
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-6.1 (T1) — self-healed tool error card: info chip, error hint, "recovered" pill. */
@Composable
fun ErrorEventCard(tool: ToolCall, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.Surface)
            .border(1.dp, ZenColors.ErrorEventStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = TestTags.ERROR_EVENT_CARD },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = Icons.Filled.Info,
                background = ZenColors.ToolRunningBackground,
                tint = ZenColors.Accent,
                size = 30.dp,
                iconSize = 16.dp,
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.name, color = ZenColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                tool.errorHint?.let {
                    Text(it, color = ZenColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
            Pill("recovered", ZenColors.Accent, ZenColors.ToolRunningBackground)
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = "Collapse details",
                tint = ZenColors.TextDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** FR-3.1 / FR-6.2 / FR-6.3 inline note row under a bubble (loader / zap-off / corner-down-right). */
@Composable
fun RunNoteRow(note: RunNote, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = TestTags.RUN_NOTE },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (note.type) {
            RunNoteType.Generating -> {
                LoaderDot(ZenColors.Accent, size = 10.dp)
                Spacer(Modifier.size(6.dp))
                Text(note.text, color = ZenColors.Accent, fontSize = 11.sp)
            }
            RunNoteType.Partial -> {
                Icon(Icons.Filled.FlashOff, contentDescription = null, tint = ZenColors.TextMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(6.dp))
                Text(note.text, color = ZenColors.TextMuted, fontSize = 11.sp)
            }
            RunNoteType.Degraded -> {
                Icon(Icons.Filled.SubdirectoryArrowRight, contentDescription = null, tint = ZenColors.TextMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(6.dp))
                Text(note.text, color = ZenColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
}
