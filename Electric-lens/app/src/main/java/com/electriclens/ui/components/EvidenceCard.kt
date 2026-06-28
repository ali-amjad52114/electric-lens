package com.electriclens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.vm.VlmResult

/**
 * Real AI proof line. Surfaces the last read straight from the model so the
 * confidence is always visible (not hidden behind a verdict). Monospace, on a
 * dark panel, accented in caution amber.
 *
 * Example:
 *   Local VLM · NPU 412ms · confidence 0.91 · FAULT_CODE · code F070
 */
@Composable
fun EvidenceCard(result: VlmResult, modifier: Modifier = Modifier) {
    val codePart = result.code?.let { " · code $it" } ?: ""
    val line = buildAnnotatedString {
        append("${result.source} · NPU ${result.runtimeMs}ms · confidence ")
        withStyle(SpanStyle(color = Caution, fontWeight = FontWeight.Bold)) {
            append("%.2f".format(result.confidence))
        }
        append(" · ${result.type}$codePart")
    }

    Text(
        text = line,
        color = TextLight,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
