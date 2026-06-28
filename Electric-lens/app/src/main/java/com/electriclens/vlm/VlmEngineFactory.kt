package com.electriclens.vlm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Builds a [VlmEngine] for a [VlmConfig], copying model files out of assets on
 * first run when they are present.
 *
 * Contract: NEVER throws. If nothing usable is available (assets missing,
 * ExecuTorch classes absent, custom runner absent), it returns an engine whose
 * [VlmEngine.ready] is false so the caller degrades to mock-style behavior.
 */
object VlmEngineFactory {

    private const val TAG = "VlmEngineFactory"

    fun create(context: Context, config: VlmConfig): VlmEngine {
        return try {
            val modelDir = resolveModelDir(context, config)
            // Best-effort copy from assets/<assetDir> to <modelDir> (no-op for
            // large models that are side-loaded via adb into external files dir).
            val copied = copyModelAssetsIfPresent(context, config, modelDir)
            if (!copied) {
                Log.i(TAG, "Model assets for ${config.modelId} not present; engine will be not-ready.")
            }

            val tokenizerPath = File(modelDir, config.tokenizer).absolutePath
            val decoderPath = File(modelDir, config.decoderPte).absolutePath

            if (config.split) {
                val visionPath = config.visionEncoderPte?.let { File(modelDir, it).absolutePath }
                val tokEmbPath = config.tokEmbeddingPte?.let { File(modelDir, it).absolutePath }
                QnnSplitVlmEngine(
                    config = config,
                    visionEncoderPath = visionPath,
                    tokEmbeddingPath = tokEmbPath,
                    decoderPath = decoderPath,
                    tokenizerPath = tokenizerPath,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    cacheDir = context.cacheDir
                )
            } else {
                ExecuTorchLlmEngine(
                    config = config,
                    modelPath = decoderPath,
                    tokenizerPath = tokenizerPath
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to build VLM engine; returning no-op engine.", t)
            NoopVlmEngine
        }
    }

    /**
     * Resolve where the model files live. Prefers the app's EXTERNAL files dir
     * (/sdcard/Android/data/<pkg>/files/<assetDir>) when the decoder is present
     * there — that's where large models are side-loaded via `adb push`. Falls
     * back to internal filesDir otherwise.
     */
    private fun resolveModelDir(context: Context, config: VlmConfig): File {
        val ext = context.getExternalFilesDir(null)?.let { File(it, config.assetDir) }
        if (ext != null && File(ext, config.decoderPte).exists()) {
            Log.i(TAG, "Using external model dir: ${ext.absolutePath}")
            return ext
        }
        return File(context.filesDir, config.assetDir)
    }

    /**
     * Copy every file under assets/<assetDir> into [destDir] when the asset
     * folder exists. Returns true if at least one file was copied or already
     * present, false if the asset folder is absent/empty.
     */
    private fun copyModelAssetsIfPresent(
        context: Context,
        config: VlmConfig,
        destDir: File
    ): Boolean {
        val am = context.assets
        val children = try {
            am.list(config.assetDir)
        } catch (t: Throwable) {
            null
        }
        if (children.isNullOrEmpty()) return false

        if (!destDir.exists()) destDir.mkdirs()

        var any = false
        for (name in children) {
            val assetPath = "${config.assetDir}/$name"
            val outFile = File(destDir, name)
            if (outFile.exists() && outFile.length() > 0L) {
                any = true
                continue
            }
            try {
                am.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 1 shl 16)
                    }
                }
                any = true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to copy asset $assetPath", t)
            }
        }
        return any
    }
}
