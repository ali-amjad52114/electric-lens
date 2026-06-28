# QAIRT / QNN SDK Acquisition — Result

**Task:** obtain the Qualcomm AI Engine Direct SDK (QAIRT / QNN) needed to build &
run the 3-part SmolVLM QNN export on SM8750 / Hexagon **v79**.

**Did I download it? NO — not in this session.** Every command-execution and
network tool (Bash, PowerShell, WSL, WebFetch, WebSearch) was **denied by the
sandbox** this run, so I could neither run `curl`/`wget` nor even network-probe the
URL. I therefore could not produce a *verified* local archive. What I *can* give
you is the exact, evidence-based version + a **non-interactive public download URL
that does not require a Qualcomm login**, plus the authenticated fallback. Honest
status: instructions are ready and high-confidence; the bytes are not yet on disk.

---

## 1. Pinned version (verified, not guessed)

**Required QAIRT/QNN runtime: `2.46.0`** (model build id `2.46.0.260424121129`).

Evidence (two independent, cross-checked sources):

| What | Value | Source |
|------|-------|--------|
| Version baked into the .pte (DECISIVE) | `2.46.0.260424121129` | `npu-bringup/VERSION-MATCH.md` §A1 — grep of all 3 `*_qnn.pte` binaries; `SM8750` also present |
| ExecuTorch *build default* (NOT enough to run the model) | `2.37.0.250724` | `executorch/backends/qualcomm/scripts/qnn_config.sh:9` |
| ExecuTorch documented/recommended build version | `2.37.0` | `executorch/docs/source/backends-qualcomm.md:74-75` |
| LPAI Arch v6 floor | `>= 2.39` | `executorch/backends/qualcomm/README.md:32` |

**Why 2.46.0 and not the 2.37.0 ExecuTorch auto-downloads:** the HTP context
binaries inside the .pte were produced by QNN 2.46.0. A runtime QNN that is *older*
(2.37 / 2.39) fails at load with `Using newer context binary on old SDK / Error
5000` (see `VERSION-MATCH.md` §A3, citing
`backends/qualcomm/runtime/backends/QnnBackendCommon.cpp:160-187` and
`docs/source/backends-qualcomm.md:390-417`). Runtime QNN must be **>= 2.46.0**.
ExecuTorch's own `qnn_config.sh` default (2.37) is fine for *compiling host AOT
parts* but will **not run these pre-built .pte** — override `QNN_SDK_ROOT` to 2.46.

> If you instead intend to **re-export** the model from scratch with this repo
> rather than reuse the pre-built .pte, ExecuTorch pins **2.37.0.250724** and will
> auto-fetch it (see route A). But for the stated goal (run the existing 2.46.0
> export) you need **2.46.0**.

---

## 2. Download routes tried + result

> Tooling note: in THIS session Bash/PowerShell/WSL/WebFetch/WebSearch all
> returned "Permission … denied", so the rows below marked **NOT EXECUTABLE HERE**
> are the commands you (or a session with shell access) must run. No download was
> faked. No local QAIRT SDK exists yet on disk (confirmed: no `sdk.yaml`, no
> `lib/hexagon-v79`, no host `libQnnHtp.so` anywhere under the project; only
> device-pulled stubs in `relaysight-android/.../jniLibs/arm64-v8a/`).

### Route A — Public "Qualcomm AI Runtime Community" zip on softwarecenter.qualcomm.com  (NO LOGIN) ✅ recommended
ExecuTorch downloads QAIRT this exact way, with **no Qualcomm account**, via
`backends/qualcomm/scripts/download_qnn_sdk.py` / `install_qnn_sdk.sh`. URL template
(`qnn_config.sh:10`, also documented at `backends-qualcomm.md:75`):
```
https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/<VER>/v<VER>.zip
```
- **2.37.0 link (known-public, the ET default):**
  `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.37.0.250724/v2.37.0.250724.zip`
- **2.46.0 link (what we actually need):**
  `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.46.0.<stamp>/v2.46.0.<stamp>.zip`
  The full version string is `2.46.0.<buildstamp>`. The model's embedded id is
  `2.46.0.260424121129`; the public Community zip uses a `2.46.0.<YYMMDD…>` stamp
  that you must confirm on the page (the 2.37 analog was `.250724`). **major.minor
  (2.46) is the hard requirement; the buildstamp may differ slightly.**

  Result this session: **NOT EXECUTABLE HERE** (network denied). Run:
  ```bash
  # WSL Ubuntu (x86_64) — exactly how ET fetches it, no login:
  VER=2.46.0.<stamp>            # confirm the stamp first (see route B)
  mkdir -p ~/qcom/qairt && cd /tmp
  curl --fail -L -o v$VER.zip \
    "https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/$VER/v$VER.zip"
  file v$VER.zip && head -c 64 v$VER.zip | xxd | head   # MUST start 'PK' (50 4B); NOT '<html'
  unzip -tq v$VER.zip                                    # integrity check
  unzip -q v$VER.zip -d /tmp                             # yields /tmp/qairt/<VER>/
  mv /tmp/qairt/$VER ~/qcom/qairt/2.46.0
  ```
  VERIFY it is the real SDK, not an HTML login/redirect: `file` says `Zip archive`,
  size is hundreds of MB, `head -c 2` is `PK`. If you get a tiny file or HTML, the
  exact stamp is wrong or that version isn't on the Community channel → use route B.

### Route A′ — Let ExecuTorch's own downloader do it (scriptable, no login)
For the **2.37.0** default this is fully automatic on Linux x86_64:
```bash
cd ~/executorch
python backends/qualcomm/scripts/download_qnn_sdk.py --print-sdk-path
# -> downloads to ~/.cache/executorch/qnn/sdk-2.37.0.250724/ and prints the path
```
To make the *same* machinery fetch **2.46.0**, edit two lines in
`backends/qualcomm/scripts/qnn_config.sh` before running:
```
QNN_VERSION="2.46.0.<stamp>"
# QNN_ZIP_URL template already interpolates ${QNN_VERSION}; leave it as-is
```
then `python backends/qualcomm/scripts/download_qnn_sdk.py --print-sdk-path`.
This reuses ET's retry/resume + zip-integrity validation. Result this session:
**NOT EXECUTABLE HERE** (shell denied).

### Route B — Confirm the exact 2.46.0 stamp / portal (free Qualcomm ID, browser)
To discover the precise `2.46.0.<stamp>` for the Community zip, or if 2.46.0 is not
on the Community channel:
- Portal: **https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk**
  → "Get Software" (lists all QAIRT versions; pick 2.46.0; note the build stamp).
- Or **Qualcomm Package Manager (QPM3) — https://qpm.qualcomm.com** (free Qualcomm
  ID, no NDA). Package: **Qualcomm AI Engine Direct SDK / QAIRT**, version **2.46.0**.
  Result this session: **NOT EXECUTABLE HERE** (WebFetch/WebSearch denied).

### Route C — Docker / pip / conda / GitHub mirror
- pip/conda: there is **no** official pip/conda wheel that ships the full QAIRT
  runtime + `hexagon-v79` skels. (pip's `qai-hub` etc. are clients, not the SDK.)
  Not a viable source for the v79 device libs. **Not pursued further.**
- Docker: community images bundling QAIRT exist but are unverified provenance and
  typically lag versions; not trustworthy for an exact 2.46.0 + v79 match.
  **Not pursued** (could not probe — network denied).
- GitHub mirrors of the SDK binaries: license-restricted; not a sanctioned route.
  **Not pursued.**
  → Route A (Community zip) is the correct non-interactive source; B is the
  authenticated confirm/fallback.

---

## 3. Authenticated fallback (only if Community zip for 2.46.0 is unavailable)

A free **Qualcomm developer account (Qualcomm ID)** — **no NDA** for the Community
/ AI Engine Direct SDK.

1. Sign in at **https://qpm.qualcomm.com** (QPM3) or the SDK page in route B.
2. Select **Qualcomm AI Engine Direct SDK (QAIRT)**, version **2.46.0**, platform
   **Linux x86_64** (so it runs in WSL2). Artifact is a `.zip` (or QPM-managed
   install named like `qualcomm_ai_engine_direct.2.46.0.<stamp>`).
3. `qpm-cli` CAN script this once credentials exist:
   ```bash
   qpm-cli --login <qualcomm-id>
   qpm-cli --download qualcomm_ai_engine_direct --version 2.46.0
   qpm-cli --extract  qualcomm_ai_engine_direct.2.46.0.<stamp>
   ```
4. Extract so you end up with `~/qcom/qairt/2.46.0/` (the dir that contains
   `bin/ include/ lib/ sdk.yaml`).

---

## 4. Install location + QNN_SDK_ROOT + verification

Target layout (matches `SDK-ACQUISITION.md` and `03-pull-qnn-libs.ps1` fallbacks):
```bash
~/qcom/qairt/2.46.0/          # this dir is QNN_SDK_ROOT
export QNN_SDK_ROOT="$HOME/qcom/qairt/2.46.0"   # add to ~/.bashrc
```
**Set `QNN_SDK_ROOT` before building** so ExecuTorch does NOT silently auto-fetch
2.37 (`backends/qualcomm/scripts/build.sh` honors an existing `QNN_SDK_ROOT`).

CONFIRM it is the right SDK for SM8750 / Hexagon v79 — these must all exist:
```bash
cat  $QNN_SDK_ROOT/sdk.yaml                                   # version: 2.46.0.xxxxx
ls   $QNN_SDK_ROOT/include/QNN/QnnCommon.h                    # headers for the build
ls   $QNN_SDK_ROOT/lib/x86_64-linux-clang/libQnnHtp.so        # host build lib (WSL)
ls   $QNN_SDK_ROOT/lib/x86_64-linux-clang/libQnnSystem.so
ls   $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so           # device libs
ls   $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV79Stub.so
ls   $QNN_SDK_ROOT/lib/aarch64-android/libQnnSystem.so
ls   $QNN_SDK_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so   # MANDATORY for v79
grep -E "QNN_API_VERSION_(MAJOR|MINOR)" $QNN_SDK_ROOT/include/QNN/QnnCommon.h
strings $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so | grep -E "2\.46\.0"
```
The `hexagon-v79/unsigned/libQnnHtpV79Skel.so` from the SDK is the same skel
`03-pull-qnn-libs.ps1` tries to pull from the phone — the SDK copy is the reliable
source (retail S25 often blocks the on-device pull).

---

## 5. Summary

- **SDK obtained:** **NO** (sandbox denied all shell + network tools this run; no
  QAIRT SDK currently on disk).
- **Pinned version:** **QAIRT/QNN 2.46.0** (`2.46.0.260424121129`), evidence in
  `VERSION-MATCH.md` §A1/§A3 + `qnn_config.sh`. (2.37.0 is only ET's build default
  and is too old to *run* these .pte.)
- **Best route (no login):** route A — public Community zip
  `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.46.0.<stamp>/v2.46.0.<stamp>.zip`
  (confirm `<stamp>` via route B), or drive ET's own
  `download_qnn_sdk.py` after bumping `qnn_config.sh` to 2.46.
- **Fallback (free account, no NDA):** QPM3 at qpm.qualcomm.com → QAIRT 2.46.0
  Linux x86_64; `qpm-cli` can script it.
- **Land it at:** `~/qcom/qairt/2.46.0`, `export QNN_SDK_ROOT=$HOME/qcom/qairt/2.46.0`,
  then run the §4 verification (must include `lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`).
