package com.electriclens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.TextLight
import com.electriclens.vm.BlockInfo

/**
 * Large red "WORK BLOCKED" card — the unmissable replacement for the small red
 * verdict chip. Shown whenever the current step is blocked (identity mismatch /
 * unverified). It states the reason, what was expected vs what was detected, and
 * offers a single recapture action.
 */
@Composable
fun WorkBlockedCard(
    block: BlockInfo,
    onRecapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Alert.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .border(2.dp, Alert, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "⛔ WORK BLOCKED",
            color = Alert,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        BlockRow("Reason", block.reason)
        BlockRow("Expected", block.expected)
        BlockRow("Detected", block.detected)
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = "Recapture Evidence",
            onClick = onRecapture
        )
    }
}

@Composable
private fun BlockRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            color = TextLight.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            color = TextLight,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}
