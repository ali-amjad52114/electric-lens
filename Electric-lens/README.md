# Electric Lens

**Electric Lens** is an on-device, **VLM-guided Lockout/Tagout (LOTO) assistant**
for industrial / mining electricians. It walks an electrician through isolating
equipment, uses a vision-language model to read the scene at each step, captures
photographic evidence, and produces a shareable LOTO permit — **entirely on the
phone**.

This is the **Qualcomm × Meta ExecuTorch Hackathon integration build**, targeting
the **Snapdragon Galaxy S25 Ultra (SM8750)** with on-device VLM inference via
**ExecuTorch + Qualcomm QNN on the Hexagon NPU**.

> **Team:** _<add team name / members here>_

Electric Lens is **fully offline**. The app declares **no `INTERNET` permission**
(see `app/src/main/AndroidManifest.xml`); the AI conversation, the
text-to-speech, the detection/VLM layer, and PDF generation all run locally.
Nothing leaves the handset — it runs end-to-end in **airplane mode**.

The UI is a **dark industrial field-tool** design built around large,
high-contrast, **glove-friendly touch targets** so it stays usable in a noisy,
gloved, low-light underground environment.

---

## How it works — 5-screen flow

Navigation is driven by a **state machine**, not a `NavController`: the `AppState`
enum in `vm/SessionViewModel.kt` is the single source of truth, and
`ui/AppNavHost.kt` maps the current state to a screen.

1. **Start** (`StartScreen`)
   App identity on the dark field-tool background with a single large
   **Start Session** button.

2. **Add Documents** (`AddDocumentsScreen`)
   Import an equipment manual via the **SAF PDF picker**, or load the bundled
   **demo document** (`PowerFlex_753_VFD_Manual.pdf`). The doc appears as
   `PROCESSING`, then flips to `PROCESSED` with an on-device summary.

3. **Live Session** (`LiveSessionScreen`)
   A **CameraX** preview behind a scripted AI conversation. A real-time
   **`NPU: X ms` latency readout** (top overlay) shows on-device inference time,
   and each AI line is **spoken aloud** via Android's offline TextToSpeech (with a
   mute toggle). The electrician taps through the script
   (`I see a fault code` → `Detect fault` → `Match to manual` → `Start Guided
   LOTO`); the fault-code step runs an actual capture/inference through the active
   detection source.

4. **Guided LOTO** (`GuidedLockoutScreen`)
   Four **detection-gated** capture steps:
   1. Breaker **B-201 OFF**
   2. Breaker **B-205 OFF**
   3. **Lock & Danger Tag applied**
   4. **MCC cabinet open**

   At each step the electrician captures a CameraX frame; it is the resulting
   **`Detection` event** — not the button — that advances the state machine. Mock
   taps and the real VLM drive the **exact same** transitions, and the captured
   frame is stored as timestamped evidence.

5. **Permit** (`PermitScreen`)
   Electric Lens generates an on-device LOTO permit PDF
   (`pdf/PermitPdfGenerator.kt`, via `android.graphics.pdf.PdfDocument`),
   embedding the captured evidence. From here you can **Share** (via a
   `FileProvider`), **Open**, or **Reset** the flow.

---

## Mock vs VLM — toggle & model selector

The detection layer is selected at runtime through the
`CaptureDetectionSource` interface; the `SessionViewModel` depends **only** on
that interface.

- **Mock ↔ VLM toggle** — `toggleDetectionSource()` swaps the active source
  between `MockDetectionSource` (**default**) and `VlmDetectionSource`. Current
  selection is observable via `useMockSource` (`true` = Mock).
- **Model selector** — `selectModel(...)` picks **SmolVLM 500M** (**default**) or
  **InternVL3 1B** for the VLM source.

**Defaults: Mock + SmolVLM.** With no model files / libraries present, the app
**stays in Mock and never crashes** — the VLM engine reports `ready = false` and
the source falls back gracefully (and selecting a model whose files are absent,
like InternVL3, does the same). See
**[MODEL_INTEGRATION.md](MODEL_INTEGRATION.md)** to enable the real NPU VLM.

---

## Tech stack

| Area          | Choice                                                                        |
| ------------- | ----------------------------------------------------------------------------- |
| Language      | Kotlin                                                                         |
| UI            | Jetpack Compose, Material 3 (`material3`, `material-icons-extended`)           |
| Architecture  | Single-activity (`MainActivity`), MVVM, state-machine navigation              |
| State         | `SessionViewModel` (`StateFlow` / `SharedFlow`), no `NavController`           |
| Camera        | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)  |
| Speech        | `android.speech.tts.TextToSpeech` (offline)                                   |
| PDF           | `android.graphics.pdf.PdfDocument`                                            |
| On-device ML  | ExecuTorch + Qualcomm QNN (Hexagon NPU) — VLM seam wired; see MODEL_INTEGRATION.md |
| Concurrency   | Kotlin Coroutines                                                             |
| Networking    | **None.** No `INTERNET` permission — fully offline.                           |

**Versions** (from the Gradle files):

- Android Gradle Plugin (AGP): **8.5.2**
- Kotlin: **1.9.24**
- Gradle wrapper: **8.9** (build tooling **Gradle 8.7+**)
- Compose BOM: **2024.08.00** (Compose compiler extension **1.5.14**)
- CameraX: **1.3.4**
- `compileSdk` / `targetSdk`: **34** (pinned — AGP 8.5.2 + installed SDK; the app
  still runs on Android 16) · **`minSdk` 30**
- ABI: **arm64-v8a** only
- JVM / Java target: **17**
- Application ID / namespace: `com.electriclens`

---

## Setup, build & run

1. Open the project in **Android Studio**. On the first Gradle sync, Android
   Studio **regenerates the Gradle wrapper JAR** and creates **`local.properties`**
   with your `sdk.dir` (neither is checked in).
2. Connect a **physical device** (Snapdragon S25 Ultra for the real NPU path) or
   an emulator running **API 30+**.
3. Grant the **Camera** permission when prompted — required for the live preview
   and evidence capture.

```bash
./gradlew installDebug
```

Or press **Run** in Android Studio.

**Out of the box this runs in Mock mode** — no model files or native libraries are
required to demo the full UX end-to-end. To enable real on-device NPU VLM
inference (drop the model, the ExecuTorch AAR, and the QNN libraries; or re-export
as a combined `.pte`), follow **[MODEL_INTEGRATION.md](MODEL_INTEGRATION.md)**.

---

## Judging alignment

- **On-device & offline (privacy).** No `INTERNET` permission; the entire flow —
  conversation, VLM detection, TTS, PDF — runs locally and works in **airplane
  mode**. No site imagery or documents leave the device.
- **Real NPU latency surfaced.** The Live Session screen shows a live
  **`NPU: X ms`** readout measured as wall-clock around each single-frame
  inference, so on-device performance is visible, not hidden.
- **End-to-end demoable.** The state machine, evidence capture, and permit
  generation are fully exercised in Mock mode today, and the VLM seam is wired so
  real inference drops into the **same** `Detection` path with no UI changes.

---

## Safety note

Electric Lens **documents visible lockout evidence only**. It **does NOT certify a
zero-energy state**, and it **does not replace required testing or the
site-approved LOTO procedure**. The captured evidence and generated permit are an
aid to the electrician's own approved process — not a substitute for it.

The app uses **strict, approved wording**. It never states or implies that
equipment is de-energized or safe to work on — for example it says *"LOTO evidence
captured. Proceed to zero-energy verification."* rather than declaring the work
safe.

**Never** write or display "equipment is safe" or "AI guarantees safety". Always
follow your site's approved LOTO procedure and perform required zero-energy
verification before contact.

---

## License

MIT — see [LICENSE](LICENSE).
