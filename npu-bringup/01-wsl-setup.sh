#!/usr/bin/env bash
# =============================================================================
# 01-wsl-setup.sh  —  WSL2 Ubuntu host setup for the SmolVLM NPU bring-up.
#
# Installs build deps, copies the ExecuTorch source into the WSL native fs
# (~/executorch — building on /mnt/c is painfully slow), creates a Python venv,
# and installs ExecuTorch + the llama example requirements.
#
# Run from WSL:  bash ./01-wsl-setup.sh
# Idempotent where practical. Prompts for sudo password for apt.
# =============================================================================
set -euo pipefail

# ---- Config (real ground-truth paths) --------------------------------------
ET_SRC_WIN="/mnt/c/AI/Projects/ElectricSafe/executorch"   # Windows clone (9p mount)
ET_DST="$HOME/executorch"                                  # native fs build copy
VENV="$ET_DST/.venv"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m    OK: %s\033[0m\n' "$*"; }
warn(){ printf '\033[1;33m    WARN: %s\033[0m\n' "$*"; }

# ---- 0. Sanity -------------------------------------------------------------
say "Sanity checks"
if [[ ! -d "$ET_SRC_WIN" ]]; then
  echo "ERROR: ExecuTorch source not found at $ET_SRC_WIN" >&2
  exit 1
fi
ok "Found ExecuTorch source at $ET_SRC_WIN"

# ---- 1. Build dependencies (apt) -------------------------------------------
say "Installing build dependencies (sudo apt) — you may be prompted for your password"
sudo apt-get update
sudo apt-get install -y \
  build-essential \
  cmake \
  clang \
  ninja-build \
  git \
  python3 \
  python3-venv \
  python3-pip \
  rsync \
  unzip \
  ca-certificates
ok "Build deps installed"
echo "    cmake:  $(cmake --version | head -1)"
echo "    clang:  $(clang --version | head -1)"
echo "    ninja:  $(ninja --version)"
echo "    python: $(python3 --version)"

# ---- 2. Copy ExecuTorch source to native fs --------------------------------
# rsync so re-runs are incremental; --delete keeps the copy faithful to source.
say "Copying ExecuTorch source -> $ET_DST (native ext4 — much faster builds)"
mkdir -p "$ET_DST"
rsync -a --delete \
  --exclude '.git/' \
  --exclude '.venv/' \
  --exclude 'build*/' \
  --exclude 'cmake-out*/' \
  "$ET_SRC_WIN/" "$ET_DST/"
ok "Source synced to $ET_DST"

# Submodules: the Windows clone may not have them populated on the Linux side.
# If this is a git checkout with submodules, initialise them in the native copy.
if [[ -d "$ET_DST/.git" ]] || [[ -f "$ET_DST/.gitmodules" ]]; then
  say "Initialising git submodules in $ET_DST (this can take a while)"
  ( cd "$ET_DST" && git submodule sync --recursive && git submodule update --init --recursive ) \
    || warn "Submodule update failed — if the source was copied without .git, run install_requirements differently or re-clone in WSL."
else
  warn "No .git/.gitmodules in copy — assuming source already vendored. ExecuTorch builds usually NEED submodules; if pip install fails, re-clone in WSL with --recursive."
fi

# ---- 3. Python venv --------------------------------------------------------
say "Creating Python venv at $VENV"
if [[ ! -d "$VENV" ]]; then
  python3 -m venv "$VENV"
  ok "venv created"
else
  ok "venv already exists"
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"
python -m pip install --upgrade pip setuptools wheel
ok "pip toolchain upgraded"

# ---- 4. Install ExecuTorch + llama requirements ----------------------------
say "Installing ExecuTorch (editable) — pip install ./"
cd "$ET_DST"
# ExecuTorch's own installer is the supported path; fall back to plain pip if absent.
if [[ -f "./install_requirements.sh" ]]; then
  bash ./install_requirements.sh || warn "install_requirements.sh returned non-zero; continuing to pip install ./"
fi
pip install ./
ok "ExecuTorch python package installed"

say "Installing llama example requirements"
LLAMA_REQ="$ET_DST/examples/models/llama/install_requirements.sh"
if [[ -f "$LLAMA_REQ" ]]; then
  bash "$LLAMA_REQ"
  ok "llama requirements installed"
else
  warn "Not found: $LLAMA_REQ — check ExecuTorch version/layout."
fi

# ---- Done ------------------------------------------------------------------
say "Phase 1 complete"
cat <<EOF
    ExecuTorch build copy : $ET_DST
    Python venv           : $VENV   (activate with: source "$VENV/bin/activate")
    Next:
      - Phase 2: install QNN SDK + export QNN_SDK_ROOT (see RUNBOOK.md / SDK-ACQUISITION.md)
      - Phase 3: bash ./02-convert-tokenizer.sh
EOF
