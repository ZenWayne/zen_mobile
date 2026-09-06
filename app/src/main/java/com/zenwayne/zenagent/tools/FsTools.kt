package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import agentflow.dsl.HostTool
import java.util.concurrent.ConcurrentHashMap

/**
 * The three filesystem tools (spec §4.2). They speak to an [FsBackend] rather
 * than the sandbox directly, so the same three tools serve both roots once
 * [FsRouter] is in front of them (P3 shared storage).
 *
 * Concurrency-safe under parallel dispatch: reads are lock-free (sandbox writes
 * are atomic temp+rename, so readers never see partial content) and writes
 * serialize on the caller side via the approval gate (one gate per toolCallId).
 */
class FsReadTool(private val ws: FsBackend) : HostTool {
    override val name = "fs_read"
    override val description = "Read a text file. Returns content and byte size."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.read(path)
    }
}

class FsWriteTool(
    private val ws: FsBackend,
    private val gates: ConcurrentHashMap<String, ApprovalGate>,
) : HostTool {
    override val name = "fs_write"
    override val description = "Write a text file. Requires user approval."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""
    override val requiresApproval = true
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        val content = extractContent(argsJson) ?: return """{"error":"missing_content"}"""
        val gate = ApprovalGate()
        gates[toolCallId] = gate
        val decision = gate.await(cancel)
        gates.remove(toolCallId)
        if (decision != Decision.Approved) return """{"error":"user_denied"}"""
        return ws.write(path, content)
    }
}

class FsListTool(private val ws: FsBackend) : HostTool {
    override val name = "fs_list"
    override val description = "List directory entries."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.list(path)
    }
}
