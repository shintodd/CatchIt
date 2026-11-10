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
import my.feeder.app.data.DepartureAlarmStore
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
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Set up battery whitelist",
                            fontWeight = FontWeight.Bold,
                            fontSize = TypeSize.body
                        )
                    }
                }
            }

            // 3. HONEST DATA NOTE — one line, low emphasis, no box.
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


