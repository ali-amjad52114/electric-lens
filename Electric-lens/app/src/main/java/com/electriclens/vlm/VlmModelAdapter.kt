package com.electriclens.vlm

import com.electriclens.detection.Detection
import com.electriclens.detection.DetectionType

/**
 * The ONLY model-specific prompt/parse place.
 *
 * Engines ([VlmEngine]) are deliberately dumb: they take a bitmap + a prompt
 * string and return raw text. This adapter owns (a) which prompt to send for a
 * given [DetectionType], and (b) how to turn the model's raw answer back into a
 * [Detection] (or null when nothing is detected).
 *
 * Keeping all model-specific text here means swapping models or tuning prompts
 * never touches the engines or the detection source.
 */
class VlmModelAdapter(val config: VlmConfig) {

    /** Exact prompt text per detection type (independent of chat template wrapping). */
    fun promptFor(type: DetectionType): String = when (type) {
        DetectionType.FAULT_CODE ->
            "Read the fault code shown on this industrial VFD display. Reply with only the code (e.g. F071 OC1)."
        DetectionType.BREAKER_B201_OFF ->
            "Read the breaker. Reply with the breaker ID label (for example B-201) and whether the handle is OFF or ON."
        DetectionType.BREAKER_B205_OFF ->
            "Read the breaker. Reply with the breaker ID label (for example B-201) and whether the handle is OFF or ON."
        DetectionType.LOCK_TAG ->
            "Is a padlock applied AND a danger tag attached? Answer yes or no for each."
        DetectionType.MCC_OPEN ->
            "Is the electrical cabinet door open? Answer yes or no."
    }

    /**
     * Map a raw model answer to a [Detection], ALWAYS returning a non-null result
     * carrying the REAL confidence (and the read code for FAULT_CODE). A
     * zero-confidence detection is the honest "tried, not confirmed" signal — it
     * reaches the ViewModel but never passes the safety gate.
     *
     * - FAULT_CODE: extract a code; confidence 0.9 when a code is found, else 0.
     *   The extracted code travels on the detection so no mirror lookup is needed.
     * - yes/no steps: confidence is the affirmative proxy (0.9 clear / 0.6 hedged)
     *   or 0 when not affirmative.
     */
    fun evaluate(type: DetectionType, rawAnswer: String, timestampMs: Long): Detection {
        return when (type) {
            DetectionType.FAULT_CODE -> {
                val code = extractFaultCode(rawAnswer)
                val confidence = if (code != null) 0.9f else 0f
                Detection(DetectionType.FAULT_CODE, confidence, timestampMs, code)
            }

            DetectionType.BREAKER_B201_OFF,
            DetectionType.BREAKER_B205_OFF -> {
                val identity = normalizeBreakerId(rawAnswer)
                val isOff = isHandleOff(rawAnswer)
                val confidence = when {
                    identity != null && isOff -> 0.9f
                    identity != null -> 0.4f
                    else -> 0f
                }
                Detection(type, confidence, timestampMs, identity = identity)
            }

            DetectionType.LOCK_TAG -> {
                val lockPresent = isLockPresent(rawAnswer)
                val tagPresent = isTagPresent(rawAnswer)
                val confidence = when {
                    lockPresent && tagPresent -> 0.9f
                    lockPresent || tagPresent -> 0.5f
                    else -> 0f
                }
                Detection(type, confidence, timestampMs)
            }

            DetectionType.MCC_OPEN -> {
                val confidence = affirmativeConfidence(rawAnswer) ?: 0f
                Detection(type, confidence, timestampMs)
            }
        }
    }

    /**
     * Pull a breaker ID such as "B-201", "B 205", "b201" out of the answer and
     * normalize it to the canonical "B-<digits>" form (uppercase, hyphen).
     * @return the normalized id, or null when no breaker id is present.
     */
    fun normalizeBreakerId(raw: String): String? {
        val match = BREAKER_ID_REGEX.find(raw) ?: return null
        val digits = match.groupValues[1]
        return "B-$digits"
    }

    /**
     * True when the answer indicates the handle is OFF: contains "off"
     * (case-insensitive) and that "off" is NOT preceded by "not" / "on".
     */
    private fun isHandleOff(raw: String): Boolean {
        val normalized = raw.lowercase()
        if (!normalized.contains("off")) return false
        // Reject "not off" and "on ... off" framings that negate the OFF reading.
        if (NOT_OFF_REGEX.containsMatchIn(normalized)) return false
        return true
    }

    /** True when the answer affirms a padlock/lock is present. */
    private fun isLockPresent(raw: String): Boolean =
        PRESENT_REGEX.containsMatchIn(raw) &&
            Regex("""\b(padlock|lock)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) &&
            !ABSENT_REGEX.containsMatchIn(raw)

    /** True when the answer affirms a danger tag is attached. */
    private fun isTagPresent(raw: String): Boolean =
        PRESENT_REGEX.containsMatchIn(raw) &&
            Regex("""\b(danger\s+tag|tag)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) &&
            !ABSENT_REGEX.containsMatchIn(raw)

    /**
     * Pull a fault code such as "F071", "F071 OC1", "E04" out of the answer.
     * Pattern: a leading letter, 2-4 digits, optional space + 2-4 alnum group.
     */
    fun extractFaultCode(rawAnswer: String): String? {
        val match = FAULT_CODE_REGEX.find(rawAnswer.trim()) ?: return null
        return match.value.trim().uppercase()
    }

    /**
     * @return a confidence proxy in (0, 0.9] when the answer is clearly
     * affirmative, or null when it is not affirmative (negative / unknown).
     */
    private fun affirmativeConfidence(rawAnswer: String): Float? {
        val normalized = rawAnswer.trim().lowercase()
        if (normalized.isEmpty()) return null

        // Explicit negatives short-circuit to "not detected".
        if (LEADING_NEGATIVE_REGEX.containsMatchIn(normalized)) return null

        // Clear affirmative leading word.
        if (!LEADING_AFFIRMATIVE_REGEX.containsMatchIn(normalized)) return null

        // Scale down when the answer hedges ("probably", "maybe", "appears", ...).
        val hedged = HEDGE_REGEX.containsMatchIn(normalized)
        return if (hedged) 0.6f else 0.9f
    }

    companion object {
        // e.g. F071, F071 OC1, E04, OC1 — letter, 2-4 digits, optional 2-4 alnum group.
        private val FAULT_CODE_REGEX = Regex("""([A-Za-z]\d{2,4}(\s?[A-Za-z0-9]{2,4})?)""")

        // Breaker ID: B / b, optional spaces/hyphen, then exactly 3 digits. e.g. B-201, B 205, b201.
        private val BREAKER_ID_REGEX = Regex("""[Bb]\s*-?\s*(\d{3})""")

        // Negated OFF: "not off" or an "on" reading that overrides a stray "off".
        private val NOT_OFF_REGEX = Regex("""\bnot\s+off\b|\bhandle\s+is\s+on\b|\bis\s+on\b""", RegexOption.IGNORE_CASE)

        // Affirmative presence (lock/tag applied/attached/present/yes).
        private val PRESENT_REGEX = Regex(
            """\b(yes|applied|attached|present|installed|there\s+is|a\s+padlock|a\s+tag)\b""",
            RegexOption.IGNORE_CASE
        )

        // Explicit absence (no lock/tag, not applied/attached/present).
        private val ABSENT_REGEX = Regex(
            """\b(no\b|not\s+(applied|attached|present|installed)|absent|missing|none)\b""",
            RegexOption.IGNORE_CASE
        )

        // Leading affirmative: yes / yeah / yep / affirmative / correct / true / it is / there is.
        private val LEADING_AFFIRMATIVE_REGEX = Regex(
            """^\W*(yes|yeah|yep|yup|affirmative|correct|true|indeed|it\s+is|there\s+is|the\s+\w+\s+is\s+(off|open)|a\s+padlock)""",
            RegexOption.IGNORE_CASE
        )

        // Leading negative: no / nope / negative / it is not / there is no.
        private val LEADING_NEGATIVE_REGEX = Regex(
            """^\W*(no|nope|nah|negative|false|not\b|it\s+is\s+not|there\s+is\s+no|the\s+\w+\s+is\s+(on|closed|up))""",
            RegexOption.IGNORE_CASE
        )

        // Hedging language that should reduce confidence.
        private val HEDGE_REGEX = Regex(
            """\b(probably|maybe|might|possibly|appears?|seems?|likely|i\s+think|not\s+sure|unclear)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
