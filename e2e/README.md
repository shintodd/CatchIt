# CatchIt — E2E tests

Device tests for the CatchIt Android app, run with the
[`e2e`](https://e2e.com) framework's mobile engine (`@e2e-dev/mobile`).

## What is covered

`tests/home.e2e.ts` — the app's whole surface, as the product spec defines it
(three screens: Bus, Alarms, Settings):

| Test | What it proves |
|---|---|
| the Bus screen shows the route directory | Home renders search and "Popular routes" |
| the bottom bar has exactly three destinations | Bus / Alarms / Settings, nothing else |
| searching a route code narrows the list to that route | search filters all 91 routes down to the match |
| picking a search result opens the route detail | navigation into the detail screen works |
| back from the route detail returns to the route list | the back affordance works |
| the Alarms screen opens | the alarms tab is reachable |
| the Settings screen opens and shows the alarm timing | both timing knobs render |
| a route can be pinned and unpinned | the star toggle round-trips |
| a pinned route appears under Your routes | pinning puts a card in the pinned section |

`tests/service-alerts.e2e.ts` — the operator's live service updates:

| Test | What it proves |
|---|---|
| the notifications screen lists service alerts | the alerts from Prasarana's page render |
| the notifications screen says when it last checked | the freshness line is shown, or the list is flagged as stale |

### Tests are self-contained

The suite reinstalls the build before it runs, which wipes the app's stored
data — including its pinned routes. A test that assumed a pinned route already
existed would pass or fail depending on what ran before it, so every test that
needs a pinned route pins one itself and puts it back afterwards.

## Run it

The emulator must be booted, and the APK must exist at the path in
`e2e.config.ts`.

```bash
# 1. Boot the emulator (or start it from Android Studio)
#    -gpu host is REQUIRED for usable frame rates. The default falls back to
#    SwiftShader, which rasterises every frame on the CPU and makes the whole
#    emulator - including stock Android apps - run at about 14 fps.
emulator -avd Medium_Phone_API_37.0 -gpu host -no-audio -no-boot-anim -no-snapshot

# 2. Build the app
cd ..
./gradlew.bat assembleDebug

# 3. Check the toolchain
cd e2e
npx e2e doctor

# 4. Run the suite
npx e2e run
```

The suite runs against whatever state the app is in, so **clear the app's data first**
(`npm run test:e2e` does this for you). A leftover armed alarm keeps the background worker
rescheduling, which stops the app process from being closed cleanly between tests and shows up
as a flaky `process remained running after close`.

`npx e2e doctor` checks agent-device, the Android SDK, the booted device and
the APK path before a run, and prints exactly what is missing if something is.

## Test tags

Compose hides `testTag()` values on Android unless a root composable sets
`Modifier.semantics { testTagsAsResourceId = true }`. That is set once, on the
root `Surface` in `MainActivity.kt`. Everything else is a plain `testTag`:

| Tag | Where |
|---|---|
| `search` | the Home search field |
| `search-result-<CODE>` | a row in the search results panel |
| `route-<CODE>` | a route card in the Popular list |
| `star-<CODE>` | the star on a route card in the Popular list |
| `pinned-<CODE>` | a route card in "Your routes" |
| `pinned-star-<CODE>` | the star on a pinned route card |
| `nav-home`, `nav-alerts`, `nav-settings` | the three bottom-bar destinations |
| `detail-back`, `detail-star` | the route detail screen's back button and star |

### Two gotchas worth knowing

**The star's state is in the icon's label, not the button's name.** The button
is named by its test tag (`detail-star`); the `Pin` / `Unpin` accessible label
sits on the icon inside it. So assert with `screen.getByLabel('Unpin')`, not
`screen.getByRole('button', 'Unpin')`.

**"Your routes" is a horizontally scrolling row.** A route pinned at the end of
it sits off-screen, and an off-screen card's controls are not in the
accessibility tree. Toggle the pin from the route detail screen instead, where
the star is always on screen.

## Agentic tests

`e2e.config.ts` has no `agents` block, so only deterministic tests run and no
AI gateway key is needed. To add agent-driven steps, add an `agents` entry
with a model and set `AI_GATEWAY_API_KEY`.
