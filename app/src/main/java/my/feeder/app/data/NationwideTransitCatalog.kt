package my.feeder.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Metadata for a transit operator / agency network in Malaysia.
 */
data class NationwideAgency(
    val tag: String,
    val region: String,
    val routeCount: Int,
    val stopCount: Int
)

/**
 * Route summary across any nationwide public bus operator.
 */
data class NationwideRoute(
    val code: String,
    val name: String,
    val agency: String,
    val id: String = code,
    val stopsCount: Int = 0
)

/**
 * Bus stop entity across Malaysia with agency attribution and serving routes.
 */
data class NationwideStop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val agency: String,
    val routes: List<String>,
    val code: String = ""
)

/**
 * Unified nationwide transit catalog for all Malaysian public bus systems.
 * Loaded from assets/nationwide_transit_catalog.json compiled from data.gov.my.
 */
data class NationwideTransitCatalog(
    val version: Int,
    val totalRoutes: Int,
    val totalStops: Int,
    val agencies: List<NationwideAgency>,
    val routes: List<NationwideRoute>,
    val stops: List<NationwideStop>
) {
    companion object {
        suspend fun load(
            context: Context,
            filename: String = "nationwide_transit_catalog.json"
        ): NationwideTransitCatalog = withContext(Dispatchers.IO) {
            val jsonStr = context.assets.open(filename).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)

            val version = root.optInt("version", 1)
            val totalRoutes = root.optInt("total_routes", 0)
            val totalStops = root.optInt("total_stops", 0)

            val agenciesArr = root.optJSONArray("agencies")
            val agencies = ArrayList<NationwideAgency>(agenciesArr?.length() ?: 0)
            if (agenciesArr != null) {
                for (i in 0 until agenciesArr.length()) {
                    val obj = agenciesArr.getJSONObject(i)
                    agencies.add(
                        NationwideAgency(
                            tag = obj.getString("tag"),
                            region = obj.getString("region"),
                            routeCount = obj.optInt("route_count", 0),
                            stopCount = obj.optInt("stop_count", 0)
                        )
                    )
                }
            }

            val routesArr = root.optJSONArray("routes")
            val routes = ArrayList<NationwideRoute>(routesArr?.length() ?: 0)
            if (routesArr != null) {
                for (i in 0 until routesArr.length()) {
                    val obj = routesArr.getJSONObject(i)
                    routes.add(
                        NationwideRoute(
                            id = obj.optString("id", obj.getString("code")),
                            code = obj.getString("code"),
                            name = obj.optString("name", obj.getString("code")),
                            agency = obj.getString("agency"),
                            stopsCount = obj.optInt("stopsCount", 0)
                        )
                    )
                }
            }

            val stopsArr = root.optJSONArray("stops")
            val stops = ArrayList<NationwideStop>(stopsArr?.length() ?: 0)
            if (stopsArr != null) {
                for (i in 0 until stopsArr.length()) {
                    val obj = stopsArr.getJSONObject(i)
                    val rArr = obj.optJSONArray("routes")
                    val routeList = ArrayList<String>(rArr?.length() ?: 0)
                    if (rArr != null) {
                        for (j in 0 until rArr.length()) {
                            routeList.add(rArr.getString(j))
                        }
                    }
                    stops.add(
                        NationwideStop(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            lat = obj.optDouble("lat", 0.0),
                            lon = obj.optDouble("lon", 0.0),
                            agency = obj.getString("agency"),
                            routes = routeList,
                            code = obj.optString("code", "")
                        )
                    )
                }
            }

            NationwideTransitCatalog(
                version = version,
                totalRoutes = totalRoutes,
                totalStops = totalStops,
                agencies = agencies,
                routes = routes,
                stops = stops
            )
        }
    }
}
