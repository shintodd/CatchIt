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
import gzip
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

# Rapid Kuantan catalog
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
    # Re-use cached zip if recent and non-empty
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
            trip_routes = {}
            trip_headsigns = {}
            if "trips.txt" in z.namelist():
                with z.open("trips.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        tid = row.get("trip_id")
                        rid = row.get("route_id")
                        hs = clean_name(row.get("trip_headsign", ""))
                        trip_routes[tid] = rid
                        if rid and hs and rid not in trip_headsigns:
                            trip_headsigns[rid] = hs
            
            route_stops = {}
            stop_routes = {}
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
            
            if "stop_times.txt" in z.namelist():
                with z.open("stop_times.txt") as f:
                    r = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
                    for row in r:
                        tid = row.get("trip_id")
                        sid = row.get("stop_id")
                        rid = trip_routes.get(tid)
                        if rid and sid:
                            if rid not in route_stops:
                                route_stops[rid] = set()
                            route_stops[rid].add(sid)
                            
                            rcode = route_codes.get(rid)
                            if rcode:
                                if sid not in stop_routes:
                                    stop_routes[sid] = set()
                                stop_routes[sid].add(rcode)
            
            for rid, code in route_codes.items():
                sc = len(route_stops.get(rid, []))
                r_obj = {
                    "id": rid,
                    "code": code,
                    "name": route_names.get(rid, code),
                    "agency": agency_tag,
                    "stopsCount": sc
                }
                all_routes.append(r_obj)
                agency_stats[agency_tag]["routes"] += 1
                
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
                        
                        serving_routes = sorted(list(stop_routes.get(sid, [])))
                        stop_key = f"{agency_tag}_{sid}"
                        if stop_key in all_stops:
                            existing_routes = set(all_stops[stop_key]["routes"])
                            existing_routes.update(serving_routes)
                            all_stops[stop_key]["routes"] = sorted(list(existing_routes))
                        else:
                            stop_obj = {
                                "id": sid,
                                "name": sname,
                                "lat": slat,
                                "lon": slon,
                                "agency": agency_tag,
                                "routes": serving_routes
                            }
                            if scode:
                                stop_obj["code"] = scode
                            all_stops[stop_key] = stop_obj
                            agency_stats[agency_tag]["stops"].add(sid)

    # Append Rapid Kuantan
    agency_stats["Rapid Kuantan"] = {"routes": len(KUANTAN_ROUTES), "stops": set(), "region": "Kuantan, Pahang"}
    for kr in KUANTAN_ROUTES:
        r_obj = {
            "id": f"KUANTAN_{kr['code']}",
            "code": kr["code"],
            "name": kr["name"],
            "agency": "Rapid Kuantan",
            "stopsCount": len(kr["stops"])
        }
        all_routes.append(r_obj)
        for s_name in kr["stops"]:
            sid = f"KTN_{abs(hash(s_name)) % 100000:05d}"
            stop_key = f"Rapid Kuantan_{sid}"
            if stop_key not in all_stops:
                all_stops[stop_key] = {
                    "id": sid,
                    "name": s_name,
                    "lat": 3.8168,
                    "lon": 103.3317,
                    "agency": "Rapid Kuantan",
                    "routes": [kr["code"]]
                }
                agency_stats["Rapid Kuantan"]["stops"].add(sid)
            else:
                if kr["code"] not in all_stops[stop_key]["routes"]:
                    all_stops[stop_key]["routes"].append(kr["code"])

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
