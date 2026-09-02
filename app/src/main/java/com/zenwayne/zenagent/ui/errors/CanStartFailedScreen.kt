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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

data class Issue(
    val title: String,
    val description: String,
    val accentColor: Color,
    val icon: ImageVector = Icons.Filled.GppBad,
    val iconColor: Color = ZenColors.Amber,
)

private val DefaultIssues = listOf(
    Issue(
        title = "signature_invalid",
        description = "HMAC-SHA256 doesn't match — file modified or signed with a different key.",
        accentColor = ZenColors.OrangeSoft,
    ),
    Issue(
        title = "unknown_tool",
        description = "agent 'hotel' references tool 'book' (not registered).",
        accentColor = ZenColors.ToolFailureBackground,
        icon = Icons.Filled.Build,
        iconColor = ZenColors.Danger,
    ),
    Issue(
        title = "agent_missing",
        description = "Workflow references an agent that is not installed.",
        accentColor = ZenColors.OrangeSoft,
    ),
)

@Composable
fun CanStartFailedScreen(
    onClose: () -> Unit,
    onEditWorkflow: () -> Unit = {},
    onRunUnsigned: () -> Unit = {},
    issues: List<Issue> = DefaultIssues,
    modifier: Modifier = Modifier,
) {
    var showUnsignedDialog by remember { mutableStateOf(false) }
    if (showUnsignedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsignedDialog = false },
            title = { Text("Run unsigned workflow?") },
            text = { Text("Unsigned workflow may be unsafe. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsignedDialog = false
                    onRunUnsigned()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsignedDialog = false }) { Text("Cancel") }
            },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZenColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics { contentDescription = TestTags.T5_SCREEN },
    ) {
        TopBar(onClose)
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
                IssuesCard(issues)
                Buttons(
                    onEditWorkflow = onEditWorkflow,
                    onRunUnsigned = { showUnsignedDialog = true },
                )
            }
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = TestTags.T5_CLOSE,
            tint = ZenColors.TextPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClose),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Run agent",
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
            .background(ZenColors.OrangeSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.GppBad,
            contentDescription = null,
            tint = ZenColors.Amber,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun Heading() {
    Text(
        text = "Can't start this agent",
        color = ZenColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "The workflow failed validation and won't run.",
        color = ZenColors.TextDim,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IssuesCard(issues: List<Issue>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.Surface)
            .border(1.dp, ZenColors.CardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        issues.take(2).forEachIndexed { index, issue ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp)
                        .height(1.dp)
                        .background(ZenColors.DividerSoft),
                )
            }
            IssueRow(issue)
        }
        val remaining = issues.size - 2
        if (remaining > 0) {
            Text(
                text = "+$remaining more ${if (remaining == 1) "issue" else "issues"}",
                color = ZenColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun IssueRow(issue: Issue) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(issue.accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                issue.icon,
                contentDescription = null,
                tint = issue.iconColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = issue.title,
                color = ZenColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = issue.description,
                color = ZenColors.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Buttons(onEditWorkflow: () -> Unit, onRunUnsigned: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CtaButton(
            "Edit workflow", Icons.Filled.DriveFileRenameOutline, primary = true,
            onClick = onEditWorkflow,
            modifier = Modifier.semantics { contentDescription = TestTags.T5_EDIT_WORKFLOW },
        )
        CtaButton(
            "Run unsigned (dev only)", Icons.Filled.GppMaybe, primary = false,
            onClick = onRunUnsigned,
            modifier = Modifier.semantics { contentDescription = TestTags.T5_RUN_UNSIGNED },
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
