package com.electriclens.ui.theme // TODO: change to your actual package

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/* =============================================================================
 *  Electric Lens — spacing, sizing, shapes, motion
 *  ONE consistent screen padding keeps every card / chip / button on the same
 *  left & right edges (the alignment rule from the mobile layout contract).
 * ========================================================================== */
object Dimens {
    // Layout
    val ScreenPadding       = 20.dp   // default horizontal padding (use the SAME value top→bottom)
    val ScreenPaddingTight  = 18.dp   // camera / LOTO screen variant — pick one per screen, keep it consistent
    val CardPadding         = 16.dp
    val BlockGap            = 14.dp   // vertical gap between stacked blocks (12–16)
    val ItemGap             = 10.dp   // gap inside rows (e.g. two-button row)
    val ChipGap             = 8.dp

    // Chips / badges
    val ChipPaddingH        = 10.dp
    val ChipPaddingV        = 6.dp
    val DotSize             = 7.dp
    val BadgeSize           = 40.dp   // instruction badge / PDF icon square

    // Touch
    val MinTouch            = 56.dp   // primary buttons
    val MinTarget           = 48.dp   // any tappable element

    // Stepper / progress
    val StepNode            = 24.dp
    val StepConnector       = 2.dp
    val ProgressHeight      = 4.dp

    // Camera viewport
    val ViewportRadius      = 18.dp   // 4:3 aspect — set height = width * 3 / 4

    // Corner radii
    val CardRadius          = 18.dp
    val ButtonRadius        = 16.dp
    val SegRadius           = 12.dp
    val ChipRadius          = 8.dp
    val BadgeRadius         = 11.dp
    val PillRadius          = 999.dp  // status pills, toggles
}

/** Material 3 shape scale derived from the radii above. Pass as `shapes = ElShapes`. */
val ElShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.ChipRadius),  // 8
    small      = RoundedCornerShape(Dimens.SegRadius),   // 12
    medium     = RoundedCornerShape(Dimens.CardPadding), // 16 — buttons/bubbles
    large      = RoundedCornerShape(Dimens.CardRadius),  // 18 — cards / viewport
    extraLarge = RoundedCornerShape(28.dp),
)

/** Convenience shapes for non-M3-slot uses. */
object ElShape {
    val Pill   = RoundedCornerShape(percent = 50)
    val Card   = RoundedCornerShape(Dimens.CardRadius)
    val Button = RoundedCornerShape(Dimens.ButtonRadius)
    val Chip   = RoundedCornerShape(Dimens.ChipRadius)
    val Badge  = RoundedCornerShape(Dimens.BadgeRadius)
}

/* -----------------------------------------------------------------------------
 *  Motion. One easing curve everywhere; honor system reduced-motion by skipping
 *  the looping animations (pulse / scanline) and snapping state changes.
 *  Use with tween(Durations.Reveal, easing = ElEase).
 * -------------------------------------------------------------------------- */
val ElEase = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

object Durations {
    const val Micro   = 180   // taps, toggles, hovers
    const val Reveal  = 420   // screen / element enter
    const val Fill    = 1400  // confidence / progress fill
    const val Pulse   = 1500  // status-dot pulse / scanline loop
}
