package my.feeder.app.ui.screens

import my.feeder.app.R
import android.content.Context
import android.content.ContextWrapper
import my.feeder.app.data.ServiceAlertsRepository
import my.feeder.app.ui.components.LucideIcon

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import my.feeder.app.ui.theme.Accent
import my.feeder.app.ui.theme.Motion
import my.feeder.app.ui.theme.Radius
import my.feeder.app.ui.theme.Spacing
import my.feeder.app.ui.theme.TabularNums
import my.feeder.app.ui.theme.TelemetryDelayedLight
import my.feeder.app.ui.theme.TypeSize

/** Unwraps the Activity behind a Compose context, if there is one. */
private tailrec fun Context.findActivity(): my.feeder.app.MainActivity? = when (this) {
    is my.feeder.app.MainActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Notifications: Prasarana's service disruptions, diversions, delays and rail
 * updates, mirrored from the operator's own PULSE alerts page.
 *
 * Filter tabs mirror the source categories (Bus / Rail / Other) so a rider can
 * ignore the rail noise when they only care about their feeder route. Read
 * state is local and persisted — there is no server-side identity to sync to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Re-read on every recomposition so the banner disappears the moment the
    // user grants the permission.
    val notificationsAllowed = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val alerts by ServiceAlertsRepository.alerts.collectAsState()
    val stale by ServiceAlertsRepository.stale.collectAsState()
    val lastFetchedAt by ServiceAlertsRepository.lastFetchedAt.collectAsState()
    val readUrls by ServiceAlertsRepository.readUrls.collectAsState()

    var filter by remember { mutableStateOf(Filter.ALL) }

    val visible = remember(alerts, filter) {
        alerts.filter { filter.matches(it) }
    }
    val unreadCount = remember(alerts, readUrls) {
        alerts.count { it.url !in readUrls }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = TypeSize.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_arrow_left,
                            contentDescription = "Back",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // === FILTER TABS (scrollable so they never clip on narrow screens) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Filter.entries.forEach { f ->
                    val isSelected = filter == f
                    val count = alerts.count { f.matches(it) && it.url !in readUrls }
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clickable { filter = f }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = f.label,
                                fontSize = TypeSize.body,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else Color(0xFFEF4444),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // === TURN ON ALERTS ===
            // Only shown when notifications are actually off, and only here:
            // the user is looking at a list of alerts, so this is the moment
            // the permission is worth asking for.
            if (!notificationsAllowed) {
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Get alerts for new disruptions",
                                fontSize = TypeSize.body,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Turn on notifications and CatchIt will tell you when a route is disrupted.",
                                fontSize = TypeSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Button(
                            onClick = {
                                context.findActivity()?.requestNotificationPermission()
                            },
                            shape = RoundedCornerShape(Radius.sm),
                            contentPadding = PaddingValues(
                                horizontal = Spacing.md,
                                vertical = Spacing.sm
                            )
                        ) {
                            Text(
                                text = "Turn on",
                                fontSize = TypeSize.caption,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // === MARK ALL READ — primary action, with a success pulse ===
            if (unreadCount > 0) {
                val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
                val scope = rememberCoroutineScope()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            ServiceAlertsRepository.markAllRead()
                            // Rule 8: one-shot pulse confirms the state change.
                            scope.launch {
                                pulse.animateTo(
                                    0.92f,
                                    androidx.compose.animation.core.tween(
                                        Motion.MICRO / 2,
                                        easing = Motion.Exit
                                    )
                                )
                                pulse.animateTo(
                                    1f,
                                    androidx.compose.animation.core.tween(
                                        Motion.EXIT,
                                        easing = Motion.Enter
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(Radius.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color(0xFF3D2B00)
                        ),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        modifier = Modifier.scale(pulse.value)
                    ) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_check,
                            contentDescription = null,
                            size = 15.dp,
                            tint = Color(0xFF3D2B00)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = if (unreadCount == 1) "Mark 1 as read" else "Mark all $unreadCount as read",
                            fontSize = TypeSize.caption,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Say plainly how fresh the list is. A real-time list should never
            // have to be taken on trust.
            val freshness = freshnessLabel(lastFetchedAt, stale)
            if (freshness != null) {
                Text(
                    text = freshness,
                    fontSize = TypeSize.caption,
                    color = if (stale) TelemetryDelayedLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_bell,
                            contentDescription = null,
                            size = 34.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (alerts.isEmpty()) "No service updates right now"
                            else "Nothing in ${filter.label.lowercase()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (alerts.isEmpty())
                                "Prasarana posts disruptions here as they happen"
                            else "Try another filter",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.url }) { sa ->
                        NotificationRow(
                            alert = sa,
                            isUnread = sa.url !in readUrls,
                            onClick = { ServiceAlertsRepository.markRead(sa.url) },
                            onMarkRead = { ServiceAlertsRepository.markRead(sa.url) }
                        )
                    }
                }
            }
        }
    }
}

/** Source categories, mapped onto the kinds the repository already classifies. */
private enum class Filter(val label: String) {
    ALL("All"),
    BUS("Bus"),
    RAIL("Rail"),
    OTHER("Other");

    fun matches(a: ServiceAlertsRepository.ServiceAlert): Boolean = when (this) {
        ALL -> true
        BUS -> a.kind == ServiceAlertsRepository.Kind.DIVERSION ||
            a.kind == ServiceAlertsRepository.Kind.DISRUPTION ||
            a.kind == ServiceAlertsRepository.Kind.DELAY ||
            a.routeCodes.isNotEmpty()
        RAIL -> a.kind == ServiceAlertsRepository.Kind.RAIL
        OTHER -> a.kind == ServiceAlertsRepository.Kind.OTHER && a.routeCodes.isEmpty()
    }
}

@Composable
private fun NotificationRow(
    alert: ServiceAlertsRepository.ServiceAlert,
    isUnread: Boolean,
    onClick: () -> Unit,
    onMarkRead: () -> Unit
) {
    val accent = when (alert.kind) {
        ServiceAlertsRepository.Kind.DIVERSION -> Color(0xFFF59E0B)
        ServiceAlertsRepository.Kind.DISRUPTION -> Color(0xFFEF4444)
        ServiceAlertsRepository.Kind.DELAY -> Color(0xFFF59E0B)
        ServiceAlertsRepository.Kind.RAIL -> Color(0xFF6366F1)
        ServiceAlertsRepository.Kind.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LucideIcon(
                        drawableId = R.drawable.ic_lucide_alert_triangle,
                        contentDescription = null,
                        size = 19.dp,
                        tint = accent
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontSize = TypeSize.body,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (alert.routeCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        alert.routeCodes.take(5).forEach { code ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(Radius.sm)
                            ) {
                                Text(
                                    text = code,
                                    fontSize = TypeSize.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (alert.routeCodes.size > 5) {
                            Text(
                                text = "+${alert.routeCodes.size - 5}",
                                fontSize = TypeSize.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                if (alert.published.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = alert.published,
                        fontSize = TypeSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TabularNums
                    )
                }
            }

            if (isUnread) {
                Spacer(modifier = Modifier.width(Spacing.xs))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Unread marker
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(7.dp)
                            .background(Accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    // Per-notification mark-as-read
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LucideIcon(
                                    drawableId = R.drawable.ic_lucide_check,
                                    contentDescription = "Mark as read",
                                    size = 16.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * How fresh the list is, in the user's terms.
 *
 * "Updated just now" is the promise this screen makes: the alerts come from
 * the same operator page the MyRapid PULSE app reads, polled every two
 * minutes. Saying so is the difference between a live list and one you have
 * to trust.
 */
private fun freshnessLabel(lastFetchedAt: Long, stale: Boolean): String? {
    if (lastFetchedAt <= 0L) return "Checking for updates…"
    val ageSeconds = (System.currentTimeMillis() - lastFetchedAt) / 1000
    val age = when {
        ageSeconds < 60 -> "just now"
        ageSeconds < 120 -> "1 minute ago"
        ageSeconds < 3600 -> "${ageSeconds / 60} minutes ago"
        ageSeconds < 7200 -> "1 hour ago"
        else -> "${ageSeconds / 3600} hours ago"
    }
    return if (stale) "Last checked $age — may be out of date" else "Updated $age"
}
