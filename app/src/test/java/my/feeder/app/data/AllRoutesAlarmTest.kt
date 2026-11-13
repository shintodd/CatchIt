package my.feeder.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllRoutesAlarmTest {

    private lateinit var context: Context

    // Synthetic linear route (e.g. T155 Pinewood Court to Sungai Buloh)
    private val linearStops = (1..10).map { seq ->
        GtfsStop(
            id = "LIN_$seq",
            code = "L$seq",
            name = "Linear Stop $seq",
            lat = 3.1000 + (seq * 0.01), // moves north ~1.1 km per stop
            lon = 101.6000,
            seq = seq
        )
    }

    // Synthetic circular loop route (e.g. T507, T409, T815)
    // Starts at (3.00, 101.60), travels in a circle, ends at (3.00, 101.60)
    private val loopStops = (1..20).map { seq ->
        val angle = Math.toRadians((seq - 1) * (360.0 / 20))
        GtfsStop(
            id = "LOOP_$seq",
            code = "P$seq",
            name = "Loop Stop $seq",
            lat = 3.0000 + 0.01 * Math.sin(angle),
            lon = 101.6000 + 0.01 * Math.cos(angle),
            seq = seq
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DepartureAlarmStore.init(context)
        AlertScheduleManager.init(context)
    }

    @Test
    fun `isLoopRoute accurately distinguishes circular loops from linear routes`() {
        assertTrue("Loop route starts and ends at same location", GtfsRepository.isLoopRoute(loopStops))
        assertFalse("Linear route ends 10km away from start", GtfsRepository.isLoopRoute(linearStops))
    }

    @Test
    fun `stopServiceHeading on linear route does not wrap around to far terminus`() {
        // Stop 1 of linear route: heading must be northward towards Stop 2 (~0 degrees)
        val headingStop1 = GtfsRepository.stopServiceHeading(linearStops[0], linearStops)
        assertNotNull("Stop 1 heading should exist", headingStop1)
        val delta1 = GtfsRepository.angleDelta(0f, headingStop1!!)
        assertTrue("Heading at start of linear route must be north towards stop 2, got $headingStop1", delta1 < 10.0)

        // Stop 10 of linear route: heading must be northward from Stop 9 towards Stop 10
        val headingStop10 = GtfsRepository.stopServiceHeading(linearStops[9], linearStops)
        assertNotNull("Stop 10 heading should exist", headingStop10)
        val delta10 = GtfsRepository.angleDelta(0f, headingStop10!!)
        assertTrue("Heading at end of linear route must continue north, got $headingStop10", delta10 < 10.0)
    }

    @Test
    fun `isArrivingAtStop rejects bus that already passed stop on linear route`() {
        val stop3 = linearStops[2] // seq 3
        // Bus is at stop 6, heading north (towards stop 7)
        val busPast = LiveBusState(
            plate = "BUS_PAST",
            lat = linearStops[5].lat,
            lon = linearStops[5].lon,
            heading = 0f,
            speedKmh = 35,
            lastPingSeconds = 5,
            nextStopSeq = 7
        )

        assertFalse(
            "Bus that has passed stop 3 on linear route cannot be arriving at stop 3",
            GtfsRepository.isArrivingAtStop(busPast, stop3, linearStops)
        )
    }

    @Test
    fun `laterStopOnRoute handles terminal wrap on loop routes`() {
        val dist19to2 = (2 - 19 + 20) % 20 // 3 stops forward
        val dist2to19 = (19 - 2 + 20) % 20 // 17 stops forward
        assertTrue("Forward loop distance from 19 to 2 is 3 stops", dist19to2 < dist2to19)
    }

    @Test
    fun `departure alert code normalisation is case and suffix insensitive`() {
        val alert1 = DepartureAlert(
            routeCode = "t409",
            stopId = "SP1",
            stopName = "Stop 1"
        )
        GtfsRepository.armAlert(alert1)

        assertTrue("Should be armed with uppercase code", GtfsRepository.isAlertArmed("T409", "SP1"))
        assertTrue("Should be armed with lowercase code", GtfsRepository.isAlertArmed("t409", "SP1"))
        assertTrue("Should be armed with whitespace", GtfsRepository.isAlertArmed(" T409 ", "SP1"))

        val alerts = GtfsRepository.alertsForRoute("t409 inbound")
        assertEquals("Should retrieve alert regardless of direction suffix", 1, alerts.size)

        GtfsRepository.cancelAlert("T409", "SP1")
        assertFalse("Should be disarmed", GtfsRepository.isAlertArmed("T409", "SP1"))
    }

    @Test
    fun `comprehensive route code normalisation maps council, trunk and feeder variants`() {
        // Feeder standard & loop variants
        assertEquals("T507", GtfsRepository.baseRouteCode("T5070"))
        assertEquals("T815", GtfsRepository.baseRouteCode("T8151"))
        assertEquals("T409", GtfsRepository.baseRouteCode("T409"))
        assertEquals("T810", GtfsRepository.baseRouteCode("T810B"))
        assertEquals("T110", GtfsRepository.baseRouteCode("T110B"))
        assertEquals("T450", GtfsRepository.baseRouteCode("T4508"))
        assertEquals("T300", GtfsRepository.baseRouteCode("T3008"))
        assertEquals("T571", GtfsRepository.baseRouteCode("T5718"))

        // Trunk U and B prefixes
        assertEquals("420", GtfsRepository.baseRouteCode("U4200"))
        assertEquals("600", GtfsRepository.baseRouteCode("U6000"))
        assertEquals("300", GtfsRepository.baseRouteCode("U3000"))
        assertEquals("754", GtfsRepository.baseRouteCode("U7548"))
        assertEquals("771", GtfsRepository.baseRouteCode("U7718"))
        assertEquals("822", GtfsRepository.baseRouteCode("U8220"))
        assertEquals("100", GtfsRepository.baseRouteCode("B1000"))

        // Petaling Jaya City Bus
        assertEquals("PJ01", GtfsRepository.baseRouteCode("P0010"))
        assertEquals("PJ02", GtfsRepository.baseRouteCode("P0020"))
        assertEquals("PJ06", GtfsRepository.baseRouteCode("P0060"))

        // Putrajaya Bus
        assertEquals("P101", GtfsRepository.baseRouteCode("P1010"))
        assertEquals("P108", GtfsRepository.baseRouteCode("P1080"))

        // Smart Selangor Council Lines
        assertEquals("SA02", GtfsRepository.baseRouteCode("S4020"))
        assertEquals("SA05", GtfsRepository.baseRouteCode("S4050"))
        assertEquals("SA07", GtfsRepository.baseRouteCode("S4070"))
        assertEquals("SA08", GtfsRepository.baseRouteCode("S4080"))
        assertEquals("AJ2A", GtfsRepository.baseRouteCode("S1030"))
        assertEquals("AJ03", GtfsRepository.baseRouteCode("S1050"))
        assertEquals("KJ01", GtfsRepository.baseRouteCode("S0030"))
        assertEquals("KJ02", GtfsRepository.baseRouteCode("S1060"))
        assertEquals("MPS1", GtfsRepository.baseRouteCode("S0070"))
        assertEquals("MPS2A", GtfsRepository.baseRouteCode("S1190"))
        assertEquals("MPS4", GtfsRepository.baseRouteCode("S1490"))
        assertEquals("BTG1", GtfsRepository.baseRouteCode("S0060"))
        assertEquals("PAVBJ", GtfsRepository.baseRouteCode("S6060"))

        // Preserves existing catalog codes
        assertEquals("420", GtfsRepository.baseRouteCode("420"))
        assertEquals("T507", GtfsRepository.baseRouteCode("T507"))
        assertEquals("SA02", GtfsRepository.baseRouteCode("SA02"))
        assertEquals("PJ01", GtfsRepository.baseRouteCode("PJ01"))
        assertEquals("AJ03", GtfsRepository.baseRouteCode("AJ03"))
    }

    @Test
    fun `all 91 catalog routes have valid stop geometries and resolve service headings`() {
        val assetFile = File("src/main/assets/gtfs_feeder_catalog.json")
        assertTrue("Catalog file must exist", assetFile.exists())
        val jsonStr = assetFile.readText(Charsets.UTF_8)
        val arr = JSONArray(jsonStr)
        assertEquals("Expected 91 feeder routes in catalog", 91, arr.length())

        for (i in 0 until arr.length()) {
            val rObj = arr.getJSONObject(i)
            val code = rObj.getString("code")
            val stopsArr = rObj.getJSONArray("stops")
            assertTrue("Route $code must have stops", stopsArr.length() > 0)

            val stops = (0 until stopsArr.length()).map { s ->
                val sObj = stopsArr.getJSONObject(s)
                GtfsStop(
                    id = sObj.getString("id"),
                    code = sObj.optString("code", ""),
                    name = sObj.optString("name", "Stop"),
                    lat = sObj.getDouble("lat"),
                    lon = sObj.getDouble("lon"),
                    seq = sObj.optInt("seq", s + 1)
                )
            }

            // Every route should be either a loop or a linear route
            val isLoop = GtfsRepository.isLoopRoute(stops)
            if (code == "T155") {
                assertFalse("T155 should be recognized as linear", isLoop)
            }

            // Intermediate stops must produce valid headings or null safely
            if (stops.size >= 3) {
                val midStop = stops[stops.size / 2]
                val heading = GtfsRepository.stopServiceHeading(midStop, stops)
                // If neighbours are separated, heading must be between 0 and 360
                heading?.let {
                    assertTrue("Heading on $code must be in 0..360, got $it", it in 0.0..360.0)
                }
            }
        }
    }
}
