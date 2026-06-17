package com.zenwayne.zenagent.data

import androidx.compose.ui.graphics.Color
import com.zenwayne.zenagent.ui.theme.ZenColors

/** Lifecycle of an agent run, mirrored from the design's failure-state screens. */
enum class RunState { Idle, Running, AwaitingApproval, Succeeded, Degraded, Failed, Stopped }

/** Status of a single tool invocation inside a turn. */
enum class ToolStatus { Pending, Running, Done, Failed }

/** Author of a chat message. */
enum class Role { User, Agent }

data class Agent(
    val id: String,
    val name: String,
    val subtitle: String,
    val accent: Color,
    val glyph: String, // single-char/emoji glyph shown in the avatar
)

data class ToolCall(
    val name: String,
    val detail: String,        // e.g. "Tokyo · 5 days · Economy"
    val status: ToolStatus,
    val resultLabel: String? = null, // e.g. "Done", "Found 3", "Running"
    val isDelegate: Boolean = false, // sub-agent delegation card
)

/** A gate that pauses the run until the user approves a side-effecting tool. */
data class ApprovalRequest(
    val tool: String,
    val args: String,
    val note: String = "Agent wants to run an action with side effects",
)

data class Message(
    val id: String,
    val role: Role,
    val text: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val approval: ApprovalRequest? = null,
    val error: String? = null,
)

data class Conversation(
    val id: String,
    val agent: Agent,
    val lastMessage: String,
    val timestamp: String,
    val runState: RunState,
    val messages: List<Message> = emptyList(),
)

object ToolStatusVisuals {
    fun color(status: ToolStatus): Color = when (status) {
        ToolStatus.Done -> ZenColors.Success
        ToolStatus.Running -> ZenColors.Running
        ToolStatus.Failed -> ZenColors.Danger
        ToolStatus.Pending -> ZenColors.TextSecondary
    }
}
