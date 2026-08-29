package com.zenwayne.zenagent.inference

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.Message
import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunFailure
import com.zenwayne.zenagent.data.RunNote
import com.zenwayne.zenagent.data.RunNoteType
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.data.SampleData
import com.zenwayne.zenagent.data.ToolCall
import com.zenwayne.zenagent.data.ToolIcon
import com.zenwayne.zenagent.data.ToolStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Owns chat state for the live conversation and drives on-device inference
 * (spec §4/§5). History conversations (SampleData design states) remain
 * reachable; sending in the live conversation wires real streaming.
 */
class ChatViewModel(
    private val client: InferenceClient,
    initialConversation: Conversation = SampleData.conversations.first(),
    private val runDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) : ViewModel() {

    private val _selected = MutableStateFlow(initialConversation)
    val selected: StateFlow<Conversation> = _selected.asStateFlow()

    private var streamingJob: Job? = null

    private var nextMessageId = 0
    private fun newId(prefix: String) = "$prefix-${nextMessageId++}"

    /** Verified model state: ready / confirmed missing. */
    var modelReady: Boolean = false
        private set
    var modelMissing: Boolean = false
        private set

    fun verifyModel() {
        val ok = client.verify()
        modelReady = ok
        modelMissing = !ok
    }

    /** Select a conversation (history or live) — cancels any in-flight run first. */
    fun select(conversation: Conversation) {
        stop()
        _selected.value = conversation
    }

    /**
     * Sends [text] in the live conversation: appends the user message, opens a
     * streaming agent bubble and collects [InferenceClient.streamTokens]
     * deltas into it. Completion → Succeeded; Stop → Stopped; error → Failed.
     */
    fun send(text: String) {
        val conv = _selected.value
        val query = text.trim()
        if (query.isEmpty() || streamingJob?.isActive == true) return

        val userMsg = Message(id = newId("user"), role = Role.User, text = query)
        val bubbleId = newId("agent")
        val streamMsg = Message(id = bubbleId, role = Role.Agent, text = "", streaming = true)

        _selected.value = conv.copy(
            runState = RunState.Running,
            lastMessage = query,
            messages = conv.messages + userMsg + streamMsg,
        )

        streamingJob = viewModelScope.launch {
            try {
                client.streamTokens(query)
                    .flowOn(runDispatcher)
                    .collect { delta ->
                        appendDelta(bubbleId, delta)
                    }
                completeBubble(bubbleId, RunState.Succeeded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failRun(bubbleId, e.message ?: "Inference failed")
            }
        }
    }

    /** Stop button: cancels the streaming job (cooperative native cancel). */
    fun stop() {
        streamingJob?.cancel()
        streamingJob = null
        val conv = _selected.value
        if (conv.runState == RunState.Running || conv.runState == RunState.AwaitingApproval) {
            _selected.value = conv.copy(
                runState = RunState.Stopped,
                messages = conv.messages.map { msg ->
                    if (msg.streaming) msg.copy(streaming = false) else msg
                } + Message(
                    id = newId("stopped"),
                    role = Role.Agent,
                    runNotes = listOf(
                        RunNote(RunNoteType.Partial, "you stopped the run — partial results kept"),
                    ),
                    failure = RunFailure(
                        kind = "Stopped",
                        title = "Stopped",
                        message = "Cancelled by user",
                        detail = "Resume re-runs the cancelled step from a clean state.",
                        canResume = true,
                    ),
                ),
            )
        }
    }

    /**
     * FR-2.6.1 / Q2 — Deny: gate becomes an un-resumable failure (Retry path).
     */
    fun denyGate() {
        val conv = _selected.value
        val denied = conv.messages.lastOrNull()?.approval ?: return
        _selected.value = conv.copy(
            runState = RunState.Failed,
            hasProposal = false,
            messages = conv.messages + Message(
                id = newId("deny"),
                role = Role.Agent,
                failure = RunFailure(
                    kind = "Denied",
                    title = "Run failed",
                    message = "Approval denied by user",
                    detail = "Tool ${denied.tool} skipped",
                    canResume = false,
                ),
            ),
        )
    }

    /**
     * FR-2.6 — Approve & run: gate resolution appends a success tool card, run continues.
     */
    fun approveGate() {
        val conv = _selected.value
        val approved = conv.messages.lastOrNull()?.approval ?: return
        _selected.value = conv.copy(
            runState = RunState.Running,
            hasProposal = false,
            messages = conv.messages + listOf(
                Message(
                    id = newId("approve-msg"),
                    role = Role.Agent,
                    text = "${approved.tool} approved — running with: ${approved.args}",
                ),
                Message(
                    id = newId("approve-tool"),
                    role = Role.Agent,
                    toolCalls = listOf(
                        ToolCall(
                            name = approved.tool,
                            detail = approved.args,
                            status = ToolStatus.Done,
                            resultLabel = "Done",
                            icon = ToolIcon.Generic,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun appendDelta(bubbleId: String, delta: String) {
        val conv = _selected.value
        _selected.value = conv.copy(
            messages = conv.messages.map { msg ->
                if (msg.id == bubbleId) msg.copy(text = (msg.text ?: "") + delta) else msg
            },
        )
    }

    private fun completeBubble(bubbleId: String, finalState: RunState) {
        val conv = _selected.value
        _selected.value = conv.copy(
            runState = finalState,
            messages = conv.messages.map { msg ->
                if (msg.id == bubbleId) msg.copy(streaming = false) else msg
            },
        )
    }

    private fun failRun(bubbleId: String, message: String) {
        val conv = _selected.value
        _selected.value = conv.copy(
            runState = RunState.Failed,
            messages = conv.messages.map { msg ->
                if (msg.id == bubbleId) msg.copy(streaming = false) else msg
            } + Message(
                id = newId("failed"),
                role = Role.Agent,
                failure = RunFailure(
                    kind = "Error",
                    title = "Run failed",
                    message = message.take(120),
                    detail = null,
                    canResume = false,
                ),
            ),
        )
    }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is InferenceError -> e
        is UnsatisfiedLinkError -> InferenceError.NativeUnavailable(e)
        is RuntimeException -> InferenceError.EngineInit(e)
        else -> e
    }

    companion object {
        /** Factory wiring the real [AgentflowInferenceClient] with the app context. */
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(AgentflowInferenceClient(context.applicationContext)) as T
            }
    }
}
