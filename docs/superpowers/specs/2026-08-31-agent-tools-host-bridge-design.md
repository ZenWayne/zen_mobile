# Agent Tools: Kotlin Filesystem + Python via a JNI Host-Tool Bridge

**Date:** 2026-08-31
**Status:** Design approved; ready for implementation planning
**Scope:** Two real agent tool capabilities — filesystem (read/write/list) and
Python code execution — executed by the native agent loop and surfaced through
the existing tool-call UI. The interface contract is driven by `zen_mobile`;
the bridge is implemented in `zen`.

## 1. Goal

Give ZenAgent's on-device agent two working tool capabilities:

1. **Filesystem tools** implemented in Kotlin — `fs_read`, `fs_write`,
   `fs_list` — sandboxed to a workspace directory, with authorized shared
   storage (SAF) as a later phase.
2. **Python code execution** — a `python_run` tool backed by **Chaquopy**.

Both are invoked through the **native agent loop** (constrained tool-call
decoding + dispatch), registered into the C++ `ToolRegistry` via a new
**JNI host-tool bridge**. The chat UI's tool cards, approval gate, and status
pills switch from `SampleData` to live events emitted by the run.

## 2. Background / current state

Findings from exploring `../zen` and `zen_mobile`:

- **The C++ core already has the full tool system.** `agentflow/tools/`
  provides `Tool`, `ToolRegistry`, `NativeFnTool` (any C++ callable as a
  tool). `AgentNode` runs the dispatch loop: constrained decoding
  (`constrained_tool_calls`, LLGuidance + Lark grammar) → model emits
  `tool_calls` → `DispatchTool` → `Invoke` → tool-role message back to the
  model. `EmitToolCall`/`EmitToolReturn` events already exist.
- **The loader validates tool names against a host registry.**
  `workflow_loader.cc` parses `"tools": ["name", ...]` and rejects unknown
  names ("agent 'x' references unknown tool 'y'"). The JNI entry
  (`runJsonWorkflowStreaming` in `jni/agentflow_jni.cc`) builds an **empty**
  `ToolRegistry` — so today any declared tool name fails to load.
- **The Kotlin DSL has no tool surface.** `agentflow.dsl` exposes only
  `run` / `runStreaming` / `streamTokens`; `agentflow.jni.NativeBridge`
  exposes `runAgent`, `runJsonWorkflow`, `runJsonWorkflowStreaming`, and
  cancel handles.
- **Constrained decoding is sync-only.** `runJsonWorkflowStreaming` forces
  `constrained_tool_calls = false` because the streaming C entry exists only
  for unconstrained conversations. The constrained path
  (`litert_lm_conversation_send_message_constrained`) has no streaming
  variant, and **LiteRT-LM itself does not support streaming constrained
  decoding** — so a streaming-constrained mode is an engine-level feature,
  out of scope here.
- **zen in-flight work — parallel tool dispatch.** The spec
  `zen/docs/superpowers/specs/2026-08-30-parallel-tool-dispatch-design.md`
  (approved, PR pending) changes `AgentNode`'s dispatch loop from sequential
  to concurrent within a turn, with a load-bearing contract: *tool
  concurrency-safety is the tool author's responsibility*; results are
  gathered 1:1 in original call order; failures become per-slot error
  placeholders. Our tools must be concurrency-safe from day one.
- **Model.** `gemma-4-E2B-it` has a **32k context window**. The current
  workflow JSON caps `max_output_tokens: 512` — an app config, not a model
  limit. The 32k window leaves ample room for tool results in context.
- **UI is already modeled.** `ToolCallCard`, `ApprovalCard`, four-state
  `ToolStatus`, sub-agent cards — all rendered from `SampleData` only.

## 3. Decisions (from brainstorming)

| # | Decision | Choice |
|---|----------|--------|
| Q1 | Intent | **Agent tools** (filesystem + Python), not an app-level file manager UI |
| Q2 | Filesystem scope | **Sandbox + authorized shared storage** (SAF, later phase) |
| Q3 | Python runtime | **Chaquopy** — MIT since v12.0.1 (no fee), v17.0, Python 3.10–3.14, arm64 native, numpy/pandas wheels available; no sandbox (accepted risk, §8) |
| Q4 | Bridge ownership | **zen_mobile drives the interface contract; zen implements** |
| Q5 | Overall approach | **Approach A — native loop + JNI host-tool bridge** (reuse constrained decoding + the in-flight parallel dispatch; the app stays thin) |
| Q6 | Text streaming in tool mode | **(b) constrained, non-streaming text.** Tool events stream live; the final text of each turn arrives as a whole. Streaming-constrained decoding is an engine-level non-goal |

## 4. Architecture

```
┌─ zen repo ─────────────────────────────────────────────────────────┐
│  agentflow/nodes (dispatch loop — sequential now, parallel PR      │
│                  in flight; unchanged by this spec)                │
│  agentflow/tools (ToolRegistry, NativeFnTool — reused)             │
│  jni/agentflow_jni.cc                                              │
│    ├─ NEW: register host tools (Kotlin → NativeFnTool)             │
│    ├─ NEW: async JNI upcall on a JVM worker pool (never blocks     │
│    │        the single-threaded runner)                            │
│    ├─ NEW: RunEventCallback (token + tool_call + tool_return)      │
│    └─ NEW: constrained run entry (no token stream)                 │
│  kotlin/agentflow/dsl (HostTool, RunEventCallback, loadWorkflow    │
│                        overload with tools)                        │
│  android-inference → rebuild agentflow-android.aar                 │
└──────────────────────────────────────────────┬─────────────────────┘
                                               ▼  (AAR dep)
┌─ zen_mobile repo ──────────────────────────────────────────────────┐
│  app/src/main/java/com/zenwayne/zenagent/                          │
│    tools/                                                          │
│      ├─ HostToolRegistry.kt   (builds the tool list per phase)     │
│      ├─ FsTool.kt             (fs_read / fs_write / fs_list)       │
│      ├─ PythonTool.kt         (python_run, Chaquopy — P2)          │
│      └─ ApprovalGate.kt       (blocking approve/deny on worker)    │
│    inference/                                                      │
│      └─ InferenceClient        (extended with a tool-mode run)     │
│    ui/chat/ChatViewModel       (RunEvent flow → real tool cards)   │
└────────────────────────────────────────────────────────────────────┘
```

### 4.1 JNI host-tool bridge (zen; contract driven by zen_mobile)

**Registration surface** (`agentflow.dsl`, mirrored in the AAR):

```kotlin
/** Cheap per-run cancellation probe, visible to tool implementations. */
fun interface CancellationSignal {
    val cancelled: Boolean
}

/** A tool implemented on the app side, executed by the native loop. */
interface HostTool {
    val name: String
    val description: String
    val paramsJsonSchema: String   // JSON Schema for constrained decoding
    val requiresApproval: Boolean  // side-effecting tools → true

    /**
     * Blocking invocation. Runs on a dedicated JVM worker thread owned by
     * the JNI bridge — never on the runner's single thread. Implementations
     * may bridge to suspend internally. [toolCallId] is the model-assigned
     * call id (used to correlate an approval gate with its tool-call card).
     * Return a JSON result string; return `{"error":"..."}` on failure.
     */
    fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String
}

fun loadWorkflow(
    modelPath: String,
    workflowJson: String,
    tools: List<HostTool>,
): JsonWorkflow
```

**Native side** (`jni/agentflow_jni.cc`):

Two small zen-side interface extensions carry `tool_call_id` end to end
(the dispatch loop already holds it; today it is dropped):

- `Tool::Invoke` gains a `std::string_view tool_call_id` parameter
  (mechanical: `tool.h`, `native_fn_tool.*`, `mcp_tool_adapter.*`,
  `delegate_tool.*`, `tool_registry.*`, `agent_node.cc` call sites).
- `proto/trace_event.proto`: `ToolCallPayload` and `ToolReturnPayload`
  gain `string tool_call_id = 3;`; `event.h` `EmitToolCall` /
  `EmitToolReturn` gain overloads taking the id; `agent_node.cc` emits
  the dispatch loop's `c.call_id`.

Then:

- For each `HostTool`, construct a `NativeFnTool` (schema from
  name/description/paramsJsonSchema) and register it into the `host_tools`
  registry passed to `WorkflowLoader::Load`. Names declared in the workflow
  JSON's `"tools"` must match; the loader's existing unknown-tool rejection
  is the validation.
- **Async upcall:** `NativeFnTool::Fn` posts the JNI upcall to a small JVM
  worker pool (`AttachCurrentThread`, resolved `invoke` method id cached).
  The runner coroutine awaits a completion slot while yielding the runner
  thread — so tool execution never blocks the single-threaded runner and
  parallel dispatch (in-flight PR) keeps its concurrency.
- **Cancellation:** the run's `CancelToken` propagates: on cancel the
  coroutine abandons the await and the slot is filled with
  `{"error":"cancelled"}`; the worker is flagged via `CancellationSignal`
  and its eventual result is discarded.
- **Event surface** — one callback replaces the token-only callback:

```kotlin
fun interface RunEventCallback {
    fun onToken(token: String)
    fun onToolCall(toolCallId: String, name: String, argsJson: String)
    fun onToolReturn(toolCallId: String, resultJson: String) // error → {"error":...}
}
```

  Wired via a `CallbackEventEmitter` (already documented as safe on the
  single-threaded runner) alongside the existing `TokenChannel`.

- **Constrained run entry:** a new JNI function
  `runJsonWorkflowConstrained(modelPath, json, query, eventCallback,
  cancelId)` running the constrained path. Tools are bound at
  `loadWorkflow` time (the workflow object holds the registered registry),
  so the run entry takes no tool list. No token stream; tool events stream
  live; the final assistant text is the function's return value.
  (Decision Q6: streaming-constrained decoding does not exist in LiteRT-LM.)

### 4.2 zen_mobile components

- **`tools/HostToolRegistry.kt`** — builds the `List<HostTool>` for the
  current phase (P1: fs tools; P2: + python_run).
- **`tools/FsTool.kt`** — the three filesystem tools:
  - Sandbox root: `context.getExternalFilesDir("workspace")` (zero
    permissions).
  - Path containment: canonicalize, prefix-check against the root; model
    absolute paths and `..` resolve relative to the root; escapes are
    errors.
  - `fs_read`: 512 KB cap; binary content → `{"error":"binary_file"}`.
  - `fs_write`: atomic (temp file + rename, readers never see partial
    content), single write lock (writes serialized); approval required.
  - `fs_list`: max 200 entries, `{name,type,size,mtime}` per entry.
- **`tools/PythonTool.kt`** (P2) — `python_run(code, timeout_ms=30000)`:
  - Chaquopy: `Python.start()` in `Application`; a single-worker executor
    **serializes all runs** (one interpreter → queue; this is also the
    concurrency-safety story under parallel dispatch).
  - I/O capture: wrapper redirects `sys.stdout`/`sys.stderr` to in-memory
    buffers; returns `{"stdout","stderr"}`.
  - Timeout: best-effort — the run thread is detached on timeout and
    `{"error":"timeout"}` returned; a hard kill of `while True` is not
    possible in-process (documented limitation, §8).
  - Code size cap: 100 KB. Approval required.
  - v1 ships stdlib only; numpy/pandas are a later optional build flavor.
- **`tools/ApprovalGate.kt`** — for `requiresApproval` tools, `invoke`
  blocks on the worker thread (a `CountDownLatch`) until the user decides:
  Approve → execute and return the result; Deny → `{"error":"user_denied"}`;
  run cancellation releases the latch. The gate state is what the UI's
  `ApprovalCard` binds to.
- **`inference/InferenceClient`** — the seam grows a tool-mode method:
  `fun runAgentWithTools(query: String): Flow<RunEvent>` where
  `RunEvent` = `Token | ToolCall | ToolReturn | Final(text) | Failed`.
  The existing `streamTokens` path stays untouched for non-tool chat.
- **`ui/chat/ChatViewModel` + `ChatScreen`** — collect the `RunEvent`
  flow: `ToolCall` → Running card (or `ApprovalCard` when the tool
  requires approval); `ToolReturn` → Done/Failed + result label; `Final` →
  the turn's text message. `ToolIcon` gains a `File` kind for fs tools
  (and the existing `Code` kind serves `python_run`).

### 4.3 Workflow JSON (tool mode)

```json
{
  "schema_version": 1,
  "name": "zenagent-tools",
  "version": "v2",
  "state": {"kind": "dynamic_json", "fields": {}},
  "agents": {
    "main": {
      "system_prompt": "You are Zen, an on-device assistant. You have tools for working with files in the workspace and (P2) running Python. Reply concisely in the user's language.",
      "model": {"max_output_tokens": 512},
      "tools": ["fs_read", "fs_write", "fs_list"]
    }
  },
  "main": "main"
}
```

`max_output_tokens` stays modest (512) so non-streamed turns return
quickly — it is a config knob, not a model limit (32k window).

## 5. Data flow

### Tool-mode turn (happy path)

1. `ChatViewModel` collects the `RunEvent` flow from
   `InferenceClient.runAgentWithTools(query)`.
2. JNI builds the constrained conversation with the registered
   `host_tools`; the model emits one or more `tool_calls`
   (grammar-forced well-formed; locally at most one call per turn —
   the constrained path's grammar limitation; parallel dispatch matters
   once remote/backends emit multi-call turns).
3. C++ dispatches each call: the JNI worker pool invokes the Kotlin
   `HostTool.invoke` (blocking at the approval gate when required) while
   the runner coroutine awaits asynchronously.
4. `onToolCall` → card shows Running / AwaitingApproval; user approves →
   gate opens → tool executes → result JSON crosses back → C++ appends
   the tool-role message → loop continues.
5. `onToolReturn` → card shows Done/Failed with result label.
6. Final text returned whole → `Final` event → message appended,
   `RunState.Succeeded`.

### Stop control

`nativeCancel(cancelId)` (existing) → runner coroutines abandon tool
awaits (`{"error":"cancelled"}` slots), worker threads flagged via
`CancellationSignal`; `ApprovalGate` latches release → `RunState.Stopped`.

## 6. Error handling

- **Tool failures are results, not run failures:** `invoke` returns
  `{"error":...}` strings the model sees and can react to; the dispatch
  loop's existing per-call catch ("Tool error: ...") is the backstop.
- **`user_denied`** and **`cancelled`** are ordinary error strings in the
  tool slot; the agent continues its turn.
- **Python:** `timeout` → `{"error":"timeout"}`; interpreter init failure
  surfaces as an `EngineInit`-style error before the run starts.
- **Bridge failures** (upcall throws, unknown tool at runtime) are
  contained per call; the run never crashes the app.
- **UI mapping:** `ToolStatus.Failed` + `errorHint` for error returns;
  the existing four-state card visuals are reused unchanged.

## 7. Testing

- **zen (host):** JNI bridge tests — register fake `HostTool`s, run a
  workflow JSON declaring them, assert: upcall results round-trip; event
  emission order (tool_call → tool_return); cancel mid-upcall fills
  `{"error":"cancelled"}`; unknown tool names still rejected by the
  loader; runner thread never blocked (upcall runs off-runner).
- **zen_mobile (JVM):** `FsTool` path-containment (escape attempts via
  `..`/absolute paths), size caps, atomic write; `PythonTool` queue
  serialization with a fake interpreter, timeout detach; `ApprovalGate`
  approve/deny/cancel; `ChatViewModel` RunEvent → card-state mapping with
  a scripted fake client.
- **On-device (manual):** real model + fs tools end-to-end (create/read a
  workspace file through chat); P2: `python_run` round-trip with stdout
  capture.

## 8. Scope boundaries

**In scope:**
- **P1** — zen bridge (registration + async upcall + events + constrained
  entry) → AAR rebuild → `fs_read`/`fs_write`/`fs_list` (sandbox) → live
  tool cards + approval gate for writes → workflow JSON v2.
- **P2** — `python_run` (Chaquopy, serialized queue, timeout, approval).
- **P3** — SAF shared storage (user-authorized directory exposed as
  `/shared/` root) + polish (icons, error folding, cancel semantics).

**Explicit non-goals:** streaming constrained decoding (LiteRT-LM
engine-level work); hard Python isolation (separate process / RustPython);
numpy/pandas in v1; remote execution; sub-agent execution (separate
track); an app-level file manager UI; `schema_version` bump.

## 9. Primary risks

1. **The zen bridge is new JNI surface** — worker-pool upcalls, JVM thread
   attach, async completion slots. Proven by host tests before any device
   work; the AAR must be rebuilt and shipped in lockstep with the mobile
   changes (versioned handshake).
2. **Non-streaming text UX** — each turn's text arrives whole; bounded by
   the 512 `max_output_tokens` config, and tool events provide live
   feedback. If the output cap is later raised, the wait grows.
3. **Chaquopy in-process risk** — no sandbox (Python holds app privileges,
   including the `java` module), no hard timeout, interpreter shared with
   the app process. Accepted and documented; mitigations: serialized
   queue, best-effort timeout, code cap, approval gate.
4. **Parallel dispatch concurrency** — tools are designed concurrency-safe
   from day one (atomic writes, single write lock, serialized Python
   queue) so the in-flight zen PR needs no rework here.
5. **Constrained grammar = one tool call per turn locally** — deep-search
   style fan-out won't parallelize on the local model; acceptable for v1,
   aligned with the zen parallel-dispatch spec's own caveat.

## 10. Next step

On approval of this spec, proceed to the **writing-plans** skill to
produce a phased implementation plan (P1 first: zen bridge + host tests +
AAR rebuild, then the fs tools, then UI wiring), with the zen-side and
mobile-side steps tracked as separate workstreams sharing the interface
contract in §4.1.
