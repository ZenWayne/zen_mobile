# Agent Tools（文件系统）— JNI Host-Tool 桥实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 ZenAgent 的本地 Agent 具备真实文件系统工具能力（`fs_read`/`fs_write`/`fs_list`），由原生 Agent 循环（约束解码 + dispatch）驱动，UI 工具卡与审批门由真实事件驱动。

**Architecture:** 方案 A — 原生循环 + JNI host-tool 桥。zen 侧新增「Kotlin 工具注册进 C++ ToolRegistry → worker 线程池异步上行调用 → 工具生命周期事件回流」的桥；mobile 侧实现并发安全的文件系统工具与审批门。**本计划只覆盖 spec 的 P1**；P2（Python/Chaquopy）与 P3（SAF 共享存储）各出独立后续计划。

**Tech Stack:** C++17（Bazel）/ JNI / Kotlin 2.0.21 / AGP 8.7.3 / Jetpack Compose BOM 2024.12.01 / kotlinx-coroutines。

**Spec:** `docs/superpowers/specs/2026-08-31-agent-tools-host-bridge-design.md`

## Global Constraints

- 工具必须**并发安全**（zen 并行 dispatch PR 契约：同轮多工具并发调用，结果按原顺序 1:1 回填）。
- 工具失败 = 结果而非运行失败：`invoke` 返回 `{"error": "..."}` 串，模型可见并自行应对。
- 审批语义：`fs_write` `requiresApproval=true`；Deny → 返回 `{"error":"user_denied"}`；Stop 取消 → 门必须释放。
- 沙箱：根 = `getExternalFilesDir("workspace")`；读上限 512KB；列目录上限 200 条；写必须原子（临时文件+rename）。
- 工具模式**文本不流式**（LiteRT-LM 约束解码无流式变体，spec Q6-b）：token 不出现在工具模式下，工具事件实时流，最终文本整块返回。
- workflow JSON：`"tools": ["fs_read","fs_write","fs_list"]` + `"constrained_tool_calls": true` + `max_output_tokens: 512`（配置值，非模型限制；gemma-4-E2B-it 窗口 32k）。
- ABI：仅 arm64-v8a。禁止 `as any` / `@ts-ignore` 类抑制；Kotlin 文件 ≤250 行（现有仓库惯例）。
- 与 zen 并行 dispatch PR（`zen/docs/superpowers/specs/2026-08-30-parallel-tool-dispatch-design.md`，在途）的合并协调：Task 1/2 改动的 `agent_node.cc` dispatch 循环是同一代码区，合并时需保留 `tool_call_id` 透传（见 Task 1 注意事项）。

---

## 文件结构总览

**zen 仓库（桥工作流，Task 1–3）：**
- `proto/trace_event.proto` — ToolCallPayload/ToolReturnPayload 增 `tool_call_id = 3`
- `agentflow/core/event.h`（+`event.cc`）— EmitToolCall/EmitToolReturn 增带 id 的重载
- `agentflow/nodes/agent_node.cc` — dispatch 循环发射 call_id；Invoke 透传 id
- `agentflow/tools/{tool.h, native_fn_tool.*, tool_registry.*, mcp_tool_adapter.*}` — Invoke 签名增 id
- `agentflow/workflow/delegate_tool.*` — 同上（机械）
- `jni/agentflow_jni.cc` — worker 池、MakeHostTool 上行调用、RunSignal 翻转、JniEventEmitter、`runJsonWorkflowConstrained`
- `kotlin/src/main/kotlin/agentflow/dsl/HostTool.kt`（新）— HostTool/CancellationSignal/RunEventCallback/RunSignal
- `kotlin/src/main/kotlin/agentflow/dsl/JsonWorkflow.kt` — loadWorkflow 重载 + runConstrained
- `kotlin/src/main/kotlin/agentflow/jni/NativeBridge.kt` — runJsonWorkflowConstrained external
- `tests/unit/nodes/agent_node_test.cc`、`kotlin/src/test/kotlin/agentflow/HostToolBridgeTest.kt`（新）

**zen 仓库（AAR 镜像，Task 4）：**
- `android-inference/src/main/kotlin/agentflow/dsl/HostTool.kt`（新）、`dsl/JsonWorkflow.kt`、`jni/NativeBridge.kt` — Task 3 DSL 改动的镜像副本

**zen_mobile 仓库（mobile 工作流，Task 5–8）：**
- `app/src/main/java/com/zenwayne/zenagent/tools/FsWorkspace.kt`（新）、`tools/FsTools.kt`（新）
- `app/src/main/java/com/zenwayne/zenagent/tools/ApprovalGate.kt`（新）、`tools/HostToolRegistry.kt`（新）
- `app/src/main/java/com/zenwayne/zenagent/inference/RunEvent.kt`（新）
- `app/src/main/java/com/zenwayne/zenagent/inference/InferenceClient.kt`、`inference/AgentflowInferenceClient.kt`（改）
- `app/src/main/java/com/zenwayne/zenagent/inference/ChatViewModel.kt`（改）
- `app/src/main/java/com/zenwayne/zenagent/data/Models.kt`（改：ToolCall.toolCallId、ToolIcon.File）
- `app/src/main/java/com/zenwayne/zenagent/ui/chat/components/ToolIcons.kt`（改）
- 测试：`app/src/test/java/com/zenwayne/zenagent/tools/{FsWorkspaceTest,ApprovalGateTest}.kt`（新）、`inference/ChatViewModelTest.kt`（扩）

---

### Task 1: zen — trace 事件携带 tool_call_id

**Files:**
- Modify: `proto/trace_event.proto`
- Modify: `agentflow/core/event.h`、`agentflow/core/event.cc`
- Modify: `agentflow/nodes/agent_node.cc`（dispatch 循环两处发射点，约 268/275/281/288 行）
- Test: `tests/unit/nodes/agent_node_test.cc`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: `proto::TraceEvent.tool_call().tool_call_id()` / `.tool_return().tool_call_id()`；`EventEmitter::EmitToolCall(node_id, tool_name, args_json, tool_call_id)`（旧三参签名保留并委托，避免全库 churn）

- [ ] **Step 1: 写失败测试**

在 `tests/unit/nodes/agent_node_test.cc` 中（该文件已有 `EventCapture` / `RegistryWith` / `testing::FakeChatBackend` 模式，仿 `ToolCallIsDispatchedAndItsIdEchoedBack`）：先给 `EventCapture` 加一个访问器（现有 `tokens()` 旁）：

```cpp
  std::vector<proto::TraceEvent> all() {
    std::lock_guard<std::mutex> l(m);
    return events;
  }
```

再新增用例（注意：此时 Task 2 尚未做，`NativeFnTool` 的 Fn 仍是两参 `(std::string_view, const CancelToken&)`）：

```cpp
TEST(AgentNodeTest, ToolCallEventCarriesCallId) {
  asio::io_context io;
  auto backend = std::make_shared<testing::FakeChatBackend>(
      std::vector<std::string>{
          R"({"role":"assistant","tool_calls":[)"
          R"({"id":"call_42","function":{"name":"echo","arguments":"{\"x\":1}"}}]})",
          R"({"role":"assistant","content":[{"type":"text","text":"done"}]})"});

  auto registry = std::make_shared<ToolRegistry>();
  registry->Register(std::make_shared<NativeFnTool>(
      ToolSchema{.name = "echo",
                 .description = "test tool",
                 .params_json_schema = R"({"type":"object","properties":{}})"},
      [](std::string_view, const CancelToken&)
          -> asio::awaitable<std::string> { co_return R"({"ok":true})"; }));

  auto cfg = BaseConfig(backend, io);
  cfg.tool_registry = registry;
  cfg.constrained_tool_calls = false;

  EventCapture cap;
  RunNode(std::move(cfg), "go", io, cap);

  const auto& events = cap.all();
  const auto tc = std::find_if(events.begin(), events.end(),
      [](const auto& e) { return e.has_tool_call(); });
  ASSERT_NE(tc, events.end());
  EXPECT_EQ(tc->tool_call().tool_call_id(), "call_42");
  const auto tr = std::find_if(events.begin(), events.end(),
      [](const auto& e) { return e.has_tool_return(); });
  ASSERT_NE(tr, events.end());
  EXPECT_EQ(tr->tool_return().tool_call_id(), "call_42");
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /home/wayne/tools/zen && bazel test //tests/unit/nodes:agent_node_test --test_filter=ToolCallEventCarriesCallId`
Expected: 编译失败（`tool_call_id` 不存在 / Fn 三参不匹配）

- [ ] **Step 3: 最小实现**

`proto/trace_event.proto`（两处 payload）：

```proto
message ToolCallPayload {
  string tool_name = 1;
  string args_json = 2;
  string tool_call_id = 3;
}
message ToolReturnPayload {
  string tool_name = 1;
  string result_json = 2;
  string tool_call_id = 3;
}
```

`agentflow/core/event.h`（保留旧签名，新增重载）：

```cpp
  void EmitToolCall(std::string_view node_id, std::string_view tool_name,
                    std::string_view args_json);
  void EmitToolCall(std::string_view node_id, std::string_view tool_name,
                    std::string_view args_json, std::string_view tool_call_id);
  void EmitToolReturn(std::string_view node_id, std::string_view tool_name,
                      std::string_view result_json);
  void EmitToolReturn(std::string_view node_id, std::string_view tool_name,
                      std::string_view result_json, std::string_view tool_call_id);
```

`agentflow/core/event.cc`：三参版本委托四参版本（传空 id）；四参版本在构造 `proto::TraceEvent` 时 `mutable_tool_call()->set_tool_call_id(...)`（同理 tool_return）。

`agentflow/nodes/agent_node.cc` dispatch 循环：`emit.EmitToolCall(Id(), name, args)` → `emit.EmitToolCall(Id(), name, args, c.call_id)`；`emit.EmitToolReturn(Id(), name, result)` → 带 id 版本（该处 `c.call_id` 若已 move，用 `calls[i].call_id` 对齐，见现文件 224–238 行的 1:1 回填循环——注意确保发射点能拿到对应调用的 id）。

> **⚠ 并行 dispatch PR 协调**：该 PR 重写同一 dispatch 循环（`co_spawn` + 按序 gather）。若其先合入，则新循环内每个 `EmitToolCall`/`EmitToolReturn` 同样要带上该调用的 call_id（PR 的 1:1 结果数组已有 id）；若本任务先合入，PR rebase 时保留即可。

- [ ] **Step 4: 运行确认通过**

Run: `bazel test //tests/unit/nodes:agent_node_test --test_filter=ToolCallEventCarriesCallId`
Expected: PASS；并全量 `bazel test //tests/unit/nodes:agent_node_test` 确认旧用例不回归

- [ ] **Step 5: 提交**

```bash
git add proto/trace_event.proto agentflow/core/event.h agentflow/core/event.cc \
        agentflow/nodes/agent_node.cc tests/unit/nodes/agent_node_test.cc
git commit -m "feat(trace): thread tool_call_id through tool_call/tool_return events"
```

---

### Task 2: zen — Tool::Invoke 透传 tool_call_id

**Files:**
- Modify: `agentflow/tools/tool.h`、`agentflow/tools/native_fn_tool.{h,cc}`、`agentflow/tools/tool_registry.{h,cc}`、`agentflow/tools/mcp_tool_adapter.{h,cc}`、`agentflow/workflow/delegate_tool.{h,cc}`
- Modify: `agentflow/nodes/agent_node.cc`（`DispatchTool` 两处 Invoke 调用点）
- Test: 受影响既有测试（grep `Invoke(` 全库，机械更新）

**Interfaces:**
- Consumes: Task 1 无依赖（纯机械重构）
- Produces: `Tool::Invoke(std::string_view args_json, std::string_view tool_call_id, const CancelToken& cancel)`；`ToolRegistry::Invoke(name, args_json, tool_call_id, cancel)`；`NativeFnTool::Fn = std::function<asio::awaitable<std::string>(std::string_view, std::string_view, const CancelToken&)>`

- [ ] **Step 1: 机械改动（无新行为，编译即测）**

`tool.h`：

```cpp
  virtual asio::awaitable<std::string> Invoke(
      std::string_view args_json,
      std::string_view tool_call_id,   // NEW — 模型分配的 call id
      const CancelToken& cancel) = 0;
```

`native_fn_tool.h`：

```cpp
  using Fn = std::function<asio::awaitable<std::string>(
      std::string_view, std::string_view, const CancelToken&)>;
```

`native_fn_tool.cc`：`Invoke(args, id, cancel)` → `fn_(args, id, cancel)`。

`mcp_tool_adapter.*`、`delegate_tool.*`：签名加中间参数并忽略（`(void)tool_call_id` 或直接不使用）。

`tool_registry.h/.cc`：`Invoke(name, args, id, cancel)`，内部透传给注册的 tool。

`agentflow/nodes/agent_node.cc` `DispatchTool`：

```cpp
    result = co_await tool->Invoke(args, call_id, cancel);
    ...
    result = co_await cfg_.tool_registry->Invoke(name, args, call_id, cancel);
```

`DispatchTool` 签名需加 `std::string_view call_id` 参数，其调用处（顺序与并行 dispatch 共用）传入 `c.call_id`。

- [ ] **Step 2: 全量编译 + 受影响测试**

Run: `cd /home/wayne/tools/zen && bazel build //... && bazel test //tests/...`
Expected: 编译通过；grep 找到的所有 `->Invoke(` 调用点均已更新（无遗漏编译错误）

- [ ] **Step 3: 提交**

```bash
git add agentflow/tools agentflow/workflow/delegate_tool.h agentflow/workflow/delegate_tool.cc \
        agentflow/nodes/agent_node.cc
git commit -m "refactor(tools): pass tool_call_id through Tool::Invoke"
```

---

### Task 3: zen — JNI 桥 + Kotlin DSL 契约

**Files:**
- Modify: `jni/agentflow_jni.cc`
- Create: `kotlin/src/main/kotlin/agentflow/dsl/HostTool.kt`
- Modify: `kotlin/src/main/kotlin/agentflow/dsl/JsonWorkflow.kt`、`kotlin/src/main/kotlin/agentflow/jni/NativeBridge.kt`
- Test: `kotlin/src/test/kotlin/agentflow/HostToolBridgeTest.kt`（新，模型门控）

**Interfaces:**
- Consumes: Task 2 的 `Tool::Invoke(..., tool_call_id, ...)`；Task 1 的带 id 事件
- Produces（mobile 侧 Task 5–8 依赖的契约，**签名即接口**）：

```kotlin
// agentflow.dsl
fun interface CancellationSignal { val cancelled: Boolean }
interface HostTool {
    val name: String
    val description: String
    val paramsJsonSchema: String
    val requiresApproval: Boolean
    fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String
}
interface RunEventCallback {  // 三方法均带默认实现
    fun onToken(token: String) {}
    fun onToolCall(toolCallId: String, name: String, argsJson: String) {}
    fun onToolReturn(toolCallId: String, resultJson: String) {}
}
fun loadWorkflow(modelPath: String, json: String, tools: List<HostTool>): JsonWorkflow
fun JsonWorkflow.runConstrained(userQuery: String, events: RunEventCallback, cancelId: Long): String
```

- [ ] **Step 1: DSL 契约（Kotlin）**

`kotlin/src/main/kotlin/agentflow/dsl/HostTool.kt`（新文件，上述四个类型全部落此文件；`RunSignal` 为 DSL 内部类）：

```kotlin
package agentflow.dsl

/** Cheap per-run cancellation probe, visible to tool implementations. */
fun interface CancellationSignal {
    val cancelled: Boolean
}

/** A tool implemented on the app side, executed by the native loop. */
interface HostTool {
    val name: String
    val description: String
    val paramsJsonSchema: String
    val requiresApproval: Boolean

    /**
     * Blocking invocation on a JNI-owned worker thread (never the runner's
     * single thread). Return a JSON result string; `{"error":"..."}` on
     * failure. [toolCallId] correlates an approval gate with its tool card.
     */
    fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String
}

/** Live run events (spec §4.1). Tool mode: no tokens; events stream. */
interface RunEventCallback {
    fun onToken(token: String) {}
    fun onToolCall(toolCallId: String, name: String, argsJson: String) {}
    fun onToolReturn(toolCallId: String, resultJson: String) {}
}

/** DSL-internal signal; the JNI flips `cancelled` when nativeCancel fires. */
internal class RunSignal : CancellationSignal {
    @Volatile
    override var cancelled: Boolean = false
}
```

`NativeBridge.kt` 增：

```kotlin
external fun runJsonWorkflowConstrained(
    modelPath: String,
    workflowJson: String,
    tools: Array<HostTool>,
    signal: CancellationSignal,
    userQuery: String,
    onEvent: RunEventCallback,
    cancelId: Long,
): String
```

`JsonWorkflow.kt`：构造函数加 `tools: List<HostTool> = emptyList()`；`loadWorkflow` 三参重载；新增：

```kotlin
fun runConstrained(userQuery: String, events: RunEventCallback, cancelId: Long): String =
    NativeBridge.runJsonWorkflowConstrained(
        modelPath, workflowJson, tools.toTypedArray(), RunSignal(),
        userQuery, events, cancelId)
```

- [ ] **Step 2: JNI 实现（`jni/agentflow_jni.cc`）**

按现有文件结构（JString/ThrowJava/g_cancels 已在），新增：

**(a) JVM 缓存 + worker 池**（文件顶部 namespace 内）：

```cpp
JavaVM* g_jvm = nullptr;

struct UpcallWorkerPool {
  std::mutex mu;
  std::condition_variable cv;
  std::deque<std::function<void()>> jobs;
  std::vector<std::thread> threads;
  bool shutdown = false;
  void EnsureStarted(size_t n = 2) {
    std::lock_guard<std::mutex> lk(mu);
    if (!threads.empty()) return;
    for (size_t i = 0; i < n; ++i) threads.emplace_back([this] {
      for (;;) {
        std::function<void()> job;
        { std::unique_lock<std::mutex> lk(mu);
          cv.wait(lk, [this] { return shutdown || !jobs.empty(); });
          if (shutdown && jobs.empty()) return;
          job = std::move(jobs.front()); jobs.pop_front(); }
        job();
      }
    });
  }
  void Post(std::function<void()> job) {
    { std::lock_guard<std::mutex> lk(mu); jobs.push_back(std::move(job)); }
    cv.notify_one();
  }
};
UpcallWorkerPool g_workers;
```

文件末尾 `extern "C"` 内新增 `JNI_OnLoad`（若不存在）：

```cpp
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
  g_jvm = vm;
  return JNI_VERSION_1_6;
}
```

**(b) MakeHostTool**（namespace 内，`using channel = asio::experimental::concurrent_channel<void(asio::error_code, std::string)>;`）：

```cpp
struct JvmToolTarget {
  jobject tool = nullptr;      // global ref
  jmethodID invoke_mid = nullptr;
};

std::shared_ptr<af::Tool> MakeHostTool(
    asio::io_context& io, std::string name, std::string description,
    std::string params_schema, JvmToolTarget target,
    jobject signal, jfieldID cancelled_fid) {
  auto fn = [target, signal, cancelled_fid, &io](
                std::string_view args, std::string_view tool_call_id,
                const af::CancelToken& cancel) -> asio::awaitable<std::string> {
    auto completion = std::make_shared<channel>(io, 1);
    const std::string args_str(args);
    const std::string id_str(tool_call_id);
    g_workers.Post([target, signal, cancelled_fid, completion, args_str, id_str] {
      JNIEnv* wenv = nullptr;
      std::string result;
      if (g_jvm->AttachCurrentThread(reinterpret_cast<void**>(&wenv), nullptr) == JNI_OK) {
        jstring id = wenv->NewStringUTF(id_str.c_str());
        jstring js_args = wenv->NewStringUTF(args_str.c_str());
        jstring js_result = static_cast<jstring>(
            wenv->CallObjectMethod(target.tool, target.invoke_mid, id, js_args, signal));
        if (wenv->ExceptionCheck()) {
          wenv->ExceptionClear();
          result = R"({"error":"tool_impl_threw"})";
        } else if (js_result != nullptr) {
          const char* cs = wenv->GetStringUTFChars(js_result, nullptr);
          result = cs ? std::string(cs) : R"({"error":"null_result"})";
          if (cs) wenv->ReleaseStringUTFChars(js_result, cs);
          wenv->DeleteLocalRef(js_result);
        } else {
          result = R"({"error":"null_result"})";
        }
        wenv->DeleteLocalRef(id);
        wenv->DeleteLocalRef(js_args);
        g_jvm->DetachCurrentThread();
      } else {
        result = R"({"error":"jvm_attach_failed"})";
      }
      asio::post(completion->get_executor(),
                 [completion, result] { completion->try_send({}, result); });
    });
    // 取消：翻转 Kotlin 可见信号（worker 池上 attach→SetBooleanField）
    cancel.OnCancel([signal, cancelled_fid] {
      g_workers.Post([signal, cancelled_fid] {
        JNIEnv* wenv = nullptr;
        if (g_jvm->AttachCurrentThread(reinterpret_cast<void**>(&wenv), nullptr) == JNI_OK) {
          wenv->SetBooleanField(signal, cancelled_fid, JNI_TRUE);
          g_jvm->DetachCurrentThread();
        }
      });
    });
    // 协程让出 runner 线程等待结果；异常/关闭 → 取消错误槽
    auto [ec, result] = co_await completion->async_receive(asio::as_tuple(asio::use_awaitable));
    if (ec) co_return std::string(R"({"error":"cancelled"})");
    co_return result;
  };
  return std::make_shared<af::NativeFnTool>(
      af::ToolSchema{std::move(name), std::move(description), std::move(params_schema)},
      std::move(fn));
}
```

**(c) JniEventEmitter**（转发带 id 的工具事件给 Kotlin）：

```cpp
class JniEventEmitter : public af::EventEmitter {
 public:
  JniEventEmitter(JNIEnv* env, jobject events, jmethodID tool_call_mid,
                  jmethodID tool_return_mid)
      : env_(env), events_(events), tool_call_mid_(tool_call_mid),
        tool_return_mid_(tool_return_mid) {}
  void Emit(af::proto::TraceEvent ev) override {
    switch (ev.payload_case()) {
      case af::proto::TraceEvent::kToolCall: {
        const auto& p = ev.tool_call();
        jstring id = env_->NewStringUTF(p.tool_call_id().c_str());
        jstring name = env_->NewStringUTF(p.tool_name().c_str());
        jstring args = env_->NewStringUTF(p.args_json().c_str());
        env_->CallVoidMethod(events_, tool_call_mid_, id, name, args);
        env_->DeleteLocalRef(id); env_->DeleteLocalRef(name); env_->DeleteLocalRef(args);
        break;
      }
      case af::proto::TraceEvent::kToolReturn: {
        const auto& p = ev.tool_return();
        jstring id = env_->NewStringUTF(p.tool_call_id().c_str());
        jstring res = env_->NewStringUTF(p.result_json().c_str());
        env_->CallVoidMethod(events_, tool_return_mid_, id, res);
        env_->DeleteLocalRef(id); env_->DeleteLocalRef(res);
        break;
      }
      default: break;
    }
  }
 private:
  JNIEnv* env_;  // 事件发射在 runner io 线程 == JNI 调用线程，env 有效（与现 token 直通同构）
  jobject events_;
  jmethodID tool_call_mid_;
  jmethodID tool_return_mid_;
};
```

**(d) `runJsonWorkflowConstrained`**（仿 `runJsonWorkflowStreaming`，区别：注册工具、trace emitter、不设 token channel、不强制 constrained=false）：

```cpp
JNIEXPORT jstring JNICALL
Java_agentflow_jni_NativeBridge_runJsonWorkflowConstrained(
    JNIEnv* env, jobject /*self*/,
    jstring model_path_j, jstring workflow_json_j,
    jobjectArray tools_j, jobject signal_j,
    jstring user_query_j, jobject events_j, jlong cancel_id_j) {
  try {
    // 1. strings（照抄 streaming 版）+ cancel token（照抄）
    // 2. 事件回调方法解析：
    jmethodID tc_mid = nullptr, tr_mid = nullptr;
    if (events_j != nullptr) {
      jclass cb = env->GetObjectClass(events_j);
      tc_mid = env->GetMethodID(cb, "onToolCall",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
      tr_mid = env->GetMethodID(cb, "onToolReturn",
          "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    // 3. HostTool 接口方法解析（一次性）：
    //    getName/()Ljava/lang/String;  getDescription/()Ljava/lang/String;
    //    getParamsJsonSchema/()Ljava/lang/String;
    //    invoke/(Ljava/lang/String;Ljava/lang/String;Lagentflow/dsl/CancellationSignal;)Ljava/lang/String;
    // 4. RunSignal: jclass → GetFieldID(signal_cls, "cancelled", "Z");
    //    jobject signal_global = env->NewGlobalRef(signal_j);
    // 5. engine/backend/io 照抄；g_workers.EnsureStarted();
    // 6. host_tools = make_shared<ToolRegistry>(io);
    //    for (i in tools_j): GetObjectArrayElement → 读 name/desc/schema → NewGlobalRef(tool)
    //      → host_tools->Register(MakeHostTool(io, ..., target, signal_global, cancelled_fid));
    // 7. WorkflowLoader::Load(workflow_json, *host_tools)（未知工具名在此被拒）
    // 8. BuildAgentNode：不传 token_channel；不覆盖 constrained_tool_calls
    //    （由 workflow JSON 的 "constrained_tool_calls": true 控制）
    // 9. JniEventEmitter emitter(env, events_j, tc_mid, tr_mid);
    //    Runner::Options{.trace = &emitter};
    // 10. co_spawn(io, runner.Run(init, cancel_tok), use_future) + io.run() → 返回 reply jstring
    // 11. 退出路径 DeleteGlobalRef(tool refs + signal_global)
  } catch (const std::exception& e) { ThrowJava(env, e.what()); return nullptr; }
  catch (...) { ThrowJava(env, "unknown C++ exception"); return nullptr; }
}
```

- [ ] **Step 3: JVM 桥测试（模型门控，仿 SmokeTest 模式）**

`kotlin/src/test/kotlin/agentflow/HostToolBridgeTest.kt`：

```kotlin
package agentflow

import agentflow.dsl.CancellationSignal
import agentflow.dsl.HostTool
import agentflow.dsl.loadWorkflow
import agentflow.dsl.RunEventCallback
import agentflow.jni.NativeBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class HostToolBridgeTest {
    private val json = """
        {"schema_version":1,"name":"bridge_test","version":"v1",
         "state":{"kind":"dynamic_json","fields":{}},
         "agents":{"main":{"system_prompt":
           "Use the echo tool when asked to echo. Reply with the tool's result.",
           "model":{"max_output_tokens":64},
           "constrained_tool_calls":true,
           "tools":["echo"]}},
         "main":"main"}
    """.trimIndent()

    class EchoTool : HostTool {
        override val name = "echo"
        override val description = "Echoes its input"
        override val paramsJsonSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""
        override val requiresApproval = false
        override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String =
            """{"echoed":"$argsJson","callId":"$toolCallId"}"""
    }

    @Test
    fun hostToolUpcallRoundTrips() {
        val modelPath = System.getenv("MODEL_PATH")
        assumeTrue(modelPath != null, "MODEL_PATH not set — skipping")
        val toolCalls = CopyOnWriteArrayList<String>()
        val returns = CopyOnWriteArrayList<String>()
        val wf = loadWorkflow(modelPath!!, json, listOf(EchoTool()))
        val cancelId = NativeBridge.nativeNewCancel()
        val reply = wf.runConstrained("Echo hello", object : RunEventCallback {
            override fun onToolCall(toolCallId: String, name: String, argsJson: String) {
                toolCalls.add("$name:$toolCallId:$argsJson")
            }
            override fun onToolReturn(toolCallId: String, resultJson: String) {
                returns.add("$toolCallId:$resultJson")
            }
        }, cancelId)
        NativeBridge.nativeFreeCancel(cancelId)
        assertTrue(toolCalls.size == 1, "expected one tool call, got ${toolCalls.size}")
        assertTrue(returns.size == 1, "expected one tool return")
        assertTrue(returns[0].contains("callId"), "upcall should carry toolCallId")
    }
}
```

- [ ] **Step 4: 运行验证**

Run:
```bash
cd /home/wayne/tools/zen && bazel build //...                       # C++ 编译
cd kotlin && MODEL_PATH=/home/wayne/tools/zen/models/gemma-4-E2B-it.litertlm ./gradlew test --tests agentflow.HostToolBridgeTest   # 桥测试（本机有模型）
```
Expected: 编译通过；桥测试 PASS（echo 工具被调用、事件含 callId）；`SmokeTest`/`WorkflowJsonTest` 不回归（`./gradlew test` 全量）

- [ ] **Step 5: 提交**

```bash
git add jni/agentflow_jni.cc kotlin/src/main/kotlin kotlin/src/test/kotlin/agentflow/HostToolBridgeTest.kt
git commit -m "feat(jni): host-tool bridge — Kotlin tools, async upcalls, tool events"
```

---

### Task 4: zen → mobile — AAR 重建与交接

**Files:**
- Create: `android-inference/src/main/kotlin/agentflow/dsl/HostTool.kt`（Task 3 的镜像）
- Modify: `android-inference/src/main/kotlin/agentflow/dsl/JsonWorkflow.kt`、`android-inference/src/main/kotlin/agentflow/jni/NativeBridge.kt`（Task 3 改动镜像）
- Modify: `app/libs/agentflow-android.aar`（二进制替换）

**Interfaces:**
- Consumes: Task 3 的 DSL 产物（签名一致）
- Produces: mobile 侧可用 `agentflow.dsl.HostTool` / `RunEventCallback` / `loadWorkflow(m,p,json,tools)` / `runConstrained`

- [ ] **Step 1: 镜像 DSL 到 android-inference**

按 Task 3 Step 1 的最终代码，把 `HostTool.kt`（含 RunSignal 内部类）、`NativeBridge.kt` 的 external、`JsonWorkflow.kt` 的重载与 `runConstrained` 逐字镜像进 `android-inference/src/main/kotlin/agentflow/`（注意该模块包结构一致：`agentflow.dsl` / `agentflow.jni`）。

- [ ] **Step 2: 重建 .so 与 AAR**

Run（依据 2026-06-17 spec 的既有流程）:
```bash
cd /home/wayne/tools/zen
bazel build //jni:libagentflow_jni.so --config=android_arm64
file bazel-bin/jni/libagentflow_jni.so   # 期望 ELF aarch64
cd android-inference && ./gradlew assembleRelease
ls build/outputs/aar/
```

- [ ] **Step 3: 交付到 zen_mobile 并验证编译**

```bash
cp /home/wayne/tools/zen/android-inference/build/outputs/aar/*-release.aar \
   /home/wayne/tools/zen_mobile/app/libs/agentflow-android.aar
cd /home/wayne/tools/zen_mobile && ./gradlew :app:assembleDebug
```
Expected: assembleDebug 通过（尚未调用新 API，纯链接验证）

- [ ] **Step 4: 提交**

```bash
cd /home/wayne/tools/zen && git add android-inference && \
  git commit -m "feat(android): mirror host-tool DSL into android-inference AAR"
cd /home/wayne/tools/zen_mobile && git add app/libs/agentflow-android.aar && \
  git commit -m "build: bump agentflow AAR with host-tool bridge"
```

---

### Task 5: mobile — FsWorkspace + FsTools（纯 JVM，TDD）

**Files:**
- Create: `app/src/main/java/com/zenwayne/zenagent/tools/FsWorkspace.kt`
- Create: `app/src/main/java/com/zenwayne/zenagent/tools/FsTools.kt`
- Test: `app/src/test/java/com/zenwayne/zenagent/tools/FsWorkspaceTest.kt`

**Interfaces:**
- Consumes: `agentflow.dsl.HostTool` / `CancellationSignal`（Task 4 的 AAR）
- Produces（Task 6/8 依赖）:
  - `class FsWorkspace(root: File)` — `resolve(path: String): Result<File>`、`read(path): String`、`write(path, content): String`、`list(path): String`（全部返回 JSON 结果串）
  - `class FsReadTool(ws: FsWorkspace) : HostTool`、`class FsWriteTool(ws: FsWorkspace, gates: ConcurrentHashMap<String, ApprovalGate>) : HostTool`、`class FsListTool(ws: FsWorkspace) : HostTool`
  - 常量：`FsLimits.READ_CAP_BYTES = 512 * 1024`、`FsLimits.LIST_CAP = 200`

- [ ] **Step 1: 写失败测试**（`FsWorkspaceTest.kt`，JVM 临时目录）

```kotlin
package com.zenwayne.zenagent.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FsWorkspaceTest {
    @TempDir lateinit var rootDir: File

    private fun ws() = FsWorkspace(rootDir)

    @Test
    fun pathEscapeIsRejected() {
        val r = ws().resolve("../../etc/passwd")
        assertTrue(r.isFailure, "escape must fail")
    }

    @Test
    fun absolutePathResolvesInsideRoot() {
        val f = ws().resolve("/tmp/evil.txt")
        assertTrue(f.isSuccess)
        assertTrue(f.getOrThrow().path.startsWith(rootDir.canonicalPath))
    }

    @Test
    fun writeThenReadRoundTrips() {
        val w = ws().write("a/b.txt", "hello")
        assertTrue(w.contains("\"ok\""), "write should succeed: $w")
        val r = ws().read("a/b.txt")
        assertTrue(r.contains("hello"), "read should return content: $r")
    }

    @Test
    fun binaryReadIsRejected() {
        File(rootDir, "bin.dat").writeBytes(byteArrayOf(0, 1, 2, 3))
        assertTrue(ws().read("bin.dat").contains("binary"), "binary content must be flagged")
    }

    @Test
    fun readOverCapIsRejected() {
        File(rootDir, "big.txt").writeBytes(ByteArray(FsLimits.READ_CAP_BYTES + 1) { 'a'.code.toByte() })
        assertTrue(ws().read("big.txt").contains("too_large"), "over-cap read must fail")
    }

    @Test
    fun listReturnsEntries() {
        File(rootDir, "x.txt").writeText("x")
        File(rootDir, "sub").mkdirs()
        val out = ws().list(".")
        assertTrue(out.contains("x.txt") && out.contains("sub"), "list should include both: $out")
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.zenwayne.zenagent.tools.FsWorkspaceTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 最小实现**

`FsWorkspace.kt`（纯 java.io，无 Android 依赖；JSON 用字符串拼接，避免引入依赖）：

```kotlin
package com.zenwayne.zenagent.tools

import java.io.File
import java.io.IOException

object FsLimits {
    const val READ_CAP_BYTES = 512 * 1024
    const val LIST_CAP = 200
}

class FsWorkspace(private val root: File) {
    private val rootCanonical: String = root.canonicalPath

    fun resolve(path: String): Result<File> {
        val p = path.trim().ifEmpty { "." }
        val raw = if (p.startsWith("/")) File(root, p.removePrefix("/")) else File(root, p)
        return try {
            val canon = raw.canonicalFile
            val ok = canon.path == rootCanonical ||
                canon.path.startsWith(rootCanonical + File.separator)
            if (ok) Result.success(canon) else Result.failure(SecurityException("path escapes workspace"))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    fun read(path: String): String {
        val f = resolve(path).getOrElse { return err("invalid_path") }
        if (!f.isFile) return err("not_a_file")
        if (f.length() > FsLimits.READ_CAP_BYTES) return err("too_large")
        val bytes = f.readBytes()
        bytes.forEach { b -> if (b == 0.toByte()) return err("binary_file") }
        val text = String(bytes, Charsets.UTF_8)
        return """{"content":${jsonEscape(text)},"bytes":${bytes.size}}"""
    }

    fun write(path: String, content: String): String {
        val f = resolve(path).getOrElse { return err("invalid_path") }
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "${f.name}.tmp-${System.nanoTime()}")
        return try {
            tmp.writeText(content)
            if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            """{"ok":true,"bytes":${content.toByteArray(Charsets.UTF_8).size}}"""
        } catch (e: IOException) {
            tmp.delete()
            err("write_failed")
        }
    }

    fun list(path: String): String {
        val d = resolve(path).getOrElse { return err("invalid_path") }
        if (!d.isDirectory) return err("not_a_directory")
        val entries = d.listFiles()?.take(FsLimits.LIST_CAP) ?: emptyList()
        val body = entries.joinToString(",") { f ->
            """{"name":${jsonEscape(f.name)},"type":${if (f.isDirectory) "\"dir\"" else "\"file\""},"size":${if (f.isFile) f.length() else 0}}"""
        }
        return """{"entries":[$body]}"""
    }

    private fun err(code: String) = """{"error":"$code"}"""

    companion object {
        fun jsonEscape(s: String): String = buildString {
            append('"')
            s.forEach { c -> when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append(String.format("\\u%04x", c.code)) else append(c)
            } }
            append('"')
        }
    }
}
```

`FsTools.kt`：

```kotlin
package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import agentflow.dsl.HostTool
import java.util.concurrent.ConcurrentHashMap

class FsReadTool(private val ws: FsWorkspace) : HostTool {
    override val name = "fs_read"
    override val description = "Read a text file from the workspace. Returns content and byte size."
    override val paramsJsonSchema = """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.read(path)
    }
}

class FsWriteTool(
    private val ws: FsWorkspace,
    private val gates: ConcurrentHashMap<String, ApprovalGate>,
) : HostTool {
    override val name = "fs_write"
    override val description = "Write a text file into the workspace. Requires user approval."
    override val paramsJsonSchema = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""
    override val requiresApproval = true
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        val content = extractContent(argsJson) ?: return """{"error":"missing_content"}"""
        val gate = ApprovalGate()
        gates[toolCallId] = gate
        val decision = gate.await(cancel)
        gates.remove(toolCallId)
        if (decision != Decision.Approved) return """{"error":"user_denied"}"""
        return ws.write(path, content)
    }
}

class FsListTool(private val ws: FsWorkspace) : HostTool {
    override val name = "fs_list"
    override val description = "List directory entries in the workspace."
    override val paramsJsonSchema = """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""
    override val requiresApproval = false
    override fun invoke(toolCallId: String, argsJson: String, cancel: CancellationSignal): String {
        val path = extractPath(argsJson) ?: return """{"error":"missing_path"}"""
        return ws.list(path)
    }
}

// 轻量参数提取（args 为 {"path":"...","content":"..."}；content 由 native 侧传入）
internal fun extractPath(argsJson: String): String? =
    Regex("\"path\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(argsJson)?.groupValues?.get(1)
        ?.replace("\\/", "/")
internal fun extractContent(argsJson: String): String? =
    Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(argsJson)?.groupValues?.get(1)
```

> 说明：`extractPath`/`extractContent` 用正则而非 JSON 库，避免新依赖；实现时若代码行数超限，可拆 `tools/ArgParse.kt`。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.zenwayne.zenagent.tools.FsWorkspaceTest`
Expected: PASS（6 用例）；`lsp_diagnostics` 清零

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zenwayne/zenagent/tools/FsWorkspace.kt \
        app/src/main/java/com/zenwayne/zenagent/tools/FsTools.kt \
        app/src/test/java/com/zenwayne/zenagent/tools/FsWorkspaceTest.kt
git commit -m "feat(tools): sandboxed filesystem tools (fs_read/fs_write/fs_list)"
```

---

### Task 6: mobile — ApprovalGate（TDD）

**Files:**
- Create: `app/src/main/java/com/zenwayne/zenagent/tools/ApprovalGate.kt`
- Test: `app/src/test/java/com/zenwayne/zenagent/tools/ApprovalGateTest.kt`

**Interfaces:**
- Consumes: `agentflow.dsl.CancellationSignal`
- Produces: `enum Decision { Approved, Denied }`；`class ApprovalGate { fun approve(); fun deny(); fun await(cancel, pollMillis=50): Decision }`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ApprovalGateTest {
    private class Signal(var flag: Boolean = false) : CancellationSignal {
        override val cancelled get() = flag
    }

    @Test
    fun approveResolvesAwait() {
        val gate = ApprovalGate()
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<Decision> { gate.await(Signal()) }
        Thread.sleep(50)
        gate.approve()
        assertEquals(Decision.Approved, fut.get(2, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun denyResolvesAwait() {
        val gate = ApprovalGate()
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<Decision> { gate.await(Signal()) }
        Thread.sleep(50)
        gate.deny()
        assertEquals(Decision.Denied, fut.get(2, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun cancelledRunYieldsDenied() {
        val gate = ApprovalGate()
        val sig = Signal(flag = true)
        assertEquals(Decision.Denied, gate.await(sig, pollMillis = 10))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.zenwayne.zenagent.tools.ApprovalGateTest`
Expected: 编译失败

- [ ] **Step 3: 实现**

```kotlin
package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal

enum class Decision { Approved, Denied }

/**
 * Blocking approve/deny gate for side-effecting tools (spec §4.1). The tool's
 * invoke runs on a JNI worker thread and blocks here until the user decides,
 * the run is cancelled, or (defensively) never — polling keeps cancel visible.
 */
class ApprovalGate {
    @Volatile private var decision: Decision? = null

    fun approve() { decision = Decision.Approved }
    fun deny() { decision = Decision.Denied }

    fun await(cancel: CancellationSignal, pollMillis: Long = 50): Decision {
        while (decision == null) {
            if (cancel.cancelled) return Decision.Denied
            Thread.sleep(pollMillis)
        }
        return decision!!
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.zenwayne.zenagent.tools.ApprovalGateTest`
Expected: PASS（3 用例）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zenwayne/zenagent/tools/ApprovalGate.kt \
        app/src/test/java/com/zenwayne/zenagent/tools/ApprovalGateTest.kt
git commit -m "feat(tools): approval gate with approve/deny/cancel semantics"
```

---

### Task 7: mobile — RunEvent + 工具模式 InferenceClient

**Files:**
- Create: `app/src/main/java/com/zenwayne/zenagent/inference/RunEvent.kt`
- Create: `app/src/main/java/com/zenwayne/zenagent/tools/HostToolRegistry.kt`
- Modify: `app/src/main/java/com/zenwayne/zenagent/inference/InferenceClient.kt`
- Modify: `app/src/main/java/com/zenwayne/zenagent/inference/AgentflowInferenceClient.kt`

**Interfaces:**
- Consumes: AAR 的 `loadWorkflow/runConstrained/HostTool/RunEventCallback`（Task 4）；Task 5/6 的 FsTools/ApprovalGate
- Produces（Task 8 依赖）:
  - `sealed class RunEvent { Token(text); ToolCall(toolCallId, name, argsJson); ToolReturn(toolCallId, resultJson); Final(text) }`
  - `interface InferenceClient { fun runAgentWithTools(query: String): Flow<RunEvent> }`（新增方法）
  - `class HostToolRegistry(context: Context) { val workspace: FsWorkspace; val gates: ConcurrentHashMap<String, ApprovalGate>; fun tools(): List<HostTool> }`

- [ ] **Step 1: RunEvent + 接口扩展**

`RunEvent.kt`：

```kotlin
package com.zenwayne.zenagent.inference

/** Live events for a tool-mode run (spec §4.2). No tokens in constrained mode. */
sealed class RunEvent {
    data class Token(val text: String) : RunEvent()
    data class ToolCall(val toolCallId: String, val name: String, val argsJson: String) : RunEvent()
    data class ToolReturn(val toolCallId: String, val resultJson: String) : RunEvent()
    data class Final(val text: String) : RunEvent()
}
```

`InferenceClient.kt` 接口追加：

```kotlin
    /**
     * Runs a tool-mode turn: constrained decoding, live tool events, final
     * text delivered whole (spec Q6-b). Cancellation mirrors streamTokens.
     */
    fun runAgentWithTools(query: String): Flow<RunEvent>
```

- [ ] **Step 2: HostToolRegistry**

```kotlin
package com.zenwayne.zenagent.tools

import agentflow.dsl.HostTool
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Assembles the P1 tool list: sandboxed fs tools (spec §4.2). */
class HostToolRegistry(context: Context) {
    val gates = ConcurrentHashMap<String, ApprovalGate>()
    val workspace: FsWorkspace = FsWorkspace(
        File(requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" }, "workspace")
    )

    fun tools(): List<HostTool> = listOf(
        FsReadTool(workspace),
        FsWriteTool(workspace, gates),
        FsListTool(workspace),
    )
}
```

- [ ] **Step 3: AgentflowInferenceClient 工具模式**

workflow JSON 换 v2（工具 + 约束解码）：

```kotlin
    private val toolsWorkflowJson: String = """
        {
          "schema_version": 1,
          "name": "zenagent-tools",
          "version": "v2",
          "state": {"kind": "dynamic_json", "fields": {}},
          "agents": {
            "main": {
              "system_prompt": "You are Zen, an on-device assistant. You can read, write and list files in the workspace with fs_read/fs_write/fs_list. Reply concisely in the user's language.",
              "model": {"max_output_tokens": 512},
              "constrained_tool_calls": true,
              "tools": ["fs_read", "fs_write", "fs_list"]
            }
          },
          "main": "main"
        }
    """.trimIndent()
```

实现（callbackFlow + Dispatchers.IO 阻塞调用 + 取消接线）：

```kotlin
    private val toolsWorkflow: JsonWorkflow by lazy {
        loadWorkflow(modelPath, toolsWorkflowJson, toolRegistry.tools())
    }

    override fun runAgentWithTools(query: String): Flow<RunEvent> = callbackFlow {
        val cancelId = NativeBridge.nativeNewCancel()
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
            NativeBridge.nativeFreeCancel(cancelId)
        }
        awaitClose { NativeBridge.nativeCancel(cancelId) }
    }
```

构造注入：`AgentflowInferenceClient(context, toolRegistry: HostToolRegistry = HostToolRegistry(context), modelFileName = ...)`。

- [ ] **Step 4: 编译 + 现有测试不回归**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: 编译通过；`ChatViewModelTest` 全绿（接口新增方法对现有 fake 客户端——给 fake 补一个默认实现或只实现新方法，见 Task 8）

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zenwayne/zenagent/inference/RunEvent.kt \
        app/src/main/java/com/zenwayne/zenagent/inference/InferenceClient.kt \
        app/src/main/java/com/zenwayne/zenagent/inference/AgentflowInferenceClient.kt \
        app/src/main/java/com/zenwayne/zenagent/tools/HostToolRegistry.kt
git commit -m "feat(inference): tool-mode run with live RunEvent flow"
```

---

### Task 8: mobile — ChatViewModel + UI 接线（真实工具卡与审批）

**Files:**
- Modify: `app/src/main/java/com/zenwayne/zenagent/data/Models.kt`（ToolCall.toolCallId、ToolIcon.File）
- Modify: `app/src/main/java/com/zenwayne/zenagent/ui/chat/components/ToolIcons.kt`
- Modify: `app/src/main/java/com/zenwayne/zenagent/inference/ChatViewModel.kt`
- Test: `app/src/test/java/com/zenwayne/zenagent/inference/ChatViewModelTest.kt`（扩）

**Interfaces:**
- Consumes: Task 7 的 `RunEvent` / `runAgentWithTools` / `HostToolRegistry`
- Produces: `ChatViewModel.sendWithTools(text)`、`ChatViewModel.approve(toolCallId)`、`ChatViewModel.deny(toolCallId)`（替换/并存现有 `approveGate`/`denyGate` 假数据路径）

- [ ] **Step 1: 模型 + 图标**

`Models.kt`：

```kotlin
enum class ToolIcon { Search, MapPin, Hotel, Flight, Weather, Code, File, Warning, Generic }

data class ToolCall(
    ...
    val toolCallId: String? = null,  // live-run correlation id
    ...
)
```

`ToolIcons.kt` 增 `ToolIcon.File -> Icons.Filled.Description`（core 图标集）。

- [ ] **Step 2: 写失败测试**（`ChatViewModelTest.kt` 追加，仿现有 fakeClient 模式）

```kotlin
    @Test
    fun `tool call then return updates card and final text`() = runTest {
        val client = fakeClient(toolEvents = listOf(
            RunEvent.ToolCall("c1", "fs_read", """{"path":"a.txt"}"""),
            RunEvent.ToolReturn("c1", """{"content":"hi","bytes":2}"""),
            RunEvent.Final("done"),
        ))
        val vm = ChatViewModel(client, registry = testRegistry())
        vm.sendWithTools("read a.txt")
        advanceUntilIdle()
        val conv = vm.selected.value
        val cards = conv.messages.flatMap { it.toolCalls }
        assertEquals(1, cards.size)
        assertEquals("c1", cards[0].toolCallId)
        assertEquals(ToolStatus.Done, cards[0].status)
        assertEquals(RunState.Succeeded, conv.runState)
        assertTrue(conv.messages.any { it.text == "done" })
    }
```

- [ ] **Step 3: VM 实现**

`ChatViewModel` 增：

```kotlin
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
                            appendMessage(Message(id = newId("agent"), role = Role.Agent, text = ev.text))
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

    private fun onToolCall(ev: RunEvent.ToolCall) {
        val tool = registry.toolByName(ev.name)
        val needsApproval = tool?.requiresApproval == true
        val conv = _selected.value
        _selected.value = conv.copy(
            runState = if (needsApproval) RunState.AwaitingApproval else RunState.Running,
            messages = conv.messages + Message(
                id = newId("tool"),
                role = Role.Agent,
                toolCalls = listOf(ToolCall(
                    name = ev.name, detail = ev.argsJson,
                    status = if (needsApproval) ToolStatus.Pending else ToolStatus.Running,
                    icon = if (ev.name.startsWith("fs_")) ToolIcon.File else ToolIcon.Code,
                    toolCallId = ev.toolCallId,
                )),
                approval = if (needsApproval) ApprovalRequest(tool = ev.name, args = ev.argsJson) else null,
            ),
        )
    }

    private fun onToolReturn(ev: RunEvent.ToolReturn) {
        val conv = _selected.value
        val failed = ev.resultJson.contains("\"error\"")
        _selected.value = conv.copy(
            runState = RunState.Running,
            messages = conv.messages.map { msg ->
                msg.copy(
                    toolCalls = msg.toolCalls.map { tc ->
                        if (tc.toolCallId == ev.toolCallId) tc.copy(
                            status = if (failed) ToolStatus.Failed else ToolStatus.Done,
                            resultLabel = resultLabel(ev.resultJson),
                            errorHint = if (failed) ev.resultJson.take(80) else null,
                        ) else tc
                    },
                    approval = if (msg.toolCalls.any { it.toolCallId == ev.toolCallId }) null else msg.approval,
                )
            },
        )
    }

    /** Approve the pending gate for [toolCallId] (replaces the SampleData approveGate path). */
    fun approve(toolCallId: String) { registry.gates[toolCallId]?.approve() }

    /** Deny the pending gate for [toolCallId]. */
    fun deny(toolCallId: String) { registry.gates[toolCallId]?.deny() }

    private fun resultLabel(resultJson: String): String =
        if (resultJson.contains("\"error\"")) "Failed"
        else if (resultJson.contains("\"bytes\"")) "Done"
        else "Done"
```

`HostToolRegistry` 增 `fun toolByName(name: String): HostTool?`。`ChatViewModel` 构造注入 `registry: HostToolRegistry`；**companion factory 必须创建单一 registry 实例并同时注入 client 与 VM**（否则 VM 的 approve/deny 与工具 worker 持有的 gates map 不是同一个对象）：

```kotlin
    companion object {
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
```

旧 `approveGate`/`denyGate` 保留（SampleData 历史会话仍走 UI 假路径）并加注释。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: 新用例 PASS；`ChatViewModelTest` 既有用例全绿；`lsp_diagnostics` 清零

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zenwayne/zenagent/data/Models.kt \
        app/src/main/java/com/zenwayne/zenagent/ui/chat/components/ToolIcons.kt \
        app/src/main/java/com/zenwayne/zenagent/inference/ChatViewModel.kt \
        app/src/test/java/com/zenwayne/zenagent/inference/ChatViewModelTest.kt
git commit -m "feat(ui): live tool cards and approval gate driven by RunEvent flow"
```

---

### Task 9: 真机端到端验收（手动，记录性任务）

**Files:** 无代码改动；产出验收记录（PR 描述或 docs 备注）

- [ ] **Step 1: 构建安装 + 模型就位**

```bash
cd /home/wayne/tools/zen_mobile && ./gradlew :app:installDebug
adb push /home/wayne/tools/zen/models/gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.zenwayne.zenagent/files/models/
```

- [ ] **Step 2: 读文件链路**

对话输入：「用 fs_read 读 workspace 里的 a.txt」→ 预期：工具卡（File 图标，Running→Done，结果标签 Done）、回复提及文件内容、`RunState.Succeeded`。

- [ ] **Step 3: 写文件 + 审批链路**

对话输入：「把 hello world 写到 b.txt」→ 预期：`ApprovalCard` 出现且 `RunState.AwaitingApproval`；点 Approve → 工具卡 Done，`adb shell` 确认文件存在且内容正确；再来一次点 Deny → 工具卡 Failed（errorHint 含 user_denied），运行继续不崩溃。

- [ ] **Step 4: 停止链路**

审批卡挂起时点 Stop → 运行转为 `Stopped`，App 不 ANR（门被取消信号释放，worker 返回）。

- [ ] **Step 5: 记录**

验收结果（含截图）附在功能 PR 描述中；任何偏差回 Task 3/8 修复后重验。

---

## 自检记录

1. **Spec 覆盖**：spec §4.1 注册面→Task 3/4；上行调用+取消→Task 3（MakeHostTool）；事件面→Task 1+3；约束入口→Task 3(d)；§4.2 组件→Task 5–8；§4.3 workflow JSON v2→Task 7；§5 数据流（审批/停止）→Task 6/8/9；§7 测试矩阵→各任务 Step；P1 范围无遗漏。P2/P3 明确留作后续计划（spec §8）。
2. **占位符扫描**：无 TBD/TODO；所有代码块为实代码；Task 3(d) 的注释式步骤明确引用 streaming 版既有代码位置（可逐行对照实现），非占位。
3. **类型一致性**：`invoke(toolCallId, argsJson, cancel)` 签名在 Task 3 契约、Task 5 实现、Task 8 消费三处一致；`RunEvent` 四子类名与 VM when 分支一致；`gates` map 键（toolCallId）在 FsWriteTool 注册与 VM approve/deny 消费一致。

## 并行度与依赖

```
Task 1 ─► Task 2 ─► Task 3 ─► Task 4 ─► Task 7 ─► Task 8 ─► Task 9
                              Task 5 ──┬───────────┘
                              Task 6 ──┘
```
Task 5/6 可与 Task 1–4 并行（不依赖 AAR 新面，仅依赖接口契约文档——实现者按 Task 3 的 Produces 签名编码）。
