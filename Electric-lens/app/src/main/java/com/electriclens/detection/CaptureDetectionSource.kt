package com.electriclens.detection

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * A [DetectionSource] that produces detections in response to explicit capture
 * events (e.g. the user tapping "Capture evidence" in the guided LOTO flow),
 * rather than from a continuous frame stream.
 *
 * This keeps the ViewModel source-agnostic: both the mock source and the real
 * on-device VLM source implement the same capture-driven contract, exposing
 * lightweight diagnostics (latency, last raw answer, model readiness) for the UI.
 */
interface CaptureDetectionSource : DetectionSource {

    /**
     * Handle a capture of [type] with an optional [bitmap]. Implementations run
     * inference (off the main thread) and emit a [Detection] via [detections]
     * when something is detected. Safe to call without a bitmap (mock / no-camera
     * paths).
     */
    suspend fun onCapture(type: DetectionType, bitmap: Bitmap?)

    /** Latency of the most recent capture inference, in milliseconds. */
    val latencyMs: StateFlow<Long>

    /** Raw model answer (or status message) from the most recent capture. */
    val lastAnswer: StateFlow<String>

    /** Whether the underlying detection model is loaded and ready. */
    val modelReady: StateFlow<Boolean>

    /** Fine-grained model lifecycle for the UI (not-loaded → ready → loading → warm). */
    val modelState: StateFlow<VlmModelState>
}
