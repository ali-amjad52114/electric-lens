# SmolVLM-on-NPU Bring-Up Runbook

Run a pre-compiled Qualcomm-QNN **SmolVLM-500M** model on a connected
**Samsung Galaxy S25 Ultra** (SoC **SM8750**, Hexagon **v79**) using ExecuTorch's
**host-driven** flow (`examples/qualcomm/oss_scripts/llama/llama.py --pre_gen_pte`).

The model is already compiled to 3 `.pte` files. We are **not** re-exporting; we are
only running the pre-generated graphs on-device, driven from the WSL2 host.

---

## Ground truth (the real values this kit is wired to)

| Thing                       | Value                                                                                                  |
| --------------------------- | ------------------------------------------------------------------------------------------------------ |
| ExecuTorch source (Windows) | `C:\AI\Projects\ElectricSafe\executorch`                                                                |
| ExecuTorch source (WSL view)| `/mnt/c/AI/Projects/ElectricSafe/executorch`                                                            |
| ExecuTorch build dir (WSL)  | `~/executorch` (copied to native fs for build speed — do NOT build on `/mnt/c`)                         |
| Model dir (Windows)         | `C:\AI\Projects\ElectricSafe\Electric-lens\Model\smolvlm_500m_instruct_ctxt-4096_SM8750\`              |
| Model dir (WSL view)        | `/mnt/c/AI/Projects/ElectricSafe/Electric-lens/Model/smolvlm_500m_instruct_ctxt-4096_SM8750`            |
| 3 PTE files                 | `vision_encoder_qnn.pte`, `tok_embedding_qnn.pte`, `hybrid_llama_qnn.pte`                               |
| Tokenizer source            | `tokenizer.json` (+ `tokenizer_config.json`, `chat_template.jinja`)                                     |
| WSL user / home             | `aliam` / `/home/aliam`                                                                                 |
| QNN AAR                     | `C:\AI\Projects\ElectricSafe\_qnn_aar\executorch-qnn.aar`                                               |
| Device-pulled QNN libs      | `C:\AI\Projects\ElectricSafe\relaysight-android\app\src\main\jniLibs\arm64-v8a\`                        |
| adb serial                  | `R3CXC0804NZ`                                                                                           |
| SoC                         | `SM8750`                                                                                                |
| adb.exe                     | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`                                                     |
| QNN SDK (QAIRT) install dir | `~/qcom/qairt/<version>` in WSL — `QNN_SDK_ROOT` points here. Version: see `VERSION-MATCH.md`           |

---

## ☐ BLOCKED-UNTIL markers — clear these two before Phase 5

> **BLOCKER A — phone authorized.** `adb` currently reports the device as
> `unauthorized`. On the phone: re-plug USB, tap **Allow** on the "Allow USB
> debugging?" dialog (check "Always allow from this computer"). Confirm with
> `adb devices` showing `R3CXC0804NZ   device` (not `unauthorized`).
>
> **BLOCKER B — QNN SDK installed.** The Qualcomm AI Engine Direct (QAIRT) SDK is
> not yet installed. See `SDK-ACQUISITION.md` to download it and `VERSION-MATCH.md`
> for the exact version, then unzip to `~/qcom/qairt/<version>` and set
> `QNN_SDK_ROOT`.

Phases 0–3 and the lib-staging part of Phase 4 can be done **before** the blockers
clear. Phases that require the device (`03-pull-qnn-libs.ps1`, Phase 5+) need
BLOCKER A. Building/running needs BLOCKER B.

---

## Phase 0 — Prerequisites & blocker check

1. **Windows side:** confirm adb sees the phase.
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   ```
   Expect: `R3CXC0804NZ   device`. If `unauthorized` → **BLOCKER A** (above).
   If empty → re-plug cable / try `adb kill-server; adb start-server`.

2. **WSL side:** confirm WSL2 Ubuntu is up and you are user `aliam`.
   ```bash
   whoami            # aliam
   uname -a          # Linux ... WSL2
   ```

3. Confirm the model files are visible from WSL:
   ```bash
   ls -la /mnt/c/AI/Projects/ElectricSafe/Electric-lens/Model/smolvlm_500m_instruct_ctxt-4096_SM8750/
   ```
   Expect the 3 `.pte` + `tokenizer.json` + configs.

---

## Phase 1 — WSL environment setup  ▶ `01-wsl-setup.sh`

Installs build deps, copies ExecuTorch into the WSL native fs, creates a venv, and
installs ExecuTorch + the llama example requirements.

```bash
cd /mnt/c/AI/Projects/ElectricSafe/npu-bringup
bash ./01-wsl-setup.sh
```

What it does (and why):
- `apt install` **cmake clang ninja-build build-essential** (+ git, python venv).
  Uses `sudo` — you will be prompted for your WSL password.
- **Copies** `/mnt/c/.../executorch` → `~/executorch`. Building under `/mnt/c`
  (the Windows 9p mount) is extremely slow; the native ext4 home is far faster.
- Creates venv at `~/executorch/.venv` and `pip install ./` (editable build of the
  ExecuTorch Python package) + runs
  `examples/models/llama/install_requirements.sh`.

> NOTE: The on-device runtime `.so`s we need (`libexecutorch.so`,
> `libqnn_executorch_backend.so`) come from the **AAR** — see Phase 4 /
> `assemble-runtime-libs.ps1`. Phase 1 is the **host** Python/CMake toolchain that
> `llama.py` needs to build the on-device runner and orchestrate the run.

---

## Phase 2 — Install QNN SDK & set QNN_SDK_ROOT   ☐ BLOCKER B

See `SDK-ACQUISITION.md` (what to download, account needed) and `VERSION-MATCH.md`
(exact version — must match the QNN libs we already have so backend ABI lines up).

After unzipping the SDK to `~/qcom/qairt/<version>`:

```bash
# Put this in ~/.bashrc so every shell (and 04-run-smolvlm.sh) sees it.
export QNN_SDK_ROOT="$HOME/qcom/qairt/<version>"     # <-- replace <version>
# Some ET scripts also read these aliases:
export QAIRT_SDK_ROOT="$QNN_SDK_ROOT"
source "$QNN_SDK_ROOT/bin/envsetup.sh"   # if present in this SDK build
echo "$QNN_SDK_ROOT"
test -d "$QNN_SDK_ROOT/lib/aarch64-android" && echo "device libs OK"
test -d "$QNN_SDK_ROOT/lib/x86_64-linux-clang" && echo "host libs OK"
```

Sanity: the SDK must contain `lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`
(the matching DSP skel). That is the same file we also try to pull from the device
in Phase 4 — having both lets you cross-check the version.

---

## Phase 3 — Convert tokenizer.json → tokenizer.bin   ▶ `02-convert-tokenizer.sh`

The ExecuTorch llama runner consumes a **`tokenizer.bin`** (a serialized tokenizer),
not `tokenizer.json` directly. Convert it with the `pytorch_tokenizers` tooling that
ships in the ExecuTorch tree.

```bash
cd /mnt/c/AI/Projects/ElectricSafe/npu-bringup
bash ./02-convert-tokenizer.sh
```

Writes `tokenizer.bin` **next to the model** so the run script finds it
automatically. The script tries, in order:
1. `pytorch_tokenizers` Python API (HuggingFace JSON → `.bin`), then
2. the legacy `tokenizer.py` / `tokenizer_util` converters in the ET tree as a
   fallback.

> If the runner version you build actually accepts `tokenizer.json` directly, this
> step is harmless — `04-run-smolvlm.sh` prefers `tokenizer.bin` if present and
> falls back to `tokenizer.json`.

---

## Phase 4 — Stage runtime libs & pull the missing DSP skel

### 4a. Stage the libs we already have  ▶ `assemble-runtime-libs.ps1` (Windows)

```powershell
cd C:\AI\Projects\ElectricSafe\npu-bringup
powershell -ExecutionPolicy Bypass -File .\assemble-runtime-libs.ps1
```
- Extracts `libexecutorch.so` + `libqnn_executorch_backend.so` from
  `executorch-qnn.aar` (it is a zip).
- Copies the 3 device libs from the relaysight `jniLibs` dir.
- Lays everything out in `npu-bringup\runtime-libs\arm64-v8a\`.
- Prints a **present / MISSING** table. `libQnnHtpV79Skel.so` will show MISSING
  until 4b.

### 4b. Pull `libQnnHtpV79Skel.so` from the phone   ☐ BLOCKER A   ▶ `03-pull-qnn-libs.ps1`

```powershell
cd C:\AI\Projects\ElectricSafe\npu-bringup
powershell -ExecutionPolicy Bypass -File .\03-pull-qnn-libs.ps1
```
- Uses adb (serial `R3CXC0804NZ`) to **search `/vendor`** for `libQnnHtpV79Skel.so`
  (checks `/vendor/lib/rfsa/adsp/`, `/vendor/dsp/`, `/vendor/lib64/snap/`, then a
  broad `find`), and re-pulls the other 3 (`libQnnHtp.so`, `libQnnHtpV79Stub.so`,
  `libQnnSystem.so`).
- Stages into `runtime-libs\arm64-v8a\` and prints the present/missing table.
- If adb is `unauthorized` it exits with a clear **BLOCKER A** message.

> The skel often needs **root/vendor read access**. If `find` returns nothing as
> shell user, try `adb root` (may be denied on a retail S25) or pull from the QNN
> SDK instead: `~/qcom/qairt/<version>/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`.
> Either source is fine as long as the version matches `VERSION-MATCH.md`.

After 4b, **all 4 Qualcomm libs + the 2 ET libs must be present.** Required set:

```
libexecutorch.so              (from AAR — arm64-v8a, QnnBackend built in)
libqnn_executorch_backend.so  (from AAR)
libQnnHtp.so                  (device / SDK)
libQnnHtpV79Stub.so           (device / SDK)
libQnnSystem.so               (device / SDK)
libQnnHtpV79Skel.so           (DSP skel — Phase 4b)
```

---

## Phase 5 — Run SmolVLM on the NPU   ☐ BLOCKERS A+B   ▶ `04-run-smolvlm.sh`

```bash
cd /mnt/c/AI/Projects/ElectricSafe/npu-bringup
# Edit the vars at the top of the script (PROMPT / IMAGE / PTE_DIR) if needed,
# or override inline:
IMAGE=/mnt/c/AI/Projects/ElectricSafe/captures/frame.jpg \
PROMPT="Read the fault code on this VFD display. Reply with only the code." \
bash ./04-run-smolvlm.sh
```

It runs (real values baked in):
```bash
python examples/qualcomm/oss_scripts/llama/llama.py \
  --build_folder build-android \
  --device R3CXC0804NZ \
  --soc_model SM8750 \
  --decoder_model smolvlm_500m_instruct \
  --model_mode kv \
  --max_seq_len 1024 \
  --prompt "$PROMPT" \
  --image_path "$IMAGE" \
  --pre_gen_pte "$PTE_DIR"
```

- `--pre_gen_pte` points at the folder holding the 3 `.pte` files, so **no
  re-export** happens — llama.py builds/pushes the on-device runner, pushes the
  pte + tokenizer + QNN libs, runs, and pulls back the output.
- `--model_mode kv` (not `hybrid`) keeps **host** memory down (see pitfall (b)).
- The script checks `QNN_SDK_ROOT` is set and the libs are staged before running.

---

## Phase 6 — Read / interpret output & confirm it ran on HTP/NPU

1. **Generated text** is printed at the end of the run and also written to the
   ET output dir (look for the runner's `outputs/` / `result.txt` under the build
   folder; the path is echoed by llama.py). Expect a short string — ideally just
   the fault code, per the prompt.

2. **Latency:** the runner prints prefill/decode timing and **tokens/sec**. Capture
   that line. Also note total wall-clock from the script.

3. **Confirm HTP/NPU execution (not CPU fallback).** While it runs, watch logcat:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s R3CXC0804NZ logcat -s ExecuTorch QnnExecuTorch QnnBackend
   ```
   Look for QNN delegate init lines, e.g.:
   - `QnnBackend ... Initialize` / `Backend build version ...`
   - `Running graph ... on HTP` / `Create HTP context`
   - `libQnnHtpV79Skel` / `Hexagon ... v79` mentions
   If you instead see "delegate init failed" or CPU-only ops, the run fell back —
   see Troubleshooting.

---

## Phase 7 — Troubleshooting (baked-in pitfalls)

**(a) `Failed to find available PD ... context size estimate 4...GB` / HTP 4GB
per-context limit.** A single HTP context cannot exceed ~4 GB. **Fix:** shard the
graph across more contexts — add `--num_sharding N` (start with `2`, raise to `4`)
to the `04-run-smolvlm.sh` command. More shards = more, smaller contexts.

**(b) Host OOM / very slow / killed during run.** `hybrid` mode is memory-heavy on
the **host**. We already use `--model_mode kv` to reduce that. If still heavy,
lower `--max_seq_len` (e.g. 512) and close other apps. Do not switch to `hybrid`
unless you have the host RAM.

**(c) Tokenizer errors / garbage tokens.** The runner expects `tokenizer.bin`. If
you see "failed to load tokenizer" or nonsense output, re-run
`02-convert-tokenizer.sh` and confirm `tokenizer.bin` sits next to the pte files.

**(d) On-device "cannot load libQnn..." / "dlopen failed" / delegate init fails.**
The device loader must find the QNN `.so`s. Ensure both
`LD_LIBRARY_PATH` **and** `ADSP_LIBRARY_PATH` include the on-device dir that holds
the pushed QNN libs (llama.py pushes them to a working dir under
`/data/local/tmp/...`). The skel (`libQnnHtpV79Skel.so`) specifically must be
reachable via `ADSP_LIBRARY_PATH` for the DSP. If llama.py doesn't set these,
export them in the adb shell wrapper or pass them via the runner args.

**(e) `adb unauthorized`.** BLOCKER A — re-accept the USB-debugging prompt on the
phone (`Always allow`). Then `adb kill-server && adb start-server`.

**(f) Version mismatch (delegate init OK but wrong-version warnings / crash).** The
QNN SDK version, the AAR's backend, and the device skel must be ABI-compatible.
Cross-check against `VERSION-MATCH.md`. Prefer pulling the skel from the matching
SDK if the device skel version differs.

**(g) WSL ↔ device over USB.** `llama.py` shells out to `adb`. WSL2 can either call
**Windows adb** (`adb.exe` on PATH, simplest) or bind the USB device into WSL with
**usbipd-win** (`usbipd attach --wsl --busid <id>`) so Linux adb sees it. The
simplest path: ensure Windows `platform-tools` is on the WSL PATH so `adb` resolves
to `adb.exe`. Document for the operator:
   - **Windows adb from WSL:** add to `~/.bashrc`:
     `export PATH="$PATH:/mnt/c/Users/aliam/AppData/Local/Android/Sdk/platform-tools"`
     then `adb.exe devices` works; many ET scripts call `adb` (no `.exe`) — symlink
     or alias `adb=adb.exe` if needed.
   - **usbipd (native Linux adb):** in an admin PowerShell:
     `usbipd list` → find the phone busid → `usbipd bind --busid <id>` →
     `usbipd attach --wsl --busid <id>`; then install `adb` inside WSL.
