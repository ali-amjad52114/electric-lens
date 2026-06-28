package com.electriclens.vlm

import android.graphics.Bitmap
import android.util.Log
import com.electriclens.executorch.QnnMultimodalRunner
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The 3-part Qualcomm QNN SmolVLM path (vision_encoder + tok_embedding + decoder),
 * running ON THE HEXAGON NPU, in-process.
 *
 * This is now a REAL engine. It drives [QnnMultimodalRunner] — a thin JNI bridge
 * to ExecuTorch's `QNNMultimodalRunner` compiled into libexecutorch.so (built
 * with QNN_SDK_ROOT). Loading the three .pte files, the QNN HTP backend, and the
 * Hexagon skel all happen inside the app process.
 *
 * Inference flow per capture:
 *   1. Preprocess the bitmap to a normalized float32 CHW raw ((x/255-0.5)/0.5),
 *      written to the app cache (the native runner reads it back, exactly as the
 *      reference qnn_multimodal_runner does with its .raw input).
 *   2. Call the runner with the RAW question — the native side applies the
 *      SmolVLM chat template + <image> expansion + dispatch, then decodes to EOS.
 *
 * Robustness: the runner is constructed lazily on first [generate]. If the QNN
 * backend cannot initialize (missing libs, device policy, etc.), [generate]
 * throws and the caller ([com.electriclens.detection.VlmDetectionSource]) catches
 * it, surfaces the message, and degrades to mock-style behavior — the demo never
 * crashes.
 */
class QnnSplitVlmEngine(
    private val config: VlmConfig,
    private val visionEncoderPath: String?,
    private val tokEmbeddingPath: String?,
    private val decoderPath: String,
    private val tokenizerPath: String,
    private val nativeLibDir: String,
    private val cacheDir: File
) : VlmEngine {

    override val ready: Boolean
        get() = visionEncoderPath != null && File(visionEncoderPath).exists() &&
            tokEmbeddingPath != null && File(tokEmbeddingPath).exists() &&
            File(decoderPath).exists() &&
            File(tokenizerPath).exists()

    /**
     * Run ONE inference on a freshly-constructed runner.
     *
     * The native QNN multimodal runner accumulates its KV-cache position
     * (`cur_pos_`) across generate() calls (it's a multi-turn design) and never
     * resets it, so the 3rd independent inference trips an ET_CHECK and aborts the
     * process. We use the runner for INDEPENDENT captures, so we build a new
     * runner per call (cur_pos_ starts at 0). The QNN HTP context is created and
     * torn down per inference anyway, so this does not change the warm-vs-cold cost
     * materially. Constructed under @Synchronized so concurrent captures serialize.
     */
    @Synchronized
    override fun generate(bitmap: Bitmap, prompt: String): String {
        val rawPath = writeNormalizedRaw(bitmap)
        // seq_len = prompt(+image tokens) + output budget, well within 4096 ctx.
        val seqLen = (config.maxNewTokens + 192).coerceAtMost(1024)
        Log.i(TAG, "Constructing fresh QnnMultimodalRunner for this capture…")
        val r = QnnMultimodalRunner(
            encoderPath = requireNotNull(visionEncoderPath) { "visionEncoderPath null" },
            tokEmbeddingPath = requireNotNull(tokEmbeddingPath) { "tokEmbeddingPath null" },
            decoderPath = decoderPath,
            tokenizerPath = tokenizerPath,
            nativeLibDir = nativeLibDir,
            modelVersion = "smolvlm",
            evalMode = 1,
            temperature = 0.0f
        )
        return try {
            r.generate(rawPath, prompt, systemPrompt = "", seqLen = seqLen).trim()
        } finally {
            runCatching { r.close() }
        }
    }

    /**
     * Resize to [VlmConfig.imageSize], normalize each channel with config mean/std,
     * and write as float32 little-endian in CHW order (R plane, G plane, B plane).
     * Size matches the encoder's expected [1,3,size,size] input (size*size*3*4 bytes).
     */
    private fun writeNormalizedRaw(bitmap: Bitmap): String {
        val size = config.imageSize
        val scaled = if (bitmap.width != size || bitmap.height != size) {
            Bitmap.createScaledBitmap(bitmap, size, size, /* filter = */ true)
        } else {
            bitmap
        }
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val buf = ByteBuffer
            .allocateDirect(3 * size * size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        val fb = buf.asFloatBuffer()
        val mean = config.mean
        val std = config.std
        // Channel 0 (R)
        for (p in pixels) {
            val r = (p ushr 16) and 0xFF
            fb.put((r / 255f - mean[0]) / std[0])
        }
        // Channel 1 (G)
        for (p in pixels) {
            val g = (p ushr 8) and 0xFF
            fb.put((g / 255f - mean[1]) / std[1])
        }
        // Channel 2 (B)
        for (p in pixels) {
            val b = p and 0xFF
            fb.put((b / 255f - mean[2]) / std[2])
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val out = File(cacheDir, "vlm_input.raw")
        FileOutputStream(out).channel.use { ch ->
            buf.rewind()
            while (buf.hasRemaining()) {
                ch.write(buf)
            }
        }
        return out.absolutePath
    }

    override fun close() {
        // No long-lived runner is held — each generate() builds and closes its own.
    }

    companion object {
        private const val TAG = "QnnSplitVlmEngine"
    }
}
