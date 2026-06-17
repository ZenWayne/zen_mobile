package com.zenwayne.zenagent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zenwayne.zenagent.data.ApprovalRequest
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.Message
import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.data.ToolCall
import com.zenwayne.zenagent.data.ToolStatusVisuals
import com.zenwayne.zenagent.ui.theme.ZenColors

@Composable
fun ChatScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenColors.Canvas),
    ) {
        ChatHeader(conversation, onBack)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(conversation.messages, key = { it.id }) { msg ->
                MessageItem(msg, onApprove = onApprove, onDeny = onDeny)
            }
        }
        InputBar(runState = conversation.runState, onStop = onStop)
    }
}

@Composable
private fun ChatHeader(conversation: Conversation, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZenColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.AutoMirrored.Filled.ArrowBack, tint = ZenColors.Accent, onClick = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                conversation.agent.name,
                color = ZenColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                runStateLabel(conversation.runState),
                color = runStateColor(conversation.runState),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconCircle(Icons.Filled.MoreHoriz, tint = ZenColors.Accent, onClick = {})
    }
}

@Composable
private fun MessageItem(msg: Message, onApprove: () -> Unit, onDeny: () -> Unit) {
    when {
        msg.approval != null -> ApprovalCard(msg.approval, onApprove = onApprove, onDeny = onDeny)
        msg.toolCalls.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            msg.toolCalls.forEach { ToolCallCard(it) }
        }
        msg.role == Role.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Bubble(text = msg.text.orEmpty(), background = ZenColors.Accent, textColor = Color.White)
        }
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Bubble(text = msg.text.orEmpty(), background = ZenColors.BubbleAgent, textColor = ZenColors.TextPrimary)
        }
    }
}

@Composable
private fun Bubble(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ToolCallCard(tool: ToolCall) {
    val statusColor = ToolStatusVisuals.color(tool.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZenColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (tool.isDelegate) ZenColors.Violet else ZenColors.Hairline),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (tool.isDelegate) "↳" else "{}",
                color = if (tool.isDelegate) Color.White else ZenColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.name, color = ZenColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(tool.detail, color = ZenColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        tool.resultLabel?.let { label ->
            Spacer(Modifier.size(8.dp))
            StatusPill(label, statusColor)
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ApprovalCard(approval: ApprovalRequest, onApprove: () -> Unit, onDeny: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZenColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ZenColors.Warning),
            )
            Spacer(Modifier.size(8.dp))
            Text("Approval required", color = ZenColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Text(approval.note, color = ZenColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        KeyValue("Tool", approval.tool)
        KeyValue("Args", approval.args)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedAction(
                label = "Deny",
                icon = Icons.Filled.Close,
                color = ZenColors.Danger,
                modifier = Modifier.weight(1f),
                onClick = onDeny,
            )
            FilledAction(
                label = "Approve & run",
                icon = Icons.Filled.Check,
                color = ZenColors.Accent,
                modifier = Modifier.weight(1f),
                onClick = onApprove,
            )
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row {
        Text("$key  ", color = ZenColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Text(value, color = ZenColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OutlinedAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FilledAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InputBar(runState: RunState, onStop: () -> Unit) {
    val running = runState == RunState.Running || runState == RunState.AwaitingApproval
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZenColors.Surface)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.Filled.Add, tint = ZenColors.TextSecondary, onClick = {})
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(ZenColors.Canvas)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                if (running) "Running… messages will queue" else "Message…",
                color = ZenColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (running) ZenColors.Danger else ZenColors.Accent)
                .clickable(enabled = running, onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.Filled.Add,
                contentDescription = if (running) "Stop" else "Send",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun IconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private fun runStateLabel(state: RunState): String = when (state) {
    RunState.Idle -> "Idle"
    RunState.Running -> "Running…"
    RunState.AwaitingApproval -> "Awaiting approval…"
    RunState.Succeeded -> "Done"
    RunState.Degraded -> "Completed with warnings"
    RunState.Failed -> "Run failed"
    RunState.Stopped -> "Stopped"
}

private fun runStateColor(state: RunState): Color = when (state) {
    RunState.Running, RunState.AwaitingApproval -> ZenColors.Running
    RunState.Succeeded -> ZenColors.Success
    RunState.Degraded -> ZenColors.Warning
    RunState.Failed -> ZenColors.Danger
    RunState.Stopped -> ZenColors.TextSecondary
    RunState.Idle -> ZenColors.TextSecondary
}
