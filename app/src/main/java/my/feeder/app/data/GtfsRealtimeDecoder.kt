package my.feeder.app.data

/**
 * Minimal GTFS-Realtime protobuf reader for vehicle positions.
 *
 * data.gov.my serves GTFS-RT as protobuf. Rather than pulling in the protobuf
 * runtime plus generated bindings (and the R8 keep rules they need), this reads
 * only the wire fields CatchIt actually uses. GTFS-RT is append-only and
 * field-number stable, so unknown fields are skipped safely.
 *
 * Field numbers follow the official gtfs-realtime.proto:
 *   FeedMessage.entity   = 2
 *   FeedEntity.vehicle   = 4
 *   VehiclePosition.trip = 1, position = 2, timestamp = 5
 *   TripDescriptor.trip_id = 1, route_id = 5
 *   VehicleDescriptor.id = 1
 *   Position.latitude = 1 (float), longitude = 2 (float), bearing = 3 (float),
 *   odometer = 4 (double), speed = 5 (float)
 */
object GtfsRealtimeDecoder {

    data class Vehicle(
        val plate: String,
        val tripId: String,
        val routeId: String,
        val lat: Double,
        val lon: Double,
        val bearing: Float,
        val speedKmh: Int,
        val timestampSeconds: Long
    )

    private const val WIRE_VARINT = 0
    private const val WIRE_64BIT = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_32BIT = 5

    /** Decodes a FeedMessage payload into vehicle position records. */
    fun decodeVehiclePositions(bytes: ByteArray): List<Vehicle> {
        val out = ArrayList<Vehicle>()
        val reader = Reader(bytes)

        while (reader.hasMore()) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wire = tag and 0x7
            if (field == 2 && wire == WIRE_LENGTH) {
                val entityBytes = reader.readLengthDelimited()
                parseEntity(entityBytes)?.let { out.add(it) }
            } else {
                reader.skip(wire)
            }
        }
        return out
    }

    private fun parseEntity(bytes: ByteArray): Vehicle? {
        val reader = Reader(bytes)
        var vehicleBytes: ByteArray? = null

        while (reader.hasMore()) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wire = tag and 0x7
            if (field == 4 && wire == WIRE_LENGTH) {
                vehicleBytes = reader.readLengthDelimited()
            } else {
                reader.skip(wire)
            }
        }

        val vBytes = vehicleBytes ?: return null
        return parseVehicle(vBytes)
    }

    private fun parseVehicle(bytes: ByteArray): Vehicle? {
        val reader = Reader(bytes)
        var plate = ""
        var tripId = ""
        var routeId = ""
        var lat = Double.NaN
        var lon = Double.NaN
        var bearing = 0f
        var speedMs = 0f
        var timestamp = 0L

        while (reader.hasMore()) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wire = tag and 0x7
            when {
                field == 1 && wire == WIRE_LENGTH -> {
                    // TripDescriptor
                    val t = Reader(reader.readLengthDelimited())
                    while (t.hasMore()) {
                        val tTag = t.readTag()
                        val tField = tTag ushr 3
                        val tWire = tTag and 0x7
                        when {
                            tField == 1 && tWire == WIRE_LENGTH -> tripId = t.readString()
                            tField == 5 && tWire == WIRE_LENGTH -> routeId = t.readString()
                            else -> t.skip(tWire)
                        }
                    }
                }
                field == 2 && wire == WIRE_LENGTH -> {
                    // Position
                    val p = Reader(reader.readLengthDelimited())
                    while (p.hasMore()) {
                        val pTag = p.readTag()
                        val pField = pTag ushr 3
                        val pWire = pTag and 0x7
                        when {
                            // latitude/longitude/bearing/speed are proto `float` = fixed32.
                            pField == 1 && pWire == WIRE_32BIT -> lat = p.readFixed32Float().toDouble()
                            pField == 2 && pWire == WIRE_32BIT -> lon = p.readFixed32Float().toDouble()
                            pField == 3 && pWire == WIRE_32BIT -> bearing = p.readFixed32Float()
                            // odometer (field 4) is the only double here; speed is field 5.
                            pField == 5 && pWire == WIRE_32BIT -> speedMs = p.readFixed32Float()
                            else -> p.skip(pWire)
                        }
                    }
                }
                field == 5 && wire == WIRE_VARINT -> timestamp = reader.readVarint()
                field == 8 && wire == WIRE_LENGTH -> {
                    // VehicleDescriptor
                    val v = Reader(reader.readLengthDelimited())
                    while (v.hasMore()) {
                        val vTag = v.readTag()
                        val vField = vTag ushr 3
                        val vWire = vTag and 0x7
                        when {
                            vField == 1 && vWire == WIRE_LENGTH -> plate = v.readString()
                            else -> v.skip(vWire)
                        }
                    }
                }
                else -> reader.skip(wire)
            }
        }

        // A position without coordinates is not usable on the map.
        if (lat.isNaN() || lon.isNaN()) return null
        if (lat == 0.0 && lon == 0.0) return null

        return Vehicle(
            plate = plate.ifBlank { "Unknown" },
            tripId = tripId,
            routeId = routeId,
            lat = lat,
            lon = lon,
            bearing = bearing,
            // data.gov.my reports this field in km/h directly (non-standard vs the
            // GTFS-RT spec's m/s). Observed values (0–60) are textbook bus speeds in
            // km/h and would be absurd (200+ km/h) if treated as m/s.
            speedKmh = speedMs.toInt().coerceAtLeast(0),
            timestampSeconds = timestamp
        )
    }

    /** Cursor over a protobuf byte array. */
    private class Reader(private val buf: ByteArray) {
        private var pos = 0

        fun hasMore(): Boolean = pos < buf.size

        fun readTag(): Int = readVarint().toInt()

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (pos < buf.size) {
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) return result
            }
            return result
        }

        fun readLengthDelimited(): ByteArray {
            val len = readVarint().toInt()
            val end = (pos + len).coerceAtMost(buf.size)
            val slice = buf.copyOfRange(pos, end)
            pos = end
            return slice
        }

        fun readString(): String = readLengthDelimited().toString(Charsets.UTF_8)

        fun readFixed64Double(): Double {
            var bits = 0L
            for (i in 0 until 8) {
                if (pos < buf.size) {
                    bits = bits or ((buf[pos++].toLong() and 0xFF) shl (8 * i))
                }
            }
            return Double.fromBits(bits)
        }

        fun readFixed32Float(): Float {
            var bits = 0
            for (i in 0 until 4) {
                if (pos < buf.size) {
                    bits = bits or ((buf[pos++].toInt() and 0xFF) shl (8 * i))
                }
            }
            return Float.fromBits(bits)
        }

        /** Advances past a field this decoder does not read. */
        fun skip(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_64BIT -> pos = (pos + 8).coerceAtMost(buf.size)
                WIRE_LENGTH -> {
                    val len = readVarint().toInt()
                    pos = (pos + len).coerceAtMost(buf.size)
                }
                WIRE_32BIT -> pos = (pos + 4).coerceAtMost(buf.size)
                else -> pos = buf.size
            }
        }
    }
}
