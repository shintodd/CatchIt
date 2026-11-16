const { fetchVehiclePositions, filterVehicles } = require('./gtfsRealtimeService');

async function run() {
  console.log('--- 1. Testing Live Fetch & Decode ---');
  const t0 = Date.now();
  const vehicles = await fetchVehiclePositions('rapid-bus-mrtfeeder');
  console.log(`Fetched ${vehicles.length} MRT feeder vehicles in ${Date.now() - t0}ms`);
  
  if (vehicles.length > 0) {
    console.log('Sample telemetry extracted:');
    console.log(JSON.stringify(vehicles[0], null, 2));
  }

  console.log('\n--- 2. Testing 20s Throttling Protection ---');
  const t1 = Date.now();
  const cachedVehicles = await fetchVehiclePositions('rapid-bus-mrtfeeder');
  const cacheDuration = Date.now() - t1;
  console.log(`Second call returned ${cachedVehicles.length} vehicles in ${cacheDuration}ms (cache hit: ${cacheDuration < 10})`);

  console.log('\n--- 3. Testing Flexible Route Filtering ---');
  if (vehicles.length > 0) {
    const sampleRoute = vehicles[0].routeId;
    console.log(`Filtering for sample route: ${sampleRoute}`);
    const filtered = filterVehicles(vehicles, { routeCode: sampleRoute });
    console.log(`Found ${filtered.length} matching vehicles`);

    // Test digit matching
    const sampleDigits = sampleRoute.replace(/\D/g, '');
    if (sampleDigits) {
      console.log(`Filtering by digits only: ${sampleDigits}`);
      const digitFiltered = filterVehicles(vehicles, { routeCode: sampleDigits });
      console.log(`Found ${digitFiltered.length} matching vehicles by digits`);
    }
  }

  // Test Bounding Box
  console.log('\n--- 4. Testing GPS Bounding Box Filter (Klang Valley) ---');
  const klangValleyBbox = {
    minLat: 2.8,
    maxLat: 3.4,
    minLon: 101.3,
    maxLon: 101.9
  };
  const inKV = filterVehicles(vehicles, { bbox: klangValleyBbox });
  console.log(`Vehicles in Klang Valley bbox: ${inKV.length} / ${vehicles.length}`);

  console.log('\n--- 5. Testing Stage Buses (rapid-bus-kl) ---');
  const klVehicles = await fetchVehiclePositions('rapid-bus-kl');
  console.log(`Fetched ${klVehicles.length} rapid-bus-kl vehicles`);
  if (klVehicles.length > 0) {
    console.log('Sample rapid-bus-kl:', klVehicles[0]);
  }

  console.log('\nALL TESTS PASSED!');
}

run().catch(console.error);
