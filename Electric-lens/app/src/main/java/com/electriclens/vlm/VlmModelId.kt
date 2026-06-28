package com.electriclens.vlm

/**
 * Identifies a vision-language model the app can run on device.
 *
 * [folderName] is the directory name of the original on-disk export (under the
 * repo's `Model/` tree) and is kept for traceability. The runtime asset layout
 * is described separately by [VlmConfig.assetDir].
 */
enum class VlmModelId(val displayName: String, val folderName: String) {
    SMOLVLM_500M("SmolVLM 500M · default", "smolvlm_500m_instruct_ctxt-4096_SM8750"),
    INTERNVL3_1B("InternVL3 1B · accuracy", "internvl3_1b_ctxt-4096_SM8750");

    companion object {
        val DEFAULT = SMOLVLM_500M
    }
}
