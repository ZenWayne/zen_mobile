package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-2.3 — centered session timestamp ("Today 14:30"), 11sp #8E8E93. */
@Composable
fun TimestampRow(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().semantics { contentDescription = TestTags.TIMESTAMP_ROW }, contentAlignment = Alignment.Center) {
        Text(text, color = ZenColors.TextSecondary, fontSize = 11.sp)
    }
}

/** FR-2.1 — user bubble: right-aligned, #007AFF, white 15sp, radius 18. */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        BubbleShell(text, background = ZenColors.Accent, textColor = Color.White)
    }
}

/** FR-2.2 / FR-3.1 — agent bubble; streaming appends the ▍ cursor. */
@Composable
fun AgentBubble(text: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val body = if (streaming) "$text ▍" else text
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        BubbleShell(body, background = ZenColors.BubbleAgent, textColor = ZenColors.TextPrimary)
    }
}

@Composable
private fun BubbleShell(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = textColor, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** FR-3.1 — "generating… tap stop to cancel" hint under a streaming bubble. */
@Composable
fun GeneratingNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = TestTags.GENERATING_NOTE },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoaderDot(ZenColors.Accent, size = 10.dp)
        Spacer(Modifier.size(6.dp))
        Text("generating… tap stop to cancel", color = ZenColors.Accent, fontSize = 11.sp)
    }
}
