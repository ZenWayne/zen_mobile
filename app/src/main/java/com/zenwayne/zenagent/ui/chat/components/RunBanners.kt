package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.data.RunFailure
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-6.2 (T3) — OOM / hard failure: red banner, kind/node/detail rows, View logs + Retry. */
@Composable
fun FailureBanner(
    failure: RunFailure,
    onViewLogs: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerShell(ZenColors.FailureBackground, ZenColors.FailureStroke, modifier) {
        BannerHeader(Icons.Filled.Report, ZenColors.Danger, failure.title, failure.message, ZenColors.FailureText)
        DetailBox {
            KindPillRow("Kind", failure.kind, ZenColors.ToolFailureBackground, ZenColors.FailureText)
            failure.node?.let { node ->
                DetailRow("Node", labelWidth = 52.dp) {
                    Text(node, color = ZenColors.TextPrimary, fontSize = 13.sp)
                }
            }
            failure.detail?.let { detail ->
                DetailRow("Detail", labelWidth = 52.dp) {
                    Text(detail, color = ZenColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = "View logs",
                icon = Icons.Filled.Description,
                textColor = ZenColors.TextDim,
                background = ZenColors.Surface,
                borderColor = ZenColors.Hairline,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.ACTION_VIEW_LOGS },
                onClick = onViewLogs,
            )
            ActionButton(
                label = "Retry",
                icon = Icons.Filled.RotateLeft,
                textColor = Color.White,
                background = ZenColors.Accent,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.ACTION_RETRY },
                onClick = onRetry,
            )
        }
    }
}

/** FR-6.3 (T4) — user stopped: gray banner, Kind pill + Note row, Restart + Resume. */
@Composable
fun StoppedBanner(
    failure: RunFailure,
    onRestart: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerShell(ZenColors.StoppedBackground, ZenColors.StoppedStroke, modifier) {
        BannerHeader(Icons.Filled.StopCircle, ZenColors.TextSecondary, failure.title, failure.message, ZenColors.TextDim)
        DetailBox {
            KindPillRow("Kind", failure.kind, ZenColors.StoppedPillBackground, ZenColors.TextSubtle)
            DetailRow("Note", labelWidth = 52.dp) {
                Text(
                    "Resume re-runs the cancelled step from a clean state.",
                    color = ZenColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = "Restart",
                icon = Icons.Filled.RotateLeft,
                textColor = ZenColors.TextDim,
                background = ZenColors.Surface,
                borderColor = ZenColors.Hairline,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.ACTION_RESTART },
                onClick = onRestart,
            )
            ActionButton(
                label = "Resume",
                icon = Icons.Filled.PlayArrow,
                textColor = Color.White,
                background = ZenColors.Accent,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.ACTION_RESUME },
                onClick = onResume,
            )
        }
    }
}

@Composable
private fun BannerShell(
    background: Color,
    stroke: Color,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.5.dp, stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun BannerHeader(
    icon: ImageVector,
    chipColor: Color,
    title: String,
    message: String,
    messageColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon = icon, background = chipColor, size = 30.dp, iconSize = 16.dp)
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, color = ZenColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = messageColor, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun DetailBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZenColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun KindPillRow(rowLabel: String, value: String, background: Color, textColor: Color) {
    DetailRow(rowLabel, labelWidth = 52.dp) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(value, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
