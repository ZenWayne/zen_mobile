package com.zenwayne.zenagent.inference

import agentflow.dsl.JsonWorkflow
import agentflow.dsl.loadWorkflow
import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Production [InferenceClient] backed by the `agentflow` DSL over JNI
 * (spec §4). Resolves the model under `getExternalFilesDir("models")` and
 * streams via [JsonWorkflow.streamTokens].
 *
 * The workflow here is a minimal single-agent JSON (no tools/approval in this
 * slice — spec §5). Streaming uses the unconstrained decoding path, so tool
 * call constraints are off by design.
 */
class AgentflowInferenceClient(
    private val context: Context,
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
