package com.electriclens.detection

import kotlinx.coroutines.flow.Flow

/**
 * A single detection event produced by a [DetectionSource].
 *
 * The architecture is deliberately source-agnostic: the ViewModel depends ONLY on
 * the [DetectionSource] interface. Today that interface is fulfilled by
 * [MockDetectionSource]; in Piece 2 it will be fulfilled by an on-device
 * ExecuTorch model via [ExecutorchDetectionSource] with NO ViewModel changes.
 */
data class Detection(
    val type: DetectionType,
    val confidence: Float,
    val timestampMs: Long,
    val code: String? = null,
    val identity: String? = null
)

enum class DetectionType {
    FAULT_CODE,
    BREAKER_B201_OFF,
    BREAKER_B205_OFF,
    LOCK_TAG,
    MCC_OPEN
}

interface DetectionSource {
    /** Stream of detections emitted by this source. */
    val detections: Flow<Detection>

    /** Begin producing detections (e.g. wire up frame analysis). */
    fun start()

    /** Stop producing detections and release any resources. */
    fun stop()
}
