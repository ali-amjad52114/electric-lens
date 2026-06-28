#!/system/bin/sh
PKG=com.electriclens
EXT=/sdcard/Android/data/$PKG/files
SRC_MODEL=$EXT/models/smolvlm_500m
SRC_M=$EXT/m
TMP=/data/local/tmp

run-as $PKG mkdir -p files/models/smolvlm_500m files/m

copy() {
  SRC="$1"; DEST="$2"
  base=$(basename "$SRC")
  cp "$SRC" "$TMP/$base"
  chmod 666 "$TMP/$base"
  run-as $PKG cp "$TMP/$base" "$DEST/$base"
  rm -f "$TMP/$base"
  echo "copied $base -> $DEST"
}

copy $SRC_MODEL/vision_encoder_qnn.pte files/models/smolvlm_500m
copy $SRC_MODEL/tok_embedding_qnn.pte files/models/smolvlm_500m
copy $SRC_MODEL/hybrid_llama_qnn.pte files/models/smolvlm_500m
copy $SRC_MODEL/tokenizer.json files/models/smolvlm_500m
copy $SRC_M/vision_encoder_input_0_0.raw files/m

echo "=== internal model dir ==="
run-as $PKG ls -la files/models/smolvlm_500m
echo "=== internal m dir ==="
run-as $PKG ls -la files/m
echo POPULATE_DONE
