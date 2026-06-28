package com.electriclens.vlm

import android.graphics.Bitmap
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * REFLECTION wrapper over ExecuTorch's `LlmModule` for a COMBINED `.pte`.
 *
 * The whole point of reflection here: `executorch.aar` is NOT on the classpath
 * yet, so this file must compile with ZERO `org.pytorch.executorch.*` imports.
 * Every ExecuTorch type is referenced by name through reflection. If the classes
 * (and the model file) are absent at runtime, [ready] is simply false and the
 * caller falls back to mock-style behavior — nothing crashes.
 *
 * NOTE: This engine targets the COMBINED single-`.pte` path. The discovered
 * SmolVLM export is a 3-part QNN split that the stock `LlmModule` CANNOT load —
 * that path is handled by [QnnSplitVlmEngine] instead.
 *
 * Mirrored API (verified ExecuTorch Android):
 *   org.pytorch.executorch.extension.llm.LlmModule
 *     ctor (modelType:Int=2, modulePath:String, tokenizerPath:String, temperature:Float)
 *     MODEL_TYPE_TEXT_VISION = 2
 *     fun load(): Unit
 *     fun generate(image:IntArray?, width:Int, height:Int, channels:Int,
 *                  prompt:String, seqLen:Int, callback:LlmCallback, echo:Boolean)
 *     fun stop(); fun resetContext(); fun close()
 *   org.pytorch.executorch.extension.llm.LlmCallback
 *     fun onResult(result:String); fun onStats(stats:String); fun onError(code:Int,msg:String)
 */
class ExecuTorchLlmEngine(
    private val config: VlmConfig,
    /** Absolute path to the combined .pte on the device filesystem. */
    private val modelPath: String,
    /** Absolute path to tokenizer.json on the device filesystem. */
    private val tokenizerPath: String,
    private val temperature: Float = 0.8f
) : VlmEngine {

    private val llmModuleClass: Class<*>? = runCatching {
        Class.forName(LLM_MODULE_CLASS)
    }.getOrNull()

    private val llmCallbackClass: Class<*>? = runCatching {
        Class.forName(LLM_CALLBACK_CLASS)
    }.getOrNull()

    @Volatile
    private var module: Any? = null

    @Volatile
    private var loadFailed: Boolean = false

    /**
     * Ready only when: the LlmModule class is loadable, the callback interface is
     * loadable, AND the model file actually exists on disk. (We do not eagerly
     * load the module here; loading happens lazily on first generate.)
     */
    override val ready: Boolean
        get() = llmModuleClass != null &&
            llmCallbackClass != null &&
            !loadFailed &&
            java.io.File(modelPath).exists() &&
            java.io.File(tokenizerPath).exists()

    override fun generate(bitmap: Bitmap, prompt: String): String {
        try {
            val cb = llmModuleClass
                ?: throw IllegalStateException("ExecuTorch LlmModule class not present on classpath")
            val callbackClass = llmCallbackClass
                ?: throw IllegalStateException("ExecuTorch LlmCallback class not present on classpath")

            val mod = ensureLoaded(cb)

            // --- Preprocess: resize to square, uint8 RGB, HWC IntArray (0..255). ---
            val size = config.imageSize
            val image = bitmapToRgbIntArray(bitmap, size)

            // --- Build the full prompt via the model's chat template. ---
            val fullPrompt = String.format(config.promptTemplate, prompt)

            // --- Accumulate streamed tokens via a reflective LlmCallback proxy. ---
            val sb = StringBuilder()
            val done = CountDownLatch(1)
            val errorRef = AtomicReference<String?>(null)

            val handler = InvocationHandler { _, method: Method, args: Array<out Any?>? ->
                when (method.name) {
                    "onResult" -> {
                        val token = args?.getOrNull(0) as? String
                        if (token != null) sb.append(token)
                        null
                    }
                    "onStats" -> null
                    "onError" -> {
                        val code = args?.getOrNull(0)
                        val msg = args?.getOrNull(1) as? String
                        errorRef.set("ExecuTorch onError(code=$code, msg=$msg)")
                        done.countDown()
                        null
                    }
                    // java.lang.Object methods on the proxy.
                    "toString" -> "ExecuTorchLlmCallbackProxy"
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> false
                    else -> null
                }
            }

            val callbackProxy = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                handler
            )

            // generate(image, width, height, channels, prompt, seqLen, callback, echo)
            val generateMethod = cb.getMethod(
                "generate",
                IntArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
                callbackClass,
                Boolean::class.javaPrimitiveType
            )

            // The stock generate() is synchronous (blocks until generation ends),
            // streaming tokens through the callback on the calling thread. We treat
            // its return as completion; the latch guards the onError early-exit path.
            generateMethod.invoke(
                mod,
                image,
                size,
                size,
                CHANNELS,
                fullPrompt,
                config.maxNewTokens,
                callbackProxy,
                false
            )
            done.countDown()
            done.await()

            errorRef.get()?.let { throw IllegalStateException(it) }

            return cleanAnswer(sb.toString())
        } catch (t: Throwable) {
            // Rethrow everything (reflection or runtime) as a clean message; the
            // caller handles fallback to mock behavior.
            throw IllegalStateException(
                "ExecuTorch combined-VLM inference failed: ${t.message}",
                t
            )
        }
    }

    private fun ensureLoaded(cb: Class<*>): Any {
        module?.let { return it }
        synchronized(this) {
            module?.let { return it }
            try {
                // Constructor (modelType:Int, modulePath:String, tokenizerPath:String, temperature:Float)
                val ctor = cb.getConstructor(
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                    Float::class.javaPrimitiveType
                )
                val instance = ctor.newInstance(
                    MODEL_TYPE_TEXT_VISION,
                    modelPath,
                    tokenizerPath,
                    temperature
                )
                cb.getMethod("load").invoke(instance)
                module = instance
                return instance
            } catch (t: Throwable) {
                loadFailed = true
                throw IllegalStateException("Failed to load ExecuTorch LlmModule: ${t.message}", t)
            }
        }
    }

    override fun close() {
        val mod = module ?: return
        module = null
        runCatching { llmModuleClass?.getMethod("close")?.invoke(mod) }
    }

    private fun cleanAnswer(raw: String): String {
        var text = raw
        // Strip the eos marker if the runtime echoes it.
        val eos = "<end_of_utterance>"
        val idx = text.indexOf(eos)
        if (idx >= 0) text = text.substring(0, idx)
        return text.trim()
    }

    companion object {
        const val LLM_MODULE_CLASS = "org.pytorch.executorch.extension.llm.LlmModule"
        const val LLM_CALLBACK_CLASS = "org.pytorch.executorch.extension.llm.LlmCallback"
        const val MODEL_TYPE_TEXT_VISION = 2
        const val CHANNELS = 3

        /**
         * Resize [bitmap] to [size] x [size] and pack as uint8 RGB in HWC order:
         * for each pixel, [R, G, B] with each value in 0..255. Length = size*size*3.
         */
        internal fun bitmapToRgbIntArray(bitmap: Bitmap, size: Int): IntArray {
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val pixels = IntArray(size * size)
            scaled.getPixels(pixels, 0, size, 0, 0, size, size)
            val out = IntArray(size * size * CHANNELS)
            var o = 0
            for (p in pixels) {
                out[o++] = (p shr 16) and 0xFF // R
                out[o++] = (p shr 8) and 0xFF  // G
                out[o++] = p and 0xFF          // B
            }
            if (scaled !== bitmap) scaled.recycle()
            return out
        }
    }
}
