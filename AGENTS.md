# AGENTS.md — ZenAgent (zen_mobile) 开发与测试规范

## 仓库概况

- Kotlin + Jetpack Compose (Material 3) Android 客户端，on-device AI agent 聊天应用。
- 原生推理引擎 `agentflow` 在 **zen 仓库**（C++/JNI），本仓库以 AAR 消费：`app/libs/agentflow-android.aar`（构建产物，git-ignored）。
- 工具链：Gradle 8.13（wrapper 锁定）、AGP 8.7.3、Kotlin 2.0.21、Compose BOM 2024.12.01；minSdk 26 / targetSdk 35；**仅 arm64-v8a**（真机 XQ-BC72）。
- 构建：`./gradlew :app:assembleDebug`；安装：`./gradlew :app:installDebug`。

## 测试规范（核心）

### 三层测试，各司其职

| 层 | 位置 | 跑法 | 依赖 |
|---|---|---|---|
| JVM 单元测试 | `app/src/test/` | `./gradlew :app:testDebugUnitTest --tests <类名>` | 无（纯 JVM，禁止真机/模型） |
| Appium E2E | `tests/appium/` | `make <suite>-<device>`（见下） | 真机 bc72 或 emulator |
| zen 宿主测试 | zen 工作树 `//tests/unit` + `kotlin/` | bazel test / gradle test | zen 工作树；kotlin 测试需 `MODEL_PATH` |

### 真机交互：必须走 Appium，禁止盲 adb

**一切与真机 UI 的交互验证（点击/输入/断言）必须通过 Appium 套件。**
禁止 `adb shell input tap/text` 盲点盲打——中文 IME 会转写 `input text`、坐标会随布局漂移，浪费时间且无断言价值。

Appium 套件（webdriverio v8 + mocha + chai）：

```bash
make appium                          # 启动 Appium 服务（端口 4723）
make <suite>-<device>                # 跑单个套件
make start-all-bc72                  # 启动 Appium + 全量套件（真机）
```

- suite：`smoke | states | inference | stop | approval | all`
- device：`bc72`（QV7808CA8G，arm64 + 模型，可跑推理）| `emu`（x86_64，仅 UI 套件 smoke/states/approval）
- 等价 npm 脚本：`cd tests/appium && npm run test:inference` 等；首次需 `cd tests/appium && npm install`
- 能力配置：`tests/appium/config/capabilities.js`（`autoLaunch:false`，`noReset:true`）
- 超时：smoke 90s / states 120s / approval 120s / inference+stop 180s

### Appium 元素定位约定（load-bearing）

- **Compose `Modifier.semantics { contentDescription = ... }` → UiAutomator2 的 content-desc → `driver.$('~标签')`。**
- 所有新 UI 测试锚点必须先加到 `ui/TestTags.kt`（已有标签见该文件）。
- 无标签时回退 `textXPath(text)` / `descXPath(label)`（`tests/appium/helpers/gestures.js`）。
- 文本输入用 `element.setValue(...)`（绕过 IME，杜绝中文转写）。
- 新测试文件：`tests/appium/tests/NN_<name>.test.js`，仿既有用例的 `before/afterEach`（失败自动截图到 `/tmp/zenagent-shots`）。
- 推理类套件前置条件：真机 bc72 + arm64 `.so` 在 AAR 中 + 模型已推送至
  `/sdcard/Android/data/com.zenwayne.zenagent/files/models/gemma-4-E2B-it.litertlm`。

### TDD 要求

实现任何逻辑（ViewModel 状态机、工具实现、解析）先写 RED 测试再实现；每个任务完成 = 测试命令 + 通过证据（非「看起来对」）。

## Agent 工具（tools）架构规范

- 工具由 zen 原生循环驱动：`agentflow.dsl.HostTool` 在 `tools/HostToolRegistry.kt` 注册，
  workflow JSON v2（`AgentflowInferenceClient.toolsWorkflowJson`）的 `"tools"` 数组声明名字。
- **工具实现必须并发安全**（zen 并行 dispatch 契约：同轮多工具并发调用）。
- `fs_write` 等副作用工具走审批门：`requiresApproval=true`，`invoke` 在 worker 线程阻塞
  `ApprovalGate.await()`；UI Approve/Deny 经 `ChatViewModel.approve/deny(toolCallId)` 找 `registry.gates[toolCallId]`。
- UI 事件源：`InferenceClient.runAgentWithTools(query): Flow<RunEvent>`
  （`Token | ToolCall | ToolReturn | Final`）。**工具模式文本不流式**（LiteRT-LM 约束解码无流式变体，spec Q6-b）。
- 工具失败 = `{"error": "..."}` 结果串（模型可见、可自愈），不是 run failure；`user_denied`/`cancelled`/`timeout` 同理。
- 沙箱：`getExternalFilesDir("workspace")`；读 512KB 上限、列 200 条上限、原子写（临时文件+rename）。

## AAR 更新流程（跨仓库）

1. zen 仓库改 DSL/JNI → `bazel build //jni:libagentflow_jni.so --config=android_arm64`（需 `ANDROID_NDK_HOME` 与 LiteRT-LM submodule）。
2. 拷贝 `.so` 到 `zen/android-inference/src/main/jniLibs/arm64-v8a/` → `./gradlew assembleDebug`。
3. `make aar` 或手动 `cp` 到本仓库 `app/libs/agentflow-android.aar`。
4. **强制校验：`file app/libs/agentflow-android.aar` 解包后 `.so` 必须是 `ARM aarch64`**——
   bazel host/arm64 配置切换会覆盖 `bazel-bin` 输出，x86-64 的 `.so` 打进 AAR 装真机即闪退。

## 提交与分支

- 功能分支 + PR（`feat/xxx`）；提交信息跟随仓库风格（`feat:` / `fix:` / `docs:` / `refactor:` / `test:`）。
- 每个提交自洽：单任务、带测试证据。
