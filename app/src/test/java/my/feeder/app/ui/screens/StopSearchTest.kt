package my.feeder.app.ui.screens

import my.feeder.app.data.GtfsRoute
import my.feeder.app.data.GtfsStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The destination search that lets someone who does not know bus routes still find
 * the bus: type where you want to go, get the route numbers and the direction.
 */
class StopSearchTest {

    private fun stop(id: String, code: String, name: String) =
        GtfsStop(id = id, code = code, name = name, lat = 3.0, lon = 101.6, seq = 1)

    private fun route(code: String, headsign: String, stops: List<GtfsStop>) =
        GtfsRoute(
            id = code,
            code = code,
            headsign = headsign,
            stopsCount = stops.size,
            stops = stops,
            shape = emptyList()
        )

    private val routes = listOf(
        route(
            "T504", "MRT CYBERJAYA UTARA - CYBER 11",
            listOf(
                stop("s1", "SP405", "SKYPARK CYBERJAYA"),
                stop("s2", "SP57", "BOMBA CYBERJAYA")
            )
        ),
        route(
            "T507", "MRT CYBERJAYA CITY CENTRE - TRANSPORT TERMINAL",
            listOf(
                stop("s3", "SP57", "BOMBA CYBERJAYA"),
                stop("s4", "SP9", "TAMAN LEP 7")
            )
        )
    )

    @Test
    fun `a stop name matches even when no route headsign contains it`() {
        val hits = matchStops(routes, "SKYPARK")
        assertEquals(1, hits.size)
        assertEquals("SKYPARK CYBERJAYA", hits[0].name)
        assertEquals(listOf("T504"), hits[0].routeCodes)
    }

    @Test
    fun `one physical stop served by two routes is a single row listing both`() {
        val hits = matchStops(routes, "BOMBA")
        assertEquals(1, hits.size)
        assertEquals("BOMBA CYBERJAYA", hits[0].name)
        assertEquals(listOf("T504", "T507"), hits[0].routeCodes)
    }

    @Test
    fun `the direction is carried with the match so the rider knows which way to board`() {
        val hits = matchStops(routes, "TAMAN LEP")
        assertEquals(1, hits.size)
        assertEquals("MRT CYBERJAYA CITY CENTRE - TRANSPORT TERMINAL", hits[0].headsigns.first())
    }

    @Test
    fun `the search is case insensitive`() {
        assertEquals(1, matchStops(routes, "skypark").size)
        assertEquals(1, matchStops(routes, "BoMbA").size)
    }

    @Test
    fun `a blank or unmatched query returns nothing`() {
        assertTrue(matchStops(routes, "").isEmpty())
        assertTrue(matchStops(routes, "   ").isEmpty())
        assertTrue(matchStops(routes, "KL Sentral").isEmpty())
    }

    @Test
    fun `every stop carries a code so the row has a stable test id`() {
        matchStops(routes, "BOMBA").forEach { assertTrue(it.code.isNotBlank()) }
    }
}
