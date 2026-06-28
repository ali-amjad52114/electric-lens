package com.electriclens.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.ui.components.PrimaryButton
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.vm.AppState
import com.electriclens.vm.DocStatus
import com.electriclens.vm.ProcessedDoc
import com.electriclens.vm.SessionViewModel

/**
 * ADD DOCUMENTS screen.
 *
 * Lets the user pick PDF manuals via the Storage Access Framework, shows each
 * document processing on-device (PROCESSING -> PROCESSED), and unlocks "Next"
 * once at least one doc is processed (state == DOCS_PROCESSED).
 *
 * No business logic lives here — the composable only calls VM methods + SAF and
 * renders [SessionViewModel] state.
 */
@Composable
fun AddDocumentsScreen(vm: SessionViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val docs by vm.processedDocs.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = displayNameOf(context, uri)
            vm.addDocument(name, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text(
            text = "Add Documents",
            color = TextLight,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Manuals are processed on-device. Nothing leaves this phone.",
            color = TextLight.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = "Pick a PDF manual",
            onClick = { pickPdf.launch(arrayOf("application/pdf")) }
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { vm.useDemoDocument() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Caution.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = PanelDark,
                contentColor = TextLight
            )
        ) {
            Text(
                text = "Use demo document",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        if (docs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No documents yet.\nPick a PDF or load the demo manual.",
                    color = TextLight.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(docs) { doc ->
                    DocumentRow(doc)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = "Next",
            onClick = { vm.proceedToLiveSession() },
            enabled = state == AppState.DOCS_PROCESSED
        )
    }
}

@Composable
private fun DocumentRow(doc: ProcessedDoc) {
    val processed = doc.status == DocStatus.PROCESSED
    val accent = if (processed) Verified else Caution

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (processed) {
                Text(
                    text = "✓",
                    color = Verified,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Caution,
                    strokeWidth = 3.dp
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            if (processed) {
                Text(
                    text = "Processed ✓",
                    color = Verified,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                doc.summary?.let { summary ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = TextLight.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = "Processing…",
                    color = Caution,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Best-effort display name from a SAF [Uri]; falls back to the last path segment. */
private fun displayNameOf(context: android.content.Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
    } catch (_: Throwable) {
        // Never crash the demo over a resolver hiccup; fall back below.
    }
    return name
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "document.pdf"
}
