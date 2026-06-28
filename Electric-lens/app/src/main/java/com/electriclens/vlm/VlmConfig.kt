package com.electriclens.vlm

/**
 * Immutable description of how to load and prompt one on-device VLM.
 *
 * This is intentionally the single source of truth for file names, image
 * preprocessing parameters and the chat/prompt template, so that the rest of
 * the VLM seam (engines, adapters) is model-agnostic.
 *
 * IMPORTANT runtime reality (see VlmConfigs.SMOLVLM):
 * The discovered SmolVLM export is a 3-PART Qualcomm QNN export
 * (vision_encoder + tok_embedding + decoder). The stock ExecuTorch Android
 * `LlmModule` loads only a SINGLE COMBINED `.pte`, so this split layout requires
 * a custom multimodal JNI runner that is NOT present today. [split] records that
 * fact so the engine factory can choose the right (and honest) code path.
 */
data class VlmConfig(
    /** Logical model identity. */
    val modelId: VlmModelId,
    /** Assets subfolder where model files are expected, e.g. "models/smolvlm_500m". */
    val assetDir: String,
    /** true = 3-part QNN export (no stock-LlmModule support); false = single combined .pte. */
    val split: Boolean,
    /** "vision_encoder_qnn.pte" for split, else null. */
    val visionEncoderPte: String?,
    /** "tok_embedding_qnn.pte" for split, else null. */
    val tokEmbeddingPte: String?,
    /** "hybrid_llama_qnn.pte" (split) OR the combined .pte filename. */
    val decoderPte: String,
    /** Tokenizer file name, "tokenizer.json". */
    val tokenizer: String,
    /** Square input edge in pixels, 512 for SmolVLM2-500M. */
    val imageSize: Int,
    /** Per-channel normalization mean (RGB), documented per model below. */
    val mean: FloatArray,
    /** Per-channel normalization std (RGB), documented per model below. */
    val std: FloatArray,
    /**
     * Prompt template. Contains a single %s for the question and the <image>
     * token, mirroring the model's chat template.
     */
    val promptTemplate: String,
    /** Maximum new tokens to generate for a single answer. */
    val maxNewTokens: Int
) {
    // data class with FloatArray fields => provide structural equals/hashCode.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VlmConfig) return false
        return modelId == other.modelId &&
            assetDir == other.assetDir &&
            split == other.split &&
            visionEncoderPte == other.visionEncoderPte &&
            tokEmbeddingPte == other.tokEmbeddingPte &&
            decoderPte == other.decoderPte &&
            tokenizer == other.tokenizer &&
            imageSize == other.imageSize &&
            mean.contentEquals(other.mean) &&
            std.contentEquals(other.std) &&
            promptTemplate == other.promptTemplate &&
            maxNewTokens == other.maxNewTokens
    }

    override fun hashCode(): Int {
        var result = modelId.hashCode()
        result = 31 * result + assetDir.hashCode()
        result = 31 * result + split.hashCode()
        result = 31 * result + (visionEncoderPte?.hashCode() ?: 0)
        result = 31 * result + (tokEmbeddingPte?.hashCode() ?: 0)
        result = 31 * result + decoderPte.hashCode()
        result = 31 * result + tokenizer.hashCode()
        result = 31 * result + imageSize
        result = 31 * result + mean.contentHashCode()
        result = 31 * result + std.contentHashCode()
        result = 31 * result + promptTemplate.hashCode()
        result = 31 * result + maxNewTokens
        return result
    }
}

object VlmConfigs {

    /**
     * Real, discovered SmolVLM2-500M layout.
     *
     * Files present on disk:
     *   vision_encoder_qnn.pte, tok_embedding_qnn.pte, hybrid_llama_qnn.pte,
     *   tokenizer.json, tokenizer_config.json, chat_template.jinja
     *
     * Tokenizer: Idefics3/GPT2, vocab 49152.
     * Special tokens: bos "<|im_start|>", eos "<end_of_utterance>", image "<image>".
     * Chat template (verbatim):
     *   "<|im_start|>User:<image>{text}<end_of_utterance>\nAssistant:"
     *
     * Image preprocessing: 512x512 RGB. SmolVLM/Idefics3 image processor uses
     * mean/std = 0.5/0.5/0.5 (i.e. (x/255 - 0.5) / 0.5 -> [-1, 1]). The stock
     * ExecuTorch LlmModule path consumes raw uint8 RGB and applies normalization
     * internally, so these values are documented for the custom QNN runner path
     * (which receives a preprocessed tensor).
     *
     * split = true: this is the 3-part QNN export. The stock LlmModule CANNOT
     * load it; a custom MultimodalRunner JNI is required (see QnnSplitVlmEngine).
     */
    val SMOLVLM: VlmConfig = VlmConfig(
        modelId = VlmModelId.SMOLVLM_500M,
        assetDir = "models/smolvlm_500m",
        split = true,
        visionEncoderPte = "vision_encoder_qnn.pte",
        tokEmbeddingPte = "tok_embedding_qnn.pte",
        decoderPte = "hybrid_llama_qnn.pte",
        tokenizer = "tokenizer.json",
        imageSize = 512,
        // SmolVLM2 / Idefics3 image processor normalization (maps [0,255] -> [-1,1]).
        mean = floatArrayOf(0.5f, 0.5f, 0.5f),
        std = floatArrayOf(0.5f, 0.5f, 0.5f),
        // Mirrors chat template: "<|im_start|>User:<image>{text}<end_of_utterance>\nAssistant:"
        promptTemplate = "<|im_start|>User:<image>%s<end_of_utterance>\nAssistant:",
        maxNewTokens = 64
    )

    /**
     * Best-effort placeholder for InternVL3 1B. The model folder is absent today,
     * so every field below is a guess and MUST be verified once the export lands.
     *
     * InternVL3 is assumed here to be a SINGLE COMBINED .pte (split = false) so it
     * can route through the stock ExecuTorch LlmModule path, but that has not been
     * verified.
     */
    val INTERNVL3: VlmConfig = VlmConfig(
        modelId = VlmModelId.INTERNVL3_1B,
        assetDir = "models/internvl3_1b",                 // TODO verify when model lands
        split = false,                                    // TODO verify when model lands
        visionEncoderPte = null,                          // TODO verify when model lands
        tokEmbeddingPte = null,                           // TODO verify when model lands
        decoderPte = "internvl3_1b.pte",                  // TODO verify when model lands
        tokenizer = "tokenizer.json",                     // TODO verify when model lands
        imageSize = 448,                                  // TODO verify (InternVL typically 448)
        // ImageNet normalization is the common InternVL default. TODO verify.
        mean = floatArrayOf(0.485f, 0.456f, 0.406f),      // TODO verify when model lands
        std = floatArrayOf(0.229f, 0.224f, 0.225f),       // TODO verify when model lands
        // TODO verify the real InternVL3 chat template when the model lands.
        promptTemplate = "<|im_start|>User:<image>%s<end_of_utterance>\nAssistant:",
        maxNewTokens = 64
    )

    fun forModel(id: VlmModelId): VlmConfig = when (id) {
        VlmModelId.SMOLVLM_500M -> SMOLVLM
        VlmModelId.INTERNVL3_1B -> INTERNVL3
    }
}
