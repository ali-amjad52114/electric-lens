package com.electriclens.ui.theme // TODO: change to your actual package

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/* =============================================================================
 *  Electric Lens — typography
 *  Display = Space Grotesk  (titles, step titles, wordmark)
 *  Body    = Inter          (sentence text, button labels)
 *  Mono    = JetBrains Mono  (eyebrows, codes, IDs, latency, timestamps, chips)
 *
 *  This file COMPILES AS-IS using system fallbacks. To get the real identity,
 *  add the OFL fonts to res/font/ (offline — no network/downloadable fonts) and
 *  switch the three families to the commented "real fonts" block below.
 * ========================================================================== */

// ---- Families (fallback so the project builds with zero extra steps) --------
val DisplayFamily: FontFamily = FontFamily.Default   // -> Space Grotesk
val BodyFamily: FontFamily    = FontFamily.Default   // -> Inter
val MonoFamily: FontFamily    = FontFamily.Monospace // -> JetBrains Mono

/*  Real fonts — after adding these files to res/font/, delete the three lines
 *  above and uncomment this block (and `import ...R`, `import androidx.compose.ui.text.font.Font`):
 *
 *  res/font/space_grotesk_medium.ttf, space_grotesk_bold.ttf
 *  res/font/inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf
 *  res/font/jetbrains_mono_regular.ttf, jetbrains_mono_bold.ttf
 *
 *  val DisplayFamily = FontFamily(
 *      Font(R.font.space_grotesk_medium, FontWeight.Medium),
 *      Font(R.font.space_grotesk_bold,   FontWeight.Bold),
 *  )
 *  val BodyFamily = FontFamily(
 *      Font(R.font.inter_regular,  FontWeight.Normal),
 *      Font(R.font.inter_medium,   FontWeight.Medium),
 *      Font(R.font.inter_semibold, FontWeight.SemiBold),
 *  )
 *  val MonoFamily = FontFamily(
 *      Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
 *      Font(R.font.jetbrains_mono_bold,    FontWeight.Bold),
 *  )
 */

// ---- M3 Typography (drives Material components) -----------------------------
val ElTypography = Typography(
    // Wordmark / hero
    displayLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 50.sp, letterSpacing = (-0.03).em,
    ),
    // Screen titles (H2)
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em,
    ),
    // Step titles / card titles
    titleLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, // ~1.5
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    // Button / control labels
    labelLarge = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 16.sp,
    ),
)

/* -----------------------------------------------------------------------------
 *  Mono / data styles. Kept OUT of the M3 slots so Material components don't
 *  accidentally render in monospace. Use these directly:
 *      Text("NPU 164 ms", style = ElText.chip)
 *      Text("STEP 1 · BREAKER B-201".uppercase(), style = ElText.eyebrow)
 * -------------------------------------------------------------------------- */
object ElText {
    /** Eyebrow / section label — render the string in UPPERCASE at the call site. */
    val eyebrow = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.12.em,
    )
    /** Status-chip text (LIVE, MOCK, VLM, NPU …). */
    val chip = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, letterSpacing = 0.04.em,
    )
    /** Inline data: fault codes, breaker IDs, timestamps, permit body. */
    val data = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    )
    /** Smallest readout: confidence caption, latency under a viewport. */
    val readout = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, letterSpacing = 0.02.em,
    )
}
