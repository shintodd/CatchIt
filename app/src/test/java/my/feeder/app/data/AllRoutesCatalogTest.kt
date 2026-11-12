package my.feeder.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllRoutesCatalogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAll435NationwideRoutesHaveStopsAndPolylines() = runBlocking {
        GtfsRepository.loadCatalog(context)
        val routes = GtfsRepository.routes.value

        assertEquals("All 435 routes must be loaded into GtfsRepository.routes", 435, routes.size)

        // Verify specific reported failure routes
        val aj03 = routes.find { it.code.equals("AJ03", ignoreCase = true) }
        assertNotNull("AJ03 must exist in catalog", aj03)
        assertEquals(21, aj03!!.stops.size)
        assertTrue("AJ03 must have a polyline bus line", aj03.shape.size >= 2)
        assertEquals("AJ219 LRT CEMPAKA", aj03.stops.first().name)

        val t351 = routes.find { it.code.equals("T351", ignoreCase = true) }
        assertNotNull("T351 must exist in catalog", t351)
        assertEquals(47, t351!!.stops.size)
        assertTrue("T351 must have a polyline bus line", t351.shape.size >= 2)
        assertEquals("AJ219 LRT CEMPAKA", t351.stops.first().name)

        val t507 = routes.find { it.code.equals("T507", ignoreCase = true) }
        assertNotNull("T507 must exist in catalog", t507)
        assertTrue("T507 must have stops", t507!!.stops.isNotEmpty())
        assertTrue("T507 must have shape", t507.shape.size >= 2)

        // Assert 100% of routes across all 12 agencies have valid stops and shapes
        for (route in routes) {
            assertTrue("Route ${route.code} (${route.agency}) must have stops", route.stops.isNotEmpty())
            assertTrue("Route ${route.code} (${route.agency}) must have polyline shape >= 2 points", route.shape.size >= 2)
            assertEquals("Route ${route.code} stopsCount must match stops.size", route.stops.size, route.stopsCount)

            for (stop in route.stops) {
                assertTrue("Stop ${stop.id} on ${route.code} must have valid lat", stop.lat > 0.0)
                assertTrue("Stop ${stop.id} on ${route.code} must have valid lon", stop.lon > 0.0)
                assertTrue("Stop ${stop.id} on ${route.code} must have non-empty name", stop.name.isNotBlank())
            }
        }
    }
}
