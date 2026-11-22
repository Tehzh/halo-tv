# HaloTV 3.2.4 independent-only integration

## Scope

Official Standard, Guard and Lite release, promoted from the reviewed test build. Runtime code is unchanged from that test; only release identity codes are increased. The installed identity remains `app.halotv.launcher` (Standard / Guard) and `app.halotv.launcher.lite` (Lite), with the existing independent release signing certificate. Official internal version codes are 222 and 78. Public test codes were 221 and 77. The user requested an official GitHub and website publication, including the independent online-update feed. No TV or emulator operations, artwork replacement or new remote announcements.

## Verified upstream

The three Desktop Aerial APKs match the SHA-256 digests in the official v1.98 release. Standard and Guard report 1.98 / 144. Upstream explicitly kept Lite at 1.30 / 32. Source is the official v1.98 tag, compared against the already integrated v1.97 base. Existing HaloTV interfaces and features are retained; this is not an upstream APK with renamed branding.

Source: https://github.com/pengchujin/aerial-tv/releases/tag/v1.98

Integrated runtime changes include the foreground activity settle window, XGIMI locked-boot dialog handling, wake-related keep_adb timing, Dangbei shutdown and post-update handling, corrected Dangbei recent-apps targeting, Dangbei / XGIMI / Xiaomi input mappings, Huawei settings deep links, TCL Google TV backlight discovery, and privacy-preserving weather failure diagnostics. Control Center already had a guarded source refresh job; it now also refreshes once when opening the main panel.

## Removed compatibility paths

- Cross-package configuration export / import provider and activities.
- Transition-build configuration, package/signature switches, update-feed selection and legacy identity pins.
- Migration review UI, phone-page transition-upload guidance, data-transfer prompts, guard takeover / yielding state and startup redirection.
- Obsolete base account-type values; both base and independent resources now use the current installation identity.
- Cross-package APK install, launch and update-return logic.
- Migration gates in boot recovery, notification listener, children mode and quiz overlays.

Existing independent-app preferences and files remain intact. Stale migration preferences are no longer read; they cannot suppress startup or key handling. Package namespaces in Kotlin, resource names and upstream attribution are retained because they are not Android installation identities or migration features.

Online and phone-pushed updates now share `ReleaseIdentity`: same installed package, pinned independent signer, same Standard / Guard / Lite edition, compatible Android level, and strictly higher internal version. The website's existing independent update feed URL is retained and its release metadata is updated to 3.2.4.

## Additional integration fixes

- A vendor shutdown temporarily suspends automatic recovery until SCREEN_ON (or a new process), without changing the user's saved recovery preference. This prevents HaloTV's boot recovery from undoing upstream's shutdown safety path.
- A delayed Android 5 service rescan no longer overwrites an explicit component-state change made while it was pending.
- Separate photo / clock / online-photo savers release the moving-wallpaper player instead of retaining its paused video layer underneath. A saver carrying the same film still uses the continuous handoff; Control Center and ordinary desktop-panel behavior are preserved.
- Lite shared generated sources/resources use Sync, so deleted migration classes and strings cannot leak into a later incremental build.

## Validation boundary

Host release builds, unit tests, Android lint, APK identities/signatures, manifest entry points, resources, ZIP integrity and alignment are checked before Desktop delivery. Host tests do not establish real-TV frame rate or eliminate vendor compositor / firmware differences. The previous report of a Changhong 4124 key is not blindly mapped to Home: it needs the actual event code / scan code and model evidence.

## Test build host checks (2026-11-15)

Final release build, unit-test and lint pipeline: BUILD SUCCESSFUL. Standard and Guard each passed 466 tests; Lite passed 148 (1,080 executions, zero failures/errors/skips). Lint has zero Error/Fatal findings, with 322 / 323 / 414 warnings and 18 / 18 / 0 hints respectively. JavaScript syntax, source migration scan and git diff whitespace checks passed.

All three signed APKs passed v1/v2 verification with the existing independent certificate, ZIP integrity and zipalign checks. Installed package names, edition metadata, API levels and internal codes were verified. No migration manifest or DEX entries remain; bundled phone assets match the cleaned source. Full builds contain both dex metadata assets with the current package and version code. Standard is 7,527,203 bytes; Guard 7,526,768 bytes; Lite 2,297,055 bytes.

Desktop delivery: `/Users/bilinggg/Desktop/HaloTV-3.2.4-测试版`, including APKs, hashes, package/test audit JSON and Chinese test notes. No TV/emulator runs. The official release increases codes to 222 / 78 so these test installations can update. Official package hashes and verification are distributed with the release.
