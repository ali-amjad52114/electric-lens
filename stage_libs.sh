#!/usr/bin/env bash
set -e
STRIP=/home/aliam/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
SO_DIR=/home/aliam/executorch/cmake-out-android-so/arm64-v8a
JNI=/mnt/c/AI/Projects/ElectricSafe/Electric-lens/app/src/main/jniLibs/arm64-v8a

echo "=== strip + stage libexecutorch.so ==="
cp "$SO_DIR/libexecutorch.so" /tmp/libexecutorch.so
"$STRIP" --strip-unneeded /tmp/libexecutorch.so
cp /tmp/libexecutorch.so "$JNI/libexecutorch.so"

echo "=== refresh libqnn_executorch_backend.so (freshly built) ==="
cp "$SO_DIR/libqnn_executorch_backend.so" "$JNI/libqnn_executorch_backend.so"

echo "=== remove stale CLI runner exec (not used in-app) ==="
rm -f "$JNI/libqnn_multimodal_runner.so"

echo "=== final jniLibs/arm64-v8a ==="
ls -la "$JNI/"
echo "=== libexecutorch.so still has my JNI symbols after strip? ==="
/home/aliam/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm -D "$JNI/libexecutorch.so" | grep -i QnnMultimodalRunner || echo "!!! lost symbols"
