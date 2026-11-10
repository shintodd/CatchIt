package my.feeder.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlertScheduleManagerTest {

    private fun createCalendar(dayOfWeek: Int, hourOfDay: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun daytimeWindow_evaluatesCorrectlyOnWeekday() {
        // Window: 17:10 to 19:10, Weekdays only
        val cfg = AlertScheduleManager.ScheduleConfig(
            enabled = true,
            startHour = 17,
            startMinute = 10,
            endHour = 19,
            endMinute = 10,
            weekdaysOnly = true
        )

        // Monday 17:05 -> Before start
        val before = createCalendar(Calendar.MONDAY, 17, 5)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(before, cfg))

        // Wednesday 17:10 -> Exact start
        val exactStart = createCalendar(Calendar.WEDNESDAY, 17, 10)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(exactStart, cfg))

        // Thursday 18:00 -> Mid commute window
        val midWindow = createCalendar(Calendar.THURSDAY, 18, 0)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(midWindow, cfg))

        // Friday 19:10 -> Exact end
        val exactEnd = createCalendar(Calendar.FRIDAY, 19, 10)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(exactEnd, cfg))

        // Friday 19:15 -> After end
        val after = createCalendar(Calendar.FRIDAY, 19, 15)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(after, cfg))
    }

    @Test
    fun weekendFiltering_blocksWhenWeekdaysOnlyIsTrue() {
        val cfg = AlertScheduleManager.ScheduleConfig(
            enabled = true,
            startHour = 17,
            startMinute = 10,
            endHour = 19,
            endMinute = 10,
            weekdaysOnly = true
        )

        // Saturday 18:00 -> within hours, but weekend
        val sat = createCalendar(Calendar.SATURDAY, 18, 0)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(sat, cfg))

        // Sunday 18:00 -> within hours, but weekend
        val sun = createCalendar(Calendar.SUNDAY, 18, 0)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(sun, cfg))

        // If weekdaysOnly is false, weekend should be active
        val dailyCfg = cfg.copy(weekdaysOnly = false)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(sat, dailyCfg))
        assertTrue(AlertScheduleManager.isWithinActiveWindow(sun, dailyCfg))
    }

    @Test
    fun overnightWindow_evaluatesCorrectly() {
        // Window spanning midnight: 22:30 to 06:30, Daily
        val cfg = AlertScheduleManager.ScheduleConfig(
            enabled = true,
            startHour = 22,
            startMinute = 30,
            endHour = 6,
            endMinute = 30,
            weekdaysOnly = false
        )

        // 22:00 -> Before window
        val before = createCalendar(Calendar.TUESDAY, 22, 0)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(before, cfg))

        // 23:30 -> Inside late night portion
        val lateNight = createCalendar(Calendar.TUESDAY, 23, 30)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(lateNight, cfg))

        // 04:15 -> Inside early morning portion
        val earlyMorning = createCalendar(Calendar.WEDNESDAY, 4, 15)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(earlyMorning, cfg))

        // 06:30 -> Exact end
        val exactEnd = createCalendar(Calendar.WEDNESDAY, 6, 30)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(exactEnd, cfg))

        // 07:00 -> Outside window
        val daylight = createCalendar(Calendar.WEDNESDAY, 7, 0)
        assertFalse(AlertScheduleManager.isWithinActiveWindow(daylight, cfg))
    }

    @Test
    fun disabledSchedule_alwaysActive() {
        val disabledCfg = AlertScheduleManager.ScheduleConfig(
            enabled = false,
            startHour = 17,
            startMinute = 10,
            endHour = 19,
            endMinute = 10,
            weekdaysOnly = true
        )

        val sundayMidnight = createCalendar(Calendar.SUNDAY, 2, 0)
        assertTrue(AlertScheduleManager.isWithinActiveWindow(sundayMidnight, disabledCfg))
        assertEquals("Always active (24/7)", AlertScheduleManager.getStatusSummary(sundayMidnight, disabledCfg))
    }

    @Test
    fun statusSummary_providesClearCommuterFeedback() {
        val cfg = AlertScheduleManager.ScheduleConfig(
            enabled = true,
            startHour = 17,
            startMinute = 10,
            endHour = 19,
            endMinute = 10,
            weekdaysOnly = true
        )

        // Active state
        val inside = createCalendar(Calendar.MONDAY, 18, 0)
        assertEquals("Active now · 17:10 – 19:10 (Mon–Fri)", AlertScheduleManager.getStatusSummary(inside, cfg))

        // Morning before commute
        val morning = createCalendar(Calendar.TUESDAY, 10, 0)
        assertEquals("Sleeping until 17:10 today", AlertScheduleManager.getStatusSummary(morning, cfg))

        // Night after commute
        val night = createCalendar(Calendar.TUESDAY, 21, 0)
        assertEquals("Sleeping until 17:10 tomorrow", AlertScheduleManager.getStatusSummary(night, cfg))

        // Friday night -> sleeps until Monday
        val friNight = createCalendar(Calendar.FRIDAY, 20, 0)
        assertEquals("Sleeping until Mon 17:10", AlertScheduleManager.getStatusSummary(friNight, cfg))

        // Saturday
        val sat = createCalendar(Calendar.SATURDAY, 12, 0)
        assertEquals("Sleeping until Mon 17:10", AlertScheduleManager.getStatusSummary(sat, cfg))
    }
}
