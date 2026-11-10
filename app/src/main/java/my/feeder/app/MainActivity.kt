package my.feeder.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.feeder.app.data.DepartureAlarmEngine
import my.feeder.app.data.DepartureAlarmStore
import my.feeder.app.data.DepartureAlarmWorker
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.ServiceAlertsNotifier
import my.feeder.app.data.ServiceAlertsRepository
import my.feeder.app.data.ServiceAlertsWorker
import my.feeder.app.ui.navigation.FeederBottomNavBar
import my.feeder.app.ui.screens.*
import my.feeder.app.ui.theme.FeederMYTheme

/**
 * Set on the intent that opens the app from a service-update notification, so
 * the notification lands the user on the notifications list rather than Home.
 */
const val EXTRA_OPEN_NOTIFICATIONS = "catchit.openNotifications"

/** Set on the intent that opens the app from a departure-alarm notification. */
const val EXTRA_OPEN_ALERMS = "catchit.openAlarms"

/** Set on the intent that opens the app for a particular route. */
const val EXTRA_ROUTE_CODE = "catchit.routeCode"

private const val REQUEST_POST_NOTIFICATIONS = 4201

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw edge-to-edge, but force light status-bar icons: the Home header is
        // dark green, so dark icons would disappear against it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        // Service updates keep arriving when the app is closed, the way PULSE's
        // own push notifications do.
        ServiceAlertsWorker.schedule(this)

        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            NotificationNavigation.pending = true
        }

        setContent {
            FeederMYTheme {
                Surface(
                    // testTagsAsResourceId exposes Compose testTag() values as Android
                    // resource ids, which is what the E2E framework's getByTestId
                    // reads on Android. Without it, no test tag is visible.
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    FeederAppNavigation()
                }
            }
        }
    }

    /**
     * Asks for the notification permission on Android 13 and up.
     *
     * This is deliberately **not** done on launch: a permission prompt before
     * the user has any reason to want notifications reads as a dark pattern,
     * and it blocks the whole first screen. It is asked for at the moment the
     * user turns on an alarm, which is exactly when notifications become
     * useful. On older versions the permission is granted at install time.
     */
    fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
    }

    /** A notification tap re-delivers the intent, so the extra has to be read again. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            NotificationNavigation.pending = true
        }
    }
}

/**
 * Carries "a service-update notification brought the user in" into the
 * composition.
 *
 * It has to be snapshot state: the app is usually already running when the
 * notification is tapped, so the flag is set after the navigation effect has
 * already run. Plain state would never be observed.
 */
object NotificationNavigation {
    var pending by mutableStateOf(false)
}

data class SelectedAlertTarget(
    val routeCode: String,
    val routeHeadsign: String = ""
) : java.io.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeederAppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    var showSplash by remember { mutableStateOf(true) }

    // Defer heavy data loading until splash finishes so the intro video plays with 0 frame drops
    LaunchedEffect(showSplash) {
        if (!showSplash) {
            withContext(Dispatchers.IO) {
                GtfsRepository.loadCatalog(context)
                GtfsRepository.loadSchedules(context)
                GtfsRepository.initPins(context)
                GtfsRepository.initAlarms(context)
                ServiceAlertsRepository.init(context)
                ServiceAlertsWorker.schedule(context)
                GtfsRepository.refreshLiveBuses()
                ServiceAlertsRepository.refresh()
            }

            while (true) {
                // Upstream data.gov.my refreshes every 30s; match that cadence.
                delay(30_000L)
                GtfsRepository.refreshLiveBuses()

                // Check the armed alarms against the fresh positions. While the
                // app is open this is exact; the background worker is the
                // best-effort backstop for when it is closed.
                if (DepartureAlarmStore.load().isNotEmpty()) {
                    val announcements = DepartureAlarmEngine.evaluate()
                    if (announcements.isNotEmpty()) {
                        DepartureAlarmEngine.announce(context, announcements)
                    }
                    DepartureAlarmStore.pruneCompleted()
                }
            }
        }
    }

    // Service alerts: poll every 2 minutes. The proxy allows 20 requests a
    // minute, so this is well inside the limit, and it is what makes a newly
    // published disruption show up in about the same time PULSE pushes it.
    LaunchedEffect(showSplash) {
        if (!showSplash) {
            while (true) {
                delay(120_000L)
                ServiceAlertsRepository.refresh()
            }
        }
    }

    // Raise a notification for each newly published alert, the way PULSE does.
    LaunchedEffect(Unit) {
        ServiceAlertsRepository.newAlerts.collect { fresh ->
            if (fresh.isNotEmpty()) {
                ServiceAlertsNotifier.notifyNew(context, fresh)
            }
        }
    }

    // A service-update notification opens straight onto the notifications list.
    // Keyed on the flag so a tap that arrives while the app is already running
    // still navigates.
    LaunchedEffect(NotificationNavigation.pending) {
        if (NotificationNavigation.pending) {
            NotificationNavigation.pending = false
            navController.navigate("notifications")
        }
    }

    var selectedAlertTarget by rememberSaveable { mutableStateOf<SelectedAlertTarget?>(null) }
    var showStationHubSheet by rememberSaveable { mutableStateOf(false) }
    var activeStationName by rememberSaveable { mutableStateOf("MRT Phileo Damansara") }
    var activeStationCode by rememberSaveable { mutableStateOf("KG12") }

    val alertSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val showBottomBar = currentRoute.startsWith("home") ||
            currentRoute.startsWith("alerts") ||
            currentRoute.startsWith("settings")

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(durationMillis = 700),
        label = "splash_crossfade"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen(
                onFinish = {
                    showSplash = false
                }
            )
        } else {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    FeederBottomNavBar(
                        currentRoute = currentRoute,
                        onTabSelected = { tab ->
                            navController.navigate(tab.route) {
                                // Pop up to the graph's start destination so the back stack
                                // can't grow unboundedly across repeated tab switches.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                composable("home") {
                    HomeScreen(
                        activeStationName = activeStationName,
                        activeStationCode = activeStationCode,
                        onStationPickerClick = { showStationHubSheet = true },
                        onRouteClick = { code -> navController.navigate("route/$code") }
                    )
                }

            composable(
                "route/{code}",
                arguments = listOf(navArgument("code") { type = NavType.StringType })
            ) { entry ->
                val code = entry.arguments?.getString("code") ?: ""
                RouteDetailScreen(
                    routeCode = code,
                    onBack = { navController.popBackStack() },
                    onAlertClick = { routeCode, headsign ->
                        selectedAlertTarget = SelectedAlertTarget(routeCode, headsign)
                    }
                )
            }

            composable("alerts") {
                AlertsScreen(
                    onOpenNotifications = { navController.navigate("notifications") }
                )
            }

            composable("notifications") {
                NotificationsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onOpenOemWizard = { navController.navigate("oem_wizard") }
                )
            }

            composable("oem_wizard") {
                OemWizardScreen(
                    onComplete = { navController.popBackStack() }
                )
            }
        }
    }
}
}

    // Station Hub Bottom Sheet
    if (showStationHubSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStationHubSheet = false },
            sheetState = stationSheetState
        ) {
            StationHubSheetContent(
                selectedStationCode = activeStationCode,
                onSelectStation = { station ->
                    activeStationName = station.name
                    activeStationCode = station.code
                    showStationHubSheet = false
                    Toast.makeText(context, "Switched to ${station.name}", Toast.LENGTH_SHORT).show()
                },
                onClose = { showStationHubSheet = false }
            )
        }
    }

    // 2-Stage Departure Alert Bottom Sheet
    selectedAlertTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { selectedAlertTarget = null },
            sheetState = alertSheetState
        ) {
            TwoStageAlertSheet(
                routeCode = target.routeCode,
                routeHeadsign = target.routeHeadsign,
                onDismiss = { selectedAlertTarget = null }
            )
        }
    }
}
