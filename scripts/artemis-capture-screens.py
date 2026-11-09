#!/usr/bin/env python3
"""
Capture CatchIt's store/ad screens by letting ARTEMIS (Google's Android automation
agent) drive the app by natural language, then screenshotting the device.

Why this exists: capturing screens by hard-coding `adb shell input tap` coordinates
breaks every time the layout moves. ARTEMIS navigates by meaning ("open the Alarms
tab"), so the capture survives UI changes.

ARTEMIS leaves the app in whatever state it ended the task in, so the screenshot is
taken AFTER the task completes - no race, no sleep-and-hope.

Usage:
    python scripts/artemis-capture-screens.py                 # all screens
    python scripts/artemis-capture-screens.py 01_bus 04_alarms
"""
import os
import re
import subprocess
import sys
import time

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = r"C:\Users\ainol\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = os.environ.get("CATCHIT_SERIAL", "emulator-5554")
ARTEMIS_RUN = r"C:\Users\ainol\Dev\.tools\artemis-run.py"
# CATCHIT_CAPTURE_DIR stages the captures instead of overwriting the shipped assets.
_STAGE = os.environ.get("CATCHIT_CAPTURE_DIR")
OUT_DIR = os.path.join(REPO, "store", "screenshots")
AD_DIR = os.path.join(REPO, "ad", "assets", "screens")
if _STAGE:
    OUT_DIR = os.path.join(_STAGE, "store")
    AD_DIR = os.path.join(_STAGE, "ad")
BRAND_BG = (0x0F, 0x2A, 0x1D)   # PulseGreen900 - the app's own canvas ink

# name -> (natural-language instruction, text that must appear on the captured screen)
SCREENS = {
    "01_bus": (
        "Open the CatchIt app, scroll the Bus list back to the very top so the CatchIt "
        "logo and the live bus count are visible, and stop there.",
        ["Popular routes", "Search route or destination"],
    ),
    "02_search": (
        "In CatchIt, tap the route search field at the top of the Bus screen, type T507, "
        "and stop with the search results showing.",
        ["T507"],
    ),
    "03_route_map": (
        "In CatchIt, open a route from the Bus list so the route detail screen with the "
        "live map is showing, and stop there.",
        ["stops"],   # the route detail header always shows "N stops"
    ),
    "04_stop_rail": (
        "In CatchIt, on the route detail screen, drag the bottom sheet upwards to open "
        "it fully so the vertical list of stops with their ETAs is visible, and stop there.",
        ["stops"],
    ),
    "05_alarms": (
        "In CatchIt, open the Alarms tab at the bottom of the screen and stop there.",
        ["Alarms"],
    ),
    "06_settings": (
        "In CatchIt, open the Settings tab at the bottom of the screen and stop there.",
        ["Settings", "How alarms work"],
    ),
    "07_alarm_setup": (
        "In CatchIt, open a route, open the bottom sheet, and tap the button that sets up "
        "a two-stage alarm so the alarm setup sheet is showing, then stop there.",
        ["Leave now", "et up your alarm", "Turn alarm on"],
    ),
}


def sh(*args):
    return subprocess.run(args, capture_output=True, text=True).stdout


def artemis_run(goal):
    env = dict(os.environ)
    env["PATH"] = r"C:\Users\ainol\Dev\.tools\scrcpy\scrcpy-win64-v2.7;" + env.get("PATH", "")
    proc = subprocess.run(
        [sys.executable, ARTEMIS_RUN, "run", goal,
         "--profile", "flash", "--locked-app", "my.feeder.app",
         "--test-name", "catchit-capture"],
        cwd=r"C:\Users\ainol\Dev\artemis", env=env, text=True, capture_output=True,
    )
    return proc.stdout + proc.stderr


def screen_text():
    sh(ADB, "-s", SERIAL, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh(ADB, "-s", SERIAL, "shell", "cat", "/sdcard/ui.xml")
    return xml


def capture(name):
    raw = os.path.join(REPO, "store", "screenshots", f".raw_{name}.png")
    with open(raw, "wb") as fh:
        proc = subprocess.run(
            [ADB, "-s", SERIAL, "exec-out", "screencap", "-p"],
            stdout=fh, stderr=subprocess.DEVNULL,
        )
    return raw


def normalise(raw, name):
    """1080x2400 device capture -> 864x1920 (the ad's device slot) and 1080x1920 (Play)."""
    from PIL import Image
    im = Image.open(raw).convert("RGB")
    if im.size != (1080, 2400):
        print(f"  ! unexpected capture size {im.size}")
    small = im.resize((864, 1920), Image.LANCZOS)
    padded = Image.new("RGB", (1080, 1920), BRAND_BG)
    padded.paste(small, (108, 0))
    os.makedirs(AD_DIR, exist_ok=True)
    small.save(os.path.join(AD_DIR, f"{name}.png"), optimize=True)
    padded.save(os.path.join(OUT_DIR, f"{name}.png"), optimize=True)
    os.remove(raw)


def main():
    wanted = sys.argv[1:] or list(SCREENS)
    os.makedirs(OUT_DIR, exist_ok=True)
    results = {}
    for name in wanted:
        if name not in SCREENS:
            print(f"unknown screen {name}")
            continue
        goal, expect = SCREENS[name]
        print(f"[{name}] driving CatchIt ...", flush=True)
        out = artemis_run(goal)
        ok = "Task completed successfully" in out
        time.sleep(1.5)
        xml = screen_text()
        texts = " ".join(re.findall(r'text="([^"]*)"', xml))
        missing = [e for e in expect if e not in texts]
        raw = capture(name)
        normalise(raw, name)
        results[name] = {"task_ok": ok, "missing": missing}
        print(f"[{name}] task={'ok' if ok else 'FAILED'}  "
              f"expected_text={'all present' if not missing else 'MISSING ' + str(missing)}")
    print("\nsummary:")
    for k, v in results.items():
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
