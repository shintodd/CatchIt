package my.feeder.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import com.github.takahirom.roborazzi.captureRoboImage
import my.feeder.app.ui.screens.AlertsScreen
import my.feeder.app.ui.screens.HomeScreen
import my.feeder.app.ui.screens.NotificationsScreen
import my.feeder.app.ui.screens.SettingsScreen
import my.feeder.app.ui.screens.StationHubSheetContent
import my.feeder.app.ui.theme.FeederMYTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baseline for the adaptive-UI work.
 *
 * Screen size is set per test with Robolectric's `@Config(qualifiers = "w…dp-h…dp")` —
 * the only mechanism that actually resizes the captured window (Compose's
 * DeviceConfigurationOverride.ForcedSize changes the composition's configuration but not
 * the window Roborazzi captures). Font scale and dark mode ARE applied via
 * DeviceConfigurationOverride, which works under Robolectric.
 *
 * Density is left at mdpi: dp-based layout is density-independent, so goldens stay small
 * and fast; density-specific rendering is checked on the emulator in Step 6 instead.
 *
 * Every screen renders from its own empty/default repository state (no network, no
 * clock, no location), so images are deterministic. Goldens live in
 * app/src/test/roborazzi/. Record: ./gradlew.bat recordRoborazziDebug. Verify:
 * ./gradlew.bat verifyRoborazziDebug.
 *
 * KNOWN LIMITATION: RouteDetailScreen embeds a WebView (FeederRealMap) which does not
 * render under Robolectric; it is covered on the emulator (Step 6) only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ScreenshotBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun capture(
        name: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(fontScale) then
                    DeviceConfigurationOverride.DarkMode(dark)
            ) {
                FeederMYTheme(darkTheme = dark) { content() }
            }
        }
        rule.onRoot().captureRoboImage("src/test/roborazzi/$name.png")
    }

    private val home: @Composable () -> Unit = { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }
    private val alarms: @Composable () -> Unit = { AlertsScreen() }
    private val settings: @Composable () -> Unit = { SettingsScreen(onOpenOemWizard = {}) }
    private val notifications: @Composable () -> Unit = { NotificationsScreen(onBack = {}) }
    private val stationPicker: @Composable () -> Unit = { StationHubSheetContent(onSelectStation = {}, onClose = {}) }

    // P1 320x568 @240dpi - smallest realistic phone
    @Config(qualifiers = "w320dp-h568dp") @Test fun home_p1() = capture("home_p1") { home() }
    @Config(qualifiers = "w320dp-h568dp") @Test fun alarms_p1() = capture("alarms_p1") { alarms() }
    @Config(qualifiers = "w320dp-h568dp") @Test fun settings_p1() = capture("settings_p1") { settings() }
    @Config(qualifiers = "w320dp-h568dp") @Test fun notifications_p1() = capture("notifications_p1") { notifications() }
    @Config(qualifiers = "w320dp-h568dp") @Test fun stationpicker_p1() = capture("stationpicker_p1") { stationPicker() }

    // P2 360x800 @480dpi - most common phone
    @Config(qualifiers = "w360dp-h800dp") @Test fun home_p2() = capture("home_p2") { home() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun alarms_p2() = capture("alarms_p2") { alarms() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun settings_p2() = capture("settings_p2") { settings() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun notifications_p2() = capture("notifications_p2") { notifications() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun stationpicker_p2() = capture("stationpicker_p2") { stationPicker() }

    // P3 412x915 @640dpi - larger modern phone
    @Config(qualifiers = "w412dp-h915dp") @Test fun home_p3() = capture("home_p3") { home() }
    @Config(qualifiers = "w412dp-h915dp") @Test fun alarms_p3() = capture("alarms_p3") { alarms() }
    @Config(qualifiers = "w412dp-h915dp") @Test fun settings_p3() = capture("settings_p3") { settings() }
    @Config(qualifiers = "w412dp-h915dp") @Test fun notifications_p3() = capture("notifications_p3") { notifications() }
    @Config(qualifiers = "w412dp-h915dp") @Test fun stationpicker_p3() = capture("stationpicker_p3") { stationPicker() }

    // P4 360x640 @480dpi, font scale 2.0 - "Display size: Largest"
    @Config(qualifiers = "w360dp-h640dp") @Test fun home_p4_fs2() = capture("home_p4_fs2", fontScale = 2f) { home() }
    @Config(qualifiers = "w360dp-h640dp") @Test fun alarms_p4_fs2() = capture("alarms_p4_fs2", fontScale = 2f) { alarms() }
    @Config(qualifiers = "w360dp-h640dp") @Test fun settings_p4_fs2() = capture("settings_p4_fs2", fontScale = 2f) { settings() }
    @Config(qualifiers = "w360dp-h640dp") @Test fun notifications_p4_fs2() = capture("notifications_p4_fs2", fontScale = 2f) { notifications() }
    @Config(qualifiers = "w360dp-h640dp") @Test fun stationpicker_p4_fs2() = capture("stationpicker_p4_fs2", fontScale = 2f) { stationPicker() }

    // P5 800x360 @480dpi landscape phone
    @Config(qualifiers = "w800dp-h360dp-land") @Test fun home_p5() = capture("home_p5") { home() }
    @Config(qualifiers = "w800dp-h360dp-land") @Test fun alarms_p5() = capture("alarms_p5") { alarms() }
    @Config(qualifiers = "w800dp-h360dp-land") @Test fun settings_p5() = capture("settings_p5") { settings() }
    @Config(qualifiers = "w800dp-h360dp-land") @Test fun notifications_p5() = capture("notifications_p5") { notifications() }
    @Config(qualifiers = "w800dp-h360dp-land") @Test fun stationpicker_p5() = capture("stationpicker_p5") { stationPicker() }

    // P6 600x960 @320dpi - unfolded foldable / small tablet
    @Config(qualifiers = "w600dp-h960dp") @Test fun home_p6() = capture("home_p6") { home() }
    @Config(qualifiers = "w600dp-h960dp") @Test fun alarms_p6() = capture("alarms_p6") { alarms() }
    @Config(qualifiers = "w600dp-h960dp") @Test fun settings_p6() = capture("settings_p6") { settings() }
    @Config(qualifiers = "w600dp-h960dp") @Test fun notifications_p6() = capture("notifications_p6") { notifications() }
    @Config(qualifiers = "w600dp-h960dp") @Test fun stationpicker_p6() = capture("stationpicker_p6") { stationPicker() }

    // P7 1280x800 @320dpi landscape - tablet landscape (width cap must center content)
    @Config(qualifiers = "w1280dp-h800dp-land") @Test fun home_p7() = capture("home_p7") { home() }
    @Config(qualifiers = "w1280dp-h800dp-land") @Test fun alarms_p7() = capture("alarms_p7") { alarms() }
    @Config(qualifiers = "w1280dp-h800dp-land") @Test fun settings_p7() = capture("settings_p7") { settings() }
    @Config(qualifiers = "w1280dp-h800dp-land") @Test fun notifications_p7() = capture("notifications_p7") { notifications() }
    @Config(qualifiers = "w1280dp-h800dp-land") @Test fun stationpicker_p7() = capture("stationpicker_p7") { stationPicker() }

    // P8 360x400 @480dpi split-screen window
    @Config(qualifiers = "w360dp-h400dp") @Test fun home_p8() = capture("home_p8") { home() }
    @Config(qualifiers = "w360dp-h400dp") @Test fun alarms_p8() = capture("alarms_p8") { alarms() }
    @Config(qualifiers = "w360dp-h400dp") @Test fun settings_p8() = capture("settings_p8") { settings() }
    @Config(qualifiers = "w360dp-h400dp") @Test fun notifications_p8() = capture("notifications_p8") { notifications() }
    @Config(qualifiers = "w360dp-h400dp") @Test fun stationpicker_p8() = capture("stationpicker_p8") { stationPicker() }

    // P9 360x800 @480dpi, dark + font scale 1.3
    @Config(qualifiers = "w360dp-h800dp") @Test fun home_p9_dark() = capture("home_p9_dark", fontScale = 1.3f, dark = true) { home() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun alarms_p9_dark() = capture("alarms_p9_dark", fontScale = 1.3f, dark = true) { alarms() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun settings_p9_dark() = capture("settings_p9_dark", fontScale = 1.3f, dark = true) { settings() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun notifications_p9_dark() = capture("notifications_p9_dark", fontScale = 1.3f, dark = true) { notifications() }
    @Config(qualifiers = "w360dp-h800dp") @Test fun stationpicker_p9_dark() = capture("stationpicker_p9_dark", fontScale = 1.3f, dark = true) { stationPicker() }
}
