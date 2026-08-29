# 实现计划 — Chat + Sub-Agent Run（FRD-ZEN-CSR-001 v1.0）

**日期:** 2026-08-29
**依据:** `docs/FRD-chat-sub-agent-run.md` v1.0（Approved）
**目标:** 将 FRD v1.0 全部需求在 `zen_mobile` Compose 实现中落地（UI 组件 + 状态数据 + 异常态全屏），真实本地推理接入**不包含**（见 `docs/superpowers/specs/2026-06-17-zen-inference-on-device-integration-design.md`）。

---

## 0. 现状 vs FRD 差距（Gap Analysis）

| FRD 需求 | 现状（`ChatScreen.kt` / `Models.kt`） | 差距 |
|---|---|---|
| FR-1.2 bot 圆形头像 | 无头像组件，仅名称 | ❌ 需加 bot 头像（36dp, #007AFF） |
| FR-1.4 Agent 树按钮 | `MoreHoriz` 占位 | ❌ 换 `git-branch` 图标 |
| FR-1.3 状态文案（Q3 决策） | 硬编码 `runStateLabel`，"Done" 无区分 | ⚠️ 需按 Q3 文案链实现 |
| FR-2.3 时间戳 | 无 | ❌ 新增 |
| FR-2.4 工具卡图标语义 | 文字 "{}" / "↳" | ⚠️ 换语义图标（status 色） |
| FR-2.5 子代理卡片四态 | 仅 `isDelegate` 泛化为 ToolCall | ❌ 新建 SubAgentCard（Goal/Summary/Pills/嵌套缩进） |
| FR-2.6 审批卡视觉 | 有功能，但白底、无警示图标 | ⚠️ 橙色底 #FFF8EC / stroke #FFE0A8 / shield-alert |
| FR-3.1 流式 ▍ + Generating Note | 无 | ❌ 新增 |
| FR-6.1 T1 ErrorEvent(recovered)+DegradedNote | 无 | ❌ 新增 |
| FR-6.2 T3 FailureBanner | 无 | ❌ 新增（View logs / Retry） |
| FR-6.3 T4 StoppedBanner | 无 | ❌ 新增（Restart / Resume） |
| FR-6.4 T5 Can'tStart 全屏 | 无 | ❌ 新建全屏 + Q4 二次确认 |
| FR-6.5 T6 SessionRestore 全屏 | 无 | ❌ 新建全屏 |
| FR-4.2/4.4 输入栏 | 静态占位 Text、无输入能力、非运行态显示 Add 图标 | ❌ 真实 TextField + 发送 arrow-up + 三种占位文案 |
| FR-8 数据模型 | ToolCall 缺 isNested/errorHint/recovered；无 SubAgentSummary/RunFailure/RunNote | ❌ 扩展 Models |

---

## 1. 执行序（Phase A–E，依赖驱动）

```
A(Models) ──► B(ChatScreen 重写) ──► D1(SampleData 场景) ──► E(验收)
              C5/T6 全屏（独立文件，可与 B 并行）
```

### Phase A — 数据模型扩展
**文件:** `app/src/main/java/com/zenwayne/zenagent/data/Models.kt`

- **A1** 新增 `RunNote`（sealed/enum: `Generating`、`Partial`、`Degraded`、`ErrorRecovered`）+ `data class RunNoteContent(type, text)`
- **A2** 新增 `SubAgentSummary`：`label, subtitle, depth, goal, summary?, toolCalls: List<ToolCall>, status: ToolStatus`
- **A3** `ToolCall` 增加：`isNested: Boolean = false`、`icon: ToolIcon`（enum：mapPin/hotel/search/flight/weather/security… 映射 Compose ImageVector）、`errorHint: String?`、`recovered: Boolean`
- **A4** 新增 `RunFailure(kind, title, message, node?, detail, canResume: Boolean)`（canResume=true → T4 形态，false → T3 形态）
- **A5** `Message` 增加：`timestamp: String?`、`subAgent: SubAgentSummary?`、`runNotes: List<RunNoteContent> = emptyList()`、`streaming: Boolean = false`、`failure: RunFailure?`
- **A6** 状态文案逻辑（Q3）移为 `RunStateUi.label(state, hasProposal)` / `.color(state)` 辅助对象（放 `ui/chat/` 或 `ui/theme/`），实现文案优先级：Failed/Stopped > AwaitingApproval > Running > 提案成功（"Sub-agent returned a proposal"）> Online

### Phase B — 聊天屏组件化重写
**文件:** `app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatScreen.kt`（拆分出的子组件建议单独文件 `ui/chat/components/`，保持单文件 ≤250 行）

- **B1** Header：bot 头像（复用 `ui/components/Avatar.kt` 或新建圆形 Icon）、AgentTreeBtn 换 `git-branch`
- **B2** `TimestampRow`（居中, 11dp, #8E8E93）
- **B3** 流式渲染：`streaming=true` 时气泡文本尾部追加 `▍` 光标；气泡下挂 `GeneratingNote`（loader + "generating… tap stop to cancel"，#007AFF）
- **B4** `ToolCallCard` 重构：语义图标（ToolIcon→ImageVector 映射）+ 状态 pill（Running: loader/#007AFF；Done: check/#34C759；结果标签如 "Found 3"）
- **B5** `SubAgentCard`（核心，FR-2.5 四态）：
  - 头部：`git-branch` #5856D6 图标 + label/subtitle（"Depth 1 · isolated context"）+ 状态 pill
  - Goal / Summary / Tool pills 区
  - `isNested` 工具缩进 40dp（NestIndent）
- **B6** `ApprovalCard` 视觉对齐：fill #FFF8EC、stroke #FFE0A8 1.5、shield-alert #FF9500、note 行 #9A7B3A；Deny→Failed 回调（Q2）
- **B7** `FailureBanner`（T3）：#FDEEEE/#F5C6C6、octagon-alert、三行详情（Kind pill/Node/Detail）、`View logs` + `Retry`（#007AFF）
- **B8** `StoppedBanner`（T4）：#F0F0F2/#DEDEE3、circle-stop、Note 行（"Resume re-runs…"）、`Restart` + `Resume`（#007AFF）
- **B9** `ErrorEventCard`（T1 recovered）：#D6E4FF stroke、info 图标、errorHint 行、recovered pill、chevron-down 折叠
- **B10** `RunNoteRow`（Partial/Degraded 提示行，`zap-off`/`hand`/`corner-down-right` 图标 + #B0B0B5 文本）
- **B11** InputBar 重构：
  - 真实 `BasicTextField`（占位按 RunState：Idle="Message Travel Planner…"（取 agent.name）／Running="Running…"／AwaitingApproval="Running · messages will queue…"）
  - 非运行：**蓝色 `arrow-up` 发送按钮**（当前误用 Add 图标）；运行/审批：红色 Stop（square 图标）
  - 空输入发送禁用（alpha 0.4）

### Phase C — 异常态全屏（独立文件，可与 B 并行）
- **C1** `ui/errors/CanStartFailedScreen.kt`（T5）：X 关闭、`Run agent` 顶栏、shield-alert hero（72dp/#FFF1E0）、标题/副文案、Issues 卡（图标 26dp + 标题/描述 + "+1 more issue"）、`Edit workflow`（#007AFF）+ `Run unsigned (dev only)`（Q4：点击弹二次确认 AlertDialog，确认后回聊天屏继续）
- **C2** `ui/errors/SessionRestoreErrorScreen.kt`（T6）：`Sessions` 顶栏（chevron-left）、database hero（#EFEAFB/#5856D6）、详情三行（Kind pill/Session/Detail）、DataLoss 警告（#FFF8EC 带 triangle-alert）、`Start fresh` + `Pick another session`
- **C3** AppRoot 路由：`canStartError: T5Error?` / `sessionRestoreError: Boolean?` 状态字段 + 全屏切换（SampleData 预置一个坏会话用于演示 T6）

### Phase D — 演示数据与状态行为
- **D1** `SampleData.kt`：为 12 屏各增加一个代表性会话（Running live / AwaitingApproval / T1 自愈 / T3 OOM / T4 Stopped / Succeeded 提案态），字段对齐 A1–A5
- **D2** `AppRoot.kt`（或最小 state hoist）：onApprove/onDeny/onStop 回调驱动 runState 转移（Deny→Failed per Q2；Stop→Stopped；Approve→Running），以便交互验证

### Phase E — 验收
- **E1** `./gradlew :app:assembleDebug` 构建通过；`lsp_diagnostics` 清零
- **E2** 安装真机/模拟器，逐屏截图对照设计稿（12 屏 mapping）
- **E3** FRD 验收矩阵（§8）逐条勾验：流式 ▍、Stop 状态、Deny→Failed、T1–T6 六横幅/全屏、输入栏三态

---

## 2. 明确不实现（保持 Out of scope）

- 真实本地推理 / streamTokens 接入（已有专门 spec）
- 消息队列化（FRD Q1 → P1，接口 `pendingQueue` 预留字段即可，不建队列逻辑）
- 工作流编辑器、"Edit workflow" 详情页、Agent 树展开详情（保留入口按钮）
- i18n 资源、无障碍完整验收（NFR-5/6 单独立项）

---

## 3. 验证命令

```bash
./gradlew :app:assembleDebug          # 构建
./gradlew :app:installDebug           # 安装
# 截图对照：adb exec-out screencap -p > /tmp/zen-s1.png
```
