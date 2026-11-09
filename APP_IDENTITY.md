# FeederMY — What This App Is, What It Does, and What It's For

*A working document for naming. Everything here is factual — pulled from the
codebase, the approved product spec, and the live data sources — not marketing
copy.*

---

## 1. What it is

**A single-purpose Android app that answers one question:**

> "When does my MRT feeder bus *actually* come, and when do I start walking?"

It is not a journey planner. It is not a rail map. It is not a general transit
app. It exists to solve one failure: missing the feeder bus and waiting 30–40
minutes for the next one.

**Concrete facts:**

| Attribute | Value |
|---|---|
| Platform | Android (Kotlin + Jetpack Compose) |
| Package / app ID | `my.feeder.app` |
| Version | 1.0.4 (build 5) |
| Min / target SDK | 26 / 35 |
| Scope | RapidKL MRT feeder buses, Klang Valley, Malaysia |
| Coverage | **91 routes**, 4,438 scheduled trips, 2,514 stops |
| Live coverage | 84 / 91 routes with real vehicle positions |
| Monetisation | None — no ads, no login, no account |

The name today is "FeederMY" = "feeder" (the bus type) + "MY" (Malaysia's
country code).

---

## 2. What it does

### The core loop
1. Open the app → Home shows your pinned routes and today's popular routes,
   each with its next departure time and live bus count.
2. Tap a route → live map, next-bus strip (plate number, speed, ETA), full
   departure list, and every stop with a per-stop arrival estimate.
3. Tap the bell on a stop → arm a **two-stage departure alarm**:
   - **"Leave now"** — `N` minutes before the bus arrives (default 10).
   - **"At the curb"** — `N` minutes before you must be standing at the bay
     (default 3).
4. The alarm is the whole point — it turns "check the app constantly" into
   "the phone tells me when to move."

### The four screens
1. **Bus (Home)** — the whole app in one scroll. Route search (all 91 routes),
   pinned routes, a daily-rotating "popular routes" list, live counts, next
   departures.
2. **Alarms** — every armed alarm in one list, plus service updates (traffic
   disruptions, diversions) pulled from the operator.
3. **Notifications** — the operator's service alerts, with read state.
4. **Settings** — exactly two knobs ("Leave now" and "At the curb" timing) and
   a battery-whitelist helper for Xiaomi / Samsung / Oppo so Android Doze
   never kills the alarm.

### The data (the real moat)
- **Real operator AVL feed** — actual bus plates, GPS, speed, bearing, engine
  status. Sourced from Prasarana's own kiosk AVL stream (Socket.IO) merged
  with the public `data.gov.my` GTFS-RT feed as fallback.
- **Bearing-aware per-stop ETA** — the feed doesn't say which stop a bus is
  approaching, so the app infers it from bearing + distance. Loop routes
  (which revisit the same stop IDs) are handled by keying ETAs to stop
  *sequence*, not stop ID.
- **Staleness filter** — anything with a GPS fix older than 120 seconds is
  treated as offline, so a bus parked at the depot overnight doesn't show as
  "arriving in 0 min."
- **Real schedules** — 4,438 trips extracted from the static GTFS feed and
  bundled into the app, so it works offline and never guesses.
- **Offline-first map** — Leaflet + ArcGIS dark tiles bundled locally; no CDN
  fetches at runtime.

### The honest gap
The alarm is **armed and displayed** end-to-end, but the background firing —
watching the bus move and pushing the notification at the right moment — is
not yet wired to Android's alarm/worker scheduler. It lives in memory today.
This is the single most important piece of build-out remaining and the single
most important thing the name must promise.

---

## 3. What it's trying to do

**Catch the feeder bus, reliably, in the Klang Valley — and beat Moovit at
exactly that.**

The competitive story is narrow and deliberate:

| Rival | Their problem | FeederMY's answer |
|---|---|---|
| **Moovit** | No RapidKL feeder real-time data — shows schedules + crowdsourced guesses, with ads | Real operator AVL, no ads |
| **Pulse** | Has the data but bad UX (2.7★); removed live speed; wrong timetables | Real data, live speed, plain language |

FeederMY does **not** try to beat Moovit at journey planning, rail maps, or
multimodal routing. Those were built and then **deliberately deleted** — the
spec kills the Journey Planner, the Rail Map, and the Live Bus tab because
they're Moovit's game, not this app's.

The wedge is: *real-time feeder-bus data + a two-stage alarm that no rival
offers, on a one-screen interface that opens instantly with no ads and no
login.*

---

## 4. What its goal is

**Be the fastest, most trustworthy way to catch an MRT feeder bus in the Klang
Valley.**

Measured, the "definition of done" from the approved spec:

1. App launches to Home and shows the next bus in under a second. No ads. No
   login.
2. Three destinations only: Bus, Alarms, Settings.
3. Every label is plain language ("Leave now" / "At the curb", never "2-stage
   departure alarm").
4. An armed alarm fires "leave now" and "at the curb" from **real bus
   movement**, not a timetable.
5. The killed screens stay dead — no journey planner, no rail map, no quick
   tools creeping back in.

In one sentence: **it wins when a commuter who used to miss the bus now
catches it — and tells someone else.**

---

## 5. Why the name matters (naming brief)

The name "FeederMY" is functional but has two weaknesses it should be measured
against:

1. **"Feeder" is jargon.** Commuters say "bas awak" / "the T815", not "the
   feeder bus." The word describes the bus's operator category, not the
   user's job.
2. **"MY" is an acronym suffix.** It reads as a country-code append (like
   "Feeder + Malaysia"), which is technically accurate but generic.

### What a good name must do
- **Promise the outcome, not the vehicle.** The user's job is *catch the bus
  on time*, not *browse feeder-bus data.*
- **Work in two languages.** The audience is bilingual (English + Bahasa
  Melayu). A name that's hard to pronounce in BM is a liability.
- **Imply speed / timing / reliability.** The emotion is "I know exactly when
  to leave and I won't miss it."
- **Be short and shoutable.** It will be said out loud ("is it on the app?").
- **Not box the product in.** A name that means "feeder buses only" makes it
  hard to later cover rail, other operators, or other cities. The app *today*
  is feeder buses — but the name shouldn't be the cage.

### Naming directions to explore
- **The moment** — names about leaving, catching, arrival, timing, the walk to
  the stop. (e.g. themes of "go", "now", "catch", "on time")
- **The alarm** — the two-stage warning is the differentiator; names about
  "nudge", "tell me", "when to leave".
- **The place** — Klang Valley / Malaysia identity without the generic "MY"
  suffix (e.g. names that *feel* local rather than *tag* local).
- **The relief** — the feeling of not waiting 40 minutes; names about peace of
  mind, certainty, "never miss it".

---

## 6. Elevator pitch (for testing names against)

> **FeederMY** tells you exactly when your MRT feeder bus will arrive — live,
> from the operator's own fleet data — and pings you twice before it comes:
> once to start walking, once to be at the curb. No ads, no login, one screen.
> Catch the bus instead of missing it.

Swap the bolded name in and read it aloud. If it still sounds right, it's a
candidate.

---

## 7. Copy-paste prompt (give this to any AI)

> I'm renaming my Android app. Below is the full context: what it is, what it
> does, and what it's trying to achieve. Use it to recommend names.
>
> **The app in short:** A single-purpose Android app for RapidKL MRT feeder
> buses in the Klang Valley, Malaysia. It tells a commuter exactly when their
> feeder bus will actually arrive — live, from the operator's own fleet data
> (real plates, GPS, speed) — and pings them twice before it comes: once to
> start walking ("Leave now", ~10 min out) and once to be standing at the curb
> ("At the curb", ~3 min out). No ads, no login, one screen to open instantly.
> It deliberately does NOT do journey planning or rail maps. Its whole pitch
> is: *catch the feeder bus instead of missing it.* Current name: "FeederMY".
>
> **Audience:** bilingual Klang Valley commuters (English + Bahasa Melayu) who
> ride these buses daily and have been burned by missing one (30–40 min waits,
> drivers skipping runs, no reliable published schedule).
>
> **Competition:** Moovit has no real-time RapidKL feeder data (schedules +
> guesses). Pulse has the data but bad UX. This app wins on real operator data
> + the two-stage alarm + speed.
>
> **Constraints for the name:**
> 1. Promise the outcome (catch the bus on time), not the vehicle ("feeder"
>    is operator jargon).
> 2. Easy to say and remember in both English and Bahasa Melayu.
> 3. Imply speed, timing, or reliability.
> 4. Short and shoutable — people will say it out loud.
> 5. Don't box the product into "feeder buses only" so it could later cover
>    rail or other cities.
> 6. No generic country-code suffix like "MY" unless it earns it.
>
> **What I want from you:**
> 1. 10–15 name candidates, each with a one-line rationale.
> 2. For each, say whether it reads naturally in Bahasa Melayu.
> 3. Flag any that are already taken or too generic in the app space.
> 4. Then give your top 3 picks and why.
> 5. Optionally: suggest a matching tagline for the winner.
>
> Keep it concrete and critical — tell me when a name is weak, not just nice.
