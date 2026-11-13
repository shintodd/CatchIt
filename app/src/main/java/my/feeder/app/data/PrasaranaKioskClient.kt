package my.feeder.app.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Live bus positions from Prasarana's own kiosk AVL stream.
 *
 * The public data.gov.my GTFS-RT feed is incomplete: it frequently omits whole
 * routes even while the operator is tracking them (T507 and ~32 others have been
 * observed live on the kiosk map but absent from GTFS-RT). The kiosk instead
 * streams every vehicle over Socket.IO, and the frame payload is base64(gzip(JSON)).
 *
 * This client speaks the minimum engine.io/socket.io protocol needed to read that
 * stream: HTTP long-poll handshake, then repeated GETs of the same session. That
 * avoids pulling in a full socket.io client dependency.
 */
object PrasaranaKioskClient {

    private const val HOST = "rapidbus-socketio-avl.prasarana.com.my"
    private const val SID_TOKEN = "veminbhqgau60up1v3kl2mnvjprne5fj"
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
     * Fetches every vehicle currently broadcasting to the kiosk.
     *
     * An empty route filter returns the whole fleet in one frame, so this needs a
     * single handshake plus one poll instead of 91 per-route round trips.
     */
    fun fetchAllBuses(): List<KioskBus>? {
        val session = openSession() ?: return null
        return try {
            requestFleet(session)
        } catch (e: Exception) {
            android.util.Log.w("KioskFeed", "fetch failed: ${e.message}")
            null
        } finally {
            closeSession(session)
        }
    }

    private data class Session(val sid: String)

    /** engine.io v3 handshake: GET /socket.io/?EIO=3&transport=polling */
    private fun openSession(): Session? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open("https://$HOST/socket.io/?EIO=3&transport=polling&t=${System.currentTimeMillis()}")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            // Frame looks like: 96:0{"sid":"...","upgrades":[...],"pingInterval":...}
            val jsonStart = body.indexOf('{')
            if (jsonStart < 0) return null
            val json = body.substring(jsonStart)
            val sid = Regex("\"sid\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            sid?.let { Session(it) }
        } catch (e: IOException) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Sends the socket.io CONNECT for the default namespace, drains its ack,
     * subscribes with an empty route (whole fleet), then reads the reply frame.
     */
    private fun requestFleet(session: Session): List<KioskBus>? {
        // 40 = socket.io "connect" packet on the default namespace.
        post("https://$HOST/socket.io/?EIO=3&transport=polling&sid=${session.sid}", "40")

        // The connect ack arrives on the next poll and must be consumed before the
        // subscription payload can be read.
        poll(session.sid)

        val subscribe = """42["onFts-reload",{"sid":"$SID_TOKEN","uid":"","provider":"RKL","route":""}]"""
        post("https://$HOST/socket.io/?EIO=3&transport=polling&sid=${session.sid}", subscribe)

        // Poll until an onFts-client frame arrives; a few empty polls are normal.
        repeat(10) {
            val body = poll(session.sid) ?: return null
            parseFrame(body)?.let { if (it.isNotEmpty()) return it }
        }
        return null
    }

    private fun post(url: String, payload: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "text/plain;charset=UTF-8")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: IOException) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun poll(sid: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open("https://$HOST/socket.io/?EIO=3&transport=polling&sid=$sid&t=${System.currentTimeMillis()}")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun closeSession(session: Session) {
        try {
            post("https://$HOST/socket.io/?EIO=3&transport=polling&sid=${session.sid}", "41")
        } catch (e: Exception) {
            // Best-effort cleanup; a dropped session expires on its own.
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Origin", "https://myrapidbus.prasarana.com.my")
            setRequestProperty("Referer", "https://myrapidbus.prasarana.com.my/")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) CatchIt/1.0.4")
        }

    /**
     * Extracts the onFts-client payload from an engine.io poll frame.
     *
     * The server replies with engine.io v4 binary framing even on EIO=3:
     *   <0x00><len><0xFF><packet> repeated
     * where len is 1 byte (or 1 byte + 0xFF marker + 2-byte length when long).
     * Older text framing ("<len>:<packet>") is also tolerated.
     */
    private fun parseFrame(frame: String): List<KioskBus>? {
        // Text framing path (legacy): "<len>:<packet>"
        if (frame.isNotEmpty() && frame[0].isDigit()) {
            var cursor = 0
            while (cursor < frame.length) {
                val colon = frame.indexOf(':', cursor)
                if (colon < 0) break
                val len = frame.substring(cursor, colon).toIntOrNull() ?: break
                val start = colon + 1
                val end = (start + len).coerceAtMost(frame.length)
                extractPayload(frame.substring(start, end))?.let { return it }
                cursor = end
            }
            return null
        }

        // Binary framing path: strip the 0x00 / length / 0xFF envelope bytes and
        // look for the socket.io event text inside what remains.
        return extractPayload(frame)
    }

    /** Finds an onFts-client event in a packet and decodes its base64 argument. */
    private fun extractPayload(packet: String): List<KioskBus>? {
        if (!packet.contains("onFts-client")) return null

        // The argument is the quoted string after the event name:
        //   42["onFts-client","<base64>"]
        val marker = "\"onFts-client\",\""
        val start = packet.indexOf(marker)
        if (start < 0) return null
        val argStart = start + marker.length
        val argEnd = packet.indexOf('"', argStart)
        if (argEnd <= argStart) return null

        val encoded = packet.substring(argStart, argEnd)
        return decodePayload(encoded)
    }

    private fun decodePayload(encoded: String): List<KioskBus>? {
        val json = try {
            val gzipped = Base64.decode(encoded, Base64.DEFAULT)
            GZIPInputStream(gzipped.inputStream()).use { stream ->
                val out = ByteArrayOutputStream()
                stream.copyTo(out)
                out.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            return null
        }
        return parseBuses(json)
    }

    private fun parseBuses(json: String): List<KioskBus>? {
        return try {
            val arr = org.json.JSONArray(json)
            val buses = ArrayList<KioskBus>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lat = o.optDouble("latitude", Double.NaN)
                val lon = o.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                // Kiosk reports route as code + direction digit, e.g. "T5070" / "T1551".
                val rawRoute = o.optString("route", "")
                val code = stripDirectionSuffix(rawRoute)

                buses.add(
                    KioskBus(
                        plate = o.optString("bus_no", "Unknown"),
                        routeCode = code,
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
            null
        }
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
