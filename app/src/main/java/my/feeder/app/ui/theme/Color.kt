package my.feeder.app.ui.theme

import androidx.compose.ui.graphics.Color

// PULSE Green Brand Tokens (High-Contrast & WCAG Compliant)
val PulseGreen900 = Color(0xFF0F2A1D)  // Deep Forest Ink
val PulseGreen700 = Color(0xFF235432)  // Forest Green
val PulseGreen500 = Color(0xFF457E54)  // Moss Green
val PulseGreen300 = Color(0xFFAEC3B0)  // Sage Accent
val PulseGreen100 = Color(0xFFE3EED4)  // Pale Mint Container
val PulseGreen50  = Color(0xFFF3F7EC)  // Soft Off-White

// Dark Theme Surfaces (Deep, calm forest)
val DarkCanvasBg   = Color(0xFF0C140F)
val DarkCardSurface = Color(0xFF14221A)
val DarkContainer   = Color(0xFF1C2E24)
val DarkHairline    = Color(0xFF284032)

// Light Theme Surfaces
val LightCanvasBg   = Color(0xFFF6F8F3)
val LightCardSurface = Color(0xFFFFFFFF)
val LightContainer   = Color(0xFFEDF4E9)
val LightHairline    = Color(0x240F2A1D)

// Text Colors (Explicit High Contrast)
val TextDarkPrimary   = Color(0xFFFFFFFF) // 17:1 on DarkCanvasBg
val TextDarkSecondary = Color(0xFFC3D8C8) // 8.5:1 on DarkCanvasBg
val TextDarkMuted     = Color(0xFF90A896) // 5.2:1 on DarkCanvasBg

val TextLightPrimary   = Color(0xFF0F2A1D) // 16:1 on LightCanvasBg
val TextLightSecondary = Color(0xFF384D3E) // 7.2:1 on LightCanvasBg
val TextLightMuted     = Color(0xFF5E7363) // 4.8:1 on LightCanvasBg

// Klang Valley Rail & Transit Lines
val LineKgGreen  = Color(0xFF007A3D) // MRT Kajang Line
val LinePyYellow = Color(0xFFFFC20E) // MRT Putrajaya Line
val LineKjRed    = Color(0xFFE42313) // LRT Kelana Jaya Line
val LineAgOrange = Color(0xFFFF7900) // LRT Ampang Line
val LineMrGreen  = Color(0xFF84BD00) // KL Monorail

// Telemetry & Trust State Colors
val TelemetryLiveLight    = Color(0xFF15803D)
val TelemetryLiveDark     = Color(0xFF4ADE80)
val TelemetryDelayedLight = Color(0xFFB45309)
val TelemetryDelayedDark  = Color(0xFFFBBF24)
val TelemetryStaleLight   = Color(0xFFB91C1C)
val TelemetryStaleDark    = Color(0xFFF87171)

// Backward Compatibility Aliases
val PulseBg = LightCanvasBg
val PulseSurface = LightCardSurface
val PulseHairline = LightHairline
val PulseInk = TextLightPrimary
val PulseMuted = TextLightSecondary
val PulseDarkHero = DarkCanvasBg
val PulseDarkHeroSurface = DarkCardSurface
val PulseDarkHeroBorder = DarkHairline

val LightSurfaceAlt = LightCanvasBg
val LightSurface = LightCardSurface
val LightSurfaceSubtle = LightContainer
val LightInk = TextLightPrimary
val LightInkMuted = TextLightSecondary
val LightInkFaint = TextLightMuted

val DarkSurfaceAlt = DarkCanvasBg
val DarkSurface = DarkCardSurface
val DarkSurfaceSubtle = DarkContainer
val DarkInk = TextDarkPrimary
val DarkInkMuted = TextDarkSecondary
val DarkInkFaint = TextDarkMuted

val DarkBackground = DarkSurfaceAlt
val DarkSurfaceVariant = DarkSurfaceSubtle
val TextPrimary = LightInk
val TextSecondary = LightInkMuted
val BorderColor = LightHairline
val FeederEmerald = PulseGreen700
val FeederEmeraldContainer = PulseGreen100
val FeederBlue = Color(0xFF0284C7)
val FeederBlueContainer = Color(0xFFE0F2FE)
val ServiceRapidKlRed = Color(0xFFCC0033)
