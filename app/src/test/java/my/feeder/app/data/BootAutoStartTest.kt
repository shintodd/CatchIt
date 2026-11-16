package my.feeder.app.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootAutoStartTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DepartureAlarmStore.init(context)
        AlertScheduleManager.init(context)
    }

    @Test
    fun `receiver handles ACTION_BOOT_COMPLETED without crashing`() {
        val receiver = DepartureAlarmReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Arm an alarm first
        val alert = DepartureAlert(
            routeCode = "T507",
            stopId = "10021132",
            stopName = "MRT CYBERJAYA",
            stage1StopId = "10021132",
            stage1StopName = "MRT CYBERJAYA",
            stage2StopId = "10021132",
            stage2StopName = "MRT CYBERJAYA"
        )
        DepartureAlarmStore.arm(alert)
        assertTrue(DepartureAlarmStore.load().isNotEmpty())

        receiver.onReceive(context, intent)
    }

    @Test
    fun `receiver handles ACTION_MY_PACKAGE_REPLACED after app update`() {
        val receiver = DepartureAlarmReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        receiver.onReceive(context, intent)
    }

    @Test
    fun `DepartureAlarmService starts and stops cleanly`() {
        DepartureAlarmService.start(context)
        DepartureAlarmService.stop(context)
    }
}
