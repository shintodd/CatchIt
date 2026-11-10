package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.ui.theme.*
import my.feeder.app.util.OemBatteryHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemWizardScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manufacturer = remember { OemBatteryHelper.getDeviceManufacturer() }
    var isUnrestricted by remember { mutableStateOf(OemBatteryHelper.isIgnoringBatteryOptimizations(context)) }
    var isStep2Done by remember { mutableStateOf(false) }
    var isStep3Done by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Battery Optimization Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onComplete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        LucideIcon(drawableId = R.drawable.ic_lucide_arrow_left, contentDescription = "Back", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "OEM BATTERY PERSISTENCE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Background Delivery Setup",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aggressive phone battery savers can kill bus departure alerts in the background. Complete these steps to guarantee on-time alarms.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Detected Device Banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Radius.md),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DETECTED DEVICE PROFILE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = when (manufacturer) {
                                    OemBatteryHelper.Manufacturer.XIAOMI -> "Xiaomi / Poco / Redmi (HyperOS / MIUI)"
                                    OemBatteryHelper.Manufacturer.SAMSUNG -> "Samsung Galaxy (OneUI)"
                                    OemBatteryHelper.Manufacturer.OPPO_REALME -> "Oppo / Realme / OnePlus (ColorOS / OxygenOS)"
                                    OemBatteryHelper.Manufacturer.VIVO -> "Vivo / iQOO (FuntouchOS / OriginOS)"
                                    OemBatteryHelper.Manufacturer.HUAWEI -> "Huawei / Honor (EMUI / MagicOS)"
                                    else -> "Android Device (Standard Android)"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Step 1: System Battery Optimization Exemption
            item {
                OemStepCard(
                    stepNumber = "1",
                    title = "Set Battery to 'No Restrictions'",
                    description = "Prevents Android Doze from putting CatchIt to sleep when the bus is approaching.",
                    isDone = isUnrestricted,
                    buttonText = if (isUnrestricted) "Whitelisted" else "Grant Exemption",
                    onAction = {
                        OemBatteryHelper.requestIgnoreBatteryOptimizations(context)
                        isUnrestricted = OemBatteryHelper.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "Battery optimization exemption requested", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Step 2: OEM Autostart
            item {
                OemStepCard(
                    stepNumber = "2",
                    title = "Enable Autostart & Background Launch",
                    description = when (manufacturer) {
                        OemBatteryHelper.Manufacturer.XIAOMI -> "In MIUI Security app: Permissions → Autostart → Enable CatchIt."
                        OemBatteryHelper.Manufacturer.SAMSUNG -> "In Device Care: Battery → Background usage limits → Never sleeping apps → Add CatchIt."
                        else -> "In Phone Manager: App management → Auto-launch → Allow CatchIt."
                    },
                    isDone = isStep2Done,
                    buttonText = if (isStep2Done) "Configured" else "Open System Settings",
                    onAction = {
                        OemBatteryHelper.openAutostartSettings(context)
                        isStep2Done = true
                        Toast.makeText(context, "Opening autostart settings", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Step 3: Recent Apps Lock
            item {
                OemStepCard(
                    stepNumber = "3",
                    title = "Lock in Recent Apps (Recommended)",
                    description = "Open your app switcher, long-press the CatchIt card, and tap the Padlock icon so 'Clear All' never kills it.",
                    isDone = isStep3Done,
                    buttonText = if (isStep3Done) "Completed" else "Got It",
                    onAction = {
                        isStep3Done = true
                        Toast.makeText(context, "App lock instructions noted", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Finish CTA
            item {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Complete & Return to Commute",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OemStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isDone: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isDone) Color(0xFF15803D) else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(Radius.sm),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDone) {
                                LucideIcon(
                                    drawableId = R.drawable.ic_lucide_check,
                                    contentDescription = "Done",
                                    size = 16.dp,
                                    tint = Color.White
                                )
                            } else {
                                Text(
                                    text = stepNumber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(Radius.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
