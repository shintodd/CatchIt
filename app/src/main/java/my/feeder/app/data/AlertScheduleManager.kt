package my.feeder.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Manages the active schedule window for bus departure alarms and service disruption alerts.
 *
 * Commuters can customize start/end commute times (e.g. 17:10 to 19:10 like the reference
 * transit bot) and weekday vs. daily active periods. Outside this commute window, background
 * polling and alarms sleep to preserve device battery and avoid nuisance alerts.
 */
object AlertScheduleManager {

    private const val PREFS_NAME = "alert_schedule_prefs"
    private const val KEY_ENABLED = "schedule_enabled"
    private const val KEY_START_HOUR = "schedule_start_hour"
    private const val KEY_START_MINUTE = "schedule_start_minute"
    private const val KEY_END_HOUR = "schedule_end_hour"
    private const val KEY_END_MINUTE = "schedule_end_minute"
    private const val KEY_WEEKDAYS_ONLY = "schedule_weekdays_only"

    // Default commute window matching t507-bus-discord-bot: 17:10 - 19:10, Mon-Fri
    private const val DEFAULT_START_HOUR = 17
    private const val DEFAULT_START_MINUTE = 10
    private const val DEFAULT_END_HOUR = 19
    private const val DEFAULT_END_MINUTE = 10

    data class ScheduleConfig(
        val enabled: Boolean = true,
        val startHour: Int = DEFAULT_START_HOUR,
        val startMinute: Int = DEFAULT_START_MINUTE,
        val endHour: Int = DEFAULT_END_HOUR,
        val endMinute: Int = DEFAULT_END_MINUTE,
        val weekdaysOnly: Boolean = true
    ) {
        val startTimeFormatted: String
            get() = String.format("%02d:%02d", startHour, startMinute)

        val endTimeFormatted: String
            get() = String.format("%02d:%02d", endHour, endMinute)
    }

    private var prefs: SharedPreferences? = null
    private val _config = MutableStateFlow(ScheduleConfig())
    val config: StateFlow<ScheduleConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _config.value = ScheduleConfig(
                enabled = p.getBoolean(KEY_ENABLED, true),
                startHour = p.getInt(KEY_START_HOUR, DEFAULT_START_HOUR),
                startMinute = p.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE),
                endHour = p.getInt(KEY_END_HOUR, DEFAULT_END_HOUR),
                endMinute = p.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE),
                weekdaysOnly = p.getBoolean(KEY_WEEKDAYS_ONLY, true)
            )
        }
    }

    /**
     * Updates and persists schedule configuration.
     */
    @Synchronized
    fun update(
        enabled: Boolean? = null,
        startHour: Int? = null,
        startMinute: Int? = null,
        endHour: Int? = null,
        endMinute: Int? = null,
        weekdaysOnly: Boolean? = null
    ) {
        val current = _config.value
        val updated = current.copy(
            enabled = enabled ?: current.enabled,
            startHour = startHour ?: current.startHour,
            startMinute = startMinute ?: current.startMinute,
            endHour = endHour ?: current.endHour,
            endMinute = endMinute ?: current.endMinute,
            weekdaysOnly = weekdaysOnly ?: current.weekdaysOnly
        )
        _config.value = updated
        prefs?.edit()?.apply {
            putBoolean(KEY_ENABLED, updated.enabled)
            putInt(KEY_START_HOUR, updated.startHour)
            putInt(KEY_START_MINUTE, updated.startMinute)
            putInt(KEY_END_HOUR, updated.endHour)
            putInt(KEY_END_MINUTE, updated.endMinute)
            putBoolean(KEY_WEEKDAYS_ONLY, updated.weekdaysOnly)
            apply()
        }
    }

    /**
     * Determines whether the given moment falls within the configured active schedule window.
     * When disabled, always returns true (active 24/7).
     */
    fun isWithinActiveWindow(
        calendar: Calendar = Calendar.getInstance(),
        cfg: ScheduleConfig = _config.value
    ): Boolean {
        if (!cfg.enabled) return true

        // Day check
        if (cfg.weekdaysOnly) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return false
            }
        }

        // Time check
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = cfg.startHour * 60 + cfg.startMinute
        val endMinutes = cfg.endHour * 60 + cfg.endMinute

        return if (startMinutes <= endMinutes) {
            // Standard daytime window, e.g. 17:10 to 19:10
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight window spanning midnight, e.g. 22:00 to 06:00
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    /**
     * Generates a concise human-readable status for UI badges and banners.
     */
    fun getStatusSummary(
        calendar: Calendar = Calendar.getInstance(),
        cfg: ScheduleConfig = _config.value
    ): String {
        if (!cfg.enabled) return "Always active (24/7)"

        val daysDesc = if (cfg.weekdaysOnly) "Mon–Fri" else "Every day"
        if (isWithinActiveWindow(calendar, cfg)) {
            return "Active now · ${cfg.startTimeFormatted} – ${cfg.endTimeFormatted} ($daysDesc)"
        }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = cfg.startHour * 60 + cfg.startMinute

        if (cfg.weekdaysOnly && (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)) {
            return "Sleeping until Mon ${cfg.startTimeFormatted}"
        }

        if (currentMinutes < startMinutes) {
            return "Sleeping until ${cfg.startTimeFormatted} today"
        }

        if (cfg.weekdaysOnly && dayOfWeek == Calendar.FRIDAY) {
            return "Sleeping until Mon ${cfg.startTimeFormatted}"
        }

        return "Sleeping until ${cfg.startTimeFormatted} tomorrow"
    }
}
