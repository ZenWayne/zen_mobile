package com.zenwayne.zenagent.ui.errors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

@Composable
fun SessionRestoreErrorScreen(
    onStartFresh: () -> Unit = {},
    onPickSession: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics { contentDescription = TestTags.T6_SCREEN },
    ) {
        TopBar()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Hero()
                Heading()
                DetailCard()
                DataLossWarning()
                Buttons(onStartFresh = onStartFresh, onPickSession = onPickSession)
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ChevronLeft,
            contentDescription = "Back",
            tint = ZenColors.Cta,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Sessions",
            color = ZenColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Hero() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(ZenColors.VioletSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Storage,
            contentDescription = null,
            tint = ZenColors.SubAgent,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun Heading() {
    Text(
        text = "Session can't be restored",
        color = ZenColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "The saved checkpoint is corrupted and can't be loaded.",
        color = ZenColors.TextDim,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DetailCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.Surface)
            .border(1.dp, ZenColors.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailRow(label = "Kind") { KindPill("checkpoint_corrupt") }
        DetailRow(label = "Session") {
            Text(
                text = "trip-planning · 06-17",
                color = ZenColors.TextPrimary,
                fontSize = 13.sp,
            )
        }
        DetailRow(label = "Detail", verticalAlignment = Alignment.Top) {
            Text(
                text = "File truncated (DataLoss) — likely interrupted while saving.",
                color = ZenColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = ZenColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(56.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun KindPill(text: String) {
    Text(
        text = text,
        color = ZenColors.SubAgent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZenColors.VioletSoft)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun DataLossWarning() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ZenColors.ApprovalBackground)
            .border(1.dp, ZenColors.ApprovalStroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = ZenColors.Amber,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Unsaved turns from this session are lost.",
            color = ZenColors.AmberText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Buttons(onStartFresh: () -> Unit, onPickSession: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CtaButton(
            label = "Start fresh",
            icon = Icons.Filled.Add,
            primary = true,
            onClick = onStartFresh,
            modifier = Modifier.semantics { contentDescription = TestTags.T6_START_FRESH },
        )
        CtaButton(
            label = "Pick another session",
            icon = Icons.Filled.FolderOpen,
            primary = false,
            onClick = onPickSession,
            modifier = Modifier.semantics { contentDescription = TestTags.T6_PICK_SESSION },
        )
    }
}

@Composable
private fun CtaButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (primary) Color.White else ZenColors.TextDim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (primary) Modifier.background(ZenColors.Cta)
                else Modifier
                    .background(ZenColors.Surface)
                    .border(1.dp, ZenColors.ButtonBorder, RoundedCornerShape(12.dp)),
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
