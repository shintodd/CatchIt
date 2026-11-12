package my.feeder.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateSurvivalTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize all stores
        GtfsRepository.initPins(context)
        DepartureAlarmStore.init(context)
        AlertScheduleManager.init(context)
        ThemeManager.init(context)
    }

    @Test
    fun testSettingsAndAlarmsSurviveAppRestartAndUpdate() {
        // 1. User pins multiple routes across nationwide catalog
        if (!GtfsRepository.isPinned("AJ03")) GtfsRepository.togglePin("AJ03")
        if (!GtfsRepository.isPinned("T351")) GtfsRepository.togglePin("T351")
        if (!GtfsRepository.isPinned("T507")) GtfsRepository.togglePin("T507")

        assertTrue("AJ03 should be pinned", GtfsRepository.isPinned("AJ03"))
        assertTrue("T351 should be pinned", GtfsRepository.isPinned("T351"))
        assertTrue("T507 should be pinned", GtfsRepository.isPinned("T507"))

        // 2. User arms a departure alarm on AJ03
        val alert = DepartureAlert(
            routeCode = "AJ03",
            stopId = "1006906",
            stopName = "SK TAMAN BUKIT TERATAI",
            stage1StopId = "1001793",
            stage1StopName = "AJ219 LRT CEMPAKA",
            stage2StopId = "1006906",
            stage2StopName = "SK TAMAN BUKIT TERATAI"
        )
        GtfsRepository.armAlert(alert)
        assertTrue("Alarm on AJ03 should be armed", GtfsRepository.isAlertArmed("AJ03", "1006906"))

        // 3. User sets Dark Mode theme
        ThemeManager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, ThemeManager.themeMode.value)

        // 4. User sets custom commute schedule
        AlertScheduleManager.update(
            enabled = true,
            startHour = 8,
            startMinute = 15,
            endHour = 18,
            endMinute = 45,
            weekdaysOnly = true
        )
        assertTrue(AlertScheduleManager.config.value.enabled)
        assertEquals("08:15", AlertScheduleManager.config.value.startTimeFormatted)

        // 5. Simulate alarm completion and prune call (which previously wiped alarms)
        DepartureAlarmStore.markFired("AJ03", "1006906", 1)
        DepartureAlarmStore.markFired("AJ03", "1006906", 2)
        val prunedCount = DepartureAlarmStore.pruneCompleted()
        assertEquals("Alarms should never be pruned automatically", 0, prunedCount)

        // 6. SIMULATE APP UPDATE / PROCESS KILL & RE-LAUNCH
        // In-memory singletons are re-bound from Context
        DepartureAlarmStore.init(context)
        val reloadedAlarms = DepartureAlarmStore.load()
        assertEquals("Saved alarm count must survive", 1, reloadedAlarms.size)
        assertEquals("AJ03", reloadedAlarms[0].alert.routeCode)
        assertEquals("1006906", reloadedAlarms[0].alert.stopId)

        GtfsRepository.initPins(context)
        assertTrue("AJ03 must remain pinned after update", GtfsRepository.isPinned("AJ03"))
        assertTrue("T351 must remain pinned after update", GtfsRepository.isPinned("T351"))
        assertTrue("T507 must remain pinned after update", GtfsRepository.isPinned("T507"))

        ThemeManager.init(context)
        assertEquals("Dark theme must survive update", AppThemeMode.DARK, ThemeManager.themeMode.value)

        AlertScheduleManager.init(context)
        assertTrue("Commute schedule must remain enabled", AlertScheduleManager.config.value.enabled)
        assertEquals("08:15", AlertScheduleManager.config.value.startTimeFormatted)
        assertEquals("18:45", AlertScheduleManager.config.value.endTimeFormatted)
    }
}
