package com.zenwayne.zenagent.ui.chat

import androidx.compose.ui.graphics.Color
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-1.3 status text + color per RunState (with Q3 proposal priority).
 *  Degraded shows "Online" per design g1Cj1o (T1+T2 self-healed run, FRD §6.1). */
object RunStateUi {
    fun label(state: RunState, hasProposal: Boolean = false): String = when (state) {
        RunState.Running -> "Generating…"
        RunState.AwaitingApproval -> "Awaiting approval…"
        RunState.Succeeded -> if (hasProposal) "Sub-agent returned a proposal" else "Online"
        RunState.Degraded -> "Online"
        RunState.Failed -> "Run failed"
        RunState.Stopped -> "Stopped"
        RunState.Idle -> "Online"
    }

    fun color(state: RunState): Color = when (state) {
        RunState.Running -> ZenColors.Accent
        RunState.AwaitingApproval -> ZenColors.Warning
        RunState.Succeeded,
        RunState.Degraded,
        RunState.Idle -> ZenColors.Success
        RunState.Failed -> ZenColors.Danger
        RunState.Stopped -> ZenColors.TextSecondary
    }
}
