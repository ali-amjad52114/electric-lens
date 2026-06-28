package com.electriclens.vm

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.electriclens.ai.Intent
import com.electriclens.ai.SessionAssistant
import com.electriclens.data.DemoData
import com.electriclens.data.DocSummarizer
import com.electriclens.detection.CaptureDetectionSource
import com.electriclens.detection.Detection
import com.electriclens.detection.DetectionType
import com.electriclens.detection.MockDetectionSource
import com.electriclens.detection.VlmDetectionSource
import com.electriclens.detection.VlmModelState
import com.electriclens.pdf.PdfTextExtractor
import com.electriclens.pdf.PermitInput
import com.electriclens.pdf.PermitPdfGenerator
import com.electriclens.vlm.VlmModelId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppState {
    IDLE,
    SESSION_STARTED,
    DOCS_PROCESSED,
    LIVE_SESSION,
    LOTO_B201,
    LOTO_B205,
    LOTO_LOCK_TAG,
    LOTO_MCC_OPEN,
    PERMIT_READY
}

enum class DocStatus { PROCESSING, PROCESSED }

data class ProcessedDoc(
    val name: String,
    val status: DocStatus,
    val summary: String?
)

data class EvidenceItem(
    val stepName: String,
    val timestampMs: Long,
    val bitmap: Bitmap
)

/** Last VLM/mock read surfaced to the UI evidence card. */
data class VlmResult(
    val source: String,
    val runtimeMs: Long,
    val confidence: Float,
    val type: DetectionType,
    val code: String?
)

/** Details of a blocked capture (identity mismatch / unverified) for the UI. */
data class BlockInfo(
    val reason: String,
    val expected: String,
    val detected: String
)

/**
 * Single source of truth for the whole Electric Lens demo flow.
 *
 * IMPORTANT: this ViewModel depends ONLY on the [CaptureDetectionSource]
 * interface. The active source starts as [MockDetectionSource] and can be
 * swapped for a [VlmDetectionSource] at runtime with NO changes to the rest of
 * the app — there are no direct references to the VLM engine here, only the
 * detection interface plus the [VlmModelId] used to build the source.
 *
 * It is an [AndroidViewModel] purely so it can hand an Application [Context] to
 * the [VlmDetectionSource] (which needs filesDir / assets to load a model). No
 * other Android UI types leak into this layer beyond Context and Bitmap.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    // ---- Detection source (interface-only dependency) -----------------------
    // SAFETY: default to the REAL VLM source. The mock is opt-in only.
    private var activeSource: CaptureDetectionSource =
        VlmDetectionSource(getApplication(), VlmModelId.DEFAULT)
    private var detectionJob: Job? = null
    private var mirrorJob: Job? = null

    /** Positive-verdict confidence threshold for advancing LOTO / accepting a code. */
    private val CONFIDENCE_THRESHOLD = 0.70f

    // ---- Public state -------------------------------------------------------
    private val _state = MutableStateFlow(AppState.IDLE)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _conversation = MutableStateFlow<List<String>>(emptyList())
    val conversation: StateFlow<List<String>> = _conversation.asStateFlow()

    private val _evidence = MutableStateFlow<List<EvidenceItem>>(emptyList())
    val evidence: StateFlow<List<EvidenceItem>> = _evidence.asStateFlow()

    private val _processedDocs = MutableStateFlow<List<ProcessedDoc>>(emptyList())
    val processedDocs: StateFlow<List<ProcessedDoc>> = _processedDocs.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _useMockSource = MutableStateFlow(false)
    val useMockSource: StateFlow<Boolean> = _useMockSource.asStateFlow()

    private val _sessionFaultCode = MutableStateFlow("")
    /** The fault code actually read by the VLM (or mock) this session. */
    val sessionFaultCode: StateFlow<String> = _sessionFaultCode.asStateFlow()

    private val _currentLotoStep = MutableStateFlow(1)
    /** 1..4, derived from state. 1=B201, 2=B205, 3=LOCK_TAG, 4=MCC_OPEN. */
    val currentLotoStep: StateFlow<Int> = _currentLotoStep.asStateFlow()

    private val _faultImage = MutableStateFlow<Bitmap?>(null)
    /** Last uploaded/captured fault-display image shown in the AI session. */
    val faultImage: StateFlow<Bitmap?> = _faultImage.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    /** True while the push-to-talk recognizer is actively listening. */
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _canStartLoto = MutableStateFlow(false)
    /** Gates the "Start Guided LOTO" button in the AI session. */
    val canStartLoto: StateFlow<Boolean> = _canStartLoto.asStateFlow()

    // ---- VLM seam state -----------------------------------------------------
    private val _selectedModel = MutableStateFlow(VlmModelId.DEFAULT)
    /** Which VLM model is used when the active source is the [VlmDetectionSource]. */
    val selectedModel: StateFlow<VlmModelId> = _selectedModel.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    /** Mirrors the active source's last inference latency (0 in mock). */
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _lastVlmAnswer = MutableStateFlow("")
    /** Mirrors the active source's last raw VLM answer ("" in mock). */
    val lastVlmAnswer: StateFlow<String> = _lastVlmAnswer.asStateFlow()

    private val _modelReady = MutableStateFlow(false)
    /** Mirrors the active source readiness (false until the VLM loads). */
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    private val _modelState = MutableStateFlow(VlmModelState.NOT_LOADED)
    /** Mirrors the active source's model lifecycle (NOT_LOADED until VLM loads). */
    val modelState: StateFlow<VlmModelState> = _modelState.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    /** True while an [onCapture] inference is running. */
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _captureFeedback = MutableStateFlow("")
    /** "" normally; a reposition hint after a negative capture. */
    val captureFeedback: StateFlow<String> = _captureFeedback.asStateFlow()

    private val _lastCaptureVerified = MutableStateFlow<Boolean?>(null)
    /** null = no result yet, true = positive verdict, false = negative verdict. */
    val lastCaptureVerified: StateFlow<Boolean?> = _lastCaptureVerified.asStateFlow()

    private val _lastResult = MutableStateFlow<VlmResult?>(null)
    /** Last VLM/mock read (source, runtime, confidence, type, code) for the UI card. */
    val lastResult: StateFlow<VlmResult?> = _lastResult.asStateFlow()

    private val _blockInfo = MutableStateFlow<BlockInfo?>(null)
    /** Non-null when the last capture was blocked (mismatch / unverified). */
    val blockInfo: StateFlow<BlockInfo?> = _blockInfo.asStateFlow()

    // ---- Session-actual permit data (set when reads succeed) ----------------
    private var sessionConfidence: Float = 0f
    private var sessionRuntimeMs: Long = 0L
    private val blockEvents = mutableListOf<String>()

    // ---- Speak events -------------------------------------------------------
    private val _speakEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val speakEvents: SharedFlow<String> = _speakEvents.asSharedFlow()

    // ---- Demo document asset ------------------------------------------------
    private val DEMO_DOC_ASSET = "docs/PowerFlex_753_VFD_Manual.pdf"

    // ---- Pending capture (positive-detection gating) ------------------------
    private var pendingBitmap: Bitmap? = null
    private var pendingType: DetectionType? = null

    init {
        observeActiveSource()
    }

    // ---- Session lifecycle --------------------------------------------------
    fun startSession() {
        if (_state.value == AppState.IDLE) {
            _state.value = AppState.SESSION_STARTED
        }
    }

    fun addDocument(name: String, uri: Uri?) {
        _processedDocs.value = _processedDocs.value + ProcessedDoc(name, DocStatus.PROCESSING, null)
        viewModelScope.launch {
            val summary = withContext(Dispatchers.Default) {
                try {
                    val text = if (uri != null) PdfTextExtractor.extractFromUri(getApplication(), uri)
                               else PdfTextExtractor.extractFromAsset(getApplication(), DEMO_DOC_ASSET)
                    DocSummarizer.summarize(text)
                } catch (t: Throwable) { "Processed on-device." }
            }
            _processedDocs.value = _processedDocs.value.map { doc ->
                if (doc.name == name && doc.status == DocStatus.PROCESSING)
                    doc.copy(status = DocStatus.PROCESSED, summary = summary)
                else doc
            }
            if (_processedDocs.value.any { it.status == DocStatus.PROCESSED } &&
                _state.value == AppState.SESSION_STARTED) {
                _state.value = AppState.DOCS_PROCESSED
            }
        }
    }

    fun useDemoDocument() = addDocument(DemoData.manual.fileName, null)

    fun proceedToLiveSession() {
        if (_state.value == AppState.DOCS_PROCESSED) {
            _state.value = AppState.LIVE_SESSION
            val greeting = SessionAssistant.reply(Intent.GREETING)
            appendConversation("AI: $greeting"); emitSpeak(greeting)
        }
    }

    // ---- AI session entry points -------------------------------------------
    fun setListening(listening: Boolean) { _isListening.value = listening }

    fun onUserUtterance(text: String) {
        if (text.isBlank()) return
        appendConversation("YOU: $text")
        val intent = SessionAssistant.classify(text)
        val reply = SessionAssistant.reply(intent)
        appendConversation("AI: $reply"); emitSpeak(reply)
        if (intent == Intent.LOTO_HELP) _canStartLoto.value = true
    }

    fun onFaultImage(bitmap: Bitmap?) {
        if (bitmap == null) return
        _faultImage.value = bitmap
        _captureFeedback.value = ""
        _blockInfo.value = null
        _lastCaptureVerified.value = null
        _isAnalyzing.value = true
        viewModelScope.launch {
            activeSource.onCapture(DetectionType.FAULT_CODE, bitmap)
            _isAnalyzing.value = false
        }
        // canStartLoto is gated in handleDetection only on a confident code read.
    }

    /** Map the current LOTO state to the detection type expected for it (or null). */
    private fun expectedTypeFor(state: AppState): DetectionType? = when (state) {
        AppState.LOTO_B201 -> DetectionType.BREAKER_B201_OFF
        AppState.LOTO_B205 -> DetectionType.BREAKER_B205_OFF
        AppState.LOTO_LOCK_TAG -> DetectionType.LOCK_TAG
        AppState.LOTO_MCC_OPEN -> DetectionType.MCC_OPEN
        else -> null
    }

    /** Expected breaker identity for the current state (null for non-breaker steps). */
    private fun expectedIdentityFor(state: AppState): String? = when (state) {
        AppState.LOTO_B201 -> "B-201"
        AppState.LOTO_B205 -> "B-205"
        else -> null
    }

    /** Human-readable description of what the current step expects. */
    private fun humanExpected(state: AppState): String = when (state) {
        AppState.LOTO_B201 -> "Breaker B-201 OFF"
        AppState.LOTO_B205 -> "Breaker B-205 OFF"
        AppState.LOTO_LOCK_TAG -> "Lock + danger tag applied"
        AppState.LOTO_MCC_OPEN -> "MCC cabinet open"
        else -> ""
    }

    fun startGuidedLockout() {
        if (_state.value == AppState.LIVE_SESSION) {
            _state.value = AppState.LOTO_B201
            _currentLotoStep.value = 1
        }
    }

    // ---- Evidence capture / detection wiring --------------------------------
    /**
     * Capture a frame for the CURRENT loto step and run inference through the
     * ACTIVE source. Evidence is stored ONLY when the matching POSITIVE
     * detection arrives (see [handleDetection]); a negative result leaves the
     * step in place and surfaces a reposition hint.
     *
     * Mock mode emits the matching detection immediately, so it always advances.
     * VLM mode advances only on a real positive read.
     */
    fun captureEvidence(bitmap: Bitmap) {
        val type = expectedTypeFor(_state.value) ?: return

        _captureFeedback.value = ""
        _blockInfo.value = null
        _lastCaptureVerified.value = null
        _isAnalyzing.value = true
        pendingBitmap = bitmap
        pendingType = type

        // The verdict + advance happen in handleDetection, gated on the real
        // detection confidence and label — never inferred from a state change.
        viewModelScope.launch {
            activeSource.onCapture(type, bitmap)
            _isAnalyzing.value = false
        }
    }

    private fun observeActiveSource() {
        // (Re)subscribe to the active source's detection stream.
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            activeSource.start()
            activeSource.detections.collect { detection ->
                handleDetection(detection)
            }
        }
        // (Re)mirror the active source's status flows into our backing state.
        mirrorJob?.cancel()
        mirrorJob = viewModelScope.launch {
            launch { activeSource.latencyMs.collect { _latencyMs.value = it } }
            launch { activeSource.lastAnswer.collect { _lastVlmAnswer.value = it } }
            launch { activeSource.modelReady.collect { _modelReady.value = it } }
            launch { activeSource.modelState.collect { _modelState.value = it } }
        }
    }

    private fun handleDetection(detection: Detection) {
        // Surface the raw read for the UI evidence card on EVERY inference.
        _lastResult.value = VlmResult(
            source = if (_useMockSource.value) "Mock" else "Local VLM",
            runtimeMs = _latencyMs.value,
            confidence = detection.confidence,
            type = detection.type,
            code = detection.code ?: detection.identity
        )

        when (detection.type) {
            DetectionType.FAULT_CODE -> {
                // Live-session fault read. Accept ONLY a confident, non-blank code.
                if (detection.confidence >= CONFIDENCE_THRESHOLD &&
                    !detection.code.isNullOrBlank()
                ) {
                    _sessionFaultCode.value = detection.code!!
                    sessionConfidence = detection.confidence
                    sessionRuntimeMs = _latencyMs.value
                    val line = SessionAssistant.faultReply(detection.code!!)
                    appendConversation("AI: $line")
                    emitSpeak(line)
                    _canStartLoto.value = true
                    _blockInfo.value = null
                } else {
                    val msg = "No fault code detected. Try again or enter code manually."
                    appendConversation("AI: $msg")
                    emitSpeak(msg)
                    _blockInfo.value = BlockInfo(
                        reason = "No fault code detected.",
                        expected = "A readable VFD fault code",
                        detected = "Not verified"
                    )
                    _captureFeedback.value = "No fault code detected."
                    // Do NOT enable canStartLoto on a failed / low-confidence read.
                }
            }

            DetectionType.BREAKER_B201_OFF,
            DetectionType.BREAKER_B205_OFF,
            DetectionType.LOCK_TAG,
            DetectionType.MCC_OPEN -> {
                val state = _state.value
                val expected = expectedTypeFor(state)

                // 1) Wrong type for this step: treat as no-match (ignore).
                if (detection.type != expected) {
                    return
                }

                // NOTE: exact B-201 vs B-205 sub-identity matching is intentionally
                // NOT gated here — SmolVLM-500M cannot reliably distinguish them.
                // The step is gated on a confident reading of the right evidence
                // TYPE; a non-matching / empty read still fails the confidence gate.

                // 2) Low / unverified confidence: BLOCK.
                if (detection.confidence < CONFIDENCE_THRESHOLD) {
                    _blockInfo.value = BlockInfo(
                        reason = "Evidence not verified.",
                        expected = humanExpected(state),
                        detected = "Not verified"
                    )
                    _captureFeedback.value = "Evidence not verified."
                    _lastCaptureVerified.value = false
                    blockEvents.add(
                        "Unverified: ${humanExpected(state)} confidence ${detection.confidence} @${detection.timestampMs}"
                    )
                    pendingBitmap = null
                    pendingType = null
                    return
                }

                // 4) VERIFIED: store evidence + advance the LOTO state machine.
                _blockInfo.value = null
                _lastCaptureVerified.value = true
                when (detection.type) {
                    DetectionType.BREAKER_B201_OFF -> advanceLoto(
                        to = AppState.LOTO_B205, step = 2, stepName = "Breaker B-201 OFF"
                    )
                    DetectionType.BREAKER_B205_OFF -> advanceLoto(
                        to = AppState.LOTO_LOCK_TAG, step = 3, stepName = "Breaker B-205 OFF"
                    )
                    DetectionType.LOCK_TAG -> advanceLoto(
                        to = AppState.LOTO_MCC_OPEN, step = 4,
                        stepName = "Lock & Danger Tag Applied"
                    )
                    DetectionType.MCC_OPEN -> {
                        storePendingEvidence("MCC Cabinet Open")
                        _state.value = AppState.PERMIT_READY
                        emitSpeak("LOTO evidence captured. Proceed to zero-energy verification.")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Advance one LOTO step on a VERIFIED detection, storing the pending captured
     * bitmap as evidence for [stepName]. Caller has already confirmed the verdict.
     */
    private fun advanceLoto(to: AppState, step: Int, stepName: String) {
        storePendingEvidence(stepName)
        _state.value = to
        _currentLotoStep.value = step
    }

    private fun storePendingEvidence(stepName: String) {
        val bmp = pendingBitmap ?: return
        _evidence.value = _evidence.value + EvidenceItem(
            stepName = stepName,
            timestampMs = System.currentTimeMillis(),
            bitmap = bmp
        )
        pendingBitmap = null
        pendingType = null
    }

    // ---- Toggles ------------------------------------------------------------
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    /** Swap the active source between [MockDetectionSource] and [VlmDetectionSource]. */
    fun toggleDetectionSource() {
        val goingToVlm = _useMockSource.value
        swapSource(
            newSource = if (goingToVlm) {
                VlmDetectionSource(getApplication(), _selectedModel.value)
            } else {
                MockDetectionSource()
            }
        )
        _useMockSource.value = !goingToVlm
    }

    /**
     * Pick the VLM model. If the VLM source is currently active, rebuild it with
     * the new model so the change takes effect immediately.
     */
    fun selectModel(id: VlmModelId) {
        _selectedModel.value = id
        if (!_useMockSource.value) {
            swapSource(VlmDetectionSource(getApplication(), id))
        }
    }

    private fun swapSource(newSource: CaptureDetectionSource) {
        activeSource.stop()
        activeSource = newSource
        // Reset mirrored status to a neutral baseline; the new source's collectors
        // will immediately re-populate these from the source's real state.
        _latencyMs.value = 0L
        _lastVlmAnswer.value = ""
        _modelReady.value = false
        _modelState.value = VlmModelState.NOT_LOADED
        observeActiveSource()
    }

    // ---- Reset --------------------------------------------------------------
    fun reset() {
        _state.value = AppState.IDLE
        _conversation.value = emptyList()
        _evidence.value = emptyList()
        _processedDocs.value = emptyList()
        _currentLotoStep.value = 1
        _captureFeedback.value = ""
        _lastCaptureVerified.value = null
        _isAnalyzing.value = false
        _latencyMs.value = 0L
        _lastVlmAnswer.value = ""
        _faultImage.value = null
        _canStartLoto.value = false
        _isListening.value = false
        _sessionFaultCode.value = ""
        _blockInfo.value = null
        _lastResult.value = null
        sessionConfidence = 0f
        sessionRuntimeMs = 0L
        blockEvents.clear()
        pendingBitmap = null
        pendingType = null

        // Restore the safe default: the REAL VLM source + default model.
        _selectedModel.value = VlmModelId.DEFAULT
        _useMockSource.value = false
        swapSource(VlmDetectionSource(getApplication(), VlmModelId.DEFAULT))
    }

    // ---- Permit -------------------------------------------------------------
    fun generatePermit(context: Context): File {
        return PermitPdfGenerator.generate(
            context,
            PermitInput(
                faultCode = _sessionFaultCode.value.ifBlank { "—" },
                confidence = sessionConfidence,
                runtimeMs = sessionRuntimeMs,
                faultType = DemoData.manual.faultType,
                isolationPoints = DemoData.asset.isolationPoints.joinToString(", "),
                assetName = DemoData.asset.name,
                vfdId = DemoData.asset.vfdId,
                location = DemoData.asset.location,
                evidence = _evidence.value,
                blockEvents = blockEvents.toList()
            )
        )
    }

    // ---- Helpers ------------------------------------------------------------
    private fun appendConversation(line: String) {
        _conversation.value = (_conversation.value + line).takeLast(10)
    }

    private fun emitSpeak(text: String) {
        _speakEvents.tryEmit(text)
    }

    override fun onCleared() {
        super.onCleared()
        detectionJob?.cancel()
        mirrorJob?.cancel()
        activeSource.stop()
    }
}
