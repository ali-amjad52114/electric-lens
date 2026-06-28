# Plan — Electric Lens "Final User Flow" refactor (detailed, file-by-file)

## Context
Electric Lens (`Electric-lens/`, Kotlin + Compose, MVVM, state-machine nav via `AppState`)
is today a polished 5-screen demo where: document "processing" is faked, the AI session is a
fixed 4-line script, all input is camera-only, and there is no speech input. Goal: keep the
5 screens + the Mock/VLM seam + fully-offline (no `INTERNET`), but make 4 things real:
(2) real PDF processing, (3) dynamic AI session with voice-in + image upload, (4) image-first
LOTO with green/red verdicts, (5) a "Fault type" line on the permit.

Honest caveat: real *model-generated* answers depend on the unfinished `QnnSplitVlmEngine`
runner. Everything below works **fully in Mock mode today**; the VLM path lights up later with
no UI changes. In Mock mode the "dynamic AI" is a **rule-based intent router**, not an LLM.

Line numbers below refer to the files as they currently exist.

---

# PAGE 2 — Real on-device PDF processing

### 2.1 `app/build.gradle.kts` — add offline PDF lib
After the Coroutines line (currently line 97), add inside `dependencies { }`:
```kotlin
// On-device PDF text extraction (offline, no network).
implementation("com.tom-roush:pdfbox-android:2.0.27.0")
```

### 2.2 NEW `app/src/main/java/com/electriclens/pdf/PdfTextExtractor.kt`
```kotlin
package com.electriclens.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Offline PDF → text. PDFBox-Android, no network. Caller runs this off-thread. */
object PdfTextExtractor {
    @Volatile private var initialized = false

    fun extractFromUri(context: Context, uri: Uri): String {
        ensureInit(context)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ""
            PDDocument.load(input).use { doc -> return PDFTextStripper().getText(doc) }
        }
    }

    fun extractFromAsset(context: Context, assetPath: String): String {
        ensureInit(context)
        context.assets.open(assetPath).use { input ->
            PDDocument.load(input).use { doc -> return PDFTextStripper().getText(doc) }
        }
    }

    private fun ensureInit(context: Context) {
        if (!initialized) { PDFBoxResourceLoader.init(context.applicationContext); initialized = true }
    }
}
```

### 2.3 NEW `app/src/main/java/com/electriclens/data/DocSummarizer.kt`
Reuses the fault-code regex idea from `VlmModelAdapter.kt:88`.
```kotlin
package com.electriclens.data

object DocSummarizer {
    private val FAULT_CODE_REGEX = Regex("""([A-Za-z]\d{2,4}(\s?[A-Za-z0-9]{2,4})?)""")
    private val PROCEDURE_REGEX = Regex("""(?im)^\s*(procedure|step|section|chapter)\b""")

    fun summarize(text: String): String {
        if (text.isBlank()) return "Processed on-device — no readable text found."
        val faults = FAULT_CODE_REGEX.findAll(text).map { it.value.trim().uppercase() }.toSet().size
        val procs = PROCEDURE_REGEX.findAll(text).count()
        return "$faults fault codes, $procs procedures found"
    }
}
```

### 2.4 `vm/SessionViewModel.kt` — make `addDocument` real
- Add imports near top: `android.net.Uri`, `kotlinx.coroutines.Dispatchers`,
  `kotlinx.coroutines.withContext`, `com.electriclens.pdf.PdfTextExtractor`,
  `com.electriclens.data.DocSummarizer`.
- Add a constant in the class: `private val DEMO_DOC_ASSET = "docs/PowerFlex_753_VFD_Manual.pdf"`.
- **Replace** `addDocument(name)` (lines 182-201) with:
```kotlin
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
```
- **Replace** `useDemoDocument()` (lines 203-205) with:
```kotlin
fun useDemoDocument() = addDocument(DemoData.manual.fileName, null)
```
- The `delay` import (line 17) is now unused → remove it.

### 2.5 `ui/screens/AddDocumentsScreen.kt` — forward the Uri
Line 71: change `vm.addDocument(name)` → `vm.addDocument(name, uri)`.

### 2.6 (optional) bundle the demo PDF
Drop `PowerFlex_753_VFD_Manual.pdf` into `app/src/main/assets/docs/`. If absent, the
`catch` in 2.4 falls back gracefully — demo still works.

---

# PAGE 3 — Dynamic AI Session (voice in + image upload)

### 3.1 `app/src/main/AndroidManifest.xml` — mic permission
After line 5 (`CAMERA`) add:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
Also (Android 11+ visibility for the recognizer) add inside `<manifest>` before `<application>`:
```xml
<queries>
    <intent><action android:name="android.speech.RecognitionService" /></intent>
</queries>
```
Keep: no `INTERNET` (line 10 comment stays true; on-device speech uses `EXTRA_PREFER_OFFLINE`).

### 3.2 NEW `util/SpeechManager.kt` (mirrors `TtsManager.kt`)
```kotlin
package com.electriclens.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Push-to-talk speech→text. Offline (EXTRA_PREFER_OFFLINE). Main-thread only. */
class SpeechManager(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    var onResult: ((String) -> Unit)? = null
    var onListening: ((Boolean) -> Unit)? = null
    val isAvailable get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (!isAvailable) return
        destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { onListening?.invoke(true) }
                override fun onEndOfSpeech() { onListening?.invoke(false) }
                override fun onError(e: Int) { onListening?.invoke(false) }
                override fun onResults(r: Bundle?) {
                    onListening?.invoke(false)
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult?.invoke(text)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
    }

    fun stop() { recognizer?.stopListening() }
    fun destroy() { recognizer?.destroy(); recognizer = null }
}
```

### 3.3 NEW `ai/SessionAssistant.kt` (rule-based intent router)
```kotlin
package com.electriclens.ai

import com.electriclens.data.DemoData

enum class Intent { GREETING, TROUBLESHOOTING, FAULT_HELP, MANUAL_HELP, LOTO_HELP, UNKNOWN }

object SessionAssistant {
    fun classify(text: String): Intent {
        val t = text.lowercase()
        return when {
            t.contains("lockout") || t.contains("loto") || t.contains("isolate") -> Intent.LOTO_HELP
            t.contains("manual") || t.contains("document")                        -> Intent.MANUAL_HELP
            t.contains("fault") || t.contains("code") || t.contains("error")      -> Intent.FAULT_HELP
            t.contains("trouble") || t.contains("problem") || t.contains("help")  -> Intent.TROUBLESHOOTING
            else -> Intent.UNKNOWN
        }
    }

    fun reply(intent: Intent): String = when (intent) {
        Intent.GREETING        -> "Electric Lens ready. Need help with a fault code, the manual, or guided lockout?"
        Intent.TROUBLESHOOTING -> "Describe the symptom, or upload a photo of the VFD fault display."
        Intent.FAULT_HELP      -> "Upload a photo of the VFD fault display and I'll read the code."
        Intent.MANUAL_HELP     -> "The manual is processed on-device. Ask about a fault code or a procedure."
        Intent.LOTO_HELP       -> "Isolate ${iso()} before cabinet access. Tap Start Guided LOTO when ready."
        Intent.UNKNOWN         -> "I can help with fault codes, the manual, or guided lockout. Which one?"
    }

    fun faultReply(code: String) =
        "Fault $code — ${DemoData.manual.meaning}. Isolate ${iso()} before cabinet access. Tap Start Guided LOTO."

    private fun iso() = DemoData.asset.isolationPoints.joinToString(" and ")
}
```

### 3.4 `vm/SessionViewModel.kt` — replace the script with conversation
**Remove:** `_nextScriptLabel`/`nextScriptLabel` (lines 96-98), `ScriptStep` (130-136),
`script` (138-162), `initialLiveLine`/`scriptIndex` (164-165), `advanceScript()` (225-244),
`onLiveAction()` (252-273).

**Add state flows** (near the other flows):
```kotlin
private val _faultImage = MutableStateFlow<Bitmap?>(null)
val faultImage: StateFlow<Bitmap?> = _faultImage.asStateFlow()

private val _isListening = MutableStateFlow(false)
val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

private val _canStartLoto = MutableStateFlow(false)
val canStartLoto: StateFlow<Boolean> = _canStartLoto.asStateFlow()
```

**Replace** `proceedToLiveSession()` (lines 207-215) with:
```kotlin
fun proceedToLiveSession() {
    if (_state.value == AppState.DOCS_PROCESSED) {
        _state.value = AppState.LIVE_SESSION
        val greeting = SessionAssistant.reply(Intent.GREETING)
        appendConversation("AI: $greeting"); emitSpeak(greeting)
    }
}
```

**Add** new entry points:
```kotlin
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
    _captureFeedback.value = ""; _isAnalyzing.value = true
    viewModelScope.launch {
        activeSource.onCapture(DetectionType.FAULT_CODE, bitmap)
        _isAnalyzing.value = false
    }
}
```

**Edit** `handleDetection` FAULT_CODE branch (lines 365-376) to:
```kotlin
DetectionType.FAULT_CODE -> {
    val code = if (_useMockSource.value) DemoData.manual.faultCode
               else parseFaultCode(_lastVlmAnswer.value)
    val line = SessionAssistant.faultReply(code)
    appendConversation("AI: $line"); emitSpeak(line)
    _canStartLoto.value = true
}
```

**Edit** `reset()` (lines 453-476): remove `_nextScriptLabel`/`scriptIndex` resets; add
`_faultImage.value = null`, `_canStartLoto.value = false`, `_isListening.value = false`.

Add imports: `com.electriclens.ai.SessionAssistant`, `com.electriclens.ai.Intent`.
(`startGuidedLockout()` lines 275-280 stays — already fires from LIVE_SESSION.)

### 3.5 NEW `ui/screens/AiSessionScreen.kt` (replaces `LiveSessionScreen.kt`)
Rework of the old screen. Keeps the top status row (NPU readout + MOCK/VLM tag + mute) and
`ConversationPanel`, but swaps the camera background + script button for: a **mic button**, an
**Upload Fault Code Image** button + preview thumbnail, and a **Start Guided LOTO** button
gated on `canStartLoto`.

Signature: `fun AiSessionScreen(vm: SessionViewModel, tts: TtsManager, speech: SpeechManager)`.
Key wiring:
- Collect `conversation`, `isMuted`, `useMock`, `latencyMs`, `modelReady`, `isAnalyzing`,
  `faultImage`, `isListening`, `canStartLoto`.
- `RECORD_AUDIO` permission launcher (same pattern as the CAMERA request in the old
  `LiveSessionScreen.kt:92-103`).
- Mic button onClick: if permission → `speech.onResult = { vm.onUserUtterance(it) }`;
  `speech.onListening = { vm.setListening(it) }`; `speech.start()`. Show a "Listening…" state
  when `isListening`.
- Image upload: `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`,
  launched with `"image/*"`; in callback decode via `ImageLoader.decode(context, uri)` (3.7)
  → `vm.onFaultImage(bitmap)`; show the returned `faultImage` as a preview thumbnail.
- `PrimaryButton("Start Guided LOTO", enabled = canStartLoto) { vm.startGuidedLockout() }`.
- Reuse `NpuReadout` + `SourceIndicator` (copy the two private composables from the old file).
- Delete `ui/screens/LiveSessionScreen.kt`.

### 3.6 `ui/AppNavHost.kt` + `MainActivity.kt` — thread the SpeechManager
- `AppNavHost` signature → `fun AppNavHost(vm, tts, speech: SpeechManager)`.
  Line 38 branch → `AppState.LIVE_SESSION -> AiSessionScreen(vm, tts, speech)`
  (update the import on line 9 from `LiveSessionScreen` to `AiSessionScreen`).
- `MainActivity`: add `private lateinit var speech: SpeechManager`; in `onCreate` after
  `tts = TtsManager(this)` add `speech = SpeechManager(this)`; pass to
  `AppNavHost(vm, tts, speech)` (line 37); in `onDestroy` add `speech.destroy()`.

### 3.7 NEW `util/ImageLoader.kt` (uri → bitmap; minSdk 30)
```kotlin
package com.electriclens.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri

object ImageLoader {
    fun decode(context: Context, uri: Uri): Bitmap? = try {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { d, _, _ -> d.isMutableRequired = false }
            .copy(Bitmap.Config.ARGB_8888, false)
    } catch (t: Throwable) { null }
}
```
(`.copy(ARGB_8888)` because `EvidenceThumbStrip`/PDF draw a software bitmap.)

---

# PAGE 4 — Image-first Guided LOTO with green/red verdicts

### 4.1 `vm/SessionViewModel.kt` — expose a per-capture verdict
**Add:**
```kotlin
private val _lastCaptureVerified = MutableStateFlow<Boolean?>(null)
val lastCaptureVerified: StateFlow<Boolean?> = _lastCaptureVerified.asStateFlow()
```
**Edit** `captureEvidence` (lines 292-318): set `_lastCaptureVerified.value = null` before the
launch; inside, after `activeSource.onCapture(...)`, set `true` when the state advanced
(positive) and `false` on the negative branch (alongside the existing `captureFeedback`).
Reset it to `null` in `reset()`.

### 4.2 `ui/screens/GuidedLockoutScreen.kt` — two-pane + upload + verdict
- Wrap the body in a `Row`: **left/center column** (active `StepCard`, evidence strip,
  verdict, Capture + Upload buttons) and a **right column** (the 4-step overview that is
  currently the inline checklist at lines 165-184 — move it to a persistent right pane,
  ~140dp wide, reusing the existing `lotoSteps` list at lines 59-64 and ✓/▶/• markers).
- **Upload action:** add `rememberLauncherForActivityResult(GetContent())`; launch `"image/*"`;
  callback `ImageLoader.decode(context, uri)?.let { vm.captureEvidence(it) }`. Place an
  "Upload evidence" button next to the existing "Capture evidence" button (line 236).
- **Verdict indicator:** collect `lastCaptureVerified`; show a green ✓ chip when `true`, a red
  ✗ chip + the existing `captureFeedback` (lines 191-202) when `false`, nothing when `null`.
- Keep camera preview, `EvidenceThumbStrip`, and the "Generate Permit PDF" button (line 209).

---

# PAGE 5 — Add "Fault type" to the permit

### 5.1 `data/DemoData.kt` — add the field
- `Manual` data class (lines 7-12): add `val faultType: String`.
- `DemoData.manual` (lines 23-28): add `"Overcurrent"` (or chosen wording) as the new arg.

### 5.2 `ui/screens/PermitScreen.kt` — show it
- `SummaryCard` call (lines 100-108): pass `faultType = manual.faultType`.
- `SummaryCard` signature (lines 190-198): add `faultType: String` param.
- After the `DetailRow("Fault", faultLine)` (line 216) add
  `DetailRow("Fault type", faultType)`.

### 5.3 `pdf/PermitPdfGenerator.kt` — add it to the PDF
In `metaRows` (lines 118-126), after the `"Fault" to ...` entry add:
`"Fault type" to manual.faultType`.

---

# Files summary
**New (5):** `pdf/PdfTextExtractor.kt`, `data/DocSummarizer.kt`, `util/SpeechManager.kt`,
`ai/SessionAssistant.kt`, `util/ImageLoader.kt`, `ui/screens/AiSessionScreen.kt`.
**Edited (9):** `app/build.gradle.kts`, `AndroidManifest.xml`, `vm/SessionViewModel.kt`,
`ui/AppNavHost.kt`, `MainActivity.kt`, `ui/screens/AddDocumentsScreen.kt`,
`ui/screens/GuidedLockoutScreen.kt`, `ui/screens/PermitScreen.kt`, `pdf/PermitPdfGenerator.kt`,
`data/DemoData.kt`.
**Deleted (1):** `ui/screens/LiveSessionScreen.kt`.
**Reused unchanged:** `CaptureDetectionSource`/`MockDetectionSource`/`VlmDetectionSource`,
`VlmModelAdapter`, `TtsManager`, `PermitPdfGenerator` core, `EvidenceThumbStrip`/`StepCard`/
`PrimaryButton`/`ConversationPanel`/`CameraPreview`.

# Out of scope
Finishing `QnnSplitVlmEngine` (NPU bringup, `npu-bringup/`); editable demo data; repo
decluttering.

# Verification
1. `cd Electric-lens && ./gradlew assembleDebug` — must stay green (arm64-v8a only).
2. Confirm `AndroidManifest.xml` still has **no `INTERNET`**; run in airplane mode.
3. Mock walkthrough (API 30+ device/emulator):
   - P2: pick a real PDF → summary shows actual extracted counts (not the old constant).
   - P3: tap mic, speak → text appears as `YOU:`; AI replies + speaks. Upload a fault image →
     AI states the code + isolation points → "Start Guided LOTO" enables.
   - P4: right pane shows all 4 steps; Capture **or** Upload each step → green ✓, step advances,
     thumbnail recorded; (VLM mode negative → red ✗ + recapture).
   - P5: summary + PDF show **Fault type**; Open/Share produce a real PDF with evidence + times.
4. VLM smoke: toggle VLM with no model files → stays usable, "model not loaded", no crash.
