package my.feeder.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live MRT feeder bus positions from Malaysia's national Open API.
 *
 * Endpoint: https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana
 * Category: rapid-bus-mrtfeeder (the 91 RapidKL MRT feeder routes CatchIt covers)
 * Auth: none. Licence: CC BY 4.0 (data.gov.my). Upstream refreshes every 30s.
 */
object GtfsRealtimeClient {

    // Trailing slash matters: the non-slash form returns 301, and HttpURLConnection
    // does not reliably follow HTTPS redirects. This form returns 200 directly.
    private const val ENDPOINT =
        "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana/?category=rapid-bus-mrtfeeder"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    /** Blocking fetch. Call from Dispatchers.IO. Returns null when the feed is unreachable. */
    fun fetchVehiclePositions(): List<GtfsRealtimeDecoder.Vehicle>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/x-protobuf, application/octet-stream, */*")
                setRequestProperty("User-Agent", "CatchIt/1.0.4 (Android; data.gov.my GTFS-RT)")
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) return null

            val body = conn.inputStream.use { it.readBytes() }
            if (body.isEmpty()) return null

            GtfsRealtimeDecoder.decodeVehiclePositions(body)
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
