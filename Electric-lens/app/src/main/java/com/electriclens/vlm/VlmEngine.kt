package com.electriclens.vlm

import android.graphics.Bitmap

/**
 * Minimal contract for a vision-language inference backend.
 *
 * Engines are model-agnostic and stateless beyond the loaded model: they take
 * one image + one already-formatted prompt and return the raw decoded text. All
 * model-specific prompting/parsing lives in [VlmModelAdapter].
 */
interface VlmEngine {
    /** True only when the underlying runtime + model are actually usable. */
    val ready: Boolean

    /**
     * Run a single image+prompt inference and return the raw answer text.
     * @throws IllegalStateException (or another Throwable) on failure; callers
     *   are expected to catch and degrade gracefully.
     */
    fun generate(bitmap: Bitmap, prompt: String): String

    /** Release native resources. Safe to call more than once. */
    fun close()
}

/** A no-op engine that is never ready; used as an honest fallback. */
internal object NoopVlmEngine : VlmEngine {
    override val ready: Boolean = false
    override fun generate(bitmap: Bitmap, prompt: String): String =
        throw IllegalStateException("No VLM engine available")
    override fun close() {}
}
