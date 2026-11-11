package my.feeder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import my.feeder.app.R
import my.feeder.app.ui.theme.*

private data class CommutePreset(
    val label: String,
    val hour24: Int,
    val minute: Int
)

private val COMMUTE_PRESETS = listOf(
    CommutePreset("7:15 AM", 7, 15),
    CommutePreset("8:00 AM", 8, 0),
    CommutePreset("5:10 PM", 17, 10),
    CommutePreset("6:30 PM", 18, 30),
    CommutePreset("7:10 PM", 19, 10),
    CommutePreset("9:30 PM", 21, 30)
)

/**
 * Clean Shopify / iOS style tumbler wheel time picker tailored to CatchIt.
 * Works seamlessly in both Dark Mode and Light Mode.
 */
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

@Composable
fun CatchItTimePickerContent(
    title: String,
    initialHour: Int = 17,
    initialMinute: Int = 10,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    // Dynamic contrast-optimized palette for dark & light mode
    val dialogBg = if (isDark) Color(0xFF161817) else Color(0xFFFFFFFF)
    val dialogBorder = if (isDark) Color(0xFF2A332C) else Color(0xFFE5E7EB)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)
    val highlightBarColor = if (isDark) Color(0x18FFFFFF) else Color(0x0A000000)
    val chipBg = if (isDark) Color(0xFF222724) else Color(0xFFF1F5F2)
    val chipSelectedBg = if (isDark) Color(0xFF1B3D2B) else Color(0xFFDCFCE7)
    val emeraldAccent = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val confirmBtnBg = if (isDark) Color(0xFF22C55E) else Color(0xFF16A34A)
    val confirmBtnText = if (isDark) Color(0xFF0B140E) else Color(0xFFFFFFFF)

    // 12-hour decomposition
    val initial12 = if (initialHour % 12 == 0) 12 else initialHour % 12
    val initialIsPm = initialHour >= 12

    var selectedHourIndex by remember { mutableStateOf((initial12 - 1).coerceIn(0, 11)) }
    var selectedMinuteIndex by remember { mutableStateOf(initialMinute.coerceIn(0, 59)) }
    var selectedPeriodIndex by remember { mutableStateOf(if (initialIsPm) 1 else 0) } // 0 = AM, 1 = PM

    val hoursList = remember { (1..12).map { String.format("%02d", it) } }
    val minutesList = remember { (0..59).map { String.format("%02d", it) } }
    val periodsList = remember { listOf("AM", "PM") }

    // Live 24-hour calculation
    val current12Hour = selectedHourIndex + 1
    val isPm = selectedPeriodIndex == 1
    val current24Hour = if (isPm) {
        if (current12Hour == 12) 12 else current12Hour + 12
    } else {
        if (current12Hour == 12) 0 else current12Hour
    }
    val current24Formatted = String.format("%02d:%02d", current24Hour, selectedMinuteIndex)
    val current12Formatted = String.format("%02d:%02d %s", current12Hour, selectedMinuteIndex, if (isPm) "PM" else "AM")

    Surface(
        modifier = modifier
            .fillMaxWidth(0.90f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        color = dialogBg,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, dialogBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title and large live preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = current12Formatted,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            style = TabularNums
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "($current24Formatted MYT)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = emeraldAccent,
                            style = TabularNums,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean Presets Pill Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                COMMUTE_PRESETS.forEach { preset ->
                    val p12 = if (preset.hour24 % 12 == 0) 12 else preset.hour24 % 12
                    val pIsPm = preset.hour24 >= 12
                    val isSelected = current24Hour == preset.hour24 && selectedMinuteIndex == preset.minute

                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (isSelected) chipSelectedBg else chipBg,
                        border = if (isSelected) BorderStroke(1.dp, emeraldAccent) else null,
                        modifier = Modifier.clickable {
                            selectedHourIndex = p12 - 1
                            selectedMinuteIndex = preset.minute
                            selectedPeriodIndex = if (pIsPm) 1 else 0
                        }
                    ) {
                        Text(
                            text = preset.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) emeraldAccent else textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tumbler / Wheel Picker (Shopify / iOS Style)
            val itemHeight = 44.dp
            val visibleCount = 5

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight * visibleCount),
                contentAlignment = Alignment.Center
            ) {
                // Soft translucent center selection bar - NO harsh border
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = highlightBarColor
                ) {}

                // 3 Rolling Columns side-by-side: [ Hour ] [ Minute ] [ AM/PM ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Column
                    WheelColumn(
                        items = hoursList,
                        selectedIndex = selectedHourIndex,
                        onIndexSelected = { selectedHourIndex = it },
                        itemHeight = itemHeight,
                        visibleCount = visibleCount,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    // Minutes Column
                    WheelColumn(
                        items = minutesList,
                        selectedIndex = selectedMinuteIndex,
                        onIndexSelected = { selectedMinuteIndex = it },
                        itemHeight = itemHeight,
                        visibleCount = visibleCount,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    // AM / PM Column
                    WheelColumn(
                        items = periodsList,
                        selectedIndex = selectedPeriodIndex,
                        onIndexSelected = { selectedPeriodIndex = it },
                        itemHeight = itemHeight,
                        visibleCount = visibleCount,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Top Fade Gradient into Dialog Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight * 1.5f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(dialogBg, Color.Transparent)
                            )
                        )
                )

                // Bottom Fade Gradient into Dialog Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight * 1.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, dialogBg)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(Radius.pill),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textSecondary
                    ),
                    border = BorderStroke(1.dp, dialogBorder),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = {
                        onConfirm(current24Hour, selectedMinuteIndex)
                    },
                    shape = RoundedCornerShape(Radius.pill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = confirmBtnBg,
                        contentColor = confirmBtnText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Text(
                        text = "Set time",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * A single wheel column that snaps items cleanly to the center position.
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    itemHeight: Dp,
    visibleCount: Int,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()

    // Smooth animate when index changes programmatically (e.g. preset clicked)
    LaunchedEffect(selectedIndex) {
        val current = listState.firstVisibleItemIndex
        if (current != selectedIndex && !listState.isScrollInProgress) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // Detect center item when user finishes scroll gesture
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val snappedIndex = (listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0)
                .coerceIn(0, items.size - 1)
            onIndexSelected(snappedIndex)
        }
    }

    val currentCenterIndex by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0)
                .coerceIn(0, items.size - 1)
        }
    }

    val halfVisible = visibleCount / 2

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * halfVisible),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { index ->
                val distance = Math.abs(index - currentCenterIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.55f
                    2 -> 0.25f
                    else -> 0.08f
                }
                val scale = when (distance) {
                    0 -> 1.15f
                    1 -> 0.92f
                    else -> 0.82f
                }
                val isSelected = distance == 0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            scope.launch { listState.animateScrollToItem(index) }
                            onIndexSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) textPrimary else textSecondary,
                        style = TabularNums,
                        modifier = Modifier.graphicsLayer {
                            this.alpha = alpha
                            this.scaleX = scale
                            this.scaleY = scale
                        }
                    )
                }
            }
        }
    }
}
