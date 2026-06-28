#!/usr/bin/env bash
set -eo pipefail
source ~/miniconda3/etc/profile.d/conda.sh
conda activate et

export ANDROID_NDK="$HOME/android-ndk-r27c"
export ANDROID_NDK_ROOT="$ANDROID_NDK"
export QNN_SDK_ROOT="/home/aliam/qcom/qairt/qairt/2.46.0.260424"
export ANDROID_ABIS="arm64-v8a"
export PYTHON_EXECUTABLE="python"
export EXECUTORCH_BUILD_EXTENSION_LLM=ON
export EXECUTORCH_CMAKE_BUILD_TYPE=Release

cd "$HOME/executorch"
echo "=== env ==="
echo "NDK=$ANDROID_NDK  QNN=$QNN_SDK_ROOT  py=$(which python)"
python --version

# Pull in the helper functions without running main()
source scripts/build_android_library.sh

mkdir -p cmake-out-android-so/
echo "=== START native build $(date) ==="
build_android_native_library arm64-v8a
echo "=== DONE native build $(date) ==="
echo "=== staged so files ==="
ls -la cmake-out-android-so/arm64-v8a/ || true
echo "=== jni so in cmake-out ==="
ls -la cmake-out-android-arm64-v8a/extension/android/*.so || true
