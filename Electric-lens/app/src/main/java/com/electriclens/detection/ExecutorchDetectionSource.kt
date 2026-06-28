package com.electriclens.detection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * ============================================================================
 *  PIECE 2 INTEGRATION POINT — ExecuTorch on-device detection.
 * ============================================================================
 *
 * This is an EMPTY STUB today. It implements [DetectionSource] so the rest of
 * the app (ViewModel, screens) can swap to it WITHOUT any other code changes —
 * the ViewModel depends only on the [DetectionSource] interface.
 *
 * Piece 2 will replace the stub bodies below. The three things Piece 2 must do
 * are marked with explicit TODOs:
 *
 *  TODO(Piece 2 — (a) LOAD MODEL):
 *      In [start], load the exported .pte model using the ExecuTorch Android
 *      runtime (e.g. org.pytorch.executorch.Module.load(modelPath)). Keep the
 *      loaded Module as a field; release it in [stop].
 *
 *  TODO(Piece 2 — (b) RUN INFERENCE ON CAMERA FRAMES):
 *      Hook this source into the shared CameraX ImageAnalysis pipeline
 *      (see ui/components/SharedComponents.kt CameraPreview / capture path).
 *      For each analyzed frame: convert the ImageProxy/Bitmap to the model's
 *      input EValue/Tensor, then call module.forward(input).
 *
 *  TODO(Piece 2 — (c) MAP OUTPUTS TO Detection EVENTS):
 *      Interpret the model output tensor, map the predicted class to the
 *      matching [DetectionType] (FAULT_CODE, BREAKER_B201_OFF, BREAKER_B205_OFF,
 *      LOCK_TAG, MCC_OPEN), and emit a [Detection] (with real confidence and
 *      System.currentTimeMillis()) through a MutableSharedFlow exposed as
 *      [detections]. Apply a confidence threshold + debounce so the LOTO state
 *      machine in SessionViewModel advances cleanly.
 *
 * Until then: detections is empty and start()/stop() do nothing, so selecting
 * this source simply produces no automatic detections.
 * ============================================================================
 */
class ExecutorchDetectionSource : DetectionSource {

    // TODO(Piece 2): replace emptyFlow() with a MutableSharedFlow<Detection>
    //  that is fed by model inference results (see (c) above).
    override val detections: Flow<Detection> = emptyFlow()

    override fun start() {
        // TODO(Piece 2 — (a)/(b)): load the .pte model and begin frame analysis.
    }

    override fun stop() {
        // TODO(Piece 2): stop frame analysis and release the loaded Module.
    }
}
