package com.electriclens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.electriclens.BuildConfig
import com.electriclens.ui.components.OnDevicePill
import com.electriclens.ui.components.PrimaryButton
import com.electriclens.ui.theme.BgDark
import com.electriclens.ui.theme.Caution
import com.electriclens.ui.theme.PanelDark
import com.electriclens.ui.theme.TextLight
import com.electriclens.ui.theme.Verified
import com.electriclens.vlm.VlmModelId
import com.electriclens.vm.SessionViewModel

/**
 * START screen — dark industrial field-tool look.
 * Centered app identity + a single big, glove-friendly "Start Session" action.
 *
 * Below the action sit two field controls, rendered purely from VM state:
 *  - a detection-source toggle: "Mock (demo)" vs "VLM · on-device",
 *  - an on-device model selector (SmolVLM / InternVL3) used when VLM is active.
 */
@Composable
fun StartScreen(vm: SessionViewModel) {
    val useMock by vm.useMockSource.collectAsStateWithLifecycle()
    val selectedModel by vm.selectedModel.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OnDevicePill()

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Electric Lens",
                color = TextLight,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 48.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Electrical Safety · On-Device",
                color = TextLight.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(56.dp))

            PrimaryButton(
                text = "Start Session",
                onClick = { vm.startSession() },
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .height(64.dp)
            )

            Spacer(Modifier.height(28.dp))

            // Detection source toggle — DEBUG builds only. The Mock source is a
            // developer aid; release builds stay on the VLM default and never
            // expose a way to switch to fake detection.
            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .border(1.dp, PanelDark, RoundedCornerShape(12.dp))
                        .background(PanelDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detection source",
                            color = TextLight,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (useMock) "Mock (demo)" else "VLM · on-device",
                            color = if (useMock) Verified else Caution,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = !useMock,
                        onCheckedChange = { vm.toggleDetectionSource() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Caution,
                            uncheckedThumbColor = Verified
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // On-device model selector — picks which VLM runs when VLM mode is
            // active. Shown regardless of Mock/VLM so the choice is ready ahead
            // of time. SmolVLM is the default.
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .border(1.dp, PanelDark, RoundedCornerShape(12.dp))
                    .background(PanelDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "On-device model",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VlmModelId.values().forEach { model ->
                        ModelChip(
                            label = model.displayName,
                            selected = model == selectedModel,
                            onClick = { vm.selectModel(model) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** A glove-friendly selectable chip for the on-device model selector. */
@Composable
private fun ModelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Caution else PanelDark,
                shape = RoundedCornerShape(10.dp)
            )
            .background(
                color = if (selected) Caution.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Caution else TextLight.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
