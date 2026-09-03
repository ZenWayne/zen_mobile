package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal

enum class Decision { Approved, Denied }

/**
 * Blocking approve/deny gate for side-effecting tools (spec §4.1). The tool's
 * invoke runs on a JNI worker thread and blocks here until the user decides,
 * or the run is cancelled (polling keeps the cancel signal visible).
 */
class ApprovalGate {
    @Volatile
    private var decision: Decision? = null

    fun approve() {
        decision = Decision.Approved
    }

    fun deny() {
        decision = Decision.Denied
    }

    fun await(cancel: CancellationSignal, pollMillis: Long = 50): Decision {
        while (decision == null) {
            if (cancel.cancelled) return Decision.Denied
            Thread.sleep(pollMillis)
        }
        return decision!!
    }
}
