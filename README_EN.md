# Kindle Assistant

A small tool made for e-ink screens. It runs on a jailbroken Kindle Paperwhite 3 (Readom / Android 8.1) and puts LAN photo transfer, a to-do list, an hourglass timer and a desktop calendar on the same display. Large text, high contrast, no animation — redesigned for e-paper habits.

[中文](README.md) · [Terms & Disclaimer](#terms--disclaimer)

## Features

### 1. Send photos to your Kindle
From a phone, computer or tablet on the same Wi-Fi, scan the QR code on the home screen (or open the displayed address), pick a few photos and upload them. The Kindle automatically opens the last one full-screen.

- Upload from phone, computer or tablet; multiple photos at once
- Photos are permanently saved in the system-visible `Pictures` folder
- Grid browsing, tap to view full-screen, swipe left/right to switch
- Tap a photo to reveal a "Rotate 90°" button (hides after 10 seconds of inactivity); swipe inward from the screen edge to go back

### 2. A to-do list you can edit from anywhere
Use it on the Kindle, or open the same address in a browser on your phone, computer or tablet — all edits land in one shared list.

- Three tabs: Pending / Done / Deleted, each with a count
- Add, edit and delete, with confirmation before deleting; restore from "Deleted", delete again to remove permanently
- Each entry shows when it was added, completed/deleted, and accumulated time spent
- Device and web views share the same data in `files/todos.json` inside the app

### 3. An hourglass countdown
- Quick buttons for 5 / 10 / 15 / 30 minutes and 1 hour, or a slider for 1–60 minutes
- The upper chamber shows time remaining, the lower chamber time elapsed, with sand falling at a constant rate
- Pausing stops the sand and the stream; leaving the page pauses automatically, and "Resume" continues where you left off
- Finishing shows elapsed and accumulated time, and writes it back to the to-do
- To protect the e-ink display, the clock and hourglass hide seconds and refresh on minute boundaries; elapsed results keep seconds

### 4. A desk calendar, one page per day
- Gregorian date, lunar date, weekday and current solar term, with lunar/term calculations in Beijing time
- The current time (hours and minutes only) shows on today's page and is hidden on other dates
- Today's weather appears to the left of the date/time area; the coming week shows 7 days from tomorrow in a horizontal row that fits one screen
- Searchable and saved city (candidates from Photon / OpenStreetMap), weather from Open-Meteo — an internet connection is required

## Device requirements

| Item | Requirement |
| --- | --- |
| Device | Kindle Paperwhite 3 (7th gen, DP75SDI) |
| Firmware | 5.16.2.1.1, jailbroken and running Readom (Android 8.1) |
| Prerequisites | `sh_integration` available in the jailbreak; at least 2.6 GB free storage |
| Network | Photos and to-dos need the same LAN; weather needs internet access |

Jailbreaking and Readom are separate projects. This app does not handle them; proceed at your own risk:

- Jailbreak: [kindlemodding.org](https://kindlemodding.org/)
- Readom install guide: [readom.app/#guide](https://readom.app/#guide)
- Recommended: hand the [install instructions for AI assistants](https://readom.app/install_readom.md) to your AI assistant for the computer-side steps

## Installation

Download `KindleAssistant.apk` and install it with adb:

```bash
adb install -r KindleAssistant.apk
```

You can also copy the APK to the device and open it with a file manager. On first launch, grant storage permission and tap "Auto add" when asked to place the "Kindle Assistant" icon on the home screen.

After installation, connect your phone, computer or tablet to the same Wi-Fi as the Kindle, then scan the QR code or open the address shown on the home page (for example `http://192.168.31.192:8088/`) to send photos and manage to-dos. The HTTP service listens on `0.0.0.0:8088` with no authentication — **use it only on a trusted LAN**.

## Building from source

The Android project is at the repository root, package `com.kindle.assistant`, `minSdk 27` / `targetSdk 27` / `compileSdk 34`.

```bat
scripts\build.bat
```

Output: `app\build\outputs\apk\release\KindleAssistant.apk` (signed).

Build requirements: Gradle 8.7, Android SDK and Android Studio's bundled JBR (JDK 21). If command-line builds fail because local GraalVM 17 lacks the jlink modules AGP needs, set:

```bash
JAVA_HOME="D:\Program Files\Android\Android Studio\jbr"
```

Signing credentials are not distributed with the repository. Configure them in `local.properties` (or via environment variables):

```properties
signing.storeFile=C:/path/to/photodrop.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
```

Environment variables: `SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`.

See [`docs/usage.md`](docs/usage.md) for the full Chinese user manual. The project homepage, promotional site and download link are available on the [4reallab/KindleAssistant](https://github.com/4reallab/KindleAssistant) repository page.

## Credits

Kindle Assistant runs on top of Readom. Thanks to [Xiaobeng Daxia @ Bilibili](https://www.bilibili.com/video/BV1Zj4v6aETB/) for open-sourcing Readom, which gives this Kindle a lot more possibilities.

- [readom.app](https://readom.app/) — Android 8.1 for Kindle
- [hbyxabc/readom](https://github.com/hbyxabc/readom)
- Weather data: [Open-Meteo](https://open-meteo.com/); city search: [Photon](https://photon.komoot.io/) / OpenStreetMap

## Terms & Disclaimer

Copyright (c) 2026 4reallab

This software is free for personal study, research and non-commercial use only.

Commercial use of any kind — including but not limited to resale, paid services, internal corporate production use, SaaS, or developing projects for clients — is prohibited without the author's written authorization. For commercial licensing, please contact the author.

This software is provided "as is", without warranty of any kind. It has not been thoroughly tested; by using it you accept the risk of possible damage to your device.

Contact: forreallab@126.com (bug reports and commercial licensing enquiries).
