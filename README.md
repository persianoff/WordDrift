# WordDrift

An Android TV app that shows a rotating vocabulary flashcard (English / German / Ukrainian / Russian) as an overlay while the screensaver is active or YouTube is playing.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how it works.

## Install on a TV

Download the latest signed APK from [Releases](../../releases/latest) and sideload it — either:

- **Via a file manager app on the TV**: download the `.apk` directly on the TV (e.g. via a browser or Downloader app) and open it to install.
- **Via ADB** (TV and computer on the same network, with ADB debugging enabled on the TV):
  ```
  adb connect <tv-ip>:5555
  adb install WordDrift-v1.0.apk
  ```

On first launch, the app has no permissions yet. Open it and:
1. Tap **"Enable display over other apps"** and grant the permission in the system settings screen that opens, then go back.
2. Tap **"Enable usage access"** if it appears, and grant it the same way (this screen doesn't exist on every TV — some models don't ship it, and there is no other way to grant it without ADB on those).

Both buttons disappear once granted, and the app updates the vocabulary list and rotation interval — the switch turns the overlay on/off. The dictionary is bundled in the APK, so nothing else needs to be pushed to the TV for it to work.

## Building from source

Requires JDK 17 and the Android SDK (API 36, build-tools 36.0.0). Then:

```
./gradlew assembleDebug
```

Release builds need a signing config; see `app/build.gradle.kts`.
