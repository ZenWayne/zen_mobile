package com.zenwayne.zenagent.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.UserProfile
import com.zenwayne.zenagent.ui.components.Avatar
import com.zenwayne.zenagent.ui.theme.ZenColors

@Composable
fun SidebarScreen(
    conversations: List<Conversation>,
    user: UserProfile,
    onConversationClick: (Conversation) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenColors.Ink)
            .statusBarsPadding(),
    ) {
        ProfileSection(user)
        SearchBar()
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(conversations, key = { it.id }) { c ->
                ConversationRow(c, onClick = { onConversationClick(c) })
            }
        }
        BottomSection(onNewChat = onNewChat, onSettings = onSettings)
    }
}

@Composable
private fun ProfileSection(user: UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(glyph = user.initial, accent = ZenColors.Accent, size = 48.dp, cornerRadius = 24.dp)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(user.name, color = ZenColors.TextOnDark, style = MaterialTheme.typography.titleLarge)
            Text(user.email, color = ZenColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SearchBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.InkElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ZenColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("搜索对话", color = ZenColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(glyph = c.agent.glyph, accent = c.agent.accent)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.agent.name,
                color = ZenColors.TextOnDark,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                c.lastMessage,
                color = ZenColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(c.timestamp, color = ZenColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BottomSection(onNewChat: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(ZenColors.InkHairline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSettings)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ZenColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text("设置", color = ZenColors.TextOnDark, style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ZenColors.Accent)
                .clickable(onClick = onNewChat)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("新建对话", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
