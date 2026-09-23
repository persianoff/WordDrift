# WordDrift

An Android TV app that shows a rotating vocabulary flashcard (English / German / Ukrainian / Russian) as an overlay while the screensaver is active or YouTube is playing, plus a phone companion app for editing the vocabulary remotely.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the TV app works.

This is a two-module repo: `app` is the TV app, `companion` is the phone app. Both are published on the same [Releases](../../releases) page — grab the asset by name (`WordDrift-vX.Y.apk` for the TV, `WordDriftCompanion-vX.Y.apk` for the phone); don't rely on "latest" since it reflects whichever module was released most recently, not one or the other specifically.

## Install on a TV

Download `WordDrift-vX.Y.apk` (latest version) from [Releases](../../releases) and sideload it — either:

- **Via a file manager app on the TV**: download the `.apk` directly on the TV (e.g. via a browser or Downloader app) and open it to install.
- **Via ADB** (TV and computer on the same network, with ADB debugging enabled on the TV):
  ```
  adb connect <tv-ip>:5555
  adb install WordDrift-vX.Y.apk
  ```

On first launch, the app has no permissions yet. Open it and:
1. Tap **"Enable display over other apps"** and grant the permission in the system settings screen that opens, then go back.
2. Tap **"Enable usage access"** if it appears, and grant it the same way (this screen doesn't exist on every TV — some models don't ship it, and there is no other way to grant it without ADB on those).

Both buttons disappear once granted, and the app updates the vocabulary list and rotation interval — the switch turns the overlay on/off. The dictionary is bundled in the APK, so nothing else needs to be pushed to the TV for it to work.

## Install the companion app on a phone

Download `WordDriftCompanion-vX.Y.apk` (latest version) from [Releases](../../releases) and sideload it onto an Android phone on the **same WiFi network** as the TV.

Open it: it finds the TV automatically (via NSD/mDNS, service type `_worddrift._tcp`, no IP to type in), fetches the current dictionary, and lets you edit it directly. Type a word in the **"New word"** box to search the list and highlight a matching line (useful for checking if something similar already exists), or tap **Add** to append it as a new line. Tap **"Save to TV"** to push your changes back — the TV picks them up on its next overlay rotation, no restart needed.

Requires the TV app to already be installed and running (its embedded server on port 8765 is what the phone talks to).

## Building from source

Requires JDK 17 and the Android SDK (API 36, build-tools 36.0.0). Then:

```
./gradlew :app:assembleDebug
./gradlew :companion:assembleDebug
```

Release builds need a signing config; see `app/build.gradle.kts` and `companion/build.gradle.kts`.
