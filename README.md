# HaloTV 3.2.3 source

HaloTV is a derivative of [Aerial](https://github.com/pengchujin/aerial-tv), not an official upstream release. Original copyright and MIT license are retained; see LICENSE and NOTICE.md.

This archive contains the Standard / Guard (`app/`) and Lite (`lite/`) source, resources, host-side tests and tooling corresponding to 3.2.3. Independent distribution is the default. Standard/Guard use `app.halotv.launcher`, code 220; Lite uses `app.halotv.launcher.lite`, code 76.

## Build

Use JDK 17 and Android SDK 36. Set the local Android SDK location through `local.properties` or your environment. Release signing is private and excluded. For your own builds, create `local/independent-signing.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` pointing to your own keystore; never commit it. A build signed with another key cannot replace an official installation.

```
./gradlew :app:assembleStandardRelease :app:assembleGuardRelease :lite:assembleRelease
./gradlew :app:testStandardDebugUnitTest :app:testGuardDebugUnitTest :lite:testDebugUnitTest --no-parallel --max-workers=1
```

See docs/RELEASE-3.2.3.md for the change and verification scope, and docs/ARCHITECTURE.md for the layout. No TV or emulator was operated for this release. Build/test artifacts, local device configuration and private keys are excluded.

[Website](https://halo-tv.pages.dev/) · [Releases](https://github.com/Null9797/halo-tv/releases)
