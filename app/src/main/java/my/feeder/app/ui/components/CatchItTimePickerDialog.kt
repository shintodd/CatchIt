package my.feeder.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.feeder.app.R
import my.feeder.app.ui.theme.*

/** Preset commuter hours for fast 1-tap selection */
private data class CommutePreset(
    val label: String,
    val hour: Int,
    val minute: Int
)

private val COMMUTE_PRESETS = listOf(
    CommutePreset("07:15 Morning Peak", 7, 15),
    CommutePreset("08:00 Work Shift", 8, 0),
    CommutePreset("17:10 Reference Commute", 17, 10),
    CommutePreset("18:30 Evening Peak", 18, 30),
    CommutePreset("19:10 CatchIt Default", 19, 10),
    CommutePreset("21:30 Late Feeder", 21, 30)
)

/**
 * High-craft time picker dialog matching CatchIt's deep OLED theme and transit aesthetics.
 *
 * Replaces the archaic Android View TimePickerDialog with a native Material 3 Compose
 * picker styled in CatchIt emerald green, featuring quick commuter presets, 24-hour
 * clock face, digital keyboard mode toggle, and responsive dark canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchItTimePickerDialog(
    title: String,
    initialHour: Int = 17,
    initialMinute: Int = 10,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CatchItTimePickerContent(
            title = title,
            initialHour = initialHour,
            initialMinute = initialMinute,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchItTimePickerContent(
    title: String,
    initialHour: Int = 17,
    initialMinute: Int = 10,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isKeyboardMode by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Surface(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                LucideIcon(
                                    drawableId = R.drawable.ic_lucide_clock,
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "24-hour commuter time",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Keyboard vs Dial Toggle
                    IconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        LucideIcon(
                            drawableId = if (isKeyboardMode) R.drawable.ic_lucide_clock else R.drawable.ic_lucide_keyboard,
                            contentDescription = if (isKeyboardMode) "Switch to dial" else "Switch to keypad",
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Quick Commute Presets Carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    COMMUTE_PRESETS.forEach { preset ->
                        val isSelected = timePickerState.hour == preset.hour && timePickerState.minute == preset.minute
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier.clickable {
                                // Re-create or adjust time
                                onConfirm(preset.hour, preset.minute)
                            }
                        ) {
                            Text(
                                text = preset.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Material 3 Picker Styled with CatchIt Brand Colors
                val pickerColors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    clockDialSelectedContentColor = Color.White,
                    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    containerColor = Color.Transparent,
                    periodSelectorBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorUnselectedContainerColor = Color.Transparent,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isKeyboardMode) {
                        TimeInput(
                            state = timePickerState,
                            colors = pickerColors
                        )
                    } else {
                        TimePicker(
                            state = timePickerState,
                            colors = pickerColors
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radius.md),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 46.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Medium,
                            fontSize = TypeSize.body
                        )
                    }

                    Button(
                        onClick = {
                            onConfirm(timePickerState.hour, timePickerState.minute)
                        },
                        shape = RoundedCornerShape(Radius.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 46.dp)
                    ) {
                        Text(
                            text = "Set time",
                            fontWeight = FontWeight.Bold,
                            fontSize = TypeSize.body
                        )
                    }
                }
            }
        }
}
