package com.electriclens.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.electriclens.vlm.VlmConfigs
import com.electriclens.vlm.VlmEngine
import com.electriclens.vlm.VlmEngineFactory
import com.electriclens.vlm.VlmModelAdapter
import com.electriclens.vlm.VlmModelId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-device VLM detection source (Piece 2).
 *
 * Capture-driven, drop-in replacement for [MockDetectionSource]: it implements
 * the same [CaptureDetectionSource] contract so the ViewModel needs no changes.
 *
 * Robustness contract: this source NEVER crashes the LOTO state machine, and it
 * NEVER masquerades a failure as a detection. EVERY capture reports an HONEST
 * result: the real model confidence on success, or a zero-confidence detection
 * on inference failure / not-ready. The ViewModel always hears a verdict, but a
 * zero-confidence detection can never advance LOTO (it fails the safety gate).
 *
 * All inference runs off the main thread (Dispatchers.Default).
 */
class VlmDetectionSource(
    private val context: Context,
    private val modelId: VlmModelId
) : CaptureDetectionSource {

    private val adapter = VlmModelAdapter(VlmConfigs.forModel(modelId))

    private val _detections = MutableSharedFlow<Detection>(
        replay = 0,
        extraBufferCapacity = 8
    )
    override val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    private val _latencyMs = MutableStateFlow(0L)
    override val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _lastAnswer = MutableStateFlow("")
    override val lastAnswer: StateFlow<String> = _lastAnswer.asStateFlow()

    private val _modelReady = MutableStateFlow(false)
    override val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    private val _modelState = MutableStateFlow(VlmModelState.NOT_LOADED)
    override val modelState: StateFlow<VlmModelState> = _modelState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var engine: VlmEngine? = null

    override fun start() {
        // Build the engine OFF the main thread; reflect availability in modelReady.
        scope.launch {
            val built = try {
                VlmEngineFactory.create(context, VlmConfigs.forModel(modelId))
            } catch (t: Throwable) {
                Log.w(TAG, "Engine creation failed", t)
                null
            }
            engine = built
            val ready = built?.ready == true
            _modelReady.value = ready
            _modelState.value = if (ready) VlmModelState.READY else VlmModelState.NOT_LOADED
            if (!ready) {
                _lastAnswer.value = MSG_NOT_LOADED
            }
        }
    }

    override fun stop() {
        runCatching { engine?.close() }
        engine = null
        _modelReady.value = false
        _modelState.value = VlmModelState.NOT_LOADED
        scope.coroutineContext.cancel()
    }

    override suspend fun onCapture(type: DetectionType, bitmap: Bitmap?) {
        val eng = engine
        val prompt = adapter.promptFor(type)

        if (eng != null && eng.ready && bitmap != null) {
            try {
                // First inference loads the model onto the NPU — surface "loading".
                if (_modelState.value != VlmModelState.WARM) {
                    _modelState.value = VlmModelState.LOADING
                }
                val start = System.currentTimeMillis()
                val raw = withContext(Dispatchers.Default) {
                    eng.generate(bitmap, prompt)
                }
                val elapsed = System.currentTimeMillis() - start
                _latencyMs.value = elapsed
                _lastAnswer.value = raw
                _modelState.value = VlmModelState.WARM

                // Honest result: real confidence + carried code (for FAULT_CODE).
                _detections.tryEmit(adapter.evaluate(type, raw, System.currentTimeMillis()))
                return
            } catch (t: Throwable) {
                // Inference failed; surface the error and emit an HONEST 0f result
                // so the VM hears a verdict that cannot pass the safety gate.
                Log.w(TAG, "VLM inference failed for $type", t)
                _lastAnswer.value = "Inference error: ${t.message ?: t.javaClass.simpleName}"
                _modelState.value = VlmModelState.FAILED
                _detections.tryEmit(Detection(type, 0f, System.currentTimeMillis()))
                return
            }
        }

        // Not ready, or no camera bitmap available: honest 0f result.
        _lastAnswer.value = MSG_NOT_LOADED
        _detections.tryEmit(Detection(type, 0f, System.currentTimeMillis()))
    }

    companion object {
        private const val TAG = "VlmDetectionSource"
        private const val MSG_NOT_LOADED = "Model not loaded"
    }
}
