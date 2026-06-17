package com.zenwayne.zenagent.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zenwayne.zenagent.data.SampleData
import com.zenwayne.zenagent.data.UserProfile
import com.zenwayne.zenagent.ui.components.Avatar
import com.zenwayne.zenagent.ui.theme.ZenColors

@Composable
fun SettingsScreen(
    user: UserProfile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenColors.Ink),
    ) {
        Header(onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ProfileCard(user)
            SettingsGroup("通用") {
                NavRow(ZenColors.Violet, "模", "模型", value = "Gemma 2B · 本地")
                Divider()
                NavRow(ZenColors.Green, "语", "语言", value = "简体中文")
                Divider()
                NavRow(ZenColors.Orange, "外", "外观", value = "浅色")
            }
            SettingsGroup("对话") {
                ToggleRow(ZenColors.Orange, "流", "流式输出", initial = true)
                Divider()
                ToggleRow(ZenColors.Pink, "记", "保存聊天记录", initial = true)
                Divider()
                NavRow(ZenColors.Accent, "字", "字体大小", value = "标准")
            }
            SettingsGroup("隐私与数据") {
                ToggleRow(ZenColors.Green, "本", "本地数据处理", initial = true)
                Divider()
                NavRow(ZenColors.Accent, "存", "存储管理", value = "128 MB")
                Divider()
                DestructiveRow("清除所有对话")
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZenColors.Ink)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "Back",
                tint = ZenColors.Accent,
                modifier = Modifier.size(18.dp),
            )
            Text("对话", color = ZenColors.Accent, style = MaterialTheme.typography.bodyLarge)
        }
        Text("设置", color = ZenColors.TextOnDark, style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun ProfileCard(user: UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZenColors.InkElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(glyph = user.initial, accent = ZenColors.Accent, size = 48.dp, cornerRadius = 24.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, color = ZenColors.TextOnDark, style = MaterialTheme.typography.titleLarge)
            Text(user.email, color = ZenColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ZenColors.TextSecondary)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = ZenColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZenColors.InkElevated),
        ) {
            content()
        }
    }
}

@Composable
private fun RowIcon(accent: Color, glyph: String) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun NavRow(accent: Color, glyph: String, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(accent, glyph)
        Spacer(Modifier.size(12.dp))
        Text(title, color = ZenColors.TextOnDark, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, color = ZenColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ZenColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleRow(accent: Color, glyph: String, title: String, initial: Boolean) {
    var checked by remember { mutableStateOf(initial) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(accent, glyph)
        Spacer(Modifier.size(12.dp))
        Text(title, color = ZenColors.TextOnDark, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ZenColors.Success,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = ZenColors.InkHairline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun DestructiveRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(ZenColors.Danger, "✕")
        Spacer(Modifier.size(12.dp))
        Text(title, color = ZenColors.Danger, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(0.5.dp)
            .background(ZenColors.InkHairline),
    )
}
