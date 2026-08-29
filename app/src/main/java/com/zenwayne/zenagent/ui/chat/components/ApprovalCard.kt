package com.zenwayne.zenagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenwayne.zenagent.data.ApprovalRequest
import com.zenwayne.zenagent.ui.TestTags
import com.zenwayne.zenagent.ui.theme.ZenColors

/** FR-2.6 — approval gate card: shield-alert header, detail box, Deny / Approve & run. */
@Composable
fun ApprovalCard(
    approval: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenColors.ApprovalBackground)
            .border(1.5.dp, ZenColors.ApprovalStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = TestTags.APPROVAL_CARD },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = Icons.Filled.GppMaybe, background = ZenColors.Warning, size = 30.dp, iconSize = 16.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Approval required", color = ZenColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(approval.note, color = ZenColors.WarningText, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ZenColors.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DetailRow("Tool") {
                Text(approval.tool, color = ZenColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            DetailRow("Args") {
                Text(approval.args, color = ZenColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = "Deny",
                icon = Icons.Filled.Close,
                textColor = ZenColors.Danger,
                background = ZenColors.Surface,
                borderColor = ZenColors.Hairline,
                iconSize = 16.dp,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.APPROVAL_DENY },
                onClick = onDeny,
            )
            ActionButton(
                label = "Approve & run",
                icon = Icons.Filled.Check,
                textColor = Color.White,
                background = ZenColors.Accent,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = TestTags.APPROVAL_APPROVE },
                onClick = onApprove,
            )
        }
    }
}
