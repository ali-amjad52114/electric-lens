package com.electriclens.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.ui.components.CameraPreview
import com.electriclens.ui.components.EvidenceThumbStrip
import com.electriclens.ui.components.PrimaryButton
import com.electriclens.ui.components.StepCard
import com.electriclens.ui.components.rememberCameraCaptureController
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.util.ImageLoader
import com.electriclens.util.TtsManager
import com.electriclens.vm.AppState
import com.electriclens.vm.SessionViewModel

/** The four guided-LOTO steps, keyed by the VM step number (1..4). */
private data class LotoStepInfo(
    val title: String,
    val instruction: String,
    val state: AppState
)

private val lotoSteps = listOf(
    LotoStepInfo("Step 1 — Breaker B-201", "Switch breaker B-201 OFF, then capture.", AppState.LOTO_B201),
    LotoStepInfo("Step 2 — Breaker B-205", "Switch breaker B-205 OFF, then capture.", AppState.LOTO_B205),
    LotoStepInfo("Step 3 — Lock & Tag", "Apply your personal lock and danger tag, then capture.", AppState.LOTO_LOCK_TAG),
    LotoStepInfo("Step 4 — MCC Cabinet", "Open the MCC cabinet, then capture.", AppState.LOTO_MCC_OPEN)
)

/**
 * Guided Lockout — two-pane layout.
 *
 * Left/center pane: small camera preview, the active StepCard, the captured-
 * evidence strip, a green/red verdict chip for the last capture, the Capture +
 * Upload evidence buttons and (once done) the Generate Permit PDF button.
 *
 * Right pane (~140dp, persistent): the 4-step overview with ✓/▶/• markers.
 *
 * Each step can be satisfied either by capturing the live CameraX frame or by
 * uploading an image; both hand a bitmap to [SessionViewModel.captureEvidence].
 * Camera permission is requested on first composition; if denied capture()
 * returns a placeholder bitmap so the flow never stalls and never crashes.
 */
@Composable
fun GuidedLockoutScreen(vm: SessionViewModel, tts: TtsManager) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currentStep by vm.currentLotoStep.collectAsStateWithLifecycle()
    val evidence by vm.evidence.collectAsStateWithLifecycle()
    val latencyMs by vm.latencyMs.collectAsStateWithLifecycle()
    val isAnalyzing by vm.isAnalyzing.collectAsStateWithLifecycle()
    val captureFeedback by vm.captureFeedback.collectAsStateWithLifecycle()
    val lastCaptureVerified by vm.lastCaptureVerified.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val controller = rememberCameraCaptureController()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            ImageLoader.decode(context, uri)?.let { vm.captureEvidence(it) }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isPermitReady = state == AppState.PERMIT_READY

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Left/center pane: live work area ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Small camera preview at the top, with an NPU latency readout overlaid.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(BgDark, RoundedCornerShape(12.dp))
            ) {
                CameraPreview(
                    controller = controller,
                    hasPermission = hasPermission,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = if (latencyMs > 0L) "NPU: $latencyMs ms" else "NPU: — ms",
                    color = Caution,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(PanelDark.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Current step card (or completion status once PERMIT_READY).
            if (isPermitReady) {
                Text(
                    text = "LOTO evidence captured. Proceed to zero-energy verification.",
                    color = Verified,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                val active = lotoSteps.getOrElse(currentStep - 1) { lotoSteps.first() }
                StepCard(
                    title = active.title,
                    instruction = active.instruction,
                    done = false
                )
            }

            // Captured-evidence thumbnails.
            EvidenceThumbStrip(items = evidence, modifier = Modifier.fillMaxWidth())

            // Verdict chip for the last capture: green ✓ / red ✗ / nothing.
            VerdictChip(verified = lastCaptureVerified, feedback = captureFeedback)

            Spacer(Modifier.weight(1f))

            // Primary actions: capture or upload current step → VM, or proceed.
            if (isPermitReady) {
                PrimaryButton(
                    text = "Generate Permit PDF",
                    onClick = { vm.generatePermit(context) }
                )
            } else if (isAnalyzing) {
                // Inference in flight — disable input and show progress.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelDark, RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Caution,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Analyzing frame…",
                        color = TextLight,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrimaryButton(
                        text = "Capture evidence",
                        onClick = {
                            // Grabs the live frame; falls back to a placeholder
                            // bitmap when no camera is available so it never stalls.
                            controller.capture()?.let { vm.captureEvidence(it) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = "Upload evidence",
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Right pane: persistent 4-step overview ---
        Column(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
                .background(PanelDark, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LOTO steps",
                color = TextLight,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            lotoSteps.forEachIndexed { index, step ->
                val stepNumber = index + 1
                val done = isPermitReady || stepNumber < currentStep
                val isActive = !isPermitReady && stepNumber == currentStep
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (done) "✓" else if (isActive) "▶" else "•",
                        color = if (done) Verified else TextLight.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = step.title,
                        color = if (done || isActive) TextLight else TextLight.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Per-capture verdict chip:
 *  - [verified] == true  → green ✓ "Verified"
 *  - [verified] == false → red ✗ + the VLM [feedback] text (prompts recapture)
 *  - [verified] == null  → render nothing
 */
@Composable
private fun VerdictChip(verified: Boolean?, feedback: String) {
    when (verified) {
        true -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✓",
                color = Verified,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Verified",
                color = Verified,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        false -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✗",
                color = Alert,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (feedback.isNotEmpty()) feedback else "Not verified — recapture.",
                color = Alert,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        null -> Unit
    }
}
