package com.electriclens.data

object DocSummarizer {
    private val FAULT_CODE_REGEX = Regex("""([A-Za-z]\d{2,4}(\s?[A-Za-z0-9]{2,4})?)""")
    private val PROCEDURE_REGEX = Regex("""(?im)^\s*(procedure|step|section|chapter)\b""")

    fun summarize(text: String): String {
        if (text.isBlank()) return "Processed on-device — no readable text found."
        val faults = FAULT_CODE_REGEX.findAll(text).map { it.value.trim().uppercase() }.toSet().size
        val procs = PROCEDURE_REGEX.findAll(text).count()
        return "$faults fault codes, $procs procedures found"
    }
}
