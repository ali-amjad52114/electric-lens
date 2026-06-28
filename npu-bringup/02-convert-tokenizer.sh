#!/usr/bin/env bash
# =============================================================================
# 02-convert-tokenizer.sh  —  Convert tokenizer.json -> tokenizer.bin
#
# The ExecuTorch llama runner consumes a serialized `tokenizer.bin`, not the
# HuggingFace `tokenizer.json`. This converts it using the pytorch_tokenizers
# tooling that ships in the ExecuTorch tree, and writes tokenizer.bin NEXT TO
# the model so 04-run-smolvlm.sh finds it automatically.
#
# Run from WSL (after 01-wsl-setup.sh):  bash ./02-convert-tokenizer.sh
# =============================================================================
set -euo pipefail

# ---- Config (real paths) ---------------------------------------------------
MODEL_DIR="/mnt/c/AI/Projects/ElectricSafe/Electric-lens/Model/smolvlm_500m_instruct_ctxt-4096_SM8750"
TOK_JSON="$MODEL_DIR/tokenizer.json"
TOK_BIN="$MODEL_DIR/tokenizer.bin"
ET_DST="$HOME/executorch"
VENV="$ET_DST/.venv"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m    OK: %s\033[0m\n' "$*"; }
warn(){ printf '\033[1;33m    WARN: %s\033[0m\n' "$*"; }

# ---- Sanity ----------------------------------------------------------------
say "Sanity checks"
[[ -f "$TOK_JSON" ]] || { echo "ERROR: tokenizer.json not found at $TOK_JSON" >&2; exit 1; }
ok "Found $TOK_JSON"

if [[ -d "$VENV" ]]; then
  # shellcheck disable=SC1091
  source "$VENV/bin/activate"
  ok "Activated venv $VENV"
else
  warn "venv not found at $VENV — using current python. Run 01-wsl-setup.sh first if this fails."
fi

if [[ -f "$TOK_BIN" ]]; then
  warn "tokenizer.bin already exists at $TOK_BIN — it will be overwritten."
fi

# ---- Conversion: try methods in order, stop at first success ----------------
converted=0

# Method 1: pytorch_tokenizers Python API (preferred; handles HF JSON).
say "Method 1: pytorch_tokenizers (HF tokenizer.json -> tokenizer.bin)"
if python - "$TOK_JSON" "$TOK_BIN" <<'PY'
import sys
src, dst = sys.argv[1], sys.argv[2]
try:
    # ExecuTorch ships pytorch_tokenizers; the HF-JSON tokenizer can serialize
    # to the runner's binary format.
    from pytorch_tokenizers.hf_tokenizer import HuggingFaceTokenizer  # type: ignore
    tok = HuggingFaceTokenizer(src)
    # Newer API: .save / .export_to_bin ; try both names.
    if hasattr(tok, "save"):
        tok.save(dst)
    elif hasattr(tok, "export_to_bin"):
        tok.export_to_bin(dst)
    else:
        raise AttributeError("no save/export_to_bin on HuggingFaceTokenizer")
    print("    wrote", dst)
    sys.exit(0)
except Exception as e:
    print("    method1 failed:", repr(e), file=sys.stderr)
    sys.exit(7)
PY
then
  converted=1
  ok "Converted via pytorch_tokenizers"
else
  warn "Method 1 unavailable/failed — trying fallbacks"
fi

# Method 2: ExecuTorch tokenizer util module (CLI form), if present.
if [[ "$converted" -eq 0 ]]; then
  say "Method 2: python -m pytorch_tokenizers.tools.* / extension tokenizer tools"
  for mod in \
      "pytorch_tokenizers.tools.llama2c.convert" \
      "extension.llm.tokenizer.tokenizer" \
      "examples.models.llama.tokenizer.tokenizer" ; do
    if python -c "import importlib,sys; importlib.import_module('$mod')" 2>/dev/null; then
      if python -m "$mod" -t "$TOK_JSON" -o "$TOK_BIN" 2>/dev/null \
         || python -m "$mod" "$TOK_JSON" "$TOK_BIN" 2>/dev/null; then
        converted=1; ok "Converted via $mod"; break
      fi
    fi
  done
fi

# Method 3: locate a converter script in the ET tree and run it.
if [[ "$converted" -eq 0 ]]; then
  say "Method 3: searching ExecuTorch tree for a tokenizer converter script"
  cand="$(grep -rIl --include='*.py' -e 'tokenizer.bin' -e 'export_to_bin' \
           "$ET_DST/extension" "$ET_DST/examples" 2>/dev/null | head -1 || true)"
  if [[ -n "${cand:-}" ]]; then
    warn "Found candidate: $cand"
    warn "Auto-run not attempted (arg shape varies). Inspect it and convert manually, e.g.:"
    echo "    python \"$cand\" --tokenizer \"$TOK_JSON\" --output \"$TOK_BIN\""
  else
    warn "No converter script found in the ET tree."
  fi
fi

# ---- Verify ----------------------------------------------------------------
say "Verify"
if [[ -s "$TOK_BIN" ]]; then
  ok "tokenizer.bin present: $TOK_BIN ($(stat -c%s "$TOK_BIN") bytes)"
  echo "    The run script (04-run-smolvlm.sh) will pick this up automatically."
else
  echo "ERROR: tokenizer.bin was not produced." >&2
  echo "       The runner you build may accept tokenizer.json directly — if so," >&2
  echo "       04-run-smolvlm.sh will fall back to tokenizer.json. Otherwise convert" >&2
  echo "       manually using the candidate script printed above." >&2
  exit 1
fi
