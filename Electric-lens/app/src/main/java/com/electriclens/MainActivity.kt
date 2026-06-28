package com.electriclens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.electriclens.ui.AppNavHost
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.ElectricLensTheme
import com.electriclens.util.SpeechManager
import com.electriclens.util.TtsManager
import com.electriclens.vm.SessionViewModel

class MainActivity : ComponentActivity() {

    private lateinit var tts: TtsManager
    private lateinit var speech: SpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TtsManager(this)
        speech = SpeechManager(this)

        // Debug-only: run the in-process NPU verification if the marker file exists.
        NpuSelfTest.maybeRun(this)

        setContent {
            ElectricLensTheme {
                val vm: SessionViewModel = viewModel()
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark),
                    color = BgDark
                ) {
                    AppNavHost(vm = vm, tts = tts, speech = speech)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        speech.destroy()
    }
}
