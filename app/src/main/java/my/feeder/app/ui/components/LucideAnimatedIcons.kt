package my.feeder.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import my.feeder.app.R
import my.feeder.app.ui.navigation.FeederNavTab
import my.feeder.app.ui.theme.Motion

/**
 * Standard Lucide Icon wrapper
 */
@Composable
fun LucideIcon(
    drawableId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id = drawableId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * Bottom navigation icon.
 *
 * Selection is a damped settle with no overshoot — a nav bar that wobbles reads
 * as unreliable in a transit app. Rotation is a short ease-in-out because it is
 * a position change, not an entrance.
 */
@Composable
fun LucideNavIcon(
    tab: FeederNavTab,
    isSelected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(Motion.MICRO, easing = Motion.Enter),
        label = "navScale"
    )

    val rotation by animateFloatAsState(
        targetValue = when {
            isSelected && tab == FeederNavTab.ALERTS -> 12f
            isSelected && tab == FeederNavTab.SETTINGS -> 60f
            else -> 0f
        },
        animationSpec = tween(Motion.MICRO, easing = Motion.Move),
        label = "navRotation"
    )

    val liftOffsetY by animateDpAsState(
        targetValue = if (isSelected && tab == FeederNavTab.HOME) (-1).dp else 0.dp,
        animationSpec = tween(Motion.MICRO, easing = Motion.Enter),
        label = "navLift"
    )

    val drawableId = when (tab) {
        FeederNavTab.HOME -> R.drawable.ic_lucide_home
        FeederNavTab.ALERTS -> R.drawable.ic_lucide_bell
        FeederNavTab.SETTINGS -> R.drawable.ic_lucide_settings
    }

    Box(
        modifier = modifier
            .offset(y = liftOffsetY)
            .scale(scale)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(
            drawableId = drawableId,
            contentDescription = tab.label,
            size = 22.dp,
            tint = tint
        )
    }
}

/**
 * Bell with a short ring on arm/disarm.
 *
 * The swing is a one-shot confirmation, so it can be expressive — but it settles
 * damped (no overshoot) and finishes in ~350ms total.
 */
@Composable
fun LucideAnimatedBell(
    isArmed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = if (isArmed) Color(0xFF4ADE80) else LocalContentColor.current,
    triggerAnim: Int = 0
) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isArmed, triggerAnim) {
        if (isArmed || triggerAnim > 0) {
            scope.launch {
                rotation.animateTo(-14f, tween(70, easing = Motion.Enter))
                rotation.animateTo(11f, tween(80, easing = Motion.Move))
                rotation.animateTo(-6f, tween(70, easing = Motion.Move))
                rotation.animateTo(0f, tween(Motion.EXIT, easing = Motion.Enter))
            }
        }
    }

    Box(
        modifier = modifier.rotate(rotation.value),
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(
            drawableId = R.drawable.ic_lucide_bell,
            contentDescription = if (isArmed) "Alarm on" else "Alarm off",
            size = size,
            tint = tint
        )
    }
}

/**
 * Refresh spinner — linear rotation, only while actually refreshing.
 */
@Composable
fun LucideAnimatedRefresh(
    isSpinning: Boolean = false,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "continuousSpin"
    )

    Box(
        modifier = modifier.rotate(if (isSpinning) spinAngle else 0f),
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(
            drawableId = R.drawable.ic_lucide_rotate_cw,
            contentDescription = "Refresh",
            size = size,
            tint = tint
        )
    }
}

/**
 * Chevron flip. A rotation is a position change, so ease-in-out at micro speed.
 */
@Composable
fun LucideAnimatedChevron(
    isExpanded: Boolean,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Motion.MICRO, easing = Motion.Move),
        label = "chevronRotation"
    )

    Box(
        modifier = modifier.rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(
            drawableId = R.drawable.ic_lucide_chevron_down,
            contentDescription = null,
            size = size,
            tint = tint
        )
    }
}

/**
 * Live-tracking pulse. One slow, low-amplitude breath — a continuous
 * attention-grabber would be wrong for a status indicator.
 */
@Composable
fun LucideAnimatedRadio(
    isBroadcasting: Boolean,
    size: Dp = 20.dp,
    tint: Color = if (isBroadcasting) Color(0xFF4ADE80) else LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radioPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = Motion.Move),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.scale(if (isBroadcasting) pulseScale else 1.0f),
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(
            drawableId = R.drawable.ic_lucide_radio,
            contentDescription = "Tracking active",
            size = size,
            tint = tint
        )
    }
}
