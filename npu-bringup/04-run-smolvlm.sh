#!/usr/bin/env bash
# =============================================================================
# 04-run-smolvlm.sh  —  Host-driven run of the pre-compiled SmolVLM QNN model.
#
# Drives examples/qualcomm/oss_scripts/llama/llama.py with --pre_gen_pte so the
# 3 pre-compiled .pte graphs run on the device HTP/NPU; NO re-export happens.
#
# Requires (clear these first — see RUNBOOK.md):
#   * BLOCKER A: phone authorized for adb (serial R3CXC0804NZ -> "device")
#   * BLOCKER B: QNN SDK installed and QNN_SDK_ROOT exported (Phase 2)
#
# Run from WSL:  bash ./04-run-smolvlm.sh
# Override vars inline, e.g.:
#   IMAGE=/mnt/c/.../frame.jpg PROMPT="..." bash ./04-run-smolvlm.sh
# =============================================================================
set -euo pipefail

# ---- Tunable vars (override via env) ----------------------------------------
PTE_DIR="${PTE_DIR:-/mnt/c/AI/Projects/ElectricSafe/Electric-lens/Model/smolvlm_500m_instruct_ctxt-4096_SM8750}"
IMAGE="${IMAGE:-/mnt/c/AI/Projects/ElectricSafe/captures/frame.jpg}"
PROMPT="${PROMPT:-Read the fault code on this VFD display. Reply with only the code.}"

# ---- Fixed ground-truth values ----------------------------------------------
ET_DST="${ET_DST:-$HOME/executorch}"
VENV="$ET_DST/.venv"
DEVICE="R3CXC0804NZ"
SOC="SM8750"
DECODER="smolvlm_500m_instruct"
MODEL_MODE="kv"          # kv keeps HOST memory down vs hybrid (pitfall b)
MAX_SEQ_LEN="1024"
BUILD_FOLDER="build-android"
# Optional sharding for HTP 4GB-per-context limit (pitfall a). Empty = off.
NUM_SHARDING="${NUM_SHARDING:-}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m    OK: %s\033[0m\n' "$*"; }
warn(){ printf '\033[1;33m    WARN: %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ---- Preflight checks -------------------------------------------------------
say "Preflight"

# QNN_SDK_ROOT (BLOCKER B)
[[ -n "${QNN_SDK_ROOT:-}" ]] || die "QNN_SDK_ROOT is not set. Install the QNN SDK (Phase 2) and 'export QNN_SDK_ROOT=~/qcom/qairt/<version>'. See VERSION-MATCH.md."
[[ -d "$QNN_SDK_ROOT" ]]    || die "QNN_SDK_ROOT='$QNN_SDK_ROOT' does not exist."
ok "QNN_SDK_ROOT=$QNN_SDK_ROOT"

# venv
if [[ -d "$VENV" ]]; then
  # shellcheck disable=SC1091
  source "$VENV/bin/activate"; ok "venv active: $VENV"
else
  warn "venv not found at $VENV — using current python (run 01-wsl-setup.sh if this fails)."
fi

# ExecuTorch + llama.py
LLAMA="$ET_DST/examples/qualcomm/oss_scripts/llama/llama.py"
[[ -f "$LLAMA" ]] || die "llama.py not found at $LLAMA (run 01-wsl-setup.sh)."
ok "Found $LLAMA"

# PTE files
[[ -d "$PTE_DIR" ]] || die "PTE_DIR not found: $PTE_DIR"
for f in vision_encoder_qnn.pte tok_embedding_qnn.pte hybrid_llama_qnn.pte; do
  [[ -f "$PTE_DIR/$f" ]] || die "Missing pre-compiled graph: $PTE_DIR/$f"
done
ok "All 3 .pte present in $PTE_DIR"

# Tokenizer: prefer tokenizer.bin (from 02-convert-tokenizer.sh), fall back to json
if [[ -f "$PTE_DIR/tokenizer.bin" ]]; then
  ok "tokenizer.bin present"
elif [[ -f "$PTE_DIR/tokenizer.json" ]]; then
  warn "tokenizer.bin missing; tokenizer.json present. Run 02-convert-tokenizer.sh if the runner needs .bin."
else
  die "No tokenizer.bin or tokenizer.json in $PTE_DIR"
fi

# Image
[[ -f "$IMAGE" ]] || die "Image not found: $IMAGE  (set IMAGE=/path/to/frame.jpg)"
ok "Image: $IMAGE"

# adb / device (BLOCKER A). Resolve adb (Linux adb or Windows adb.exe).
ADB="$(command -v adb || command -v adb.exe || true)"
if [[ -n "$ADB" ]]; then
  state="$("$ADB" -s "$DEVICE" get-state 2>/dev/null || true)"
  if [[ "$state" == "device" ]]; then
    ok "adb device $DEVICE online"
  else
    warn "adb did not report '$DEVICE' as 'device' (got: '${state:-none}'). If 'unauthorized', accept the USB-debugging prompt (BLOCKER A)."
  fi
else
  warn "adb not on PATH. Add Windows platform-tools to PATH or use usbipd (see RUNBOOK Phase 7g). llama.py needs adb."
fi

# ---- Run --------------------------------------------------------------------
say "Running SmolVLM (host-driven, pre-compiled pte) on device $DEVICE / $SOC"
cd "$ET_DST"

CMD=(python examples/qualcomm/oss_scripts/llama/llama.py
  --build_folder "$BUILD_FOLDER"
  --device "$DEVICE"
  --soc_model "$SOC"
  --decoder_model "$DECODER"
  --model_mode "$MODEL_MODE"
  --max_seq_len "$MAX_SEQ_LEN"
  --prompt "$PROMPT"
  --image_path "$IMAGE"
  --pre_gen_pte "$PTE_DIR")

if [[ -n "$NUM_SHARDING" ]]; then
  CMD+=(--num_sharding "$NUM_SHARDING")
  ok "Using --num_sharding $NUM_SHARDING (HTP 4GB-context mitigation)"
fi

echo "    + ${CMD[*]}"
START=$(date +%s)
"${CMD[@]}"
END=$(date +%s)

say "Done in $((END - START))s wall-clock"
cat <<EOF
    - Generated text + tokens/sec are printed above by the runner.
    - Confirm NPU/HTP execution via logcat (RUNBOOK Phase 6):
        adb -s $DEVICE logcat -s ExecuTorch QnnExecuTorch QnnBackend
      Look for QNN backend Initialize / "on HTP" / Hexagon v79 / libQnnHtpV79Skel.
    - If you hit "context size estimate ~4GB / Failed to find available PD":
        re-run with NUM_SHARDING=2 (or 4):  NUM_SHARDING=2 bash ./04-run-smolvlm.sh
EOF
