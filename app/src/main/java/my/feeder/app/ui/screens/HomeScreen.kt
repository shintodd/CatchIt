package my.feeder.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import my.feeder.app.R
import my.feeder.app.ui.components.MaxWidthContainer
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.StaggeredRow
import my.feeder.app.ui.components.pressScale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.GtfsRoute
import my.feeder.app.ui.theme.*
import java.util.Calendar
import java.util.TimeZone

/**
 * A stop that matches the search query, together with every route that serves it.
 *
 * Grouped by stop NAME rather than stop id: one physical stop is usually served by
 * several feeder routes, and a commuter who searches "TAMAN LEP 7" wants a single row
 * listing the route numbers that stop them - not one row per route.
 */
internal data class StopMatch(
    val name: String,
    val code: String,
    val routeCodes: List<String>,
    val headsigns: List<String>
)

/**
 * Find stops whose name matches the query.
 *
 * This is what lets someone who does not know bus routes still find the bus: they
 * type where they want to go ("Cyberjaya", "Putrajaya", "BOMBA CYBERJAYA") and the
 * app answers with the route numbers and the direction that go there.
 */
internal fun matchStops(routes: List<GtfsRoute>, query: String): List<StopMatch> {
    if (query.isBlank()) return emptyList()
    val byName = LinkedHashMap<String, StopMatch>()
    for (route in routes) {
        for (stop in route.stops) {
            if (!stop.name.contains(query, ignoreCase = true)) continue
            val existing = byName[stop.name]
            byName[stop.name] = if (existing == null) {
                StopMatch(stop.name, stop.code, listOf(route.code), listOf(route.headsign))
            } else if (existing.routeCodes.none { it.equals(route.code, ignoreCase = true) }) {
                existing.copy(
                    routeCodes = existing.routeCodes + route.code,
                    headsigns = existing.headsigns + route.headsign
                )
            } else {
                existing
            }
        }
    }
    return byName.values.toList()
}

/**
 * Home is the route directory: search any of the 91 feeder routes, star the ones
 * you ride, and read each one's next departures and live bus count at a glance.
 * Tapping a route opens its live map and timetable.
 */
@Composable
fun HomeScreen(
    activeStationName: String = "MRT Phileo Damansara",
    activeStationCode: String = "KG12",
    onStationPickerClick: () -> Unit,
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val routes by GtfsRepository.routes.collectAsState()
    val liveBuses by GtfsRepository.liveBuses.collectAsState()
    val schedules by GtfsRepository.schedules.collectAsState()
    val pinned by GtfsRepository.pinnedRoutes.collectAsState()
    val hasLiveData by GtfsRepository.hasLiveData.collectAsState()

    // Live-bus counts per route, computed once per poll — not per row per frame.
    val liveCounts = remember(liveBuses, routes) {
        val feederCodes = routes.map { it.code.uppercase() }.toSet()
        val counts = HashMap<String, Int>(feederCodes.size)
        for ((key, buses) in liveBuses) {
            val base = key.substringBefore(' ').uppercase()
            if (base in feederCodes) counts[base] = (counts[base] ?: 0) + buses.size
        }
        counts
    }

    var query by rememberSaveable { mutableStateOf("") }

    val pinnedRoutes = remember(pinned, routes) {
        pinned.mapNotNull { code -> routes.find { it.code.equals(code, ignoreCase = true) } }
    }

    /**
     * Default view shows a short, curated list — never all 91 routes at once.
     * The set rotates daily: drawn from the 30 busiest routes (measured by
     * scheduled trips/day), but the specific 6 shown changes each day. Stable
     * within a day (same 6 all day), deterministic across days (no flicker on
     * recomposition, and a restart on the same day shows the same set).
     * Pinned routes come first and are excluded from the rotation.
     */
    val popularRoutes = remember(routes, schedules, pinnedRoutes) {
        val pinnedCodes = pinnedRoutes.map { it.code.uppercase() }.toSet()
        val pool = routes
            .filter { it.code.uppercase() !in pinnedCodes }
            .sortedByDescending { schedules[it.code.uppercase()]?.size ?: 0 }
            .take(30)
        if (pool.size <= 6) return@remember pool

        // Day-of-year in Malaysia time seeds a stable 6-route slice per day.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"))
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val start = (dayOfYear * 6) % pool.size
        (0 until 6).map { pool[(start + it) % pool.size] }
    }

    val ordered = popularRoutes

    // Count only vehicles on routes we actually list — the kiosk feed carries the
    // whole Prasarana fleet, so summing it would overstate this app's coverage.
    val liveTotal = remember(liveCounts) { liveCounts.values.sum() }
    val isLoading = routes.isEmpty()

    MaxWidthContainer(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = Spacing.lg)
        ) {
        item {
            HeroHeader(
                stationCode = activeStationCode,
                stationName = activeStationName,
                hasLiveData = hasLiveData,
                liveTotal = liveTotal,
                onStationClick = onStationPickerClick
            )
        }

        item {
            RouteSearchBar(
                query = query,
                onQueryChange = { query = it },
                routes = routes,
                liveCounts = liveCounts,
                schedules = schedules,
                onRouteSelected = { code ->
                    query = ""
                    onRouteClick(code)
                },
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }

        if (isLoading) {
            // Loading state: skeleton rows, so the screen never flashes empty.
            items(6) { LoadingRouteRow() }
        } else {
            if (pinnedRoutes.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("Your routes")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            pinnedRoutes.forEach { r ->
                                PinnedRouteCard(
                                    route = r,
                                    schedule = schedules[r.code.uppercase()] ?: emptyList(),
                                    liveCount = liveCounts[r.code.uppercase()] ?: 0,
                                    onTogglePin = { GtfsRepository.togglePin(r.code) },
                                    onClick = { onRouteClick(r.code) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("Popular routes")
            }

            itemsIndexed(ordered, key = { _, r -> r.code }) { index, route ->
                RouteRow(
                    route = route,
                    schedule = schedules[route.code.uppercase()] ?: emptyList(),
                    liveCount = liveCounts[route.code.uppercase()] ?: 0,
                    isPinned = GtfsRepository.isPinned(route.code),
                    index = index,
                    onTogglePin = { GtfsRepository.togglePin(route.code) },
                    onClick = { onRouteClick(route.code) }
                )
            }

            item {
                Text(
                    text = "Showing ${ordered.size} of ${routes.size} routes — " +
                        "a different set each day. Search above for any route.",
                    fontSize = TypeSize.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                        vertical = Spacing.md
                    )
                )
            }
        }
    }
    }
}

// ============================================================================
// Hero header
// ============================================================================

@Composable
private fun HeroHeader(
    stationCode: String,
    stationName: String,
    hasLiveData: Boolean,
    liveTotal: Int,
    onStationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(PulseGreen700, PulseGreen900)))
            // Android 15 draws edge-to-edge, so the gradient runs under the status
            // bar but the content must start below it or it collides with the clock.
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo_catchit_header),
                contentDescription = "CatchIt",
                modifier = Modifier.height(34.dp)
            )

            // Specific live status, not a decorative dot.
            Text(
                text = when {
                    !hasLiveData -> "Connecting…"
                    liveTotal == 0 -> "No buses live"
                    liveTotal == 1 -> "1 bus live"
                    else -> "$liveTotal buses live"
                },
                fontSize = TypeSize.caption,
                fontWeight = FontWeight.Bold,
                color = if (liveTotal > 0) Color(0xFF86EFAC) else PulseGreen100.copy(alpha = 0.85f),
                style = TabularNums
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Surface(
            color = Color.White.copy(alpha = 0.14f),
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStationClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_locate,
                    contentDescription = null,
                    size = 18.dp,
                    tint = Color(0xFF86EFAC)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "$stationCode · $stationName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_chevron_down,
                    contentDescription = "Change station",
                    size = 16.dp,
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ============================================================================
// Search
// ============================================================================

/**
 * One search field, one job: type a route code or place name and the list
 * appears directly beneath it. Picking a route opens it.
 *
 * The results panel is an inline card rather than a popup menu so it inherits
 * the app's own corner radius and surface colour — no detached system-looking
 * dropdown floating over the page.
 */
@Composable
private fun RouteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    routes: List<GtfsRoute>,
    liveCounts: Map<String, Int>,
    schedules: Map<String, List<String>>,
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val matches = remember(routes, query) {
        if (query.isBlank()) routes.sortedBy { it.code.uppercase() }
        else routes.filter {
            it.code.contains(query, ignoreCase = true) ||
                it.headsign.contains(query, ignoreCase = true)
        }
    }

    // Stops are matched too, so "where do I want to go" works even when the place
    // is a stop name rather than a route destination.
    val stopMatches = remember(routes, query) { matchStops(routes, query) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            placeholder = { Text("Search route or destination", fontSize = TypeSize.body) },
            leadingIcon = {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_search,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_x,
                            contentDescription = "Clear search",
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search")
                .onFocusChanged { focus ->
                    if (focus.isFocused) expanded = true
                    else if (query.isBlank()) expanded = false
                },
            shape = RoundedCornerShape(Radius.xl),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.xs)
                ) {
                    if (matches.isEmpty() && stopMatches.isEmpty()) {
                        Text(
                            text = "No route or stop matches \"$query\"",
                            fontSize = TypeSize.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.md
                            )
                        )
                    } else {
                        if (matches.isNotEmpty()) {
                            SectionLabel("Routes")
                            matches.forEach { r ->
                                SearchResultRow(
                                    route = r,
                                    liveCount = liveCounts[r.code.uppercase()] ?: 0,
                                    onClick = {
                                        expanded = false
                                        onRouteSelected(r.code)
                                    }
                                )
                            }
                        }
                        if (stopMatches.isNotEmpty()) {
                            SectionLabel("Stops")
                            stopMatches.take(40).forEach { stop ->
                                StopSearchResultRow(
                                    stop = stop,
                                    schedules = schedules,
                                    liveCounts = liveCounts,
                                    onClick = { code ->
                                        expanded = false
                                        onRouteSelected(code)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One tappable route inside the search results panel. */
@Composable
private fun SearchResultRow(
    route: GtfsRoute,
    liveCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search-result-${route.code}")
            .clickable { onClick() }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.code,
                fontSize = TypeSize.body,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = shortHeadsign(route.headsign),
                fontSize = TypeSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (liveCount > 0) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = if (liveCount == 1) "1 live" else "$liveCount live",
                fontSize = TypeSize.caption,
                fontWeight = FontWeight.Bold,
                color = TelemetryLiveLight,
                style = TabularNums
            )
        }
    }
}

/**
 * One stop inside the search results panel.
 *
 * This is the "I don't know the bus routes, I just know where I want to go" answer:
 * the stop name is where to board, the route codes are the numbers to board, and the
 * headsign is which way the bus is going. Feeder routes are loops, so the direction
 * matters as much as the number.
 */
@Composable
private fun StopSearchResultRow(
    stop: StopMatch,
    schedules: Map<String, List<String>>,
    liveCounts: Map<String, Int>,
    onClick: (String) -> Unit
) {
    val codes = stop.routeCodes.take(3).joinToString(" · ")
    val more = stop.routeCodes.size - 3
    val direction = stop.headsigns.firstOrNull()?.let { shortHeadsign(it) }.orEmpty()
    val next = stop.routeCodes.firstNotNullOfOrNull { code ->
        nextDepartures(schedules[code.uppercase()] ?: emptyList(), 1).firstOrNull()
    }
    val live = stop.routeCodes.sumOf { liveCounts[it.uppercase()] ?: 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stop-result-${stop.code}")
            .clickable { onClick(stop.routeCodes.first()) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stop.name,
                fontSize = TypeSize.body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(codes)
                    if (more > 0) append(" +$more")
                    if (direction.isNotEmpty()) append("  →  ").append(direction)
                },
                fontSize = TypeSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (next != null) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = next,
                fontSize = TypeSize.caption,
                fontWeight = FontWeight.Bold,
                color = TelemetryLiveLight,
                style = TabularNums
            )
        } else if (live > 0) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = if (live == 1) "1 live" else "$live live",
                fontSize = TypeSize.caption,
                fontWeight = FontWeight.Bold,
                color = TelemetryLiveLight,
                style = TabularNums
            )
        }
    }
}

// ============================================================================
// Section label
// ============================================================================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = TypeSize.title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
}

// ============================================================================
// Pinned route card
// ============================================================================

@Composable
private fun PinnedRouteCard(
    route: GtfsRoute,
    schedule: List<String>,
    liveCount: Int,
    onTogglePin: () -> Unit,
    onClick: () -> Unit
) {
    val next = nextDepartures(schedule, 1).firstOrNull()

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(158.dp)
            .testTag("pinned-${route.code}")
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.code,
                    fontSize = TypeSize.title,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Filled star so the pinned card can be unpinned right here.
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("pinned-star-${route.code}")
                ) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_star_filled,
                        contentDescription = "Unpin ${route.code}",
                        size = 18.dp,
                        tint = Accent
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = shortHeadsign(route.headsign),
                fontSize = TypeSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (next != null) {
                Text(
                    text = next,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentOnContainer,
                    style = TabularNums
                )
            } else {
                Text(
                    text = "Ended",
                    fontSize = TypeSize.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (liveCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = if (liveCount == 1) "1 live now" else "$liveCount live now",
                    fontSize = TypeSize.caption,
                    fontWeight = FontWeight.Bold,
                    color = TelemetryLiveLight
                )
            }
        }
    }
}

// ============================================================================
// Route row
// ============================================================================

@Composable
private fun RouteRow(
    route: GtfsRoute,
    schedule: List<String>,
    liveCount: Int,
    isPinned: Boolean,
    index: Int,
    onTogglePin: () -> Unit,
    onClick: () -> Unit
) {
    val nextThree = nextDepartures(schedule, 3)
    val next = nextThree.firstOrNull()

    // Rule 4: press compression. Rule 13: damped settle, no overshoot.
    val interaction = remember { MutableInteractionSource() }

    StaggeredRow(index = index) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                .testTag("route-${route.code}")
                .pressScale(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route code is the identifier — no extra badge wrapper around it.
                Text(
                    text = route.code,
                    fontSize = TypeSize.title,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.headsign,
                        fontSize = TypeSize.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            liveCount > 0 && nextThree.isNotEmpty() ->
                                "${if (liveCount == 1) "1 live" else "$liveCount live"} · ${nextThree.joinToString(" · ")}"
                            liveCount > 0 -> if (liveCount == 1) "1 live now" else "$liveCount live now"
                            nextThree.isNotEmpty() -> nextThree.joinToString(" · ")
                            else -> "Service ended"
                        },
                        fontSize = TypeSize.caption,
                        color = if (liveCount > 0) TelemetryLiveLight
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (liveCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TabularNums
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                // Next departure — the row's main figure, in the accent colour.
                if (next != null) {
                    Text(
                        text = next,
                        fontSize = TypeSize.title,
                        fontWeight = FontWeight.Black,
                        color = AccentOnContainer,
                        style = TabularNums
                    )
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("star-${route.code}")
                ) {
                    // Rule 18: crossfade the star with scale instead of a hard swap.
                    androidx.compose.animation.Crossfade(
                        targetState = isPinned,
                        animationSpec = androidx.compose.animation.core.tween(
                            Motion.MICRO,
                            easing = Motion.Enter
                        ),
                        label = "starCrossfade"
                    ) { pinned ->
                        LucideIcon(
                            drawableId = if (pinned) R.drawable.ic_lucide_star_filled
                            else R.drawable.ic_lucide_star,
                            contentDescription = if (pinned) "Unpin ${route.code}"
                            else "Pin ${route.code}",
                            size = 20.dp,
                            tint = if (pinned) Accent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Loading + empty states
// ============================================================================

@Composable
private fun LoadingRouteRow() {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 16.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                )
            }
        }
    }
}

// ============================================================================
// Schedule helpers
// ============================================================================

/** Current wall-clock minutes since midnight, Malaysia time. */
private fun nowMinutesMyT(): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"))
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

/** Upcoming departures from a sorted "HH:mm" list. */
private fun nextDepartures(schedule: List<String>, count: Int): List<String> {
    if (schedule.isEmpty()) return emptyList()
    val now = nowMinutesMyT()
    return schedule
        .mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size != 2) return@mapNotNull null
            val mins = (parts[0].toIntOrNull() ?: return@mapNotNull null) * 60 +
                (parts[1].toIntOrNull() ?: return@mapNotNull null)
            mins to s
        }
        .filter { it.first >= now }
        .map { it.second }
        .take(count)
}

/** Collapses a verbose headsign to the part before the first " - ". */
internal fun shortHeadsign(headsign: String?): String {
    if (headsign.isNullOrBlank()) return ""
    return headsign.substringBefore(" - ")
}
