package com.electriclens.detection

/**
 * Lifecycle of the on-device VLM model, surfaced to the UI so the AI session can
 * honestly distinguish "files present" from "actually running on the NPU".
 *
 * - [NOT_LOADED]: model files are missing / engine not ready.
 * - [READY]: files are present; the model will load onto the NPU on first use.
 * - [LOADING]: an inference is in flight and the model is being loaded/run on the NPU.
 * - [WARM]: at least one inference has completed — the model is loaded and warm.
 * - [FAILED]: an inference threw (e.g. QNN/NPU init failed).
 *
 * Mock mode reports [READY].
 */
enum class VlmModelState {
    NOT_LOADED,
    READY,
    LOADING,
    WARM,
    FAILED
}
