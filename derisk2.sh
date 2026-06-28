#!/system/bin/sh
NLD=/data/app/~~CP-_P_nszrXEReEMkcTvEw==/com.electriclens-SHjyYaCP6Cj1pR6o4iY3mQ==/lib/arm64
M=/sdcard/Android/data/com.electriclens/files/m
cd /data/data/com.electriclens || exit 9
export LD_LIBRARY_PATH=$NLD
# SIGNED-PD attempt: do NOT point ADSP at our unsigned skel; let fastRPC use the
# device's system signed skel. Try the common cDSP search paths.
unset ADSP_LIBRARY_PATH
echo "=== ATTEMPT A: no ADSP override (system default skel), uid=$(id -u) ==="
$NLD/libqnn_multimodal_runner.so --decoder_path $M/hybrid_llama_qnn.pte --encoder_path $M/vision_encoder_qnn.pte --tok_embedding_path $M/tok_embedding_qnn.pte --tokenizer_path $M/tokenizer.json --image_path $M/vision_encoder_input_0_0.raw --decoder_model_version smolvlm --eval_mode 1 --seq_len 16 --prompt "Read the code." --output_path outA.txt 2>&1 | grep -iE "skel|signed|domain|device_handle|error|fail|QnnBackend|Output|^Assistant|F0" | head -25
echo "RC_A=$?"
echo ""
echo "=== ATTEMPT B: ADSP_LIBRARY_PATH=/vendor/dsp/cdsp ==="
export ADSP_LIBRARY_PATH=/vendor/dsp/cdsp
$NLD/libqnn_multimodal_runner.so --decoder_path $M/hybrid_llama_qnn.pte --encoder_path $M/vision_encoder_qnn.pte --tok_embedding_path $M/tok_embedding_qnn.pte --tokenizer_path $M/tokenizer.json --image_path $M/vision_encoder_input_0_0.raw --decoder_model_version smolvlm --eval_mode 1 --seq_len 16 --prompt "Read the code." --output_path outB.txt 2>&1 | grep -iE "skel|signed|domain|device_handle|error|fail|QnnBackend" | head -20
echo "RC_B=$?"
