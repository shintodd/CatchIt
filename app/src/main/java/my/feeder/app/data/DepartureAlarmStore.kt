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
        /** Epoch millis the alarm was armed, so a stale one can be dropped. */
        val armedAt: Long = 0L
    ) {
        /** Both stages have been announced; the alarm has done its job. */
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

    /** Every armed alarm, with its fired stages. */
    @Synchronized
    fun load(): List<Armed> {
        val raw = prefs?.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Armed(
                    alert = DepartureAlert(
                        routeCode = o.getString("routeCode"),
                        stopId = o.getString("stopId"),
                        stopName = o.optString("stopName"),
                        isArmed = true,
                        // Alarms saved before the two-stop model had a single
                        // stop for both stages.
                        stage1StopId = o.optString("stage1StopId").ifEmpty { o.getString("stopId") },
                        stage1StopName = o.optString("stage1StopName").ifEmpty { o.optString("stopName") },
                        stage2StopId = o.optString("stage2StopId").ifEmpty { o.getString("stopId") },
                        stage2StopName = o.optString("stage2StopName").ifEmpty { o.optString("stopName") }
                    ),
                    stage1Fired = o.optBoolean("stage1Fired", false),
                    stage2Fired = o.optBoolean("stage2Fired", false),
                    armedAt = o.optLong("armedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
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
        all[index] = when (stage) {
            1 -> current.copy(stage1Fired = true)
            2 -> current.copy(stage2Fired = true)
            else -> current
        }
        save(all)
    }

    /** Drops alarms that have already announced both stages. */
    @Synchronized
    fun pruneCompleted(): Int {
        val all = load()
        val kept = all.filter { !it.isComplete }
        if (kept.size != all.size) save(kept)
        return all.size - kept.size
    }

}
