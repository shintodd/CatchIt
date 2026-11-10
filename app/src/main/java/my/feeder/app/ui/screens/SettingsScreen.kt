package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.BuildConfig
import my.feeder.app.ui.components.MaxWidthContainer
import my.feeder.app.ui.components.LucideIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import my.feeder.app.data.AppThemeMode
import my.feeder.app.data.AppUpdateManager
import my.feeder.app.data.DepartureAlarmStore
import my.feeder.app.data.ThemeManager
import my.feeder.app.data.UpdateStatus
import my.feeder.app.ui.theme.*

/**
 * Settings holds two things only: how early to warn you, and the battery
 * whitelist that keeps those warnings alive. Everything else was cut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenOemWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = TypeSize.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        MaxWidthContainer(modifier = modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. HOW ALARMS WORK
            // There is nothing to configure: the rider picks two stops and the
            // app warns them when the bus reaches each one. So this is an
            // explainer, not a set of knobs.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radius.lg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "How alarms work",
                        fontSize = TypeSize.title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Open a route and tap Set up my alarm. Pick two stops on " +
                            "that route \u2014 the first one you want to be warned about, " +
                            "and the one where you actually wait.",
                        fontSize = TypeSize.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "CatchIt warns you when the bus reaches the first stop, then " +
                            "again when it reaches the second. Both warnings are about the " +
                            "same bus.",
                        fontSize = TypeSize.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            // 2. BATTERY
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radius.lg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(
                            drawableId = R.drawable.ic_lucide_zap,
                            contentDescription = null,
                            size = 20.dp,
                            tint = Accent
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Keep alarms alive",
                            fontSize = TypeSize.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "MIUI, One UI and ColorOS pause background alarms unless " +
                            "CatchIt is whitelisted.",
                        fontSize = TypeSize.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // The one CTA on this screen — solid, full width, unmissable.
                    Button(
                        onClick = onOpenOemWizard,
                        shape = RoundedCornerShape(Radius.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Set up battery whitelist",
                            fontWeight = FontWeight.Bold,
                            fontSize = TypeSize.body
                        )
                    }
                }
            }

            // 3. APPEARANCE — manual theme toggle (System / Light / Dark)
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                LucideIcon(
                                    drawableId = when (currentThemeMode) {
                                        AppThemeMode.LIGHT -> R.drawable.ic_lucide_sun
                                        AppThemeMode.DARK -> R.drawable.ic_lucide_moon
                                        AppThemeMode.SYSTEM -> R.drawable.ic_lucide_settings
                                    },
                                    contentDescription = "Theme",
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Appearance",
                                fontSize = TypeSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose theme for CatchIt",
                                fontSize = TypeSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(Radius.pill)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppThemeMode.values().forEach { mode ->
                            val isSelected = currentThemeMode == mode
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { ThemeManager.setThemeMode(mode) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. APP UPDATES — silent background update with 1-tap install
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                LucideIcon(
                                    drawableId = R.drawable.ic_lucide_rotate_cw,
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CatchIt v${BuildConfig.VERSION_NAME}",
                                fontSize = TypeSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Installed directly · Silent GitHub Updates",
                                fontSize = TypeSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    when (val status = updateStatus) {
                        is UpdateStatus.Checking -> {
                            Text(
                                text = "Checking for new releases on GitHub...",
                                fontSize = TypeSize.body,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is UpdateStatus.Available -> {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "Update available: v${status.update.versionName}",
                                    fontSize = TypeSize.body,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = { AppUpdateManager.downloadAndInstall(context, status.update) },
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                    shape = RoundedCornerShape(Radius.md),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Download & Install Update", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is UpdateStatus.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "Downloading update: ${status.progressPercent}%",
                                    fontSize = TypeSize.caption,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LinearProgressIndicator(
                                    progress = { status.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        is UpdateStatus.ReadyToInstall -> {
                            Button(
                                onClick = { AppUpdateManager.installApk(context, status.file) },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(Radius.md),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Tap to Install v${status.update.versionName}", fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateStatus.UpToDate -> {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "✓ Up to date (v${status.currentVersion})",
                                    fontSize = TypeSize.body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            AppUpdateManager.checkForUpdates(context, silent = false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                    shape = RoundedCornerShape(Radius.md)
                                ) {
                                    Text("Check again", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        AppUpdateManager.checkForUpdates(context, silent = false)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(Radius.md)
                            ) {
                                Text("Check for updates", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // 4. HONEST DATA NOTE — one line, low emphasis, no box.
            Text(
                text = "Bus positions: Prasarana AVL. Routes and stops: data.gov.my " +
                    "(Open Government Licence). v${BuildConfig.VERSION_NAME}",
                fontSize = TypeSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = Spacing.xs)
            )
        }
    }
    }
}


