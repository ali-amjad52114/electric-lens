#!/usr/bin/env bash
LOG=/home/aliam/et_rebuild.log
for i in $(seq 1 90); do
  if grep -q "REBUILD_NATIVE_DONE" "$LOG"; then echo "REBUILD_DONE"; break; fi
  if grep -qE "FAILED:|fatal error:|\] Error [0-9]|ninja: build stopped|undefined reference to" "$LOG"; then echo "REBUILD_ERROR"; break; fi
  sleep 10
done
echo "=== tail ==="; tail -15 "$LOG"
