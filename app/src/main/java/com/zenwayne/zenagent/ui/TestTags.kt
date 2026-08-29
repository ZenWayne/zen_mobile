package com.zenwayne.zenagent.ui

/**
 * Semantics labels for Appium / UiAutomator2.
 *
 * Compose `contentDescription` semantics map to Android content-desc, which
 * Appium exposes as accessibility id (`driver.$('~label')`). Keep these names
 * stable — tests/appium tests reference them by string.
 */
object TestTags {
    // Header / chat frame (values match existing Icon contentDescription)
    const val HEADER_BACK = "Back"
    const val HEADER_AGENT_NAME = "header_agent_name"
    const val HEADER_STATUS = "header_status"
    const val HEADER_AGENT_TREE = "Agent tree"

    // Message stream
    const val MESSAGES_LIST = "messages_list"
    const val TIMESTAMP_ROW = "message_timestamp"
    const val USER_BUBBLE = "bubble_user"
    const val AGENT_BUBBLE = "bubble_agent"
    const val GENERATING_NOTE = "note_generating"
    const val RUN_NOTE = "note_run"

    // Tool / sub-agent cards
    const val TOOL_CARD = "card_tool"
    const val SUBAGENT_CARD = "card_subagent"

    // Approval gate
    const val APPROVAL_CARD = "card_approval"
    const val APPROVAL_DENY = "approval_deny"
    const val APPROVAL_APPROVE = "approval_approve"

    // Failure / stopped banners
    const val FAILURE_BANNER = "banner_failure"
    const val STOPPED_BANNER = "banner_stopped"
    const val ERROR_EVENT_CARD = "card_error_event"
    const val ACTION_VIEW_LOGS = "action_view_logs"
    const val ACTION_RETRY = "action_retry"
    const val ACTION_RESTART = "action_restart"
    const val ACTION_RESUME = "action_resume"

    // Input bar (Send/Stop/Add match existing Icon contentDescription)
    const val INPUT_FIELD = "chat_input"
    const val INPUT_ADD = "Add"
    const val INPUT_SEND = "Send"
    const val INPUT_STOP = "Stop"

    // Sidebar drawer
    const val DRAWER = "sidebar_drawer"
    const val DRAWER_PROFILE = "drawer_profile"
    const val DRAWER_SEARCH = "drawer_search"
    const val DRAWER_CONVERSATION_ITEM = "drawer_conversation"
    const val DRAWER_NEW_CHAT = "drawer_new_chat"
    const val DRAWER_SETTINGS = "drawer_settings"

    // T5 can't start agent
    const val T5_SCREEN = "t5_cant_start"
    const val T5_CLOSE = "t5_close"
    const val T5_EDIT_WORKFLOW = "t5_edit_workflow"
    const val T5_RUN_UNSIGNED = "t5_run_unsigned"

    // T6 session restore error
    const val T6_SCREEN = "t6_restore_error"
    const val T6_START_FRESH = "t6_start_fresh"
    const val T6_PICK_SESSION = "t6_pick_session"
}
