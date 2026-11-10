package my.feeder.app.data

import android.content.Context
import android.util.Log

/**
 * Decides when to announce a departure alarm, from the bus's real position.
 *
 * This is the part the product is actually about. The rule is the one
 * commuters actually use:
 *
 *  - **Stage 1** fires when the bus is a few minutes from *stage 1's stop* —
 *    an early stop on the route. That is the moment to start moving.
 *  - **Stage 2** fires when the bus is a few minutes from *stage 2's stop* —
 *    a later stop. That is the moment to be at your waiting spot.
 *
 * Both stages are measured against **one and the same approaching bus**, so
 * the two warnings always describe the same vehicle.
 *
 * Both stages fire at most once per armed alarm, and the fired state is
 * persisted, so a restart cannot re-announce a stage the user already saw.
 */
object DepartureAlarmEngine {

    /** What the engine decided to announce. */
    data class Announcement(
        val stage: Int,              // 1 = leave now, 2 = go to your waiting spot
        val routeCode: String,
        val stopId: String,
        val stopName: String,
        val plate: String,
        val etaMinutes: Int,
        val speedKmh: Int,
        /** True when both stages share one stop, which changes the wording. */
        val sameStop: Boolean = false
    )

    /**
     * Checks every armed alarm against the live feed.
     *
     * @return the announcements to post, in the order they should be posted.
     */
    fun evaluate(): List<Announcement> {
        if (!AlertScheduleManager.isWithinActiveWindow()) return emptyList()

        val alarms = DepartureAlarmStore.load()
        if (alarms.isEmpty()) return emptyList()

        val announcements = mutableListOf<Announcement>()

        for (armed in alarms) {
            val alert = armed.alert

            // Anchor on the later of the two stops: that is the one the
            // commuter is waiting for, and the bus we care about is the one
            // heading for it.
            val anchorStopId = GtfsRepository.laterStopOnRoute(
                routeCode = alert.routeCode,
                stopIdA = alert.stage1StopId,
                stopIdB = alert.stage2StopId
            )

            // Both stages are measured against that same bus.
            val approach = try {
                GtfsRepository.approachingBusWithEtas(
                    routeCode = alert.routeCode,
                    anchorStopId = anchorStopId,
                    stopIds = listOf(alert.stage1StopId, alert.stage2StopId)
                )
            } catch (e: Exception) {
                Log.w("DepartureAlarm", "lookup failed: ${e.message}")
                null
            } ?: continue

            val eta1 = approach.etaFor(alert.stage1StopId) ?: continue
            val eta2 = approach.etaFor(alert.stage2StopId) ?: continue

            // The rider never sets a lead time: a stage fires when the bus is
            // arriving at that stage's stop.
            val arriving1 = approach.isArrivingAt(alert.stage1StopId)
            val arriving2 = approach.isArrivingAt(alert.stage2StopId)

            // Stage 1 first: it is the earlier stop, so it always wins.
            if (!armed.stage1Fired && arriving1) {
                announcements.add(
                    Announcement(
                        stage = 1,
                        routeCode = alert.routeCode,
                        stopId = alert.stage1StopId,
                        stopName = alert.stage1StopName,
                        plate = approach.plate,
                        etaMinutes = eta1,
                        speedKmh = approach.speedKmh,
                        sameStop = alert.sameStop
                    )
                )
                DepartureAlarmStore.markFired(alert.routeCode, alert.stopId, 1)
                continue
            }

            if (!armed.stage2Fired && arriving2) {
                announcements.add(
                    Announcement(
                        stage = 2,
                        routeCode = alert.routeCode,
                        stopId = alert.stage2StopId,
                        stopName = alert.stage2StopName,
                        plate = approach.plate,
                        etaMinutes = eta2,
                        speedKmh = approach.speedKmh,
                        sameStop = alert.sameStop
                    )
                )
                DepartureAlarmStore.markFired(alert.routeCode, alert.stopId, 2)
            }
        }
        return announcements
    }

    /**
     * Posts the announcements as notifications.
     *
     * @return how many were posted.
     */
    fun announce(context: Context, announcements: List<Announcement>): Int {
        var posted = 0
        announcements.forEach { a ->
            if (DepartureAlarmNotifier.post(context, a)) posted++
        }
        return posted
    }
}
