# Beddery 🌙

Turn your charging phone into a beautiful bedside display. Beddery shows a screensaver whenever your
phone is charging and locked: a clock, the weather, your agenda, or something calming to fall asleep to.

## Screensavers

| Clocks | Info | Relax | Ambient |
| --- | --- | --- | --- |
| Minimal | Charging | Breathe (guided) | Starfield |
| Analog | Weather | Moonlit Ocean | Orbits |
| Flip Clock | Agenda (calendar) | Aurora | Constellation |
| Word Clock | World Clock | Lava Lamp | Digital Rain |
| Nightlight (dim red) | Photo Frame | Fireflies | Game of Life |
| Binary | | | |
| Bouncing Clock | | | |

Every screensaver works in portrait and landscape. The gallery shows live previews.

Shared features:

- **Night mode**: during your sleep hours, brightness drops to minimum, colors darken, and an optional
  red tint removes blue light.
- **Burn-in protection**: static elements slowly drift so OLED screens stay healthy.
- **Shuffle**: rotate through your favorites every 5–60 minutes.
- **Small clock overlay** on scenic screensavers, plus next alarm and charge time remaining.
- Accent colors, 12/24-hour clock, brightness override.

## How it starts

Beddery offers two ways to appear while charging:

1. **Android screensaver** (recommended where available). Beddery is a standard Android screensaver
   (a `DreamService`). Pick it in *Settings → Display → Screen saver* and set it to start while charging.
   The app links straight there.
2. **Standby mode** works on every phone, including ones whose maker removed the screensaver setting.
   A small foreground service watches for the charger and the screen locking, then shows Beddery over
   the lock screen through a full-screen notification. It never unlocks your phone; tap to close.
   Standby needs notification and full-screen-notification permission, and the app walks you through both.

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
