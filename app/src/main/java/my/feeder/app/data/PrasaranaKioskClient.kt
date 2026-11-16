package my.feeder.app.data

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live bus positions from Prasarana's official kiosk AVL REST stream.
 *
 * data.gov.my GTFS-RT feed frequently omits routes (like T507) that are active
 * in operator dispatch. Prasarana's official kiosk serves all active vehicles via
 * REST endpoints:
 *   - Klang Valley: https://myrapidbus.prasarana.com.my/avl/api/avl/region/rkl
 *   - Penang: https://myrapidbus.prasarana.com.my/avl/api/avl/region/rpg
 */
object PrasaranaKioskClient {

    private const val BASE_URL = "https://myrapidbus.prasarana.com.my/avl/api/avl/region/"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    /** One vehicle as reported by the kiosk AVL stream. */
    data class KioskBus(
        val plate: String,
        /** Route code without the trailing direction digit, e.g. "T507". */
        val routeCode: String,
        /** Direction digit as reported, e.g. "01". */
        val direction: String,
        val lat: Double,
        val lon: Double,
        val bearing: Float,
        val speedKmh: Int,
        val gpsTimestamp: String
    )

    /**
     * Fetches every vehicle currently broadcasting to Prasarana's dispatch.
     * Merges Klang Valley ("rkl", 1000+ buses) and Penang ("rpg", 200+ buses).
     */
    fun fetchAllBuses(): List<KioskBus>? {
        val rkl = fetchRegion("rkl")
        val rpg = fetchRegion("rpg")
        return when {
            rkl != null && rpg != null -> rkl + rpg
            rkl != null -> rkl
            else -> rpg
        }
    }

    private fun fetchRegion(region: String): List<KioskBus>? {
        var conn: HttpURLConnection? = null
        return try {
            val url = "$BASE_URL$region"
            conn = open(url)
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w("KioskFeed", "Region $region HTTP $code")
                return null
            }

            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JSONObject(body)
            val vehiclesArr = root.optJSONArray("vehicles") ?: return null
            val buses = ArrayList<KioskBus>(vehiclesArr.length())

            for (i in 0 until vehiclesArr.length()) {
                val o = vehiclesArr.optJSONObject(i) ?: continue
                val lat = o.optDouble("latitude", Double.NaN)
                val lon = o.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val rawRoute = o.optString("route", "")
                val normalizedCode = stripDirectionSuffix(rawRoute)

                buses.add(
                    KioskBus(
                        plate = o.optString("bus_no", "Unknown"),
                        routeCode = normalizedCode,
                        direction = o.optString("dir", ""),
                        lat = lat,
                        lon = lon,
                        bearing = o.optDouble("angle", 0.0).toFloat(),
                        speedKmh = o.optDouble("speed", 0.0).toInt().coerceAtLeast(0),
                        gpsTimestamp = o.optString("dt_gps", "")
                    )
                )
            }
            buses
        } catch (e: Exception) {
            Log.w("KioskFeed", "Fetch region $region failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("Origin", "https://myrapidbus.prasarana.com.my")
            setRequestProperty("Referer", "https://myrapidbus.prasarana.com.my/kiosk")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) CatchIt/1.2.9")
        }

    /**
     * Known Prasarana internal route_id mappings to public catalog route codes.
     * Derived from official data.gov.my GTFS-static feed for Rapid Bus KL & Council services.
     */
    private val OPERATOR_ROUTE_MAP = mapOf(
        // Petaling Jaya City Bus (PJ01 to PJ06)
        "P001" to "PJ01", "P002" to "PJ02", "P003" to "PJ03",
        "P004" to "PJ04", "P005" to "PJ05", "P006" to "PJ06",
        // Putrajaya Bus (P101 to P108)
        "P101" to "P101", "P102" to "P102", "P103" to "P103",
        "P104" to "P104", "P105" to "P105", "P106" to "P106", "P108" to "P108",
        // Smart Selangor - Shah Alam
        "S402" to "SA02", "S405" to "SA05", "S407" to "SA07", "S408" to "SA08",
        // Smart Selangor - Ampang Jaya
        "S103" to "AJ2A", "S104" to "AJ2B", "S105" to "AJ03", "S114" to "AJ04", "S004" to "AJ01",
        // Smart Selangor - Kajang / Bangi
        "S003" to "KJ01", "S106" to "KJ02", "S107" to "KJ03", "S117" to "KJ04",
        // Smart Selangor - Selayang / Rawang
        "S007" to "MPS1", "S119" to "MPS2A", "S139" to "MPS3", "S149" to "MPS4",
        // Smart Selangor - Banting
        "S006" to "BTG1", "S206" to "BTG2",
        // Special shuttles & BRT
        "S606" to "PAVBJ", "S501" to "MENARA PRASARANA", "S012" to "HLB2",
        "B100" to "100",
        // Feeder loop variants
        "T4069" to "T406B", "T4001" to "T400B", "T5121" to "T512B"
    )

    /**
     * "T5070" -> "T507", "T1551" -> "T155", "T8520" -> "T852".
     * "U4200" -> "420", "U6000" -> "600", "B1000" -> "100".
     * "P0010" -> "PJ01", "S4070" -> "SA07", "T810B" -> "T810".
     * Strips operator prefixes, council mappings, and direction suffixes (digits or A/B).
     */
    internal fun stripDirectionSuffix(raw: String): String {
        val trimmed = raw.trim().uppercase()
        if (trimmed.length < 2) return trimmed

        // 1. Direct match in operator route map
        OPERATOR_ROUTE_MAP[trimmed]?.let { return it }

        // 2. Match with 1-character suffix dropped (e.g. P0010 -> P001 -> PJ01)
        if (trimmed.length >= 4) {
            OPERATOR_ROUTE_MAP[trimmed.dropLast(1)]?.let { return it }
        }

        var code = trimmed
        // 3. Operator trunk prefix 'U' or 'B' on numeric routes (e.g. U4200 -> 4200, B1000 -> 1000)
        if (code.length >= 2 && (code.startsWith("U") || code.startsWith("B")) && code[1].isDigit()) {
            code = code.substring(1)
        }

        // 4. 4-digit numeric route with direction digit (e.g. 4200 -> 420, 7548 -> 754, 6000 -> 600)
        if (code.length == 4 && code.all { it.isDigit() }) {
            code = code.dropLast(1)
        }
        // 5. 5-character feeder route with direction digit (e.g. T5070 -> T507, T8151 -> T815, T4508 -> T450)
        else if (code.startsWith("T") && code.length == 5 && code.substring(1).all { it.isDigit() }) {
            code = code.dropLast(1)
        }
        // 6. 5-character feeder route with letter direction suffix (e.g. T810B -> T810, T110A -> T110)
        else if (code.startsWith("T") && code.length == 5 && (code.endsWith("A") || code.endsWith("B")) && code.substring(1, 4).all { it.isDigit() }) {
            code = code.dropLast(1)
        }

        return code
    }
}
