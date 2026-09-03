package com.zenwayne.zenagent.inference

import agentflow.dsl.JsonWorkflow
import agentflow.dsl.RunEventCallback
import agentflow.dsl.cancelRun
import agentflow.dsl.freeCancelHandle
import agentflow.dsl.loadWorkflow
import agentflow.dsl.newCancelHandle
import android.content.Context
import com.zenwayne.zenagent.tools.HostToolRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Production [InferenceClient] backed by the `agentflow` DSL over JNI
 * (spec §4). Resolves the model under `getExternalFilesDir("models")` and
 * streams via [JsonWorkflow.streamTokens].
 *
 * Tool mode ([runAgentWithTools]) runs the constrained path with the fs tools
 * registered; tool lifecycle events stream live, final text arrives whole
 * (spec Q6-b).
 */
class AgentflowInferenceClient(
    private val context: Context,
    private val toolRegistry: HostToolRegistry = HostToolRegistry(context),
    private val modelFileName: String = MODEL_FILE_NAME,
) : InferenceClient {

    private val modelPath: String
        get() = File(
            requireNotNull(context.getExternalFilesDir("models")) {
                "external files dir unavailable"
            },
            modelFileName,
        ).absolutePath

    /** Single-agent workflow JSON — concise assistant, no tools (spec §5). */
    private val workflowJson: String = """
        {
          "schema_version": 1,
          "name": "zenagent-chat",
          "version": "v1",
          "state": {"kind": "dynamic_json", "fields": {}},
          "agents": {
            "main": {
              "system_prompt": "You are Zen, a helpful on-device assistant. Reply concisely in the user's language.",
              "model": {"max_output_tokens": 512},
              "tools": []
            }
          },
          "main": "main"
        }
    """.trimIndent()

    private val workflow: JsonWorkflow by lazy {
        loadWorkflow(modelPath, workflowJson)
    }

    /** Tool-mode workflow JSON (spec §4.3): constrained decoding + fs tools. */
    private val toolsWorkflowJson: String = """
        {
          "schema_version": 1,
          "name": "zenagent-tools",
          "version": "v2",
          "state": {"kind": "dynamic_json", "fields": {}},
          "agents": {
            "main": {
              "system_prompt": "You are Zen, an on-device assistant. You can read, write and list files in the workspace with fs_read/fs_write/fs_list. Reply concisely in the user's language.",
              "model": {"max_output_tokens": 512, "constrained_tool_calls": true},
              "tools": ["fs_read", "fs_write", "fs_list"]
            }
          },
          "main": "main"
        }
    """.trimIndent()

    private val toolsWorkflow: JsonWorkflow by lazy {
        loadWorkflow(modelPath, toolsWorkflowJson, toolRegistry.tools())
    }

    override fun verify(): Boolean {
        val model = File(modelPath)
        return model.exists() && model.isFile
    }

    override fun streamTokens(query: String): Flow<String> = try {
        // First reference resolves modelPath + builds the workflow (loads the
        // native engine); mapping happens here for load-time failures. The
        // emitted Flow may also throw when collected — the ViewModel maps
        // those with the same rules (see ChatViewModel).
        workflow.streamTokens(query)
    } catch (e: Throwable) {
        throw mapError(e)
    }

    override fun runAgentWithTools(query: String): Flow<RunEvent> = callbackFlow {
        val cancelId = newCancelHandle()
        val callback = object : RunEventCallback {
            override fun onToolCall(toolCallId: String, name: String, argsJson: String) {
                trySend(RunEvent.ToolCall(toolCallId, name, argsJson))
            }

            override fun onToolReturn(toolCallId: String, resultJson: String) {
                trySend(RunEvent.ToolReturn(toolCallId, resultJson))
            }
        }
        try {
            val reply = withContext(Dispatchers.IO) {
                toolsWorkflow.runConstrained(query, callback, cancelId)
            }
            trySend(RunEvent.Final(reply))
            close()
        } catch (e: Throwable) {
            close(mapError(e))
        } finally {
            freeCancelHandle(cancelId)
        }
        awaitClose { cancelRun(cancelId) }
    }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is InferenceError -> e
        is UnsatisfiedLinkError -> InferenceError.NativeUnavailable(e)
        is RuntimeException -> InferenceError.EngineInit(e)
        else -> InferenceError.EngineInit(e)
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
