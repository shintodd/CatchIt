package my.feeder.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for CatchIt adhering to Material Design 3 and the 4pt/8pt Spacing & Layout System.
 * Establishes strict mathematical spatial rhythm and concentric radius harmony across all viewports.
 */

object Spacing {
    val hairline: Dp = 2.dp      // Step 0.5 (2px): hairlines, subtle dividers
    val xs: Dp = 4.dp            // Step 1   (4px): micro gaps, badge padding
    val fine: Dp = 6.dp          // Step 1.5 (6px): compact chip padding
    val sm: Dp = 8.dp            // Step 2   (8px): button icon spacing, intra-group gap
    val md: Dp = 12.dp           // Step 3   (12px): medium-compact list item gaps
    val lg: Dp = 16.dp           // Step 4   (16px): standard card padding, mobile gutter
    val xl: Dp = 20.dp           // Step 5   (20px): modal card inset, header padding
    val xxl: Dp = 24.dp          // Step 6   (24px): section stack gaps
    val xxxl: Dp = 32.dp         // Step 8   (32px): major section breathing room
    val hero: Dp = 48.dp         // Step 12  (48px): hero padding, minimum touch target (M3/WCAG)
    val huge: Dp = 64.dp         // Step 16  (64px): top safe-area breathing room, splash

    val minTouchTarget: Dp = 48.dp
    val gutterMobile: Dp = 16.dp
}

object Radius {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val base: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val pill: Dp = 100.dp

    /**
     * Concentric Border Radius formula: Radius_outer = Radius_inner + Padding
     * Keeps curves visually parallel without visual corner distortion.
     */
    fun concentric(inner: Dp, padding: Dp): Dp = inner + padding
}

object TypeSize {
    val caption = 12.sp
    val body = 14.sp
    val title = 16.sp
    val headline = 20.sp
}

/**
 * The single accent colour. Green is the brand/primary; amber marks the
 * interactive highlights — pins, live indicators, and the next-departure
 * moment. Used sparingly so it means "this is the thing to notice".
 */
val Accent = Color(0xFFF59E0B)              // amber
val AccentContainer = Color(0xFFFEF3C7)      // pale amber
val AccentOnContainer = Color(0xFF7C5800)    // readable amber text

/**
 * Tabular figures: all digits occupy the same width, so a countdown or a
 * column of departure times does not jitter as the numbers change.
 * Apply to any Text whose primary content is digits/times.
 */
val TabularNums = TextStyle(
    fontFeatureSettings = "tnum"
)
