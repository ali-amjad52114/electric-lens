package com.electriclens.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.detection.VlmModelState
import com.electriclens.ui.components.ConversationPanel
import com.electriclens.ui.components.EvidenceCard
import com.electriclens.ui.components.LiveBadge
import com.electriclens.ui.components.PrimaryButton
import com.electriclens.ui.components.ProgressRail
import com.electriclens.ui.components.WorkBlockedCard
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.util.ImageLoader
import com.electriclens.util.SpeechManager
import com.electriclens.util.TtsManager
import com.electriclens.vm.SessionViewModel

/**
 * AI Session — dark, camera-free conversation screen.
 *
 * Replaces the old fixed-script LiveSessionScreen. On top of a plain dark
 * background sit:
 *  - a top status row (LIVE badge + NPU latency + MOCK/VLM tag + mute toggle),
 *  - a translucent [ConversationPanel] docked to the start edge,
 *  - a bottom control stack: a push-to-talk mic button (RECORD_AUDIO), an
 *    "Upload Fault Code Image" button (+ preview thumbnail), and a gated
 *    "Start Guided LOTO" button.
 *
 * Speech input is on-device (offline). Image upload decodes the picked image
 * to a software bitmap and hands it to the VM for on-device fault-code reading.
 */
@Composable
fun AiSessionScreen(
    vm: SessionViewModel,
    tts: TtsManager,
    speech: SpeechManager
) {
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val useMock by vm.useMockSource.collectAsStateWithLifecycle()
    val latencyMs by vm.latencyMs.collectAsStateWithLifecycle()
    val modelState by vm.modelState.collectAsStateWithLifecycle()
    val isAnalyzing by vm.isAnalyzing.collectAsStateWithLifecycle()
    val faultImage by vm.faultImage.collectAsStateWithLifecycle()
    val isListening by vm.isListening.collectAsStateWithLifecycle()
    val canStartLoto by vm.canStartLoto.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionFaultCode by vm.sessionFaultCode.collectAsStateWithLifecycle()
    val lastResult by vm.lastResult.collectAsStateWithLifecycle()
    val blockInfo by vm.blockInfo.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) startListening(vm, speech)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            ImageLoader.decode(context, uri)?.let { vm.onFaultImage(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top overlay: 5-step progress rail + status row (LIVE badge + NPU/source
        // readout + mute toggle).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressRail(
                state = state,
                faultRead = sessionFaultCode.isNotBlank(),
                canStartLoto = canStartLoto,
                blocked = blockInfo != null
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveBadge()
                    NpuReadout(latencyMs = latencyMs)
                    SourceIndicator(useMock = useMock, modelState = modelState)
                }
                IconButton(
                    onClick = {
                        vm.toggleMute()
                        tts.setMuted(!isMuted)
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = PanelDark.copy(alpha = 0.85f),
                        contentColor = TextLight
                    )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }
        }

        // Conversation panel docked to the start edge (capped to last 10 by VM).
        ConversationPanel(
            lines = conversation,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.6f)
                .width(260.dp)
                .padding(start = 16.dp)
        )

        // Bottom control stack: mic, image upload + preview, Start Guided LOTO.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Real AI proof of the last on-device read (confidence visible).
            lastResult?.let { result ->
                EvidenceCard(
                    result = result,
                    modifier = Modifier.widthIn(max = 480.dp)
                )
            }

            // Large red blocked card (e.g. no fault code read) — unmissable.
            blockInfo?.let { block ->
                WorkBlockedCard(
                    block = block,
                    onRecapture = { imagePicker.launch("image/*") },
                    modifier = Modifier.widthIn(max = 480.dp)
                )
            }

            // Fault image preview thumbnail (once one has been uploaded).
            faultImage?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Uploaded fault code image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .background(PanelDark, RoundedCornerShape(8.dp))
                )
            }

            // Reading… state while the fault image is being analyzed on-device.
            if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
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
                        text = "Reading…",
                        color = TextLight,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Push-to-talk mic button (requests RECORD_AUDIO on first tap).
            PrimaryButton(
                text = if (isListening) "Listening…" else "🎤 Push to Talk",
                enabled = !isListening && !isAnalyzing,
                onClick = {
                    if (hasMicPermission) {
                        startListening(vm, speech)
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.widthIn(max = 480.dp)
            )

            // Upload a photo of the VFD fault display.
            PrimaryButton(
                text = "Upload Fault Code Image",
                enabled = !isAnalyzing,
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.widthIn(max = 480.dp)
            )

            // Gated transition into the guided lockout flow.
            PrimaryButton(
                text = "Start Guided LOTO",
                enabled = canStartLoto,
                onClick = { vm.startGuidedLockout() },
                modifier = Modifier.widthIn(max = 480.dp)
            )
        }
    }
}

/** Wires the speech callbacks to the VM and starts a push-to-talk session. */
private fun startListening(vm: SessionViewModel, speech: SpeechManager) {
    speech.onResult = { vm.onUserUtterance(it) }
    speech.onListening = { vm.setListening(it) }
    speech.start()
}

/** Monospace "NPU: X ms" readout; shows "NPU: — ms" when latency is unknown. */
@Composable
private fun NpuReadout(latencyMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = if (latencyMs > 0L) "NPU: $latencyMs ms" else "NPU: — ms",
        color = Caution,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(PanelDark.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * "MOCK"/"VLM" source tag. In VLM mode it shows the live model lifecycle:
 * not loaded → ready → loading model… → model ready ✓ (or load failed).
 */
@Composable
private fun SourceIndicator(
    useMock: Boolean,
    modelState: VlmModelState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(PanelDark.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (useMock) "MOCK" else "VLM",
            color = if (useMock) TextLight.copy(alpha = 0.85f) else Caution,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        if (!useMock) {
            val (label, color) = when (modelState) {
                VlmModelState.NOT_LOADED -> "model not loaded" to Alert
                VlmModelState.READY -> "ready" to TextLight.copy(alpha = 0.7f)
                VlmModelState.LOADING -> "loading model…" to Caution
                VlmModelState.WARM -> "model ready ✓" to Verified
                VlmModelState.FAILED -> "load failed" to Alert
            }
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
