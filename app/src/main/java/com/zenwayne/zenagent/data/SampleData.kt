package com.zenwayne.zenagent.data

import com.zenwayne.zenagent.ui.theme.ZenColors

/**
 * Static sample content mirroring the Pencil design so the UI is populated on
 * first launch. Replace with a real repository / data layer later.
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

    private val travelMessages = listOf(
        Message(id = "m1", role = Role.User, text = "Plan a 5-day Tokyo trip and book the flights and hotel"),
        Message(
            id = "m2",
            role = Role.Agent,
            text = "Sure — I'll check flights first, then have the hotel specialist find a place.",
        ),
        Message(
            id = "m3",
            role = Role.Agent,
            toolCalls = listOf(
                ToolCall(
                    name = "search_flights",
                    detail = "Tokyo · 5 days · Economy",
                    status = ToolStatus.Done,
                    resultLabel = "Done",
                ),
            ),
        ),
        Message(
            id = "m4",
            role = Role.Agent,
            toolCalls = listOf(
                ToolCall(
                    name = "Delegate · Hotel Specialist",
                    detail = "Depth 1 · Find top-rated hotels in Shinjuku",
                    status = ToolStatus.Running,
                    resultLabel = "Running",
                    isDelegate = true,
                ),
                ToolCall(
                    name = "search_hotels",
                    detail = "Shinjuku · 4 nights",
                    status = ToolStatus.Done,
                    resultLabel = "Found 3",
                ),
            ),
        ),
        Message(
            id = "m5",
            role = Role.Agent,
            approval = ApprovalRequest(
                tool = "book_hotel",
                args = "Hotel Gracery Shinjuku · 5 nights · ¥48,000",
            ),
        ),
    )

    val conversations = listOf(
        Conversation(
            id = "c1",
            agent = travelPlanner,
            lastMessage = "Awaiting approval…",
            timestamp = "14:32",
            runState = RunState.AwaitingApproval,
            messages = travelMessages,
        ),
        Conversation(
            id = "c2",
            agent = pythonHelper,
            lastMessage = "sorted() 函数的用法解析…",
            timestamp = "昨天",
            runState = RunState.Succeeded,
        ),
        Conversation(
            id = "c3",
            agent = englishTutor,
            lastMessage = "Let's practice daily conversation…",
            timestamp = "周一",
            runState = RunState.Idle,
        ),
        Conversation(
            id = "c4",
            agent = writingHelper,
            lastMessage = "故事开头不知道怎么写…",
            timestamp = "上周",
            runState = RunState.Idle,
        ),
        Conversation(
            id = "c5",
            agent = dataAnalyst,
            lastMessage = "帮我清理这份销售数据…",
            timestamp = "上周",
            runState = RunState.Succeeded,
        ),
    )
}

data class UserProfile(
    val name: String,
    val email: String,
    val initial: String,
)
