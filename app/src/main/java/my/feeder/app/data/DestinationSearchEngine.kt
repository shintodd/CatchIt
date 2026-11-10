package my.feeder.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Resolved destination search match containing its coordinate, canonical address,
 * and nearest feeder bus stops and routes.
 */
data class DestinationResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val nearestStops: List<NearbyStopRouteMatch>
)

/**
 * A nearby feeder stop serving the resolved destination, ranked by walking distance.
 */
data class NearbyStopRouteMatch(
    val stopName: String,
    val distanceMeters: Int,
    val routeCodes: List<String>,
    val agency: String = "RapidKL"
)

/**
 * Intermediate geocoded geographic point.
 */
data class GeocodedPlace(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double
)

/**
 * Flexible stop candidate pairing a physical stop with its serving feeder route(s).
 */
data class StopCandidate(
    val name: String,
    val lat: Double,
    val lon: Double,
    val routeCode: String = "",
    val routeCodes: List<String> = if (routeCode.isNotBlank()) listOf(routeCode) else emptyList(),
    val agency: String = "RapidKL"
) {
    constructor(stop: GtfsStop, routeCode: String, agency: String = "RapidKL") :
        this(
            name = stop.name,
            lat = stop.lat,
            lon = stop.lon,
            routeCode = routeCode,
            routeCodes = if (routeCode.isNotBlank()) listOf(routeCode) else emptyList(),
            agency = agency
        )
}

/**
 * Google Maps-style Destination Search Engine for CatchIt.
 *
 * Resolves destination queries (e.g. "Hospital Shah Alam", "Mid Valley", "Gurney Plaza",
 * "JB Sentral") into GPS coordinates and finds nearest feeder bus stops and routes within
 * walking radius.
 *
 * Architecture:
 * 1. Fast Spatial Haversine Engine (exact meter-level distance).
 * 2. Multi-tier Geocoding Service:
 *    - Tier 1: Instant zero-latency offline Malaysian landmark alias table (<1ms response).
 *    - Tier 2: `android.location.Geocoder` with Malaysia country code / locale bias.
 *    - Tier 3: Lightweight HTTP fallback (Photon / OSM Nominatim).
 * 3. Spatial Route Aggregator: Groups stops by physical name, deduplicates and gathers route
 *    codes, and ranks by ascending walking distance.
 */
object DestinationSearchEngine {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Efficient Haversine great-circle distance calculation in meters.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val a = (sinDLat * sinDLat) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * (sinDLon * sinDLon)
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return (EARTH_RADIUS_METERS * c).roundToInt()
    }

    /**
     * Finds and ranks nearby bus routes and stops within [maxRadiusMeters] of a destination coordinate.
     * Stops sharing the same physical name are merged, gathering their serving route codes.
     */
    fun findNearbyRoutes(
        destLat: Double,
        destLon: Double,
        allStops: List<StopCandidate>,
        maxRadiusMeters: Int = 800
    ): List<NearbyStopRouteMatch> {
        if (allStops.isEmpty() || maxRadiusMeters <= 0) return emptyList()

        val grouped = LinkedHashMap<String, MutableNearbyStop>()

        for (candidate in allStops) {
            val dist = distanceMeters(destLat, destLon, candidate.lat, candidate.lon)
            if (dist > maxRadiusMeters) continue

            val key = candidate.name.trim().uppercase()
            val existing = grouped[key]

            if (existing == null) {
                val routes = LinkedHashSet<String>()
                if (candidate.routeCode.isNotBlank()) routes.add(candidate.routeCode.trim().uppercase())
                candidate.routeCodes.forEach { code ->
                    if (code.isNotBlank()) routes.add(code.trim().uppercase())
                }
                grouped[key] = MutableNearbyStop(
                    displayName = candidate.name.trim(),
                    minDistanceMeters = dist,
                    routeCodes = routes,
                    agency = candidate.agency
                )
            } else {
                if (dist < existing.minDistanceMeters) {
                    existing.minDistanceMeters = dist
                }
                if (candidate.routeCode.isNotBlank()) {
                    existing.routeCodes.add(candidate.routeCode.trim().uppercase())
                }
                candidate.routeCodes.forEach { code ->
                    if (code.isNotBlank()) existing.routeCodes.add(code.trim().uppercase())
                }
            }
        }

        return grouped.values
            .map { match ->
                NearbyStopRouteMatch(
                    stopName = match.displayName,
                    distanceMeters = match.minDistanceMeters,
                    routeCodes = match.routeCodes.toList().sorted(),
                    agency = match.agency
                )
            }
            .sortedBy { it.distanceMeters }
    }

    /**
     * Overload accepting [GtfsRoute] collections (e.g. `GtfsRepository.routes.value`).
     */
    @JvmName("findNearbyRoutesFromGtfsRoutes")
    fun findNearbyRoutes(
        destLat: Double,
        destLon: Double,
        routes: Collection<GtfsRoute>,
        maxRadiusMeters: Int = 800,
        agency: String = "RapidKL"
    ): List<NearbyStopRouteMatch> {
        val candidates = routes.flatMap { route ->
            route.stops.map { stop ->
                StopCandidate(
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon,
                    routeCode = route.code,
                    agency = agency
                )
            }
        }
        return findNearbyRoutes(destLat, destLon, candidates, maxRadiusMeters)
    }

    /**
     * Overload accepting bare [GtfsStop] collections.
     */
    @JvmName("findNearbyRoutesFromGtfsStops")
    fun findNearbyRoutes(
        destLat: Double,
        destLon: Double,
        allStops: Collection<GtfsStop>,
        maxRadiusMeters: Int = 800,
        agency: String = "RapidKL"
    ): List<NearbyStopRouteMatch> {
        val candidates = allStops.map { stop ->
            StopCandidate(
                name = stop.name,
                lat = stop.lat,
                lon = stop.lon,
                routeCode = "",
                agency = agency
            )
        }
        return findNearbyRoutes(destLat, destLon, candidates, maxRadiusMeters)
    }

    private fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Resolves a destination query directly against physical stop candidates across
     * Malaysia (e.g. schools, clinics, hospitals, taman, condos, stations, malls).
     * Zero network latency (<1ms) and 100% offline.
     */
    fun resolveFromStops(
        query: String,
        stops: List<StopCandidate>
    ): GeocodedPlace? {
        val qClean = query.trim()
        val qSlug = slug(qClean)
        if (qSlug.length < 3 || stops.isEmpty()) return null

        // 1. Exact slug match (e.g. "dpulze" == "D'PULZE", "klcc" == "KLCC")
        val exact = stops.firstOrNull { slug(it.name) == qSlug }
        if (exact != null) {
            return GeocodedPlace(
                name = exact.name,
                address = "${exact.name}, ${exact.agency}",
                lat = exact.lat,
                lon = exact.lon
            )
        }

        // 2. All tokens match (each token >= 3 chars, e.g. "taman melawati" -> "SK TAMAN MELAWATI")
        val tokens = qClean.split(Regex("\\s+"))
            .map { slug(it) }
            .filter { it.length >= 3 }
        if (tokens.isNotEmpty()) {
            val tokenMatch = stops.firstOrNull { stop ->
                val sSlug = slug(stop.name)
                tokens.all { t -> sSlug.contains(t) }
            }
            if (tokenMatch != null) {
                return GeocodedPlace(
                    name = tokenMatch.name,
                    address = "${tokenMatch.name}, ${tokenMatch.agency}",
                    lat = tokenMatch.lat,
                    lon = tokenMatch.lon
                )
            }
        }

        return null
    }

    /**
     * Resolves a query to a geographic place using offline landmarks, local stops catalog,
     * Android Geocoder, or OpenStreetMap HTTP fallback.
     */
    suspend fun resolveDestination(
        query: String,
        candidateStops: List<StopCandidate> = emptyList(),
        context: Context? = null
    ): GeocodedPlace? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        // Tier 1: Offline Landmark Alias Table (instant, reliable, zero network latency)
        LandmarkAliasTable.lookup(trimmed)?.let { return it }

        // Tier 2: Instant Stop Catalog match across nationwide stops (zero latency, 100% offline)
        val stopsToSearch = if (candidateStops.isNotEmpty()) candidateStops else GtfsRepository.nationwideStops.value
        if (stopsToSearch.isNotEmpty()) {
            resolveFromStops(trimmed, stopsToSearch)?.let { return it }
        }

        // Tier 3: Online Real Places Search Engine (DuckDuckGo Places / Apple Maps POI Engine)
        val onlinePlace = withTimeoutOrNull(2800L) {
            withContext(Dispatchers.IO) {
                // Step A: Real places search engine (finds academies, shops, restaurants, night markets, schools)
                try {
                    val ddgResults = HttpDdgPlacesGeocoder.geocode(trimmed)
                    if (ddgResults.isNotEmpty()) return@withContext ddgResults.first()
                } catch (_: Throwable) {}

                // Step B: Photon OSM fallback
                try {
                    val photonResults = HttpOsmPhotonGeocoder.geocode(trimmed)
                    if (photonResults.isNotEmpty()) return@withContext photonResults.first()
                } catch (_: Throwable) {}

                // Step C: Android system Geocoder
                if (context != null && runCatching { Geocoder.isPresent() }.getOrDefault(false)) {
                    try {
                        val androidResults = geocodeWithAndroid(context, trimmed)
                        if (androidResults.isNotEmpty()) return@withContext androidResults.first()
                    } catch (_: Throwable) {}
                }
                null
            }
        }
        if (onlinePlace != null) return onlinePlace

        // Tier 4: Relaxed partial token match against Landmark table
        return LandmarkAliasTable.lookupPartial(trimmed)
    }

    /**
     * Backward-compatible overload without explicit candidate stops.
     */
    suspend fun resolveDestination(
        query: String,
        context: Context? = null
    ): GeocodedPlace? = resolveDestination(query, GtfsRepository.nationwideStops.value, context)

    /**
     * Resolves query to a list of candidate places.
     */
    suspend fun resolveDestinations(
        query: String,
        candidateStops: List<StopCandidate> = emptyList(),
        context: Context? = null,
        limit: Int = 5
    ): List<GeocodedPlace> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val results = mutableListOf<GeocodedPlace>()

        // Check stops first if strong match
        if (candidateStops.isNotEmpty()) {
            resolveFromStops(trimmed, candidateStops)?.let { results.add(it) }
        }

        // Check landmark table
        LandmarkAliasTable.lookup(trimmed)?.let { p ->
            if (results.none { distanceMeters(it.lat, it.lon, p.lat, p.lon) < 50 }) results.add(p)
        }

        // Real Places Search Engine (DuckDuckGo Places / Apple Maps)
        try {
            val ddgPlaces = HttpDdgPlacesGeocoder.geocode(trimmed, limit)
            for (p in ddgPlaces) {
                if (results.none { distanceMeters(it.lat, it.lon, p.lat, p.lon) < 50 }) {
                    results.add(p)
                }
            }
        } catch (_: Throwable) {}

        // System Geocoder
        if (context != null && runCatching { Geocoder.isPresent() }.getOrDefault(false)) {
            try {
                val androidPlaces = geocodeWithAndroid(context, trimmed, limit)
                for (p in androidPlaces) {
                    if (results.none { distanceMeters(it.lat, it.lon, p.lat, p.lon) < 50 }) {
                        results.add(p)
                    }
                }
            } catch (_: Throwable) {}
        }

        // HTTP fallback if still insufficient
        if (results.size < limit) {
            try {
                val httpPlaces = HttpOsmPhotonGeocoder.geocode(trimmed, limit)
                for (p in httpPlaces) {
                    if (results.none { distanceMeters(it.lat, it.lon, p.lat, p.lon) < 50 }) {
                        results.add(p)
                    }
                }
            } catch (_: Throwable) {}
        }

        if (results.isEmpty()) {
            LandmarkAliasTable.lookupPartial(trimmed)?.let { results.add(it) }
        }

        return results.take(limit)
    }

    /**
     * Full end-to-end destination search taking a query, GTFS routes, and optional nationwide
     * candidate stops, resolving coordinates and matching nearest bus routes across Malaysia.
     */
    suspend fun searchDestination(
        query: String,
        routes: Collection<GtfsRoute>,
        nationwideStops: List<StopCandidate> = emptyList(),
        context: Context? = null,
        maxRadiusMeters: Int = 800
    ): DestinationResult? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        val candidateStops = ArrayList<StopCandidate>(routes.size * 15 + nationwideStops.size)
        for (r in routes) {
            for (s in r.stops) {
                candidateStops.add(StopCandidate(name = s.name, lat = s.lat, lon = s.lon, routeCode = r.code, agency = "RapidKL"))
            }
        }
        candidateStops.addAll(nationwideStops)

        val place = resolveDestination(trimmed, candidateStops, context) ?: return null
        val nearbyFeeder = findNearbyRoutes(place.lat, place.lon, routes, maxRadiusMeters)
        val nearbyNationwide = if (nationwideStops.isNotEmpty()) {
            findNearbyRoutes(place.lat, place.lon, nationwideStops, maxRadiusMeters)
        } else {
            emptyList()
        }

        val combinedMap = LinkedHashMap<String, NearbyStopRouteMatch>()
        for (m in nearbyFeeder) {
            combinedMap[m.stopName.uppercase()] = m
        }
        for (m in nearbyNationwide) {
            val key = m.stopName.uppercase()
            val existing = combinedMap[key]
            if (existing == null) {
                combinedMap[key] = m
            } else {
                val mergedRoutes = (existing.routeCodes + m.routeCodes).distinct().sorted()
                combinedMap[key] = existing.copy(
                    distanceMeters = minOf(existing.distanceMeters, m.distanceMeters),
                    routeCodes = mergedRoutes,
                    agency = if (existing.agency != "RapidKL") existing.agency else m.agency
                )
            }
        }

        var sortedNearby = combinedMap.values.sortedBy { it.distanceMeters }
        if (sortedNearby.isEmpty() && maxRadiusMeters < 1500) {
            val expNationwide = if (nationwideStops.isNotEmpty()) {
                findNearbyRoutes(place.lat, place.lon, nationwideStops, 1500)
            } else {
                findNearbyRoutes(place.lat, place.lon, routes, 1500)
            }
            sortedNearby = expNationwide.take(3)
        }

        return DestinationResult(
            name = place.name,
            address = place.address,
            lat = place.lat,
            lon = place.lon,
            nearestStops = sortedNearby
        )
    }

    /**
     * Backward-compatible overload without explicit nationwide stops collection.
     */
    suspend fun searchDestination(
        query: String,
        routes: Collection<GtfsRoute>,
        context: Context? = null,
        maxRadiusMeters: Int = 800
    ): DestinationResult? = searchDestination(query, routes, GtfsRepository.nationwideStops.value, context, maxRadiusMeters)

    /**
     * Resolves query to multiple destination candidates (e.g. branches, malls, universities)
     * and finds the nearest bus stops and routes for each candidate.
     */
    suspend fun searchDestinations(
        query: String,
        routes: Collection<GtfsRoute>,
        nationwideStops: List<StopCandidate> = emptyList(),
        context: Context? = null,
        limit: Int = 3,
        maxRadiusMeters: Int = 800
    ): List<DestinationResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val candidateStops = ArrayList<StopCandidate>(routes.size * 15 + nationwideStops.size)
        for (r in routes) {
            for (s in r.stops) {
                candidateStops.add(StopCandidate(name = s.name, lat = s.lat, lon = s.lon, routeCode = r.code, agency = "RapidKL"))
            }
        }
        candidateStops.addAll(nationwideStops)

        val places = resolveDestinations(trimmed, candidateStops, context, limit)
        if (places.isEmpty()) {
            val single = searchDestination(trimmed, routes, nationwideStops, context, maxRadiusMeters)
            return if (single != null) listOf(single) else emptyList()
        }

        val mapped = places.mapNotNull { place ->
            val nearbyFeeder = findNearbyRoutes(place.lat, place.lon, routes, maxRadiusMeters)
            val nearbyNationwide = if (nationwideStops.isNotEmpty()) {
                findNearbyRoutes(place.lat, place.lon, nationwideStops, maxRadiusMeters)
            } else emptyList()

            val combinedMap = LinkedHashMap<String, NearbyStopRouteMatch>()
            for (m in nearbyFeeder) combinedMap[m.stopName.uppercase()] = m
            for (m in nearbyNationwide) {
                val key = m.stopName.uppercase()
                val existing = combinedMap[key]
                if (existing == null) {
                    combinedMap[key] = m
                } else {
                    val mergedRoutes = (existing.routeCodes + m.routeCodes).distinct().sorted()
                    combinedMap[key] = existing.copy(
                        distanceMeters = minOf(existing.distanceMeters, m.distanceMeters),
                        routeCodes = mergedRoutes,
                        agency = if (existing.agency != "RapidKL") existing.agency else m.agency
                    )
                }
            }

            var sortedNearby = combinedMap.values.sortedBy { it.distanceMeters }
            if (sortedNearby.isEmpty() && maxRadiusMeters < 1500) {
                val expFeeder = findNearbyRoutes(place.lat, place.lon, routes, 1500)
                val expNationwide = if (nationwideStops.isNotEmpty()) {
                    findNearbyRoutes(place.lat, place.lon, nationwideStops, 1500)
                } else emptyList()
                val expCombined = LinkedHashMap<String, NearbyStopRouteMatch>()
                for (m in expFeeder) expCombined[m.stopName.uppercase()] = m
                for (m in expNationwide) {
                    val key = m.stopName.uppercase()
                    if (!expCombined.containsKey(key)) expCombined[key] = m
                }
                sortedNearby = expCombined.values.sortedBy { it.distanceMeters }.take(3)
            }

            DestinationResult(
                name = place.name,
                address = place.address,
                lat = place.lat,
                lon = place.lon,
                nearestStops = sortedNearby
            )
        }

        val withStops = mapped.filter { it.nearestStops.isNotEmpty() }
        return if (withStops.isNotEmpty()) withStops else mapped.take(1)
    }

    /**
     * Full end-to-end destination search with candidate stops.
     */
    suspend fun searchDestinationWithStops(
        query: String,
        allStops: List<StopCandidate>,
        context: Context? = null,
        maxRadiusMeters: Int = 800
    ): DestinationResult? {
        val place = resolveDestination(query, context) ?: return null
        val nearby = findNearbyRoutes(place.lat, place.lon, allStops, maxRadiusMeters)
        return DestinationResult(
            name = place.name,
            address = place.address,
            lat = place.lat,
            lon = place.lon,
            nearestStops = nearby
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeWithAndroid(
        context: Context,
        query: String,
        maxResults: Int = 5
    ): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale("ms", "MY"))
        val searchQuery = if (query.contains("Malaysia", ignoreCase = true)) query else "$query, Malaysia"
        val addresses = geocoder.getFromLocationName(searchQuery, maxResults) ?: emptyList()
        addresses.map { addr ->
            val rawFeature = addr.featureName?.trim()?.takeIf { it.isNotBlank() }
            val isJustNumber = rawFeature != null && (
                rawFeature.matches(Regex("^(?i)(no\\.?)?\\s*[0-9A-Za-z\\-/#]+$")) ||
                rawFeature.all { it.isDigit() || it == '-' || it == '/' || it == '.' }
            )
            val name = if (rawFeature != null && !isJustNumber) {
                rawFeature
            } else {
                addr.premises?.takeIf { it.isNotBlank() }
                    ?: addr.subLocality?.takeIf { it.isNotBlank() }
                    ?: query
            }
            val addressLines = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }
            val fullAddress = if (addressLines.isNotEmpty()) addressLines.joinToString(", ") else name
            GeocodedPlace(
                name = name,
                address = fullAddress,
                lat = addr.latitude,
                lon = addr.longitude
            )
        }
    }

    private data class MutableNearbyStop(
        val displayName: String,
        var minDistanceMeters: Int,
        val routeCodes: MutableSet<String>,
        val agency: String
    )
}

/**
 * Offline landmark alias directory for high-priority Malaysian transit destinations,
 * malls, hospitals, and hubs.
 */
internal object LandmarkAliasTable {

    data class LandmarkEntry(
        val name: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val aliases: List<String>
    )

    private val landmarks = listOf(
        // Hospitals
        LandmarkEntry(
            name = "Hospital Shah Alam",
            address = "Seksyen 7, 40000 Shah Alam, Selangor",
            lat = 3.0716,
            lon = 101.4903,
            aliases = listOf("hospital shah alam", "shah alam hospital", "hsa", "hosp shah alam", "hospital seksyen 7")
        ),
        LandmarkEntry(
            name = "Hospital Kuala Lumpur",
            address = "Jalan Pahang, 50586 Kuala Lumpur",
            lat = 3.1718,
            lon = 101.7025,
            aliases = listOf("hospital kuala lumpur", "hkl", "general hospital kl")
        ),
        LandmarkEntry(
            name = "Hospital Sungai Buloh",
            address = "Jalan Hospital, 47000 Sungai Buloh, Selangor",
            lat = 3.2201,
            lon = 101.5831,
            aliases = listOf("hospital sungai buloh", "sungai buloh hospital", "hsb")
        ),
        LandmarkEntry(
            name = "Hospital Selayang",
            address = "Lebuhraya Selayang-Kepong, 68100 Batu Caves, Selangor",
            lat = 3.2425,
            lon = 101.6467,
            aliases = listOf("hospital selayang", "selayang hospital")
        ),
        LandmarkEntry(
            name = "Hospital Serdang",
            address = "Jalan Puchong, 43000 Kajang, Selangor",
            lat = 2.9765,
            lon = 101.7208,
            aliases = listOf("hospital serdang", "hospital sultan idris shah", "serdang hospital")
        ),
        LandmarkEntry(
            name = "Hospital Putrajaya",
            address = "Pusat Pentadbiran Kerajaan Persekutuan, Presint 7, 62250 Putrajaya",
            lat = 2.9295,
            lon = 101.6742,
            aliases = listOf("hospital putrajaya", "putrajaya hospital")
        ),
        LandmarkEntry(
            name = "Pusat Perubatan Universiti Malaya (PPUM)",
            address = "Lembah Pantai, 59100 Kuala Lumpur",
            lat = 3.1130,
            lon = 101.6538,
            aliases = listOf("ppum", "ummc", "university malaya medical centre", "hospital universiti", "hospital um")
        ),
        LandmarkEntry(
            name = "Sunway Medical Centre",
            address = "5, Jalan Lagoon Selatan, Bandar Sunway, 47500 Subang Jaya, Selangor",
            lat = 3.0673,
            lon = 101.6101,
            aliases = listOf("sunway medical centre", "sunway medical", "sunway hospital")
        ),

        // Malls & Retail Hubs
        LandmarkEntry(
            name = "Mid Valley Megamall",
            address = "Lingkaran Syed Putra, Mid Valley City, 59200 Kuala Lumpur",
            lat = 3.1177,
            lon = 101.6775,
            aliases = listOf("mid valley", "mid valley megamall", "midvalley", "mid valley city")
        ),
        LandmarkEntry(
            name = "The Gardens Mall",
            address = "Mid Valley City, 59200 Kuala Lumpur",
            lat = 3.1186,
            lon = 101.6757,
            aliases = listOf("the gardens", "the gardens mall", "gardens mid valley")
        ),
        LandmarkEntry(
            name = "Gurney Plaza",
            address = "170, Gurney Dr, Pulau Tikus, 10250 George Town, Penang",
            lat = 5.4377,
            lon = 100.3098,
            aliases = listOf("gurney plaza", "gurney", "plaza gurney", "gurney drive")
        ),
        LandmarkEntry(
            name = "Suria KLCC",
            address = "241, Petronas Twin Tower, Kuala Lumpur City Centre, 50088 Kuala Lumpur",
            lat = 3.1578,
            lon = 101.7119,
            aliases = listOf("klcc", "suria klcc", "petronas twin towers", "klcc mall")
        ),
        LandmarkEntry(
            name = "Pavilion Kuala Lumpur",
            address = "168, Jalan Bukit Bintang, 55100 Kuala Lumpur",
            lat = 3.1488,
            lon = 101.7133,
            aliases = listOf("pavilion kl", "pavilion kuala lumpur", "pavilion bukit bintang")
        ),
        LandmarkEntry(
            name = "Pavilion Bukit Jalil",
            address = "2, Persiaran Jalil 8, Bukit Jalil, 57000 Kuala Lumpur",
            lat = 3.0519,
            lon = 101.6708,
            aliases = listOf("pavilion bukit jalil", "pavilion bj")
        ),
        LandmarkEntry(
            name = "1 Utama Shopping Centre",
            address = "1, Lebuh Bandar Utama, Bandar Utama, 47800 Petaling Jaya, Selangor",
            lat = 3.1502,
            lon = 101.6152,
            aliases = listOf("1 utama", "one utama", "1utama", "one utama shopping centre", "1u", "bandar utama 1u")
        ),
        LandmarkEntry(
            name = "Sunway Pyramid",
            address = "3, Jalan PJS 11/15, Bandar Sunway, 47500 Subang Jaya, Selangor",
            lat = 3.0733,
            lon = 101.6078,
            aliases = listOf("sunway pyramid", "pyramid mall", "sunway mall")
        ),
        LandmarkEntry(
            name = "DPULZE Shopping Centre",
            address = "Persiaran Flora, Cyber 12, 63000 Cyberjaya, Selangor",
            lat = 2.9217,
            lon = 101.6514,
            aliases = listOf("dpulze", "d pulze", "d'pulze", "dpulze cyberjaya", "dpulze mall", "d'pulze shopping centre")
        ),
        LandmarkEntry(
            name = "Todak Academy Cyberjaya",
            address = "Coplace 11, Block 2340, Century Square Phase 1, Jalan Usahawan Cyber 6, 63000 Cyberjaya, Selangor",
            lat = 2.924356,
            lon = 101.661809,
            aliases = listOf("todak academy", "todak", "todak cyberjaya", "todak academy cyberjaya", "todak fusion")
        ),
        LandmarkEntry(
            name = "Century Square Cyberjaya",
            address = "Jalan Usahawan, Cyber 6, 63000 Cyberjaya, Selangor",
            lat = 2.9248,
            lon = 101.6612,
            aliases = listOf("century square", "century square cyberjaya", "coplace", "coplace 11")
        ),
        LandmarkEntry(
            name = "Tamarind Square Cyberjaya",
            address = "Persiaran Multimedia, Cyber 10, 63000 Cyberjaya, Selangor",
            lat = 2.9189,
            lon = 101.6423,
            aliases = listOf("tamarind square", "tamarind cyberjaya", "tamarind")
        ),
        LandmarkEntry(
            name = "Shaftsbury Square Cyberjaya",
            address = "Persiaran Multimedia, Cyber 6, 63000 Cyberjaya, Selangor",
            lat = 2.9238,
            lon = 101.6601,
            aliases = listOf("shaftsbury", "shaftsbury square", "shaftsbury cyberjaya")
        ),
        LandmarkEntry(
            name = "Alamanda Shopping Centre",
            address = "Jalan Alamanda, Presint 1, 62000 Putrajaya",
            lat = 2.9427,
            lon = 101.7107,
            aliases = listOf("alamanda", "alamanda putrajaya", "alamanda mall")
        ),
        LandmarkEntry(
            name = "Nu Sentral",
            address = "201, Jalan Tun Sambanthan, Brickfields, 50470 Kuala Lumpur",
            lat = 3.1333,
            lon = 101.6871,
            aliases = listOf("nu sentral", "nu sentral mall")
        ),
        LandmarkEntry(
            name = "KL Gateway Mall",
            address = "2, Jalan Kerinchi, Kampung Kerinchi, 59200 Kuala Lumpur",
            lat = 3.1147,
            lon = 101.6631,
            aliases = listOf("kl gateway", "kl gateway mall", "gateway mall")
        ),
        LandmarkEntry(
            name = "LaLaport Bukit Bintang City Centre",
            address = "2, Jalan Hang Tuah, Bukit Bintang, 55100 Kuala Lumpur",
            lat = 3.1408,
            lon = 101.7088,
            aliases = listOf("lalaport", "lalaport bbcc", "bbcc")
        ),
        LandmarkEntry(
            name = "Sunway Velocity Mall",
            address = "Lingkaran SV, Sunway Velocity, 55100 Kuala Lumpur",
            lat = 3.1278,
            lon = 101.7252,
            aliases = listOf("sunway velocity", "velocity mall", "velocity")
        ),
        LandmarkEntry(
            name = "MyTOWN Shopping Centre / IKEA Cheras",
            address = "6, Jalan Cochrane, Seksyen 90, 55100 Kuala Lumpur",
            lat = 3.1352,
            lon = 101.7247,
            aliases = listOf("mytown", "my town", "ikea cheras")
        ),
        LandmarkEntry(
            name = "Tropicana Gardens Mall",
            address = "2A, Persiaran Surian, Tropicana Indah, 47810 Petaling Jaya, Selangor",
            lat = 3.1504,
            lon = 101.5947,
            aliases = listOf("tropicana gardens", "tropicana gardens mall", "tropicana mall")
        ),
        LandmarkEntry(
            name = "IOI City Mall",
            address = "IOI Resort City, 62502 Sepang, Putrajaya, Selangor",
            lat = 2.9702,
            lon = 101.7144,
            aliases = listOf("ioi city mall", "ioi mall putrajaya", "ioi city")
        ),
        LandmarkEntry(
            name = "The Curve",
            address = "6, Jalan PJU 7/3, Mutiara Damansara, 47810 Petaling Jaya, Selangor",
            lat = 3.1582,
            lon = 101.6117,
            aliases = listOf("the curve", "curve damansara", "the curve mall")
        ),
        LandmarkEntry(
            name = "IPC Shopping Centre / IKEA Damansara",
            address = "Mutiara Damansara, 47800 Petaling Jaya, Selangor",
            lat = 3.1575,
            lon = 101.6133,
            aliases = listOf("ikea damansara", "ipc shopping centre", "ipc")
        ),
        LandmarkEntry(
            name = "Paradigm Mall PJ",
            address = "1, Jalan SS 7/26a, Kelana Jaya, 47301 Petaling Jaya, Selangor",
            lat = 3.1051,
            lon = 101.5958,
            aliases = listOf("paradigm mall", "paradigm mall pj", "paradigm kelana jaya")
        ),
        LandmarkEntry(
            name = "The Starling",
            address = "6, Jalan SS 21/37, Damansara Utama, 47400 Petaling Jaya, Selangor",
            lat = 3.1347,
            lon = 101.6231,
            aliases = listOf("starling mall", "the starling", "starling uptown")
        ),
        LandmarkEntry(
            name = "Central i-City",
            address = "i-City, Persiaran Multimedia, Seksyen 7, 40000 Shah Alam, Selangor",
            lat = 3.0645,
            lon = 101.4883,
            aliases = listOf("i-city", "central i-city", "icity", "i city shah alam")
        ),
        LandmarkEntry(
            name = "AEON Mall Shah Alam",
            address = "1, Jalan Akuatik 13/64, Seksyen 13, 40100 Shah Alam, Selangor",
            lat = 3.0792,
            lon = 101.5458,
            aliases = listOf("aeon shah alam", "aeon mall shah alam", "aeon sek 13")
        ),

        // Transit Hubs
        LandmarkEntry(
            name = "JB Sentral",
            address = "Jalan Jim Quee, Bandar Johor Bahru, 80300 Johor Bahru, Johor",
            lat = 1.4628,
            lon = 103.7644,
            aliases = listOf("jb sentral", "johor bahru sentral", "stesen jb sentral", "jb transit")
        ),
        LandmarkEntry(
            name = "KL Sentral",
            address = "Brickfields, 50470 Kuala Lumpur",
            lat = 3.1342,
            lon = 101.6861,
            aliases = listOf("kl sentral", "kuala lumpur sentral", "stesen kl sentral")
        ),
        LandmarkEntry(
            name = "Terminal Bersepadu Selatan (TBS)",
            address = "Jalan Terminal Selatan, Bandar Tasik Selatan, 57100 Kuala Lumpur",
            lat = 3.0765,
            lon = 101.7107,
            aliases = listOf("tbs", "terminal bersepadu selatan", "bandar tasik selatan")
        ),
        LandmarkEntry(
            name = "Putrajaya Sentral",
            address = "Presint 7, 62250 Putrajaya",
            lat = 2.9304,
            lon = 101.6706,
            aliases = listOf("putrajaya sentral", "stesen putrajaya sentral")
        ),
        LandmarkEntry(
            name = "KTM Komuter Shah Alam",
            address = "Seksyen 19, 40300 Shah Alam, Selangor",
            lat = 3.0569,
            lon = 101.5245,
            aliases = listOf("ktm shah alam", "stesen ktm shah alam", "komuter shah alam")
        ),

        // Education & Universities
        LandmarkEntry(
            name = "Universiti Malaya (UM)",
            address = "Jalan Universiti, 50603 Kuala Lumpur",
            lat = 3.1209,
            lon = 101.6538,
            aliases = listOf("um", "universiti malaya", "university of malaya")
        ),
        LandmarkEntry(
            name = "UiTM Shah Alam",
            address = "Seksyen 1, 40450 Shah Alam, Selangor",
            lat = 3.0689,
            lon = 101.5037,
            aliases = listOf("uitm shah alam", "uitm", "universiti teknologi mara")
        ),
        LandmarkEntry(
            name = "Multimedia University (MMU Cyberjaya)",
            address = "Persiaran Multimedia, 63100 Cyberjaya, Selangor",
            lat = 2.9279,
            lon = 101.6418,
            aliases = listOf("mmu", "mmu cyberjaya", "multimedia university")
        ),

        // Attractions & Culture
        LandmarkEntry(
            name = "Batu Caves",
            address = "Gombak, 68100 Batu Caves, Selangor",
            lat = 3.2379,
            lon = 101.6840,
            aliases = listOf("batu caves", "kuil batu caves")
        ),
        LandmarkEntry(
            name = "Masjid Negara (National Mosque)",
            address = "Jalan Perdana, Tasik Perdana, 50480 Kuala Lumpur",
            lat = 3.1419,
            lon = 101.6918,
            aliases = listOf("masjid negara", "national mosque")
        ),
        LandmarkEntry(
            name = "Masjid Sultan Salahuddin Abdul Aziz Shah",
            address = "Seksyen 14, 40000 Shah Alam, Selangor",
            lat = 3.0784,
            lon = 101.5208,
            aliases = listOf("blue mosque", "masjid negeri shah alam", "masjid biru", "masjid shah alam")
        ),

        // Nationwide Malaysia Hubs & Landmarks
        // Penang
        LandmarkEntry(
            name = "KOMTAR",
            address = "Jalan Penang, 10000 George Town, Pulau Pinang",
            lat = 5.4145,
            lon = 100.3298,
            aliases = listOf("komtar", "komtar penang", "terminal komtar", "george town komtar")
        ),
        LandmarkEntry(
            name = "Queensbay Mall",
            address = "100, Persiaran Bayan Indah, 11900 Bayan Lepas, Pulau Pinang",
            lat = 5.3327,
            lon = 100.3066,
            aliases = listOf("queensbay mall", "queensbay", "queens bay mall")
        ),
        LandmarkEntry(
            name = "Penang Sentral",
            address = "Jalan Bagan Dalam, 12000 Butterworth, Pulau Pinang",
            lat = 5.3944,
            lon = 100.3664,
            aliases = listOf("penang sentral", "butterworth sentral", "terminal penang sentral")
        ),
        LandmarkEntry(
            name = "Hospital Pulau Pinang",
            address = "Jalan Residensi, 10990 George Town, Pulau Pinang",
            lat = 5.4172,
            lon = 100.3114,
            aliases = listOf("hospital pulau pinang", "penang hospital", "hospital general penang")
        ),

        // Johor
        LandmarkEntry(
            name = "Mid Valley Southkey",
            address = "1, Persiaran Southkey 1, Kota Southkey, 80150 Johor Bahru, Johor",
            lat = 1.4988,
            lon = 103.7774,
            aliases = listOf("mid valley southkey", "southkey", "southkey jb", "mid valley jb")
        ),
        LandmarkEntry(
            name = "Paradigm Mall JB",
            address = "Jalan Skudai, 81200 Johor Bahru, Johor",
            lat = 1.5165,
            lon = 103.6853,
            aliases = listOf("paradigm mall jb", "paradigm jb", "paradigm mall johor")
        ),
        LandmarkEntry(
            name = "Hospital Sultanah Aminah",
            address = "Jalan Persiaran Abu Bakar Sultan, 80100 Johor Bahru, Johor",
            lat = 1.4589,
            lon = 103.7461,
            aliases = listOf("hospital sultanah aminah", "hsa jb", "hospital jb")
        ),
        LandmarkEntry(
            name = "Larkin Sentral",
            address = "Jalan Garuda, Larkin Jaya, 80350 Johor Bahru, Johor",
            lat = 1.4947,
            lon = 103.7423,
            aliases = listOf("larkin sentral", "terminal larkin", "larkin bus terminal")
        ),

        // Perak (Ipoh)
        LandmarkEntry(
            name = "Stesen Bas Medan Kidd",
            address = "Jalan Kidd, 30200 Ipoh, Perak",
            lat = 4.5947,
            lon = 101.0722,
            aliases = listOf("medan kidd", "stesen bas medan kidd", "terminal medan kidd", "medan kidd ipoh")
        ),
        LandmarkEntry(
            name = "Ipoh Parade",
            address = "105, Jalan Sultan Abdul Jalil, 30450 Ipoh, Perak",
            lat = 4.5969,
            lon = 101.0908,
            aliases = listOf("ipoh parade", "ipoh parade mall")
        ),
        LandmarkEntry(
            name = "Hospital Raja Permaisuri Bainun (Hospital Ipoh)",
            address = "Jalan Raja Ashman Shah, 30450 Ipoh, Perak",
            lat = 4.6042,
            lon = 101.0908,
            aliases = listOf("hospital ipoh", "hrpb", "hospital raja permaisuri bainun")
        ),

        // Melaka
        LandmarkEntry(
            name = "Melaka Sentral",
            address = "Jalan Tun Abdul Razak, Peringgit, 75400 Melaka",
            lat = 2.2198,
            lon = 102.2492,
            aliases = listOf("melaka sentral", "terminal melaka sentral", "stesen melaka sentral")
        ),
        LandmarkEntry(
            name = "Jonker Walk (Jonker Street)",
            address = "Jalan Hang Jebat, 75200 Melaka",
            lat = 2.1952,
            lon = 102.2483,
            aliases = listOf("jonker walk", "jonker street", "jonker", "jalan hang jebat")
        ),
        LandmarkEntry(
            name = "Mahkota Parade / Dataran Pahlawan",
            address = "Jalan Merdeka, Bandar Hilir, 75000 Melaka",
            lat = 2.1905,
            lon = 102.2505,
            aliases = listOf("mahkota parade", "dataran pahlawan", "pahlawan melaka", "bandar hilir")
        ),
        LandmarkEntry(
            name = "Hospital Melaka",
            address = "Jalan Mufti Haji Khalil, 75400 Melaka",
            lat = 2.2178,
            lon = 102.2619,
            aliases = listOf("hospital melaka", "melaka hospital", "general hospital melaka")
        ),

        // Negeri Sembilan
        LandmarkEntry(
            name = "Terminal One Seremban",
            address = "Jalan Lintang, 70000 Seremban, Negeri Sembilan",
            lat = 2.7235,
            lon = 101.9362,
            aliases = listOf("terminal one seremban", "terminal 1 seremban", "seremban sentral", "terminal one")
        ),
        LandmarkEntry(
            name = "Hospital Tuanku Ja'afar Seremban",
            address = "Jalan Rasah, 70300 Seremban, Negeri Sembilan",
            lat = 2.7161,
            lon = 101.9442,
            aliases = listOf("hospital seremban", "htjs", "hospital tuanku ja afar")
        ),

        // Kedah & Perlis
        LandmarkEntry(
            name = "Aman Central",
            address = "1, Darul Aman Hwy, Kampung Lubok Peringgi, 05100 Alor Setar, Kedah",
            lat = 6.1245,
            lon = 100.3672,
            aliases = listOf("aman central", "aman central alor setar", "mall alor setar")
        ),
        LandmarkEntry(
            name = "Terminal Bas Shahab Perdana",
            address = "Jalan Shahab 1, 05150 Alor Setar, Kedah",
            lat = 6.1437,
            lon = 100.3688,
            aliases = listOf("shahab perdana", "terminal bas shahab perdana", "stesen bas alor setar")
        ),
        LandmarkEntry(
            name = "Terminal Bukit Lagi Kangar",
            address = "Jalan Bukit Lagi, 01000 Kangar, Perlis",
            lat = 6.4385,
            lon = 100.1983,
            aliases = listOf("terminal bukit lagi", "stesen bas kangar", "terminal kangar")
        ),

        // Pahang
        LandmarkEntry(
            name = "Terminal Sentral Kuantan (TSK)",
            address = "Bandar Indera Mahkota, 25200 Kuantan, Pahang",
            lat = 3.8294,
            lon = 103.2844,
            aliases = listOf("terminal sentral kuantan", "tsk", "tsk kuantan", "stesen bas kuantan")
        ),
        LandmarkEntry(
            name = "Teluk Cempedak",
            address = "Teluk Cempedak Beach, 25050 Kuantan, Pahang",
            lat = 3.8117,
            lon = 103.3689,
            aliases = listOf("teluk cempedak", "pantai teluk cempedak")
        ),

        // Terengganu & Kelantan
        LandmarkEntry(
            name = "Terminal Bas MBKT",
            address = "Jalan Syed Hussein, 20100 Kuala Terengganu, Terengganu",
            lat = 5.3344,
            lon = 103.1388,
            aliases = listOf("terminal mbkt", "terminal bas mbkt", "stesen bas kuala terengganu")
        ),
        LandmarkEntry(
            name = "Hospital Raja Perempuan Zainab II (HRPZ II)",
            address = "Jalan Hospital, 15586 Kota Bharu, Kelantan",
            lat = 6.1268,
            lon = 102.2458,
            aliases = listOf("hrpz", "hrpz ii", "hospital kota bharu", "hospital raja perempuan zainab")
        ),

        // Sarawak
        LandmarkEntry(
            name = "Kuching Waterfront",
            address = "Jalan Main Bazaar, 93000 Kuching, Sarawak",
            lat = 1.5583,
            lon = 110.3444,
            aliases = listOf("kuching waterfront", "waterfront kuching", "main bazaar kuching")
        ),
        LandmarkEntry(
            name = "The Spring Shopping Mall Kuching",
            address = "Jalan Simpang Tiga, 93300 Kuching, Sarawak",
            lat = 1.5348,
            lon = 110.3582,
            aliases = listOf("the spring", "the spring mall", "the spring kuching")
        )
    )

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun lookup(rawQuery: String): GeocodedPlace? {
        val q = normalize(rawQuery)
        val qSlug = slug(rawQuery)
        if (qSlug.isBlank()) return null

        // 1. Exact alias match or exact alphanumeric slug match
        val exact = landmarks.firstOrNull { entry ->
            entry.aliases.any { alias ->
                normalize(alias) == q || slug(alias) == qSlug
            } || slug(entry.name) == qSlug
        }
        if (exact != null) {
            return GeocodedPlace(exact.name, exact.address, exact.lat, exact.lon)
        }

        // 2. Substring & slug containment match
        if (qSlug.length >= 3) {
            val partial = landmarks.firstOrNull { entry ->
                entry.aliases.any { alias ->
                    val aliasNorm = normalize(alias)
                    val aliasSlug = slug(alias)
                    q.contains(aliasNorm) || aliasNorm.contains(q) ||
                        qSlug.contains(aliasSlug) || aliasSlug.contains(qSlug)
                } || slug(entry.name).contains(qSlug)
            }
            if (partial != null) {
                return GeocodedPlace(partial.name, partial.address, partial.lat, partial.lon)
            }
        }

        return null
    }

    fun lookupPartial(rawQuery: String): GeocodedPlace? {
        val q = normalize(rawQuery)
        val qSlug = slug(rawQuery)
        if (qSlug.length < 3) return null

        val tokens = q.split(" ").filter { it.length >= 3 }
        if (tokens.isEmpty() && qSlug.length >= 3) {
            val slugMatched = landmarks.firstOrNull { entry ->
                slug(entry.name).contains(qSlug) || entry.aliases.any { slug(it).contains(qSlug) }
            }
            if (slugMatched != null) {
                return GeocodedPlace(slugMatched.name, slugMatched.address, slugMatched.lat, slugMatched.lon)
            }
        }

        val matched = landmarks.firstOrNull { entry ->
            tokens.all { token ->
                normalize(entry.name).contains(token) ||
                    entry.aliases.any { normalize(it).contains(token) }
            }
        } ?: landmarks.firstOrNull { entry ->
            tokens.any { token ->
                normalize(entry.name).contains(token) ||
                    entry.aliases.any { normalize(it).contains(token) }
            }
        } ?: landmarks.firstOrNull { entry ->
            slug(entry.name).contains(qSlug) || entry.aliases.any { slug(it).contains(qSlug) }
        }

        return matched?.let { GeocodedPlace(it.name, it.address, it.lat, it.lon) }
    }

    fun allLandmarks(): List<LandmarkEntry> = landmarks
}

/**
 * Real Places Search Engine powered by DuckDuckGo Places (Apple Maps POI directory).
 * Zero API keys, worldwide & Malaysia-wide coverage for academies, schools, restaurants,
 * shops, residential complexes, office towers, clinics, and landmarks.
 */
internal object HttpDdgPlacesGeocoder {
    private const val TIMEOUT_MS = 3000
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun geocode(query: String, limit: Int = 5): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://duckduckgo.com/local.js?q=$encoded&tg=maps_places"
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return@withContext emptyList()

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = ArrayList<GeocodedPlace>(results.length())

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val coords = item.optJSONObject("coordinates") ?: continue
                val lat = coords.optDouble("latitude", Double.NaN)
                val lon = coords.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val name = item.optString("name", "").trim().ifBlank { query }
                val address = item.optString("address", "").trim().ifBlank { name }
                val fullAddress = if (address.contains(name)) address else "$name, $address"

                // Verify result is within Malaysia latitude (0.8 to 7.5) and longitude (99.5 to 119.5)
                val isMalaysia = (lat in 0.8..7.5 && lon in 99.5..119.5) ||
                    fullAddress.contains("Malaysia", ignoreCase = true) ||
                    fullAddress.contains("MY", ignoreCase = false)

                if (isMalaysia) {
                    list.add(GeocodedPlace(name = name, address = fullAddress, lat = lat, lon = lon))
                    if (list.size >= limit) break
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Lightweight HTTP geocoder connecting to Photon (Komoot) or OpenStreetMap Nominatim.
 */
internal object HttpOsmPhotonGeocoder {
    private const val TIMEOUT_MS = 3000
    private const val USER_AGENT = "CatchIt-DestinationSearch/1.1 (Android)"

    suspend fun geocode(query: String, limit: Int = 5): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val photonUrl = "https://photon.komoot.io/api/?q=$encoded&lat=3.1390&lon=101.6869&limit=$limit"
            val photonResults = fetchPhoton(photonUrl)
            if (photonResults.isNotEmpty()) return@withContext photonResults

            val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encoded&countrycodes=my&format=json&limit=$limit"
            fetchNominatim(nominatimUrl)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchPhoton(urlString: String): List<GeocodedPlace> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val features = root.optJSONArray("features") ?: return emptyList()
            val list = ArrayList<GeocodedPlace>(features.length())

            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val geom = feat.getJSONObject("geometry")
                val coords = geom.getJSONArray("coordinates")
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                val props = feat.optJSONObject("properties")
                val country = props?.optString("country", "Malaysia") ?: "Malaysia"
                val countryCode = props?.optString("countrycode", "MY")?.uppercase() ?: "MY"
                if (countryCode != "MY" && !country.contains("Malaysia", ignoreCase = true)) {
                    continue
                }
                val rawName = props?.optString("name", "")?.trim() ?: ""
                val street = props?.optString("street", "")?.trim() ?: ""
                val city = props?.optString("city", "")?.trim() ?: ""
                val state = props?.optString("state", "")?.trim() ?: ""

                val isNumber = rawName.matches(Regex("^(?i)(no\\.?)?\\s*[0-9A-Za-z\\-/#]+$")) ||
                    rawName.all { it.isDigit() || it == '-' || it == '/' || it == '.' }

                val name = if (rawName.isNotBlank() && !isNumber) {
                    rawName
                } else if (street.isNotBlank()) {
                    if (rawName.isNotBlank()) "$rawName $street" else street
                } else if (city.isNotBlank()) {
                    city
                } else {
                    "Destination"
                }

                val address = listOf(name, street, city, state, country)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                list.add(GeocodedPlace(name = name, address = address, lat = lat, lon = lon))
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchNominatim(urlString: String): List<GeocodedPlace> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            val list = ArrayList<GeocodedPlace>(array.length())

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val lat = item.getString("lat").toDoubleOrNull() ?: continue
                val lon = item.getString("lon").toDoubleOrNull() ?: continue
                val displayName = item.optString("display_name", "")
                val rawName = item.optString("name", "").trim()
                val firstToken = displayName.substringBefore(',').trim()
                val isNumber = firstToken.matches(Regex("^(?i)(no\\.?)?\\s*[0-9A-Za-z\\-/#]+$")) ||
                    firstToken.all { it.isDigit() || it == '-' || it == '/' || it == '.' }

                val name = if (rawName.isNotBlank()) {
                    rawName
                } else if (!isNumber && firstToken.isNotBlank()) {
                    firstToken
                } else {
                    // If first token is house number e.g. "No. 1", take the second token as street
                    val parts = displayName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.size >= 2) "${parts[0]} ${parts[1]}" else displayName
                }
                list.add(GeocodedPlace(name = name, address = displayName, lat = lat, lon = lon))
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}
