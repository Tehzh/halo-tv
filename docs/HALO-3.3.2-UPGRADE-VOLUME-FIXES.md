# Upgrade entry and held volume-key fixes (local test)

## Scope and evidence

Two owner-selected feedback reports were read without changing their status or replying to
their authors. Raw records and the private attachment remain outside the repository.

- TCL t982_ar31a8 / Android 11 / Guard 3.3.2: diagnostics already show the guard configured,
  enabled and bound before the confirmation press. This is not evidence that every upgrade
  revokes accessibility permission. The current welcome's new completion key ignores the
  installation's previously completed 3.x welcome, so an artwork revision can cover Home with
  another confirmation screen even though guard recovery succeeded. The report does not
  establish the cause of every historical upgrade failure.
- Xiaomi MiTV-MFTP0 / Android 9 / Guard 3.3.2: reported held volume keys not adjusting. Source
  inspection found that repeat-down events cancel guard-generated repeats and are consumed
  on the assumption that firmware applied each one. Home's window also drops every repeat
  after an initial firmware-handled down. Neither assumption holds for a driver which changes
  volume on the first down only. The submitted log has no volume-key sequence, so actual
  firmware behavior still needs the owner's device test.

## Changes

- Carry this installation's `welcome3_completed` into `welcome_light_completed`. Completed
  light welcomes stay completed. A fresh install, an unfinished welcome, or only the old 2.x
  guide flag still displays the accepted static welcome. No external package import, package
  migration, permission grant, artwork change, timer or new onboarding screen.
- For actual repeat events of a consumed volume press, compare the music level/mute state
  with the preceding event. Allow 300 ms for late firmware handling before replacing an
  unmoved step. Settle pending real events when faster repeats arrive. Firmware-handled
  events are not deliberately stepped again.
- Keep existing firmware-specific synthetic-held-key eligibility and its 8-second cap. The
  new fallback has no repeat timer: it can supply only steps backed by actual repeat events.
  Pass key-up through, reject repeats of a released/older press, preserve missing-lift
  protection, and cancel pending work when Home pauses or the guard unbinds/dies.
- Log whether first-use confirmation is pending when full Home is created, to distinguish
  an onboarding screen from actual service recovery failure in later reports.

Android's key-filter contract describes consumed events and warns about preserving valid
down/up streams; repeat counts describe held downs, not whether firmware adjusted audio:
[AccessibilityService.onKeyEvent](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)),
[KeyEvent](https://developer.android.com/reference/android/view/KeyEvent).

## Delivery and validation

Local test only: visible 3.3.2, full code 242 / Lite 98, same independent IDs/signer. Standard
and Guard share both fixes; Lite shares welcome completion handling and keeps its native
volume path. No TV/emulator operation, publication, update-feed change or remote announcement.

Regression coverage includes prior completion, fresh/unfinished installs, firmware-handled
and late-handled repeats, unhandled holds in both key-filter and Home-window paths, volume
down, quick repeats, key-up/stale events, bounded learned holds, missing-lift protection and
unbind cleanup. Desktop verification records the final executed checks and package hashes.
Host tests cannot establish physical OEM remote behavior or that a revoked permission was
regranted; the user's TV review remains necessary.

Final host verification: all three Release builds passed; Standard 544, Guard 544 and Lite
182 tests passed (1,270 total, zero failures/errors/skips). All three pre-fix regression
checks failed against the original sources and passed after repair. Lint Error/Fatal 0;
warnings 350/351/440 remain. The additional UseKtx suggestion is on the native welcome
shared with API-14 Lite, where AndroidX is intentionally unavailable. APK identities,
version codes, minSdk, independent signer, Lite v1 signing, ZIP/alignment, unchanged shared
artwork/notices and full-build dex metadata passed verification. No OEM-device validation.
