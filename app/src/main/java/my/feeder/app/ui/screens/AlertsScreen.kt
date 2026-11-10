package my.feeder.app.ui.screens

import my.feeder.app.R
import android.content.Intent
import android.net.Uri
import my.feeder.app.ui.components.MaxWidthContainer
import my.feeder.app.ui.components.LucideAnimatedBell
import my.feeder.app.ui.components.LucideIcon

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.data.DepartureAlert
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.ServiceAlertsRepository
import my.feeder.app.ui.theme.*

/**
 * Every armed alarm, in one list. This is the feature Moovit and Pulse do not
 * have: a two-stage warning timed to the actual bus, not a timetable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onOpenNotifications: () -> Unit = {},
    onRouteClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeAlerts by GtfsRepository.activeAlerts.collectAsState()
    val liveBuses by GtfsRepository.liveBuses.collectAsState()
    val serviceAlerts by ServiceAlertsRepository.alerts.collectAsState()
    val serviceAlertsStale by ServiceAlertsRepository.stale.collectAsState()
    val readUrls by ServiceAlertsRepository.readUrls.collectAsState()

    val alerts = activeAlerts.values.toList()
    val unreadServiceAlerts = remember(serviceAlerts, readUrls) {
        serviceAlerts.count { it.url !in readUrls }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Alarms",
                        fontWeight = FontWeight.Bold,
                        fontSize = TypeSize.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.size(48.dp)
                        ) {
                            LucideIcon(
                                drawableId = R.drawable.ic_lucide_alert_triangle,
                                contentDescription = "Service notifications",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (unreadServiceAlerts > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(16.dp)
                                    .background(Accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadServiceAlerts > 9) "9+" else "$unreadServiceAlerts",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    style = TabularNums
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        MaxWidthContainer(modifier = modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // === YOUR ALARMS (the app's core feature — first) ===
            if (alerts.isEmpty()) {
                item { NoAlarmsHint() }
            } else {
                item {
                    Text(
                        text = if (alerts.size == 1) "1 armed" else "${alerts.size} armed",
                        fontSize = TypeSize.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TabularNums,
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }
                items(alerts, key = { "${it.routeCode}_${it.stopId}" }) { alert ->
                    AlertRow(
                        alert = alert,
                        liveBus = remember(liveBuses, alert.routeCode) {
                            GtfsRepository.soonestBusFor(alert.routeCode)
                        }
                    )
                }
            }

            // === SERVICE UPDATES (Prasarana disruptions, mirrored from PULSE) ===
            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service updates",
                            fontSize = TypeSize.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (serviceAlertsStale) {
                            Text(
                                text = "May be out of date",
                                fontSize = TypeSize.caption,
                                color = TelemetryDelayedLight
                            )
                        }
                    }
                    Text(
                        text = "See all",
                        fontSize = TypeSize.body,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onOpenNotifications)
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    )
                }
            }

            if (serviceAlerts.isEmpty()) {
                item { ServiceAlertsEmptyRow() }
            } else {
                items(serviceAlerts.take(4), key = { it.url }) { sa ->
                    ServiceAlertRow(sa, onRouteClick = onRouteClick)
                }
            }
        }
    }
    }
}

@Composable
private fun ServiceAlertsEmptyRow() {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_alert_triangle,
                        contentDescription = null,
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No disruptions reported",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "All feeder routes running normally.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoAlarmsHint() {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_bell,
                        contentDescription = null,
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No alarms yet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Open a route and tap \"Set up my alarm\" to pick two stops.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceAlertRow(
    sa: ServiceAlertsRepository.ServiceAlert,
    onRouteClick: (String) -> Unit = {}
) {
    var expanded by rememberSaveable(sa.url) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val kindColor = when (sa.kind) {
        ServiceAlertsRepository.Kind.DIVERSION -> Color(0xFFF59E0B)   // amber
        ServiceAlertsRepository.Kind.DISRUPTION -> Color(0xFFEF4444)  // red
        ServiceAlertsRepository.Kind.DELAY -> Color(0xFFF59E0B)       // amber
        ServiceAlertsRepository.Kind.RAIL -> Color(0xFF6366F1)        // indigo
        ServiceAlertsRepository.Kind.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val kindLabel = when (sa.kind) {
        ServiceAlertsRepository.Kind.DIVERSION -> "Route Diversion"
        ServiceAlertsRepository.Kind.DISRUPTION -> "Service Disruption"
        ServiceAlertsRepository.Kind.DELAY -> "Service Delay"
        ServiceAlertsRepository.Kind.RAIL -> "Rail Update"
        ServiceAlertsRepository.Kind.OTHER -> "Notice"
    }

    val rotationDegrees by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "alert_chevron"
    )

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable {
                expanded = !expanded
                ServiceAlertsRepository.markRead(sa.url)
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = kindColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_alert_triangle,
                            contentDescription = null,
                            size = 20.dp,
                            tint = kindColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = kindColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = kindLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = kindColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (sa.published.isNotBlank()) {
                            Text(
                                text = sa.published,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TabularNums
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = sa.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.xs))

                LucideIcon(
                    drawableId = R.drawable.ic_lucide_chevron_down,
                    contentDescription = if (expanded) "Collapse" else "Expand details",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotationDegrees)
                )
            }

            if (sa.routeCodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    sa.routeCodes.take(if (expanded) sa.routeCodes.size else 6).forEach { code ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = if (expanded) Modifier.clickable { onRouteClick(code) } else Modifier
                        ) {
                            Text(
                                text = code,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (!expanded && sa.routeCodes.size > 6) {
                        Text(
                            text = "+${sa.routeCodes.size - 6}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            // Expanded details: full excerpt + action button
            if (expanded) {
                Spacer(modifier = Modifier.height(Spacing.sm))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "UPDATE DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = kindColor,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (sa.excerpt.isNotBlank()) sa.excerpt else "No additional description in bulletin header. Tap below to read full announcement.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (sa.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sa.url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(Radius.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = kindColor.copy(alpha = 0.15f),
                            contentColor = kindColor
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_globe,
                            contentDescription = null,
                            size = 16.dp,
                            tint = kindColor
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Open Official Bulletin on MyRapid",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: DepartureAlert,
    liveBus: my.feeder.app.data.LiveBusState?
) {
    val isMoving = liveBus != null && liveBus.speedKmh >= 3

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4ADE80)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = alert.routeCode,
                    fontSize = TypeSize.title,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (alert.sameStop) alert.stage2StopName
                        else "${alert.stage1StopName} → ${alert.stage2StopName}",
                        fontSize = TypeSize.body,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            liveBus == null -> "No live bus on this route"
                            isMoving -> "Bus ${liveBus.plate} moving · ${liveBus.speedKmh} km/h"
                            else -> "Bus ${liveBus.plate} stopped"
                        },
                        fontSize = TypeSize.caption,
                        color = if (liveBus == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (isMoving) TelemetryLiveLight else TelemetryDelayedLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { GtfsRepository.cancelAlert(alert.routeCode, alert.stopId) },
                    modifier = Modifier.size(48.dp)
                ) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_x,
                        contentDescription = "Cancel alarm",
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // The two stages, in plain words: each one is a stop on the route.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StageChip(
                    label = "Leave now",
                    stop = alert.stage1StopName,
                    active = !isMoving
                )
                StageChip(
                    label = if (alert.sameStop) "At the curb" else "Go to stop",
                    stop = alert.stage2StopName,
                    active = isMoving
                )
            }
        }
    }
}

@Composable
private fun StageChip(label: String, stop: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = if (active) Color(0x334ADE80)
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label · $stop",
                fontSize = TypeSize.caption,
                fontWeight = FontWeight.Bold,
                color = if (active) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
