package my.feeder.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NationwideTransitCatalogTest {

    @Test
    fun `nationwide transit catalog asset exists and has valid structure`() {
        val assetFile = File("src/main/assets/nationwide_transit_catalog.json")
        assertTrue("Asset file must exist", assetFile.exists())
        assertTrue("Asset file must not be empty", assetFile.length() > 500_000)

        val jsonStr = assetFile.readText(Charsets.UTF_8)
        val root = JSONObject(jsonStr)

        val totalRoutes = root.getInt("total_routes")
        val totalStops = root.getInt("total_stops")
        val agencies = root.getJSONArray("agencies")
        val routes = root.getJSONArray("routes")
        val stops = root.getJSONArray("stops")

        assertTrue("Should have over 400 routes nationwide", totalRoutes >= 400)
        assertTrue("Should have over 15000 stops nationwide", totalStops >= 15000)
        assertEquals(totalRoutes, routes.length())
        assertEquals(totalStops, stops.length())

        val agencyTags = (0 until agencies.length()).map { agencies.getJSONObject(it).getString("tag") }.toSet()
        assertTrue("Must include RapidKL", agencyTags.contains("RapidKL"))
        assertTrue("Must include Rapid Penang", agencyTags.contains("Rapid Penang"))
        assertTrue("Must include Rapid Kuantan", agencyTags.contains("Rapid Kuantan"))
        assertTrue("Must include myBAS JB", agencyTags.contains("myBAS JB"))
        assertTrue("Must include myBAS Ipoh", agencyTags.contains("myBAS Ipoh"))
        assertTrue("Must include myBAS Melaka", agencyTags.contains("myBAS Melaka"))
        assertTrue("Must include myBAS Kuching", agencyTags.contains("myBAS Kuching"))
    }
}
