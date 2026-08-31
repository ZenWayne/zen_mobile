package com.zenwayne.zenagent.inference

import kotlinx.coroutines.flow.Flow

/**
 * Sealed error taxonomy for on-device inference (spec §6). Every failure path
 * in the app maps to exactly one of these so the UI can choose the right
 * design state (T5 can't-start / T3 run-failed).
 */
sealed class InferenceError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The model checkpoint is absent from device storage. */
    class ModelNotFound(path: String) :
        InferenceError("Model not found at $path (push it via adb)")

    /** Engine (LiteRT-LM) failed to initialise — e.g. incompatible .so. */
    class EngineInit(cause: Throwable) :
        InferenceError("Native engine failed to initialise", cause)

    /** Interrupted or failed inference run. */
    class InferenceFailed(cause: Throwable?) :
        InferenceError("Inference failed", cause)

    /** libagentflow_jni.so could not be loaded (missing/incompatible). */
    class NativeUnavailable(cause: Throwable) :
        InferenceError("Native engine unavailable", cause)
}

/**
 * Streaming inference seam (spec §4). One method: stream the assistant reply
 * for a user query as a cold [Flow] of text deltas. Implementations handle
 * their own model resolution; errors surface as [InferenceError]s.
 */
interface InferenceClient {
    /**
     * Resolves + verifies the model. Returns true when the model is present
     * and the engine can be constructed; throws [InferenceError] otherwise.
     */
    fun verify(): Boolean

    /**
     * Streams the assistant reply for [query] as text deltas.
     *
     * Cooperative cancellation: cancelling the collector signals the native
     * run to stop promptly — this is what backs the Stop button.
     *
     * @throws InferenceError on model/engine/run failures.
     */
    fun streamTokens(query: String): Flow<String>

    /**
     * Runs a tool-mode turn: constrained decoding, live tool lifecycle events
     * ([RunEvent.ToolCall]/[RunEvent.ToolReturn]), final text delivered whole
     * (spec Q6-b — the constrained path has no token stream). Cancellation
     * mirrors [streamTokens].
     */
    fun runAgentWithTools(query: String): Flow<RunEvent>
}
