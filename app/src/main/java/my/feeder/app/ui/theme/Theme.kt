package my.feeder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PulseGreen900,
    onPrimary = Color.White,
    primaryContainer = PulseGreen100,
    onPrimaryContainer = PulseGreen900,
    secondary = PulseGreen700,
    onSecondary = Color.White,
    secondaryContainer = LightContainer,
    onSecondaryContainer = PulseGreen900,
    background = LightCanvasBg,
    onBackground = TextLightPrimary,
    surface = LightCardSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = LightContainer,
    onSurfaceVariant = TextLightSecondary,
    outline = LightHairline
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF0C140F),
    primaryContainer = DarkContainer,
    onPrimaryContainer = Color(0xFFE3EED4),
    secondary = PulseGreen300,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF16251C),
    onSecondaryContainer = Color(0xFFC3D8C8),
    background = DarkCanvasBg,
    onBackground = TextDarkPrimary,
    surface = DarkCardSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = DarkContainer,
    onSurfaceVariant = TextDarkSecondary,
    outline = DarkHairline
)

@Composable
fun FeederMYTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
