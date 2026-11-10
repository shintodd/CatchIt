package my.feeder.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import my.feeder.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Tactile press compression: the surface scales to 0.97 while held, then
 * releases. Gives a tap physical feedback without moving the layout.
 *
 * Drawn entirely in draw-phase via graphicsLayer to eliminate recompositions.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = Motion.PRESS_SCALE
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = Motion.MICRO, easing = Motion.Exit),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered entrance for list rows: each row fades and rises 6dp into place,
 * offset by [index] * 30ms so the list ripples in rather than snapping.
 *
 * The cascade is capped at [Motion.STAGGER_MAX_ROWS] so a long list never
 * leaves the last row waiting. Rendered in graphicsLayer for 120fps smoothness.
 */
@Composable
fun StaggeredRow(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val capped = index.coerceAtMost(Motion.STAGGER_MAX_ROWS)
        delay(capped * Motion.STAGGER.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER, easing = Motion.Enter),
        label = "staggerAlpha"
    )
    val rise by animateDpAsState(
        targetValue = if (visible) 0.dp else 6.dp,
        animationSpec = tween(durationMillis = Motion.ENTER, easing = Motion.Enter),
        label = "staggerRise"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = rise.toPx()
        }
    ) {
        content()
    }
}
