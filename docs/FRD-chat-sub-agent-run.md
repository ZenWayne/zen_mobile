# FRD — ZenAgent 聊天与子代理运行 (Chat + Sub-Agent Run)

| 项目 | 内容 |
|---|---|
| 文档编号 | FRD-ZEN-CSR-001 |
| 版本 | v1.0（Approved） |
| 日期 | 2026-08-29 |
| 版本说明 | v0.9 → v1.0：开放问题 Q1–Q4 已决（§9），决策同步至 FR-2.6 / FR-4.5 / FR-6.4 |
| 产品 | ZenAgent（Android 本地 AI Agent 聊天应用） |
| 依据设计稿 | `zen/design/zenagent.lib.pen` — Frame `Ⓣ Chat + Sub-Agent Run`（`hBsYj`）及相关运行态屏幕（12 屏） |
| 文档状态 | 已评审（Approved） |

---

## 1. 概述 (Overview)

### 1.1 产品背景

ZenAgent 是一个 Android 端的本地 AI Agent 聊天应用：用户与 Agent（如 "Travel Planner"）对话，Agent 通过**工具调用**（tool calls）完成任务，并在需要时**委托子代理**（sub-agent）以隔离上下文执行子任务。运行过程完整暴露给用户：工具执行状态、子代理进度、需要确认的副作用操作（approval gate）、以及失败/停止等异常结局。

本文档定义 **聊天 + 子代理运行** 这一核心链路的全部功能需求，范围涵盖 12 张设计稿屏幕中的：正常聊天、运行中（live）、工具事件、子代理卡片、审批门、自愈降级（T1/T2）、OOM 失败（T3）、用户停止（T4）、无法启动（T5）、会话无法恢复（T6）。

### 1.2 目标

- 将设计稿中的交互与状态完整转化为可验收的功能需求。
- 明确每一状态下的**界面元素、交互、状态转移、异常处理**。
- 为工程实现（Compose 组件、状态机、数据模型）与测试提供依据。

### 1.3 范围 (Scope)

**包含 (In scope):**
- 聊天主界面（Header / Messages / Input Bar）全状态
- 消息气泡（用户 / Agent / 时间戳）
- 工具事件卡片（Running / Done / Failed / recovered）
- 子代理卡片（正在运行 / 已完成 / 嵌套工具）
- 审批门（Approval required — Deny / Approve & run）
- 运行生命周期：运行中、自愈（T1/T2）、OOM 失败（T3）、停止（T4）、无法启动（T5）、会话无法恢复（T6）
- 输入栏状态切换（可输入 / 运行中占位 / Stop 按钮）

**不包含 (Out of scope):**
- 侧边栏抽屉与设置页（已有独立 FRD 需求）
- 工作流编辑器（T5 中 "Edit workflow" 仅保留入口，详细需求另立文档）
- Agent 树展开详情（"AgentTreeBtn" 仅保留入口）

---

## 2. 术语 (Glossary)

| 术语 | 定义 |
|---|---|
| **Run（运行）** | 一次 Agent 响应请求的完整执行，从用户发送消息到产生最终结果（或失败/停止） |
| **Tool Call（工具调用）** | Agent 执行的单个外部操作，如 `search_hotels`、`get_weather`、`book_hotel` |
| **Sub-agent（子代理）** | Agent 委托的、在隔离上下文中执行的子任务执行器，如 "Hotel Specialist" |
| **Approval Gate（审批门）** | 工具即将产生副作用（如预订、写操作）时暂停运行，等待用户确认 |
| **RunState** | 运行生命周期状态：Idle / Running / AwaitingApproval / Succeeded / Degraded / Failed / Stopped |
| **T1–T6** | 设计稿定义的异常/分支场景编号（见 §6） |
| **KV-cache** | 模型推理时的键值缓存；其分配失败是 T3 OOM 的典型原因 |

---

## 3. 用户场景与优先级 (User Scenarios & Priority)

| 场景编号 | 描述 | 优先级 |
|---|---|---|
| S1 | 用户发送消息，Agent 正常回复（无工具/纯文本） | P0 |
| S2 | Agent 调用工具并成功（如 `search_hotels` → Done） | P0 |
| S3 | Agent 委托子代理完成任务（独立上下文、深度 1） | P0 |
| S4 | 工具需副作用操作，弹出审批门（Deny / Approve & run） | P0 |
| S5 | 运行中：流式输出 + Stop 按钮 | P0 |
| S6 | 工具调用出错但 Agent 自行修复，运行降级完成（T1/T2） | P1 |
| S7 | 运行失败（OOM 等），展示失败详情 + 重试（T3） | P1 |
| S8 | 用户停止运行，保留部分结果，可 Restart / Resume（T4） | P1 |
| S9 | 工作流校验失败，无法启动 Agent（T5） | P1 |
| S10 | 会话检查点损坏，无法恢复会话（T6） | P1 |

---

## 4. 界面结构 (Screen Architecture)

聊天屏（390 × 844，cornerRadius 40，fill #F5F5F5）固定三段式垂直布局：

```
┌──────────────────────────────┐
│ Status & Header (56h, 白底)   │  ← 返回 / 头像 / 名称+状态 / Agent树按钮
├──────────────────────────────┤
│ Messages Area (flex, bottom) │  ← 消息流：气泡/工具卡片/子代理卡/审批卡/横幅
│  padding 16, gap 12          │
├──────────────────────────────┤
│ Input Bar (白底, 顶部分隔线)   │  ← [+] [输入框] [发送/停止]
│  padding [10,16,34,16]       │
└──────────────────────────────┘
```

---

## 5. 详细功能需求 (Functional Requirements)

### FR-1 聊天头部 (Status & Header)

**FR-1.1 头部基本信息**
- 左侧：`chevron-left` 返回按钮（36×36 触区，图标 24，色 #007AFF）。
- 返回行为：从聊天屏返回会话列表（侧边抽屉）。

**FR-1.2 Agent 标识**
- 圆形头像（36×36，cornerRadius 18，fill #007AFF，内含白色 `bot` 图标 20）。
- 名称文本（Inter 16/600，#1C1C1E），如 "Travel Planner"。

**FR-1.3 运行状态文本（statusText）**
- 位于名称下方（Inter 12/400），随 RunState 动态变化；文案与颜色映射：

| RunState | 文案（设计稿） | 颜色 |
|---|---|---|
| Running | "Generating…" | #007AFF |
| AwaitingApproval | "Awaiting approval…" | #FF9500 |
| Succeeded | "Sub-agent returned a proposal" / "Online" / "Done"（语义随内容） | #34C759 |
| Failed | "Run failed" | #FF3B30 |
| Stopped | "Stopped" | #8E8E93 |

**决策 Q3**：文案 "Sub-agent returned a proposal" 的触发条件 = **运行成功结束，且结果中包含子代理提案**（子代理已返回 proposal 待用户消费）；普通成功（无子代理）显示 "Online"；含子代理但未返回提案（被停止/失败）显示对应状态文案。文案优先级：Failed/Stopped > AwaitingApproval > Running > Succeeded（含子代理提案）> Online。

**FR-1.4 Agent 树入口**
- 右侧 `git-branch` 图标按钮（36×36，fill #F2F2F7，图标 #007AFF）。
- 点击打开 Agent 树视图（本期仅保留入口，展开详情 Out of scope）。

**验收标准 (FR-1):**
- 头部在 56dp 高度内完整呈现，无溢出；状态文本在 12 个场景下均显示正确文案与颜色。

---

### FR-2 消息区 (Messages Area)

消息区采用垂直 flex 布局（`justifyContent: end`，即新消息贴底），padding 16，gap 12。滚动：最新消息自动滚到底部；运行中禁止手动滚动（或自动回弹到底部，见 FR-4.5）。

#### FR-2.1 用户消息气泡 (Sent Bubble)
- 右对齐，宽 250–260（max width），fill #007AFF，cornerRadius 18，padding [10,14]。
- 文本白色（Inter 15/400），`fixed-width` 换行。

#### FR-2.2 Agent 消息气泡 (Received Bubble)
- 左对齐，宽 250–268，fill #E8E8EA，cornerRadius 18，padding [10,14]。
- 文本 #1C1C1E（Inter 15/400）。

#### FR-2.3 时间戳 (Timestamp)
- 会话首个消息上方居中显示，如 "Today 14:30"（Inter 11/400，#8E8E93）。

#### FR-2.4 工具事件卡片 (Tool Card)
工具执行状态以卡片形式内嵌于消息流，卡片含三部分：

- **图标区**：30×30，cornerRadius 8，背景与状态色同色系（Running: #E9F1FF/图标 #007AFF；Done: #E9F9EF/图标 #34C759；Fail: #E9F1FF/图标 #007AFF 或 #FDE7E7/红）。图标语义随工具类型（map-pin、hotel、search、plane-takeoff…）。
- **名称 + 描述**：工具名（Inter 13/600）如 `search_attractions`；描述（Inter 12/400，#8E8E93）如 "Kyoto · temples + food"。
- **状态 pill**（右侧）：
  - Running：`loader` 图标 + "Running"（#007AFF，#E9F1FF 底）
  - Done：`check` + "Done"（#34C759，#E9F9EF 底）
  - 带结果时显示结果标签，如 "Found 3"（绿色）
- 卡片样式：fill #FFFFFF，cornerRadius 12，stroke #E5E5EA 1px，padding [10,12]，gap 10。

**验收 (FR-2.4):** 状态变化时（Pending→Running→Done/Failed）pill 与图标颜色同步更新，无闪跳。

#### FR-2.5 子代理卡片 (Sub-Agent Card)
子代理委托以紫色系卡片呈现（fill #F2F0FB，stroke #E2DCF6，cornerRadius 12，padding [10,12]，gap 8），结构：

- **头部**（gap 10）：`git-branch` 紫色圆角图标（30×30，#5856D6）；
  - 标题 "Hotel Specialist · Sub-agent"（13/600）
  - 副标题 "Depth 1 · isolated context"（11/400，#6C6C70）
  - 状态 pill：Running（#E9F1FF/#007AFF + loader）或 Done（#E9F9EF/#34C759 + check）
- **Goal 行**（12/400，#1C1C1E，lineHeight 1.4）：`Goal: find top-rated hotels in Shinjuku, ≤ ¥50k/night`
- **Summary 行**（12/400，#6C6C70，lineHeight 1.5，做完后显示）：`Narrowed 34 → 3 candidates. Recommend Hotel Gracery Shinjuku.`
- **Tool pills**（垂直，gap 4）：子代理内部工具调用，样式与 FR-2.4 一致但更紧凑（icon 12+text 11/600，padding [6,8]，cornerRadius 8，fill #FFFFFF，stroke #ECE8F8）：
  - 成功工具：`search_hotels`（check #34C759）、`compare_by_reviews`（map #34C759）
  - 待确认工具：`book_hotel · awaiting confirm`（`shield-alert` #FF9500，底色 #FFF8EC，stroke #FFE0A8，右侧黄色 "Confirm" 标签）

**FR-2.5.1 嵌套工具（子代理运行中）**
- 运行中的子代理卡片会列出一个**缩进 40px** 的嵌套工具行（NestIndent）：工具图标 22×22 + 名称（12/600）+ 右侧结果 pill（如 `Found 3`）。
- 该行仅在子代理运行中显示；完成后由 Tool pills 汇总替代。

**验收 (FR-2.5):**
- 委托时（Delegate · Hotel Specialist）→ 运行中（Running + 嵌套工具）→ 完成（Done + Goal + Summary + Tool pills）状态逐步正确。
- 子代理卡片的四态（委托中/运行中/已恢复/完成）均有对应设计。

#### FR-2.6 审批门卡片 (Approval Card)
当 Agent 即将执行**有副作用的工具**时，消息流中出现橙色审批卡（fill #FFF8EC，stroke #FFE0A8 1.5px，cornerRadius 12，padding [12,14]，gap 10）：

- **头部**：`shield-alert` 橙色图标（30×30，#FF9500）；"Approval required"（14/600）+ "Agent wants to run an action with side effects"（12/400，#9A7B3A）。
- **详情区**（白底，cornerRadius 8，padding [10,12]，gap 6）两行：
  - `Tool`：如 `book_hotel`（13/600）
  - `Args`：如 `Hotel Gracery Shinjuku · 5 nights · ¥48,000`（13/400，#1C1C1E）
- **按钮区**（gap 10，高 40）：
  - `Deny`（白底，#FF3B30 文字 + x 图标，stroke #E5E5EA）
  - `Approve & run`（#007AFF 底，白色文字 + check 图标）

**FR-2.6.1 行为定义**

| 分支 | 行为 |
|---|---|
| **Deny** | 拒绝该工具执行；运行转为 **Failed**（决策 Q2，可直接重试）；审批卡收起；输入栏恢复可用 |
| **Approve & run** | 工具继续执行；审批卡变为常规工具卡片（Running→Done）；运行继续 |
| **审批等待期** | RunState = AwaitingApproval；头部状态文本 "Awaiting approval…"；输入栏进入 "Running · messages will queue…" 状态（见 FR-4.4），右侧为红色 Stop 按钮 |

**验收 (FR-2.6):**
- 审批卡仅在工具需审批时出现；Deny / Approve 两路径均正确转移状态；Args 完整显示工具参数（含价格/数量）。
- **Q2 验证**：Deny 后头部状态显示 "Run failed"（#FF3B30），失败横幅可点 Retry 重新运行（复用 FR-6.2 失败横幅样式，Kind 为 Denied/Aborted）。

---

### FR-3 运行中状态 (Live Running)

**FR-3.1 流式输出**
- Agent 回复采用流式生成：气泡文本随 token 逐个出现，末尾显示 `▍` 光标符。
- 生成中的 Agent 气泡底部附带 "generating… tap stop to cancel" 提示行（`loader` 图标 + 文本，#007AFF，11/400）。

**FR-3.2 头部状态**："Generating…"（#007AFF）。

**FR-3.3 输入栏**：占位文本切换为 "Running…"，输入框不可编辑；右侧发送按钮切换为红色 **Stop** 按钮（#FF3B30 36×36，内含白色 13×13 方形 glyph）。

**FR-3.4 Stop 行为**：立即取消当前生成；RunState = Stopped；保留已生成的部分内容 + "Partial Note"（见 FR-6.3）；停止后输入栏恢复可输入。

**验收 (FR-3):** 运行中流式文本持续追加；Stop 后 200ms 内取消生成并保留部分结果，无崩溃、无死锁。

---

### FR-4 输入栏 (Input Bar)

**FR-4.1 静态结构**：`[+ Add 按钮(36×36，#E8E8EA)] [输入框(fill #F2F2F7，cornerRadius 20)] [发送按钮(#007AFF 36×36，white arrow-up)]`，父容器白底，顶部分隔线 stroke #E5E5EA 0.5px，padding [10,16,34,16]，gap 10。

**FR-4.2 占位文案表**

| RunState | 占位文案 |
|---|---|
| Idle / Succeeded / Failed / Stopped | "Message Travel Planner…" |
| Running | "Running…" |
| AwaitingApproval | "Running · messages will queue…" |

**FR-4.3 发送禁用条件**：输入为空时不显示可发送态（设计稿仅表现常态）；Running / AwaitingApproval 时不可编辑。

**FR-4.4 发送按钮形态切换**：
- Idle/Succeeded/Failed/Stopped → 蓝色发送 `arrow-up`；
- Running/AwaitingApproval → 红色 `square` Stop 按钮。

**FR-4.5 运行中消息排队**（决策 Q1）：
- **本期（v1.0）**：WaitApproval/Running 期间输入框禁用，仅显示占位提示 "Running · messages will queue…"，**不做真正的消息队列**。
- **P1 演进**：真实队列化——用户输入进入队列，审批通过/运行结束后按序提交。接口预留（`pendingQueue: List<String>`），本期不实现。

**验收 (FR-4):** 六类设计稿状态（正常/运行中/审批中/失败/停止/自愈完成）对应输入栏形态全部正确；审批等待期输入框不可编辑、无内容丢失。

---

### FR-5 运行生命周期状态机 (Run State Machine)

状态集合（与 `RunState` 枚举一致）：

```
Idle → Running → AwaitingApproval ─→ Approve → Running
  │        │                            └→ Deny → Failed
  │        ├─ Stop → Stopped
  │        ├─ 工具自愈成功 → Degraded → Succeeded(降级完成)
  │        └─ 不可恢复错误(OOM等) → Failed
  └─ (发送消息) → Running
```

**FR-5.1 状态转移表**

| 当前 | 事件 | 目标 | 触达界面行为 |
|---|---|---|---|
| Idle | 发送消息 | Running | 用户气泡入流；头部 "Generating…"；输入栏禁用 |
| Running | 工具需审批 | AwaitingApproval | 审批卡出现；头部 "Awaiting approval…" |
| AwaitingApproval | Approve & run | Running | 审批卡→工具卡；运行继续 |
| AwaitingApproval | Deny | Failed | 失败横幅（含 kind/detail）；可 Retry |
| Running | Stop | Stopped | Stop 横幅；部分结果保留**
| Running | 工具出错且重试成功 | Degraded → (Succeeded) | recovered 事件卡；降级提示行 |
| Running | 不可恢复错误 | Failed | 失败横幅（Kind/Node/Detail + View logs/Retry） |
| Failed | Retry | Running | 重发运行；失败横幅移除 |
| Stopped | Restart | Running | 从头重新执行 |
| Stopped | Resume | Running | 从断点继续（"Resume re-runs the cancelled step from a clean state."） |

**验收 (FR-5):** 状态机覆盖 S1–S10 全部场景；任一转移路径无非法中间态。

---

### FR-6 运行结局与异常场景 (T1–T6)

设计稿以 T1–T6 编号的六类分支场景，全部在**同一聊天屏**内通过消息流元素表达，不跳转页面。

#### FR-6.1 T1+T2 — 工具错误自愈 (Self-healed Run)

场景：`get_weather` 调用出错（API 503），Agent 自动重试成功；输出 JSON 无效被降级为 raw text 处理。

消息流（自上而下）：
1. 时间戳 "Today 09:12"
2. 用户气泡（天气问题）
3. Agent 气泡 "Let me check the forecast."
4. **Error Event (recovered) 卡** — 白底，stroke #D6E4FF：`info` 图标（#E9F1FF/#007AFF） + 工具名 `get_weather`（13/600） + "tool_error · API 503 — agent retried"（12/400，#8E8E93） + "recovered" pill（#E9F1FF/#007AFF） + 右侧 `chevron-down` 折叠指示（点击展开错误详情，P1）
5. 工具卡（Done 态，同 §FR-2.4）
6. **降级提示 (Degraded Note)**：`corner-down-right` 图标 + "output wasn't valid JSON — used raw text"（11/400，#B0B0B5，居中）
7. Agent 最终气泡 "Tokyo next week: mostly sunny, 18–24°C. ☀️"

要求：失败→重试→成功全链路对用户透明；降级行为必须显式提示。头部状态 "Online"（#34C759）。

#### FR-6.2 T3 — 运行失败 OOM (Run Failed)

场景：合同摘要任务中途 OOM（KV-cache allocation failed）。

消息流：
1. 时间戳 "Today 11:48"
2. 用户气泡（80 页合同摘要）
3. Agent 部分输出气泡 + **Partial Note**：`zap-off` 图标 + "partial output — generation stopped mid-stream"（#B0B0B5）
4. **失败横幅 (Failure Banner)** — fill #FDEEEE，stroke #F5C6C6 1.5px：
   - 头部：`octagon-alert` 红色图标（#FF3B30）；"Run failed"（14/600）+ "Out of memory during generation"（12/400，#9A3A3A）
   - 详情表（白底，cornerRadius 8）：三行 `Kind`（pill，如 "OOM" #FDE7E7）/ `Node`（如 "main · depth 0"）/ `Detail`（"KV-cache allocation failed (model too large for device)"）
   - 按钮：`View logs`（白底，#636366，file-text 图标）+ `Retry`（#007AFF 主按钮）
5. "View logs" 展示运行日志（OS 日志页入口）；"Retry" 重新发起运行（新 Run）。

#### FR-6.3 T4 — 用户停止 (Stopped)

场景：欧盟 10 城机票对比，用户在第 3 城后点击 Stop。

消息流：
1. 时间戳 "Today 16:05"
2. 用户气泡
3. Agent 部分输出 "+ **Partial Note**："you stopped the run — partial results kept"（`hand` 图标）
4. **停止横幅 (Stopped Banner)** — fill #F0F0F2，stroke #DEDEE3：
   - 头部：`circle-stop` 灰色图标（#8E8E93）；"Stopped"（14/600）+ "Cancelled after 3 of 10 cities"（12/400，#636366）
   - 详情：`Kind` pill（灰）+ `Note`："Resume re-runs the cancelled step from a clean state."
   - 按钮：`Restart`（白底）+ `Resume`（#007AFF 主按钮，play 图标）
5. **Resume 语义**：从取消点重新执行取消的步骤（从清明状态重跑）；**Restart**：整个任务重开。

#### FR-6.4 T5 — 无法启动 Agent (Can't Start)

独立全屏（非聊天流内）：顶部栏 `X` 关闭 + 标题 "Run agent"。

内容区（居中，gap 16）：
- 黄橙警示 hero（72×72，#FFF1E0，`shield-alert` #FF9500 34）
- 主标题 "Can't start this agent"（22/700）
- 副文案 "The workflow failed validation and won't run."（15/400，#636366）
- **Issues 卡**（白底，cornerRadius 12，stroke #EAEAEC，gap 12，padding 14）：校验问题列表，每条含 26×26 图标（#FFF1E0 或 #FDE7E7）+ 标题/描述；问题多于 2 条显示 "+1 more issue"（12/600，#8E8E93）。问题行间分隔线（#EEEEF0 1px）。
- 按钮：`Edit workflow`（#007AFF 主按钮 46h，file-pen 图标）+ `Run unsigned (dev only)`（白底，shield-off 图标，#636366）

行为：关闭 X → 返回上一屏；Edit workflow → 工作流编辑器（Out of scope）；Run unsigned → 跳过校验继续运行，**需二次确认对话框**（决策 Q4：文案 "Unsigned workflow may be unsafe. Continue?"，确认后运行，仅开发者模式可用）。

#### FR-6.5 T6 — 会话无法恢复 (Can't Restore Session)

顶部栏：`chevron-left` + "Sessions"。

内容区：
- 紫色警示 hero（72×72，#EFEAFB，`database` #5856D6 34）
- 主标题 "Session can't be restored"（22/700，**此屏居中**）
- 副文案 "The saved checkpoint is corrupted and can't be loaded."
- **详情表**（白底，cornerRadius 12，stroke #EAEAEC，gap 10，padding [12,14]）三行：
  - `Kind` pill（紫色 #EFEAFB）/ `Session`（"trip-planning · 06-17"）/ `Detail`（"File truncated (DataLoss) — likely interrupted while saving."）
- **数据丢失警告**（fill #FFF8EC，stroke #FFE0A8，`triangle-alert` #FF9500）："Unsaved turns from this session are lost."（12/500，#8E6A2B）
- 按钮：`Start fresh`（#007AFF 主按钮，plus 图标）+ `Pick another session`（白底，folder-open 图标）

行为：Start fresh → 新建会话返回聊天；Pick another → 会话选择列表。

**验收 (FR-6):** 六类场景的横幅/提示/按钮均与设计稿元素一一对应；文案逐字一致（中英 i18n 资源另立要求）。

---

### FR-7 时间线回放 / 历史会话

- 会话消息完整保留（含工具卡、子代理卡、审批结果），重新进入会话可原样回放（P1）。
- 会话卡片（侧边栏，Out of scope）上的 lastMessage / runState 由最近一条消息推导。

---

### FR-8 数据模型需求

对齐现 `Models.kt`，FRD 要求补充字段：

```kotlin
enum class RunState { Idle, Running, AwaitingApproval, Succeeded, Degraded, Failed, Stopped }
enum class ToolStatus { Pending, Running, Done, Failed }

data class ToolCall(
    val name: String,
    val detail: String,
    val status: ToolStatus,
    val resultLabel: String? = null,     // "Done" / "Found 3" / "Running"
    val isDelegate: Boolean = false,     // 子代理卡
    val isNested: Boolean = false,       // 嵌套工具（缩进）
    val errorHint: String? = null,       // "tool_error · API 503 — agent retried"
    val recovered: Boolean = false,
    val action: ToolAction? = null,      // pending approval 的工具
)

data class SubAgentSummary(
    val label: String,          // "Hotel Specialist · Sub-agent"
    val subtitle: String,       // "Depth 1 · isolated context"
    val goal: String,
    val summary: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
)

data class Message(
    val id: String,
    val role: Role,
    val text: String? = null,
    val streaming: Boolean = false,
    val toolCalls: List<ToolCall> = emptyList(),
    val subAgent: SubAgentSummary? = null,
    val approval: ApprovalRequest? = null,
    val runNotes: List<RunNote> = emptyList(),  // Partial Note / Degraded Note / Generating Note
    val error: String? = null,
)

data class RunFailure(
    val kind: String,            // "OOM" / "Stopped"
    val title: String,           // "Run failed" / "Stopped"
    val message: String,         // "Out of memory during generation"
    val node: String?,           // "main · depth 0"
    val detail: String,          // "KV-cache allocation failed (model too large for device)"
    val canResume: Boolean,      // true → Resume; false → Retry
)
```

---

## 6. 视觉规范 (Visual Tokens)

### 6.1 颜色

| Token | 值 | 用途 |
|---|---|---|
| Accent | #007AFF | 发送、Agent 头像、主按钮、Running |
| Success | #34C759 | Done / recovered / online |
| Danger | #FF3B30 | 失败、Stop |
| Warning | #FF9500 | 审批、待确认 |
| SubAgent | #5856D6 | 子代理品牌色 |
| Canvas | #F5F5F5 | 页面背景 |
| Chrome | #1C1C1E | 深色组件（侧栏） |
| Surface | #FFFFFF | 卡片 |
| TextPrimary | #1C1C1E | 主文字 |
| TextSecondary | #6C6C70 / #636366 | 次要文字 |
| TextTertiary | #8E8E93 | 时间戳/占位 |
| TextDisabled | #C7C7CC | 不可用 |
| TextMuted | #B0B0B5 | 注释行 |
| BubbleAgent | #E8E8EA | Agent 气泡 |
| Stroke | #E5E5EA | 常规描边 |
| SubAgentStroke | #E2DCF6 | 子代理卡描边 |
| ApprovalBg | #FFF8EC | 审批卡底 |
| FailureBg | #FDEEEE | 失败横幅底 |
| StoppedBg | #F0F0F2 | 停止横幅底 |

### 6.2 字体

- Family：Inter（打包 `res/font`，回退 sans-serif）
- 22/700 主标题；16/600 头部名称；15/400 正文；13/600 工具名；12/400 描述；11/400 注释；10/600 pill
- lineHeight：正文 1.4–1.5

### 6.3 尺寸

- 圆角：屏 40 / 卡 12 / 气泡 18 / 按钮 10 / pill 8–9 / 头像 18
- 触区：≥36×36；输入栏高 36；主按钮 40–46

---

## 7. 非功能需求 (NFR)

| 编号 | 需求 | 指标 |
|---|---|---|
| NFR-1 | 流式性能 | 首 token ≤ 1s（本地模型）；token 间延迟 ≤ 50ms 无卡顿 |
| NFR-2 | 停止响应 | Stop 点击后 ≤ 200ms 生成停止，UI 无冻结 |
| NFR-3 | 内存 | 长会话/长输出不 OOM；OOM 须走 T3 失败路径而非崩溃（可用 KV-cache 换出策略，P2） |
| NFR-4 | 恢复 | 进程被杀后，重启可恢复会话（检查点）；损坏时走 T6 |
| NFR-5 | 无障碍 | 全部图标按钮带 contentDescription；滚动行为符合 TalkBack |
| NFR-6 | 本地化 | 全部文案资源化（中文/英文） |
| NFR-7 | 错误可观测 | View logs 可导出运行日志；Sentry 上报（已有 sentry-cli skill 对接） |
| NFR-8 | 数据隐私 | 模型本地推理，聊天数据不出设备（对照 README "Gemma 2B · 本地"） |

---

## 8. 验收清单 (Acceptance Matrix)

| 需求 | 验收场景 | 证据 |
|---|---|---|
| FR-1 | 12 屏头部状态文本/颜色全对 | 截图对照设计稿 |
| FR-2 | 气泡/工具卡/子代理卡/审批卡逐元素匹配 | Compose 实现 + 截图 |
| FR-2.6 | Deny→Failed；Approve→Running | 交互测试 |
| FR-3 | 流式 ▍ + Stop ≤200ms | 性能埋点 |
| FR-5 | 全状态机无非法转移 | 状态单测 + UI 测试 |
| FR-6.1 | API 503 重试后 recovered + 降级提示 | 故障注入 |
| FR-6.2 | OOM→失败横幅(三行详情)+Retry | 故障注入（内存限制） |
| FR-6.3 | Stop→停止横幅(Note)+Restart/Resume | 交互测试 |
| FR-6.4 | 校验失败→Issues 列表+两个按钮 | 注入非法 workflow |
| FR-6.5 | 损坏 checkpoint→T6 详情+警告+两按钮 | 截断存储文件 |
| NFR | 性能/内存/恢复指标达标 | 真机 (arm64) 基准 |

---

## 9. 开放问题 / 决策记录 (Decision Log)

v0.9 评审提出的 Q1–Q4 已全部决策（2026-08-29，产品确认），决策同步至相应需求段落。

| # | 问题 | 决策 | 状态 |
|---|---|---|---|
| Q1 | AwaitingApproval 期间用户输入是否真正排队？ | 本期仅占位提示，不做真实队列；队列化列为 P1 演进（FR-4.5，接口预留 `pendingQueue`） | ✅ 已决 |
| Q2 | Deny 后 RunState 为 Failed 还是 Stopped？ | **Failed**（可直接 Retry 重跑）；失败横幅 Kind 记为 Denied/Aborted（FR-2.6.1） | ✅ 已决 |
| Q3 | "Sub-agent returned a proposal" 文案触发条件 | 运行成功结束且结果包含子代理提案时显示；否则普通成功显示 "Online"；优先级 Failed/Stopped > AwaitingApproval > Running > 提案成功 > Online（FR-1.3） | ✅ 已决 |
| Q4 | T5 "Run unsigned (dev only)" 是否需要二次确认 | 需要：二次确认对话框，仅开发者模式可用（FR-6.4） | ✅ 已决 |

**后续待办（已识别，未排期）：**
- Q1 队列化实现（P1）
- Agent 树展开详情（FR-1.4 入口保留）
- 工作流编辑器详细需求（T5 "Edit workflow" 另立文档）

---

## 10. 参考 (References)

- 设计稿：`zen/design/zenagent.lib.pen`（Frame 索引：`hBsYj` Ⓣ Chat+Sub-Agent Run；`M0M7h` Running live；`g1Cj1o` T1+T2；`lvcTL` T3 OOM；`ZlFnL` T4 Stopped；`cq3MD` T5；`EtoIa` T6；`Jtd82` Chat Screen 含审批门）
- 实现基线：`app/src/main/java/com/zenwayne/zenagent/`（ChatScreen/Sidebar/Settings 已按设计实现；RunState 枚举已镜像）
- 联库文档：`docs/superpowers/specs/2026-06-17-zen-inference-on-device-integration-design.md`
