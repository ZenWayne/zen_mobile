package com.zenwayne.zenagent.data

import androidx.compose.ui.graphics.Color
import com.zenwayne.zenagent.ui.theme.ZenColors

/** Lifecycle of an agent run, mirrored from the design's failure-state screens. */
enum class RunState { Idle, Running, AwaitingApproval, Succeeded, Degraded, Failed, Stopped }

/** Status of a single tool invocation inside a turn. */
enum class ToolStatus { Pending, Running, Done, Failed }

/** Author of a chat message. */
enum class Role { User, Agent }

/** Semantic tool icon kind, mapped to a Material icon at render time. */
enum class ToolIcon { Search, MapPin, Hotel, Flight, Weather, Code, Warning, Generic }

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
    val isNested: Boolean = false,   // nested tool inside a sub-agent (indented)
    val icon: ToolIcon = ToolIcon.Generic,
    val errorHint: String? = null,   // e.g. "tool_error · API 503 — agent retried"
    val recovered: Boolean = false,  // tool failed then agent retried successfully
)

/** Summary of a delegated sub-agent run (FR-2.5). */
data class SubAgentSummary(
    val label: String,          // "Hotel Specialist · Sub-agent"
    val subtitle: String,       // "Depth 1 · isolated context"
    val goal: String,           // "Goal: find top-rated hotels in Shinjuku, ≤ ¥50k/night"
    val summary: String? = null, // "Narrowed 34 → 3 candidates. …"
    val status: ToolStatus = ToolStatus.Running,
    val toolCalls: List<ToolCall> = emptyList(),
)

/** Inline annotation row under a bubble (Generating Note / Partial Note / Degraded Note). */
enum class RunNoteType { Generating, Partial, Degraded }

data class RunNote(
    val type: RunNoteType,
    val text: String,
)

/** Failure / stopped banner payload (FR-6.2 / FR-6.3). */
data class RunFailure(
    val kind: String,            // "OOM" / "Stopped" (pill label)
    val title: String,           // "Run failed" / "Stopped"
    val message: String,         // "Out of memory during generation"
    val node: String? = null,    // "main · depth 0"
    val detail: String? = null,  // "KV-cache allocation failed (model too large for device)"
    val canResume: Boolean = false, // true → Resume / Restart (T4), false → Retry (T3)
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
    val streaming: Boolean = false,   // shows ▍ cursor + Generating Note while true
    val timestamp: String? = null,    // e.g. "Today 14:30", shown once per session
    val toolCalls: List<ToolCall> = emptyList(),
    val subAgent: SubAgentSummary? = null,
    val approval: ApprovalRequest? = null,
    val runNotes: List<RunNote> = emptyList(),
    val failure: RunFailure? = null,  // failure / stopped banner
    val error: String? = null,
)

data class Conversation(
    val id: String,
    val agent: Agent,
    val lastMessage: String,
    val timestamp: String,
    val runState: RunState,
    val hasProposal: Boolean = false, // Q3: true → "Sub-agent returned a proposal"
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
