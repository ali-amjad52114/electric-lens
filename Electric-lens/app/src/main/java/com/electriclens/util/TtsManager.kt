package com.electriclens.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper around [TextToSpeech]. US English, QUEUE_ADD so successive AI
 * lines are spoken in order rather than cutting each other off. Fully offline —
 * uses the on-device TTS engine.
 */
class TtsManager(context: Context) {

    private var muted: Boolean = false
    private var ready: Boolean = false

    // lateinit: the OnInitListener lambda fires asynchronously after construction,
    // by which point `tts` is assigned. Referencing it in the property initializer
    // directly would fail definite-assignment analysis.
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                ready = true
            }
        }
    }

    /** Speak [text]. No-op while muted. */
    fun speak(text: String) {
        if (muted) return
        if (text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) {
            tts.stop()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
