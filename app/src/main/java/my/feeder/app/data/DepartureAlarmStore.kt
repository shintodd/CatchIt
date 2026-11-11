package my.feeder.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores the armed departure alarms.
 *
 * An alarm has to survive the app being closed — that is the whole point of an
 * alarm. The repository keeps the armed list in memory for the UI; this is
 * the durable copy, and it also remembers which stages have already fired so
 * "Leave now" is not announced twice.
 */
object DepartureAlarmStore {

    /** An armed alarm plus what has already been announced for it. */
    data class Armed(
        val alert: DepartureAlert,
        val stage1Fired: Boolean = false,
        val stage2Fired: Boolean = false,
        val stage1FiredAt: Long = 0L,
        val stage2FiredAt: Long = 0L,
        /** Epoch millis the alarm was armed, so a stale one can be dropped. */
        val armedAt: Long = 0L
    ) {
        /** Both stages have been announced for the current bus approach. */
        val isComplete: Boolean get() = stage1Fired && stage2Fired
    }

    private const val PREFS = "departure_alarms"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_SETTINGS = "settings"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun key(routeCode: String, stopId: String) = "${routeCode.uppercase()}|$stopId"

    // === Armed alarms ===

    /** Every armed alarm, with its fired stages. Auto-recovers stale states. */
    @Synchronized
    fun load(): List<Armed> {
        val raw = prefs?.getString(KEY_ALARMS, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        var needsResave = false

        val loaded = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val s1Fired = o.optBoolean("stage1Fired", false)
                val s2Fired = o.optBoolean("stage2Fired", false)
                val s1FiredAt = o.optLong("stage1FiredAt", 0L)
                val s2FiredAt = o.optLong("stage2FiredAt", 0L)

                // If Stage 1 fired over 20 mins ago and Stage 2 never fired, that vehicle is gone.
                // Reset stage 1 so the next bus on the route can trigger.
                val resetStaleStage1 = s1Fired && !s2Fired && (now - s1FiredAt > 20 * 60 * 1000L)
                // If both stages fired over 15 mins ago, reset both so subsequent buses or future
                // commute windows can trigger alerts without losing the alarm.
                val resetCompleted = s1Fired && s2Fired && (now - s2FiredAt > 15 * 60 * 1000L)

                if (resetStaleStage1 || resetCompleted) {
                    needsResave = true
                }

                val effS1 = if (resetStaleStage1 || resetCompleted) false else s1Fired
                val effS2 = if (resetCompleted) false else s2Fired
                val effS1At = if (resetStaleStage1 || resetCompleted) 0L else s1FiredAt
                val effS2At = if (resetCompleted) 0L else s2FiredAt

                Armed(
                    alert = DepartureAlert(
                        routeCode = o.getString("routeCode"),
                        stopId = o.getString("stopId"),
                        stopName = o.optString("stopName"),
                        isArmed = true,
                        stage1StopId = o.optString("stage1StopId").ifEmpty { o.getString("stopId") },
                        stage1StopName = o.optString("stage1StopName").ifEmpty { o.optString("stopName") },
                        stage2StopId = o.optString("stage2StopId").ifEmpty { o.getString("stopId") },
                        stage2StopName = o.optString("stage2StopName").ifEmpty { o.optString("stopName") }
                    ),
                    stage1Fired = effS1,
                    stage2Fired = effS2,
                    stage1FiredAt = effS1At,
                    stage2FiredAt = effS2At,
                    armedAt = o.optLong("armedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (needsResave) {
            save(loaded)
        }
        return loaded
    }

    @Synchronized
    private fun save(alarms: List<Armed>) {
        val arr = JSONArray()
        alarms.forEach { armed ->
            arr.put(
                JSONObject()
                    .put("routeCode", armed.alert.routeCode)
                    .put("stopId", armed.alert.stopId)
                    .put("stopName", armed.alert.stopName)
                    .put("stage1StopId", armed.alert.stage1StopId)
                    .put("stage1StopName", armed.alert.stage1StopName)
                    .put("stage2StopId", armed.alert.stage2StopId)
                    .put("stage2StopName", armed.alert.stage2StopName)
                    .put("stage1Fired", armed.stage1Fired)
                    .put("stage2Fired", armed.stage2Fired)
                    .put("stage1FiredAt", armed.stage1FiredAt)
                    .put("stage2FiredAt", armed.stage2FiredAt)
                    .put("armedAt", armed.armedAt)
            )
        }
        prefs?.edit()?.putString(KEY_ALARMS, arr.toString())?.apply()
    }

    /** Arms an alarm, replacing any earlier alarm for the same route and stop. */
    @Synchronized
    fun arm(alert: DepartureAlert) {
        val all = load().toMutableList()
        all.removeAll { it.alert.routeCode.equals(alert.routeCode, true) && it.alert.stopId == alert.stopId }
        all.add(
            Armed(
                alert = alert,
                stage1Fired = false,
                stage2Fired = false,
                stage1FiredAt = 0L,
                stage2FiredAt = 0L,
                armedAt = System.currentTimeMillis()
            )
        )
        save(all)
    }

    @Synchronized
    fun disarm(routeCode: String, stopId: String) {
        val all = load().toMutableList()
        all.removeAll { it.alert.routeCode.equals(routeCode, true) && it.alert.stopId == stopId }
        save(all)
    }

    /** Records that a stage has been announced. */
    @Synchronized
    fun markFired(routeCode: String, stopId: String, stage: Int) {
        val all = load().toMutableList()
        val index = all.indexOfFirst {
            it.alert.routeCode.equals(routeCode, true) && it.alert.stopId == stopId
        }
        if (index < 0) return
        val current = all[index]
        val now = System.currentTimeMillis()
        all[index] = when (stage) {
            1 -> current.copy(stage1Fired = true, stage1FiredAt = now)
            2 -> current.copy(stage2Fired = true, stage2FiredAt = now)
            else -> current
        }
        save(all)
    }

    /** Drops alarms that have already announced both stages, unless commute schedule is active. */
    @Synchronized
    fun pruneCompleted(): Int {
        val all = load()
        // If user has commute schedule enabled, retain alarms across their daily schedule
        if (AlertScheduleManager.config.value.enabled) return 0

        val now = System.currentTimeMillis()
        // Otherwise, only prune if completed more than 30 minutes ago
        val kept = all.filter { !it.isComplete || (now - it.stage2FiredAt < 30 * 60 * 1000L) }
        if (kept.size != all.size) save(kept)
        return all.size - kept.size
    }

}
