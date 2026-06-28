#!/usr/bin/env bash
LOG=/home/aliam/et_aar_build.log
for i in $(seq 1 120); do
  if grep -q "=== DONE native build" "$LOG"; then echo "BUILD_DONE"; break; fi
  if grep -qE "FAILED:|fatal error:|\] Error [0-9]|ninja: build stopped|undefined reference to|gmake.*\*\*\*" "$LOG"; then echo "BUILD_ERROR_DETECTED"; break; fi
  sleep 12
done
echo "=== last 25 lines of log ==="
tail -25 "$LOG"
