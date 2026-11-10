package my.feeder.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.feeder.app.data.DepartureAlarmEngine
import my.feeder.app.data.DepartureAlarmStore
import my.feeder.app.data.DepartureAlarmWorker
import my.feeder.app.data.GtfsRepository
import my.feeder.app.data.ServiceAlertsNotifier
import my.feeder.app.data.ServiceAlertsRepository
import my.feeder.app.data.ServiceAlertsWorker
import my.feeder.app.data.ThemeManager
import my.feeder.app.ui.navigation.FeederBottomNavBar
import my.feeder.app.ui.screens.*
import my.feeder.app.ui.theme.CatchItTheme

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
        try {
            ServiceAlertsWorker.schedule(this)
        } catch (e: Exception) {
            // In unit tests WorkManager may not be initialized; ignore
        }

        handleIntentExtras(intent)
        ThemeManager.init(this)

        setContent {
            CatchItTheme {
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
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            NotificationNavigation.pendingNotifications = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERMS, false)) {
            NotificationNavigation.pendingAlarms = true
        }
        intent.getStringExtra(EXTRA_ROUTE_CODE)?.let { route ->
            if (route.isNotBlank()) NotificationNavigation.pendingRouteCode = route
        }
    }
}

/**
 * Carries "a notification brought the user in" into the composition.
 */
object NotificationNavigation {
    var pendingNotifications by mutableStateOf(false)
    var pending: Boolean
        get() = pendingNotifications
        set(value) { pendingNotifications = value }
    var pendingAlarms by mutableStateOf(false)
    var pendingRouteCode by mutableStateOf<String?>(null)
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

    val lifecycleOwner = LocalLifecycleOwner.current

    // Defer heavy data loading until splash finishes so the intro video plays with 0 frame drops
    LaunchedEffect(showSplash) {
        if (!showSplash) {
            withContext(Dispatchers.IO) {
                GtfsRepository.loadCatalog(context)
                GtfsRepository.loadNationwideCatalog(context)
                GtfsRepository.loadSchedules(context)
                GtfsRepository.initPins(context)
                GtfsRepository.initAlarms(context)
                ServiceAlertsRepository.init(context)
                ServiceAlertsWorker.schedule(context)
                GtfsRepository.refreshLiveBuses()
                ServiceAlertsRepository.refresh()
                my.feeder.app.data.AppUpdateManager.checkSilently(context)
            }
        }
    }

    // Lifecycle-aware foreground polling: paused automatically when app is backgrounded
    LaunchedEffect(showSplash, lifecycleOwner) {
        if (!showSplash) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Tick freshness counters every second for live UI time-ago displays
                launch {
                    while (true) {
                        delay(1_000L)
                        GtfsRepository.tickFreshness()
                    }
                }

                // 2. Refresh upstream GTFS-RT vehicle positions every 30s
                launch {
                    while (true) {
                        delay(30_000L)
                        GtfsRepository.refreshLiveBuses()

                        if (DepartureAlarmStore.load().isNotEmpty()) {
                            val announcements = DepartureAlarmEngine.evaluate()
                            if (announcements.isNotEmpty()) {
                                DepartureAlarmEngine.announce(context, announcements)
                            }
                            DepartureAlarmStore.pruneCompleted()
                        }
                    }
                }

                // 3. Service alerts: poll every 2 minutes
                launch {
                    while (true) {
                        delay(120_000L)
                        ServiceAlertsRepository.refresh()
                    }
                }
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

    // Deep link navigation from notifications
    LaunchedEffect(NotificationNavigation.pendingNotifications) {
        if (NotificationNavigation.pendingNotifications) {
            NotificationNavigation.pendingNotifications = false
            navController.navigate("notifications")
        }
    }

    LaunchedEffect(NotificationNavigation.pendingAlarms) {
        if (NotificationNavigation.pendingAlarms) {
            NotificationNavigation.pendingAlarms = false
            navController.navigate("alerts")
        }
    }

    LaunchedEffect(NotificationNavigation.pendingRouteCode) {
        val route = NotificationNavigation.pendingRouteCode
        if (!route.isNullOrBlank()) {
            NotificationNavigation.pendingRouteCode = null
            navController.navigate("route/$route")
        }
    }

    var selectedAlertTarget by rememberSaveable { mutableStateOf<SelectedAlertTarget?>(null) }
    var showStationHubSheet by rememberSaveable { mutableStateOf(false) }
    var activeStationName by rememberSaveable { mutableStateOf("MRT Phileo Damansara") }
    var activeStationCode by rememberSaveable { mutableStateOf("KG12") }

    val alertSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetCoroutineScope = rememberCoroutineScope()

    fun dismissStationSheet() {
        sheetCoroutineScope.launch {
            try {
                stationSheetState.hide()
            } finally {
                showStationHubSheet = false
            }
        }
    }

    fun dismissAlertSheet() {
        sheetCoroutineScope.launch {
            try {
                alertSheetState.hide()
            } finally {
                selectedAlertTarget = null
            }
        }
    }

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
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(280))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(280))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(280))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(280))
                }
            ) {
                composable("home") {
                    HomeScreen(
                        activeStationName = activeStationName,
                        activeStationCode = activeStationCode,
                        onStationPickerClick = { showStationHubSheet = true },
                        onRouteClick = { code -> navController.navigate("route/$code") },
                        onOpenNotifications = { navController.navigate("notifications") }
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
                        onOpenNotifications = { navController.navigate("notifications") },
                        onRouteClick = { code -> navController.navigate("route/$code") }
                    )
                }

                composable("notifications") {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onRouteClick = { code -> navController.navigate("route/$code") }
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

    // Station Hub Bottom Sheet
    if (showStationHubSheet) {
        ModalBottomSheet(
            onDismissRequest = { dismissStationSheet() },
            sheetState = stationSheetState
        ) {
            StationHubSheetContent(
                selectedStationCode = activeStationCode,
                onSelectStation = { station ->
                    activeStationName = station.name
                    activeStationCode = station.code
                    dismissStationSheet()
                    Toast.makeText(context, "Switched to ${station.name}", Toast.LENGTH_SHORT).show()
                },
                onClose = { dismissStationSheet() }
            )
        }
    }

    // 2-Stage Departure Alert Bottom Sheet
    selectedAlertTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { dismissAlertSheet() },
            sheetState = alertSheetState
        ) {
            TwoStageAlertSheet(
                routeCode = target.routeCode,
                routeHeadsign = target.routeHeadsign,
                onDismiss = { dismissAlertSheet() }
            )
        }
    }
}
}
