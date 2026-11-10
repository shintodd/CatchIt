package my.feeder.app

import kotlinx.coroutines.runBlocking
import my.feeder.app.data.DestinationResult
import my.feeder.app.data.DestinationSearchEngine
import my.feeder.app.data.GtfsRoute
import my.feeder.app.data.GtfsStop
import my.feeder.app.data.LandmarkAliasTable
import my.feeder.app.data.NearbyStopRouteMatch
import my.feeder.app.data.StopCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSearchEngineTest {

    // ========================================================================
    // 1. Spatial Haversine Distance Calculations
    // ========================================================================

    @Test
    fun `distance between identical coordinates is zero`() {
        val dist = DestinationSearchEngine.distanceMeters(3.1415, 101.6869, 3.1415, 101.6869)
        assertEquals(0, dist)
    }

    @Test
    fun `distance calculation is symmetric`() {
        val lat1 = 3.1342
        val lon1 = 101.6861
        val lat2 = 3.1177
        val lon2 = 101.6775

        val d1 = DestinationSearchEngine.distanceMeters(lat1, lon1, lat2, lon2)
        val d2 = DestinationSearchEngine.distanceMeters(lat2, lon2, lat1, lon1)

        assertEquals(d1, d2)
        assertTrue(d1 > 2000 && d1 < 2100) // ~2066 meters
    }

    @Test
    fun `distance along one degree of latitude near equator is accurate within meters`() {
        // At equator, 1 degree latitude = 2 * pi * 6371000 / 360 ~ 111,195 m
        val dist = DestinationSearchEngine.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("Distance $dist should be close to 111195m", Math.abs(dist - 111195) <= 5)
    }

    @Test
    fun `distance between known bus stops in Kota Damansara is accurate`() {
        // MRT Surian Pintu B (3.149946, 101.593092) to Dataran Sunway Timur (3.151753, 101.595633)
        val dist = DestinationSearchEngine.distanceMeters(3.149946, 101.593092, 3.151753, 101.595633)
        // Straight line distance is ~344m
        assertTrue("Distance $dist should be around 344m", dist in 340..350)
    }

    // ========================================================================
    // 2. findNearbyRoutes Ranking & Gathering Route Codes
    // ========================================================================

    @Test
    fun `findNearbyRoutes correctly ranks stops by ascending distance`() {
        val destLat = 3.0716 // Near Hospital Shah Alam
        val destLon = 101.4903

        val stops = listOf(
            StopCandidate(name = "Far Stop", lat = 3.0770, lon = 101.4903, routeCode = "T753"), // ~600m
            StopCandidate(name = "Close Stop", lat = 3.0725, lon = 101.4903, routeCode = "T754"), // ~100m
            StopCandidate(name = "Medium Stop", lat = 3.0745, lon = 101.4903, routeCode = "T753") // ~322m
        )

        val matches = DestinationSearchEngine.findNearbyRoutes(destLat, destLon, stops, maxRadiusMeters = 800)

        assertEquals(3, matches.size)
        assertEquals("Close Stop", matches[0].stopName)
        assertEquals("Medium Stop", matches[1].stopName)
        assertEquals("Far Stop", matches[2].stopName)

        assertTrue(matches[0].distanceMeters < matches[1].distanceMeters)
        assertTrue(matches[1].distanceMeters < matches[2].distanceMeters)
    }

    @Test
    fun `findNearbyRoutes gathers multiple route codes for the same physical stop`() {
        val destLat = 3.1500
        val destLon = 101.6000

        // Single physical stop served by multiple routes (e.g., T808, T809, T810)
        val stops = listOf(
            StopCandidate(name = "Stesen MRT Surian", lat = 3.1499, lon = 101.6001, routeCode = "T808"),
            StopCandidate(name = "Stesen MRT Surian", lat = 3.1501, lon = 101.6002, routeCode = "T809"),
            StopCandidate(name = "Stesen MRT Surian", lat = 3.1499, lon = 101.6000, routeCode = "T810")
        )

        val matches = DestinationSearchEngine.findNearbyRoutes(destLat, destLon, stops, maxRadiusMeters = 500)

        assertEquals(1, matches.size)
        val match = matches[0]
        assertEquals("Stesen MRT Surian", match.stopName)
        assertEquals(listOf("T808", "T809", "T810"), match.routeCodes)
        assertEquals("RapidKL", match.agency)
    }

    @Test
    fun `findNearbyRoutes excludes stops outside max radius`() {
        val destLat = 3.0000
        val destLon = 101.5000

        val stops = listOf(
            StopCandidate(name = "Within Radius", lat = 3.0030, lon = 101.5000, routeCode = "T101"), // ~333m
            StopCandidate(name = "Outside Radius", lat = 3.0100, lon = 101.5000, routeCode = "T102")  // ~1112m
        )

        val matches = DestinationSearchEngine.findNearbyRoutes(destLat, destLon, stops, maxRadiusMeters = 800)

        assertEquals(1, matches.size)
        assertEquals("Within Radius", matches[0].stopName)
    }

    @Test
    fun `findNearbyRoutes works directly with GtfsRoute objects`() {
        val destLat = 3.1200
        val destLon = 101.6500

        val stopA = GtfsStop(id = "s1", code = "KL101", name = "Pintu Utama UM", lat = 3.1210, lon = 101.6505, seq = 1)
        val stopB = GtfsStop(id = "s2", code = "KL102", name = "Masjid Ar-Rahman", lat = 3.1230, lon = 101.6520, seq = 2)
        val stopC = GtfsStop(id = "s3", code = "KL101", name = "Pintu Utama UM", lat = 3.1211, lon = 101.6504, seq = 1)

        val route1 = GtfsRoute(
            id = "r1",
            code = "T789",
            headsign = "MRT UNIVERSITI - UM",
            stopsCount = 2,
            stops = listOf(stopA, stopB),
            shape = emptyList()
        )
        val route2 = GtfsRoute(
            id = "r2",
            code = "T790",
            headsign = "LRT KERINCHI - UM",
            stopsCount = 1,
            stops = listOf(stopC),
            shape = emptyList()
        )

        val matches = DestinationSearchEngine.findNearbyRoutes(
            destLat = destLat,
            destLon = destLon,
            routes = listOf(route1, route2),
            maxRadiusMeters = 800
        )

        assertEquals(2, matches.size)
        assertEquals("Pintu Utama UM", matches[0].stopName)
        assertEquals(listOf("T789", "T790"), matches[0].routeCodes)
        assertEquals("Masjid Ar-Rahman", matches[1].stopName)
        assertEquals(listOf("T789"), matches[1].routeCodes)
    }

    // ========================================================================
    // 3. Geocoder Landmark Resolution (Malaysia Bias & Key POIs)
    // ========================================================================

    @Test
    fun `resolves Hospital Shah Alam to correct GPS coordinates`() = runBlocking {
        val result = DestinationSearchEngine.resolveDestination("Hospital Shah Alam")
        assertNotNull(result)
        assertEquals("Hospital Shah Alam", result!!.name)
        assertTrue("Latitude should match Shah Alam Seksyen 7", Math.abs(result.lat - 3.0716) < 0.001)
        assertTrue("Longitude should match Shah Alam Seksyen 7", Math.abs(result.lon - 101.4903) < 0.001)
    }

    @Test
    fun `resolves Mid Valley to correct GPS coordinates`() = runBlocking {
        val result = DestinationSearchEngine.resolveDestination("Mid Valley")
        assertNotNull(result)
        assertEquals("Mid Valley Megamall", result!!.name)
        assertTrue("Latitude should match Mid Valley KL", Math.abs(result.lat - 3.1177) < 0.001)
        assertTrue("Longitude should match Mid Valley KL", Math.abs(result.lon - 101.6775) < 0.001)
    }

    @Test
    fun `resolves Gurney Plaza to correct Penang GPS coordinates`() = runBlocking {
        val result = DestinationSearchEngine.resolveDestination("Gurney Plaza")
        assertNotNull(result)
        assertEquals("Gurney Plaza", result!!.name)
        assertTrue("Latitude should match George Town", Math.abs(result.lat - 5.4377) < 0.001)
        assertTrue("Longitude should match George Town", Math.abs(result.lon - 100.3098) < 0.001)
    }

    @Test
    fun `resolves JB Sentral to correct Johor Bahru GPS coordinates`() = runBlocking {
        val result = DestinationSearchEngine.resolveDestination("JB Sentral")
        assertNotNull(result)
        assertEquals("JB Sentral", result!!.name)
        assertTrue("Latitude should match Johor Bahru", Math.abs(result.lat - 1.4628) < 0.001)
        assertTrue("Longitude should match Johor Bahru", Math.abs(result.lon - 103.7644) < 0.001)
    }

    @Test
    fun `landmark resolution is case-insensitive and supports alias synonyms`() = runBlocking {
        val r1 = DestinationSearchEngine.resolveDestination("mid valley megamall")
        val r2 = DestinationSearchEngine.resolveDestination("MID VALLEY")
        val r3 = DestinationSearchEngine.resolveDestination("shah alam hospital")
        val r4 = DestinationSearchEngine.resolveDestination("hsa")
        val r5 = DestinationSearchEngine.resolveDestination("1 utama")
        val r6 = DestinationSearchEngine.resolveDestination("one utama")
        val r7 = DestinationSearchEngine.resolveDestination("dpulze")
        val r8 = DestinationSearchEngine.resolveDestination("d'pulze")
        val r9 = DestinationSearchEngine.resolveDestination("d pulze")

        assertNotNull(r1)
        assertNotNull(r2)
        assertNotNull(r3)
        assertNotNull(r4)
        assertNotNull(r5)
        assertNotNull(r6)
        assertNotNull(r7)
        assertNotNull(r8)
        assertNotNull(r9)

        assertEquals("Mid Valley Megamall", r1!!.name)
        assertEquals("Mid Valley Megamall", r2!!.name)
        assertEquals("Hospital Shah Alam", r3!!.name)
        assertEquals("Hospital Shah Alam", r4!!.name)
        assertEquals("1 Utama Shopping Centre", r5!!.name)
        assertEquals("1 Utama Shopping Centre", r6!!.name)
        assertEquals("DPULZE Shopping Centre", r7!!.name)
        assertEquals("DPULZE Shopping Centre", r8!!.name)
        assertEquals("DPULZE Shopping Centre", r9!!.name)
    }

    @Test
    fun `blank or whitespace query returns null safely without exceptions`() = runBlocking {
        assertNull(DestinationSearchEngine.resolveDestination(""))
        assertNull(DestinationSearchEngine.resolveDestination("   "))
    }

    // ========================================================================
    // 4. End-to-End searchDestination
    // ========================================================================

    @Test
    fun `searchDestination resolves place and finds nearest bus routes in one call`() = runBlocking {
        val destQuery = "Hospital Shah Alam"

        // Simulated GTFS routes serving Section 7 Shah Alam
        val route = GtfsRoute(
            id = "T753",
            code = "T753",
            headsign = "HOSPITAL SHAH ALAM - SEKSYEN 7",
            stopsCount = 2,
            stops = listOf(
                GtfsStop("s1", "SA101", "HOSPITAL SHAH ALAM (BARAT)", 3.0720, 101.4905, 1),
                GtfsStop("s2", "SA102", "PINTU BELAKANG UITM", 3.0690, 101.4950, 2)
            ),
            shape = emptyList()
        )

        val result: DestinationResult? = DestinationSearchEngine.searchDestination(
            query = destQuery,
            routes = listOf(route),
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Hospital Shah Alam", result!!.name)
        assertEquals(2, result.nearestStops.size)
        assertEquals("HOSPITAL SHAH ALAM (BARAT)", result.nearestStops[0].stopName)
        assertEquals(listOf("T753"), result.nearestStops[0].routeCodes)
        assertTrue("Distance should be within 100 meters", result.nearestStops[0].distanceMeters < 100)
    }

    // ========================================================================
    // 5. Nationwide Malaysia Search Tests
    // ========================================================================

    @Test
    fun `searchDestination with dpulze finds nearby stop DPULZE serving T505`() = runBlocking {
        val dpulzeStop = StopCandidate(
            name = "D'PULZE",
            lat = 2.92167,
            lon = 101.65141,
            routeCodes = listOf("T505"),
            agency = "RapidKL"
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "dpulze",
            routes = emptyList(),
            nationwideStops = listOf(dpulzeStop),
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("DPULZE Shopping Centre", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("D'PULZE", result.nearestStops[0].stopName)
        assertTrue(result.nearestStops[0].distanceMeters <= 50)
        assertEquals(listOf("T505"), result.nearestStops[0].routeCodes)
    }

    @Test
    fun `searchDestination with nationwide stops for Penang resolves Gurney Plaza and Rapid Penang routes`() = runBlocking {
        val nationwideStops = listOf(
            StopCandidate(
                name = "Gurney Plaza",
                lat = 5.4371,
                lon = 100.3094,
                routeCodes = listOf("101", "102", "103"),
                agency = "Rapid Penang"
            )
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "Gurney Plaza",
            routes = emptyList(),
            nationwideStops = nationwideStops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Gurney Plaza", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("Gurney Plaza", result.nearestStops[0].stopName)
        assertEquals("Rapid Penang", result.nearestStops[0].agency)
        assertTrue(result.nearestStops[0].routeCodes.contains("101"))
    }

    @Test
    fun `searchDestination with nationwide stops for Johor resolves JB Sentral and myBAS routes`() = runBlocking {
        val nationwideStops = listOf(
            StopCandidate(
                name = "JB Sentral",
                lat = 1.4626,
                lon = 103.7638,
                routeCodes = listOf("J10", "J100", "J11"),
                agency = "myBAS JB"
            )
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "JB Sentral",
            routes = emptyList(),
            nationwideStops = nationwideStops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("JB Sentral", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("myBAS JB", result.nearestStops[0].agency)
        assertTrue(result.nearestStops[0].routeCodes.contains("J10"))
    }

    @Test
    fun `searchDestination with nationwide stops for Ipoh resolves Medan Kidd and myBAS Ipoh routes`() = runBlocking {
        val nationwideStops = listOf(
            StopCandidate(
                name = "STESEN BAS MEDAN KIDD",
                lat = 4.5942,
                lon = 101.0723,
                routeCodes = listOf("A100", "A101A", "A30A"),
                agency = "myBAS Ipoh"
            )
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "medan kidd",
            routes = emptyList(),
            nationwideStops = nationwideStops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Stesen Bas Medan Kidd", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("myBAS Ipoh", result.nearestStops[0].agency)
        assertTrue(result.nearestStops[0].routeCodes.contains("A100"))
    }

    @Test
    fun `searchDestination with nationwide stops for Melaka resolves Mahkota Parade and myBAS Melaka routes`() = runBlocking {
        val nationwideStops = listOf(
            StopCandidate(
                name = "Dataran Pahlawan",
                lat = 2.1905,
                lon = 102.2505,
                routeCodes = listOf("M100"),
                agency = "myBAS Melaka"
            )
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "mahkota parade",
            routes = emptyList(),
            nationwideStops = nationwideStops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Mahkota Parade / Dataran Pahlawan", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("myBAS Melaka", result.nearestStops[0].agency)
        assertTrue(result.nearestStops[0].routeCodes.contains("M100"))
    }

    @Test
    fun `searchDestination with nationwide stops for Kuching resolves Kuching Waterfront and myBAS Kuching routes`() = runBlocking {
        val nationwideStops = listOf(
            StopCandidate(
                name = "TERMINAL SAUJANA PARKING",
                lat = 1.5583,
                lon = 110.3444,
                routeCodes = listOf("Q01", "Q05"),
                agency = "myBAS Kuching"
            )
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "kuching waterfront",
            routes = emptyList(),
            nationwideStops = nationwideStops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Kuching Waterfront", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("myBAS Kuching", result.nearestStops[0].agency)
        assertTrue(result.nearestStops[0].routeCodes.contains("Q01"))
    }

    // ========================================================================
    // 7. Universal Local Stops Gazette Resolution (Offline <1ms)
    // ========================================================================

    @Test
    fun `resolveFromStops matches arbitrary school clinic or residential taman`() {
        val stops = listOf(
            StopCandidate(name = "Klinik Kesihatan Jinjang", lat = 3.2197, lon = 101.6544, routeCode = "T117"),
            StopCandidate(name = "SMK Damansara Utama", lat = 3.1360, lon = 101.6210, routeCode = "T813"),
            StopCandidate(name = "SK Taman Melawati", lat = 3.2100, lon = 101.7500, routeCode = "222"),
            StopCandidate(name = "D'PULZE Shopping Centre", lat = 2.9217, lon = 101.6514, routeCode = "T505")
        )

        // Exact slug match without apostrophe
        val dpulzeMatch = DestinationSearchEngine.resolveFromStops("dpulze", stops)
        assertNotNull(dpulzeMatch)
        assertEquals("D'PULZE Shopping Centre", dpulzeMatch!!.name)

        // Multi-token match for clinic
        val clinicMatch = DestinationSearchEngine.resolveFromStops("klinik kesihatan jinjang", stops)
        assertNotNull(clinicMatch)
        assertEquals("Klinik Kesihatan Jinjang", clinicMatch!!.name)

        // Multi-token match for school
        val schoolMatch = DestinationSearchEngine.resolveFromStops("smk damansara utama", stops)
        assertNotNull(schoolMatch)
        assertEquals("SMK Damansara Utama", schoolMatch!!.name)

        // Multi-token match for taman
        val tamanMatch = DestinationSearchEngine.resolveFromStops("taman melawati", stops)
        assertNotNull(tamanMatch)
        assertEquals("SK Taman Melawati", tamanMatch!!.name)
    }

    @Test
    fun `searchDestination expands search radius to 1500m when no stops within 800m`() = runBlocking {
        // Destination is Mid Valley Megamall (lat 3.1177, lon 101.6775).
        // Place a mock stop ~1100m away (lat 3.1276, lon 101.6775).
        val farStop = StopCandidate(
            name = "Stesen Bas Jauh 1100m",
            lat = 3.1276,
            lon = 101.6775,
            routeCode = "T800",
            agency = "RapidKL"
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "mid valley megamall",
            routes = emptyList(),
            nationwideStops = listOf(farStop),
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Mid Valley Megamall", result!!.name)
        // Should find the stop via radius expansion fallback (since none within 800m)
        assertTrue("Expected stops via 1500m expansion", result.nearestStops.isNotEmpty())
        assertEquals("Stesen Bas Jauh 1100m", result.nearestStops[0].stopName)
        assertTrue(
            "Distance ${result.nearestStops[0].distanceMeters} should be between 800 and 1500m",
            result.nearestStops[0].distanceMeters in 800..1500
        )
    }

    @Test
    fun `searchDestination resolves Todak Academy Cyberjaya and finds nearby Century Square stop`() = runBlocking {
        val stops = listOf(
            StopCandidate(name = "CENTURY SQUARE", lat = 2.9248, lon = 101.6612, routeCode = "T507", agency = "RapidKL"),
            StopCandidate(name = "SHAFTSBURY SQUARE", lat = 2.9238, lon = 101.6601, routeCode = "T507", agency = "RapidKL")
        )

        val result = DestinationSearchEngine.searchDestination(
            query = "todak academy",
            routes = emptyList(),
            nationwideStops = stops,
            maxRadiusMeters = 800
        )

        assertNotNull(result)
        assertEquals("Todak Academy Cyberjaya", result!!.name)
        assertTrue(result.nearestStops.isNotEmpty())
        assertEquals("CENTURY SQUARE", result.nearestStops[0].stopName)
        assertTrue(result.nearestStops[0].routeCodes.contains("T507"))
        assertTrue("Distance should be under 150m", result.nearestStops[0].distanceMeters < 150)
    }
}
