package com.electriclens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Electric Lens is always dark (field-tool aesthetic). The dark color scheme is
 * built from the named constants in Color.kt so the Material3 theme and the raw
 * constants never drift apart.
 */
private val ElectricLensColorScheme = darkColorScheme(
    primary = Caution,
    onPrimary = BgDark,
    secondary = Verified,
    onSecondary = BgDark,
    tertiary = Verified,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextLight,
    surface = PanelDark,
    onSurface = TextLight,
    surfaceVariant = PanelDark,
    onSurfaceVariant = TextLight,
    error = Alert,
    onError = TextLight,
    outline = Caution
)

@Composable
fun ElectricLensTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always dark — this is an industrial field tool, not a SaaS dashboard.
    MaterialTheme(
        colorScheme = ElectricLensColorScheme,
        typography = Typography,
        content = content
    )
}
