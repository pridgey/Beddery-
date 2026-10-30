# Beddery 🌙

Turn your charging phone into a beautiful bedside display. Beddery shows a screensaver whenever your
phone is charging and locked: a clock, the weather, your agenda, or something calming to fall asleep to.

## Screensavers

69 screensavers in five groups. Every one works in portrait and landscape.

| Clocks | Sleep | Info | Scenes | Abstract |
| --- | --- | --- | --- | --- |
| Minimal | Sleep | Weather | Moonlit Ocean | Starfield |
| Analog | Sunrise Alarm (dawn before your alarm) | Weather Window | Aurora | Orbits |
| Nightlight (dim red) | Sleep Coach | Agenda (calendar) | Campfire | Constellation |
| Bedside Classic (red LED) | Breathe (+ Refined) | Month | Rain on Glass | Plexus |
| Big Type | Box Breathing | Notifications | Snowfall | Flow Field |
| Flip Clock | Tide | Now Playing | Sleeping Cat | Color Field |
| Rings | Wind Down | Moon Phase | Koi Pond | Contours |
| Word Clock | Count Down | Sky Tonight (real stars and planets) | Lighthouse | Ridgelines |
| World Clock |  | Progress | Sleeper Train | Harmonograph |
| Nixie |  | Photo Frame | Aquarium | Pendulum Wave |
| Odometer |  | Charging | Candle | Lava Lamp |
| Binary |  | Charge Vial | Fireflies | Digital Rain |
| Block Clock |  | Fill Up | Jellyfish | Game of Life |
| Bouncing Clock |  | Power Cells | The Orb |  |
| Day Arc (sun path) |  | Power Flow |  |  |
| Tiny Room (pixel art) |  | Charge Garden |  |  |
| Hourglass |  |  |  |  |
| Three Hourglasses (day, hour, minute) |  |  |  |  |

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
- **Free, no ads.** Optional tips: Google Play tips in the Play version, a Ko-fi link in the GitHub version.
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

Requirements: JDK 17 and the Android SDK (API 36).

Beddery comes in two versions built from the same code:

- **play**: for Google Play. Play delivers updates, so this version has no updater and no install permission.
  Tips use Google Play Billing: create one-time consumable products `tip_small`, `tip_medium` and `tip_large`
  in Play Console.
- **github**: the APK on GitHub Releases. It checks GitHub for new versions and installs them itself.
  Set `KOFI_URL` (environment variable, or a repository variable for CI) to show a Ko-fi tip link in Settings.

```bash
./gradlew assembleGithubDebug        # app/build/outputs/apk/github/debug/
./gradlew bundlePlayRelease          # app/build/outputs/bundle/playRelease/ (upload to Play Console)
./gradlew testGithubDebugUnitTest    # unit + Robolectric render tests
./gradlew lintGithubDebug lintPlayRelease
```

The render tests draw every screensaver in both orientations and save PNGs to `app/build/screenshots/`,
which makes visual review easy.

## CI

`.github/workflows/build.yml` runs lint and the tests, then builds the GitHub APKs and the Play bundle on every
push and PR. APKs, the Play bundle (`play-bundle-<version>`) and screenshots are attached to each run as artifacts.

Every push to `main` also publishes a GitHub Release (`v1.0.<run number>`) with the GitHub release APK, so the
latest build is always at **Releases → Latest**. Pull requests only build. You can still push a tag like
`v2.0.0` to release with a specific version.

### Release signing (optional)

The two versions use separate keys. Without a keystore, CI signs that version with a throwaway debug
key: it installs fine, but each run has a different signature, so you'd need to uninstall before updating.

**GitHub version.** This key signs the APK people install, so it must never change, or the built-in
updater's downloads will refuse to install over the old version.

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

**Play version.** This is only the *upload* key. With Play App Signing, Google re-signs the app with
its own key, and you can ask Google to reset a lost upload key.

| Secret | Value |
| --- | --- |
| `PLAY_KEYSTORE_BASE64` | `base64 -w0 play-upload.jks` |
| `PLAY_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_KEY_ALIAS` | key alias |
| `PLAY_KEY_PASSWORD` | key password |

Create a keystore with:

```bash
keytool -genkeypair -v -keystore play-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload
```

For a local signed build, set the same names as environment variables, with `BEDDERY_KEYSTORE` /
`PLAY_KEYSTORE` pointing at the `.jks` file (and `BEDDERY_` in place of the bare `KEY_` names).

## Privacy

The full policy is in [`docs/privacy.html`](docs/privacy.html), published with GitHub Pages.

- Weather comes from [Open-Meteo](https://open-meteo.com) (no account, no tracking). Only the
  coordinates you choose are sent.
- Calendar events and photos are read on-device only and never leave your phone.
- Location is used once, when you tap "Use my location", to pick a weather location.
