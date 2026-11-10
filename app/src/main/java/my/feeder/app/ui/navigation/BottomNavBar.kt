package my.feeder.app.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.feeder.app.R
import my.feeder.app.data.ThemeManager
import my.feeder.app.ui.components.LucideNavIcon
import my.feeder.app.ui.theme.*

/**
 * Three destinations only. The app answers one question — when does my bus
 * come and when do I walk out — so the navigation is Bus, Alarms, Settings.
 */
enum class FeederNavTab(val route: String, val label: String, val drawableRes: Int) {
    HOME("home", "Bus", R.drawable.ic_lucide_home),
    ALERTS("alerts", "Alarms", R.drawable.ic_lucide_bell),
    SETTINGS("settings", "Settings", R.drawable.ic_lucide_settings)
}

@Composable
fun FeederBottomNavBar(
    currentRoute: String,
    onTabSelected: (FeederNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = ThemeManager.isDarkActive()

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        FeederNavTab.values().forEach { tab ->
            val isSelected = currentRoute == tab.route
            val activeColor = if (isDark) Color(0xFF4ADE80) else PulseGreen900
            val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.testTag("nav-${tab.route}"),
                icon = {
                    LucideNavIcon(
                        tab = tab,
                        isSelected = isSelected,
                        tint = if (isSelected) activeColor else inactiveColor
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = TypeSize.caption,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = activeColor,
                    selectedTextColor = activeColor,
                    indicatorColor = if (isDark) Color(0xFF1E382A) else PulseGreen100,
                    unselectedIconColor = inactiveColor,
                    unselectedTextColor = inactiveColor
                )
            )
        }
    }
}
