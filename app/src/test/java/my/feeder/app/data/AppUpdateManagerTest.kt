package my.feeder.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun `isNewerVersion returns true for higher minor or patch version`() {
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("1.10.0", "1.9.0"))
    }

    @Test
    fun `isNewerVersion correctly strips v prefix and handles whitespace`() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("V1.2.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion(" v1.2.0 ", " 1.1.0 "))
    }

    @Test
    fun `isNewerVersion returns false for same or older versions`() {
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.9", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("0.9.0", "1.1.0"))
    }

    @Test
    fun `isNewerVersion handles different segment lengths`() {
        assertTrue(AppUpdateManager.isNewerVersion("1.1.0.1", "1.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", "1.1.0.1"))
    }
}
