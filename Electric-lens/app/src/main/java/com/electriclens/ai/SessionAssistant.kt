package com.electriclens.ai

import com.electriclens.data.DemoData

enum class Intent { GREETING, TROUBLESHOOTING, FAULT_HELP, MANUAL_HELP, LOTO_HELP, UNKNOWN }

object SessionAssistant {
    fun classify(text: String): Intent {
        val t = text.lowercase()
        return when {
            t.contains("lockout") || t.contains("loto") || t.contains("isolate") -> Intent.LOTO_HELP
            t.contains("manual") || t.contains("document")                        -> Intent.MANUAL_HELP
            t.contains("fault") || t.contains("code") || t.contains("error")      -> Intent.FAULT_HELP
            t.contains("trouble") || t.contains("problem") || t.contains("help")  -> Intent.TROUBLESHOOTING
            else -> Intent.UNKNOWN
        }
    }

    fun reply(intent: Intent): String = when (intent) {
        Intent.GREETING        -> "Electric Lens ready. Need help with a fault code, the manual, or guided lockout?"
        Intent.TROUBLESHOOTING -> "Describe the symptom, or upload a photo of the VFD fault display."
        Intent.FAULT_HELP      -> "Upload a photo of the VFD fault display and I'll read the code."
        Intent.MANUAL_HELP     -> "The manual is processed on-device. Ask about a fault code or a procedure."
        Intent.LOTO_HELP       -> "Isolate ${iso()} before cabinet access. Tap Start Guided LOTO when ready."
        Intent.UNKNOWN         -> "I can help with fault codes, the manual, or guided lockout. Which one?"
    }

    fun faultReply(code: String) =
        "Fault $code — ${DemoData.manual.meaning}. Isolate ${iso()} before cabinet access. Tap Start Guided LOTO."

    private fun iso() = DemoData.asset.isolationPoints.joinToString(" and ")
}
