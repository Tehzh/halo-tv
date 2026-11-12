# Control Center and background-cleanup test build

Base: HaloTV 3.0.1 (181). Test versionCode: 182; versionName remains 3.0.1. Guard and Standard only; Lite is unchanged. Not published.

## Changes

- Keep one composed Control Center while Home is active, with a one-off idle warm-up. Hidden controls are unplaced and disabled; no hidden brightness polling or update checks. Release retained content when Home stops or the screensaver starts.
- Restore focus on every opening, not just on first composition. Re-read the glass snapshot for each open/close session so cached text does not retain stale wallpaper colours.
- Replace the two full-screen veils with one draw with the same settled opacity (0.604). Remove the second full-screen Home render-node recording that ran during each opening. Keep native Home graphics layers and the existing short fade/rounded-glass style.
- Manual cleanup no longer interprets a UID-limited process list as proof that no background apps exist. On Android 5–13 it can enumerate launchable user apps and ask ActivityManager to reclaim eligible background processes. No force-stop, package disabling, cache deletion or network-setting changes.
- Preserve system apps, shared UID, observed active apps, VPN service packages, current input method, enabled accessibility/notification services and the default HOME package. Active audio or VPN/unknown network state skips the operation with an explicit reason. Android 14+ reports its platform restriction.
- Manual cleanup is single-flight and distinguishes unsupported, protected, failed, busy, empty and submitted states. The count is submitted requests, not an invented number of killed processes; reported available-memory delta is system-wide. Idle cleanup stays conservative and only uses observed cached processes.

## Validation

Guard and Standard each pass the unit-test suite, including regression coverage for UID-limited process discovery and hidden-panel focus/touch rejection. Release build and signing checks recorded with the delivered APKs.

User explicitly asked to stop operating the television and deliver desktop APKs only. No modified APK has been installed, no emulator has been launched, and no performance improvement percentage is claimed. TV performance and actual firmware cleanup behaviour remain to be tested by the owner. The attempted same-turn live benchmark was not valid (another video app was foreground); its zero-frame outputs must not be used as a baseline.
