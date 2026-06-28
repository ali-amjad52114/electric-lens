package com.electriclens.detection

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mock detection source used for the clickable demo (Piece 1).
 *
 * Detections are pushed manually via [emit] — typically when the user taps
 * "Capture evidence" in the guided LOTO flow. This lets the entire UX be driven
 * end-to-end without any ML on device.
 *
 * It also implements [CaptureDetectionSource] so it is drop-in interchangeable
 * with the real on-device VLM source ([VlmDetectionSource]): [onCapture] simply
 * forwards to [emit], and the diagnostic flows report a ready "model".
 */
class MockDetectionSource : CaptureDetectionSource {

    private val _detections = MutableSharedFlow<Detection>(
        replay = 0,
        extraBufferCapacity = 16
    )

    override val detections: Flow<Detection> = _detections.asSharedFlow()

    // Diagnostics: the mock is always "ready", instant, with no raw answer.
    override val latencyMs: StateFlow<Long> = MutableStateFlow(0L)
    override val lastAnswer: StateFlow<String> = MutableStateFlow("")
    override val modelReady: StateFlow<Boolean> = MutableStateFlow(true)
    override val modelState: StateFlow<VlmModelState> = MutableStateFlow(VlmModelState.READY)

    @Volatile
    private var running = false

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    /**
     * Capture-driven entry point. The bitmap is ignored in mock mode; the demo
     * simply emits the expected detection for [type].
     */
    override suspend fun onCapture(type: DetectionType, bitmap: Bitmap?) {
        emit(type)
    }

    /**
     * Push a detection of [type] with full confidence and the current timestamp.
     * For FAULT_CODE it carries the canned demo code so the gate (confidence +
     * non-blank code) passes — the UI separately warns that the source is mock.
     * Safe to call regardless of [start]/[stop] state for demo purposes.
     */
    fun emit(type: DetectionType) {
        _detections.tryEmit(
            Detection(
                type = type,
                confidence = 0.99f,
                timestampMs = System.currentTimeMillis(),
                code = if (type == DetectionType.FAULT_CODE)
                    com.electriclens.data.DemoData.manual.faultCode else null,
                identity = when (type) {
                    DetectionType.BREAKER_B201_OFF -> "B-201"
                    DetectionType.BREAKER_B205_OFF -> "B-205"
                    else -> null
                }
            )
        )
    }
}
