package my.feeder.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for CatchIt. One source of truth for spacing, corner radii,
 * type scale and the accent colour — so screens stop inventing their own
 * ad-hoc 10/12/14/16 values and instead compose from this scale.
 *
 * Radius rule: an inner element's corner radius = its parent's radius minus the
 * padding between them. E.g. a 16dp card with 12dp content padding uses 8dp for
 * inner chips (rounded down to the nearest scale step).
 */

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object Radius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 100.dp
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
