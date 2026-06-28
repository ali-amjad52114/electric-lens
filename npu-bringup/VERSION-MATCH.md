# SmolVLM-500M QNN on S25 Ultra — ExecuTorch / QAIRT Version Match

**Goal:** run the pre-compiled 3-part SmolVLM2-500M QNN export
(`vision_encoder_qnn.pte`, `tok_embedding_qnn.pte`, `hybrid_llama_qnn.pte`) on a
Samsung Galaxy S25 Ultra (SM8750, Hexagon/HTP **v79**) via
`examples/qualcomm/oss_scripts/llama/llama.py --pre_gen_pte` +
`qnn_multimodal_runner`.

**Top-line recommendation (HIGH confidence):**
Build ExecuTorch from tag **`v1.2.0`** (fallback `v1.1.0`) against
**QAIRT/QNN 2.46.0** headers, and ship/run with the **QAIRT 2.46.0** runtime
`.so` set. The pre-built model was compiled with **QNN 2.46.0**
(build id `2.46.0.260424121129`), so the runtime QNN libraries must be **>= 2.46.0**.
The official **ExecuTorch 1.0.0-qnn AAR you already have CANNOT run these files**
(1.0.0 predates SmolVLM support and is built against QNN 2.37).

---

## (a) Evidence found — literal strings + sources

### A1. The model was compiled with QNN/QAIRT 2.46.0  (DECISIVE)
Grep of the raw `.pte` binaries (whitelisted `grep -a -o -E`):

| File | Literal strings found | Source command |
|------|----------------------|----------------|
| `hybrid_llama_qnn.pte` | `2.46.0.260424121129`, `QnnBackend`, `qnn_compile_spec`, `HTP`, `SM8750` | `grep -a -o -E "2\.[0-9]+\.[0-9]+\.[0-9]+\|QnnBackend\|qnn_compile_spec\|HTP" <file>` |
| `vision_encoder_qnn.pte` | `2.46.0.260424121129`, `QnnBackend`, `qnn_compile_spec`, `SM8750` | same |
| `tok_embedding_qnn.pte`  | `2.46.0.260424121129`, `QnnBackend`, `qnn_compile_spec` | same |

`2.46.0.260424121129` is the Qualcomm AI Engine Direct **Backend Build Id**
(`<version>.<buildstamp>`). All three parts agree → the entire export used
**QAIRT 2.46.0**. `SM8750` confirms the SoC target (S25 Ultra, HTP v79).

Model dir listing (source: `ls -la`): files dated **Jun 25, 2026**; sizes
hybrid ~388 MB, vision ~102 MB, tok_embedding ~94 MB.

### A2. The cloned ExecuTorch repo
- `C:\AI\Projects\ElectricSafe\executorch\version.txt` → **`1.4.0a0`** (a `main`
  snapshot, newer than any released tag).
- `examples/qualcomm/oss_scripts/llama/decoder_constants.py:64` →
  `"smolvlm_500m_instruct": "smolvlm",` (support present on this checkout).
- `examples/qualcomm/oss_scripts/llama/README.md` lists **SmolVLM 500M** under
  Vision-Language Models and states the flow is "validated on … **Samsung Galaxy
  S25** …".
- `backends/qualcomm/scripts/qnn_config.sh:9` →
  `QNN_VERSION="2.37.0.250724"` (this is only the *auto-download default for the
  build*, NOT the version the model was compiled with).
- `backends/qualcomm/scripts/qnn_config.sh:14-15` →
  `HEXAGON_SDK_VERSION="6.5.0.0"`, `HEXAGON_TOOLS_VERSION="19.0.07"`.

### A3. ExecuTorch QNN version-skew rule (from source)
`backends/qualcomm/runtime/backends/QnnBackendCommon.cpp:160-187`:
- QNN API **major** mismatch → `Error::Internal` (FATAL).
- minor **lower** than the version ET was compiled against → WARN "minimum
  supported version".
- minor **higher** → WARN "tested against …" but returns `Error::Ok` (allowed).

Separately, the HTP context-binary loader rejects a context that was produced by
a *newer* QNN than the runtime. This exact failure is documented in
`docs/source/backends-qualcomm.md:390-417`:
```
W [Qnn ExecuTorch]: Qnn API version 2.33.0 is mismatched
E [Qnn ExecuTorch]: Using newer context binary on old SDK
E [Qnn ExecuTorch]: Can't create context from binary. Error 5000
```
Cause (quoted): "Model compiled with QNN SDK version X but APK uses QNN runtime
version Y." Fix (quoted): match the QNN runtime version or recompile the model.
**Net rule: runtime QNN must be >= the QNN that compiled the .pte → here, >= 2.46.0.**

### A4. QNN version recommended / pinned by ExecuTorch docs
- `docs/source/backends-qualcomm.md:74-75`: "we have verified and recommend using
  **QNN 2.37.0** for stability" with URL
  `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.37.0.250724/v2.37.0.250724.zip`.
- `backends/qualcomm/README.md:32`: "only LPAI Arch v6 is supported, which
  requires **QNN SDK version 2.39 or higher**."
- These are ExecuTorch's *recommended build* versions. They are independent of,
  and **lower than**, the 2.46.0 that produced this specific model. Because QNN is
  forward-compatible at the API-version level (A3) but the context binary is NOT
  backward-compatible to older runtimes, we must use **2.46.0**, not 2.37.0.

### A5. When SmolVLM landed in ExecuTorch (web — github.com)
- PR **#16292** "Qualcomm AI Engine Direct - Support Multimodal VLMs", merged
  **2025-12-23** by cccclai — adds AOT support for **SmolVLM-500M** and
  InternVL3-1B + the multimodal compile pipeline.
  (https://github.com/pytorch/executorch/pull/16292)
- PR **#16536** "Support multimodal(VLM) runner", merged **2026-01-26** — the
  `qnn_multimodal_runner`. (https://github.com/pytorch/executorch/pull/16536)
- PR #17308 multi-turn VLM (2026-03-18), and later quant PRs, refine it.

### A6. Which release tags contain SmolVLM (web — raw.githubusercontent.com)
`examples/qualcomm/oss_scripts/llama/decoder_constants.py` at each tag:
| Tag | contains `smolvlm_500m_instruct`? | QNN_VERSION pinned |
|-----|-----------------------------------|--------------------|
| `v1.0.0` | **NO** | 2.37.0.250724 |
| `v1.1.0` | **YES** | 2.37.0.250724 |
| `v1.2.0` | **YES** | 2.37.0.250724 |
| `main` (local `1.4.0a0`) | **YES** | 2.37.0.250724 |

→ **Minimum ExecuTorch release with SmolVLM 3-part export + multimodal runner =
`v1.1.0`.** Every release still defaults its build to QNN 2.37, so the QNN
version must be overridden to 2.46.0 regardless of which ET tag you pick.

### A7. The two local AAR files
NOTE: I could **not** unzip the AARs — `unzip`, PowerShell `ExpandToDirectory`,
and `cd`-prefixed shell were all blocked in this session, and the inner `.so`
files are deflate-compressed so version strings are not grep-able from the outer
zip. Identification below is by provenance, not by inspecting the `.so`:
- `C:\AI\Projects\ElectricSafe\_qnn_aar\executorch-qnn.aar` — described as the
  official **1.0.0-qnn** release (libexecutorch.so with QnnBackend +
  libqnn_executorch_backend.so). Given A6, **ET 1.0.0 has no SmolVLM support and
  is built against QNN 2.37 → it will NOT load these 2.46-compiled SmolVLM .pte.**
  Do not use it for this model.
- `C:\AI\Projects\ElectricSafe\relaysight-android\app\libs\executorch.aar` —
  XNNPACK-only (no QNN backend) → cannot run QNN .pte at all.
- **ACTION:** unzip both AARs in a normal shell and run
  `strings libQnnHtp.so | grep -E "2\.[0-9]+\.[0-9]+"` and check
  `AndroidManifest.xml` / any `version` file to confirm. Neither is the right
  artifact for this model anyway (see recommendation).

---

## (b) Recommended ExecuTorch git tag/commit to check out

1. **Preferred: `v1.2.0`.** Earliest *stable, post-SmolVLM* tag that also has the
   multi-turn VLM fixes; matches the file layout the model was almost certainly
   exported with (`smolvlm3` sibling entry present, encoder/multimodal_runner
   tree present).
2. **Conservative minimum: `v1.1.0`** — first tag containing
   `smolvlm_500m_instruct` + `qnn_multimodal_runner`.
3. **Use the existing `main` checkout (`1.4.0a0`) ONLY if v1.2.0 fails to load the
   .pte.** `main` is the most likely to load a Jun-2026 export, but also the most
   likely to have drifted (schema/runner API). See version-skew note (e).

```bash
cd C:/AI/Projects/ElectricSafe/executorch
git fetch --tags
git checkout v1.2.0      # or v1.1.0
cat version.txt          # sanity check
```

---

## (c) Exact QAIRT/QNN SDK version + download + placement

- **Required version: QAIRT / Qualcomm AI Engine Direct (QNN) `2.46.0`**
  (build id seen in the model: `2.46.0.260424121129`).
- **Why exactly 2.46.0 (not 2.37 / not "latest"):** the context binaries inside
  the .pte were produced by 2.46.0. A runtime QNN that is *older* (2.37, 2.39…)
  fails with "Using newer context binary on old SDK / Error 5000" (A3). A runtime
  that is *newer* than 2.46 usually works (forward-compatible) but is unverified
  for these files — prefer an exact match first.

### Download (Qualcomm AI Runtime Community — free, no NDA)
The ExecuTorch-documented community channel (A4) uses this URL template:
```
https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/<VER>/v<VER>.zip
```
For 2.46.0 the version string is `2.46.0.<buildstamp>`. The model's embedded build
id is `2.46.0.260424121129`; the public Community zip uses a `2.46.0.<YYMMDD>`
stamp. **Verify the exact stamp on the page before downloading** (the 2.37 analog
was `2.37.0.250724`):
```
https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.46.0.<stamp>/v2.46.0.<stamp>.zip
```
- **Account / NDA:** the *Community* (`Qualcomm_AI_Runtime_Community`) packages on
  `softwarecenter.qualcomm.com` are public — a free Qualcomm account (Qualcomm ID)
  may be required to accept the license, but **no NDA**. The full QAIRT SDK is also
  available from the Qualcomm developer portal
  (https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk —
  "Get Software"), and via Qualcomm Package Manager **QPM3 / qpm.qualcomm.com**
  (free Qualcomm ID). Use QPM if the direct Community zip for the exact 2.46.0
  stamp is not listed.
- **If 2.46.0 is unavailable through Community:** install via QPM3
  (`qpm-cli --extract qualcomm_ai_engine_direct.2.46.0...`) which exposes all
  released QAIRT versions to logged-in accounts.

### Where to place it
```
# Linux/macOS build host
~/qcom/qairt/2.46.0/        # unzip here; this becomes $QNN_SDK_ROOT
export QNN_SDK_ROOT=$HOME/qcom/qairt/2.46.0
# (Windows host) e.g. C:\Qualcomm\AIStack\QAIRT\2.46.0  -> set QNN_SDK_ROOT to it
```
Override ExecuTorch's 2.37 default by exporting `QNN_SDK_ROOT` *before* building
(`backends/qualcomm/scripts/build.sh:23` honors it; otherwise it auto-downloads
2.37). Device-side `.so` set to push to the phone:
`$QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp*.so`,
`$QNN_SDK_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`,
`libQnnSystem.so` (v79 skel is mandatory for SM8750).

Also use **Android NDK r26c** (docs) and Hexagon SDK 6.5.0.0 / Hexagon Tools
19.0.07 if you compile op packages (A2).

---

## (d) How to verify the runtime matches the .pte BEFORE a long build

1. **Confirm the model's QNN build id (already done, repeat to be safe):**
   ```bash
   grep -a -o -E "2\.[0-9]+\.[0-9]+\.[0-9]+" hybrid_llama_qnn.pte | sort -u
   # expect: 2.46.0.260424121129
   ```
2. **Confirm the downloaded SDK version matches major.minor (2.46):**
   ```bash
   cat $QNN_SDK_ROOT/sdk.yaml        # or bin/.../qnn-version  -> version: 2.46.0.xxxxx
   strings $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so | grep -E "2\.46\.0"
   ```
   The major.minor (2.46) MUST match; the buildstamp may differ slightly.
3. **Confirm the headers ET will compile against are 2.46** (this sets
   `QNN_API_VERSION_*` checked at runtime, A3):
   ```bash
   grep -R "QNN_API_VERSION_MAJOR\|QNN_API_VERSION_MINOR" $QNN_SDK_ROOT/include/QNN/QnnCommon.h
   ```
4. **Cheap end-to-end smoke test before the full APK build:** build only the
   x86_64 `qnn_executor_runner` / python QnnManager (fast) and call
   `QnnBackendCache`/context-binary load on `vision_encoder_qnn.pte`; if you get
   "Using newer context binary on old SDK / Error 5000" the QNN runtime is too
   old → stop and fix the version before the long Android build.
5. On device, watch logcat for the A3 warnings: a "tested against" WARN is fine; an
   `Error::Internal` major-mismatch or `Error 5000` is fatal.

---

## (e) Fallback plan if versions don't line up

1. **2.46.0 Community zip not published / wrong stamp:** get 2.46.0 via **QPM3**
   (qpm.qualcomm.com, free account). If still unobtainable, use the **next
   newer** QAIRT that *is* available (e.g. 2.47/2.48) — newer runtime loading an
   older context binary is the forward-compatible direction and usually works
   (expect only a "tested against" WARN). Do **NOT** drop to 2.37/2.39.
2. **`v1.2.0` build loads the .pte but crashes at runtime (runner API drift):**
   move to the local **`main` (1.4.0a0)** checkout — it definitely contains the
   current `qnn_multimodal_runner` and is closest in time to the Jun-2026 export.
3. **`main` fails to load the .pte (PTE flatbuffer schema newer/older than
   runtime):** this is the version-skew risk — the .pte were exported by a
   *specific* ET commit. Re-derive that commit: the export is dated 2026-06-25 and
   uses QNN 2.46; check out an ET commit from on/just-before that date
   (`git log --until=2026-06-25 --first-parent main`), rebuild, retry. Verifying
   load with the fast x86 runner (step d4) keeps each iteration cheap.
4. **Last resort — re-export instead of reusing the .pte:** if no runtime loads
   the pre-built parts, regenerate them with your chosen ET tag using
   `llama.py --decoder_model smolvlm_500m_instruct --soc_model SM8750` against the
   matching QNN SDK, replacing `--pre_gen_pte`. Slow (full quant + compile) but
   guarantees an ET/QNN/.pte match.

---

## TL;DR pairing matrix

| Component        | Use                                  | Hard requirement |
|------------------|--------------------------------------|------------------|
| ExecuTorch tag   | **v1.2.0** (min v1.1.0; `main` fallback) | must contain `smolvlm_500m_instruct` (>= v1.1.0) |
| QAIRT/QNN SDK    | **2.46.0** (`2.46.0.260424121129` family) | runtime QNN **>= 2.46.0** |
| NDK              | r26c                                  | recommended |
| SoC / skel       | SM8750 → `libQnnHtpV79Skel.so`        | mandatory |
| Do NOT use       | the 1.0.0-qnn AAR; the XNNPACK-only AAR; QNN 2.37/2.39 | would fail |

**Confidence: HIGH** that QNN must be 2.46.0 (embedded build id is direct
evidence) and that ET must be >= v1.1.0 (decoder_constants per-tag check is direct
evidence). **MEDIUM** on v1.2.0-vs-main being the exact best ET tag — resolve
cheaply with the x86 load smoke test (d4) before committing to a full Android build.
