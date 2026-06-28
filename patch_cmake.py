#!/usr/bin/env python3
import re, sys, io

p = "/home/aliam/executorch/extension/android/CMakeLists.txt"
s = open(p, encoding="utf-8").read()

marker = "examples/qualcomm/oss_scripts/llama/runner/kv_manager.cpp"
if "multimodal_runner/multimodal_runner.cpp" in s:
    print("ALREADY PATCHED (multimodal sources present)")
    sys.exit(0)
if marker not in s:
    print("ERROR: marker line not found"); sys.exit(1)

mm = [
    "multimodal_runner/multimodal_runner.cpp",
    "multimodal_runner/encoder.cpp",
    "multimodal_runner/tok_embedding_runner.cpp",
    "multimodal_runner/tok_embedding_processor.cpp",
    "multimodal_runner/multimodal_prompt_processor.cpp",
    "multimodal_runner/multimodal_token_generator.cpp",
    "multimodal_runner/multimodal_lhd_token_generator.cpp",
    "multimodal_runner/multimodal_embedding_merger.cpp",
]
prefix = "        ${EXECUTORCH_ROOT}/examples/qualcomm/oss_scripts/llama/runner/"
ins = "".join(prefix + m + "\n" for m in mm)

# insert after the kv_manager.cpp source line (keep that line)
line = prefix + "kv_manager.cpp\n"
if line not in s:
    # fall back: match by marker with any leading whitespace
    idx = s.index(marker)
    eol = s.index("\n", idx) + 1
    s = s[:eol] + ins + s[eol:]
else:
    s = s.replace(line, line + ins, 1)

open(p, "w", encoding="utf-8").write(s)
print("PATCHED: added 8 multimodal_runner sources after kv_manager.cpp")
