# 交界文档 — Agent Tools 剩余工作与操作要点

**日期:** 2026-09-04
**状态:** P1（文件系统工具）已合并；本文档交接剩余工作与踩坑记录。

## 1. 已完成并合并

| 仓库 | PR | 内容 | 状态 |
|---|---|---|---|
| zen | [#41](https://github.com/ZenWayne/ZenAgent/pull/41) | JNI host-tool 桥（tool_call_id 透传、异步上行调用、事件面、约束入口、DSL 契约）、engine-upgrade 合并、bazel 锁 8.4.2 | MERGED |
| zen_mobile | [#2](https://github.com/ZenWayne/zen_mobile/pull/2) | FsWorkspace/FsTools/ApprovalGate、工具模式推理 + UI 接线、AGENTS.md、Appium 套件（含清数据卫生流程） | MERGED |

**验证证据**：zen C++ 单测 47/47；zen_mobile JVM 单测 14/14；真机端侧 E2E 18/18（`make e2e`，pm clear 清数据起跑）。

**设计依据**：`docs/superpowers/specs/2026-08-31-agent-tools-host-bridge-design.md`（P2/P3 已定义，见下）。
**实施计划**：`docs/superpowers/plans/2026-08-31-agent-tools-host-bridge.md`（P1 九个任务，已完成）。

---

## 2. 剩余工作（按建议顺序）

### A. P2 — Python 工具（Chaquopy）★ 下一个大项

**目标**：给 Agent 加 `python_run` 工具（spec §3/§8-P2）。用户明确指示「暂时不动」，本段是给后续开工的完整交接。

**已拍板的决策**（来自 spec 头脑风暴）：
- 运行时：**Chaquopy 17.0**（MIT 免费；Python 3.10–3.14；arm64 原生；AGP 8.7.3 兼容 ✓）
- v1 只带标准库（**不带 pip 包**，APK 增量最小）；numpy/pandas 作为后续可选 build flavor（+10~20MB）
- 单解释器 → **所有执行串行经单 worker 队列**（这同时满足并行 dispatch 的并发安全契约）
- `requiresApproval=true`，复用 `ApprovalGate`
- 无沙箱（Python 有 App 全部权限含 `java` 模块）、无硬超时（`while True` 无法强杀）——**文档化接受**

**实施草图**（沿用 P1 的 TDD 模式）：
1. Gradle：settings/根/app 三处接入 Chaquopy plugin（17.0.0），`chaquopy { defaultConfig { version = "3.13" } }`，不配置 pip
2. `Application.onCreate` → `Python.start()`
3. `tools/PythonTool.kt`：`python_run(toolCallId, code, cancel)` → stdout/stderr 重定向捕获（执行前包一层换掉 `sys.stdout`）→ 返回 `{"stdout","stderr"}`；代码上限 100KB；尽力超时（超时 detach worker 线程）
4. `HostToolRegistry.tools()` 增加 `PythonRunTool`；workflow JSON v2 的 `"tools"` 数组加 `"python_run"`
5. UI 零改动（`ToolIcon.Code` 已有）
6. 测试：JVM 单测（队列串行、超时、参数提取，Chaquopy mock 掉）+ Appium E2E（`07_tool_mode_python.test.js`：让模型跑一段 Python 计算，断言结果）

**已知风险**：
- Chaquopy `Python.start()` 首启延迟；无沙箱需在 AGENTS.md/spec 明示
- 真机上线程/内存：单 worker + 串行队列天然限流

### B. P3 — SAF 共享存储

**目标**：Agent 经用户授权访问沙箱外目录（spec §8-P3）。**不是权限管理**，是「文件管理器般的访问面」：用户在设置里用系统文件选择器挑目录 → Agent 以 `/shared/` 前缀读写。

**要点**：
- Settings 加「授权共享存储」→ `ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission`，URI 持久化
- `FsWorkspace` 扩展第二根：SAF `DocumentFile` 树与现有 `java.io` 是两套 API——按前缀 `/shared/` 分流或统一包装
- 逃逸防护靠 SAF 树本身 + 前缀校验（DocumentFile 无 canonical 路径）
- 审批门沿用
- E2E 难点：setup 里要点系统文件选择器（Appium 自动化系统 UI，易碎）

### C. zen 宿主 PIC 修复（恢复 JVM 测试层）

- **问题**：LiteRT-LM 90f 的 host 预编译归档（`third_party/litert_lm/lib/`）缺 `-fPIC`，host 链 `libagentflow_jni.so` 被 ld 拒绝。arm64 不受影响（真机全绿）。
- **影响**：zen 的 kotlin JVM 模型门控测试（HostToolBridgeTest/SmokeTest/WorkflowJsonTest）无法运行。
- **修法**：LiteRT-LM 源码 CMake 加 `CMAKE_POSITION_INDEPENDENT_CODE=ON` 重编 host 归档替换之（套路同 zen 5bc421a 的 re2-PIC 重建）。

### D. `.bcr-override` 绝对路径（zen 仓库卫生）

`MODULE.bazel` 里 `registry = "file:///home/wayne/tools/zen/.bcr-override"` 是机器相关路径。改为仓库内相对路径（override 目前仅含 abseil-cpp 一个模块）。

### E. 可选 / 明确不做

- **numpy/pandas build flavor**：P2 的可选扩展，需时再开
- **约束解码流式**：LiteRT-LM 引擎层工作（spec Q6-a），明确不做
- **子代理执行轨道**：UI 卡片已就绪，执行是独立轨道，不在本线
- **bazel 9.x 兼容**：如需上 9.x，给 nlohmann_json 3.11.3 打 `load("@rules_cc//cc:defs.bzl","cc_library")` 补丁或升版（现锁 8.4.2，已进 zen master）

---

## 3. 操作要点（避免后来者重踩）

### 工具链
- **bazel 8.4.2**（zen `.bazelversion` 已锁）：7.4.1 不满足 protobuf v35.1（≥8）；9.x 移除原生 `cc_library` 挂 nlohmann 3.11.3。裸二进制在 `~/.cache/bazelisk/downloads/sha256/4dc8e99.../bin/bazel`
- zen 构建需 `ANDROID_NDK_HOME=/opt/android-sdk/ndk/28.2.13676358`（本机唯一 NDK）

### AAR 更新（AGENTS.md 有完整流程）
1. zen：`bazel build //jni:libagentflow_jni.so --config=android_arm64`
2. 拷入 `zen/android-inference/src/main/jniLibs/arm64-v8a/` → gradle `assembleDebug`
3. 拷 AAR 到 `zen_mobile/app/libs/agentflow-android.aar`（git-ignored）
4. **必须 `file` 校验 ARM aarch64**——bazel host/arm64 配置切换会覆盖 bazel-bin 输出（曾误打 x86-64 进 AAR）

### 真机 E2E
- **一切 UI 交互走 Appium**，禁 adb 盲点盲打（中文 IME 会转写 `input text`）
- `make e2e`：pm clear 清数据（**仅保留模型**：模型暂存 `/data/local/tmp`，清后设备内 cp 恢复）+ 重建 workspace 种子文件
- Compose 文本输入：`//android.widget.EditText` 节点 + setValue（`~chat_input` 语义在 BasicTextField 上，但节点类仍是 android.view.View，setValue 打不进去）；输入后不要立即 hideKeyboard（会丢 composing text）——`typeAndCommit` 已封装
- `pm clear` 后有瞬时竞态，recipe 已加 2s settle
- 设备 IME 必须是非转换 IME（Gboard Latin），Appium unicodeKeyboard 能力在本套件**不要用**（会弄脏设备 IME 状态 + hideKeyboard 报错）
- 首次冷跑模型要生成 `.xnnpack_cache`（分钟级），推理类用例超时给足

### 工具模式架构要点
- `constrained_tool_calls` **必须写在 workflow JSON 的 `"model"` 对象内**（loader 从那读，放 agent 层级会静默回退非约束路径）
- 工具实现必须并发安全（并行 dispatch 契约）；工具失败 = `{"error":...}` 结果串
- 审批门：`invoke` 阻塞在 worker 线程等 `registry.gates[toolCallId]`，Approve/Deny 经 `ChatViewModel.approve/deny(toolCallId)`
- 工具模式文本不流式（约束解码无流式变体）

---

## 4. 关键文件索引

- **spec**：`docs/superpowers/specs/2026-08-31-agent-tools-host-bridge-design.md`（§3 Python、§8 P2/P3 范围）
- **plan**：`docs/superpowers/plans/2026-08-31-agent-tools-host-bridge.md`（P1 已完成；P2/P3 出独立计划）
- **规范**：`AGENTS.md`（测试/工具架构/AAR 流程）
- **工具实现**：`app/src/main/java/com/zenwayne/zenagent/tools/`（FsWorkspace、FsTools、ApprovalGate、HostToolRegistry）
- **推理缝**：`app/src/main/java/com/zenwayne/zenagent/inference/`（RunEvent、AgentflowInferenceClient 的 toolsWorkflowJson）
- **E2E**：`tests/appium/tests/05_*.js`（读）、`06_*.js`（写/列/拒绝）、helpers/gestures.js（resetToChat/typeAndCommit/dismissKeyboard）
