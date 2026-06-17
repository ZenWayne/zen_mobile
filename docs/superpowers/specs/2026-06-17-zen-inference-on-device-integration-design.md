# On-Device Inference Integration: wiring the `zen` agentflow lib into `zen_mobile`

**Date:** 2026-06-17
**Status:** Design approved; ready for implementation planning
**Scope:** First thin vertical slice — streaming chat running on-device on arm64-v8a.

## 1. Goal

Make the `zen_mobile` Android app run **real on-device inference** through the
`zen` agentflow library, proving the full native pipeline
(NDK → JNI → LiteRT-LM → Compose) end-to-end on a physical arm64 phone.

This first slice deliberately implements the **smallest real slice**: one agent,
one model, **streaming chat** with a working **Stop** control. Everything else in
the design (tools, approval gate, sub-agents) builds on this foundation later.

## 2. Background / current state

Findings from exploring `../zen`:

- **The lib** (`zen/kotlin`) is a pure-**JVM** Kotlin library (`kotlin("jvm")`)
  wrapping native inference via JNI. Public API (`agentflow.dsl`):
  - `workflow { agent("id") { modelPath; systemPrompt; constrainedToolCalls } }.run(q): String`
  - `loadWorkflow(modelPath, json): JsonWorkflow` with `.run()`, `.runStreaming(onToken)`,
    and **`streamTokens(query): Flow<String>`** — a cold flow with **cooperative
    cancellation** (cancelling the collector signals native cancel and breaks the
    in-flight engine request). This maps directly onto the Chat screen's streaming
    + Stop control.
  - JNI surface (`agentflow.jni.NativeBridge`): `runAgent`, `runJsonWorkflow`,
    `runJsonWorkflowStreaming(modelPath, json, query, TokenCallback, cancelId)`,
    `nativeNewCancel/Cancel/FreeCancel`.
- **The native lib** `libagentflow_jni.so` is built by **Bazel**
  (`//jni:libagentflow_jni.so`) pulling `agentflow/{core,inference,nodes,tools,workflow}`
  + LiteRT-LM. It is currently built for the **host (x86_64 Linux)** only. There is
  **no Android/NDK/arm64 target** in the build today.
- **LiteRT-LM** is consumed as a **host prebuilt** static archive
  (`third_party/litert_lm/lib/libce_external.a`, etc., all x86_64). The full
  LiteRT-LM **source** is vendored at `zen/LiteRT-LM/` and includes
  `android_ndk_env.bzl`, Android build docs, and `prebuilt/android_arm64/` — but
  the arm64 prebuilts there are **GPU/accelerator plugins only** and are
  **unmaterialized Git LFS pointers** (git-lfs not installed). The arm64 **core**
  engine has no prebuilt and must be built from source.
- **Model:** `zen/models/gemma-4-E2B-it.litertlm` is **2.6 GB** (+788 MB
  `.xnnpack_cache`) — far too large to bundle in an APK.
- **Toolchain on this machine:** NDK **28.2.13676358** is installed at
  `/opt/android-sdk/ndk`; `ANDROID_NDK_HOME` is unset. git-lfs is not installed.
- **`zen_mobile`** currently renders the Chat/Sidebar/Settings screens from static
  `SampleData`; there is no data/inference layer yet.

## 3. Decisions (from brainstorming)

| # | Decision | Choice |
|---|----------|--------|
| Q1 | Where inference runs | **On-device (NDK)** — true to the design's "本地/local" model |
| Q2 | Functional scope | **Thin vertical slice**: single-agent streaming chat + Stop |
| Q3 | Where the Android inference lib lives | **New Android library module in the `zen` repo**, consumed by `zen_mobile` as an AAR |
| §1 | How to produce the arm64 `.so` | **Approach A** — extend zen's existing **Bazel** build with an Android NDK config; Gradle packages the Bazel output |
| §3 | ABIs | **arm64-v8a only** (both target phones); no x86_64/emulator, no GPU plugins |

## 4. Architecture

```
┌─ zen repo ────────────────────────────────────────────────┐
│  LiteRT-LM/            (source; built for arm64 via Bazel) │
│  agentflow/*           (C++ engine, reused as-is)          │
│  jni/agentflow_jni.cc  (reused; cancel symbols present)    │
│      │  bazel build //jni:libagentflow_jni.so              │
│      │     --config=android_arm64   ─────────┐             │
│  android-inference/    (NEW Gradle module)   │             │
│    ├─ build.gradle.kts (com.android.library) │             │
│    ├─ src/main/jniLibs/arm64-v8a/            ▼             │
│    │     libagentflow_jni.so  ← packaged from Bazel out    │
│    ├─ src/main/kotlin/agentflow/   (DSL ported from JVM)   │
│    │     jni/NativeBridge.kt, dsl/Workflow.kt,             │
│    │     dsl/JsonWorkflow.kt  (streamTokens: Flow<String>) │
│    └─ → produces  agentflow-android.aar                    │
└────────────────────────────────────────────────│──────────┘
                                                   ▼  (AAR dep)
┌─ zen_mobile repo ─────────────────────────────────────────┐
│  app/  (existing Compose UI)                              │
│   data/inference/                                          │
│     ├─ InferenceClient (interface)         ← seam         │
│     ├─ AgentflowInferenceClient  (wraps JsonWorkflow)     │
│     └─ ModelLocator  (resolves .litertlm path on device)  │
│   ui/chat/ChatViewModel  (collects Flow<String>)          │
│   ui/chat/ChatScreen     (existing; bind to VM state)     │
└───────────────────────────────────────────────────────────┘
```

### Components

- **zen native (reused):** `agentflow/*`, `jni/agentflow_jni.cc` compile unchanged;
  the work is toolchain config, not C++ changes.
- **zen Bazel Android config (new):** a `--config=android_arm64` (NDK toolchain +
  `platforms`/`--platforms`) that compiles `//jni:libagentflow_jni.so` for
  arm64-v8a, linking an **arm64 build of LiteRT-LM from `zen/LiteRT-LM/` source**
  (replacing the host `third_party/litert_lm` prebuilt for this config). Uses the
  in-repo `android_ndk_env.bzl`.
- **`zen/android-inference` (new Gradle module, `com.android.library`):**
  - Ports the three DSL `.kt` files (`NativeBridge`, `Workflow`, `JsonWorkflow`)
    with behavior unchanged; depends on `kotlinx-coroutines-android`.
  - Packages the Bazel-produced arm64 `.so` under `src/main/jniLibs/arm64-v8a/`
    (a Gradle task copies `bazel-bin/jni/libagentflow_jni.so`).
  - Emits `agentflow-android.aar`.
- **`zen_mobile` inference seam (new):**
  - `InferenceClient` — interface: `fun streamTokens(prompt: String): Flow<String>`
    (+ a way to surface model/engine availability).
  - `AgentflowInferenceClient` — wraps `loadWorkflow(modelPath, singleAgentJson)`
    and delegates to `JsonWorkflow.streamTokens`; maps native exceptions to
    `InferenceError`.
  - `ModelLocator` — resolves the model path under
    `getExternalFilesDir("models")`; reports missing model.
- **`zen_mobile` UI glue (new + minimal edits):**
  - `ChatViewModel` — owns chat state, runs the collect coroutine, appends deltas,
    handles Stop (cancel `Job`) and error→state mapping.
  - `ChatScreen` — existing composable bound to `ChatViewModel` state instead of
    static `SampleData`.

## 5. Data flow

### Streaming a turn (happy path)
1. `ChatScreen` input → `ChatViewModel.send(text)`.
2. VM appends a `User` message + an empty streaming `Agent` message; `RunState.Running`.
3. VM launches a coroutine: `client.streamTokens(text).collect { appendToLastAgentBubble(it) }`.
4. `AgentflowInferenceClient` → `JsonWorkflow.streamTokens` → JNI
   `runJsonWorkflowStreaming` → LiteRT-LM emits tokens → each delta crosses JNI via
   `TokenCallback` → `trySend` into the `Flow`.
5. Compose recomposes per delta (bubble text grows live).
6. Flow completes → `RunState.Succeeded`.

### Stop control
- Stop cancels the collecting coroutine → `awaitClose` → `nativeCancel(cancelId)`
  breaks the in-flight engine request → run stops promptly → `RunState.Stopped`.
  (This path already exists in the lib; the VM only cancels its `Job`.)

### Workflow definition
- A small **static single-agent JSON** string (one agent + system prompt) passed to
  `loadWorkflow(modelPath, json)`. No tools/approval/sub-agents in this slice.

### Model delivery (dev)
- Push the model via adb to app-specific external storage:
  `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.zenwayne.zenagent/files/models/`
- `ModelLocator` resolves
  `getExternalFilesDir("models")/gemma-4-E2B-it.litertlm`; if absent, the UI shows a
  "model not found" state (ties into the design's *T5 can't-start* screen).
- `.xnnpack_cache` is **regenerated on-device on first run** by default (push
  alongside only if first-run latency matters). No model in the APK; no in-app
  download in this slice.

## 6. Error handling

- JNI throws `RuntimeException` from C++ on engine-create / inference errors.
  `AgentflowInferenceClient` catches and maps to a sealed `InferenceError`
  (`ModelNotFound`, `EngineInit`, `InferenceFailed`), surfaced as `RunState.Failed`
  with a message (wires into the design's *T3 run-failed* / *T5 can't-start* states).
- `UnsatisfiedLinkError` (missing/incompatible `.so`) is caught at first use and
  shown as "native engine unavailable", not a crash.
- Cancellation is **not** an error: cancelled collect → `RunState.Stopped`.
- Native runs on `Dispatchers.IO`; the UI thread never blocks.

## 7. Testing

- **Unit / JVM:** `ChatViewModel` against a **fake `InferenceClient`** emitting a
  scripted `Flow<String>` — covers streaming append, completion, Stop/cancel, and
  error→state mapping. No NDK/model needed.
- **Native smoke (host):** keep zen's existing JVM lib test (`SmokeTest`) green so
  the C++/JNI contract is unbroken by the refactor.
- **On-device instrumented (manual/CI):** one connected-device test that loads the
  real model and asserts ≥1 token streams back; gated behind model presence so it
  skips cleanly when absent.
- **Build verification:** `bazel build //jni:libagentflow_jni.so --config=android_arm64`
  produces an arm64 ELF (`file` check); AAR packages it under `jniLibs/arm64-v8a/`.

## 8. Scope boundaries

**In scope:** Bazel arm64 config + LiteRT-LM arm64 build; `android-inference` AAR
(ported DSL + arm64 `.so`); `InferenceClient` seam + `ChatViewModel`; single-agent
streaming chat + Stop wired into the existing Chat screen; adb model delivery;
model-missing / failed states.

**Explicit non-goals (this slice):** tool-call execution; approval gate; sub-agent
delegation; x86_64/emulator; GPU/OpenCL plugins (CPU/XNNPACK only); in-app model
download; multi-conversation persistence; host-bridge transport (the `InferenceClient`
seam leaves room for it, but it is not built here).

## 9. Primary risks

1. **LiteRT-LM arm64-from-source build** is the dominant unknown — the host build
   uses a prebuilt `.a`; producing an arm64 equivalent via Bazel+NDK (protobuf/abseil
   version pins, Rust tokenizer, kissfft) is the make-or-break task. Mitigation:
   prove `bazel build //jni:libagentflow_jni.so --config=android_arm64` in isolation
   before any app wiring.
2. **On-device memory:** the E2B model on a phone may hit RAM/startup limits;
   surface failures gracefully (§6) rather than assume success.
3. **NDK version match:** LiteRT-LM may expect a specific NDK; the installed 28.2 may
   need alignment (set `ANDROID_NDK_HOME`).

## 10. Next step

On approval of this spec, proceed to the **writing-plans** skill to produce a phased
implementation plan, ordered to retire risk #1 first (native arm64 build), then the
AAR, then the app seam + ViewModel + UI binding, then on-device verification.
