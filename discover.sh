#!/usr/bin/env bash
set +e
echo "===== HOME ====="; echo "$HOME"
echo "===== executorch tree ====="
ls -d ~/executorch 2>/dev/null && (cd ~/executorch && git describe --tags 2>/dev/null; git rev-parse --short HEAD 2>/dev/null)
echo "===== multimodal_runner sources present? ====="
ls -1 ~/executorch/examples/qualcomm/oss_scripts/llama/runner/multimodal_runner/ 2>/dev/null
echo "===== QNN SDK (QAIRT) candidates ====="
find ~ -maxdepth 6 -iname 'libQnnHtp.so' 2>/dev/null | head -20
echo "--- QNN_SDK_ROOT env ---"; echo "${QNN_SDK_ROOT:-<unset>}"
echo "===== Android NDK candidates ====="
find ~ -maxdepth 5 -type d -iname 'ndk*' 2>/dev/null | head -10
echo "--- ANDROID_NDK envs ---"; echo "ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-<unset>}  ANDROID_NDK=${ANDROID_NDK:-<unset>}  ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-<unset>}"
echo "===== conda envs ====="
conda env list 2>/dev/null
echo "===== prior CLI runner artifact ====="
ls -la ~/executorch/build-android/examples/qualcomm/oss_scripts/llama/qnn_multimodal_runner 2>/dev/null
echo "===== android build scripts ====="
ls -1 ~/executorch/scripts/build_android_library.sh 2>/dev/null
ls -1 ~/executorch/extension/android/ 2>/dev/null
echo "===== existing AAR(s) ====="
find ~/executorch -iname '*.aar' 2>/dev/null | head -20
echo "===== DONE ====="
