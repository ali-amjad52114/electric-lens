package com.electriclens

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.electriclens.detection.DetectionType
import com.electriclens.executorch.QnnMultimodalRunner
import com.electriclens.vlm.VlmConfigs
import com.electriclens.vlm.VlmEngineFactory
import com.electriclens.vlm.VlmModelAdapter
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug-only, in-process verification that the QNN SmolVLM runner actually
 * executes on the Hexagon NPU inside the app's own process (not adb shell).
 *
 * Gated on a marker file so it never runs in normal use:
 *   adb push (touch) /sdcard/Android/data/com.electriclens/files/SELFTEST
 *
 * Uses the on-device model at files/models/smolvlm_500m/ and a pre-normalized
 * float32 CHW raw at files/m/vision_encoder_input_0_0.raw. Logs to tag NPUSelfTest.
 */
object NpuSelfTest {
    private const val TAG = "NPUSelfTest"

    fun maybeRun(context: Context) {
        val ext = context.getExternalFilesDir(null) ?: return
        val marker = File(ext, "SELFTEST")
        if (!marker.exists()) return

        thread(name = "npu-selftest") {
            try {
                // Model + raw live in INTERNAL filesDir (the app fully controls it;
                // adb-created subdirs under external Android/data aren't traversable
                // by the app on this device).
                val modelDir = File(context.filesDir, "models/smolvlm_500m")
                val raw = File(context.filesDir, "m/vision_encoder_input_0_0.raw")
                Log.i(TAG, "=== NPU self-test START ===")
                Log.i(TAG, "modelDir=$modelDir exists=${modelDir.exists()}")
                Log.i(TAG, "raw=$raw exists=${raw.exists()} size=${raw.length()}")
                Log.i(TAG, "nativeLibDir=${context.applicationInfo.nativeLibraryDir}")

                val t0 = System.currentTimeMillis()
                val runner = QnnMultimodalRunner(
                    encoderPath = File(modelDir, "vision_encoder_qnn.pte").absolutePath,
                    tokEmbeddingPath = File(modelDir, "tok_embedding_qnn.pte").absolutePath,
                    decoderPath = File(modelDir, "hybrid_llama_qnn.pte").absolutePath,
                    tokenizerPath = File(modelDir, "tokenizer.json").absolutePath,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    modelVersion = "smolvlm",
                    evalMode = 1,
                    temperature = 0.0f
                )
                val tCreate = System.currentTimeMillis() - t0
                Log.i(TAG, "runner CREATED in ${tCreate}ms ready=${runner.isReady}")

                val t1 = System.currentTimeMillis()
                val out = runner.generate(
                    imageRawPath = raw.absolutePath,
                    prompt = "Describe this image.",
                    systemPrompt = "",
                    seqLen = 128
                )
                val tGen = System.currentTimeMillis() - t1
                Log.i(TAG, "=== NPU OUTPUT (gen ${tGen}ms) ===")
                Log.i(TAG, "OUTPUT>>> $out")
                runner.close()

                // ---- Phase 2: full PRODUCTION path (camera-equivalent) -------
                // Decode a real PNG -> Bitmap and run it through the exact engine
                // the live capture uses: VlmEngineFactory -> QnnSplitVlmEngine
                // (bitmap preprocessing) -> NPU -> VlmModelAdapter fault parse.
                val png = File(context.filesDir, "m/vfd_test.png")
                if (png.exists()) {
                    Log.i(TAG, "=== PRODUCTION ENGINE TEST (real Bitmap) ===")
                    val bmp = BitmapFactory.decodeFile(png.absolutePath)
                    Log.i(TAG, "decoded bitmap ${bmp.width}x${bmp.height}")
                    val cfg = VlmConfigs.SMOLVLM
                    val engine = VlmEngineFactory.create(context, cfg)
                    Log.i(TAG, "engine ready=${engine.ready}")
                    val adapter = VlmModelAdapter(cfg)
                    val faultPrompt = adapter.promptFor(DetectionType.FAULT_CODE)
                    val tp = System.currentTimeMillis()
                    val answer = engine.generate(bmp, faultPrompt)
                    Log.i(TAG, "PROD answer (${System.currentTimeMillis() - tp}ms): $answer")
                    val code = adapter.extractFaultCode(answer)
                    val det = adapter.evaluate(DetectionType.FAULT_CODE, answer, System.currentTimeMillis())
                    Log.i(TAG, "PROD extracted code=$code detection=$det")
                    engine.close()
                } else {
                    Log.w(TAG, "vfd_test.png not present; skipping production engine test")
                }

                Log.i(TAG, "=== NPU self-test DONE ===")
            } catch (t: Throwable) {
                Log.e(TAG, "NPU self-test FAILED: ${t.message}", t)
            }
        }
    }
}
