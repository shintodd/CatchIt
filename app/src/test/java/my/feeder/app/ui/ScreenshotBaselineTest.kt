package my.feeder.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
 * "Before" screenshot baseline for the adaptive-UI work.
 *
 * Every screen renders from its own empty/default repository state (no network, no
 * clock, no location), so these images are deterministic. Goldens live in
 * app/src/test/roborazzi/ and are recorded with:
 *
 *     ./gradlew.bat recordRoborazziDebug
 *
 * and compared with:
 *
 *     ./gradlew.bat verifyRoborazziDebug
 *
 * KNOWN LIMITATION: RouteDetailScreen embeds a real WebView (FeederRealMap), which does
 * not render under Robolectric. That screen is therefore NOT covered here and must be
 * checked on an emulator (Step 6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ScreenshotBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(
        name: String,
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        dark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                    DeviceConfigurationOverride.FontScale(fontScale) then
                    DeviceConfigurationOverride.DarkMode(dark)
            ) {
                FeederMYTheme(darkTheme = dark) {
                    content()
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/roborazzi/$name.png")
    }

    // P1 320x568 @240dpi - smallest realistic phone
    // P2 360x800 @480dpi - most common phone
    // P4 360x640 @480dpi, font scale 2.0 - largest display size
    // P6 600x960 @320dpi - unfolded foldable / small tablet
    // P9 360x800 @480dpi, dark + font scale 1.3

    @Test fun home_p1() = shot("home_p1", 320, 568) { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }
    @Test fun home_p2() = shot("home_p2", 360, 800) { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }
    @Test fun home_p4_fs2() = shot("home_p4_fs2", 360, 640, fontScale = 2f) { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }
    @Test fun home_p6() = shot("home_p6", 600, 960) { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }
    @Test fun home_p9_dark() = shot("home_p9_dark", 360, 800, fontScale = 1.3f, dark = true) { HomeScreen(onStationPickerClick = {}, onRouteClick = {}) }

    @Test fun alarms_p1() = shot("alarms_p1", 320, 568) { AlertsScreen() }
    @Test fun alarms_p2() = shot("alarms_p2", 360, 800) { AlertsScreen() }
    @Test fun alarms_p4_fs2() = shot("alarms_p4_fs2", 360, 640, fontScale = 2f) { AlertsScreen() }
    @Test fun alarms_p6() = shot("alarms_p6", 600, 960) { AlertsScreen() }
    @Test fun alarms_p9_dark() = shot("alarms_p9_dark", 360, 800, fontScale = 1.3f, dark = true) { AlertsScreen() }

    @Test fun settings_p1() = shot("settings_p1", 320, 568) { SettingsScreen(onOpenOemWizard = {}) }
    @Test fun settings_p2() = shot("settings_p2", 360, 800) { SettingsScreen(onOpenOemWizard = {}) }
    @Test fun settings_p4_fs2() = shot("settings_p4_fs2", 360, 640, fontScale = 2f) { SettingsScreen(onOpenOemWizard = {}) }
    @Test fun settings_p6() = shot("settings_p6", 600, 960) { SettingsScreen(onOpenOemWizard = {}) }
    @Test fun settings_p9_dark() = shot("settings_p9_dark", 360, 800, fontScale = 1.3f, dark = true) { SettingsScreen(onOpenOemWizard = {}) }

    @Test fun notifications_p1() = shot("notifications_p1", 320, 568) { NotificationsScreen(onBack = {}) }
    @Test fun notifications_p2() = shot("notifications_p2", 360, 800) { NotificationsScreen(onBack = {}) }
    @Test fun notifications_p4_fs2() = shot("notifications_p4_fs2", 360, 640, fontScale = 2f) { NotificationsScreen(onBack = {}) }
    @Test fun notifications_p6() = shot("notifications_p6", 600, 960) { NotificationsScreen(onBack = {}) }
    @Test fun notifications_p9_dark() = shot("notifications_p9_dark", 360, 800, fontScale = 1.3f, dark = true) { NotificationsScreen(onBack = {}) }

    @Test fun stationpicker_p1() = shot("stationpicker_p1", 320, 568) { StationHubSheetContent(onSelectStation = {}, onClose = {}) }
    @Test fun stationpicker_p2() = shot("stationpicker_p2", 360, 800) { StationHubSheetContent(onSelectStation = {}, onClose = {}) }
    @Test fun stationpicker_p4_fs2() = shot("stationpicker_p4_fs2", 360, 640, fontScale = 2f) { StationHubSheetContent(onSelectStation = {}, onClose = {}) }
    @Test fun stationpicker_p6() = shot("stationpicker_p6", 600, 960) { StationHubSheetContent(onSelectStation = {}, onClose = {}) }
    @Test fun stationpicker_p9_dark() = shot("stationpicker_p9_dark", 360, 800, fontScale = 1.3f, dark = true) { StationHubSheetContent(onSelectStation = {}, onClose = {}) }
}
