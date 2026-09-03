package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import agentflow.dsl.HostTool
import java.util.concurrent.ConcurrentHashMap

/**
 * The three P1 filesystem tools (spec §4.2). Concurrency-safe under parallel
 * dispatch: reads are lock-free (writes are atomic temp+rename, so readers
 * never see partial content) and writes serialize on the caller side via the
 * approval gate (one gate per toolCallId).
 */
class FsReadTool(private val ws: FsWorkspace) : HostTool {
    override val name = "fs_read"
    override val description = "Read a text file from the workspace. Returns content and byte size."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.read(path)
    }
}

class FsWriteTool(
    private val ws: FsWorkspace,
    private val gates: ConcurrentHashMap<String, ApprovalGate>,
) : HostTool {
    override val name = "fs_write"
    override val description = "Write a text file into the workspace. Requires user approval."
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

class FsListTool(private val ws: FsWorkspace) : HostTool {
    override val name = "fs_list"
    override val description = "List directory entries in the workspace."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.list(path)
    }
}

// Lightweight argument extraction from the JSON args string (avoiding a JSON
// dependency): args look like {"path":"a.txt","content":"..."} with
// backslash-escaped quotes/slashes.
internal fun extractPath(argsJson: String): String? =
    Regex("\"path\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(argsJson)?.groupValues?.get(1)?.replace("\\/", "/")

internal fun extractContent(argsJson: String): String? =
    Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(argsJson)?.groupValues?.get(1)
