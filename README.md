# Social Blocker

Personal-use Android 13+ app that limits selected social apps. It runs a foreground service that polls `UsageStatsManager` about once a second and draws a full-screen overlay when an app is blocked.

## Rules (strictest wins)
1. **Morning lockout**: all tracked apps are blocked for N min (default 30) after the day's first unlock.
2. **Session cooldown**: after N min (default 10) of use, with up to 90 s of backgrounding allowed, the app is blocked for M min (default 30).
3. **Daily cap**: after N min (default 60) in a day, the app is blocked until midnight.

While a tracked app is open, a draggable timer at the top of the screen counts down to the next block. It turns amber with 1 minute left and red with 30 seconds left. At 0:00 the full-screen block covers the app.

The daily total is whichever is larger: the app's own count or Android's screen-time data for today (the same data Digital Wellbeing shows). So time used before installing the blocker still counts toward the daily limit.

## Structure
| Path | Role |
|---|---|
| `domain/RuleEngine.kt` | Pure rule logic (`accrue`, `evaluate`), unit tested |
| `domain/ChallengeEngine.kt` | Pure quiz logic (vocab/math, scoring, 60 s retry cooldown, 5 min unlock), unit tested |
| `data/` | Room entities and a single DAO; vocab seeded from `assets/german_vocab.json` |
| `service/BlockerService.kt` | `specialUse` foreground service with a 1 s polling loop |
| `overlay/BlockOverlay.kt` | `TYPE_APPLICATION_OVERLAY` blocking window |
| `receiver/Receivers.kt` | Exact midnight alarm and boot restart |
| `ui/` | Compose settings screen and challenge gate (MVVM, no DI framework) |

## Build & install
```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
Open the app and grant everything in the **Setup required** card: usage access, draw over other apps, notifications and unrestricted battery. The service starts once usage access and overlay are granted.

To test quickly, unlock editing, add an app and set its session limit to 1 min.

### "Restricted setting" / toggle greyed out
Android blocks usage access and overlay permissions for apps installed from an APK file.
1. Try the toggle once so the "Restricted setting" dialog appears.
2. Go to **App info → ⋮ → Allow restricted settings** and confirm with your PIN.
3. Grant the permissions again.

Or, with USB debugging on:
```sh
adb shell appops set ch.puc.blocker ACCESS_RESTRICTED_SETTINGS allow
adb shell appops set ch.puc.blocker GET_USAGE_STATS allow
adb shell appops set ch.puc.blocker SYSTEM_ALERT_WINDOW allow
```

## Edge cases & limitations
- **Detection latency is about 1 s**, so a blocked app may flash briefly before the overlay covers it.
- **OEM battery killers** (Xiaomi, Huawei, Samsung, OnePlus and others) may still stop the service even with the battery exemption. Also allow autostart and "no restrictions" in the vendor settings (see dontkillmyapp.com).
- **Bypassable by design**: uninstalling the app, revoking usage access or overlay permission, force-stopping it, or changing the system clock all defeat the limits. There is no uninstall protection, because Device Admin can't reliably prevent uninstall and adds complexity.
- **Morning lockout at midnight**: if the phone is in use at midnight, that moment counts as the new day's "first unlock".
- **Time while blocked isn't counted** toward the daily total.
- **`USE_EXACT_ALARM`** is auto-granted for sideloaded use but would be rejected on Google Play. The code falls back to an inexact alarm if exact alarms aren't allowed. Since counters are keyed by date, the reset stays correct either way; the alarm only prunes old rows and refreshes the service promptly.
- **Edit window**: settings re-lock when the screen leaves the foreground, including while you're granting a permission.
