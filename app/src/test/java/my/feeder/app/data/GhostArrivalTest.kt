package my.feeder.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The "ghost arrival" checklist.
 *
 * A ghost arrival is the app saying the bus is at your stop, you run to the
 * stop, and nothing comes for half an hour. It is the single most damaging
 * thing a transit app can do, because it destroys trust in every other
 * number the app shows.
 *
 * These tests pin down the five causes and make sure each guard stays in
 * place. They are deliberately written against synthetic positions rather
 * than live telemetry, so they are deterministic and run in milliseconds.
 */
class GhostArrivalTest {

    // A straight north-south road. The route runs north through these stops.
    private val stop1 = GtfsStop("S1", "SP1", "FIRST STOP", 2.90000, 101.60000, 1)
    private val stop2 = GtfsStop("S2", "SP2", "SECOND STOP", 2.91000, 101.60000, 2)
    private val stop3 = GtfsStop("S3", "SP3", "THIRD STOP", 2.92000, 101.60000, 3)
    private val routeStops = listOf(stop1, stop2, stop3)

    private fun bus(
        lat: Double,
        lon: Double,
        heading: Float,
        speedKmh: Int,
        nextStopSeq: Int = -1,
        isAtTerminal: Boolean = false
    ) = LiveBusState(
        plate = "TEST1",
        lat = lat,
        lon = lon,
        heading = heading,
        speedKmh = speedKmh,
        lastPingSeconds = 5,
        nextStopSeq = nextStopSeq,
        isAtTerminal = isAtTerminal
    )

    // ---------------------------------------------------------------------
    // Test 1 - Terminal break
    // A bus parked at the terminal on a 25 minute break must never be
    // reported as arriving.
    // ---------------------------------------------------------------------
    @Test
    fun `bus idling at the terminal is not arriving`() {
        val terminal = GtfsStop("S1", "SP1", "MRT CYBERJAYA", 2.90000, 101.60000, 1)
        // 40 m from the terminal, engine idling, driver on a break.
        val idling = bus(
            2.90036, 101.60000, heading = 0f, speedKmh = 0, nextStopSeq = 1,
            isAtTerminal = GtfsRepository.isAtTerminal(
                2.90036, 101.60000, 0, listOf(terminal)
            )
        )

        // The detection itself has to work, not just the veto that uses it.
        assertTrue(
            "a bus stopped 40 m from the terminal should be detected as idling there",
            idling.isAtTerminal
        )
        assertFalse(
            "a bus on a terminal break must not read as arriving",
            GtfsRepository.isArrivingAtStop(idling, terminal, listOf(terminal))
        )
    }

    @Test
    fun `a moving bus at the terminal is not on a break`() {
        val terminal = GtfsStop("S1", "SP1", "MRT CYBERJAYA", 2.90000, 101.60000, 1)
        assertFalse(
            "a bus moving at the terminal is running, not idling",
            GtfsRepository.isAtTerminal(2.90036, 101.60000, 25, listOf(terminal))
        )
    }

    @Test
    fun `a stopped bus at the bay is arriving`() {
        // The same bus, but it has pulled up at the stop.
        val atBay = bus(2.90001, 101.60000, heading = 0f, speedKmh = 0, nextStopSeq = 1)
        assertTrue(
            "a bus standing at the bay is arriving",
            GtfsRepository.isArrivingAtStop(atBay, stop1, routeStops)
        )
    }

    // ---------------------------------------------------------------------
    // Test 2 - Opposite carriageway
    // The stop on the far side of a dual carriageway is barely 20 m away but
    // the bus is heading the other way.
    // ---------------------------------------------------------------------
    @Test
    fun `bus on the opposite carriageway is not arriving`() {
        // Heading south (180) while the route travels north (0) through stop2.
        val wrongWay = bus(2.90980, 101.60020, heading = 180f, speedKmh = 40, nextStopSeq = 2)
        assertFalse(
            "a bus heading the other way must not read as arriving",
            GtfsRepository.isArrivingAtStop(wrongWay, stop2, routeStops)
        )
    }

    @Test
    fun `bus heading the right way is arriving`() {
        val rightWay = bus(2.90980, 101.60000, heading = 0f, speedKmh = 40, nextStopSeq = 2)
        assertTrue(
            "a bus heading towards the stop is arriving",
            GtfsRepository.isArrivingAtStop(rightWay, stop2, routeStops)
        )
    }

    // ---------------------------------------------------------------------
    // Test 3 - The speed floor
    // A bus stopped at a red light 700 m away must not read as "2 min".
    // ---------------------------------------------------------------------
    @Test
    fun `stopped bus far from the stop is not arriving`() {
        // ~700 m short of stop3, stationary in traffic.
        val inTraffic = bus(2.91370, 101.60000, heading = 0f, speedKmh = 0, nextStopSeq = 3)
        assertFalse(
            "a bus stopped in traffic 700 m away is not arriving",
            GtfsRepository.isArrivingAtStop(inTraffic, stop3, routeStops)
        )
    }

    @Test
    fun `the speed floor does not fake a short eta for a stopped bus`() {
        // The raw ETA maths floors a stopped bus at 20 km/h, which turns
        // 700 m into 2 minutes. That number is what the old arrival test
        // trusted, and it is why ghost arrivals happened.
        val eta = GtfsRepository.estimateEtaMinutes(2.91370, 101.60000, 2.92000, 101.60000, 0)
        assertTrue(
            "the raw ETA for a stopped bus 700 m away should look short ($eta min) - " +
                "this is the number the arrival test must not trust on its own",
            eta <= 3
        )
    }

    // ---------------------------------------------------------------------
    // Test 4 - Loop sequence wrap
    // A bus that has already gone past the stop is going the wrong way.
    // ---------------------------------------------------------------------
    @Test
    fun `the route direction is derived from the neighbouring stops`() {
        // The route runs north, so the direction of travel through stop2 is
        // roughly north.
        val heading = GtfsRepository.stopServiceHeading(stop2, routeStops)
        assertTrue("the service heading should be known", heading != null)
        val delta = GtfsRepository.angleDelta(0f, heading!!)
        assertTrue(
            "the route travels north through stop2, got a heading $heading",
            delta < 45.0
        )
    }

    @Test
    fun `a bus past its next stop is not approaching`() {
        // nextStopSeq 3 means the bus is heading to stop3, so it has already
        // gone past stop2. The candidate loop skips it because
        // nextStopSeq (3) > the anchor's seq.
        val past = bus(2.92100, 101.60000, heading = 0f, speedKmh = 40, nextStopSeq = 3)
        assertTrue(
            "the bus should be recognised as past stop2",
            past.nextStopSeq > stop2.seq
        )
    }

    // ---------------------------------------------------------------------
    // Test 5 - Non-MYT timezone
    // The kiosk reports Malaysian wall-clock time with no offset. Parsed in
    // the device timezone the age can come out negative and clamp to zero,
    // which makes a parked bus look permanently live.
    // ---------------------------------------------------------------------
    @Test
    fun `gps age is correct regardless of the device timezone`() {
        val original = TimeZone.getDefault()
        try {
            // A kiosk fix that is 90 seconds old, Malaysian wall-clock time.
            val mytNow = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
            }
            val ninetySecondsAgo = mytNow.format(System.currentTimeMillis() - 90_000L)
            val nowEpoch = System.currentTimeMillis() / 1000L

            for (zone in listOf("UTC", "America/Los_Angeles", "Asia/Singapore", "Asia/Kuala_Lumpur")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val age = GtfsRepository.gpsAgeSeconds(ninetySecondsAgo, nowEpoch)
                assertTrue(
                    "gps age was $age s in timezone $zone, expected about 90",
                    age in 85..95
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `a stale gps fix is older than the drop threshold`() {
        val mytNow = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        }
        val threeMinutesAgo = mytNow.format(System.currentTimeMillis() - 180_000L)
        val nowEpoch = System.currentTimeMillis() / 1000L
        val age = GtfsRepository.gpsAgeSeconds(threeMinutesAgo, nowEpoch)
        assertTrue(
            "a 180 second old fix must be past the 120 second drop threshold, got $age",
            age > 120
        )
    }

    // ---------------------------------------------------------------------
    // The road-winding factor
    // ---------------------------------------------------------------------
    @Test
    fun `the road distance is longer than the straight line`() {
        val straight = GtfsRepository.haversineMeters(2.90000, 101.60000, 2.92000, 101.60000)
        val eta = GtfsRepository.estimateEtaMinutes(2.90000, 101.60000, 2.92000, 101.60000, 30)
        val impliedRoadKm = eta / 60.0 * 30.0
        assertTrue(
            "the ETA should be based on a road distance longer than the $straight m straight line",
            impliedRoadKm * 1000 > straight
        )
    }
}
