package com.electriclens.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.data.DemoData
import com.electriclens.ui.components.EvidenceThumbStrip
import com.electriclens.ui.components.PrimaryButton
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.vm.EvidenceItem
import com.electriclens.vm.SessionViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PERMIT_TITLE = "Electric Lens — Electrical LOTO Evidence Package"
private const val FILE_PROVIDER_AUTHORITY = "com.electriclens.fileprovider"
private const val STATUS_LINE =
    "LOTO evidence captured. Proceed to zero-energy verification."
private const val DISCLAIMER =
    "This package documents visible lockout evidence only. It does not certify a " +
        "zero-energy state and does not replace required testing or the site-approved " +
        "LOTO procedure. Always verify the absence of voltage before contact."

@Composable
fun PermitScreen(vm: SessionViewModel) {
    val context = LocalContext.current
    val evidence by vm.evidence.collectAsStateWithLifecycle()
    val sessionFaultCode by vm.sessionFaultCode.collectAsStateWithLifecycle()
    val lastResult by vm.lastResult.collectAsStateWithLifecycle()

    // Generate the PDF exactly once when this screen is first shown.
    var permitFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        if (permitFile == null) {
            permitFile = runCatching { vm.generatePermit(context) }.getOrNull()
        }
    }

    val asset = DemoData.asset
    val manual = DemoData.manual
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Header / generation status ------------------------------------
        Text(
            text = if (permitFile == null) "Generating permit…" else "Permit ready",
            color = TextLight,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (permitFile == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Caution, strokeWidth = 3.dp)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "  Building LOTO evidence package…",
                    color = TextLight.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // ---- Summary card ---------------------------------------------------
        SummaryCard(
            assetName = asset.name,
            vfdId = asset.vfdId,
            location = asset.location,
            faultLine = if (sessionFaultCode.isNotBlank()) {
                "$sessionFaultCode — ${manual.meaning}"
            } else {
                "— (no code read)"
            },
            faultType = manual.faultType,
            isolationPoints = asset.isolationPoints.joinToString(", "),
            readProof = lastResult?.let {
                "${it.source} · NPU ${it.runtimeMs}ms · confidence ${"%.2f".format(it.confidence)}"
            },
            evidence = evidence,
            timeFormat = timeFormat
        )

        // ---- Status line (approved wording) --------------------------------
        Text(
            text = STATUS_LINE,
            color = Verified,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // ---- Safety disclaimer ---------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Safety notice",
                color = Caution,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = DISCLAIMER,
                color = TextLight.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // ---- Actions --------------------------------------------------------
        PrimaryButton(
            text = "Open PDF",
            enabled = permitFile != null,
            onClick = {
                permitFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        context, FILE_PROVIDER_AUTHORITY, file
                    )
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(view, "Open permit PDF")
                    )
                }
            }
        )

        PrimaryButton(
            text = "Share PDF",
            enabled = permitFile != null,
            onClick = {
                permitFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        context, FILE_PROVIDER_AUTHORITY, file
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, PERMIT_TITLE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, "Share permit PDF")
                    )
                }
            }
        )

        PrimaryButton(
            text = "Reset Demo",
            onClick = { vm.reset() },
            modifier = Modifier.navigationBarsPadding()
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(
    assetName: String,
    vfdId: String,
    location: String,
    faultLine: String,
    faultType: String,
    isolationPoints: String,
    readProof: String?,
    evidence: List<EvidenceItem>,
    timeFormat: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = PERMIT_TITLE,
            color = TextLight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        DetailRow("Asset", assetName)
        DetailRow("VFD ID", vfdId)
        DetailRow("Location", location)
        DetailRow("Fault", faultLine)
        DetailRow("Fault type", faultType)
        DetailRow("Isolation points", isolationPoints)
        readProof?.let { DetailRow("Read by", it) }

        Spacer(Modifier.height(2.dp))
        Text(
            text = "Captured evidence",
            color = Caution,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (evidence.isEmpty()) {
            Text(
                text = "No evidence captured.",
                color = TextLight.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            EvidenceThumbStrip(evidence)
            Spacer(Modifier.height(2.dp))
            evidence.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = item.stepName,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeFormat.format(Date(item.timestampMs)),
                        color = TextLight.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextLight.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            color = TextLight,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f)
        )
    }
}
