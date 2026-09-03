package com.zenwayne.zenagent.tools

import agentflow.dsl.HostTool
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Assembles the P1 tool list: sandboxed fs tools (spec §4.2). */
class HostToolRegistry(context: Context) {
    val gates = ConcurrentHashMap<String, ApprovalGate>()
    val workspace: FsWorkspace = FsWorkspace(
        File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" },
            "workspace",
        ).apply { mkdirs() },
    )

    fun tools(): List<HostTool> = listOf(
        FsReadTool(workspace),
        FsWriteTool(workspace, gates),
        FsListTool(workspace),
    )

    fun toolByName(name: String): HostTool? = tools().firstOrNull { it.name == name }
}
