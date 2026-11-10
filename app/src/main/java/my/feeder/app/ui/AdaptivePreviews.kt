package my.feeder.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes

/**
 * One annotation so every screen gets the full matrix in Android Studio:
 * phone/tablet/foldable sizes, large font, light and dark.
 *
 * Usage:  @AdaptivePreviews
 *         @Composable fun HomeScreenPreview() { ... }
 */
@PreviewScreenSizes
@PreviewFontScale
@PreviewLightDark
annotation class AdaptivePreviews

/** Smallest realistic phone (P1). */
@Preview(name = "P1 320x568", device = "spec:width=320dp,height=568dp,dpi=240")
annotation class PreviewP1

/** Landscape phone (P5). */
@Preview(name = "P5 800x360", device = "spec:width=800dp,height=360dp,dpi=480")
annotation class PreviewP5

/** Unfolded foldable / small tablet (P6). */
@Preview(name = "P6 600x960", device = "spec:width=600dp,height=960dp,dpi=320")
annotation class PreviewP6

/** Tablet landscape (P7). */
@Preview(name = "P7 1280x800", device = "spec:width=1280dp,height=800dp,dpi=320")
annotation class PreviewP7
