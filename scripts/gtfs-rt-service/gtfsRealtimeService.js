const GtfsRealtimeBindings = require('gtfs-realtime-bindings');

/**
 * In-memory cache for throttling protection.
 * Key: category string, Value: { data: VehicleTelemetry[], timestamp: number }
 */
const cache = new Map();
const CACHE_TTL_MS = 20 * 1000; // 20-second throttle cache

// In-flight fetch promises to prevent parallel duplicate fetches
const pendingRequests = new Map();

/**
 * Fetch and decode live GTFS-RT feed from data.gov.my.
 * 
 * @param {'rapid-bus-mrtfeeder' | 'rapid-bus-kl' | 'rapid-bus-penang'} [category='rapid-bus-mrtfeeder']
 * @param {boolean} [forceRefresh=false]
 * @returns {Promise<Array<{id: string, plate: string, latitude: number, longitude: number, speedKmh: number, bearing: number, routeId: string, timestamp: number}>>}
 */
async function fetchVehiclePositions(category = 'rapid-bus-mrtfeeder', forceRefresh = false) {
  const now = Date.now();
  const cached = cache.get(category);

  // Return cached result if still within 20s TTL
  if (!forceRefresh && cached && (now - cached.timestamp < CACHE_TTL_MS)) {
    return cached.data;
  }

  // De-duplicate concurrent in-flight requests
  if (pendingRequests.has(category)) {
    return pendingRequests.get(category);
  }

  const fetchPromise = (async () => {
    try {
      const url = `https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana?category=${category}`;
      const response = await fetch(url, {
        headers: {
          'Accept': 'application/x-protobuf, application/octet-stream, */*',
          'User-Agent': 'CatchIt/1.2.8 (BusTracker)'
        }
      });

      if (!response.ok) {
        if (response.status === 429) {
          console.warn(`[GTFS-RT] 429 Rate limit hit on ${category}. Returning cached data if available.`);
          if (cached) return cached.data;
        }
        throw new Error(`GTFS-RT fetch failed: ${response.status} ${response.statusText}`);
      }

      // 1. Fetch & Decode
      const buffer = await response.arrayBuffer();
      const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(new Uint8Array(buffer));

      // 2. Extract Vehicle Telemetry
      const vehicles = [];
      for (const entity of feed.entity) {
        const v = entity.vehicle;
        if (!v || !v.position) continue;

        const pos = v.position;
        const lat = pos.latitude || 0;
        const lon = pos.longitude || 0;

        // Skip records with empty/invalid coordinates
        if (!lat || !lon || (lat === 0 && lon === 0)) continue;

        // Speed conversion from m/s to km/h: Math.round(speed * 3.6)
        const speedKmh = Math.max(0, Math.round((pos.speed || 0) * 3.6));

        vehicles.push({
          id: v.vehicle?.id || entity.id || '',
          plate: v.vehicle?.licensePlate || 'Unknown',
          latitude: lat,
          longitude: lon,
          speedKmh: speedKmh,
          bearing: pos.bearing || 0,
          routeId: v.trip?.routeId || '',
          timestamp: v.timestamp ? Number(v.timestamp) : Math.floor(Date.now() / 1000)
        });
      }

      // 3. Throttling Protection: Store in-memory with timestamp
      cache.set(category, { data: vehicles, timestamp: Date.now() });
      return vehicles;
    } finally {
      pendingRequests.delete(category);
    }
  })();

  pendingRequests.set(category, fetchPromise);
  return fetchPromise;
}

/**
 * Fetch live vehicle positions from Prasarana's official AVL REST API.
 * 
 * Endpoint: https://myrapidbus.prasarana.com.my/avl/api/avl/region/{region}
 * Regions: 'rkl' (Klang Valley) | 'rpg' (Penang)
 * 
 * @param {'rkl' | 'rpg'} [region='rkl']
 * @returns {Promise<Array<{id: string, plate: string, latitude: number, longitude: number, speedKmh: number, bearing: number, routeId: string, timestamp: number}>>}
 */
async function fetchPrasaranaAvl(region = 'rkl') {
  const url = `https://myrapidbus.prasarana.com.my/avl/api/avl/region/${region}`;
  const response = await fetch(url, {
    headers: {
      'Accept': 'application/json, */*',
      'Origin': 'https://myrapidbus.prasarana.com.my',
      'Referer': 'https://myrapidbus.prasarana.com.my/kiosk',
      'User-Agent': 'CatchIt/1.2.9 (Official Prasarana AVL REST)'
    }
  });

  if (!response.ok) {
    throw new Error(`Prasarana AVL REST fetch failed: ${response.status} ${response.statusText}`);
  }

  const data = await response.json();
  const vehicles = [];

  for (const v of data.vehicles || []) {
    const lat = Number(v.latitude);
    const lon = Number(v.longitude);
    if (!lat || !lon || isNaN(lat) || isNaN(lon)) continue;

    const rawRoute = (v.route || '').trim().toUpperCase();
    let routeId = rawRoute;
    if (routeId.length === 4 && /^\d+$/.test(routeId)) {
      routeId = routeId.slice(0, 3);
    } else if (routeId.length === 5 && routeId.startsWith('T') && /^\d+$/.test(routeId.slice(1))) {
      routeId = routeId.slice(0, 4);
    }

    const gpsTime = v.dt_gps
      ? Math.floor(new Date(v.dt_gps.replace(' ', 'T') + '+08:00').getTime() / 1000)
      : Math.floor(Date.now() / 1000);

    vehicles.push({
      id: (v.bus_no || '').toString(),
      plate: (v.bus_no || 'Unknown').toString(),
      latitude: lat,
      longitude: lon,
      speedKmh: Math.max(0, Math.round(Number(v.speed) || 0)),
      bearing: Math.round(Number(v.angle) || 0),
      routeId,
      timestamp: isNaN(gpsTime) ? Math.floor(Date.now() / 1000) : gpsTime
    });
  }

  return vehicles;
}

/**
 * 4. Flexible Route Filter:
 * Filters by route code exact match, route digit inclusion (e.g. T507 / T5070),
 * or GPS bounding box.
 * 
 * @param {Array<Object>} vehicles 
 * @param {Object} [filterOptions={}]
 * @param {string} [filterOptions.routeCode]
 * @param {{minLat: number, maxLat: number, minLon: number, maxLon: number}} [filterOptions.bbox]
 * @returns {Array<Object>}
 */
function filterVehicles(vehicles, filterOptions = {}) {
  const { routeCode, bbox } = filterOptions;
  let results = vehicles;

  if (routeCode) {
    const cleanQuery = routeCode.trim().toUpperCase();
    const queryDigits = cleanQuery.replace(/\D/g, '');

    results = results.filter(v => {
      const vRoute = (v.routeId || '').trim().toUpperCase();
      if (!vRoute) return false;

      // 1. Exact match
      if (vRoute === cleanQuery) return true;

      // 2. Route digits inclusion (e.g. 507 matches T507, T5070, 507)
      if (queryDigits.length >= 2) {
        const routeDigits = vRoute.replace(/\D/g, '');
        if (routeDigits.includes(queryDigits) || queryDigits.includes(routeDigits)) {
          return true;
        }
      }

      // 3. Substring match
      if (vRoute.includes(cleanQuery) || cleanQuery.includes(vRoute)) {
        return true;
      }

      return false;
    });
  }

  if (bbox) {
    results = results.filter(v =>
      v.latitude >= bbox.minLat &&
      v.latitude <= bbox.maxLat &&
      v.longitude >= bbox.minLon &&
      v.longitude <= bbox.maxLon
    );
  }

  return results;
}

module.exports = {
  fetchVehiclePositions,
  fetchPrasaranaAvl,
  filterVehicles,
  CACHE_TTL_MS
};
