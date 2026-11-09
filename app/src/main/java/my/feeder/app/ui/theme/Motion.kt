package my.feeder.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Motion tokens. One place for every duration, easing curve and spring used in
 * the app, so animation feels consistent instead of each screen inventing its
 * own timing.
 *
 * Rules encoded here:
 *  - Entrances use ease-out (fast start, gentle settle), not ease-in.
 *  - Exits are 20% shorter than entrances — dismissal needs less attention.
 *  - Micro-interactions stay in the 150–250ms band; longer feels sluggish.
 *  - Everyday UI uses damped springs with no overshoot. Bounce is for playful
 *    products; a transit tool that wobbles reads as unreliable.
 */
object Motion {

    // ---- Durations (ms) ----
    /** Standard entrance: content appearing. */
    const val ENTER = 250

    /** Exit is 20% shorter than the matching entrance. */
    const val EXIT = 200

    /** Micro-interaction band: presses, chips, toggles. */
    const val MICRO = 180

    /** Per-row cascade step for lists. 30ms reads as a ripple, not a wait. */
    const val STAGGER = 30

    /** Cap the cascade so a long list doesn't take a second to settle. */
    const val STAGGER_MAX_ROWS = 10

    // ---- Easing curves ----
    /** Entrances: decelerate into place. */
    val Enter: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /** Exits: accelerate away. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    /** Moves between two on-screen positions: accelerate then decelerate. */
    val Move: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // ---- Springs ----
    /**
     * Damped, no overshoot. Use for anything that settles into place in
     * everyday UI (nav selection, chevron flip, pin state).
     */
    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Press compression factor — subtle tactile feedback, not a squish. */
    const val PRESS_SCALE = 0.97f

    /** Popover / sheet entry scale: start near the final size, never from zero. */
    const val POPOVER_START_SCALE = 0.95f
}
