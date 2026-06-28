package com.electriclens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.ui.screens.AddDocumentsScreen
import com.electriclens.ui.screens.AiSessionScreen
import com.electriclens.ui.screens.GuidedLockoutScreen
import com.electriclens.ui.screens.PermitScreen
import com.electriclens.ui.screens.StartScreen
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.TextLight
import com.electriclens.util.TtsManager
import com.electriclens.vm.AppState
import com.electriclens.vm.SessionViewModel

/**
 * State-driven navigation. There is no NavController — the current [AppState]
 * fully determines which screen is shown. Speak events are collected here and
 * routed to the [TtsManager] (which itself no-ops when muted).
 */
@Composable
fun AppNavHost(vm: SessionViewModel, tts: TtsManager, speech: com.electriclens.util.SpeechManager) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val useMock by vm.useMockSource.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.speakEvents.collect { line ->
            if (!isMuted) tts.speak(line)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            AppState.IDLE -> StartScreen(vm)
            AppState.SESSION_STARTED,
            AppState.DOCS_PROCESSED -> AddDocumentsScreen(vm)
            AppState.LIVE_SESSION -> AiSessionScreen(vm, tts, speech)
            AppState.LOTO_B201,
            AppState.LOTO_B205,
            AppState.LOTO_LOCK_TAG,
            AppState.LOTO_MCC_OPEN -> GuidedLockoutScreen(vm, tts)
            AppState.PERMIT_READY -> PermitScreen(vm)
        }

        // Persistent mock-mode warning banner — pinned above every screen so a
        // fake detection source can never be mistaken for real on-device AI.
        if (useMock) {
            Text(
                text = "MOCK MODE — NOT REAL AI",
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Alert)
                    .statusBarsPadding()
                    .padding(10.dp)
            )
        }
    }
}
