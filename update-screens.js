const fs = require('fs');
const path = require('path');

const baseDir = 'C:/Users/ainol/.gemini/antigravity/scratch/feedermy-android/app/src/main/java/my/feeder/app';

// 1. UPDATE HomeScreen.kt
let homePath = path.join(baseDir, 'ui/screens/HomeScreen.kt');
let home = fs.readFileSync(homePath, 'utf8');

// Ensure import for Lucide icons
if (!home.includes('my.feeder.app.R')) {
  home = home.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\nimport my.feeder.app.ui.components.LucideAnimatedBell\nimport my.feeder.app.ui.components.LucideAnimatedChevron\n');
}

// Clean up corrupted characters in strings: ' + ' -> ' ↺ ', ' A ' -> ' · ', ' +' ' -> ' → '
home = home.replace(/\+/g, '↺');
home = home.replace(/A/g, '·');
home = home.replace(/\+'/g, '→');

// Replace top bar station pill icon
home = home.replace(
  `Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "O-", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$activeStationCode A $activeStationName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "O,", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)`,
  `LucideIcon(
                                    drawableId = R.drawable.ic_lucide_map_pin,
                                    contentDescription = null,
                                    size = 15.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$activeStationCode · $activeStationName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                LucideAnimatedChevron(
                                    isExpanded = false,
                                    size = 14.dp,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )`
);

// Replace Bell Button on departure items
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

// Replace Quick Tools tiles with Lucide vector icons
// Bus
home = home.replace(
  `Text(text = "dYsO", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_bus, contentDescription = "91 Routes", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);
// Map
home = home.replace(
  `Text(text = "dY-,?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_map, contentDescription = "Rail Map", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);
// Map Pin (Bays)
home = home.replace(
  `Text(text = "dY"?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_map_pin, contentDescription = "257 Bays", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);
// Settings
home = home.replace(
  `Text(text = "sT,?", fontSize = 18.sp)`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_settings, contentDescription = "Settings", size = 22.dp, tint = MaterialTheme.colorScheme.primary)`
);

fs.writeFileSync(homePath, home, 'utf8');
console.log('HomeScreen.kt updated successfully!');

// 2. UPDATE LiveBusScreen.kt
let livePath = path.join(baseDir, 'ui/screens/LiveBusScreen.kt');
let live = fs.readFileSync(livePath, 'utf8');

if (!live.includes('my.feeder.app.R')) {
  live = live.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\nimport my.feeder.app.ui.components.LucideAnimatedBell\nimport my.feeder.app.ui.components.LucideAnimatedRefresh\n');
}

live = live.replace(/\+/g, '↺');
live = live.replace(/A/g, '·');
live = live.replace(/\+'/g, '→');

// Top bar refresh button
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

// Stop bell button
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

fs.writeFileSync(livePath, live, 'utf8');
console.log('LiveBusScreen.kt updated successfully!');

// 3. UPDATE JourneyPlannerScreen.kt
let journeyPath = path.join(baseDir, 'ui/screens/JourneyPlannerScreen.kt');
let journey = fs.readFileSync(journeyPath, 'utf8');

if (!journey.includes('my.feeder.app.R')) {
  journey = journey.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\nimport my.feeder.app.ui.components.LucideAnimatedRadio\n');
}

journey = journey.replace(/\+/g, '↺');
journey = journey.replace(/A/g, '·');
journey = journey.replace(/\+'/g, '→');

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

fs.writeFileSync(journeyPath, journey, 'utf8');
console.log('JourneyPlannerScreen.kt updated successfully!');

// 4. UPDATE SettingsScreen.kt
let settingsPath = path.join(baseDir, 'ui/screens/SettingsScreen.kt');
let settings = fs.readFileSync(settingsPath, 'utf8');

if (!settings.includes('my.feeder.app.R')) {
  settings = settings.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\n');
}

settings = settings.replace(/\+/g, '↺');
settings = settings.replace(/A/g, '·');
settings = settings.replace(/\+'/g, '→');

// Battery zap icon
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

fs.writeFileSync(settingsPath, settings, 'utf8');
console.log('SettingsScreen.kt updated successfully!');

// 5. UPDATE OemWizardScreen.kt
let oemPath = path.join(baseDir, 'ui/screens/OemWizardScreen.kt');
let oem = fs.readFileSync(oemPath, 'utf8');

if (!oem.includes('my.feeder.app.R')) {
  oem = oem.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\n');
}

oem = oem.replace(/\+/g, '↺');
oem = oem.replace(/A/g, '·');
oem = oem.replace(/\+'/g, '→');

// Back button
oem = oem.replace(
  `Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_arrow_left, contentDescription = "Back", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)`
);

// Check icons
oem = oem.replace(/buttonText = if \(isUnrestricted\) "[^"]+" else "Grant Exemption"/,
  'buttonText = if (isUnrestricted) "✓ Whitelisted" else "Grant Exemption"');
oem = oem.replace(/buttonText = if \(isStep2Done\) "[^"]+" else "Open System Settings"/,
  'buttonText = if (isStep2Done) "✓ Configured" else "Open System Settings"');
oem = oem.replace(/buttonText = if \(isStep3Done\) "[^"]+" else "Got It"/,
  'buttonText = if (isStep3Done) "✓ Completed" else "Got It"');

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

fs.writeFileSync(oemPath, oem, 'utf8');
console.log('OemWizardScreen.kt updated successfully!');

// 6. UPDATE StationHubSheet.kt
let stationPath = path.join(baseDir, 'ui/screens/StationHubSheet.kt');
let station = fs.readFileSync(stationPath, 'utf8');

if (!station.includes('my.feeder.app.R')) {
  station = station.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\n');
}

station = station.replace(/\+/g, '↺');
station = station.replace(/A/g, '·');
station = station.replace(/\+'/g, '→');

// Close icon
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

// Selected check icon
station = station.replace(
  `Text(
                                        text = "o"",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )`,
  `LucideIcon(
                                        drawableId = R.drawable.ic_lucide_check,
                                        contentDescription = "Selected",
                                        size = 14.dp,
                                        tint = Color.White
                                    )`
);

fs.writeFileSync(stationPath, station, 'utf8');
console.log('StationHubSheet.kt updated successfully!');

// 7. UPDATE TwoStageAlertSheet.kt
let alertPath = path.join(baseDir, 'ui/screens/TwoStageAlertSheet.kt');
let alert = fs.readFileSync(alertPath, 'utf8');

if (!alert.includes('my.feeder.app.R')) {
  alert = alert.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\nimport my.feeder.app.ui.components.LucideAnimatedBell\n');
}

alert = alert.replace(/\+/g, '↺');
alert = alert.replace(/A/g, '·');
alert = alert.replace(/\+'/g, '→');

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

// Close icon
alert = alert.replace(
  `Icon(imageVector = Icons.Default.Close, contentDescription = "Close")`,
  `LucideIcon(drawableId = R.drawable.ic_lucide_x, contentDescription = "Close", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)`
);

fs.writeFileSync(alertPath, alert, 'utf8');
console.log('TwoStageAlertSheet.kt updated successfully!');

// 8. UPDATE RailMapScreen.kt
let railPath = path.join(baseDir, 'ui/screens/RailMapScreen.kt');
let rail = fs.readFileSync(railPath, 'utf8');

if (!rail.includes('my.feeder.app.R')) {
  rail = rail.replace('package my.feeder.app.ui.screens\n', 'package my.feeder.app.ui.screens\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\n');
}

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

fs.writeFileSync(railPath, rail, 'utf8');
console.log('RailMapScreen.kt updated successfully!');

// 9. UPDATE MainActivity.kt dismiss button
let mainPath = path.join(baseDir, 'MainActivity.kt');
let main = fs.readFileSync(mainPath, 'utf8');
if (!main.includes('my.feeder.app.R')) {
  main = main.replace('package my.feeder.app\n', 'package my.feeder.app\n\nimport my.feeder.app.R\nimport my.feeder.app.ui.components.LucideIcon\n');
}
main = main.replace(/Text\("o ", color = Color\.White, fontSize = 12\.sp\)/,
  'LucideIcon(drawableId = R.drawable.ic_lucide_x, contentDescription = "Dismiss", size = 14.dp, tint = Color.White)');
fs.writeFileSync(mainPath, main, 'utf8');
console.log('MainActivity.kt updated successfully!');

console.log('ALL SCREEN UPGRADES COMPLETE!');
