package my.feeder.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches live bus positions from Malaysia's national Open API (data.gov.my).
 *
 * Endpoint: https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana
 * Categories: rapid-bus-mrtfeeder, rapid-bus-kl, rapid-bus-penang
 * Throttling: In-memory 20-second cache prevents HTTP 429 rate limit errors.
 */
object GtfsRealtimeClient {

    private const val BASE_URL =
        "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana/?category="

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000
    private const val CACHE_TTL_MS = 20_000L // 20s in-memory throttle cache

    private val cache = ConcurrentHashMap<String, Pair<Long, List<GtfsRealtimeDecoder.Vehicle>>>()

    /** Blocking fetch with 20s cache throttle protection. Call from Dispatchers.IO. */
    fun fetchVehiclePositions(
        category: String = "rapid-bus-mrtfeeder",
        forceRefresh: Boolean = false
    ): List<GtfsRealtimeDecoder.Vehicle>? {
        val now = System.currentTimeMillis()
        val cached = cache[category]

        if (!forceRefresh && cached != null && (now - cached.first < CACHE_TTL_MS)) {
            return cached.second
        }

        var conn: HttpURLConnection? = null
        return try {
            val endpoint = "$BASE_URL$category"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/x-protobuf, application/octet-stream, */*")
                setRequestProperty("User-Agent", "CatchIt/1.2.9 (Android; data.gov.my GTFS-RT)")
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                if (code == 429 && cached != null) {
                    return cached.second
                }
                return null
            }

            val body = conn.inputStream.use { it.readBytes() }
            if (body.isEmpty()) return null

            val vehicles = GtfsRealtimeDecoder.decodeVehiclePositions(body)
            cache[category] = Pair(now, vehicles)
            vehicles
        } catch (e: IOException) {
            cached?.second
        } catch (e: Exception) {
            cached?.second
        } finally {
            conn?.disconnect()
        }
    }
}
