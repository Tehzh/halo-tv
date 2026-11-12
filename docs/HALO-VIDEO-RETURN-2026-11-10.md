# Halo TV 3.0 video return test build — 2026-11-10

Scope: Guard and Standard only; Lite has no video wallpaper. Visible version 3.0, internal code 175. Built on 302bacc, retaining opt-in automatic update downloads and single/local screensaver selection.

## Changes

- Decoder cleanup runs synchronously in lifecycle ON_STOP. It does not depend on a subsequent Compose frame, which is unavailable once the Activity is stopped. ON_PAUSE alone still permits a short page or system-dialog transition to reuse the existing player.
- Track actual rendered-surface readiness independently from isPlaying. HomeBackground cannot expose an empty video surface just because its previous cover value was one. A pause retains a valid frame; release, surface loss and decoder errors invalidate it.
- Local-video return cancels stale software-only frame catch-up rather than waiting up to 3 s for a MediaMetadataRetriever and another 1.5 s for catch-up. Removed the local wallpaper hardware retriever fallback entirely; it could contend with the next app or jam vendor extractors. PixelCopy, cancellable software keyframes and the current video's poster remain.
- Online catch-up no longer runs in a non-cancellable block: it checks current selection, current media ID and cover state again after frame preparation, rejecting late frames from a previous online video.
- Epoch-tag local poster/catch-up work so a late result cannot replace a newer video's background or a restarted player. Released-player callbacks cannot modify current playback state.
- Retain the same wallpaper's prepared glass during player release/restart; invalidate pending samples, clear the retained glass on explicit wallpaper selection. Delay glass sampling 1.2 s after playback resumes, allowing page transitions and first-frame upload to settle.
- Stop writing every 320×180 glass sample back into the video's poster. That caused repeated JPEG/disk work and could replace a high-quality uploaded cover with a tiny moving preview. One-time poster capture remains for uploads that lack one.
- Local wallpaper read-ahead is bounded to 24 MiB with a 2–8 s buffer and 250/750 ms startup/rebuffer thresholds. Online streaming keeps its existing separate network buffering policy.
- Screensaver carry detection checks a real rendered frame, not just a leftover cover value.

## Verification and limits

Build both release variants, Standard unit tests and Standard lint. Added epoch-race regression tests and lifecycle observer tests that verify release without another UI frame. No emulator or real TV was used, installed or modified; no publication. These changes require the owner's real-device testing, particularly rapid Home/Back, local/online video, screensaver handoff and modal settings. Vendor display-mode changes and unavailable network streams may still produce an interruption; this build does not keep a competing video decoder running behind other apps.
