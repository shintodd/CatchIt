package my.feeder.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GtfsStop(
    val id: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val seq: Int
)

data class GtfsRoute(
    val id: String,
    val code: String,
    val headsign: String,
    val stopsCount: Int,
    val stops: List<GtfsStop>,
    val shape: List<Pair<Double, Double>> // lat, lon
)

data class LiveBusState(
    val plate: String,
    val lat: Double,
    val lon: Double,
    val heading: Float,
    val speedKmh: Int,
    val lastPingSeconds: Int,
    val isDelayed: Boolean = false,
    val isStale: Boolean = false,
    val routeCode: String = "",
    val nextStopName: String = "",
    /** Sequence of the inferred next stop, or -1 when unknown. */
    val nextStopSeq: Int = -1,
    val etaMinutes: Int = -1,
    /**
     * True when the bus is stopped and parked at the route's first or last stop,
     * i.e. waiting at a terminal rather than running. The ETA to a downstream stop
     * would otherwise read as a misleading multi-minute countdown.
     */
    val isAtTerminal: Boolean = false
)

/**
 * One approaching bus, with how long it takes to reach each of the stops the
 * alarm cares about. Both stages of an alarm are measured against this single
 * bus, so the two warnings always describe the same vehicle.
 */
data class BusApproachWithEtas(
    val plate: String,
    val speedKmh: Int,
    /** Stop id to minutes away, for every stop asked for. */
    val etasByStopId: Map<String, Int>,
    /** The stops this bus is arriving at right now. */
    val arrivingStopIds: Set<String>
) {
    fun etaFor(stopId: String): Int? = etasByStopId[stopId]

    /** True when the bus is arriving at this stop. */
    fun isArrivingAt(stopId: String): Boolean = stopId in arrivingStopIds
}

/**
 * A two-stage departure alarm on one route.
 *
 * Each stage is anchored to its **own stop** on the route, not to a time
 * offset from a single stop. That is how people actually ride a feeder bus:
 *
 *  - **Stage 1** fires when the bus is a few minutes away from an early stop
 *    on the route — the point at which you should start moving.
 *  - **Stage 2** fires when the bus is a few minutes away from a later stop —
 *    the point at which you should be at your waiting spot.
 *
 * Both stages are measured against **the same approaching bus**, so a single
 * bus drives the whole sequence.
 *
 * [stopId] and [stopName] are the waiting stop, which is the stage 2 anchor.
 * They are kept as the canonical fields so older saved alarms still load.
 */
data class DepartureAlert(
    val routeCode: String,
    val stopId: String,
    val stopName: String,
    val isArmed: Boolean = true,
    /** The stop that triggers stage 1 — normally earlier on the route. */
    val stage1StopId: String = stopId,
    val stage1StopName: String = stopName,
    /** The stop that triggers stage 2 — normally later on the route. */
    val stage2StopId: String = stopId,
    val stage2StopName: String = stopName
) {
    /** True when both stages are anchored to the same stop. */
    val sameStop: Boolean get() = stage1StopId == stage2StopId
}

/** First-departure schedule for a route, e.g. ["06:00","06:15",…]. Loaded from the static GTFS feed. */
data class RouteSchedule(
    val routeCode: String,
    val departures: List<String>
)

enum class FeedFreshness {
    LIVE,
    DELAYED,
    STALE,
    SCHEDULED_ONLY
}

object GtfsRepository {
    /** Within this many metres of the first/last stop, a stopped bus counts as at a terminal. */
    private const val TERMINAL_RADIUS_M = 150.0

    /**
     * A kiosk vehicle whose last GPS fix is older than this is treated as offline.
     * Overnight the kiosk keeps returning a bus's last-known position long after it
     * has parked and stopped transmitting, which shows up as a "live" bus at a depot.
     * Anything past this age is dropped so the route reads "No bus online".
     */
    private const val GPS_STALE_SECONDS = 120

    /** Within this radius a bus counts as at the stop regardless of heading. */
    private const val APPROACH_RADIUS_M = 400.0

    /** How far off its heading toward the stop a bus may be and still count. */
    private const val APPROACH_CONE_DEG = 100.0

    /**
     * A bus counts as "arriving" at a stop when it is within this many
     * minutes of it, or when the stop is the bus's next stop on the route.
     *
     * The rider never sets this: an alarm is simply "tell me when the bus
     * reaches this stop".
     */
    private const val ARRIVING_ETA_MINUTES = 3

    /**
     * A bus within this many metres of a stop counts as being at that stop.
     *
     * This is the only distance that counts as "arriving" for a bus that is
     * not moving. A stopped bus gets a 20 km/h speed floor in the ETA maths,
     * which turns 700 m at a traffic light into "2 min" - the classic ghost
     * arrival. A stopped bus therefore only counts as arriving when it is
     * genuinely at the bay.
     */
    private const val AT_STOP_RADIUS_M = 50.0

    /**
     * A stopped bus whose next stop is this close is treated as being at the
     * light just short of the bay rather than somewhere else entirely.
     */
    private const val STOPPED_NEAR_BAY_M = 150.0

    /**
     * How far a bus's heading may differ from the direction the route travels
     * through a stop before it is treated as going the other way.
     *
     * On a dual carriageway the stop on the far side is barely 20 m away but
     * the bus is heading the other way and will never serve it.
     */
    private const val OPPOSITE_LANE_DEG = 90.0

    private val _routes = MutableStateFlow<List<GtfsRoute>>(emptyList())
    val routes: StateFlow<List<GtfsRoute>> = _routes.asStateFlow()

    private val _activeAlerts = MutableStateFlow<Map<String, DepartureAlert>>(emptyMap())
    val activeAlerts: StateFlow<Map<String, DepartureAlert>> = _activeAlerts.asStateFlow()

    private val _feedFreshness = MutableStateFlow(FeedFreshness.SCHEDULED_ONLY)
    val feedFreshness: StateFlow<FeedFreshness> = _feedFreshness.asStateFlow()

    private val _lastUpdatedSeconds = MutableStateFlow(-1)
    val lastUpdatedSeconds: StateFlow<Int> = _lastUpdatedSeconds.asStateFlow()

    /** Every live bus from the last successful fetch, keyed by normalised route code. */
    private val _liveBuses = MutableStateFlow<Map<String, List<LiveBusState>>>(emptyMap())
    val liveBuses: StateFlow<Map<String, List<LiveBusState>>> = _liveBuses.asStateFlow()

    /** True once a real feed response has been applied; false means nothing is known yet. */
    private val _hasLiveData = MutableStateFlow(false)
    val hasLiveData: StateFlow<Boolean> = _hasLiveData.asStateFlow()

    /** Route codes the user has starred/pinned, in pin order. Persisted. */
    private val _pinnedRoutes = MutableStateFlow<List<String>>(emptyList())
    val pinnedRoutes: StateFlow<List<String>> = _pinnedRoutes.asStateFlow()

    /** First-departure schedules keyed by route code. */
    private val _schedules = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val schedules: StateFlow<Map<String, List<String>>> = _schedules.asStateFlow()

    /** Nationwide candidate stops across all Malaysian states. */
    private val _nationwideStops = MutableStateFlow<List<StopCandidate>>(emptyList())
    val nationwideStops: StateFlow<List<StopCandidate>> = _nationwideStops.asStateFlow()

    /** Nationwide routes across all Malaysian transit agencies. */
    private val _nationwideRoutes = MutableStateFlow<List<NationwideRoute>>(emptyList())
    val nationwideRoutes: StateFlow<List<NationwideRoute>> = _nationwideRoutes.asStateFlow()

    private var pinPrefs: android.content.SharedPreferences? = null

    /** Binds pin persistence. Safe to call repeatedly; only the first call binds. */
    /**
     * Restores the armed alarms saved on disk, so an alarm set before the app
     * was closed is still armed when it reopens.
     */
    fun initAlarms(context: Context) {
        DepartureAlarmStore.init(context)
        val restored = DepartureAlarmStore.load()
        if (restored.isNotEmpty()) {
            _activeAlerts.value = restored.associate { alarm ->
                "${alarm.alert.routeCode}_${alarm.alert.stopId}" to alarm.alert
            }
        }
    }

    fun initPins(context: Context) {
        if (pinPrefs != null) return
        val p = context.applicationContext
            .getSharedPreferences("feeder_pins", Context.MODE_PRIVATE)
        pinPrefs = p
        val saved = p.getStringSet("routes", emptySet()) ?: emptySet()
        _pinnedRoutes.value = saved.toList().sorted()
    }

    fun togglePin(routeCode: String) {
        val code = routeCode.trim().uppercase()
        val current = _pinnedRoutes.value.toMutableList()
        val next = if (current.contains(code)) current.filterNot { it == code }
        else current + code
        _pinnedRoutes.value = next
        pinPrefs?.edit()?.putStringSet("routes", next.toSet())?.apply()
    }

    fun isPinned(routeCode: String): Boolean =
        _pinnedRoutes.value.contains(routeCode.trim().uppercase())

    private var lastFetchEpochSeconds = 0L

    /**
     * GTFS-RT reports route_id with a direction suffix, e.g. "T155 Inbound" /
     * "T155 Outbound", while catalogue codes are bare ("T155"). Keep the direction
     * so the two directions never collapse into one bucket and cross-contaminate
     * which bus the UI treats as "soonest".
     */
    private fun normaliseRouteCode(raw: String): String =
        raw.trim().uppercase()

    /** Catalogue lookup key: strips the direction suffix. */
    private fun baseRouteCode(normalised: String): String =
        normalised.substringBefore(' ')

    suspend fun loadCatalog(context: Context) = withContext(Dispatchers.IO) {
        if (_routes.value.isNotEmpty()) return@withContext
        try {
            val jsonString = context.assets.open("gtfs_feeder_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val parsedRoutes = ArrayList<GtfsRoute>(jsonArray.length())

            for (i in 0 until jsonArray.length()) {
                val rObj = jsonArray.getJSONObject(i)
                val id = rObj.getString("id")
                val code = rObj.getString("code")
                val headsign = rObj.getString("headsign")
                val stopsCount = rObj.getInt("stopsCount")

                val stopsArr = rObj.getJSONArray("stops")
                val stopsList = ArrayList<GtfsStop>(stopsArr.length())
                for (s in 0 until stopsArr.length()) {
                    val sObj = stopsArr.getJSONObject(s)
                    stopsList.add(
                        GtfsStop(
                            id = sObj.getString("id"),
                            code = sObj.optString("code", ""),
                            name = sObj.optString("name", "Stop"),
                            lat = sObj.optDouble("lat", 0.0),
                            lon = sObj.optDouble("lon", 0.0),
                            seq = sObj.optInt("seq", s + 1)
                        )
                    )
                }

                val shapeArr = rObj.getJSONArray("shape")
                val shapeList = ArrayList<Pair<Double, Double>>(shapeArr.length())
                for (sp in 0 until shapeArr.length()) {
                    val ptArr = shapeArr.getJSONArray(sp)
                    shapeList.add(Pair(ptArr.getDouble(0), ptArr.getDouble(1)))
                }

                parsedRoutes.add(
                    GtfsRoute(
                        id = id,
                        code = code,
                        headsign = headsign,
                        stopsCount = stopsCount,
                        stops = stopsList,
                        shape = shapeList
                    )
                )
            }

            _routes.value = parsedRoutes
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadNationwideCatalog(context: Context) = withContext(Dispatchers.IO) {
        if (_nationwideStops.value.isNotEmpty()) return@withContext
        try {
            val jsonString = context.assets.open("nationwide_transit_catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            val routesArr = root.optJSONArray("routes")
            val parsedRoutes = ArrayList<NationwideRoute>(routesArr?.length() ?: 0)
            if (routesArr != null) {
                for (i in 0 until routesArr.length()) {
                    val rObj = routesArr.getJSONObject(i)
                    parsedRoutes.add(
                        NationwideRoute(
                            code = rObj.getString("code"),
                            name = rObj.optString("name", ""),
                            agency = rObj.optString("agency", "")
                        )
                    )
                }
            }

            val stopsArr = root.optJSONArray("stops")
            val parsedStops = ArrayList<StopCandidate>(stopsArr?.length() ?: 0)
            if (stopsArr != null) {
                for (i in 0 until stopsArr.length()) {
                    val sObj = stopsArr.getJSONObject(i)
                    val rArr = sObj.optJSONArray("routes")
                    val rList = ArrayList<String>(rArr?.length() ?: 0)
                    if (rArr != null) {
                        for (j in 0 until rArr.length()) {
                            rList.add(rArr.getString(j))
                        }
                    }
                    parsedStops.add(
                        StopCandidate(
                            name = sObj.getString("name"),
                            lat = sObj.getDouble("lat"),
                            lon = sObj.getDouble("lon"),
                            routeCodes = rList,
                            agency = sObj.optString("agency", "RapidKL")
                        )
                    )
                }
            }

            _nationwideRoutes.value = parsedRoutes
            _nationwideStops.value = parsedStops
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Pulls live vehicle positions and rebuilds [_liveBuses].
     *
     * Sources are merged, not chosen: the kiosk AVL stream is authoritative for the
     * fleet (it covers routes data.gov.my omits entirely, such as T507), while the
     * GTFS-RT feed supplies spec-compliant records. A vehicle present in both is
     * taken once, preferring the kiosk reading because it is fresher per-vehicle.
     *
     * Returns true when at least one source produced data.
     */
    suspend fun refreshLiveBuses(): Boolean = withContext(Dispatchers.IO) {
        val gtfsVehicles = GtfsRealtimeClient.fetchVehiclePositions()
        val kioskBuses = PrasaranaKioskClient.fetchAllBuses()

        if (gtfsVehicles == null && kioskBuses == null) {
            // Both sources unreachable: keep last known positions but mark stale.
            _feedFreshness.value = if (_hasLiveData.value) FeedFreshness.STALE else FeedFreshness.SCHEDULED_ONLY
            _liveBuses.value = _liveBuses.value.mapValues { (_, buses) ->
                buses.map { it.copy(isStale = true) }
            }
            return@withContext false
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        lastFetchEpochSeconds = nowSeconds

        val grouped = HashMap<String, MutableList<LiveBusState>>()
        val routeIndex = _routes.value.associateBy { it.code.uppercase() }
        // Plates already contributed by the kiosk, so GTFS-RT does not duplicate them.
        val seen = HashSet<String>()

        // 1. Kiosk fleet first: widest coverage.
        kioskBuses?.forEach { b ->
            val code = b.routeCode.uppercase()
            if (code.isEmpty()) return@forEach
            seen.add("$code|${b.plate}")

            val age = gpsAgeSeconds(b.gpsTimestamp, nowSeconds)
            // Skip vehicles that stopped reporting: the kiosk keeps replaying their
            // last fix, which would otherwise look like a live bus parked at a depot.
            if (age > GPS_STALE_SECONDS) return@forEach
            val stops = routeIndex[code]?.stops ?: emptyList()
            val nextStop = stops.let { pickNextStop(it, b.lat, b.lon, b.bearing) }
            grouped.getOrPut(code) { ArrayList() }.add(
                buildState(
                    plate = b.plate,
                    lat = b.lat,
                    lon = b.lon,
                    bearing = b.bearing,
                    speedKmh = b.speedKmh,
                    ageSeconds = age,
                    code = code,
                    nextStop = nextStop,
                    routeStops = stops
                )
            )
        }

        // 2. GTFS-RT fills any gaps and adds vehicles the kiosk did not report.
        gtfsVehicles?.forEach { v ->
            val code = normaliseRouteCode(v.routeId)
            if (code.isEmpty()) return@forEach
            if (!seen.add("$code|${v.plate}")) return@forEach

            val age = (nowSeconds - v.timestampSeconds).coerceAtLeast(0L).toInt()
            // Same rule as the kiosk path: a vehicle that stopped reporting is offline.
            if (age > GPS_STALE_SECONDS) return@forEach
            val stops = routeIndex[baseRouteCode(code)]?.stops ?: emptyList()
            val nextStop = stops.let { pickNextStop(it, v.lat, v.lon, v.bearing) }
            grouped.getOrPut(code) { ArrayList() }.add(
                buildState(
                    plate = v.plate,
                    lat = v.lat,
                    lon = v.lon,
                    bearing = v.bearing,
                    speedKmh = v.speedKmh,
                    ageSeconds = age,
                    code = code,
                    nextStop = nextStop,
                    routeStops = stops
                )
            )
        }

        _liveBuses.value = grouped
        _hasLiveData.value = true
        _feedFreshness.value = FeedFreshness.LIVE
        _lastUpdatedSeconds.value = 0
        true
    }

    /** Refreshes the "seconds since last successful fetch" counter without touching the network. */
    fun tickFreshness() {
        if (!_hasLiveData.value || lastFetchEpochSeconds == 0L) return
        val now = System.currentTimeMillis() / 1000L
        val elapsed = (now - lastFetchEpochSeconds).coerceAtLeast(0L).toInt()
        _lastUpdatedSeconds.value = elapsed
        _feedFreshness.value = when {
            elapsed <= 45 -> FeedFreshness.LIVE
            elapsed <= 180 -> FeedFreshness.DELAYED
            else -> FeedFreshness.STALE
        }
    }

    /**
     * Loads first-departure schedules for every route from the bundled static GTFS
     * stop_times. Departures are per route (not per stop) and represent the first
     * stop's departure time — what a commuter sees on a timetable.
     */
    suspend fun loadSchedules(context: Context) = withContext(Dispatchers.IO) {
        if (_schedules.value.isNotEmpty()) return@withContext
        try {
            val json = context.assets.open("feeder_schedule.json").bufferedReader().use { it.readText() }
            val obj = org.json.JSONObject(json)
            val map = HashMap<String, List<String>>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val arr = obj.getJSONArray(code)
                val deps = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) deps.add(arr.getString(i))
                map[code] = deps
            }
            _schedules.value = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Upcoming departures for a route from the timetable, "HH:mm" strings. */
    fun scheduleFor(routeCode: String): List<String> =
        _schedules.value[routeCode.trim().uppercase()] ?: emptyList()

    /**
     * Live buses on a route. Matches both the bare catalogue code ("T155") and
     * direction-suffixed feed ids ("T155 INBOUND"), so a route with two directions
     * returns the vehicles from both rather than losing one silently.
     */
    fun liveBusesFor(routeCode: String): List<LiveBusState> {
        val want = routeCode.trim().uppercase()
        val exact = _liveBuses.value[want]
        if (exact != null) return exact

        // No exact bucket: gather every direction-suffixed variant of this code.
        return _liveBuses.value
            .filterKeys { it.substringBefore(' ') == want }
            .values
            .flatten()
    }

    /** Soonest arrival on a route, i.e. the bus a commuter would actually catch. */
    fun soonestBusFor(routeCode: String): LiveBusState? =
        liveBusesFor(routeCode).minByOrNull { if (it.etaMinutes < 0) Int.MAX_VALUE else it.etaMinutes }

    fun getRouteByCode(code: String): GtfsRoute? {
        return _routes.value.find { it.code.equals(code, ignoreCase = true) }
            ?: _routes.value.firstOrNull()
    }

    fun armAlert(alert: DepartureAlert) {
        val current = _activeAlerts.value.toMutableMap()
        val normalizedCode = baseRouteCode(normaliseRouteCode(alert.routeCode))
        val normalizedAlert = alert.copy(routeCode = normalizedCode)
        current["${normalizedCode}_${alert.stopId}"] = normalizedAlert
        _activeAlerts.value = current
        // The durable copy is what the background worker reads, so an alarm
        // survives the app being closed.
        DepartureAlarmStore.arm(normalizedAlert)
    }

    fun cancelAlert(routeCode: String, stopId: String) {
        val current = _activeAlerts.value.toMutableMap()
        val normalizedCode = baseRouteCode(normaliseRouteCode(routeCode))
        current.remove("${normalizedCode}_$stopId")
        _activeAlerts.value = current
        DepartureAlarmStore.disarm(normalizedCode, stopId)
    }

    /** Resyncs active alert flow with the persisted alarms store. */
    fun syncAlarmsFromStore() {
        val restored = DepartureAlarmStore.load()
        _activeAlerts.value = restored.associate { alarm ->
            val normalizedCode = baseRouteCode(normaliseRouteCode(alarm.alert.routeCode))
            "${normalizedCode}_${alarm.alert.stopId}" to alarm.alert.copy(routeCode = normalizedCode)
        }
    }

    fun isAlertArmed(routeCode: String, stopId: String): Boolean {
        val normalizedCode = baseRouteCode(normaliseRouteCode(routeCode))
        return _activeAlerts.value.containsKey("${normalizedCode}_$stopId")
    }

    /** Great-circle distance in metres. */
    internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * True when a bus stopped at this position is parked at the route's first
     * or last stop — waiting at a terminal or depot rather than running the
     * route.
     *
     * Feeder routes are continuous loops, so the driver takes a 15-30 minute
     * break between runs with the engine idling and the transponder still
     * pinging. The terminal is within 150 m of the first and last stop, so a
     * distance-only test says the bus is about to serve the route. It is on
     * a break.
     */
    internal fun isAtTerminal(
        lat: Double,
        lon: Double,
        speedKmh: Int,
        routeStops: List<GtfsStop>
    ): Boolean {
        if (speedKmh >= 3 || routeStops.isEmpty()) return false
        val first = routeStops.minByOrNull { it.seq } ?: return false
        val last = routeStops.maxByOrNull { it.seq } ?: return false
        val nearFirst = haversineMeters(lat, lon, first.lat, first.lon) < TERMINAL_RADIUS_M
        val nearLast = haversineMeters(lat, lon, last.lat, last.lon) < TERMINAL_RADIUS_M
        return nearFirst || nearLast
    }

    /** Builds a LiveBusState with ETA derived from the resolved next stop. */
    private fun buildState(
        plate: String,
        lat: Double,
        lon: Double,
        bearing: Float,
        speedKmh: Int,
        ageSeconds: Int,
        code: String,
        nextStop: GtfsStop?,
        routeStops: List<GtfsStop>
    ): LiveBusState {
        // A bus sitting still at either end of the loop is waiting at a terminal,
        // not running. Reporting "38 min" to a downstream stop from there is
        // technically true but useless — the commuter reads "at terminal" instead.
        val atTerminal = isAtTerminal(lat, lon, speedKmh, routeStops)

        val eta = if (atTerminal) {
            -1
        } else {
            nextStop?.let {
                estimateEtaMinutes(
                    busLat = lat,
                    busLon = lon,
                    stopLat = it.lat,
                    stopLon = it.lon,
                    speedKmh = speedKmh
                )
            } ?: -1
        }

        return LiveBusState(
            plate = plate,
            lat = lat,
            lon = lon,
            heading = bearing,
            speedKmh = speedKmh,
            lastPingSeconds = ageSeconds,
            isDelayed = ageSeconds > 120,
            isStale = ageSeconds > 300,
            routeCode = code,
            nextStopName = nextStop?.name ?: "",
            nextStopSeq = nextStop?.seq ?: -1,
            etaMinutes = eta,
            isAtTerminal = atTerminal
        )
    }

    /**
     * Age of a kiosk GPS fix. The kiosk reports Malaysia local wall-clock text
     * ("2026-11-08 00:38:22") with no offset. It must be parsed in MYT explicitly:
     * parsing in the device timezone makes the result depend on where the phone is
     * set, and on a non-MYT device the age can come out negative and clamp to zero,
     * making parked buses look permanently live.
     */
    internal fun gpsAgeSeconds(gpsTimestamp: String, nowEpochSeconds: Long): Int {
        if (gpsTimestamp.isBlank()) return 0
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")
            val parsed = fmt.parse(gpsTimestamp) ?: return 0
            val age = nowEpochSeconds - (parsed.time / 1000L)
            age.coerceIn(0L, 86_400L).toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Picks the stop a bus is heading toward.
     *
     * The GTFS-RT feed omits current_stop_sequence/stop_id, so this infers the next
     * stop from geometry plus heading: among stops within a sensible radius, prefer
     * the nearest one that lies ahead of the reported bearing. Falling back to plain
     * nearest-by-distance is wrong on a loop — it reports the stop the bus just left.
     */
    private fun pickNextStop(
        stops: List<GtfsStop>,
        busLat: Double,
        busLon: Double,
        bearing: Float
    ): GtfsStop? {
        if (stops.isEmpty()) return null

        // Only consider stops plausibly ahead on the route.
        val maxAheadMeters = 3000.0
        val candidates = stops.mapNotNull { stop ->
            val distance = haversineMeters(busLat, busLon, stop.lat, stop.lon)
            if (distance > maxAheadMeters) return@mapNotNull null

            // Bearing from the bus to this stop, 0..360.
            val toStopBearing = bearingDegrees(busLat, busLon, stop.lat, stop.lon)
            val delta = angleDelta(bearing, toStopBearing)

            // 0 deg = dead ahead, 180 deg = directly behind.
            Triple(stop, distance, delta)
        }

        if (candidates.isEmpty()) {
            // Nothing within range: fall back to nearest overall so the UI still
            // shows something meaningful rather than an empty label.
            return stops.minByOrNull { haversineMeters(busLat, busLon, it.lat, it.lon) }
        }

        // A bus crawling at a bay often reports a stale bearing, so allow a wide
        // cone when nearly stationary and tighten it when the bus is moving fast.
        val aheadOnly = candidates.filter { it.third <= 75.0 }
        val pool = aheadOnly.ifEmpty { candidates }

        // Prefer ahead-of-bearing stops; among those take the closest.
        return pool.minByOrNull { it.second }?.first
    }

    /** Compass bearing in degrees from point 1 to point 2. */
    internal fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest absolute difference between two bearings, 0..180. */
    internal fun angleDelta(a: Float, b: Double): Double {
        val diff = Math.abs((a.toDouble() - b) % 360.0)
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /**
     * Finds the bus a commuter would catch, and how long it takes that bus to
     * reach each of the given stops.
     *
     * The bus is chosen by the **anchor** stop — the last stop of the journey,
     * because that is the one the commuter is actually waiting for. Every stop
     * in [stopIds] is then measured against that same bus, so stage 1 and
     * stage 2 of an alarm always describe the same vehicle rather than two
     * different buses.
     */
    fun approachingBusWithEtas(
        routeCode: String,
        anchorStopId: String,
        stopIds: List<String>
    ): BusApproachWithEtas? {
        val code = baseRouteCode(normaliseRouteCode(routeCode))
        val route = _routes.value.firstOrNull { it.code.uppercase() == code } ?: return null
        val anchor = route.stops.firstOrNull { it.id == anchorStopId } ?: return null

        val buses = _liveBuses.value
            .filterKeys { baseRouteCode(it) == code }
            .values
            .flatten()
            .filter { !it.isStale }
        if (buses.isEmpty()) return null

        var bestBus: LiveBusState? = null
        var bestEta = Int.MAX_VALUE

        for (bus in buses) {
            // A bus idling at a terminal or depot is on a scheduled break. It
            // is not on its way to the commuter, and it must never be the bus
            // that "arrives". Once it pulls away it re-enters the pool.
            if (bus.isAtTerminal) continue

            // A bus that has already gone past the anchor is going the wrong
            // way for this journey. Loop routes wrap along the loop; linear routes cannot.
            if (bus.nextStopSeq > 0 && anchor.seq > 0) {
                val isLoop = isLoopRoute(route.stops)
                val totalStops = route.stops.size
                if (isLoop && totalStops > 0) {
                    val stopsAhead = (anchor.seq - bus.nextStopSeq + totalStops) % totalStops
                    if (stopsAhead > (totalStops * 0.85).toInt()) continue
                } else {
                    if (bus.nextStopSeq > anchor.seq) continue
                }
            }

            val metres = haversineMeters(bus.lat, bus.lon, anchor.lat, anchor.lon)
            // A stopped bus has no meaningful heading - it is sitting at a
            // terminal or a red light. Its heading is stale or zero, so
            // filtering on it would throw away the very bus that is about to
            // depart. The stop-sequence check above already rules out buses
            // travelling the other way.
            val isMoving = bus.speedKmh >= 5
            if (isMoving) {
                // The heading has to agree with the direction of the stop at
                // EVERY distance, not only beyond the approach radius. A bus
                // on the opposite carriageway of a dual carriageway is barely
                // 20 m away but heading the other way, and a distance-only
                // test calls that an arrival.
                val toStop = bearingDegrees(bus.lat, bus.lon, anchor.lat, anchor.lon)
                if (angleDelta(bus.heading, toStop) > APPROACH_CONE_DEG) continue
            }
            val eta = estimateEtaMinutes(bus.lat, bus.lon, anchor.lat, anchor.lon, bus.speedKmh)
            if (eta < bestEta) {
                bestEta = eta
                bestBus = bus
            }
        }

        val chosen = bestBus ?: return null
        val etas = LinkedHashMap<String, Int>()
        val arriving = LinkedHashSet<String>()
        for (id in stopIds) {
            val stop = route.stops.firstOrNull { it.id == id } ?: continue
            val eta = estimateEtaMinutes(chosen.lat, chosen.lon, stop.lat, stop.lon, chosen.speedKmh)
            etas[id] = eta
            if (isArrivingAtStop(chosen, stop, route.stops)) arriving.add(id)
        }
        return BusApproachWithEtas(
            plate = chosen.plate,
            speedKmh = chosen.speedKmh,
            etasByStopId = etas,
            arrivingStopIds = arriving
        )
    }

    /**
     * True when the route forms a continuous loop (starts and terminates at the same station/area).
     * Linear routes (point-to-point) have distant start and end terminals.
     */
    internal fun isLoopRoute(routeStops: List<GtfsStop>): Boolean {
        if (routeStops.size < 2) return false
        val first = routeStops.first()
        val last = routeStops.last()
        return haversineMeters(first.lat, first.lon, last.lat, last.lon) < 1000.0
    }

    /**
     * The direction the route travels through [stop], worked out from the
     * stops either side of it.
     *
     * A loop route is served in one direction only, so this says which way the
     * bus must be going to serve that stop. It is the reliable way to spot the
     * opposite carriageway of a dual carriageway: the stop on the far side of
     * the road is 20 m away, but the bus serving it is heading the other way.
     *
     * Returns null when the route has too few stops, or when the neighbours
     * are too close together to give a meaningful direction.
     */
    internal fun stopServiceHeading(stop: GtfsStop, routeStops: List<GtfsStop>): Double? {
        if (routeStops.size < 2) return null
        val ordered = routeStops.sortedBy { it.seq }
        val idx = ordered.indexOfFirst { it.seq == stop.seq }
        if (idx < 0) return null
        val isLoop = isLoopRoute(ordered)
        val (fromStop, toStop) = when {
            idx == 0 && !isLoop -> ordered[0] to ordered[1]
            idx == ordered.size - 1 && !isLoop -> ordered[ordered.size - 2] to ordered[ordered.size - 1]
            else -> {
                val prev = ordered[(idx - 1 + ordered.size) % ordered.size]
                val next = ordered[(idx + 1) % ordered.size]
                prev to next
            }
        }
        if (haversineMeters(fromStop.lat, fromStop.lon, toStop.lat, toStop.lon) < 50.0) return null
        return bearingDegrees(fromStop.lat, fromStop.lon, toStop.lat, toStop.lon)
    }

    /**
     * Whether [bus] is genuinely arriving at [stop].
     *
     * This is the guard against the **ghost arrival**: the app says the bus
     * is here, the commuter runs to the stop, and nothing comes for half an
     * hour. Three things cause it, and each is handled here:
     *
     * 1. **Terminal layover.** The driver is on a 15-30 minute break with the
     *    engine idling and the transponder still pinging. The bus is within
     *    150 m of the first or last stop, so it looks like it is about to
     *    serve the route. It is not.
     * 2. **Opposite carriageway.** On a dual carriageway the stop on the other
     *    side is 18 m away, but the bus is heading the other way.
     * 3. **The speed floor.** A stopped bus is given a 20 km/h floor so the
     *    ETA maths does not divide by zero. That turns a bus 700 m away at a
     *    red light into "2 min away". A bus that is not moving only counts
     *    as arriving when it is actually at the bay.
     */
    internal fun isArrivingAtStop(
        bus: LiveBusState,
        stop: GtfsStop,
        routeStops: List<GtfsStop>
    ): Boolean {
        // On a break at the terminal. Not arriving, however close it looks.
        if (bus.isAtTerminal) return false

        val metres = haversineMeters(bus.lat, bus.lon, stop.lat, stop.lon)

        // 1. Strict sequence check:
        // A bus cannot be "arriving" at a stop if it is still multiple stops behind!
        if (bus.nextStopSeq > 0 && stop.seq > 0) {
            val totalStops = routeStops.size
            val isLoop = isLoopRoute(routeStops)
            val stopsAway = if (isLoop && totalStops > 0) {
                (stop.seq - bus.nextStopSeq + totalStops) % totalStops
            } else {
                stop.seq - bus.nextStopSeq
            }
            // On a linear route, if bus has passed stop (stopsAway < 0), it is not arriving.
            if (stopsAway < 0 || stopsAway > 1) return false
            if (stopsAway == 1 && metres > 400.0) return false
        }

        // 2. Physical distance ceiling: A bus more than 450m away in straight line can NEVER be arriving now.
        // Prevents cross-town false triggers on winding feeder loops.
        if (metres > APPROACH_RADIUS_M + 50.0) return false

        if (bus.speedKmh >= 5) {
            // Moving. The direction has to agree with the road service heading.
            val serviceHeading = stopServiceHeading(stop, routeStops)
            if (serviceHeading != null &&
                angleDelta(bus.heading, serviceHeading) > OPPOSITE_LANE_DEG
            ) return false

            val toStop = bearingDegrees(bus.lat, bus.lon, stop.lat, stop.lon)
            if (angleDelta(bus.heading, toStop) > APPROACH_CONE_DEG) return false

            return metres <= APPROACH_RADIUS_M
        }

        // Stopped. Only count it as arriving when it is actually at the bay:
        // the 20 km/h speed floor used for the display ETA would otherwise
        // turn a bus 700 m away at a red light into "2 min".
        if (metres <= AT_STOP_RADIUS_M) return true

        // Stopped at the light just short of the bay.
        if (bus.nextStopSeq > 0 && stop.seq > 0 && bus.nextStopSeq == stop.seq &&
            metres <= STOPPED_NEAR_BAY_M
        ) return true

        return false
    }

    /**
     * Which of two stops comes later along the route.
     *
     * The two stages of an alarm are anchored on different stops, and the bus
     * that matters is the one heading for the later one. When the route's stop
     * sequence is unknown, fall back to the second argument.
     * For circular routes, determines downstream direction along the loop.
     */
    fun laterStopOnRoute(routeCode: String, stopIdA: String, stopIdB: String): String {
        val code = baseRouteCode(normaliseRouteCode(routeCode))
        val route = _routes.value.firstOrNull { it.code.uppercase() == code } ?: return stopIdB
        val seqA = route.stops.firstOrNull { it.id == stopIdA }?.seq ?: -1
        val seqB = route.stops.firstOrNull { it.id == stopIdB }?.seq ?: -1
        if (seqA < 0 || seqB < 0) return stopIdB
        if (seqA == seqB) return stopIdB

        val isLoop = isLoopRoute(route.stops)
        val totalStops = route.stops.size
        return if (isLoop && totalStops > 0) {
            val distAtoB = (seqB - seqA + totalStops) % totalStops
            val distBtoA = (seqA - seqB + totalStops) % totalStops
            if (distAtoB <= distBtoA) stopIdB else stopIdA
        } else {
            if (seqB >= seqA) stopIdB else stopIdA
        }
    }

    /** Every armed alarm on a route, newest state first. */
    fun alertsForRoute(routeCode: String): List<DepartureAlert> {
        val code = baseRouteCode(normaliseRouteCode(routeCode))
        return _activeAlerts.value
            .filterKeys { baseRouteCode(it.substringBefore('_')) == code }
            .values
            .toList()
    }

    /**
     * ETA from straight-line distance and reported speed.
     * Falls back to a 20 km/h city-average when the bus is stopped, which is
     * typical at a bay or red light and would otherwise read as "arriving now".
     */
    internal fun estimateEtaMinutes(
        busLat: Double,
        busLon: Double,
        stopLat: Double,
        stopLon: Double,
        speedKmh: Int
    ): Int {
        val metres = haversineMeters(busLat, busLon, stopLat, stopLon)
        // 1.35 road-winding factor: straight-line distance understates real driving distance.
        val roadMetres = metres * 1.35
        val effectiveKmh = if (speedKmh < 5) 20.0 else speedKmh.toDouble()
        val minutes = (roadMetres / 1000.0) / effectiveKmh * 60.0
        return minutes.toInt().coerceIn(0, 180)
    }
}
