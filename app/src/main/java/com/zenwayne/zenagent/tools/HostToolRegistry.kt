package com.zenwayne.zenagent.tools

import agentflow.dsl.HostTool
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Assembles the agent's tool list: the filesystem tools over the sandbox plus
 * the user-authorized `/shared` tree (P1 + P3), and `python_run` (P2).
 *
 * The list is built once and reused — the tools are stateful (the Python
 * runner owns the interpreter and its serialization queue), so handing out
 * fresh instances per call would quietly break both.
 */
class HostToolRegistry(context: Context) {
    val gates = ConcurrentHashMap<String, ApprovalGate>()

    val workspace: FsWorkspace = FsWorkspace(
        File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" },
            "workspace",
        ).apply { mkdirs() },
    )

    /** The `/shared` grant, surfaced in Settings and consulted per tool call. */
    val sharedStorage: SharedStorageAccess = SharedStorageAccess(context)

    /** Sandbox by default, SAF tree under `/shared` (spec §8-P3). */
    val files: FsBackend = FsRouter(workspace) { sharedStorage.backend() }

    /** One interpreter per process; started lazily on the first approved run. */
    val python: PythonRunner = PythonRunner({ ChaquopyEngine(context) })

    private val toolList: List<HostTool> = listOf(
        FsReadTool(files),
        FsWriteTool(files, gates),
        FsListTool(files),
        PythonRunTool(python, gates),
    )

    fun tools(): List<HostTool> = toolList

    fun toolByName(name: String): HostTool? = toolList.firstOrNull { it.name == name }
}
