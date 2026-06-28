package com.electriclens.ui.theme // TODO: change to your actual package

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* =============================================================================
 *  Electric Lens — color tokens
 *  Rule: color encodes SAFETY STATE, never decoration.
 *    Green  = evidence captured / verified · on-device OK
 *    Amber  = caution / pending / the user's primary action
 *    Red    = alert / failed evidence / blocked
 *    Blue   = reading / scanning (system thinking, transient)
 *    Gray   = inactive / unavailable
 *  Green/Verified means "evidence captured" — it does NOT mean equipment is safe.
 * ========================================================================== */

// ---- Raw palette ------------------------------------------------------------
val ElBg            = Color(0xFF0A0E13) // app background
val ElSurface       = Color(0xFF121922) // cards, chips, bubbles
val ElSurfaceRaise  = Color(0xFF19222E) // raised / secondary fills
val ElLine          = Color(0xFF283442) // borders
val ElLineSoft      = Color(0xFF1B2531) // subtle borders / dividers

val ElTextPrimary   = Color(0xFFEDF1F5)
val ElTextDim       = Color(0xFF82909F)
val ElTextFaint     = Color(0xFF525E6C) // inactive / unavailable ("gray")

val ElAmber         = Color(0xFFFFB020) // caution · pending · primary action
val ElAmberHi       = Color(0xFFFFC04A) // top of the amber button gradient
val ElInk           = Color(0xFF1A1205) // text/icon ON amber
val ElRed           = Color(0xFFFF5A5F) // alert · failed · blocked
val ElGreen         = Color(0xFF27D796) // verified · on-device
val ElBlue          = Color(0xFF5AC8FF) // reading · scanning

// ---- Faint tints for chip / badge fills (alpha-baked) -----------------------
val ElAmberFill = Color(0x1AFFB020) // ~10%
val ElGreenFill = Color(0x1427D796) // ~8%
val ElRedFill   = Color(0x14FF5A5F) // ~8%
val ElBlueFill  = Color(0x245AC8FF) // ~14%

/* -----------------------------------------------------------------------------
 *  Extended colors. Material 3 has no "success"/"info" slot, so green & blue
 *  and the field-specific roles live here, themed via a CompositionLocal.
 *  Read them in composables with:  val el = LocalElColors.current
 * -------------------------------------------------------------------------- */
@Immutable
data class ElExtendedColors(
    val verified: Color,      // green
    val caution: Color,       // amber
    val alert: Color,         // red
    val reading: Color,       // blue
    val inactive: Color,      // gray
    val onAccent: Color,      // ink on amber
    val textDim: Color,
    val textFaint: Color,
    val line: Color,
    val lineSoft: Color,
    val surfaceRaise: Color,
    val verifiedFill: Color,
    val cautionFill: Color,
    val alertFill: Color,
    val readingFill: Color,
)

val DefaultElExtendedColors = ElExtendedColors(
    verified = ElGreen,
    caution = ElAmber,
    alert = ElRed,
    reading = ElBlue,
    inactive = ElTextFaint,
    onAccent = ElInk,
    textDim = ElTextDim,
    textFaint = ElTextFaint,
    line = ElLine,
    lineSoft = ElLineSoft,
    surfaceRaise = ElSurfaceRaise,
    verifiedFill = ElGreenFill,
    cautionFill = ElAmberFill,
    alertFill = ElRedFill,
    readingFill = ElBlueFill,
)

val LocalElColors = staticCompositionLocalOf { DefaultElExtendedColors }

/* -----------------------------------------------------------------------------
 *  Material 3 dark scheme. Wire this into your ElectricLensTheme, e.g.:
 *
 *    MaterialTheme(
 *        colorScheme = ElectricDarkColorScheme,
 *        typography  = ElTypography,   // from Type.kt
 *        shapes      = ElShapes,       // from Dimens.kt
 *    ) {
 *        CompositionLocalProvider(LocalElColors provides DefaultElExtendedColors) {
 *            content()
 *        }
 *    }
 * -------------------------------------------------------------------------- */
val ElectricDarkColorScheme: ColorScheme = darkColorScheme(
    primary = ElAmber,
    onPrimary = ElInk,
    primaryContainer = ElAmberFill,
    onPrimaryContainer = ElAmber,
    secondary = ElGreen,
    onSecondary = ElInk,
    secondaryContainer = ElGreenFill,
    onSecondaryContainer = ElGreen,
    tertiary = ElBlue,
    onTertiary = ElInk,
    background = ElBg,
    onBackground = ElTextPrimary,
    surface = ElSurface,
    onSurface = ElTextPrimary,
    surfaceVariant = ElSurfaceRaise,
    onSurfaceVariant = ElTextDim,
    outline = ElLine,
    outlineVariant = ElLineSoft,
    error = ElRed,
    onError = ElInk,
    scrim = Color(0xCC05080C),
)
