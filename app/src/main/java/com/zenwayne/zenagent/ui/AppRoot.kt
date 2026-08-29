package com.zenwayne.zenagent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.SampleData
import com.zenwayne.zenagent.inference.AgentflowInferenceClient
import com.zenwayne.zenagent.inference.ChatViewModel
import com.zenwayne.zenagent.ui.chat.ChatScreen
import com.zenwayne.zenagent.ui.errors.CanStartFailedScreen
import com.zenwayne.zenagent.ui.errors.SessionRestoreErrorScreen
import com.zenwayne.zenagent.ui.settings.SettingsScreen
import com.zenwayne.zenagent.ui.sidebar.SidebarScreen
import com.zenwayne.zenagent.ui.theme.ZenColors
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val conversations = SampleData.conversations
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(context),
    )
    val selected by chatViewModel.selected.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showCanStartError by remember { mutableStateOf(false) }
    var showRestoreError by remember { mutableStateOf(false) }

    // Verify the model once at startup; drives the model-missing UI state.
    LaunchedEffect(Unit) {
        chatViewModel.verifyModel()
    }

    if (showSettings) {
        SettingsScreen(
            user = SampleData.user,
            onBack = { showSettings = false },
        )
        return
    }

    // T5 — engine can't start: surfaces when the model is missing on-device.
    if (showCanStartError || chatViewModel.modelMissing) {
        CanStartFailedScreen(
            onClose = { showCanStartError = false },
            onEditWorkflow = { showCanStartError = false },
            onRunUnsigned = {
                showCanStartError = false
                chatViewModel.select(conversations.first { it.id == "c1" })
            },
        )
        return
    }

    // T6 — session checkpoint corrupted (full-screen route).
    if (showRestoreError) {
        SessionRestoreErrorScreen(
            onStartFresh = { showRestoreError = false },
            onPickSession = { showRestoreError = false },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.86f),
                drawerContainerColor = ZenColors.Ink,
            ) {
                SidebarScreen(
                    conversations = conversations,
                    user = SampleData.user,
                    onConversationClick = { conv: Conversation ->
                        when (conv.id) {
                            "cantstart" -> showCanStartError = true
                            "corrupt" -> showRestoreError = true
                            else -> chatViewModel.select(conv)
                        }
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = { scope.launch { drawerState.close() } },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        ChatScreen(
            conversation = selected,
            onBack = { scope.launch { drawerState.open() } },
            onApprove = { chatViewModel.approveGate() },
            onDeny = { chatViewModel.denyGate() },
            onStop = { chatViewModel.stop() },
            onSend = { text -> chatViewModel.send(text) },
            onRetry = { },
            onRestart = { },
            onResume = { },
            onViewLogs = { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
