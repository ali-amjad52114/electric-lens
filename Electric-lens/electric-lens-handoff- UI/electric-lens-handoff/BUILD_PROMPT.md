# Electric Lens — Android UI/UX Build Prompt (Kotlin · Jetpack Compose · Material 3)

> Paste this into Claude **inside the Electric Lens repo**. It combines (A) the exact visual design system to reproduce, and (B) your existing stack, data, architecture, and safety rules. **The visual system in §2–§5 is the authoritative look. The constraints in §1 and §7 must never be violated.** Build the UI layer only; keep the existing architecture and data wiring.

---

## 1. Hard constraints (preserve from the existing repo — do NOT break)

- Kotlin 1.9.24 · Jetpack Compose · Material 3 · Compose BOM 2024.08.00 · material-icons-extended · existing `ElectricLensTheme`.
- **MVVM, single `MainActivity`/`ComponentActivity`.** State lives in `SessionViewModel` exposed via `StateFlow`/`SharedFlow`, collected with `collectAsStateWithLifecycle()`.
- **Navigation stays state-machine driven:** `AppState` enum in `SessionViewModel`, `AppNavHost.kt` uses a `when` block. **Do NOT add NavController. No XML. No WebView. No cloud calls. No INTERNET permission.**
- Portrait-locked. minSdk 30, targetSdk 34, arm64-v8a.
- Camera = CameraX via `AndroidView` + `PreviewView`. TTS = `TtsManager`. Voice in = `SpeechManager` if present (else a clean hook, don't fake transcription). PDF = `android.graphics.pdf.PdfDocument` + Canvas/Paint (no heavy lib).
- **Keep Mock mode and VLM mode.** Mock = deterministic demo data. VLM = real on-device path; **never fake VLM success when the pipeline fails** — surface a calm field message.
- Before coding, inspect: `app/build.gradle.kts`, `MainActivity`, `SessionViewModel`, `AppState`, `AppNavHost.kt`, existing screen composables, `ui/theme/{Color,Theme,Type}.kt`, detection-source classes, CameraX utils, PDF code, doc-summarizer, `REFACTOR_PLAN.md`, `UI/UX/` if present. Adapt existing state models before adding new ones.

**Safety language (mandatory).** Green/Verified means *evidence captured*, **not** that equipment is safe.
- ❌ Never: "Equipment is safe", "Verified safe", "AI guarantees safety".
- ✅ Use: "Evidence captured" · "Visible evidence verified" · "Proceed to zero-energy verification" · "Not detected — reposition and recapture" · final: **"LOTO evidence captured. Proceed to zero-energy verification."**

**Demo data (use verbatim):** Fault `F071 OC1` · Meaning `Overcurrent Phase B` · Type `Overcurrent` · Asset `Conveyor CV-104` · Drive `VFD-104` · Location `MCC-2 | Bucket 17` · Isolation `B-201`, `B-205` · Manual `PowerFlex_753_VFD_Manual.pdf`.

---

## 2. Visual design system — tokens

Make it feel like a **serious industrial field instrument** (precision meter meets aircraft lockout checklist): dark, high-contrast, glove-friendly, minimal text, strong hierarchy.

### 2.1 Color — add to `ui/theme/Color.kt`
```kotlin
val ElBg          = Color(0xFF0A0E13)  // app background
val ElSurface     = Color(0xFF121922)  // cards, chips, bubbles
val ElSurfaceRaise= Color(0xFF19222E)  // raised/secondary fills
val ElLine        = Color(0xFF283442)  // borders
val ElLineSoft    = Color(0xFF1B2531)  // subtle borders/dividers

val ElTextPrimary = Color(0xFFEDF1F5)
val ElTextDim     = Color(0xFF82909F)
val ElTextFaint   = Color(0xFF525E6C)  // inactive / unavailable (the "gray" state)

val ElAmber = Color(0xFFFFB020)        // caution · pending · primary action
val ElInk   = Color(0xFF1A1205)        // text/icon ON amber
val ElRed   = Color(0xFFFF5A5F)        // alert · failed evidence · blocked
val ElGreen = Color(0xFF27D796)        // evidence captured/verified · on-device
val ElBlue  = Color(0xFF5AC8FF)        // reading/scanning (system thinking)
```

**Safety-state color logic (the core rule — color always agrees with real state):**
| State | Color | Meaning in this app |
|---|---|---|
| Verified / complete | **Green** | evidence captured, step verified, on-device/offline OK |
| Caution / pending / primary action | **Amber** | the user's next action; waiting for evidence |
| Alert / failed / blocked | **Red** | evidence failed, blocked |
| Reading / scanning | **Blue** | model is analyzing (transient) |
| Inactive / unavailable | **Gray** (`ElTextFaint`/`ElLine`) | disabled steps, no value yet |

### 2.2 Map into M3 `ElectricLensTheme` (`Theme.kt`)
`primary = ElAmber`, `onPrimary = ElInk`, `background = ElBg`, `surface = ElSurface`, `surfaceVariant = ElSurfaceRaise`, `outline = ElLine`, `outlineVariant = ElLineSoft`, `onSurface = ElTextPrimary`, `onSurfaceVariant = ElTextDim`, `error = ElRed`. M3 has no "success" slot — expose green/blue via a small palette object or `CompositionLocal` (`LocalElColors`) so composables read `ElGreen`/`ElBlue` from the theme, not hardcoded.

### 2.3 Amber primary button look (top-lit gradient + dark ink, never flat)
A `FilledTonalButton`/`Button` with `Brush.verticalGradient(listOf(Color(0xFFFFC04A), ElAmber))` background, `contentColor = ElInk`, and a soft drop shadow (`Modifier.shadow(12.dp, shape, ambientColor/spotColor = ElAmber.copy(.5f))`). Use only for the single primary action per screen.

### 2.4 Dimens — add a `Dimens` object
```
ScreenPadding = 20.dp   // ONE consistent horizontal padding per screen (LOTO may use 18.dp; keep it identical top→bottom)
CardPadding   = 16.dp
BlockGap      = 14.dp   // vertical rhythm between stacked blocks (12–16)
CardRadius    = 18.dp
ChipRadius    = 8.dp
PillRadius    = 999.dp
MinTouch      = 56.dp   // primary buttons; all targets ≥48.dp, ≥8.dp apart
```

## 3. Typography — `Type.kt`

Bundle three OFL fonts **offline** in `res/font/` (no network): `space_grotesk` (display), `inter` (body), `jetbrains_mono` (data/labels). If bundling isn't possible, fall back to `FontFamily.Default` for display/body and `FontFamily.Monospace` for data — but prefer the real fonts; the pairing is the identity.
```kotlin
val Display = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold), Font(R.font.space_grotesk_medium, FontWeight.Medium))
val Body    = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold))
val Mono    = FontFamily(Font(R.font.jetbrains_mono_regular), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))
```
Roles & scale: **Display** → screen titles, step titles, wordmark. **Body** → all sentence text + button labels. **Mono** → eyebrows/labels (11.sp, UPPERCASE, letterSpacing .04–.18.em), fault codes, breaker IDs, latency, timestamps, status chips, step numbers, the permit body.
Sizes: wordmark `Display Bold 48–52.sp` (autosize down for 360.dp); screen H2 `Display Bold 30.sp`; step title `Display SemiBold 18.sp`; body `14.sp/1.5`; eyebrow/chip `Mono 11.sp`; smallest readout `Mono 10.sp`.

## 4. MOBILE LAYOUT CONTRACT (this is what fixes "bad UI" — verify every item)

These exist because earlier versions overlapped text, wrapped button labels mid-word, and split content into cramped columns. Do not reproduce that.

- **Frame:** every screen is a `Scaffold`. Top status row via `topBar` (or first item), primary action via `bottomBar`. Apply `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` / use Scaffold insets so nothing renders under the system status bar or the gesture nav bar.
- **No overlap:** structure screens with `Column`/`Row` + `Arrangement.spacedBy(BlockGap)`. Use `Box` (z-stacking) **only** for CameraX overlays *inside* the camera preview (reticle, scanline, state chip, confidence strip). Overlays must stay within the preview bounds and never sit on top of the instruction card, stepper, or buttons below.
- **Bottom bar never covers content:** the scroll area is a `LazyColumn`/`verticalScroll` with `contentPadding` bottom ≥ bottom-bar height; the last item must always be reachable.
- **No bad text breaks:** button/chip labels are short and single-line — `maxLines = 1`. Never let "Capture" wrap to "Captu/re": keep labels concise, shrink padding/`fontSize` before wrapping. Long values (filenames, asset strings) use `maxLines = 1, overflow = TextOverflow.Ellipsis` inside a `Row` where the text child has `Modifier.weight(1f)` so it truncates instead of pushing siblings off-screen. Paragraph copy wraps freely at `lineHeight = 1.5.em`, never in a fixed-height box that clips descenders.
- **Alignment:** all cards, the chip row, and the bottom button share the **same** `ScreenPadding` left/right edges — left edges line up down the whole screen.
- **Two-button rows** (e.g. Capture | Upload): `Row(Arrangement.spacedBy(10.dp))` with each button `Modifier.weight(1f)` — equal width, never wraps to separate rows.
- **Touch:** primary buttons `heightIn(min = 56.dp)`; all targets ≥48.dp, ≥8.dp apart.
- **Self-check at 360.dp and 430.dp widths**, report PASS/FAIL each: (a) nothing overlaps, (b) no label wraps mid-word, (c) no text clipped or under system bars, (d) all left edges aligned, (e) last scroll item clears the bottom bar, (f) `prefers-reduced-motion`/animations honored.

## 5. Shared composables (stateless: receive state, emit lambdas; logic in ViewModel)

- **`StatusChip(label, value?, tone)`** — `Surface` (not necessarily M3 AssistChip if it can't match): `ElSurface` fill, 1.dp `ElLineSoft` border, `ChipRadius`, padding `6.dp×10.dp`, Mono 11.sp. Optional leading 6–7.dp dot. Tones: `Live` (red text/border, faint red fill, pulsing dot), `Npu` (`NPU 164 ms`, value `ElGreen`), `Ready` (green dot + label), `Mode` (`MOCK`/`VLM`). Where M3 `AssistChip`/`FilterChip` styling can match, use it; otherwise custom `Surface`.
- **`FieldToolCard(...)`** — `ElSurface`, 1.dp `ElLineSoft`, `CardRadius`, `CardPadding`.
- **`DetectionModeSelector(...)`** — two equal-weight `FilterChip`s (`Mock Demo` / `VLM · On-Device`) bound to `DetectionSource`; below, model `FilterChip`s `SmolVLM` / `InternVL3`. Selected = amber border + faint amber fill + amber text; unselected = `ElSurfaceRaise` + `ElLineSoft` + `ElTextDim`.
- **`AssistantMessageBubble(...)`** — left-aligned `ElSurface` card, eyebrow `ELECTRIC LENS` (Mono 10.sp UPPERCASE amber), body 14.sp/1.5; inline emphasis by meaning (fault name amber, dangerous state red, codes mono). Optional manual-ref footer: divider + Mono 11.sp `ElGreen` `Manual p.112 · §6.3`. User line right-aligned with amber-gradient fill + `ElInk`.
- **`FaultResultCard(...)`** — `FieldToolCard` with: title `Fault F071 OC1 — Overcurrent Phase B` (Display SemiBold), `Asset: Conveyor CV-104`, `Required isolation: B-201 and B-205 before cabinet access.` Codes/IDs in Mono.
- **`LotoStepOverview(...)`** — vertical connected stepper: 24.dp circular node + label per step. Waiting = 2.dp `ElLine` border + Mono number `ElTextFaint`. Active = 2.dp amber + soft ring + label `ElTextPrimary`. Verified = green fill + dark check + label `ElTextDim`. Connector line `ElLine`, green for completed segments.
- **`LotoEvidenceCard(...)`** — active step: title (Display 18), short instruction (14 `ElTextDim`), `Required visible evidence:` line, then `Capture` + `Upload` (equal weight row), evidence thumbnail, and an `EvidenceVerdictChip`.
- **`EvidenceVerdictChip(status)`** — bind to `EvidenceStatus`: `Waiting` → amber "Waiting for evidence"; `Reading` → blue "Checking visible evidence…"; `Verified` → green "Visible evidence verified"; `Failed` → red "Not detected — reposition and recapture".
- **`PermitSummaryCard(...)`** — see §6 Permit.

## 6. Screens (state-machine driven; copy & data exact)

**Screen 1 — Start.** Centered: green on-device pill `On-Device`; wordmark `Electric Lens` (LENS-style accent optional, Display Bold, autosize); subtitle `Electrical Safety · On-Device`; thin amber accent rule; **Start Session** primary → document state; `DetectionModeSelector` (Mock Demo / VLM · On-Device + SmolVLM/InternVL3); honesty note (Mono/dim): "Mock mode is deterministic for demos. VLM mode uses the on-device vision path when available." Selections update `DetectionSource`/model state.

**Screen 2 — Add Documents.** H2 `Add Equipment Manual`; green offline badge `No network required`; **Use Demo Document** (primary) / **Choose PDF** (outline). Processing states (animated, calm): "Extracting text offline…" → "Scanning for fault codes and procedures…". Result `FieldToolCard`: title truncates → `PowerFlex_753_VFD_Manual.pdf`, then Mono green lines `3 fault codes found · 7 procedures found · 2 isolation references found`. Connect to real summarizer if present; else deterministic demo state (don't make it look fake). On complete → AI Session.

**Screen 3 — AI Session.** Top status row: mode chip `MOCK`/`VLM`, model chip, latency chip `NPU: — ms` (or real), mute `IconButton` (TtsManager). Conversation (`LazyColumn`, weight(1f)): greeting `Electric Lens ready. Need help with a fault code, the manual, or guided lockout?`; after user has a fault → `Describe the symptom, or upload a photo of the VFD fault display.`; upload state → thumbnail + spinner + `Reading fault display…`; then `FaultResultCard` (F071 OC1). Bottom controls: push-to-talk mic, **Upload Fault Code Image**, **Start Guided LOTO** (disabled until `canStartLoto == true`, which flips only after fault→isolation mapping). Mock = deterministic; VLM = real pipeline, surface failure calmly.

**Screen 4 — Guided LOTO (hero, image-first, evidence-first).** Four steps: **B-201 OFF · B-205 OFF · Lock & Tag Applied · MCC Door Opened.** Portrait: active `LotoEvidenceCard` first, compact `LotoStepOverview` rail above/below; wider screens: card + side overview. Optional CameraX preview with the **reticle + scanline** overlay (amber framing; turns **blue** while a verdict is `Reading`) and a transient confidence strip — but the verdict source of truth is `EvidenceStatus`, shown via `EvidenceVerdictChip`. Header: `← Exit`, `STEP n / 4` (current number amber); a `LinearProgressIndicator` (amber→green) for progress. `Failed` keeps the user on the same step. `Verified` records {thumbnail, timestamp, verdict} and advances; show a thumbnail strip of captured evidence. After all 4: **Generate Permit PDF** + completion copy **"LOTO evidence captured. Proceed to zero-energy verification."** (never "safe"). Mock passes deterministically; VLM uses the real verify fn if available.

**Screen 5 — Permit.** `PermitSummaryCard`: `Asset: Conveyor CV-104` · `Drive: VFD-104` · `Location: MCC-2 | Bucket 17` · `Fault: F071 OC1` · `Fault meaning: Overcurrent Phase B` · `Fault type: Overcurrent` · isolation `B-201`, `B-205` · per evidence step {title, status, timestamp, thumbnail}. Final statement: **"LOTO evidence captured. Proceed to zero-energy verification."** Actions: **Open PDF**, **Share PDF**, **Start New Session** (resets `SessionViewModel` → Start). Extend existing `PdfDocument` generation to include fault code, meaning, **type (Overcurrent)**, asset, isolation points, evidence timestamps; embed thumbnails only if image embedding exists — otherwise include evidence metadata and don't pretend thumbnails are inside. Clean layout: Header · Asset block · Fault block · Isolation block · Evidence block · final zero-energy statement.

## 7. Motion

Define one easing and reuse it: `val ElEase = CubicBezierEasing(.22f, .61f, .36f, 1f)`.
- Screen/content reveals: `tween(420, easing = ElEase)` (enter `fadeIn + slideIn`/`translationX 24→0` + slight scale).
- Micro-interactions: 120–300 ms. Status-dot pulse / scanline loop ~1.2–1.5 s. Confidence/progress fill ~1.4 s with `ElEase`.
- Respect reduced-motion / disable looping animations when the system requests it; keep state changes instant.

---

## 8. Acceptance path & build

Implement so this works end-to-end: open → Start Session → **Mock Demo** → Use Demo Document → offline processing result → AI Session → upload/trigger fault → detects **F071 OC1** → explains **Overcurrent Phase B** → identifies **Conveyor CV-104** → requires isolation **B-201 & B-205** → **Start Guided LOTO** enables → complete 4 evidence steps (each shows thumbnail + timestamp + verdict; a `Failed` state is visually possible and does **not** advance; `Verified` advances) → **"LOTO evidence captured. Proceed to zero-energy verification."** → permit summary incl. **Fault type: Overcurrent** → open/share PDF (if supported) → Start New Session.

After implementing: **run the Gradle build, fix all compile errors**, leave no broken/unused imports, add no unsupported dependencies, don't churn package structure. Keep it readable and demo-stable — **don't overbuild.** Then report the §4 mobile self-check (PASS/FAIL per item) at 360.dp and 430.dp.
