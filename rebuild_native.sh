#!/usr/bin/env bash
set -eo pipefail
cp /mnt/c/AI/Projects/ElectricSafe/jni_layer_qnn_multimodal.cpp /home/aliam/executorch/extension/android/jni/jni_layer_qnn_multimodal.cpp
sed -i "s/\r$//" /home/aliam/executorch/extension/android/jni/jni_layer_qnn_multimodal.cpp
echo "=== incremental native build ==="
bash /mnt/c/AI/Projects/ElectricSafe/build_native.sh
echo "=== strip + stage ==="
bash /mnt/c/AI/Projects/ElectricSafe/stage_libs.sh
echo "=== REBUILD_NATIVE_DONE ==="
