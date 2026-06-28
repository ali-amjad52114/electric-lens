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

                // ---- Phase 2+3: ONE warm engine (mirrors the real app) over the
                // fault read + all 4 LOTO label reads. Logs raw + parsed for each.
                val cfg = VlmConfigs.SMOLVLM
                val adapter = VlmModelAdapter(cfg)
                val engine = VlmEngineFactory.create(context, cfg)
                Log.i(TAG, "=== PRODUCTION ENGINE TEST (one warm engine) ready=${engine.ready} ===")
                val reads = listOf(
                    DetectionType.FAULT_CODE to "vfd_test.png",
                    DetectionType.BREAKER_B201_OFF to "breaker_b201_off.png",
                    DetectionType.BREAKER_B205_OFF to "breaker_b205_off.png",
                    DetectionType.LOCK_TAG to "lock_tag.png",
                    DetectionType.MCC_OPEN to "mcc_open.png"
                )
                for ((type, fname) in reads) {
                    val f = File(context.filesDir, "m/$fname")
                    if (!f.exists()) { Log.w(TAG, "$fname missing"); continue }
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    val tp = System.currentTimeMillis()
                    val ans = engine.generate(bmp, adapter.promptFor(type))
                    val ms = System.currentTimeMillis() - tp
                    val det = adapter.evaluate(type, ans, System.currentTimeMillis())
                    Log.i(TAG, "READ $type (${ms}ms) raw>>> $ans")
                    Log.i(TAG, "READ $type parsed: conf=${det.confidence} identity=${det.identity} code=${det.code} PASS=${det.confidence >= 0.70f}")
                }
                // ---- Phase 4: REAL-PHOTO BREAKER PROBE ----------------------
                // Run the EXACT breaker prompt on real breaker photographs placed
                // in files/m/probe/. This tests whether the VLM analyses physical
                // handle state on real images — vs just reading the synthetic cards.
                val probeDir = File(context.filesDir, "m/probe")
                val probeFiles = probeDir.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                Log.i(TAG, "=== REAL-PHOTO BREAKER PROBE (${probeFiles.size} images) ===")
                val breakerPrompt = adapter.promptFor(DetectionType.BREAKER_B201_OFF)
                Log.i(TAG, "PROBE prompt>>> $breakerPrompt")
                for (f in probeFiles) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp == null) { Log.w(TAG, "PROBE ${f.name}: decode failed"); continue }
                    val tp = System.currentTimeMillis()
                    val ans = engine.generate(bmp, breakerPrompt)
                    val ms = System.currentTimeMillis() - tp
                    val det = adapter.evaluate(DetectionType.BREAKER_B201_OFF, ans, System.currentTimeMillis())
                    Log.i(TAG, "PROBE ${f.name} (${ms}ms) raw>>> $ans")
                    Log.i(TAG, "PROBE ${f.name} verdict: conf=${det.confidence} -> ${if (det.confidence >= 0.70f) "SAYS OFF (pass)" else "not-off / unverified"}")
                }

                engine.close()

                Log.i(TAG, "=== NPU self-test DONE ===")
            } catch (t: Throwable) {
                Log.e(TAG, "NPU self-test FAILED: ${t.message}", t)
            }
        }
    }
}
