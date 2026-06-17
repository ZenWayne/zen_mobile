package com.zenwayne.zenagent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.SampleData
import com.zenwayne.zenagent.ui.chat.ChatScreen
import com.zenwayne.zenagent.ui.settings.SettingsScreen
import com.zenwayne.zenagent.ui.sidebar.SidebarScreen
import com.zenwayne.zenagent.ui.theme.ZenColors
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val conversations = SampleData.conversations
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(conversations.first()) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            user = SampleData.user,
            onBack = { showSettings = false },
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
                        selected = conv
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}
