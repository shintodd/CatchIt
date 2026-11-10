package my.feeder.app.ui.screens

import my.feeder.app.R
import my.feeder.app.ui.components.LucideIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.ui.theme.*

data class FeederStation(
    val code: String,
    val name: String,
    val lineName: String,
    val lineColor: Color,
    val distanceMeters: Int,
    val bayCount: Int,
    val connectingRoutes: List<String>,
    val isPinned: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationHubSheetContent(
    selectedStationCode: String = "KG12",
    onSelectStation: (FeederStation) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Nearby, 1 = Pinned
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val allStations = remember {
        listOf(
            FeederStation(
                code = "KG12",
                name = "MRT Phileo Damansara",
                lineName = "Kajang Line",
                lineColor = LineKgGreen,
                distanceMeters = 120,
                bayCount = 3,
                connectingRoutes = listOf("T507", "T815", "T816"),
                isPinned = true
            ),
            FeederStation(
                code = "KG10",
                name = "MRT TTDI",
                lineName = "Kajang Line",
                lineColor = LineKgGreen,
                distanceMeters = 850,
                bayCount = 2,
                connectingRoutes = listOf("T813", "T814"),
                isPinned = true
            ),
            FeederStation(
                code = "PY12",
                name = "MRT Sri Delima",
                lineName = "Putrajaya Line",
                lineColor = LinePyYellow,
                distanceMeters = 1400,
                bayCount = 2,
                connectingRoutes = listOf("T818", "T819"),
                isPinned = false
            ),
            FeederStation(
                code = "PY41",
                name = "MRT Putrajaya Sentral",
                lineName = "Putrajaya Line",
                lineColor = LinePyYellow,
                distanceMeters = 24000,
                bayCount = 8,
                connectingRoutes = listOf("T155", "T508", "T509", "T510"),
                isPinned = false
            ),
            FeederStation(
                code = "KJ14",
                name = "LRT Pasar Seni",
                lineName = "Kelana Jaya Line",
                lineColor = LineKjRed,
                distanceMeters = 7200,
                bayCount = 6,
                connectingRoutes = listOf("750", "751", "770", "BET3"),
                isPinned = false
            )
        )
    }

    val filteredStations = remember(searchQuery, selectedTab) {
        allStations.filter { st ->
            val matchesTab = if (selectedTab == 0) true else st.isPinned
            val matchesSearch = st.name.contains(searchQuery, ignoreCase = true) ||
                    st.code.contains(searchQuery, ignoreCase = true) ||
                    st.connectingRoutes.any { it.contains(searchQuery, ignoreCase = true) }
            matchesTab && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Station & Feeder Hubs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select active MRT / LRT interchange",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_x,
                    contentDescription = "Close",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 257 rail stations or bus routes...", fontSize = 13.sp) },
            leadingIcon = {
                LucideIcon(
                    drawableId = R.drawable.ic_lucide_search,
                    contentDescription = "Search",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("station-search"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = PulseGreen700,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Button Row (Nearby vs Pinned)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PulseGreen100.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 0) PulseGreen900 else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nearby Stations",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 0) Color.White else PulseMuted
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 1) PulseGreen900 else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pinned Hubs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 1) Color.White else PulseMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Station List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredStations) { station ->
                val isSelected = station.code == selectedStationCode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) PulseGreen100 else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) PulseGreen700 else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStation(station) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Line badge
                        Surface(
                            color = station.lineColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = station.code,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Station name and distance
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (station.distanceMeters < 1000) "${station.distanceMeters}m away" else "${station.distanceMeters / 1000}km away",
                                    fontSize = 13.sp,
                                    color = PulseGreen700,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " · ${station.bayCount} Bays",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Connecting routes row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                station.connectingRoutes.forEach { route ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = route,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = PulseGreen900,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    LucideIcon(
                                        drawableId = R.drawable.ic_lucide_check,
                                        contentDescription = "Selected",
                                        size = 14.dp,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
