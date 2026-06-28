#!/usr/bin/env bash
SO=/home/aliam/executorch/cmake-out-android-so/arm64-v8a/libexecutorch.so
NM=/home/aliam/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm
QAIRT=/home/aliam/qcom/qairt/qairt/2.46.0.260424
NDK=/home/aliam/android-ndk-r27c

echo "=== my JNI symbols (exported) ==="
"$NM" -D "$SO" | grep -i "QnnMultimodalRunner" || echo "!!! NOT FOUND"
echo "=== QNNMultimodalRunner C++ class present ==="
"$NM" "$SO" 2>/dev/null | grep -i "QNNMultimodalRunner" | head -3 || echo "(static check)"
echo "=== qnn backend DT_NEEDED in libexecutorch.so ==="
/home/aliam/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf -d "$SO" 2>/dev/null | grep -iE "NEEDED" | grep -iE "qnn|c\+\+" || echo "(none)"
echo "=== QAIRT HTP libs present? ==="
ls -la "$QAIRT/lib/aarch64-android/libQnnHtp.so" "$QAIRT/lib/aarch64-android/libQnnHtpV79Stub.so" "$QAIRT/lib/aarch64-android/libQnnHtpPrepare.so" "$QAIRT/lib/aarch64-android/libQnnSystem.so" 2>&1
ls -la "$QAIRT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so" 2>&1
echo "=== NDK libc++_shared ==="
ls -la "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" 2>&1
echo "=== current app jniLibs ==="
ls -la /mnt/c/AI/Projects/ElectricSafe/Electric-lens/app/src/main/jniLibs/arm64-v8a/ 2>&1
