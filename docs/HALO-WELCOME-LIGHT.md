# HaloTV static light welcome (local test, 2026-11-17)

The owner replaced the previously accepted animation with a static welcome. The accepted pearl/lilac composition, handwriting, real brand icon, halo/star/horizon, localized text and left-aligned footer stay intact.

- Shared native static view for Standard, Guard and API-14 Lite, rendered on the same uniformly scaled 960×540 canvas. The complete image is visible immediately; no animator, entrance/reveal/fade, timer, animated skip or scheduled invalidation remains.
- One deliberate confirmation enters Home immediately. Orphan key-up is ignored. Detaching frees all three decoded artworks. Text remains native/localized and accessible instead of rasterizing translations.
- Retain `first_run_guide/welcome_light_completed`. The later local upgrade-entry fix also accepts this installation's completed `welcome3_completed` so an artwork revision cannot interrupt a restored guard with another confirmation. Fresh installs and unfinished welcomes still see the accepted static page once; the old 2.x `finished` flag alone does not skip it. Future normal upgrades do not reset completion.
- Visible 3.3.1, full code 240 / Lite 96, local tests above animated 239/95 and public 238/94. Preserve independent signer/IDs, app icons, launch art and remote sources. No publication, TV, emulator or remote announcement requested.
- Desktop target: `/Users/bilinggg/Desktop/HaloTV-3.3.1-静态欢迎页测试`. Verification records the actual builds, focused welcome regressions, lint, signatures, ZIP/alignment/assets and host-render comparison. Prior animated QA and test counts are historical.

Verified: all three builds and 11 focused regressions pass; lint Error/Fatal 0 (349/350/440 warnings remain); identity/signer/ZIP/alignment/dex metadata/artwork checks pass. Static first-frame pixel comparison and five-second stability regression pass. No real TV or emulator used.
