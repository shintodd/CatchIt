package my.feeder.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeManagerTest {

    @Test
    fun defaultModeIsSystem() {
        assertEquals(AppThemeMode.SYSTEM, ThemeManager.themeMode.value)
    }

    @Test
    fun setThemeModeUpdatesState() {
        ThemeManager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, ThemeManager.themeMode.value)

        ThemeManager.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, ThemeManager.themeMode.value)

        ThemeManager.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, ThemeManager.themeMode.value)
    }

    @Test
    fun appThemeModeEnumValues() {
        assertEquals("System", AppThemeMode.SYSTEM.title)
        assertEquals("Light", AppThemeMode.LIGHT.title)
        assertEquals("Dark", AppThemeMode.DARK.title)
    }
}
