# 交界文档 — Agent Tools 剩余工作与操作要点

**日期:** 2026-09-04（P2/P3/C/D 落地后更新）
**状态:** P1 已合并；**P2（Python）、P3（SAF 共享存储）已实现**；C/D 见 §2。

## 1. 已完成并合并

| 仓库 | PR | 内容 | 状态 |
|---|---|---|---|
| zen | [#41](https://github.com/ZenWayne/ZenAgent/pull/41) | JNI host-tool 桥（tool_call_id 透传、异步上行调用、事件面、约束入口、DSL 契约）、engine-upgrade 合并、bazel 锁 8.4.2 | MERGED |
| zen_mobile | [#2](https://github.com/ZenWayne/zen_mobile/pull/2) | FsWorkspace/FsTools/ApprovalGate、工具模式推理 + UI 接线、AGENTS.md、Appium 套件（含清数据卫生流程） | MERGED |

**设计依据**：`docs/superpowers/specs/2026-08-31-agent-tools-host-bridge-design.md`
**P1 实施计划**：`docs/superpowers/plans/2026-08-31-agent-tools-host-bridge.md`

---

## 2. 本轮完成情况

### A. P2 — Python 工具（Chaquopy）✅ 已实现

分支 `worktree-agent-tools-p2-p3`。**与原计划有两处刻意偏离，见下。**

- Chaquopy **17.0.0**，`app/src/main/python/zen_exec.py` 做 exec + 流捕获，仅标准库（无 pip）
- 分层：`PythonEngine`（接口）→ `ChaquopyEngine` → `PythonRunner`（单 worker 串行 / 尽力超时 / 100KB 上限）→ `PythonRunTool`（HostTool + 审批门）
- workflow JSON v2 的 `"tools"` 已加 `python_run`，system prompt 同步；UI 零改动（`ToolIcon.Code` 命中）
- **偏离 1 — 目标 Python 版本是 3.14 而非 3.13**：Chaquopy 要求构建机存在与目标同 major.minor 的 `python3`，本机只有 3.14（`/usr/bin/python3.14`）。锁 3.13 会在 configure 阶段直接失败。换机器时对齐 `python3 --version` 改 `app/build.gradle.kts` 的 `chaquopy.defaultConfig.version`。
- **偏离 2 — `Python.start()` 延迟到首次执行**，不放 `Application.onCreate`（计划原文如此）。理由：冷启动不该为一个门后工具付几百毫秒，而首次 `python_run` 本来就阻塞在审批门上。也因此不需要自定义 Application 类。
- **代价 — Gradle 配置缓存已关闭**（`gradle.properties`）。Chaquopy 17 与配置缓存不兼容：configure 阶段起 buildPython 子进程，且 `BuildPackagesTask` 持有不可序列化的 lazy。`--configuration-cache-problems=warn` 无效。等 Chaquopy 支持后再开。
- APK 49 MB（Python 运行时约 +11 MB）

**顺带修掉的真 bug**：`extractContent` 过去只做正则抽取、不解 JSON 转义，所以模型写多行文件时 `\n` 被原样写进文件。现统一走 `tools/JsonArgs.kt` 的 `jsonUnescape`（`JsonArgsTest` 11 例盯着，其中 8 例在修复前是红的）。

### B. P3 — SAF 共享存储 ✅ 已实现

- `FsBackend` 接口 + `FsRouter` 前缀分流：默认沙箱，`/shared` 开头才走授权树（模型忘前缀 → 落无害沙箱，这是安全的方向）
- `SafSharedStorage`（DocumentFile）+ `SharedStorageAccess`（`takePersistableUriPermission` + SharedPreferences），**每次调用重新校验实时授权**，系统设置里撤销即刻生效
- containment 走结构校验 `sharedSegments()`（SAF 无 canonical path），逐段拒 `..` 与控制字符
- 设置页新增「共享存储」行（授权 / 显示当前树 / 撤销），TestTags 已加
- **SAF 写非原子**（无 rename-into-place，只能 `"wt"` 截断）——与沙箱原子写的刻意差异
- **系统文件选择器不做自动化**：`08_shared_storage.test.js` 只覆盖设置页入口 + 未授权错误路径；授权后的读写是**手工验收项**（驱动另一 package 的 UI，各 OEM 布局不同，必碎）

### C. zen 宿主 PIC 修复 ⚠️ 已做好并验证，**差最后一步上传**

zen 分支 `fix/host-re2-pic`（worktree `.claude/worktrees/host-re2-pic`）。

- 定位：非 PIC 的只有 **re2 的 10 个 object**，不是整个归档 → 不必重编 LiteRT-LM（数小时 / 13GB 构建树），只重编 re2 再换进归档
- 脚本：`third_party/litert_lm/scripts/rebuild_re2_pic_host.sh`（成员名锚定匹配 + 符号集包含校验，防止把别的库的同名 object 换错）
- **已验证**：host `libagentflow_jni.so` 链接通过（x86-64），`kotlin/` 的 `gradle test` **6/6 全绿**（HostToolBridgeTest / SmokeTest / WorkflowJsonTest ×4），即原先挂掉的 JVM 测试层已恢复
- **剩下的一步（需要你）**：归档是按 URL 消费的。把
  `litert_build_90f/dist-host-90f-pic-libce_external.a`
  （sha256 `7d44e9cb9ca14b1e7e8f9fc68ec96a8c29148c9b54cb79a73a915fbb292d700e`）
  传到新的 release tag，再更新 MODULE.bazel 里 `litert_ce_external` 的 `urls` + `sha256`。
  本地 `file://` URL 只能验证、**不能提交**（只在一台机器上成立）。

### D. `.bcr-override` 绝对路径 ✅ 已修

zen 分支 `chore/bcr-override-relative`。`registry` 属性只接受 URL（必然是绝对路径），所以把声明挪到 `.bazelrc` 用 `%workspace%`：`common --registry=file://%workspace%/.bcr-override`（scheme 必须写，光 `%workspace%` 会被拒）。上游 BCR 必须显式列出——一旦指定 registry 就会替换默认值。
验证：`bazel mod deps` 通过（这本身就是证据：只有 wrapper registry 提供 compat 1 的 abseil），arm64 构建 1586/1586 全缓存命中、产物一致。

### E. 可选 / 明确不做

- numpy/pandas build flavor；约束解码流式；子代理执行轨道；bazel 9.x 兼容（同前）

---

## 3. 尚未完成的验证 ⚠️

**真机 E2E 一次都没跑。** 会话中途 XQ-BC72 从 USB 掉线（`adb devices` 空），无法恢复——重插线/解锁/重新授权 USB 调试是手工动作。

已就绪、等设备回来即可跑：

```bash
make e2e                                  # 全量（pm clear + 恢复模型 + 种子文件）
cd tests/appium && npm run test:python    # 07 python_run
cd tests/appium && npm run test:shared    # 08 /shared
```

**跑之前务必留意**：工具变多、system prompt 变长，**对小模型的工具选择是有风险的**——重点回归 `05`/`06`（fs 三工具）有没有被 `python_run` 抢走。这是本轮最可能出问题的地方。

已有的证据（非真机部分）：

- zen_mobile JVM 单测 **56/56 绿**（原 14 → 新增 42：JsonArgs 11、PythonRunner 8、PythonRunTool 6、FsRouter 9、SharedPath 8）
- RED 证据：把 `jsonUnescape` 短接成恒等后，8 个测试立刻失败
- APK 构建通过，Python 3.14 运行时确认打进 APK
- zen host JVM 测试 6/6（见 §2-C）

---

## 4. 操作要点（避免后来者重踩）

### 工具链
- **bazel 8.4.2**（zen `.bazelversion` 已锁）。裸二进制在 `~/.cache/bazelisk/downloads/sha256/4dc8e99.../bin/bazel`；**`/usr/bin/bazel` 是个找不到 8.4.2 的启动器**，跑 `kotlin/` 的 gradle 测试要把真二进制 PATH 前置。
- zen 构建需 `ANDROID_NDK_HOME=/opt/android-sdk/ndk/28.2.13676358`
- zen_mobile 构建需构建机 `python3` 与 Chaquopy 目标版本同 major.minor（当前 3.14）

### AAR 更新（跨仓库，AGENTS.md 有完整流程）
1. zen：`bazel build //jni:libagentflow_jni.so --config=android_arm64`
2. 拷入 `zen/android-inference/src/main/jniLibs/arm64-v8a/` → gradle `assembleDebug`
3. 拷 AAR 到 `zen_mobile/app/libs/agentflow-android.aar`（git-ignored）
4. **必须 `file` 校验 ARM aarch64**——bazel host/arm64 配置切换会覆盖 bazel-bin 输出
   - 本轮实测：checkout 里的 AAR 早于 PR #41，缺 `runJsonWorkflowConstrained`，**整个 app 编译不过**。新 checkout 第一件事就是按上面重建 AAR。

### 真机 E2E
- **一切 UI 交互走 Appium**，禁 adb 盲点盲打（中文 IME 会转写 `input text`）
- `make e2e`：pm clear 清数据（仅保留模型：暂存 `/data/local/tmp`，清后设备内 cp 恢复）+ 重建 workspace 种子文件
- Compose 文本输入：`//android.widget.EditText` 节点 + setValue；输入后不要立即 hideKeyboard（会丢 composing text）——`typeAndCommit` 已封装
- `pm clear` 后有瞬时竞态，recipe 已加 2s settle
- 设备 IME 必须是非转换 IME（Gboard Latin），Appium unicodeKeyboard 能力**不要用**
- 首次冷跑模型要生成 `.xnnpack_cache`（分钟级）；`python_run` 首次还要付 `Python.start()`，`07` 的首个断言超时给到 180s
- worktree 里跑测试需要自己 `cd tests/appium && npm ci`（node_modules 不共享）

### 工具模式架构要点
- `constrained_tool_calls` **必须写在 workflow JSON 的 `"model"` 对象内**（放 agent 层级会静默回退非约束路径）
- 工具实现必须并发安全；工具失败 = `{"error":...}` 结果串
- 审批门：`invoke` 阻塞在 worker 线程等 `registry.gates[toolCallId]`
- 工具模式文本不流式
- **工具参数是转义过的 JSON 字符串，必须解码后再用**（见 §2-A 的 bug）

---

## 5. 关键文件索引

- **spec**：`docs/superpowers/specs/2026-08-31-agent-tools-host-bridge-design.md`
- **规范**：`AGENTS.md`（测试 / 工具架构 / AAR 流程 / Chaquopy 构建约束）
- **工具实现**：`app/src/main/java/com/zenwayne/zenagent/tools/`
  - P1：`FsWorkspace` `FsTools` `ApprovalGate` `HostToolRegistry`
  - P2：`PythonEngine` `PythonRunner` `PythonTool` `ChaquopyEngine` + `app/src/main/python/zen_exec.py`
  - P3：`FsBackend` `FsRouter` `SafSharedStorage` `SharedStorageAccess`
  - 共用：`JsonArgs`
- **推理缝**：`app/src/main/java/com/zenwayne/zenagent/inference/`
- **E2E**：`tests/appium/tests/05_*`（读）、`06_*`（写/列/拒绝）、`07_*`（python）、`08_*`（/shared）
