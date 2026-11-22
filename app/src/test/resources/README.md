# Host test media

`local-sdr-test.mp4` is an original synthetic 128×72, six-frame 8-bit AVC SDR gradient. It was created with macOS AVFoundation using `tools/fixtures/generate-local-sdr.swift`; no third-party video or device recording is used. Regenerate from the repository root with:

```sh
swift tools/fixtures/generate-local-sdr.swift app/src/test/resources/local-sdr-test.mp4
```

It exercises real MP4 metadata extraction in `LocalVideoSurfacePolicyTest`, not Android hardware decoding, GPU composition or TV playback. Test resources are not packaged into the application APK.
