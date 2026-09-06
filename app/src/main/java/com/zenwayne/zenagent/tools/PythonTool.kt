package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import agentflow.dsl.HostTool
import java.util.concurrent.ConcurrentHashMap

/**
 * `python_run` (spec §3/§8-P2). Executing model-authored code is the most
 * side-effecting thing this app does — Python runs in-process with the app's
 * own privileges (including the `java` module), so it is gated behind the same
 * approval flow as `fs_write` and nothing reaches the interpreter until the
 * user approves.
 *
 * Concurrency-safe under parallel dispatch: [PythonRunner] serializes every
 * execution, and each call gets its own gate keyed by `toolCallId`.
 */
class PythonRunTool(
    private val runner: PythonRunner,
    private val gates: ConcurrentHashMap<String, ApprovalGate>,
) : HostTool {
    override val name = "python_run"
    override val description =
        "Run a Python 3 snippet on the device and capture its output. " +
            "Use print() to return values. Requires user approval."
    override val paramsJsonSchema =
        """{"type":"object","properties":{"code":{"type":"string"}},"required":["code"]}"""
    override val requiresApproval = true

    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val code = extractCode(argsJson) ?: return """{"error":"missing_code"}"""
        val gate = ApprovalGate()
        gates[toolCallId] = gate
        val decision = try {
            gate.await(cancel)
        } finally {
            gates.remove(toolCallId)
        }
        if (decision != Decision.Approved) return """{"error":"user_denied"}"""
        return runner.run(code)
    }
}
