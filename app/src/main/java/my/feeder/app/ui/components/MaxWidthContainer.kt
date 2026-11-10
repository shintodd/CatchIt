package my.feeder.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Caps content width on wide screens (foldables, tablets, split-screen) so the phone
 * layout does not stretch full-bleed; the content renders as a centered column instead.
 *
 * A no-op below [maxWidth] (all phones are < 600dp wide, so phone profiles are
 * untouched). Pass the screen's size modifier (e.g. `Modifier.fillMaxSize()`) so the
 * child still gets a bounded height.
 */
@Composable
fun MaxWidthContainer(
    maxWidth: Dp = 600.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}
