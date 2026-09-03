package com.zenwayne.zenagent.inference

/** Live events for a tool-mode run (spec §4.2). No tokens in constrained mode. */
sealed class RunEvent {
    data class Token(val text: String) : RunEvent()
    data class ToolCall(val toolCallId: String, val name: String, val argsJson: String) : RunEvent()
    data class ToolReturn(val toolCallId: String, val resultJson: String) : RunEvent()
    data class Final(val text: String) : RunEvent()
}
