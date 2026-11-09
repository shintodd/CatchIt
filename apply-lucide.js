const fs = require('fs');
const path = require('path');

const base = 'C:/Users/ainol/.gemini/antigravity/scratch/feedermy-android/app/src/main/java/my/feeder/app';

// 1. SettingsScreen.kt
let settings = fs.readFileSync(base + '/ui/screens/SettingsScreen.kt', 'utf8');

// Add imports
settings = settings.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.BuildConfig
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.util.DownloadResult
import my.feeder.app.util.UpdateInfo`
);

// Fix UpdateManager.checkUpdate and downloadAndInstallApk call
settings = settings.replace(
  `                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    updateStatusMessage = "Checking latest release build..."
                                    val info = UpdateManager.checkUpdate(1)
                                    isCheckingUpdate = false
                                    updateStatusMessage = "Downloading \${info.latestVersionName}..."
                                    downloadProgress = 0
                                    val success = UpdateManager.downloadAndInstallApk(
                                        context,
                                        info.downloadUrl
                                    ) { p -> downloadProgress = p }
                                    if (success) {
                                        updateStatusMessage = "Ready for installation"
                                    } else {
                                        updateStatusMessage = "Up to date (v1.0.1)"
                                    }
                                    downloadProgress = -1
                                }`,
  `                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    updateStatusMessage = "Checking latest release build..."
                                    val info = UpdateManager.checkUpdate(context, BuildConfig.VERSION_CODE)
                                    isCheckingUpdate = false
                                    if (info.isUpdateAvailable) {
                                        updateStatusMessage = "Downloading \${info.latestVersionName}..."
                                        downloadProgress = 0
                                        val result = UpdateManager.downloadAndInstallApk(
                                            context,
                                            info
                                        ) { p, text ->
                                            downloadProgress = p
                                        }
                                        when (result) {
                                            is DownloadResult.Success -> {
                                                updateStatusMessage = "Ready for installation"
                                            }
                                            is DownloadResult.Error -> {
                                                updateStatusMessage = "Download failed: \${result.message}"
                                            }
                                        }
                                    } else {
                                        updateStatusMessage = "App is already on the latest build (v\${BuildConfig.VERSION_NAME})"
                                    }
                                    downloadProgress = -1
                                }`
);

// Lucide Battery Zap in Battery card
settings = settings.replace(
  `Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Battery",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )`,
  `LucideIcon(
                                    drawableId = R.drawable.ic_lucide_zap,
                                    contentDescription = "Battery",
                                    size = 20.dp,
                                    tint = Color(0xFFFFC20E)
                                )`
);

// Lucide Bell in Send Test Alert Button
settings = settings.replace(
  `Text(
                            text = "Send Test Alert Notification",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )`,
  `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LucideIcon(drawableId = R.drawable.ic_lucide_bell, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Send Test Alert Notification",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }`
);

// Lucide Zap in Open Battery Whitelist Wizard Button
settings = settings.replace(
  `Text(
                            text = "Open Battery Whitelist Wizard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )`,
  `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LucideIcon(drawableId = R.drawable.ic_lucide_zap, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Open Battery Whitelist Wizard",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }`
);

// Lucide Download in Check & Install APK Update Button
settings = settings.replace(
  `Text(
                                text = if (isCheckingUpdate) "Checking..." else "Check & Install APK Update",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )`,
  `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LucideIcon(drawableId = R.drawable.ic_lucide_download, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                Text(
                                    text = if (isCheckingUpdate) "Checking..." else "Check & Install APK Update",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }`
);

fs.writeFileSync(base + '/ui/screens/SettingsScreen.kt', settings, 'utf8');
console.log('1. SettingsScreen.kt done');

// 2. HomeScreen.kt
let home = fs.readFileSync(base + '/ui/screens/HomeScreen.kt', 'utf8');
home = home.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.LucideAnimatedBell
import my.feeder.app.ui.components.LucideAnimatedChevron`
);

// Bell on departures
home = home.replace(
  `Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Set Alert",
                                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )`,
  `LucideAnimatedBell(
                                        isArmed = isArmed,
                                        size = 20.dp,
                                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )`
);

// Quick tool 1: Bus
home = home.replace(
  `Text(text = "dYsO", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_bus, contentDescription = "91 Routes", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);

// Quick tool 2: Rail Map
home = home.replace(
  `Text(text = "dY-,?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_map, contentDescription = "Rail Map", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);

// Quick tool 3: Bays
home = home.replace(
  `Text(text = "dY"?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_map_pin, contentDescription = "257 Bays", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);

// Quick tool 4: Settings
home = home.replace(
  `Text(text = "sT,?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_settings, contentDescription = "Settings", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);

fs.writeFileSync(base + '/ui/screens/HomeScreen.kt', home, 'utf8');
console.log('2. HomeScreen.kt done');

// 3. LiveBusScreen.kt
let live = fs.readFileSync(base + '/ui/screens/LiveBusScreen.kt', 'utf8');
live = live.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.LucideAnimatedBell
import my.feeder.app.ui.components.LucideAnimatedRefresh`
);

// Top bar refresh icon
live = live.replace(
  `Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurface
                        )`,
  `LucideAnimatedRefresh(
                            isSpinning = false,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurface
                        )`
);

// All 91 routes search button icon
live = live.replace(
  `Text(text = "dY"?", fontSize = 11.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_bus, contentDescription = "Routes", size = 14.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)`
);

// Timeline bell
live = live.replace(
  `Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Alert",
                                                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )`,
  `LucideAnimatedBell(
                                                        isArmed = isArmed,
                                                        size = 18.dp,
                                                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )`
);

fs.writeFileSync(base + '/ui/screens/LiveBusScreen.kt', live, 'utf8');
console.log('3. LiveBusScreen.kt done');

// 4. JourneyPlannerScreen.kt
let journey = fs.readFileSync(base + '/ui/screens/JourneyPlannerScreen.kt', 'utf8');
journey = journey.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.LucideAnimatedRadio`
);

// Back button
journey = journey.replace(
  `Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_arrow_left, contentDescription = "Back", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)`
);

// Swap button
journey = journey.replace(
  `Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Swap",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )`,
  `LucideIcon(
                                            drawableId = R.drawable.ic_lucide_arrow_down_up,
                                            contentDescription = "Swap",
                                            tint = MaterialTheme.colorScheme.primary,
                                            size = 18.dp
                                        )`
);

// Live tracking radio icon
journey = journey.replace(
  `Text(text = "dY>,?", fontSize = 16.sp)`,
  `LucideAnimatedRadio(isBroadcasting = true, size = 18.dp, tint = Color.White)`
);

fs.writeFileSync(base + '/ui/screens/JourneyPlannerScreen.kt', journey, 'utf8');
console.log('4. JourneyPlannerScreen.kt done');

// 5. OemWizardScreen.kt
let oem = fs.readFileSync(base + '/ui/screens/OemWizardScreen.kt', 'utf8');
oem = oem.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon`
);

// Back button
oem = oem.replace(
  `Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_arrow_left, contentDescription = "Back", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)`
);

// Check in step card
oem = oem.replace(
  `Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )`,
  `LucideIcon(
                                    drawableId = R.drawable.ic_lucide_check,
                                    contentDescription = "Done",
                                    size = 16.dp,
                                    tint = Color.White
                                )`
);

fs.writeFileSync(base + '/ui/screens/OemWizardScreen.kt', oem, 'utf8');
console.log('5. OemWizardScreen.kt done');

// 6. StationHubSheet.kt
let station = fs.readFileSync(base + '/ui/screens/StationHubSheet.kt', 'utf8');
station = station.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon`
);

// Close button
station = station.replace(
  `Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )`,
  `LucideIcon(
                    drawableId = R.drawable.ic_lucide_x,
                    contentDescription = "Close",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurface
                )`
);

// Search icon
station = station.replace(
  `Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )`,
  `LucideIcon(
                    drawableId = R.drawable.ic_lucide_search,
                    contentDescription = "Search",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )`
);

fs.writeFileSync(base + '/ui/screens/StationHubSheet.kt', station, 'utf8');
console.log('6. StationHubSheet.kt done');

// 7. TwoStageAlertSheet.kt
let alert = fs.readFileSync(base + '/ui/screens/TwoStageAlertSheet.kt', 'utf8');
alert = alert.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon
import my.feeder.app.ui.components.LucideAnimatedBell`
);

// Title bell
alert = alert.replace(
  `Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )`,
  `LucideAnimatedBell(
                        isArmed = isArmed,
                        size = 22.dp,
                        tint = if (isArmed) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary
                    )`
);

// Close button
alert = alert.replace(
  `Icon(imageVector = Icons.Default.Close, contentDescription = "Close")`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_x, contentDescription = "Close", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)`
);

fs.writeFileSync(base + '/ui/screens/TwoStageAlertSheet.kt', alert, 'utf8');
console.log('7. TwoStageAlertSheet.kt done');

// 8. RailMapScreen.kt
let rail = fs.readFileSync(base + '/ui/screens/RailMapScreen.kt', 'utf8');
rail = rail.replace(
  'package my.feeder.app.ui.screens',
  `package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon`
);

rail = rail.replace(
  `Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )`,
  `LucideIcon(
                            drawableId = R.drawable.ic_lucide_search,
                            contentDescription = "Search",
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )`
);

fs.writeFileSync(base + '/ui/screens/RailMapScreen.kt', rail, 'utf8');
console.log('8. RailMapScreen.kt done');

console.log('All screens updated cleanly!');
