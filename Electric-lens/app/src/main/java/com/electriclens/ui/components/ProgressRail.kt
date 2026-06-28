package com.electriclens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.electriclens.ui.theme.Alert
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.vm.AppState

/** Visual status of a single rail stage. */
private enum class StageStatus { GRAY, AMBER, GREEN, RED }

/**
 * Five-step LOTO progress rail rendered horizontally:
 *   Fault → Isolation Match → Breakers OFF → Lock + Tag → Permit
 *
 * Each stage is a colored bar + label: gray (not started), amber (active),
 * green (verified) or red (blocked). Status is derived purely from VM state.
 */
@Composable
fun ProgressRail(
    state: AppState,
    faultRead: Boolean,
    canStartLoto: Boolean,
    blocked: Boolean,
    modifier: Modifier = Modifier
) {
    val pastLive = state == AppState.LOTO_B201 ||
        state == AppState.LOTO_B205 ||
        state == AppState.LOTO_LOCK_TAG ||
        state == AppState.LOTO_MCC_OPEN ||
        state == AppState.PERMIT_READY

    val fault = when {
        faultRead -> StageStatus.GREEN
        state == AppState.LIVE_SESSION -> StageStatus.AMBER
        else -> StageStatus.GRAY
    }

    val isolation = when {
        canStartLoto || pastLive -> StageStatus.GREEN
        faultRead && !canStartLoto -> StageStatus.AMBER
        else -> StageStatus.GRAY
    }

    val breakers = when {
        state == AppState.LOTO_LOCK_TAG ||
            state == AppState.LOTO_MCC_OPEN ||
            state == AppState.PERMIT_READY -> StageStatus.GREEN
        blocked && (state == AppState.LOTO_B201 || state == AppState.LOTO_B205) -> StageStatus.RED
        state == AppState.LOTO_B201 || state == AppState.LOTO_B205 -> StageStatus.AMBER
        else -> StageStatus.GRAY
    }

    val lockTag = when {
        state == AppState.LOTO_MCC_OPEN || state == AppState.PERMIT_READY -> StageStatus.GREEN
        blocked && state == AppState.LOTO_LOCK_TAG -> StageStatus.RED
        state == AppState.LOTO_LOCK_TAG -> StageStatus.AMBER
        else -> StageStatus.GRAY
    }

    val permit = when {
        state == AppState.PERMIT_READY -> StageStatus.GREEN
        state == AppState.LOTO_MCC_OPEN -> StageStatus.AMBER
        else -> StageStatus.GRAY
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Stage("Fault", fault, Modifier.weight(1f))
        Stage("Isolation Match", isolation, Modifier.weight(1f))
        Stage("Breakers OFF", breakers, Modifier.weight(1f))
        Stage("Lock + Tag", lockTag, Modifier.weight(1f))
        Stage("Permit", permit, Modifier.weight(1f))
    }
}

@Composable
private fun Stage(label: String, status: StageStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        StageStatus.GRAY -> TextLight.copy(alpha = 0.25f)
        StageStatus.AMBER -> Caution
        StageStatus.GREEN -> Verified
        StageStatus.RED -> Alert
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (status == StageStatus.GRAY) TextLight.copy(alpha = 0.45f) else TextLight,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (status == StageStatus.GRAY) FontWeight.Normal else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
