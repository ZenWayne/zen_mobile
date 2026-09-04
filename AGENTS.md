# AGENTS.md — ZenAgent (zen_mobile) 开发与测试规范

## 仓库概况

- Kotlin + Jetpack Compose (Material 3) Android 客户端，on-device AI agent 聊天应用。
- 原生推理引擎 `agentflow` 在 **zen 仓库**（C++/JNI），本仓库以 AAR 消费：`app/libs/agentflow-android.aar`（构建产物，git-ignored）。
- 工具链：Gradle 8.13（wrapper 锁定）、AGP 8.7.3、Kotlin 2.0.21、Compose BOM 2024.12.01；minSdk 26 / targetSdk 35；**仅 arm64-v8a**（真机 XQ-BC72）。
- **Chaquopy 17.0.0**（`python_run` 工具的内嵌 CPython）带来两条构建约束：
  - **构建机必须有与目标同 major.minor 的 python3**。当前 `chaquopy.defaultConfig.version = "3.14"`，跟随本机默认 `python3`。换机器若没有 3.14，改这个值去对齐 `python3 --version`（Chaquopy 17 支持 3.10–3.14），否则 configure 阶段直接失败。
  - **配置缓存必须关闭**（`gradle.properties` 已置 `org.gradle.configuration-cache=false`）。Chaquopy 在 configure 阶段起 buildPython 子进程，且 `BuildPackagesTask` 持有无法序列化的 lazy（store 时报 `env/<variant>/lib does not exist`）。`--configuration-cache-problems=warn` 绕不过去。
- 构建：`./gradlew :app:assembleDebug`；安装：`./gradlew :app:installDebug`。

## 测试规范（核心）

### 三层测试，各司其职

| 层 | 位置 | 跑法 | 依赖 |
|---|---|---|---|
| JVM 单元测试 | `app/src/test/` | `./gradlew :app:testDebugUnitTest --tests <类名>` | 无（纯 JVM，禁止真机/模型；Chaquopy/SAF 都在接口后面 mock 掉） |
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

- suite：`smoke | states | inference | stop | approval | toolmode | toolmode-se | python | shared | all`
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
- **每测试进程级隔离**：`resetToChat` 先 `terminateApp` 再拉起（会话状态仅内存，新进程=干净 ViewModel，回到默认 c1）。
- **整套件开跑前清空状态**（`make e2e` 或 `make reset-device`）：**定向删除**，不碰
  `files/models/`。清掉 `shared_prefs`（含 App 侧存的 `/shared` 授权）、`cache`、
  `code_cache`、`databases`、内部 `files`、以及 workspace，然后重建 `hello.txt`。
  内部数据只能经 `run-as` 删（debug 包才行）。测试不得依赖上一次运行遗留的任何文件状态。
  - **为什么不用 `pm clear`**：它会连 `files/models/` 一起清掉，于是每跑一次都要设备内
    拷 2.6GB 模型回来，还会毁掉约 790MB 的 `.xnnpack_cache`，让首个推理用例再付一次生成。
  - **`reset-device-full`（`pm clear`）仍在**：定向删除有一个缺口——**持久化的 SAF URI
    授权存在系统里、不在 App 数据里，所以它删不掉**。App 从 `shared_prefs` 读授权，
    那个被清了，所以 `/shared` 仍报未授权；但系统侧的授权会残留。要排除这类残留时用它。
  - 模型暂存在 `/data/local/tmp`（App 数据之外），`stage-model` 只在暂存副本缺失时才走
    USB 推送。
- 推理类套件前置条件：真机 bc72 + arm64 `.so` 在 AAR 中 + 模型已推送至
  `/sdcard/Android/data/com.zenwayne.zenagent/files/models/gemma-4-E2B-it.litertlm`。
- **整套 smoke 全红时先查窗口焦点**：`adb shell dumpsys window | grep mCurrentFocus`。
  通知栏被下拉（`mCurrentFocus=NotificationShade`）会让 UiAutomator 查到通知栏而不是 App，
  所有元素查找返回空、看着像代码回归——但 App 其实好好的（`pidof` 有进程、
  `ResumedActivity` 就是 MainActivity）。`adb shell cmd statusbar collapse` 收起即可。
  同理 `logcat -b crash` 里的崩溃未必是本 App 的，**先看包名**（踩过一次：是别的 App 在崩）。
- **`/shared` 的授权步骤是手工的**：`08_shared_storage.test.js` 只覆盖设置页入口和未授权
  错误路径。真正点系统文件选择器要驱动另一个 package 的 UI（各 OEM/版本布局不同），
  自动化必碎——授权后的读写属于**手工验收项**，别为它写 Appium。

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
- **工具参数必须解码**：约束解码给的是 JSON 串，字符串值是转义过的。`tools/JsonArgs.kt`
  的 `extractPath/extractContent/extractCode` 统一走 `jsonUnescape`——直接把 `\n`
  原样写进文件或喂给 Python 是 bug（`JsonArgsTest` 盯着这条）。

### `python_run`（P2，Chaquopy）

- 分层：`PythonEngine`（接口）→ `ChaquopyEngine`（真实 CPython）→ `PythonRunner`
  （队列/超时/上限）→ `PythonRunTool`（HostTool + 审批门）。JVM 单测用假 engine，
  测试机上不需要 Chaquopy。
- **单解释器 → 单 worker 串行**：这就是本工具对并行 dispatch 并发契约的答案。
- **超时是尽力而为**：CPython 无法中断，超时后 worker 被 detach（继续以 daemon 线程跑），
  换新 worker 顶上，调用方拿 `{"error":"timeout"}`。超时窗口含排队时间。
- **无沙箱（已知并接受，spec §9.3）**：Python 拥有 App 全部权限（含 `java` 模块）。
  缓解手段只有：审批门 + 串行队列 + 100KB 代码上限 + 尽力超时。
- `Python.start()` 延迟到首次执行（在 worker 线程上），不放 `Application.onCreate`——
  否则每次冷启动都要付这个钱，而首次 `python_run` 本来就卡在审批门后面。
- 执行/捕获在 `app/src/main/python/zen_exec.py`：重定向 `sys.stdout/stderr`，
  代码自身的异常打成 traceback 进 stderr（模型可读可自愈），不算工具失败。

### `/shared` 共享存储（P3，SAF）

- `FsBackend` 是 fs 三工具面对的接口；`FsWorkspace`（java.io 沙箱）和
  `SafSharedStorage`（SAF 树）各实现一份，`FsRouter` 按前缀分流。
- **`DocNode` 是 SAF 的可测缝**（同 `PythonEngine` 的套路）：`DocumentFile`/
  `ContentResolver` 在 JVM 上不存在，所以遍历与策略判断（哪一段必须是目录、何时创建、
  读/列上限、二进制识别）都写在缝之上，用内存假树单测；`DocumentFileNode` 是薄适配层。
  **别把判断逻辑写进适配层**——那里没有测试覆盖。
- SAF provider 是第三方 App，**类型标志不可信**：`descend()` 明确拒绝穿过非目录节点，
  哪怕它照样返回子节点（`aNonDirectoryIsNotTraversedThroughEvenIfItResolvesChildren` 盯着）。
- provider 报的 `length` 只是提示，读回来还要**按实际字节再判一次上限**。
- **默认落沙箱**：只有以绝对段 `/shared` 打头才走授权树。模型忘了前缀 → 写进无害的沙箱，
  这是安全的方向。
- 授权：设置页 →「共享存储」→ `ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission`；
  URI 存 SharedPreferences，**每次工具调用重新校验实时授权**，所以在系统设置里撤销会立刻生效。
- **containment 靠结构校验**：`DocumentFile` 没有 canonical path，无法像沙箱那样前缀比对。
  `sharedSegments()` 逐段拒绝 `..` 与控制字符（`SharedPathTest` 盯着）。
- **SAF 写不是原子的**：没有 rename-into-place，只能 `"wt"` 截断写——与沙箱的原子写不同，
  这条差异是刻意的。
- 未授权时工具返回 `{"error":"shared_not_authorized"}`。

## AAR 更新流程（跨仓库）

1. zen 仓库改 DSL/JNI → `bazel build //jni:libagentflow_jni.so --config=android_arm64`（需 `ANDROID_NDK_HOME` 与 LiteRT-LM submodule）。
2. 拷贝 `.so` 到 `zen/android-inference/src/main/jniLibs/arm64-v8a/` → `./gradlew assembleDebug`。
3. `make aar` 或手动 `cp` 到本仓库 `app/libs/agentflow-android.aar`。
4. **强制校验：`file app/libs/agentflow-android.aar` 解包后 `.so` 必须是 `ARM aarch64`**——
   bazel host/arm64 配置切换会覆盖 `bazel-bin` 输出，x86-64 的 `.so` 打进 AAR 装真机即闪退。

## 提交与分支

- 功能分支 + PR（`feat/xxx`）；提交信息跟随仓库风格（`feat:` / `fix:` / `docs:` / `refactor:` / `test:`）。
- 每个提交自洽：单任务、带测试证据。
