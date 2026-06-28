package com.electriclens.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.vm.EvidenceItem

/** "ON DEVICE" pill — reinforces the fully-offline value prop. */
@Composable
fun OnDevicePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(PanelDark, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Verified, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "ON DEVICE",
            color = TextLight,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Small red "LIVE" badge. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Alert, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.White, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Big, glove-friendly primary button (min 56dp tall). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Caution,
            contentColor = BgDark,
            disabledContainerColor = PanelDark,
            disabledContentColor = TextLight.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Translucent side panel of conversation lines (already capped to last 10). */
@Composable
fun ConversationPanel(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(PanelDark.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "…",
                color = TextLight.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        lines.forEach { line ->
            val isUser = line.startsWith("YOU:")
            Text(
                text = line,
                color = if (isUser) TextLight.copy(alpha = 0.75f) else TextLight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isUser) FontWeight.Normal else FontWeight.SemiBold
            )
        }
    }
}

/** A single LOTO step card with a done state. */
@Composable
fun StepCard(
    title: String,
    instruction: String,
    done: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(if (done) Verified else Caution, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (done) "✓" else "!",
                color = BgDark,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = instruction,
                color = TextLight.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Horizontal strip of captured evidence thumbnails. */
@Composable
fun EvidenceThumbStrip(items: List<EvidenceItem>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = item.stepName,
                    modifier = Modifier
                        .size(72.dp)
                        .background(PanelDark, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.stepName,
                    color = TextLight.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(72.dp)
                )
            }
        }
    }
}

// ============================================================================
//  CameraX preview + frame capture (shared by both camera screens).
// ============================================================================

/**
 * Holds a reference to the live [PreviewView] so [capture] can grab the current
 * frame. When no camera is available (permission denied / mock mode), capture()
 * returns a generated solid-color placeholder bitmap so the LOTO flow NEVER
 * breaks and the app never crashes.
 */
class CameraCaptureController {

    internal var previewView: PreviewView? = null

    fun capture(): Bitmap? {
        val bmp = previewView?.bitmap
        if (bmp != null) return bmp
        return placeholderBitmap()
    }

    private fun placeholderBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PanelDark.toArgb())
        return bitmap
    }
}

@Composable
fun rememberCameraCaptureController(): CameraCaptureController =
    remember { CameraCaptureController() }

/**
 * CameraX [PreviewView] hosted in an [AndroidView]. When [hasPermission] is
 * false, renders a dark placeholder instead of binding the camera.
 */
@Composable
fun CameraPreview(
    controller: CameraCaptureController,
    hasPermission: Boolean,
    modifier: Modifier = Modifier
) {
    if (!hasPermission) {
        // Detach any stale preview reference so capture() falls back to placeholder.
        controller.previewView = null
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera unavailable — mock mode",
                color = TextLight.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            controller.previewView = previewView

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                } catch (t: Throwable) {
                    // Never crash the demo over a camera bind failure.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
