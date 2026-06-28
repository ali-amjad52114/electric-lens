# Model Integration — Electric Lens (on-device VLM)

This document explains how to take Electric Lens from its default **Mock**
detection mode to **real on-device NPU inference** with a vision-language model
(VLM) running through **ExecuTorch + Qualcomm QNN** on the Snapdragon **SM8750**
(Hexagon NPU) in the Galaxy S25 Ultra.

It is written to be **honest about what runs today**. With no model files, no
`executorch.aar`, and no custom runner present, the app stays in Mock mode and
**never crashes**. The VLM seam compiles and is wired end-to-end, but the
SmolVLM export that ships in this repo (`Model/…`) is a **3-part Qualcomm QNN
split** that the **stock ExecuTorch Android API cannot load** — running it
requires the additional pieces described in
[The honest blocker](#3-the-honest-blocker) below.

> Everything is fully **offline**. The app declares **no `INTERNET`
> permission**. None of the steps below require network access on-device.

---

## 1. The discovered SmolVLM model layout

The only model actually present in the repo is a SmolVLM2-500M export tuned for
SM8750, under:

```
Model/smolvlm_500m_instruct_ctxt-4096_SM8750/
```

It is a **3-part Qualcomm QNN export** (vision encoder + token embedding +
decoder), plus the tokenizer and chat template:

| File                       | Approx size | Role                                  |
| -------------------------- | ----------- | ------------------------------------- |
| `vision_encoder_qnn.pte`   | ~102 MB     | Vision encoder (image → embeddings)   |
| `tok_embedding_qnn.pte`    | ~95 MB      | Token embedding                       |
| `hybrid_llama_qnn.pte`     | ~389 MB     | Decoder (Llama-family, hybrid)        |
| `tokenizer.json`           | ~3.5 MB     | Idefics3 / GPT2 tokenizer (vocab 49152) |
| `tokenizer_config.json`    | ~0.6 KB     | Tokenizer config / special tokens     |
| `chat_template.jinja`      | ~0.4 KB     | Chat template (Jinja)                 |

**Tokenizer / special tokens** (from `tokenizer_config.json`):

- Tokenizer class: `GPT2Tokenizer`, processor `Idefics3Processor`, vocab `49152`.
- `bos_token` = `<|im_start|>`
- `eos_token` = `<end_of_utterance>`
- image placeholder = `<image>` (plus `<fake_token_around_image>`)
- `pad_token` = `<|im_end|>`, `unk_token` = `<|endoftext|>`

**Chat template** (effective form the app mirrors):

```
<|im_start|>User:<image>{text}<end_of_utterance>
Assistant:
```

**Image input:** `512 × 512` RGB. SmolVLM2 / Idefics3 image processing uses
mean/std = `0.5 / 0.5 / 0.5` per channel, i.e. `(x/255 − 0.5) / 0.5` → `[-1, 1]`.

> **InternVL3** is offered as a selectable model in the UI, but **its folder is
> not present**. Selecting InternVL3 with no files simply falls back to Mock.
> Its `VlmConfig` (`VlmConfigs.INTERNVL3`) is a documented best-effort placeholder
> and every field is marked `TODO verify when model lands`.

---

## 2. Where to drop things to make the VLM path real

The app loads models by **absolute path from `filesDir`**: on first run it copies
everything under `assets/<assetDir>/` into `filesDir/<assetDir>/` and then loads
from there (see `VlmEngineFactory.copyModelAssetsIfPresent`). The build is
configured with `noCompress += ["pte", "bin", "model"]` so the `.pte` files are
stored uncompressed in the APK.

Drop the three artifact groups in place:

### (a) The ExecuTorch AAR

```
app/libs/executorch.aar
```

`app/build.gradle.kts` already wires this in with
`implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))`
— an empty/absent `libs/` directory is a no-op. Use the **LLM/multimodal-capable**
AAR that matches the ExecuTorch build you exported the model with. The runtime
helper deps (`com.facebook.soloader:soloader:0.10.5`,
`com.facebook.fbjni:fbjni:0.7.0`) are already declared.

### (b) Native libraries (jniLibs, arm64-v8a only)

```
app/src/main/jniLibs/arm64-v8a/
  libexecutorch.so
  libqnn_executorch_backend.so
  libQnnHtp.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnSystem.so
  # + the custom multimodal runner .so for the split path (see §3)
```

The build pins `abiFilters += "arm64-v8a"` and uses
`jniLibs { useLegacyPackaging = true }` so the QNN `.so` files load reliably.
The HTP **V79** skel/stub pair matches the Hexagon version on SM8750. At runtime
the QNN HTP backend typically also needs `ADSP_LIBRARY_PATH` (and often
`LD_LIBRARY_PATH`) pointed at the app's native lib dir so the HTP skel is
discoverable.

### (c) Model files (assets)

```
app/src/main/assets/models/smolvlm_500m/
  vision_encoder_qnn.pte
  tok_embedding_qnn.pte
  hybrid_llama_qnn.pte
  tokenizer.json
  # (tokenizer.bin if your runner consumes the binary tokenizer — see §3)
```

The asset subfolder name **must** match `VlmConfig.assetDir` (`models/smolvlm_500m`
for SmolVLM). On first launch these are copied to
`filesDir/models/smolvlm_500m/` and loaded by absolute path.

---

## 3. The honest blocker

**Stock ExecuTorch Android cannot load this model as-is.**

The stock Android API `org.pytorch.executorch.extension.llm.LlmModule` loads a
**single combined `.pte`**. The SmolVLM export here is **three separate `.pte`
programs** (`vision_encoder` + `tok_embedding` + `hybrid_llama` decoder). There
is **no shipped Android/Kotlin binding** for the split path.

Upstream, the split is driven by a **custom C++ `MultimodalRunner`**
(ExecuTorch `examples/qualcomm/oss_scripts/llama` → `qnn_multimodal_runner.cpp`
and `runner/multimodal_runner/`). It has no Android binding out of the box. So
there are two routes to make inference real:

### Route A — Custom JNI runner for the 3-part split (run THIS export)

Build a custom JNI wrapper around ExecuTorch's QNN multimodal runner and expose
it as the exact class the app reflects on:

```
com.electriclens.executorch.QnnMultimodalRunner
```

(see `QnnSplitVlmEngine.RUNNER_CLASS`). The native side must:

1. Wrap the upstream QNN multimodal runner, taking the **three `.pte` absolute
   paths** (vision encoder, token embedding, decoder) plus the tokenizer. The
   runner usually consumes a binary `tokenizer.bin`; convert `tokenizer.json`
   to that format if needed.
2. Load the three programs, run the **encoder → embeddings**, expand the
   `<image>` placeholder, feed embeddings + text into the **decoder**, and decode
   to EOS (`<end_of_utterance>`).
3. Ship the QNN HTP v79 `.so` set (§2b) plus the custom runner `.so` and
   `libexecutorch.so`, and set `ADSP_LIBRARY_PATH` so HTP initializes.

When that class and the files are present, `QnnSplitVlmEngine.ready` becomes
true and `VlmDetectionSource` routes captures through the NPU. Until then
`QnnSplitVlmEngine.generate(...)` throws a descriptive `IllegalStateException`
and the source degrades to Mock — the demo never sticks.

> The Kotlin reflection seam for the runner already exists in
> `vlm/QnnSplitVlmEngine.kt`; you only need to provide the native class +
> libraries it looks up by name. No Kotlin/ViewModel changes are required.

### Route B — Re-export as a single COMBINED multimodal `.pte`

Re-export SmolVLM as **one combined multimodal `.pte`** (XNNPACK, or a single
combined QNN program), then point the config at it with `split = false`:

```kotlin
// vlm/VlmConfig.kt — VlmConfigs.SMOLVLM
split = false,
visionEncoderPte = null,
tokEmbeddingPte  = null,
decoderPte       = "smolvlm_500m.pte",   // the combined program
```

With `split = false`, `VlmEngineFactory` builds `ExecuTorchLlmEngine`, which
drives the stock `LlmModule` **via reflection** (no compile-time dependency on
the AAR). That path is already complete in `vlm/ExecuTorchLlmEngine.kt`:

- `LlmModule(modelType = 2 /* TEXT_VISION */, modulePath, tokenizerPath, temperature)`
- `load()`
- `generate(image: IntArray /* uint8 RGB HWC */, width, height, channels = 3, prompt, seqLen, callback, echo)`
- `LlmCallback.onResult(String)` / `onStats(String)` / `onError(int, String)`
- `stop()` / `resetContext()` / `close()`

Route B is the lower-effort path to a working NPU demo if you can produce a
combined export; Route A runs the exact 3-part artifact already in the repo.

---

## 4. `VlmConfig` fields per route

`vlm/VlmConfig.kt` is the **single source of truth** for file names, image
preprocessing, and the prompt template. Set fields per route:

| Field              | Split (Route A)            | Combined (Route B)         |
| ------------------ | -------------------------- | -------------------------- |
| `split`            | `true`                     | `false`                    |
| `visionEncoderPte` | `"vision_encoder_qnn.pte"` | `null`                     |
| `tokEmbeddingPte`  | `"tok_embedding_qnn.pte"`  | `null`                     |
| `decoderPte`       | `"hybrid_llama_qnn.pte"`   | combined `.pte` filename   |
| `tokenizer`        | `"tokenizer.json"`         | `"tokenizer.json"`         |
| `assetDir`         | `"models/smolvlm_500m"`    | `"models/smolvlm_500m"`    |
| `imageSize`        | `512`                      | `512`                      |
| `mean` / `std`     | `0.5,0.5,0.5 / 0.5,0.5,0.5`| `0.5,0.5,0.5 / 0.5,0.5,0.5`|
| `promptTemplate`   | `"<\|im_start\|>User:<image>%s<end_of_utterance>\nAssistant:"` | same |
| `maxNewTokens`     | `64`                       | `64`                       |

Notes on preprocessing:

- The **combined** (`ExecuTorchLlmEngine`) path feeds the model a **raw uint8 RGB
  HWC `IntArray`** (0..255) sized `imageSize × imageSize`; the stock runtime
  applies normalization internally, so `mean`/`std` are documentation there.
- The **split** (custom runner) path receives a **preprocessed/normalized
  tensor**, so it should apply `mean`/`std` (`[-1, 1]` for SmolVLM).

---

## 5. Verifying NPU / HTP delegation

To confirm inference is actually running on the Hexagon NPU (not silently
falling back):

1. **Native libs loaded.** In `logcat`, watch for the QNN/HTP `.so` files loading
   (`libQnnHtp.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`,
   `libqnn_executorch_backend.so`). Missing libs show as `UnsatisfiedLinkError`
   or `dlopen` failures.
2. **HTP backend init.** Look for ExecuTorch QNN delegate / HTP backend
   initialization logs (QNN delegate registration, HTP context creation). A
   CPU fallback shows up as the QNN delegate failing to initialize.
3. **Model readiness in the app.** `VlmDetectionSource` exposes
   `modelReady: StateFlow<Boolean>`. It is true only when the engine's `ready`
   is true (runner/classes present **and** all model files exist on disk). The
   raw answer / status is mirrored to the UI via `lastAnswer`.
4. **Latency in the UI.** `VlmDetectionSource.onCapture(...)` measures
   **wall-clock** around the single-frame `engine.generate(...)` call and
   publishes it to `latencyMs: StateFlow<Long>`. The ViewModel mirrors this to
   the Live Session screen, where `NpuReadout` renders **`NPU: X ms`** (and
   `NPU: — ms` until the first inference). A real, non-zero on-device number that
   moves with model size is your end-to-end confirmation.

---

## 6. Switching models at runtime

The whole UI depends only on the `CaptureDetectionSource` interface; switching is
done in `SessionViewModel`:

- **Mock ↔ VLM toggle** — `toggleDetectionSource()` swaps the active source
  between `MockDetectionSource` (default) and `VlmDetectionSource`. Current
  selection is observable via `useMockSource: StateFlow<Boolean>` (`true` = Mock).
- **Model selector** — `selectModel(VlmModelId)` picks
  `SMOLVLM_500M` (default) or `INTERNVL3_1B`. If the VLM source is active it is
  rebuilt immediately with the new model.

**Safety of empty state:** with nothing present (no AAR, no `.so`, no model
files), `VlmEngineFactory.create(...)` never throws — it returns an engine whose
`ready` is false. `VlmDetectionSource` then surfaces an honest status
(`"Model not loaded — using mock detection"`) and **falls back gracefully** so
the LOTO state machine never gets stuck. Selecting **InternVL3** with no files
behaves the same way.

---

## 7. Per-step prompts the adapter sends

`vlm/VlmModelAdapter.promptFor(DetectionType)` is the only model-specific
prompting place. The exact prompts sent (before chat-template wrapping):

| Detection step                       | Prompt text                                                                                          |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| `FAULT_CODE`                         | `Read the fault code shown on this industrial VFD display. Reply with only the code (e.g. F071 OC1).` |
| `BREAKER_B201_OFF` / `BREAKER_B205_OFF` | `Is the circuit breaker handle in the OFF (down) position? Answer yes or no.`                      |
| `LOCK_TAG`                           | `Is a padlock AND a danger tag applied to this breaker? Answer yes or no.`                            |
| `MCC_OPEN`                           | `Is the electrical cabinet door open? Answer yes or no.`                                              |

Each prompt is wrapped with the model's `promptTemplate` before generation.
Answer parsing (`VlmModelAdapter.parse`) extracts a fault code for `FAULT_CODE`
(regex `[A-Za-z]\d{2,4}(\s?[A-Za-z0-9]{2,4})?`), and for the yes/no steps emits a
`Detection` only on a clearly **affirmative** answer (confidence `0.9`, reduced to
`0.6` when the answer hedges). Negative or empty answers emit no detection, which
the UI shows as "Not detected — reposition and recapture."

---

## Safety

Electric Lens **documents visible lockout evidence only**. It does **not** certify
a zero-energy state and does **not** replace required testing or the
site-approved LOTO procedure. The app uses strict, approved wording and never
states or implies that equipment is de-energized or safe to work on. Always
follow your site's approved LOTO procedure and perform required zero-energy
verification before contact.
