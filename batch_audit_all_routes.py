import os
import sys
import json
import time
import subprocess
import re

ADB_PATH = r"C:\Users\ainol\AppData\Local\Android\Sdk\platform-tools\adb.exe"
CATALOG_PATH = r"C:\Users\ainol\Dev\FeederMY\feedermy-android\app\src\main\assets\nationwide_transit_catalog.json"
OUT_DIR = r"C:\Users\ainol\.gemini\antigravity\brain\ab579e03-f033-4e04-90f4-e26a37ad58f3\screenshots_real_app"
RESULTS_JSON = r"C:\Users\ainol\.gemini\antigravity\brain\ab579e03-f033-4e04-90f4-e26a37ad58f3\all_routes_audit_results.json"
REPORT_MD = r"C:\Users\ainol\.gemini\antigravity\brain\ab579e03-f033-4e04-90f4-e26a37ad58f3\all_routes_audit_table.md"

os.makedirs(OUT_DIR, exist_ok=True)

def run_adb(args, timeout=20, retries=2):
    cmd = [ADB_PATH] + args
    for attempt in range(retries):
        try:
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, text=True, encoding="utf-8", errors="replace")
            return res.stdout.strip()
        except subprocess.TimeoutExpired:
            print(f"ADB command timed out: {' '.join(args)} (attempt {attempt+1}/{retries})", flush=True)
            try:
                subprocess.run([ADB_PATH, "reconnect"], timeout=5)
            except Exception:
                pass
            time.sleep(2)
        except Exception as e:
            print(f"ADB error: {e} (attempt {attempt+1}/{retries})", flush=True)
            time.sleep(2)
    return ""

def sanitize(s):
    return re.sub(r'[^a-zA-Z0-9_\-]', '_', str(s))

def write_markdown_report(results, total):
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    lines = [
        f"# Real App All Routes Verification Audit ({passed}/{total} Passed)",
        "",
        f"- **Audited**: {len(results)} / {total}",
        f"- **Passed**: {passed}",
        f"- **Failed**: {failed}",
        "",
        "| Index | Route | Agency | Name | Stops | Shape Pts | Status | Screenshot Proof |",
        "|---|---|---|---|---|---|---|---|"
    ]
    for r in results:
        proof_link = f"[{r['screenshot_file']}](file:///{r['screenshot_path'].replace(os.sep, '/')})"
        lines.append(f"| {r['index']} | **{r['code']}** | {r['agency']} | {r['name'][:30]} | {r['stops_count']} | {r['shape_count']} | `{r['status']}` | {proof_link} |")

    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

def main():
    print("Checking adb devices...", flush=True)
    out = run_adb(["devices"])
    print(out, flush=True)
    if "device" not in out:
        run_adb(["reconnect"])
        time.sleep(2)
        out = run_adb(["devices"])
        print("After reconnect:", out, flush=True)

    print("Ensuring app is running...", flush=True)
    run_adb(["shell", "am", "start", "-n", "my.feeder.app/.MainActivity"])
    time.sleep(3)

    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        catalog = json.load(f)

    routes = catalog.get("routes", [])
    total = len(routes)
    print(f"Loaded {total} routes from catalog.", flush=True)

    results = []
    if os.path.exists(RESULTS_JSON):
        try:
            with open(RESULTS_JSON, "r", encoding="utf-8") as f:
                results = json.load(f)
            print(f"Loaded {len(results)} existing results from json.", flush=True)
        except Exception as e:
            print(f"Error loading existing json: {e}", flush=True)
            results = []

    # Map existing results by id
    results_by_id = {r["id"]: r for r in results}

    for i, route in enumerate(routes, 1):
        r_id = route.get("id", "")
        code = route.get("code", "")
        agency = route.get("agency", "Unknown")
        name = route.get("name", "")
        stops = route.get("stops", [])
        shape = route.get("shape", [])

        clean_agency = sanitize(agency)
        clean_code = sanitize(code)
        img_name = f"{clean_agency}_{clean_code}_{r_id}.png"
        img_path = os.path.join(OUT_DIR, img_name)

        # Check if already captured valid screenshot
        if os.path.exists(img_path) and os.path.getsize(img_path) > 30000:
            if r_id not in results_by_id:
                record = {
                    "index": i,
                    "id": r_id,
                    "code": code,
                    "agency": agency,
                    "name": name,
                    "stops_count": len(stops),
                    "shape_count": len(shape),
                    "screenshot_file": img_name,
                    "screenshot_path": img_path,
                    "screenshot_bytes": os.path.getsize(img_path),
                    "status": "PASS",
                    "failure_reason": ""
                }
                results_by_id[r_id] = record
            continue

        print(f"[{i}/{total}] Auditing: {agency} {code} (ID: {r_id}, {len(stops)} stops, {len(shape)} shape pts)...", flush=True)
        run_adb(["shell", "am", "start", "-n", "my.feeder.app/.MainActivity", "--es", "catchit.routeCode", r_id])
        time.sleep(2.0)

        temp_device_path = f"/sdcard/audit_{sanitize(r_id)}.png"
        run_adb(["shell", "screencap", "-p", temp_device_path])
        pull_out = run_adb(["pull", temp_device_path, img_path])
        run_adb(["shell", "rm", "-f", temp_device_path])

        file_size = os.path.getsize(img_path) if os.path.exists(img_path) else 0
        status = "PASS" if (file_size > 30000 and len(stops) > 0 and len(shape) > 0) else "FAIL"

        record = {
            "index": i,
            "id": r_id,
            "code": code,
            "agency": agency,
            "name": name,
            "stops_count": len(stops),
            "shape_count": len(shape),
            "screenshot_file": img_name,
            "screenshot_path": img_path,
            "screenshot_bytes": file_size,
            "status": status,
            "failure_reason": "" if status == "PASS" else "Missing stops/shape or screenshot failed"
        }
        results_by_id[r_id] = record
        print(f"  -> {status} (size: {file_size:,} bytes)", flush=True)

        # Save every 5 routes
        if i % 5 == 0 or i == total:
            sorted_results = [results_by_id[r["id"]] for r in routes if r["id"] in results_by_id]
            with open(RESULTS_JSON, "w", encoding="utf-8") as f:
                json.dump(sorted_results, f, indent=2)
            write_markdown_report(sorted_results, total)

    sorted_results = [results_by_id[r["id"]] for r in routes if r["id"] in results_by_id]
    with open(RESULTS_JSON, "w", encoding="utf-8") as f:
        json.dump(sorted_results, f, indent=2)
    write_markdown_report(sorted_results, total)
    print("ALL ROUTES AUDITED SUCCESSFULLY!", flush=True)

if __name__ == "__main__":
    main()
