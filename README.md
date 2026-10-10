# WallSwap

An [Irvue](https://irvue.tumblr.com/)-style Unsplash wallpaper changer for Android, built for the
Galaxy Z Flip 8. It changes the **cover screen**, the **interior screen**, or both.

## Features

- Photos from Unsplash albums (collections). Two wallpaper albums are included by default; add more
  by link, and disable them all to get photos from all of Unsplash.
- Auto-change every 15 min to every day, switched on or off per screen.
- **Next** per screen, **Save** to `Pictures/WallSwap`, and **Ban** (never show that photo again).
- Photographer credit with links to Unsplash.
- Quick Settings tile **Next wallpaper**: new photo on the screen in use.

## Setup

1. Install the APK (see below) and open WallSwap.
2. Tap **Allow** under *Background changes* so One UI doesn't put the app to sleep.
3. Optional: add albums by pasting links like `https://unsplash.com/collections/<id>/<name>`.
4. Optional: add the **Next wallpaper** tile from the Quick Settings edit screen.

The Unsplash Access Key is built into the APK. Demo apps get 50 requests per hour, and each
wallpaper change uses 2.

## How the cover and interior screens work

Android has no API to target a specific screen. One UI applies a wallpaper to the screen **in use**:
the cover screen while the phone is folded, the interior screen while it is open. So WallSwap:

- reads the hinge sensor to find out which screen is in use,
- only changes a screen while it is in use: the cover while folded, the interior while open,
- queues **Next** on the other screen and applies it the next time that screen is in use.

Android only delivers sensor events to foreground apps. A small foreground service (minimised
notification "Watching the fold state") keeps WallSwap allowed to read the hinge angle when a
scheduled change runs. Checks run every 15 minutes, so a queued or due change lands within about
15 minutes of folding or opening the phone.

## Build

The Unsplash Access Key stays out of this public repo and is read from the Gradle property
`unsplashAccessKey`; the build fails without it.

- **Locally**: add `unsplashAccessKey=<your key>` to `~/.gradle/gradle.properties`, then run
  `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
- **GitHub Actions**: add the repository secret `UNSPLASH_ACCESS_KEY` (Settings → Secrets and
  variables → Actions).

## Install

- **From GitHub Actions**: every push builds `wallswap.apk` (Actions tab → latest run →
  *wallswap-apk* artifact). Pushing a tag like `v0.1.0` also publishes it as a GitHub release.

Every build is signed with the committed `app/wallswap.keystore`, so a new APK installs over the
previous one. It's a convenience key for a personal, sideloaded app; don't use it for a Play Store
release.
