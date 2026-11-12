package my.feeder.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.R
import my.feeder.app.data.AlertScheduleManager
import my.feeder.app.data.DepartureAlarmStore
import my.feeder.app.data.DepartureAlarmWorker
import my.feeder.app.data.DepartureAlert
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.GtfsStop
import my.feeder.app.ui.components.LucideAnimatedBell
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.theme.*

/** Unwraps the Activity behind a Compose context, if there is one. */
private tailrec fun Context.findActivity(): my.feeder.app.MainActivity? = when (this) {
    is my.feeder.app.MainActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Finds a stop by name in the route's stop list. */
private fun resolveStop(stops: List<GtfsStop>, name: String?): GtfsStop? {
    if (name.isNullOrBlank()) return null
    return stops.firstOrNull { s -> s.name == name }
}

/**
 * Sets up a two-stage departure alarm on one route.
 *
 * Each stage is anchored to **its own stop** on the route:
 *
 *  - stage 1 fires when the bus is a few minutes from an early stop — the
 *    moment to start moving;
 *  - stage 2 fires when the bus is a few minutes from a later stop — the
 *    moment to be at your waiting spot.
 *
 * Both stages are measured against the same approaching bus.
 */
@Composable
fun TwoStageAlertSheet(
    routeCode: String,
    routeHeadsign: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val route = remember(routeCode) { GtfsRepository.getRouteByCode(routeCode) }
    val stops = remember(route) { route?.stops?.sortedBy { it.seq } ?: emptyList() }

    // An alarm already set on this route, if there is one, so the sheet opens
    // showing what is actually armed.
    val existing = remember(routeCode) {
        GtfsRepository.alertsForRoute(routeCode).firstOrNull()
    }

    var stage1Stop by remember(routeCode, existing) {
        mutableStateOf<GtfsStop?>(
            resolveStop(stops, existing?.stage1StopName) ?: stops.firstOrNull()
        )
    }
    var stage2Stop by remember(routeCode, existing) {
        mutableStateOf<GtfsStop?>(
            resolveStop(stops, existing?.stage2StopName) ?: stops.lastOrNull()
        )
    }
    var pickingFor by rememberSaveable { mutableStateOf<Int?>(null) }   // 1 or 2

    val isArmed = existing != null
    val sameStop = stage1Stop?.id == stage2Stop?.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideAnimatedBell(
                        isArmed = isArmed,
                        size = 20.dp,
                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = if (isArmed) "Alarm is on" else "Set up your alarm",
                        fontSize = TypeSize.headline,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (routeHeadsign.isBlank()) routeCode else "$routeCode · $routeHeadsign",
                    fontSize = TypeSize.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_x,
                    contentDescription = "Close",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // ---- Stage 1: Early Warning ----
        AlarmStageCard(
            title = "1. Early warning",
            subtitle = "Leave home/office when bus arrives here",
            stop = stage1Stop,
            onPickStop = { pickingFor = 1 }
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // ---- Stage 2: Boarding Stop ----
        AlarmStageCard(
            title = "2. Boarding stop",
            subtitle = "Where you wait & board the bus",
            stop = stage2Stop,
            onPickStop = { pickingFor = 2 }
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        val scheduleConfig by AlertScheduleManager.config.collectAsState()

        // Highlighted Commute Schedule Badge (Clean & Concise)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_clock,
                    contentDescription = "Schedule",
                    size = 18.dp,
                    tint = if (scheduleConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (scheduleConfig.enabled) {
                            "${scheduleConfig.startTimeFormatted} – ${scheduleConfig.endTimeFormatted} (${if (scheduleConfig.weekdaysOnly) "Mon–Fri" else "Daily"})"
                        } else {
                            "24/7 Monitoring"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (scheduleConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = TabularNums
                    )
                    Text(
                        text = if (scheduleConfig.enabled) "Active commute hours · Sleeps to save battery" else "Alerts run 24/7 · Customize in Settings",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = if (scheduleConfig.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (scheduleConfig.enabled) "Active" else "24/7",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (scheduleConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // CTA
        if (isArmed) {
            OutlinedButton(
                onClick = {
                    existing?.let { GtfsRepository.cancelAlert(it.routeCode, it.stopId) }
                    Toast.makeText(context, "Alarm turned off", Toast.LENGTH_SHORT).show()
                    if (DepartureAlarmStore.load().isEmpty()) {
                        DepartureAlarmWorker.cancel(context)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TelemetryStaleLight)
            ) {
                Text(text = "Turn alarm off", fontSize = TypeSize.body, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    val s1 = stage1Stop ?: return@Button
                    val s2 = stage2Stop ?: return@Button
                    GtfsRepository.armAlert(
                        DepartureAlert(
                            routeCode = routeCode,
                            stopId = s2.id,
                            stopName = s2.name,
                            stage1StopId = s1.id,
                            stage1StopName = s1.name,
                            stage2StopId = s2.id,
                            stage2StopName = s2.name
                        )
                    )
                    Toast.makeText(context, "Alarm on for $routeCode", Toast.LENGTH_SHORT).show()
                    DepartureAlarmWorker.schedule(context)
                    onDismiss()
                    // Ask for notification permission now, not on launch: this is
                    // the moment the user actually wants to be warned.
                    context.findActivity()?.requestNotificationPermission()
                    // Prompt 1-tap background auto-start if battery optimizations active
                    if (!my.feeder.app.util.OemBatteryHelper.isIgnoringBatteryOptimizations(context)) {
                        my.feeder.app.util.OemBatteryHelper.launchOneTapSetup(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = "Turn alarm on", fontSize = TypeSize.body, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ---- Stop picker ----
    pickingFor?.let { which ->
        val current = if (which == 1) stage1Stop else stage2Stop
        AlertDialog(
            onDismissRequest = { pickingFor = null },
            title = {
                Text(
                    text = if (which == 1) "Stage 1 stop" else "Stage 2 stop",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(stops, key = { it.id }) { stop ->
                        val isSelected = current?.id == stop.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (which == 1) stage1Stop = stop else stage2Stop = stop
                                    pickingFor = null
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stop.name,
                                    fontSize = TypeSize.body,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (stop.code.isNotBlank()) {
                                    Text(
                                        text = "Stop ${stop.code}",
                                        fontSize = TypeSize.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                LucideIcon(
                                    drawableId = R.drawable.ic_lucide_check,
                                    contentDescription = "Selected",
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingFor = null }) { Text("Close") }
            }
        )
    }
}

/**
 * One stage of the alarm: clean, unified card with zero box-in-box nesting.
 */
@Composable
private fun AlarmStageCard(
    title: String,
    subtitle: String,
    stop: GtfsStop?,
    onPickStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickStop)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_map_pin,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· $subtitle",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stop?.name ?: "Tap to choose a stop",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stop != null && stop.code.isNotBlank()) {
                    Text(
                        text = "Stop ${stop.code}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LucideIcon(
                drawableId = R.drawable.ic_lucide_chevron_down,
                contentDescription = "Change stop",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
