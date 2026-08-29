package com.zenwayne.zenagent.data

import com.zenwayne.zenagent.ui.theme.ZenColors

/**
 * Static sample content mirroring the Pencil design so the UI is populated on
 * first launch. Replace with a real repository / data layer later.
 *
 * Each conversation demonstrates one of the run-lifecycle design states so the
 * 12 screens can be visually verified: Normal chat, Awaiting approval, Running
 * live, T1+T2 self-healed, T3 OOM, T4 stopped, Sub-agent succeeded, idle, and
 * the full-screen T5 (can't start) / T6 (can't restore) routes reachable from
 * the drawer via the special `cantstart` / `corrupt` entries.
 */
object SampleData {

    val user = UserProfile(
        name = "Wayne",
        email = "gzhw734@gmail.com",
        initial = "W",
    )

    private val travelPlanner = Agent(
        id = "travel",
        name = "Travel Planner",
        subtitle = "帮你规划行程 / 查机票 / 订酒店",
        accent = ZenColors.Accent,
        glyph = "✈",
    )
    private val pythonHelper = Agent(
        id = "python",
        name = "Python 代码助手",
        subtitle = "sorted() 函数的用法…",
        accent = ZenColors.Green,
        glyph = "🐍",
    )
    private val englishTutor = Agent(
        id = "english",
        name = "英语学习助手",
        subtitle = "Let's practice daily conversation…",
        accent = ZenColors.Orange,
        glyph = "A",
    )
    private val writingHelper = Agent(
        id = "writing",
        name = "创意写作助手",
        subtitle = "故事开头不知道怎么写…",
        accent = ZenColors.Violet,
        glyph = "✎",
    )
    private val dataAnalyst = Agent(
        id = "data",
        name = "数据分析专家",
        subtitle = "帮我清理这份销售数据…",
        accent = ZenColors.Pink,
        glyph = "📊",
    )

    val conversations = listOf(
        // c1 — Normal chat (Idle): plain tool call, no sub-agent, no approval.
        Conversation(
            id = "c1",
            agent = travelPlanner,
            lastMessage = "Plan a 5-day Tokyo trip and book the flights and hotel ✈️",
            timestamp = "Today 14:30",
            runState = RunState.Idle,
            messages = listOf(
                Message(
                    id = "c1-m1",
                    role = Role.User,
                    text = "Plan a 5-day Tokyo trip and book the flights and hotel ✈️",
                    timestamp = "Today 14:30",
                ),
                Message(
                    id = "c1-m2",
                    role = Role.Agent,
                    text = "Sure — I'll check flights first, then have the hotel specialist find a place.",
                ),
                Message(
                    id = "c1-m3",
                    role = Role.Agent,
                    toolCalls = listOf(
                        ToolCall(
                            name = "search_flights",
                            detail = "Tokyo · 5 days · Economy",
                            status = ToolStatus.Done,
                            resultLabel = "Found 24",
                            icon = ToolIcon.Flight,
                        ),
                    ),
                ),
            ),
        ),

        // c2 — Awaiting approval (AwaitingApproval): full design flow with sub-agent + approval gate.
        Conversation(
            id = "c2",
            agent = travelPlanner,
            lastMessage = "Awaiting approval…",
            timestamp = "Today 14:32",
            runState = RunState.AwaitingApproval,
            hasProposal = true,
            messages = listOf(
                Message(
                    id = "c2-m1",
                    role = Role.User,
                    text = "Book the best hotel and the flights for my Tokyo trip",
                    timestamp = "Today 14:32",
                ),
                Message(
                    id = "c2-m2",
                    role = Role.Agent,
                    text = "Sure — I'll check flights first, then have the hotel specialist find a place.",
                ),
                Message(
                    id = "c2-m3",
                    role = Role.Agent,
                    toolCalls = listOf(
                        ToolCall(
                            name = "search_flights",
                            detail = "Tokyo · 5 days · Economy",
                            status = ToolStatus.Done,
                            resultLabel = "Done",
                            icon = ToolIcon.Flight,
                        ),
                    ),
                ),
                Message(
                    id = "c2-m4",
                    role = Role.Agent,
                    subAgent = SubAgentSummary(
                        label = "Delegate · Hotel Specialist",
                        subtitle = "Depth 1 · Goal: find top-rated hotels in Shinjuku",
                        goal = "Goal: find top-rated hotels in Shinjuku",
                        status = ToolStatus.Running,
                        toolCalls = listOf(
                            ToolCall(
                                name = "search_hotels",
                                detail = "Shinjuku · 4 nights",
                                status = ToolStatus.Done,
                                resultLabel = "Found 3",
                                isNested = true,
                                icon = ToolIcon.Hotel,
                            ),
                        ),
                    ),
                ),
                Message(
                    id = "c2-m5",
                    role = Role.Agent,
                    approval = ApprovalRequest(
                        tool = "book_hotel",
                        args = "Hotel Gracery Shinjuku · 5 nights · ¥48,000",
                        note = "Agent wants to run an action with side effects",
                    ),
                ),
            ),
        ),

        // c3 — Running live (Running): streaming agent text + running tool card.
        Conversation(
            id = "c3",
            agent = travelPlanner,
            lastMessage = "Day 1 — Higashiyama…",
            timestamp = "Today 15:10",
            runState = RunState.Running,
            messages = listOf(
                Message(
                    id = "c3-m1",
                    role = Role.User,
                    text = "Give me a 3-day Kyoto itinerary",
                    timestamp = "Today 15:10",
                ),
                Message(
                    id = "c3-m2",
                    role = Role.Agent,
                    text = "Day 1 — Higashiyama\n• Kiyomizu-dera (morning)\n• Sannenzaka tea houses\n• Nishiki Market for lunch",
                    streaming = true,
                ),
                Message(
                    id = "c3-m3",
                    role = Role.Agent,
                    toolCalls = listOf(
                        ToolCall(
                            name = "search_attractions",
                            detail = "Kyoto · temples + food",
                            status = ToolStatus.Running,
                            resultLabel = "Running",
                            icon = ToolIcon.MapPin,
                        ),
                    ),
                ),
            ),
        ),

        // c4 — T1+T2 self-healed (Degraded): recovered tool + degraded note.
        Conversation(
            id = "c4",
            agent = travelPlanner,
            lastMessage = "Tokyo next week: mostly sunny, 18–24°C. ☀️",
            timestamp = "Today 17:57",
            runState = RunState.Degraded,
            messages = listOf(
                Message(
                    id = "c4-m1",
                    role = Role.User,
                    text = "What's the weather in Tokyo next week?",
                    timestamp = "Today 17:57",
                ),
                Message(
                    id = "c4-m2",
                    role = Role.Agent,
                    text = "Let me check the forecast.",
                ),
                Message(
                    id = "c4-m3",
                    role = Role.Agent,
                    toolCalls = listOf(
                        ToolCall(
                            name = "get_weather",
                            detail = "Tokyo · 7-day forecast",
                            status = ToolStatus.Done,
                            resultLabel = "Done",
                            icon = ToolIcon.Weather,
                            errorHint = "tool_error · API 503 — agent retried",
                            recovered = true,
                        ),
                    ),
                    runNotes = listOf(
                        RunNote(
                            type = RunNoteType.Degraded,
                            text = "output wasn't valid JSON — used raw text",
                        ),
                    ),
                ),
                Message(
                    id = "c4-m4",
                    role = Role.Agent,
                    text = "Tokyo next week: mostly sunny, 18–24°C. ☀️",
                ),
            ),
        ),

        // c5 — T3 OOM failed (Failed): partial output + un-resumable failure banner.
        Conversation(
            id = "c5",
            agent = travelPlanner,
            lastMessage = "Out of memory during generation",
            timestamp = "Today 11:48",
            runState = RunState.Failed,
            messages = listOf(
                Message(
                    id = "c5-m1",
                    role = Role.User,
                    text = "Summarize this 80-page contract and highlight the liabilities",
                    timestamp = "Today 11:48",
                ),
                Message(
                    id = "c5-m2",
                    role = Role.Agent,
                    text = "Here's what I have so far:\n1. Liability is capped…\n2. Either party may terminate…\n3. Confidentiality survives for…",
                    runNotes = listOf(
                        RunNote(
                            type = RunNoteType.Partial,
                            text = "partial output — generation stopped mid-stream",
                        ),
                    ),
                ),
                Message(
                    id = "c5-m3",
                    role = Role.Agent,
                    failure = RunFailure(
                        kind = "OOM",
                        title = "Run failed",
                        message = "Out of memory during generation",
                        node = "main · depth 0",
                        detail = "KV-cache allocation failed (model too large for device)",
                        canResume = false,
                    ),
                ),
            ),
        ),

        // c6 — T4 stopped (Stopped): partial output + resumable stopped banner.
        Conversation(
            id = "c6",
            agent = travelPlanner,
            lastMessage = "Cancelled after 3 of 10 cities",
            timestamp = "Today 16:05",
            runState = RunState.Stopped,
            messages = listOf(
                Message(
                    id = "c6-m1",
                    role = Role.User,
                    text = "Compare round-trip fares to 10 EU cities",
                    timestamp = "Today 16:05",
                ),
                Message(
                    id = "c6-m2",
                    role = Role.Agent,
                    text = "Comparing fares:\n• Paris — from ¥3,180\n• Rome — from ¥3,540\n• Berlin — from ¥2,990…",
                    runNotes = listOf(
                        RunNote(
                            type = RunNoteType.Partial,
                            text = "you stopped the run — partial results kept",
                        ),
                    ),
                ),
                Message(
                    id = "c6-m3",
                    role = Role.Agent,
                    failure = RunFailure(
                        kind = "Stopped",
                        title = "Stopped",
                        message = "Cancelled after 3 of 10 cities",
                        detail = "Resume re-runs the cancelled step from a clean state.",
                        canResume = true,
                    ),
                ),
            ),
        ),

        // c7 — Sub-agent succeeded (Succeeded + proposal): hero sub-agent card, done, with pending confirm.
        Conversation(
            id = "c7",
            agent = travelPlanner,
            lastMessage = "Sub-agent returned a proposal",
            timestamp = "Today 09:02",
            runState = RunState.Succeeded,
            hasProposal = true,
            messages = listOf(
                Message(
                    id = "c7-m1",
                    role = Role.User,
                    text = "Can you find me a hotel in Shinjuku under budget?",
                    timestamp = "Today 09:02",
                ),
                Message(
                    id = "c7-m2",
                    role = Role.Agent,
                    text = "On it — I'll have the hotel specialist scope it out. I'll bring back one clean result.",
                ),
                Message(
                    id = "c7-m3",
                    role = Role.Agent,
                    subAgent = SubAgentSummary(
                        label = "Hotel Specialist · Sub-agent",
                        subtitle = "Depth 1 · isolated context",
                        goal = "Goal: find top-rated hotels in Shinjuku, ≤ ¥50k/night",
                        summary = "Narrowed 34 → 3 candidates. Recommend Hotel Gracery Shinjuku.",
                        status = ToolStatus.Done,
                        toolCalls = listOf(
                            ToolCall(
                                name = "search_hotels",
                                detail = "Shinjuku · any",
                                status = ToolStatus.Done,
                                resultLabel = "Done",
                                icon = ToolIcon.Search,
                            ),
                            ToolCall(
                                name = "compare_by_reviews",
                                detail = "Shinjuku · reviews",
                                status = ToolStatus.Done,
                                resultLabel = "Done",
                                icon = ToolIcon.MapPin,
                            ),
                            // name is plain "book_hotel" — the pill appends "· awaiting confirm".
                            ToolCall(
                                name = "book_hotel",
                                detail = "Hotel Gracery Shinjuku",
                                status = ToolStatus.Pending,
                                resultLabel = "Confirm",
                                icon = ToolIcon.Warning,
                            ),
                        ),
                    ),
                ),
            ),
        ),

        // c8 — Idle (python): keeps the drawer populated.
        Conversation(
            id = "c8",
            agent = pythonHelper,
            lastMessage = "sorted() 函数的用法解析…",
            timestamp = "昨天",
            runState = RunState.Idle,
        ),

        // Existing agent conversations (simple idle/succeeded) for a realistic drawer list.
        Conversation(
            id = "c9",
            agent = englishTutor,
            lastMessage = "Let's practice daily conversation…",
            timestamp = "周一",
            runState = RunState.Idle,
        ),
        Conversation(
            id = "c10",
            agent = writingHelper,
            lastMessage = "故事开头不知道怎么写…",
            timestamp = "上周",
            runState = RunState.Idle,
        ),
        Conversation(
            id = "c11",
            agent = dataAnalyst,
            lastMessage = "帮我清理这份销售数据…",
            timestamp = "上周",
            runState = RunState.Succeeded,
        ),

        // Special drawer entry → T5 Can't-start full-screen route.
        Conversation(
            id = "cantstart",
            agent = travelPlanner,
            lastMessage = "Can't start — workflow failed validation",
            timestamp = "今天",
            runState = RunState.Failed,
        ),

        // Special drawer entry → T6 Session-restore-error full-screen route.
        Conversation(
            id = "corrupt",
            agent = travelPlanner,
            lastMessage = "Session corrupt — can't restore",
            timestamp = "今天",
            runState = RunState.Failed,
        ),
    )
}

data class UserProfile(
    val name: String,
    val email: String,
    val initial: String,
)
