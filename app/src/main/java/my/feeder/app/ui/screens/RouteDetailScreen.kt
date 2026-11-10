package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideAnimatedBell
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.FeederRealMap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.GtfsStop
import my.feeder.app.data.LiveBusState
import my.feeder.app.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Straight-line distance in metres between two coordinates. */
private fun distMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Route detail: live map up top, schedule + stops below, a star to pin, and a
 * bell to arm a departure alarm. Tapping a route on Home lands here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeCode: String,
    onBack: () -> Unit,
    onAlertClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val routes by GtfsRepository.routes.collectAsState()
    val liveBuses by GtfsRepository.liveBuses.collectAsState()
    val schedules by GtfsRepository.schedules.collectAsState()
    val pinned by GtfsRepository.pinnedRoutes.collectAsState()
    val activeAlerts by GtfsRepository.activeAlerts.collectAsState()

    val route = remember(routes, routeCode) {
        routes.find { it.code.equals(routeCode, ignoreCase = true) }
    }

    val buses = remember(liveBuses, routeCode) { GtfsRepository.liveBusesFor(routeCode) }
    val schedule = remember(schedules, routeCode) {
        schedules[routeCode.trim().uppercase()] ?: emptyList()
    }
    val isPinned = remember(pinned, routeCode) { GtfsRepository.isPinned(routeCode) }

    val nextBus = remember(buses) {
        buses.minByOrNull { if (it.etaMinutes < 0) Int.MAX_VALUE else it.etaMinutes }
    }

    val stops = route?.stops ?: emptyList()

    // Per-stop ETAs from the live bus, keyed by sequence.
    val stopEtas = remember(route, buses) {
        if (stops.isEmpty() || nextBus == null) {
            emptyMap<Int, Int>()
        } else {
            val nextSeq = nextBus.nextStopSeq
            if (nextSeq <= 0) {
                emptyMap()
            } else {
                val speed = if (nextBus.speedKmh < 5) 20.0 else nextBus.speedKmh.toDouble()
                var cumulative = nextBus.etaMinutes.toDouble().coerceAtLeast(0.0)
                val result = LinkedHashMap<Int, Int>()
                val ordered = stops.sortedBy { it.seq }.filter { it.seq >= nextSeq }
                ordered.forEachIndexed { index, stop ->
                    if (index > 0) {
                        val prev = ordered[index - 1]
                        cumulative += (distMeters(prev.lat, prev.lon, stop.lat, stop.lon) / 1000.0) / speed * 60.0
                    }
                    result[stop.seq] = cumulative.toInt().coerceIn(0, 180)
                }
                result
            }
        }
    }

    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 140.dp,
        sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = routeCode,
                            fontSize = TypeSize.title,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = route?.headsign ?: "",
                            fontSize = TypeSize.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("detail-back")
                ) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_arrow_left,
                            contentDescription = "Back",
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { GtfsRepository.togglePin(routeCode) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail-star")
                    ) {
                        LucideIcon(
                            drawableId = if (isPinned) R.drawable.ic_lucide_star_filled
                            else R.drawable.ic_lucide_star,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            size = 22.dp,
                            tint = if (isPinned) Accent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        sheetContent = {
            RouteStopsSheet(
                routeCode = routeCode,
                stops = stops,
                schedule = schedule,
                stopEtas = stopEtas,
                nextBus = nextBus,
                activeAlerts = activeAlerts,
                headsign = route?.headsign.orEmpty(),
                onAlertClick = onAlertClick
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // === NEXT BUS STRIP ===
            NextBusStrip(
                nextBus = nextBus,
                routeCode = routeCode,
                liveCount = buses.size,
                isArmed = activeAlerts.keys.any { it.startsWith("${routeCode}_") }
            )

            // === MAP (fills the rest) ===
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                FeederRealMap(
                    routeCode = routeCode,
                    stops = stops,
                    shapePoints = route?.shape ?: emptyList(),
                    activeBuses = buses,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ============================================================================
// Next bus strip
// ============================================================================

@Composable
private fun NextBusStrip(
    nextBus: LiveBusState?,
    routeCode: String,
    liveCount: Int,
    isArmed: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (nextBus == null) {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_bus,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = "No bus online",
                        fontSize = TypeSize.body,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Check the timetable below",
                        fontSize = TypeSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Surface
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nextBus.plate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = when {
                            nextBus.isAtTerminal -> "at terminal"
                            nextBus.speedKmh < 3 -> "stopped"
                            else -> "${nextBus.speedKmh} km/h"
                        },
                        fontSize = TypeSize.caption,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            nextBus.isAtTerminal -> MaterialTheme.colorScheme.onSurfaceVariant
                            nextBus.speedKmh < 3 -> TelemetryDelayedLight
                            else -> TelemetryLiveLight
                        },
                        style = TabularNums
                    )
                }
                if (nextBus.nextStopName.isNotBlank()) {
                    Text(
                        text = "Next: ${nextBus.nextStopName}",
                        fontSize = TypeSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // The main figure on this screen — largest text in the strip.
            Surface(
                color = if (isArmed) Color(0x334ADE80) else AccentContainer,
                shape = RoundedCornerShape(Radius.md)
            ) {
                Text(
                    text = when {
                        nextBus.isAtTerminal -> "Waiting"
                        nextBus.etaMinutes <= 0 -> "now"
                        else -> "${nextBus.etaMinutes} min"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isArmed) Color(0xFF15803D) else AccentOnContainer,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    style = TabularNums
                )
            }
        }
    }
}

// ============================================================================
// Stops sheet: schedule chips + stop list with bells
// ============================================================================

@Composable
private fun RouteStopsSheet(
    routeCode: String,
    stops: List<GtfsStop>,
    schedule: List<String>,
    stopEtas: Map<Int, Int>,
    nextBus: LiveBusState?,
    activeAlerts: Map<String, my.feeder.app.data.DepartureAlert>,
    headsign: String,
    onAlertClick: (String, String) -> Unit
) {
    val nextSeq = stopEtas.keys.minOrNull()

    // An alarm is already set on this route when any of the route's stops is
    // armed.
    val hasAlarm = remember(activeAlerts, routeCode) {
        activeAlerts.keys.any { it.substringBefore('_').equals(routeCode, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        // === SCHEDULE (departure times) ===
        if (schedule.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Departures",
                        fontSize = TypeSize.title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${schedule.size} trips today",
                        fontSize = TypeSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TabularNums
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                val upcoming = upcomingDepartures(schedule, 6)
                if (upcoming.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        upcoming.forEachIndexed { i, t ->
                            Surface(
                                shape = RoundedCornerShape(Radius.sm),
                                color = if (i == 0) AccentContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = t,
                                    fontSize = TypeSize.body,
                                    fontWeight = FontWeight.Bold,
                                    color = if (i == 0) AccentOnContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                                    style = TabularNums
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Service ended for today",
                        fontSize = TypeSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }

        // === STOPS ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stops.size} stops",
                fontSize = TypeSize.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = TabularNums
            )
            Text(
                text = if (hasAlarm) "Alarm is on" else "Set up a two-stage alarm",
                fontSize = TypeSize.caption,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // One obvious way to set up the alarm: pick two stops on this route.
        Button(
            onClick = { onAlertClick(routeCode, headsign) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            LucideAnimatedBell(
                isArmed = hasAlarm,
                size = 18.dp,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = if (hasAlarm) "Change my alarm" else "Set up my alarm",
                fontSize = TypeSize.body,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        if (stops.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading stops…",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stops.sortedBy { it.seq }) { stop ->
                    val isArmed = activeAlerts.containsKey("${routeCode}_${stop.id}")
                    val eta = stopEtas[stop.seq]
                    val isNext = eta != null && stop.seq == nextSeq

                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = if (isNext) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isArmed -> Color(0xFF4ADE80)
                                isNext -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (stop.seq == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${stop.seq}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stop.seq == 1) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stop.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (stop.code.isNotBlank()) {
                                    Text(
                                        text = "Stop ${stop.code}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = when {
                                    isNext -> Color(0x334ADE80)
                                    eta != null -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(Radius.sm)
                            ) {
                                Text(
                                    text = when {
                                        isNext -> "Arriving"
                                        eta != null -> "$eta min"
                                        else -> "Passed"
                                    },
                                    fontSize = TypeSize.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isNext -> Color(0xFF15803D)
                                        eta != null -> AccentOnContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                                    style = TabularNums
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Schedule helpers (shared with Home)
// ============================================================================

private fun upcomingDepartures(schedule: List<String>, count: Int): List<String> {
    if (schedule.isEmpty()) return emptyList()
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur"))
    val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    return schedule
        .mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size != 2) return@mapNotNull null
            val mins = (parts[0].toIntOrNull() ?: return@mapNotNull null) * 60 +
                (parts[1].toIntOrNull() ?: return@mapNotNull null)
            if (mins >= now) s else null
        }
        .take(count)
}
