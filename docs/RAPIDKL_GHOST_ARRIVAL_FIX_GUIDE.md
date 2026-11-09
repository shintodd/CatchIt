# RapidKL Feeder Ghost Arrival Bug: Root Causes & Hermes Engineering Fix Guide

> **Audience**: Hermes Agent / Android & Backend Engineers  
> **Target Systems**: `feedermy-android` (`CatchIt`), `t507-bus-discord-bot` (`TodakBus`)  
> **Telemetry Source**: RapidKL AVL Socket.IO (`rapidbus-socketio-avl.prasarana.com.my`) & GTFS-RT (`data.gov.my`)  
> **Status**: Verified in Production & Fixed in Android Client

---

## 1. Executive Summary & Problem Statement

Commuters frequently experience the **"Ghost Arrival"** anomaly:
- The app announces: **"Bus arriving in 1 min!"** or **"Arriving Now (0 mins)"**.
- The commuter runs down to the stop.
- **No bus arrives.** The road is completely empty. The actual bus does not arrive for another 25 to 40 minutes.

This bug destroys user trust in public transit apps. In the Klang Valley feeder bus ecosystem (MRT feeder routes like T507, T155, T815), this is **not** random GPS jitter. It is caused by **5 systemic algorithmic flaws** when handling loop route topologies and raw Malaysian transit telemetry feeds.

---

## 2. The 5 Root Causes

### Root Cause 1: Terminal / Depot Layover False Positives
- **Mechanism**: Feeder buses operate on continuous circular routes starting and ending at an MRT station or transit depot. Drivers take a 15–30 minute scheduled layover/break between runs with the engine idling and AVL transponder pinging.
- **Why Naive Math Fails**: Terminal layover bays are physically close (<150m) to Stop 1 (departure) and Stop 31 (arrival). A naive distance check calculates straight-line distance $d \approx 40\text{ m}$, deriving an ETA of $< 1\text{ min}$.
- **Symptom**: App screams "Arriving now" while the driver is eating lunch or resting.

### Root Cause 2: Opposite-Lane Snapping on Dual-Carriageway Boulevards
- **Mechanism**: Boulevards in Malaysian transit corridors (e.g. Persiaran Multimedia in Cyberjaya) feature outbound traffic (heading south towards Kanvas) and inbound traffic (returning north towards MRT) separated by a 15–20 meter tree-lined median.
- **Why Naive Math Fails**: When a bus is returning inbound to the MRT on the opposite side of the road, a naive nearest-stop distance search snaps the bus to the outbound bus stop directly across the median because it is only 18 meters away.
- **Symptom**: App alerts a waiting passenger on the outbound side that the bus is "arriving in 2 minutes", when in reality the bus is driving away in the opposite direction and won't return for 35 minutes on its next loop.

### Root Cause 3: Loop Sequence Wrapping ("Already Passed" Blindspot)
- **Mechanism**: Feeder routes are circular loops without distinct outbound/inbound trip IDs in the Malaysian GTFS-RT feed (both directions share the same loop trip ID).
- **Why Naive Math Fails**: A commuter is waiting at Stop 15. The bus passes Stop 15 and is now at Stop 16. In simple circular distance calculations, the bus is 100 meters away from Stop 15. Naive proximity triggers an arrival alarm for a bus that is actively moving further away.
- **Symptom**: Arrival alarm triggers immediately after the commuter missed the bus.

### Root Cause 4: Upstream Stale GPS Telemetry Replay & Timezone Clamping
- **Mechanism**: When a bus finishes service and the driver cuts the master power switch, upstream aggregators (`data.gov.my` or Prasarana Kiosk AVL) do not emit a "vehicle decommissioned" event. Instead, the backend keeps replaying the last known GPS coordinate in every poll.
- **The Malaysian Timezone Bug**: Prasarana AVL returns wall-clock strings (e.g. `"2026-11-08 00:38:22"`) without UTC timezone offset headers. If parsed in the client device's local timezone (e.g. UTC or US Pacific on an emulator), the timestamp calculation results in a future date or clamps negative age to zero ($0\text{ s}$ old).
- **Symptom**: Parked buses at depots appear "live" at 2:00 AM with 0 km/h speed.

### Root Cause 5: Euclidean (Haversine) vs Physical Tarmac Polyline
- **Mechanism**: Feeder routes meander through residential enclaves, office parks, and roundabouts.
- **Why Naive Math Fails**: Straight-line Euclidean distance as the crow flies through buildings might measure 400 meters. The actual driving distance along tarmac, around roundabouts, and through traffic lights is 2.8 km.
- **Symptom**: 2-minute ETA displayed; actual arrival takes 12–15 minutes.

---

## 3. Reference Implementation: TodakBus Backend (`src/gtfs.mjs`)

The legacy Node.js setup solved this in `t507-bus-discord-bot/src/gtfs.mjs`:

### 3.1 Bearing Angle Penalty for Opposite Lane Snapping
```javascript
// t507-bus-discord-bot/src/gtfs.mjs (Lines 228-248)
// Disambiguate parallel roads / opposite dual-carriageway lanes using bearing
if (bearing !== null && bearing !== undefined && Number.isFinite(bearing)) {
  let bestScore = Infinity;
  let bestAlignIdx = bestIdx;
  const threshold = Math.min(50, minDist + 25);
  
  for (let i = 0; i < routeShape.length; i++) {
    const d = haversineDistanceMeters(lat, lon, routeShape[i][0], routeShape[i][1]);
    if (d <= threshold) {
      const segBearing = shapeBearings[i];
      const diff = angleDiff(bearing, segBearing);
      // If bus heading is >90° off the road segment direction, apply 200m penalty
      const score = d + (diff > 90 ? 200 : 0);
      if (score < bestScore) {
        bestScore = score;
        bestAlignIdx = i;
      }
    }
  }
  bestIdx = bestAlignIdx;
  minDist = haversineDistanceMeters(lat, lon, routeShape[bestIdx][0], routeShape[bestIdx][1]);
}
```

### 3.2 Terminal Layover Classifier
```javascript
// t507-bus-discord-bot/src/gtfs.mjs (Lines 574-585)
let statusType = "MOVING";
let statusLabel = `🟢 Moving (${speed} km/h)`;

if (speed === 0) {
  // Terminal sequence check: Seq 1 (MRT), Seq 10 (Terminal), Seq 31 (MRT End)
  if (closestStop.seq === 1 || closestStop.seq === 10 || closestStop.seq === 31) {
    statusType = "IDLING";
    statusLabel = "🟡 Terminal Break / Standby (0 km/h)";
  } else {
    statusType = "STOPPED";
    statusLabel = "⏸️ At stop / Signal (0 km/h)";
  }
} else if (speed < 15) {
  statusType = "SLOW";
  statusLabel = `🟠 Slow / Heavy Traffic (${speed} km/h)`;
}
```

### 3.3 Loop Sequence Wrap Detection
```javascript
// t507-bus-discord-bot/src/gtfs.mjs (Lines 308-347)
if (currentSeq > destSeq) {
  // Bus has completed pickup at target stop and is heading back to terminal
  const totalDist = totalRouteLengthMeters - busRouteDist + destDistMeters;
  return {
    routeDistanceMeters: Math.round(totalDist),
    stopsRemaining: 0,
    estMinutes: Math.max(15, Math.round(estSeconds / 60)),
    formattedEta: "Next loop (~35m)",
    passedDestination: true
  };
}
```

---

## 4. Modern Android Implementation: CatchIt / FeederMY (`GtfsRepository.kt`)

In the native Android app (`feedermy-android`), these rules are formalized in Kotlin in `my.feeder.app.data.GtfsRepository`.

### 4.1 Strict Timezone Parsing & Stale Replay Cutoff
```kotlin
// GtfsRepository.kt (Lines 133-140, 540-557)
private const val GPS_STALE_SECONDS = 120

private fun gpsAgeSeconds(gpsTimestamp: String, nowEpochSeconds: Long): Int {
    if (gpsTimestamp.isBlank()) return 0
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        // MUST enforce Asia/Kuala_Lumpur; otherwise non-MYT devices clamp negative age to 0
        fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        val parsed = fmt.parse(gpsTimestamp) ?: return 0
        val age = nowEpochSeconds - (parsed.time / 1000L)
        age.coerceIn(0L, 86_400L).toInt()
    } catch (e: Exception) {
        0
    }
}

// In refreshLiveBuses():
if (age > GPS_STALE_SECONDS) return@forEach // Drop ghost buses from stale replay
```

### 4.2 Terminal Radius Suppression
```kotlin
// GtfsRepository.kt (Lines 131-132, 498-520)
private const val TERMINAL_RADIUS_M = 150.0

val atTerminal = speedKmh < 3 && routeStops.isNotEmpty() && run {
    val first = routeStops.minByOrNull { it.seq }
    val last = routeStops.maxByOrNull { it.seq }
    val nearFirst = first != null && haversineMeters(lat, lon, first.lat, first.lon) < TERMINAL_RADIUS_M
    val nearLast = last != null && haversineMeters(lat, lon, last.lat, last.lon) < TERMINAL_RADIUS_M
    nearFirst || nearLast
}

val eta = if (atTerminal) {
    -1 // Suppress numerical ETA; UI displays "At Terminal"
} else {
    nextStop?.let { estimateEtaMinutes(lat, lon, it.lat, it.lon, speedKmh) } ?: -1
}
```

### 4.3 Approach Cone & Monotonic Sequence Filtering
```kotlin
// GtfsRepository.kt (Lines 143-146, 689-713)
private const val APPROACH_RADIUS_M = 400.0
private const val APPROACH_CONE_DEG = 100.0

for (bus in buses) {
    // 1. Loop Sequence Guard: Drop bus if it already passed user's anchor stop
    if (bus.nextStopSeq > 0 && anchor.seq > 0 && bus.nextStopSeq > anchor.seq) {
        continue
    }

    // 2. Heading Cone: Drop bus if moving fast (>5 km/h) but pointed away from stop
    val metres = haversineMeters(bus.lat, bus.lon, anchor.lat, anchor.lon)
    val isMoving = bus.speedKmh >= 5
    if (metres > APPROACH_RADIUS_M && isMoving) {
        val toStop = bearingDegrees(bus.lat, bus.lon, anchor.lat, anchor.lon)
        if (angleDelta(bus.heading, toStop) > APPROACH_CONE_DEG) {
            continue // Bus is travelling on opposite boulevard lane
        }
    }

    val eta = estimateEtaMinutes(bus.lat, bus.lon, anchor.lat, anchor.lon, bus.speedKmh)
    if (eta < bestEta) {
        bestEta = eta
        bestBus = bus
    }
}
```

### 4.4 Road-Winding Factor & Minimum Speed Floor
```kotlin
// GtfsRepository.kt (Lines 767-780)
private fun estimateEtaMinutes(
    busLat: Double,
    busLon: Double,
    stopLat: Double,
    stopLon: Double,
    speedKmh: Int
): Int {
    val metres = haversineMeters(busLat, busLon, stopLat, stopLon)
    // 1.35x road-winding multiplier converts crow-flies to actual road tarmac distance
    val roadMetres = metres * 1.35
    // Use 20 km/h floor when bus is stationary at red lights/dwell to prevent division by 0
    val effectiveKmh = if (speedKmh < 5) 20.0 else speedKmh.toDouble()
    val minutes = (roadMetres / 1000.0) / effectiveKmh * 60.0
    return minutes.toInt().coerceIn(0, 180)
}
```

---

## 5. Verification Checklist for Engineers

Before shipping any transit build to production, verify these 5 test cases:

1. **Terminal Break Test**: Place simulated vehicle at MRT Cyberjaya terminal at $0\text{ km/h}$. Verify UI displays `"At Terminal"` or `"Terminal Standby"`, NOT `"Arriving in 1 min"`.
2. **Opposite Lane Test**: Place simulated vehicle on northbound lane of Persiaran Multimedia with heading $350^\circ$. Verify Stop 6 (southbound, heading $170^\circ$) does NOT trigger an approach alert.
3. **Passed Stop Test**: Set bus `nextStopSeq = 17` and target stop `seq = 15`. Verify vehicle is excluded from approaching buses or flagged as `"Next Loop"`.
4. **Offline Purge Test**: Send telemetry ping with timestamp 180 seconds in the past. Verify vehicle is evicted from live state within 1 tick.
5. **Non-MYT Timezone Test**: Set Android emulator timezone to UTC-8 (PST). Poll live telemetry. Verify `gpsAgeSeconds` returns correct real elapsed time, not negative numbers clamped to 0.
