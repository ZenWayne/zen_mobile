package com.zenwayne.zenagent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.Message
import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.chat.components.AgentBubble
import com.zenwayne.zenagent.ui.chat.components.ApprovalCard
import com.zenwayne.zenagent.ui.chat.components.ErrorEventCard
import com.zenwayne.zenagent.ui.chat.components.FailureBanner
import com.zenwayne.zenagent.ui.chat.components.GeneratingNote
import com.zenwayne.zenagent.ui.chat.components.IconCircle
import com.zenwayne.zenagent.ui.chat.components.InputBar
import com.zenwayne.zenagent.ui.chat.components.RunNoteRow
import com.zenwayne.zenagent.ui.chat.components.StoppedBanner
import com.zenwayne.zenagent.ui.chat.components.SubAgentCard
import com.zenwayne.zenagent.ui.chat.components.TimestampRow
import com.zenwayne.zenagent.ui.chat.components.ToolCallCard
import com.zenwayne.zenagent.ui.chat.components.UserBubble
import com.zenwayne.zenagent.ui.theme.ZenColors

/** Chat screen orchestrator: header + scrolling message stream + input bar. */
@Composable
fun ChatScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onStop: () -> Unit = {},
    onSend: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onRestart: () -> Unit = {},
    onResume: () -> Unit = {},
    onViewLogs: () -> Unit = {},
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
                .fillMaxWidth()
                .semantics { contentDescription = TestTags.MESSAGES_LIST },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            conversation.messages.firstNotNullOfOrNull { it.timestamp }?.let { timestamp ->
                item(key = "timestamp") { TimestampRow(timestamp) }
            }
            items(conversation.messages, key = { it.id }) { msg ->
                MessageItem(
                    msg = msg,
                    onApprove = onApprove,
                    onDeny = onDeny,
                    onRetry = onRetry,
                    onRestart = onRestart,
                    onResume = onResume,
                    onViewLogs = onViewLogs,
                )
            }
        }
        InputBar(
            runState = conversation.runState,
            placeholder = inputPlaceholder(conversation),
            onSend = onSend,
            onStop = onStop,
        )
    }
}

/** FR-1 — header: chevron-left back, bot avatar, name + live status, agent-tree button. */
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
        IconCircle(
            icon = Icons.Filled.ChevronLeft,
            tint = ZenColors.Accent,
            iconSize = 24.dp,
            contentDescription = "Back",
            onClick = onBack,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ZenColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = TestTags.HEADER_AGENT_NAME },
        ) {
            Text(
                conversation.agent.name,
                color = ZenColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                RunStateUi.label(conversation.runState, conversation.hasProposal),
                color = RunStateUi.color(conversation.runState),
                fontSize = 12.sp,
                modifier = Modifier.semantics { contentDescription = TestTags.HEADER_STATUS },
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ZenColors.FieldBackground)
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = "Agent tree",
                tint = ZenColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Dispatch order per FRD: failure banner > approval > sub-agent > tool cards > bubble. */
@Composable
private fun MessageItem(
    msg: Message,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onResume: () -> Unit,
    onViewLogs: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            msg.failure != null -> if (msg.failure.canResume) {
                StoppedBanner(
                    msg.failure,
                    onRestart = onRestart,
                    onResume = onResume,
                    modifier = Modifier.semantics { contentDescription = TestTags.STOPPED_BANNER },
                )
            } else {
                FailureBanner(
                    msg.failure,
                    onViewLogs = onViewLogs,
                    onRetry = onRetry,
                    modifier = Modifier.semantics { contentDescription = TestTags.FAILURE_BANNER },
                )
            }
            msg.approval != null ->
                ApprovalCard(msg.approval, onApprove = onApprove, onDeny = onDeny)
            msg.subAgent != null -> SubAgentCard(msg.subAgent)
            msg.toolCalls.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                msg.toolCalls.forEach { tool ->
                    if (tool.errorHint != null) {
                        ErrorEventCard(tool)
                    }
                    ToolCallCard(tool)
                }
            }
            msg.role == Role.User -> UserBubble(
                msg.text.orEmpty(),
                modifier = Modifier.semantics { contentDescription = TestTags.USER_BUBBLE },
            )
            else -> AgentBubble(
                msg.text.orEmpty(),
                streaming = msg.streaming,
                modifier = Modifier.semantics { contentDescription = TestTags.AGENT_BUBBLE },
            )
        }
        if (msg.streaming && msg.role == Role.Agent) {
            GeneratingNote()
        }
        msg.runNotes.forEach { RunNoteRow(it) }
    }
}

/** FR-4.2 — placeholder text per RunState. */
private fun inputPlaceholder(conversation: Conversation): String = when (conversation.runState) {
    RunState.Running -> "Running…"
    RunState.AwaitingApproval -> "Running · messages will queue…"
    else -> "Message ${conversation.agent.name}…"
}
