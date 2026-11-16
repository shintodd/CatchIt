import GtfsRealtimeBindings from 'gtfs-realtime-bindings';

export type PrasaranaCategory = 'rapid-bus-mrtfeeder' | 'rapid-bus-kl' | 'rapid-bus-penang';

export interface VehicleTelemetry {
  id: string;
  plate: string;
  latitude: number;
  longitude: number;
  speedKmh: number;
  bearing: number;
  routeId: string;
  timestamp: number;
}

export interface BoundingBox {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
}

export interface RouteFilterOptions {
  routeCode?: string;
  bbox?: BoundingBox;
}

interface CacheEntry {
  data: VehicleTelemetry[];
  timestamp: number;
}

// In-memory cache for throttling protection (20s TTL)
const cache = new Map<PrasaranaCategory, CacheEntry>();
export const CACHE_TTL_MS = 20 * 1000;

// In-flight fetch promises to prevent parallel duplicate fetches
const pendingRequests = new Map<PrasaranaCategory, Promise<VehicleTelemetry[]>>();

/**
 * Fetch and decode live GTFS-RT feed from data.gov.my.
 * 
 * @param category 'rapid-bus-mrtfeeder' | 'rapid-bus-kl' | 'rapid-bus-penang'
 * @param forceRefresh Bypass 20s cache if true
 */
export async function fetchVehiclePositions(
  category: PrasaranaCategory = 'rapid-bus-mrtfeeder',
  forceRefresh: boolean = false
): Promise<VehicleTelemetry[]> {
  const now = Date.now();
  const cached = cache.get(category);

  // 3. Throttling Protection: Return cached data if within 20s TTL
  if (!forceRefresh && cached && (now - cached.timestamp < CACHE_TTL_MS)) {
    return cached.data;
  }

  // De-duplicate concurrent in-flight requests
  if (pendingRequests.has(category)) {
    return pendingRequests.get(category)!;
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
          console.warn(`[GTFS-RT] 429 Throttled on ${category}. Returning cached data if available.`);
          if (cached) return cached.data;
        }
        throw new Error(`GTFS-RT fetch failed: ${response.status} ${response.statusText}`);
      }

      // 1. Fetch & Decode
      const buffer = await response.arrayBuffer();
      const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(new Uint8Array(buffer));

      // 2. Extract Vehicle Telemetry
      const vehicles: VehicleTelemetry[] = [];
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
          id: (v.vehicle?.id || entity.id || '').toString(),
          plate: (v.vehicle?.licensePlate || 'Unknown').toString(),
          latitude: lat,
          longitude: lon,
          speedKmh,
          bearing: pos.bearing || 0,
          routeId: (v.trip?.routeId || '').toString(),
          timestamp: v.timestamp ? Number(v.timestamp) : Math.floor(Date.now() / 1000)
        });
      }

      // Store in-memory cache
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
 * 4. Flexible Route Filter:
 * Filters by exact route match, digit inclusion (e.g. T507 / T5070),
 * or GPS bounding box.
 */
export function filterVehicles(
  vehicles: VehicleTelemetry[],
  options: RouteFilterOptions = {}
): VehicleTelemetry[] {
  const { routeCode, bbox } = options;
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
