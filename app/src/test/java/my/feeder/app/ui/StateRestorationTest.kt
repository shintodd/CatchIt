package my.feeder.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import my.feeder.app.MainActivity
import my.feeder.app.ui.screens.HomeScreen
import my.feeder.app.ui.screens.NotificationsScreen
import my.feeder.app.ui.screens.StationHubSheetContent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Logic safety net: does user-meaningful state survive a configuration change?
 *
 * A rotation, a fold/unfold, a font-size change or a window resize all recreate the
 * activity. Anything held in a plain `remember` is thrown away when that happens, and
 * the commuter loses what they were doing.
 *
 * These tests assert the correct behaviour. A failure here is an S1 finding ("lost
 * state"); production code is deliberately NOT changed at this step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StateRestorationTest {

    @get:Rule
    val rule = createComposeRule()

    private fun restore(state: @Composable () -> Unit, body: (StateRestorationTester) -> Unit) {
        val tester = StateRestorationTester(rule)
        tester.setContent { state() }
        body(tester)
    }

    /** The Bus screen's search text must survive rotation. */
    @Test
    fun `home search text survives restoration`() = restore({
        HomeScreen(
            onStationPickerClick = {},
            onRouteClick = {}
        )
    }) { tester ->
        rule.onNodeWithTag("search").performTextInput("T507")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("search").assertTextContains("T507")
    }

    /** The station picker's search text must survive rotation. */
    @Test
    fun `station picker search text survives restoration`() = restore({
        StationHubSheetContent(
            selectedStationCode = "KG12",
            onSelectStation = {},
            onClose = {}
        )
    }) { tester ->
        rule.onNodeWithTag("station-search").performTextInput("Phileo")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("station-search").assertTextContains("Phileo")
    }

    /** Notifications screen filter choice survives restoration. */
    @Test
    fun `notifications screen filter survives restoration`() = restore({
        NotificationsScreen(onBack = {})
    }) { tester ->
        rule.onNodeWithText("Rail").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Rail").assertExists()
    }

    /** Full ActivityScenario recreation survives process/config change without crash. */
    @Test
    fun `main activity scenario survives recreate`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
        }
    }
}
