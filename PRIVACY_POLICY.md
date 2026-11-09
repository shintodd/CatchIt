# Privacy Policy — CatchIt

**Last updated: 9 November 2026**

CatchIt is an Android app that tells you when your RapidKL MRT feeder bus is coming, and when to start walking to the stop.

## The short version

CatchIt has **no account, no login, no ads, and no analytics**. We do not collect, store, sell, or share any personal information. There is no server that we operate. Everything you set in the app stays on your device.

## What the app stores on your device

CatchIt saves the following locally, in your device's private app storage. None of it leaves your phone.

| What | Why | Where it lives |
| --- | --- | --- |
| Your pinned ("starred") routes | To show your routes on the home screen | App private storage |
| Your departure alarms, and which stages have already been announced | So an alarm survives closing the app and is never announced twice | App private storage |
| Your alarm timings ("Leave now" / "At the curb" minutes) | To remember your preference | App private storage |
| Which service notices you have read | To show the unread badge correctly | App private storage |
| Your selected station | To make the home screen useful on open | App private storage |

You can erase all of it by clearing the app's storage in Android Settings, or by uninstalling the app.

## What the app sends over the network

CatchIt only makes **outbound requests** to fetch public information. These requests tell the servers which data you want; they do not identify you.

| Destination | What is fetched | Why |
| --- | --- | --- |
| `api.data.gov.my` (Government of Malaysia open data) | Live vehicle positions and the published timetable for RapidKL feeder buses | The timetable and one of the live bus-position feeds |
| `rapidbus-socketio-avl.prasarana.com.my` (Prasarana RapidKL) | Live vehicle positions | The second live bus-position feed, used to cover routes the open-data feed omits |
| `myrapid.com.my` | Published service-alert notices | To show you disruption notices such as route diversions |
| `jsdelivr` / `unpkg` map tile and library requests as shown on the live map | Map tiles and the map library | To draw the map |

**Your device's location is never read.** The app shows where the *bus* is, using the operator's own published positions — not where you are. CatchIt does not request the location permission at all.

There is no sign-in, no advertising identifier, no fingerprinting, and no third-party tracking or analytics SDK.

## Notifications

If you allow notifications, CatchIt posts:

- **Departure alarms** — triggered locally on your device when the live bus feed shows your bus is close to your stop.
- **Service updates** — notices about disruptions, pulled from the operator's published alerts.

These are generated on your device from public data. There is no push server behind them.

## Data sharing

We do not share any data with third parties, because we do not collect any in the first place. We do not sell data. There is no analytics or advertising SDK in the app.

## Children's privacy

CatchIt is a public transport utility and is not directed at children under 13. Since we collect no personal information from anyone, including children, this policy applies equally to all users.

## Permissions and why we ask for them

| Permission | Why |
| --- | --- |
| `INTERNET` | Fetching bus positions, timetables and service notices |
| `ACCESS_NETWORK_STATE` | Checking there is a connection before polling |
| `POST_NOTIFICATIONS` | Showing your departure alarms and service updates |
| `VIBRATE` | Making a departure alarm noticeable |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Letting you exempt CatchIt from Android's battery saver so alarms still fire when the app is closed. This is optional and the app works without it. |

CatchIt does **not** request location, camera, microphone, contacts, storage, or phone permissions.

## Third-party open data

Live bus data is provided by Prasarana RapidKL and the Government of Malaysia's open data portal. Those terms also apply to your use of their data.

## Changes to this policy

If this policy changes, the updated version will be published on the Play Store listing for CatchIt, and the "last updated" date above will change.

## Contact

Questions about this policy: **catchit.support@example.com**

*(Replace this address with your real contact address before publishing.)*
