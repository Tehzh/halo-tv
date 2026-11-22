# HaloTV 3.2.8 local test: performance and system registrations

## Scope

Owner requested local APKs after the 2026-11-16 TCL measurements. Standard/Guard
code 230, Lite code 86; preserve independent application IDs, release signer and
all artwork. Do not publish, send notices, install on the TV, or run an emulator.
These codes must be exceeded by the next public build.

## Evidence and changes

The installed 3.2.7 Guard on the owner's TCL reported approximately 200 MiB PSS
with a still wallpaper and 225–263 MiB with the original 4K HEVC aerial. The
important problems were transition-time slow frames, not exhausted RAM: returning
from another app, expanding Control Center, and settings navigation. Measurements
were collected before these changes; they do not prove the new APK's frame times.

- Move MainActivity's secure-settings/account recovery Binder work off its first
  returning UI frame. Existing guard authorization, recovery policy and backoff
  remain in place.
- Keep Control Center composition and effect-mode framebuffer between app/saver
  round trips. Hidden content is unplaced, unfocusable and does not poll brightness
  or inputs repeatedly. Wallpaper changes still refresh its glass snapshot.
- Replace the 64-entry artwork caches with more reusable entries plus byte limits:
  full 40 MiB (12 MiB reduced-effects), Lite 8 MiB. Artwork generation no longer
  holds the shared cache lock. Low-memory callbacks drop cache references without
  recycling bitmaps that live views/render lists might still use.
- Online wallpaper parks its stopped player/playlist when leaving for an app;
  the hardware decoder and read-ahead are stopped. Reuse avoids rebuilding the
  player/playlist on every return; parked local and online players expire after a
  minute away. Screensaver format handoffs and source/quality changes still release.
- Wallpaper-only seeks use CLOSEST_SYNC to avoid exact-GOP preroll. This can resume
  at a nearby keyframe; normal playback speed and screensaver seek policy are
  unchanged. First-frame readiness is reset on stop. Online startup has a bounded
  first-frame timeout/retry and retains the native still cover until a real frame.

## What the old package means

The installed package is `app.halotv.launcher`; classes intentionally remain in
the `app.orchard.tv` namespace. Thus the correct component is
`app.halotv.launcher/app.orchard.tv.guard.HomeGuardService`, not
`app.halotv.launcher/.guard.HomeGuardService`.

Android persists system component selections independently of app preferences.
Installing a different package neither retargets those selections nor transfers
its UID permissions. The owner's system dream still pointed at the uninstalled
`app.orchard.tv` package; the independent guard was actually running during the
measurement, so this is not proof that every reported guard failure has this cause.

`SystemRegistrationRepair` works only after unlock with WRITE_SECURE_SETTINGS:

- Remove only recognized orphan legacy guard/listener registrations when the old
  package is absent. Preserve installed Aerial, unknown components and other apps.
- Remove known invalid same-package class aliases written by package-substitution
  scripts. A previously configured same-package guard is recoverable, but an
  explicit recovery opt-out takes precedence. Legacy grants are never adopted.
- Replace an orphan selected dream with the installed independent dream and save
  ownership for later OEM resets. Respect System and Never choices, disabled
  system dreams, and lists containing another selected dream.
- Repair an orphan vendor `boot_app_componentname` only when independent guard
  recovery was chosen. Never replace another app/input or change Android's HOME
  role, uninstall an app, grant notification access, or turn global accessibility
  on merely because a legacy registration existed.

Without the new package's permission, repair cannot write system settings; the
user must authorize the independent package. OEM autostart, restricted settings,
force-stop and unavailable accessibility APIs still require device-specific
diagnosis. Inherited upstream Aerial installer/restore scripts are not independent
HaloTV installers; use the independent `tools/halo-guard.sh` tool for guard setup.

## Validation

Run all three Release builds, Standard/Guard/Lite host tests and all three Lints.
New regression cases cover orphan/installed legacy packages, unrelated services,
missing permission, locked storage, explicit opt-out, malformed own components,
OEM dream resets, vendor boot entries, and Never/System screensaver choices.
Record actual totals, package identities, signing certificates, ZIP/alignment and
dex-metadata checks in the Desktop delivery's verification file.

No before/after firmware performance, HOME-key, reboot or system-dream validation
is claimed for this local test build. The owner will test on their TV.
