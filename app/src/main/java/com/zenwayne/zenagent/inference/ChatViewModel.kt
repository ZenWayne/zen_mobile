package com.zenwayne.zenagent.inference

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.zenwayne.zenagent.data.ApprovalRequest
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
import com.zenwayne.zenagent.tools.HostToolRegistry
import com.zenwayne.zenagent.tools.SharedStorageAccess
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
    private val registry: HostToolRegistry? = null,
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
     * Tool-mode turn (spec §4.2/§5): collects the live [RunEvent] flow —
     * tool calls open Running/awaiting-approval cards, tool returns resolve
     * them, the final text lands whole (constrained mode has no token stream).
     */
    fun sendWithTools(text: String) {
        val conv = _selected.value
        val query = text.trim()
        if (query.isEmpty() || streamingJob?.isActive == true) return
        _selected.value = conv.copy(
            runState = RunState.Running,
            lastMessage = query,
            messages = conv.messages + Message(id = newId("user"), role = Role.User, text = query),
        )
        streamingJob = viewModelScope.launch {
            try {
                client.runAgentWithTools(query).flowOn(runDispatcher).collect { ev ->
                    when (ev) {
                        is RunEvent.ToolCall -> onToolCall(ev)
                        is RunEvent.ToolReturn -> onToolReturn(ev)
                        is RunEvent.Final -> {
                            appendMessage(
                                Message(id = newId("agent"), role = Role.Agent, text = ev.text),
                            )
                            _selected.value = _selected.value.copy(runState = RunState.Succeeded)
                        }
                        is RunEvent.Token -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failRun(newId("agent"), e.message ?: "Inference failed")
            }
        }
    }

    /**
     * The `/shared` grant (P3), so Settings can show and change it. Null in
     * tests and design-state previews, which run without a registry.
     */
    val sharedStorage: SharedStorageAccess?
        get() = registry?.sharedStorage

    /** Approve the pending gate for [toolCallId] (live tool-mode runs). */
    fun approve(toolCallId: String) {
        registry?.gates?.get(toolCallId)?.approve()
    }

    /** Deny the pending gate for [toolCallId] (live tool-mode runs). */
    fun deny(toolCallId: String) {
        registry?.gates?.get(toolCallId)?.deny()
    }

    private fun onToolCall(ev: RunEvent.ToolCall) {
        val needsApproval = registry?.toolByName(ev.name)?.requiresApproval == true
        val conv = _selected.value
        _selected.value = conv.copy(
            runState = if (needsApproval) RunState.AwaitingApproval else RunState.Running,
            messages = conv.messages + Message(
                id = newId("tool"),
                role = Role.Agent,
                toolCalls = listOf(
                    ToolCall(
                        name = ev.name,
                        detail = ev.argsJson,
                        status = if (needsApproval) ToolStatus.Pending else ToolStatus.Running,
                        icon = if (ev.name.startsWith("fs_")) ToolIcon.File else ToolIcon.Code,
                        toolCallId = ev.toolCallId,
                    ),
                ),
                approval = if (needsApproval) {
                    ApprovalRequest(tool = ev.name, args = ev.argsJson)
                } else {
                    null
                },
            ),
        )
    }

    private fun onToolReturn(ev: RunEvent.ToolReturn) {
        val conv = _selected.value
        val failed = ev.resultJson.contains("\"error\"")
        _selected.value = conv.copy(
            runState = RunState.Running,
            messages = conv.messages.map { msg ->
                val resolvesThis = msg.toolCalls.any { it.toolCallId == ev.toolCallId }
                msg.copy(
                    toolCalls = msg.toolCalls.map { tc ->
                        if (tc.toolCallId == ev.toolCallId) {
                            tc.copy(
                                status = if (failed) ToolStatus.Failed else ToolStatus.Done,
                                resultLabel = if (failed) "Failed" else "Done",
                                errorHint = if (failed) ev.resultJson.take(80) else null,
                            )
                        } else {
                            tc
                        }
                    },
                    approval = if (resolvesThis) null else msg.approval,
                )
            },
        )
    }

    private fun appendMessage(message: Message) {
        val conv = _selected.value
        _selected.value = conv.copy(messages = conv.messages + message)
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
        /**
         * Factory wiring the real [AgentflowInferenceClient] with the app
         * context. One [HostToolRegistry] instance is shared between the
         * client (tool registration) and the VM (approval gates).
         */
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val registry = HostToolRegistry(app)
                    return ChatViewModel(
                        AgentflowInferenceClient(app, registry),
                        registry = registry,
                    ) as T
                }
            }
    }
}
