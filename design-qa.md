# HaloTV static welcome design QA

final result: passed

Scope: preserve the accepted final light welcome composition and replace the timed entrance with an immediately complete static screen. No redesign or new asset generation.

Accepted design: `exec-0b4040cc-cbd9-4bce-972d-428f4dabc9ff.png` (1672×941).
Prior approved native end-state: Desktop `HaloTV-3.3.1-欢迎页动效测试/欢迎页原生绘制-1920x1080.png`.
Current native output: `app/build/reports/welcome-preview/welcome-1920x1080.png`.

Compare current first-frame output with the prior approved static end-state at identical dimensions, alongside the accepted design. Check native typography, shared text baseline, pastel color, source artwork quality, full copy and focused CTA. The host render regression advances five seconds and requires identical pixels; the complete static view must be present from attachment. No emulator or television operation.

Current static first-frame PNG is byte-identical to the previously reviewed final native frame (SHA-256 `226c86bb9d2404264d3799ecbd41e4884c09b1390399807cfbdbe0c36169f4c9`). A new same-input reference/current comparison is saved as Desktop `设计与静态首帧对照.jpg`. Native fonts, spacing, colors, artwork and copy are preserved; no P0/P1/P2 visual drift. The five-second host regression confirms the static first frame remains unchanged at the same viewport. Real TV behavior remains unverified.
