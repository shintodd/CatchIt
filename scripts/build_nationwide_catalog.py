#!/usr/bin/env python3
"""
Nationwide Transit Catalog Builder for FeederMY / CatchIt
Compiles static GTFS feeds from data.gov.my across all Malaysian public bus systems into an
optimized, unified nationwide stops & routes catalog (nationwide_transit_catalog.json).

Supported Operators & Agencies:
- RapidKL MRT Feeder (Klang Valley)
- RapidKL Stage Bus (Klang Valley)
- Rapid Penang (Penang Island & Seberang Perai)
- myBAS Johor Bahru (Johor)
- myBAS Ipoh (Perak)
- myBAS Melaka (Melaka)
- myBAS Kuching (Sarawak)
- myBAS Seremban & Nilai (Negeri Sembilan)
- myBAS Alor Setar (Kedah)
- myBAS Kangar (Perlis)
- myBAS Kuala Terengganu (Terengganu)
- myBAS Kota Bharu (Kelantan)
- Rapid Kuantan (Pahang)
"""

import os
import sys
import urllib.request
import zipfile
import csv
import io
import json
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")
CACHE_DIR = os.path.join(SCRIPT_DIR, ".gtfs_cache")
os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

FEEDS = [
    {
        "file": "rapid-bus-mrtfeeder.zip",
        "url": "https://api.data.gov.my/gtfs-static/prasarana?category=rapid-bus-mrtfeeder",
        "agency": "RapidKL",
        "sub_agency": "MRT Feeder",
        "region": "Klang Valley",
        "mode": "mrt"
    },
    {
        "file": "rapid-bus-kl.zip",
        "url": "https://api.data.gov.my/gtfs-static/prasarana?category=rapid-bus-kl",
        "agency": "RapidKL",
        "sub_agency": "Stage Bus",
        "region": "Klang Valley",
        "mode": "kl_bus"
    },
    {
        "file": "rapid-bus-penang.zip",
        "url": "https://api.data.gov.my/gtfs-static/prasarana?category=rapid-bus-penang",
        "agency": "Rapid Penang",
        "sub_agency": "Rapid Penang",
        "region": "Penang",
        "mode": "penang"
    },
    {
        "file": "mybas-johor.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-johor",
        "agency": "myBAS JB",
        "sub_agency": "Causeway Link",
        "region": "Johor Bahru",
        "mode": "mybas"
    },
    {
        "file": "mybas-ipoh.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-ipoh",
        "agency": "myBAS Ipoh",
        "sub_agency": "The Combine Bus",
        "region": "Ipoh",
        "mode": "ipoh"
    },
    {
        "file": "mybas-melaka.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-melaka",
        "agency": "myBAS Melaka",
        "sub_agency": "Panorama Melaka / Causeway Link",
        "region": "Melaka",
        "mode": "mybas"
    },
    {
        "file": "mybas-kuching.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-kuching",
        "agency": "myBAS Kuching",
        "sub_agency": "City Public Link",
        "region": "Kuching",
        "mode": "mybas"
    },
    {
        "file": "mybas-seremban-a.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-seremban-a",
        "agency": "myBAS Seremban",
        "sub_agency": "Cityliner / KR Travel",
        "region": "Seremban & Nilai",
        "mode": "seremban"
    },
    {
        "file": "mybas-seremban-b.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-seremban-b",
        "agency": "myBAS Seremban",
        "sub_agency": "Cityliner",
        "region": "Seremban & Bahau",
        "mode": "seremban"
    },
    {
        "file": "mybas-alor-setar.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-alor-setar",
        "agency": "myBAS Alor Setar",
        "sub_agency": "myBAS Kedah",
        "region": "Alor Setar",
        "mode": "mybas"
    },
    {
        "file": "mybas-kangar.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-kangar",
        "agency": "myBAS Kangar",
        "sub_agency": "myBAS Perlis",
        "region": "Kangar",
        "mode": "mybas"
    },
    {
        "file": "mybas-kuala-terengganu.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-kuala-terengganu",
        "agency": "myBAS Terengganu",
        "sub_agency": "myBAS Terengganu",
        "region": "Kuala Terengganu",
        "mode": "mybas"
    },
    {
        "file": "mybas-kota-bharu.zip",
        "url": "https://api.data.gov.my/gtfs-static/mybas-kota-bharu",
        "agency": "myBAS Kota Bharu",
        "sub_agency": "Mutiara Rentas Sentosa",
        "region": "Kota Bharu",
        "mode": "mybas"
    }
]

KUANTAN_COORDS = {
    "Hentian Bandar": (3.8078, 103.3260),
    "Hospital Tengku Ampuan Afzan (HTAA)": (3.8031, 103.3242),
    "Jayagading": (3.7582, 103.1974),
    "Gambang Resort": (3.7051, 103.0531),
    "Tanah Putih": (3.7915, 103.3075),
    "Indera Sempurna": (3.7324, 103.2650),
    "Batu 3": (3.8062, 103.2985),
    "Permatang Badak": (3.7981, 103.2731),
    "Tanjung Lumpur": (3.7985, 103.3392),
    "Teluk Cempedak Beach": (3.8126, 103.3698),
    "Bukit Setongkol": (3.8192, 103.3082),
    "Taman Impian": (3.8260, 103.2950),
    "Semambu": (3.8421, 103.3320),
    "Bukit Sagu": (3.9650, 103.2450),
    "Stadium Darul Makmur": (3.8175, 103.3240),
    "Indera Mahkota 1": (3.8285, 103.3021),
    "Kuantan Parade": (3.8115, 103.3280),
    "Terminal Sentral Kuantan (TSK)": (3.8290, 103.2842),
    "Padang Lalang": (3.7650, 103.3350),
    "Pekan Sentral": (3.4839, 103.3934),
    "Universiti Malaysia Pahang (UMP)": (3.5430, 103.4280),
    "Kuala Pahang": (3.5310, 103.4680),
    "Panching": (3.8750, 103.1420),
    "Pekan Sungai Lembing": (3.9140, 103.0340),
    "Beserah": (3.8780, 103.3650),
    "Batu Hitam": (3.8960, 103.3680),
    "Balok Makmor": (3.9550, 103.3750),
    "Air Putih": (3.8340, 103.3420),
    "POLISAS": (3.8560, 103.3370),
    "Semambu Industrial Area": (3.8480, 103.3350),
    "PSDC": (3.8520, 103.3380),
    "Sungai Ular": (4.0450, 103.3980),
    "Cherating Cultural Village": (4.1278, 103.3888)
}

KUANTAN_ROUTES = [
    {"code": "100", "name": "Hentian Bandar - Gambang Resort", "stops": ["Hentian Bandar", "Hospital Tengku Ampuan Afzan (HTAA)", "Jayagading", "Gambang Resort"]},
    {"code": "101", "name": "Hentian Bandar - Indera Sempurna", "stops": ["Hentian Bandar", "Tanah Putih", "Indera Sempurna"]},
    {"code": "102", "name": "Hentian Bandar - Permatang Badak", "stops": ["Hentian Bandar", "Batu 3", "Permatang Badak"]},
    {"code": "200", "name": "Hentian Bandar - Teluk Cempedak", "stops": ["Hentian Bandar", "Tanjung Lumpur", "Teluk Cempedak Beach"]},
    {"code": "300", "name": "Hentian Bandar - Taman Impian", "stops": ["Hentian Bandar", "Bukit Setongkol", "Taman Impian"]},
    {"code": "301", "name": "Hentian Bandar - Bukit Sagu", "stops": ["Hentian Bandar", "Semambu", "Bukit Sagu"]},
    {"code": "302", "name": "Hentian Bandar - Indera Mahkota 1", "stops": ["Hentian Bandar", "Stadium Darul Makmur", "Indera Mahkota 1"]},
    {"code": "303", "name": "Hentian Bandar - Terminal Sentral Kuantan (TSK)", "stops": ["Hentian Bandar", "Kuantan Parade", "Terminal Sentral Kuantan (TSK)"]},
    {"code": "400", "name": "Hentian Bandar - Pekan", "stops": ["Hentian Bandar", "Padang Lalang", "Pekan Sentral"]},
    {"code": "401", "name": "Hentian Bandar - Kuala Pahang", "stops": ["Hentian Bandar", "Universiti Malaysia Pahang (UMP)", "Kuala Pahang"]},
    {"code": "500", "name": "Hentian Bandar - Sungai Lembing", "stops": ["Hentian Bandar", "Panching", "Pekan Sungai Lembing"]},
    {"code": "600", "name": "Hentian Bandar - Balok Makmor via Beserah", "stops": ["Hentian Bandar", "Beserah", "Batu Hitam", "Balok Makmor"]},
    {"code": "601", "name": "Hentian Bandar - POLISAS Semambu", "stops": ["Hentian Bandar", "Air Putih", "POLISAS"]},
    {"code": "603", "name": "Hentian Bandar - PSDC", "stops": ["Hentian Bandar", "Semambu Industrial Area", "PSDC"]},
    {"code": "604", "name": "Balok Makmor - Cherating", "stops": ["Balok Makmor", "Sungai Ular", "Cherating Cultural Village"]}
]

def clean_name(s):
    if not s:
        return ""
    s = re.sub(r'\s+', ' ', s.strip())
    return s.strip('"\'')

def download_feed_if_needed(feed):
    cache_path = os.path.join(CACHE_DIR, feed["file"])
    if os.path.exists(cache_path) and os.path.getsize(cache_path) > 1000:
        return cache_path
    
    print(f"Downloading {feed['file']} from {feed['url']}...")
    req = urllib.request.Request(feed['url'], headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        with open(cache_path, "wb") as f:
            f.write(resp.read())
    return cache_path

def build_catalog():
    all_routes = []
    all_stops = {}
    agency_stats = {}

    for feed in FEEDS:
        zpath = download_feed_if_needed(feed)
        agency_tag = feed["agency"]
        if agency_tag not in agency_stats:
            agency_stats[agency_tag] = {"routes": 0, "stops": set(), "region": feed["region"]}
        
        with zipfile.ZipFile(zpath) as z:
            # 1. Stops
            stops_info = {}
            if "stops.txt" in z.namelist():
                with z.open("stops.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        sid = row.get("stop_id", "").strip()
                        sname = clean_name(row.get("stop_name", ""))
                        scode = clean_name(row.get("stop_code", ""))
                        try:
                            slat = round(float(row.get("stop_lat", 0)), 6)
                            slon = round(float(row.get("stop_lon", 0)), 6)
                        except ValueError:
                            continue
                        if slat == 0 and slon == 0:
                            continue
                        stops_info[sid] = {
                            "id": sid,
                            "name": sname,
                            "code": scode,
                            "lat": slat,
                            "lon": slon
                        }

            # 2. Shapes
            shapes_data = {}
            if "shapes.txt" in z.namelist():
                with z.open("shapes.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        sh_id = row.get("shape_id", "").strip()
                        try:
                            seq = int(row.get("shape_pt_sequence", 0))
                            lat = round(float(row.get("shape_pt_lat", 0)), 6)
                            lon = round(float(row.get("shape_pt_lon", 0)), 6)
                            shapes_data.setdefault(sh_id, []).append((seq, lat, lon))
                        except ValueError:
                            pass
                for sh_id in shapes_data:
                    shapes_data[sh_id].sort(key=lambda x: x[0])
                    shapes_data[sh_id] = [[round(x[1], 5), round(x[2], 5)] for x in shapes_data[sh_id]]

            # 3. Trips
            trip_routes = {}
            trip_headsigns = {}
            trip_shapes = {}
            if "trips.txt" in z.namelist():
                with z.open("trips.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        tid = row.get("trip_id", "").strip()
                        rid = row.get("route_id", "").strip()
                        hs = clean_name(row.get("trip_headsign", ""))
                        sh_id = row.get("shape_id", "").strip()
                        trip_routes[tid] = rid
                        trip_shapes[tid] = sh_id
                        if rid and hs and rid not in trip_headsigns:
                            trip_headsigns[rid] = hs

            # 4. Stop times
            trip_stops = {}
            stop_routes = {}
            if "stop_times.txt" in z.namelist():
                with z.open("stop_times.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        tid = row.get("trip_id", "").strip()
                        sid = row.get("stop_id", "").strip()
                        try:
                            seq = int(row.get("stop_sequence", 0))
                            trip_stops.setdefault(tid, []).append((seq, sid))
                        except ValueError:
                            pass
                for tid in trip_stops:
                    trip_stops[tid].sort(key=lambda x: x[0])

            # 5. Routes
            route_codes = {}
            route_names = {}
            if "routes.txt" in z.namelist():
                with z.open("routes.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        rid = row.get("route_id", "").strip()
                        short = clean_name(row.get("route_short_name", ""))
                        longn = clean_name(row.get("route_long_name", ""))
                        mode = feed["mode"]
                        if mode == "mrt":
                            code = longn if longn else short
                        elif mode in ("seremban", "ipoh"):
                            code = rid if rid else short
                        else:
                            code = short if short else (longn if longn else rid)
                        
                        name = longn if longn else short
                        if not name or name == code:
                            name = trip_headsigns.get(rid, code)
                        
                        route_codes[rid] = code
                        route_names[rid] = name

            # Assemble routes with stops and shape
            for rid, code in route_codes.items():
                # Find best trip: highest stop count
                candidate_trips = [tid for tid, r in trip_routes.items() if r == rid]
                best_trip = None
                max_stops = -1
                for tid in candidate_trips:
                    st_len = len(trip_stops.get(tid, []))
                    if st_len > max_stops:
                        max_stops = st_len
                        best_trip = tid

                r_stops = []
                if best_trip:
                    for idx, (_, sid) in enumerate(trip_stops.get(best_trip, [])):
                        s = stops_info.get(sid)
                        if s:
                            r_stops.append({
                                "id": s["id"],
                                "code": s["code"],
                                "name": s["name"],
                                "lat": s["lat"],
                                "lon": s["lon"],
                                "seq": idx + 1
                            })
                            # Track stop -> routes
                            if sid not in stop_routes:
                                stop_routes[sid] = set()
                            stop_routes[sid].add(code)

                # Get shape
                r_shape = []
                sh_id = trip_shapes.get(best_trip, "") if best_trip else ""
                if sh_id and sh_id in shapes_data:
                    r_shape = shapes_data[sh_id]
                elif r_stops:
                    # Fallback to connecting stop coordinates
                    r_shape = [[s["lat"], s["lon"]] for s in r_stops]

                headsign = trip_headsigns.get(rid, route_names.get(rid, code))
                r_obj = {
                    "id": rid,
                    "code": code,
                    "name": route_names.get(rid, code),
                    "headsign": headsign,
                    "agency": agency_tag,
                    "stopsCount": len(r_stops),
                    "stops": r_stops,
                    "shape": r_shape
                }
                all_routes.append(r_obj)
                agency_stats[agency_tag]["routes"] += 1

            # Populate global stops dictionary
            for sid, s in stops_info.items():
                serving_routes = sorted(list(stop_routes.get(sid, [])))
                stop_key = f"{agency_tag}_{sid}"
                if stop_key in all_stops:
                    existing = set(all_stops[stop_key]["routes"])
                    existing.update(serving_routes)
                    all_stops[stop_key]["routes"] = sorted(list(existing))
                else:
                    stop_obj = {
                        "id": sid,
                        "name": s["name"],
                        "lat": s["lat"],
                        "lon": s["lon"],
                        "agency": agency_tag,
                        "routes": serving_routes
                    }
                    if s["code"]:
                        stop_obj["code"] = s["code"]
                    all_stops[stop_key] = stop_obj
                    agency_stats[agency_tag]["stops"].add(sid)

    # Append Rapid Kuantan
    agency_stats["Rapid Kuantan"] = {"routes": len(KUANTAN_ROUTES), "stops": set(), "region": "Kuantan, Pahang"}
    for kr in KUANTAN_ROUTES:
        ktn_stops = []
        ktn_shape = []
        for idx, s_name in enumerate(kr["stops"]):
            lat, lon = KUANTAN_COORDS.get(s_name, (3.8168, 103.3317))
            sid = f"KTN_{abs(hash(s_name)) % 100000:05d}"
            stop_entry = {
                "id": sid,
                "code": f"KT{idx+1:02d}",
                "name": s_name,
                "lat": lat,
                "lon": lon,
                "seq": idx + 1
            }
            ktn_stops.append(stop_entry)
            ktn_shape.append([lat, lon])

            stop_key = f"Rapid Kuantan_{sid}"
            if stop_key not in all_stops:
                all_stops[stop_key] = {
                    "id": sid,
                    "name": s_name,
                    "lat": lat,
                    "lon": lon,
                    "agency": "Rapid Kuantan",
                    "routes": [kr["code"]]
                }
                agency_stats["Rapid Kuantan"]["stops"].add(sid)
            else:
                if kr["code"] not in all_stops[stop_key]["routes"]:
                    all_stops[stop_key]["routes"].append(kr["code"])

        r_obj = {
            "id": f"KUANTAN_{kr['code']}",
            "code": kr["code"],
            "name": kr["name"],
            "headsign": kr["name"],
            "agency": "Rapid Kuantan",
            "stopsCount": len(ktn_stops),
            "stops": ktn_stops,
            "shape": ktn_shape
        }
        all_routes.append(r_obj)

    agencies_summary = []
    for tag, data in sorted(agency_stats.items()):
        agencies_summary.append({
            "tag": tag,
            "region": data["region"],
            "route_count": data["routes"],
            "stop_count": len(data["stops"])
        })

    catalog = {
        "version": 1,
        "total_routes": len(all_routes),
        "total_stops": len(all_stops),
        "agencies": agencies_summary,
        "routes": all_routes,
        "stops": list(all_stops.values())
    }

    out_path = os.path.join(ASSETS_DIR, "nationwide_transit_catalog.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, separators=(',', ':'))

    size_bytes = os.path.getsize(out_path)
    print("=" * 60)
    print("NATIONWIDE TRANSIT CATALOG GENERATION COMPLETE")
    print("=" * 60)
    print(f"Target Output: {out_path}")
    print(f"Total Agencies: {len(agencies_summary)}")
    print(f"Total Routes:   {len(all_routes)}")
    print(f"Total Stops:    {len(all_stops)}")
    print(f"File Size:      {size_bytes:,} bytes ({size_bytes / (1024*1024):.2f} MB)")
    print("=" * 60)

if __name__ == "__main__":
    build_catalog()
