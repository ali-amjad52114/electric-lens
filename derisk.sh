#!/system/bin/sh
NLD=/data/app/~~CP-_P_nszrXEReEMkcTvEw==/com.electriclens-SHjyYaCP6Cj1pR6o4iY3mQ==/lib/arm64
M=/sdcard/Android/data/com.electriclens/files/m
cd /data/data/com.electriclens || exit 9
export LD_LIBRARY_PATH=$NLD
export ADSP_LIBRARY_PATH=$NLD
echo "=== running runner as uid=$(id -u) (app domain) ==="
$NLD/libqnn_multimodal_runner.so \
  --decoder_path $M/hybrid_llama_qnn.pte \
  --encoder_path $M/vision_encoder_qnn.pte \
  --tok_embedding_path $M/tok_embedding_qnn.pte \
  --tokenizer_path $M/tokenizer.json \
  --image_path $M/vision_encoder_input_0_0.raw \
  --decoder_model_version smolvlm \
  --eval_mode 1 --seq_len 64 \
  --prompt "Read the fault code shown on this industrial VFD display. Reply with only the code." \
  --output_path out.txt
echo "RUNNER_RC=$?"
echo "=== out.txt ==="
cat out.txt 2>/dev/null
