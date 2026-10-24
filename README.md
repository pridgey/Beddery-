# Beddery 🌙

Turn your charging phone into a beautiful bedside display. Beddery shows a screensaver whenever your
phone is charging and locked: a clock, the weather, your agenda, or something calming to fall asleep to.

## Screensavers

68 screensavers in four groups. Every one works in portrait and landscape, and the gallery shows live previews.

| Clocks | Info | Relax | Ambient |
| --- | --- | --- | --- |
| Minimal | Charge Vial | Breathe (+ Refined) | Starfield |
| Analog | Fill Up | Box Breathing | Orbits |
| Flip Clock | Power Flow | Tide | Constellation |
| Bedside Classic (red LED) | Charge Garden | Wind Down | Plexus |
| Nixie | Power Cells | Count Down | The Orb |
| Odometer | Charging | Moonlit Ocean | Jellyfish |
| Big Type | Weather | Aurora | Color Field |
| Rings | Weather Window | Lava Lamp | Contours |
| Day Arc (sun path) | Agenda (calendar) | Fireflies | Ridgelines |
| Sunrise Alarm (dawn before your alarm) | Notifications | Rain on Glass | Flow Field |
| Hourglass | Sleep | Campfire | Harmonograph |
| Word Clock | Sleep Coach | Candle | Pendulum Wave |
| Nightlight (dim red) | Moon Phase | Snowfall | Digital Rain |
| Binary | Sky Tonight (real stars and planets) | Koi Pond | Game of Life |
| Bouncing Clock | Month | Lighthouse |  |
| Block Clock | Progress | Sleeper Train |  |
| Tiny Room (pixel art) | World Clock | Sleeping Cat |  |
| Three Hourglasses (day, hour, minute) | Photo Frame | Aquarium |  |

Breathe also comes in a **Refined** version; swipe between the two in full-screen preview to compare.

Shared features:

- **Full-screen preview**: swipe left and right to browse every screensaver, then pick one or add it to
  shuffle without leaving the preview.
- **Notifications** (optional): clocks and info screens show icons for apps with new notifications, and a
  banner when something arrives. Choose icons only or full previews, and whether they appear at night.
  Silent, ongoing and lock-screen-private notifications are respected. Nothing leaves the phone.
- **Day and night screens**: pick a separate screensaver for your sleep hours, and Beddery switches to
  it at night and back to your day pick (or shuffle) in the morning.
- **Night mode**: during your sleep hours, brightness drops to minimum, colors darken, and an optional
  red tint removes blue light.
- **Burn-in protection**: static elements slowly drift so OLED screens stay healthy.
- **Shuffle**: a new screensaver each time Beddery starts (every one shows before any repeats), or rotate
  every 5–60 minutes.
- **Small clock overlay** on scenic screensavers, plus next alarm and charge time remaining.
- Accent colors, 12/24-hour clock, brightness override.
- A few small surprises. Try tapping things in preview, or with "Double-tap to close" turned on.

## How it starts

Beddery offers two ways to appear while charging:

Settings asks you to pick one (recommending the best fit for your phone), then shows a short checklist
that ticks itself off as you finish setting it up. "Which should I use?" opens a side-by-side comparison.

1. **Android screensaver** (recommended where available). Beddery is a standard Android screensaver
   (a `DreamService`). Pick it in *Settings → Display → Screen saver* and set it to start while charging.
   The app links straight there. Android decides when it starts (usually when the screen times out).
2. **Standby mode** works on every phone, including ones whose maker removed the screensaver setting.
   A small foreground service watches for the charger and the screen locking, then shows Beddery over
   the lock screen through a full-screen notification. It never unlocks your phone; tap to close.
   Standby needs notification and full-screen-notification permission, and the app walks you through both.

Either way, "Double-tap to close" stops a single bump from closing Beddery at night.

## Building

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit + Robolectric render tests
./gradlew lintDebug
```

The render tests draw every screensaver in both orientations and save PNGs to `app/build/screenshots/`,
which makes visual review easy.

## CI

`.github/workflows/build.yml` runs lint and the tests, then builds debug + release APKs on every push and PR.
APKs (and screenshots) are attached to each run as artifacts.

Every push to `main` also publishes a GitHub Release (`v1.0.<run number>`) with the release APK, so the
latest build is always at **Releases → Latest**. Pull requests only build. You can still push a tag like
`v2.0.0` to release with a specific version.

### Release signing (optional)

Without a keystore, CI signs the release APK with a throwaway debug key. It installs fine, but each CI
run's APK has a different signature, so you'll need to uninstall before updating. To sign properly,
add these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create a keystore with:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias beddery
```

## Privacy

- Weather comes from [Open-Meteo](https://open-meteo.com) (no account, no tracking). Only the
  coordinates you choose are sent.
- Calendar events and photos are read on-device only and never leave your phone.
- Location is used once, when you tap "Use my location", to pick a weather location.
