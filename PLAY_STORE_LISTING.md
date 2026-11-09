# CatchIt — Google Play Store listing

Everything Play needs, ready to paste. Replace the bracketed placeholders before publishing.

---

## App name (30 chars max)

```
CatchIt: RapidKL Feeder
```

## Short description (80 chars max)

```
Know when your feeder bus is coming — and when to start walking.
```

## Full description (4000 chars max)

```
CatchIt tells you when your RapidKL MRT feeder bus is actually coming — and, more usefully, when you should start walking to the stop.

Feeder buses run every 15 to 40 minutes. Missing one means a long, hot wait. CatchIt removes the guesswork.

WHAT CATCHIT DOES

• Live bus positions, from the operator's own data
  CatchIt shows where your feeder bus really is right now, how fast it is moving, and which stop it is heading to. It merges two official live feeds, so it covers more routes than either source does on its own.

• Real timetables
  Every route's published schedule is built into the app, with actual departure times and the full stop list. No estimates, no invented times.

• Two-stage departure alarms
  The feature that changes your morning. Pick TWO stops on your route, and CatchIt watches the live bus feed for you:
    — "Leave now" when the bus reaches your first stop
    — "Go to your waiting spot" when the bus reaches your second stop
  Nothing to configure — you choose the stops, and the app tells you when the bus gets there. Both warnings describe the same bus, so you are never chasing one that has already gone. The alarm runs on your phone, so it still fires if you close the app.

• Stop-by-stop arrival estimates
  CatchIt estimates arrival at each stop ahead, using the bus's live position, speed, direction and the route's stop order — not a straight line on a map.

• Service updates
  Disruptions, diversions and route notices from the operator, pushed to your phone so you hear about them before you leave the house.

• Your routes, one tap away
  Star the routes you use. They sit at the top of the home screen with live bus counts and the next departures. A rotating "Popular routes" shelf surfaces the busiest routes each day.

BUILT FOR HOW RAPIDKL FEEDERS ACTUALLY WORK

Live positions, not crowdsourced guesses. Two official feeds merged for wider coverage. Stale vehicles are filtered out, so you never chase a bus that parked twenty minutes ago.

NO ADS. NO ACCOUNT. NO SIGN-IN.

CatchIt has no advertising, no tracking, no analytics, and no account. It never asks for your location — it shows you where the bus is, not where you are. Your routes and alarms stay on your phone.

PERMISSIONS, PLAINLY

• Internet — to fetch bus positions, timetables and service notices
• Notifications — for your departure alarms and service updates
• Battery optimisation exemption — optional, so alarms still fire when the app is closed

CatchIt does not ask for location, camera, microphone, contacts, or storage.

DATA CREDITS

Live bus data: Prasarana RapidKL and the Government of Malaysia's open data portal (data.gov.my).

---

## Category & tags

- **Category:** Travel & Local
- **Tags:** RapidKL, MRT feeder, bus tracker, Kuala Lumpur, Klang Valley, public transport, bus timetable, departure alarm, live bus

---

## Content rating notes

- No user-generated content
- No violence, sexuality, language, gambling, or controlled substances
- No user interaction beyond tapping and typing a route
- No location sharing between users
- **Reference rating:** Everyone / suitable for all ages

---

## Data safety declaration

| Question | Answer |
| --- | --- |
| Does your app collect or share any required user data types? | **No** |
| Is all user data encrypted in transit? | **Yes** — all connections are HTTPS |
| Do you provide a way for users to request that their data is deleted? | Not applicable — CatchIt does not collect user data |
| Is the app's data collection practices compliant with Play's policies? | Yes |

**Permissions declared:**

- `INTERNET` — fetch bus data
- `ACCESS_NETWORK_STATE` — connectivity check
- `POST_NOTIFICATIONS` — alarms and service updates
- `VIBRATE` — noticeable alarms
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — optional, for reliable background alarms

**Not declared:** location, camera, microphone, contacts, phone, photos/media, storage.

> Note for Play review: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is used solely so that a user-set departure alarm can still be evaluated when the app is not in the foreground. The app is fully functional without this exemption — it only asks the user to it when they arm an alarm.

---

## Privacy policy

Publish `PRIVACY_POLICY.md` at a public URL and paste its contents into the Play Console's privacy policy field.

---

## Store assets checklist

| Asset | Spec | Status |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit | Adaptive icon — green field, white "C", amber check |
| Feature graphic | 1024 × 500 PNG | See `store/feature_graphic.png` |
| Phone screenshots | 2–8, 16:9 or 9:16, min 320 px per side | See `store/screenshots/` — 01 Bus home, 02 Bus scrolled, 03 Route detail with the live map, 04 Alarms (a two-stage alarm on T507), 05 Settings, 06 Service updates, 07 Setting up a two-stage alarm |
| Privacy policy | Public URL | `PRIVACY_POLICY.md` |
| Short description | ≤ 80 chars | above |
| Full description | ≤ 4000 chars | above |
| Content rating | Questionnaire | above |
| Data safety | Questionnaire | above |

---

## Release checklist

- [x] Release build passes R8 (`app/proguard-rules.pro` present)
- [x] Release build is signed (upload keystore, `keystore.properties` — git-ignored)
- [x] `app-release.aab` produced for Play upload (4.27 MB)
- [x] No `REQUEST_INSTALL_PACKAGES` permission
- [x] No Firebase, no analytics or advertising SDK
- [x] No account or login
- [x] Privacy policy written
- [x] Live bus feed verified working in the **minified release build** (181 buses live, no crashes)
- [x] Both alarm stages verified firing from live bus movement in the release build
- [x] Alarm persistence verified across a force-stop and relaunch
- [x] Version bumped to 1.1.0 (build 6)
- [x] Launcher icon regenerated — white C with an amber check, inside the adaptive safe zone
- [x] Play icon (512×512), feature graphic (1024×500) and 6 phone screenshots produced
- [ ] Replace the placeholder contact address in `PRIVACY_POLICY.md`
- [ ] Play Console listing created and assets uploaded
- [ ] Internal testing track release
- [ ] Closed testing track (required before production for new personal developer accounts)
- [ ] Production release
