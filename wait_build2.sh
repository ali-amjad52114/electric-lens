#!/usr/bin/env bash
LOG=/home/aliam/et_aar_build2.log
for i in $(seq 1 120); do
  if grep -q "=== DONE native build" "$LOG"; then echo "BUILD_DONE"; break; fi
  if grep -qE "FAILED:|fatal error:|\] Error [0-9]|ninja: build stopped|undefined reference to|gmake.*\*\*\*" "$LOG"; then echo "BUILD_ERROR_DETECTED"; break; fi
  sleep 10
done
echo "=== errors (if any) ==="
grep -nE "error:|FAILED:|undefined reference|jni_layer_qnn_multimodal" "$LOG" | tail -30
echo "=== last 20 lines ==="
tail -20 "$LOG"
