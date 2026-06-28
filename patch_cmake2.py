#!/usr/bin/env python3
import sys
p = "/home/aliam/executorch/extension/android/CMakeLists.txt"
s = open(p, encoding="utf-8").read()
if "jni/jni_layer_qnn_multimodal.cpp" in s:
    print("ALREADY PATCHED (jni_layer_qnn_multimodal.cpp present)"); sys.exit(0)
anchor = "${EXECUTORCH_ROOT}/examples/qualcomm/oss_scripts/llama/runner/multimodal_runner/multimodal_embedding_merger.cpp\n"
if anchor not in s:
    print("ERROR: anchor not found"); sys.exit(1)
add = "        ${CMAKE_CURRENT_SOURCE_DIR}/jni/jni_layer_qnn_multimodal.cpp\n"
s = s.replace(anchor, anchor + add, 1)
open(p, "w", encoding="utf-8").write(s)
print("PATCHED: added jni_layer_qnn_multimodal.cpp to QNN target_sources")
