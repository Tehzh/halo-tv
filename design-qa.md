# Welcome screen visual QA

final result: passed

## Target and evidence

- Exact selected first visual: exec-a9105188-5c07-4aa5-a262-508fc2f7e4ab.png.
- Native release captures: local/qa/welcome-standard-final.png and local/qa/welcome-lite-final.png.
- Combined normalized comparison inspected: local/qa/welcome-comparison-final.jpg (all screens normalized to the same 960×540 viewport).
- Guard was launched and inspected before the final typography polish; final Guard and Standard share the same welcome implementation, packaged artwork and strings, with only manifest HOME declaration differing.

## Iterations

1. Initial native comparison found system force-dark in Lite darkened text/artwork until they became unreadable (P1). Disabled platform auto-darkening on the welcome View only, guarded at API 29. The recaptured Lite screen matches the full app.
2. Matched headline width, tagline and credit sizes more closely to the selected target and strengthened the primary-button halo. Recaptured both final release screens and compared them against the target in a single combined image.
3. No remaining P0/P1/P2 visual differences. P3: native font glyph metrics and the generated background/button glow differ slightly from the raster mockup; strings, positions, palette, overall composition and primary-button geometry are retained.

## Behavior and compatibility

- Old finished-guide flag does not suppress the new welcome; verified by unit test.
- Primary action persists the new completion flag; repeated actions execute completion once; tested.
- A key release that originated on a previous screen cannot dismiss the welcome; tested.
- Native release start and restart verified in an isolated Android TV emulator; user TV was not modified.
- No continuous welcome animation, no decoder, no timer. Artwork released on detach.
- Phone online preview action test: Bing = preview/apply; video = preview/useVideo. No save/download action is created.
- Server source no longer imports online images/videos into the user's local library, even when an old page sends download=true.
- All three release APKs built, lint has zero errors, welcome tests 3/3 and existing Lite tests 7/7 passed.
- Lite minSdk remains 14, V1 signature retained. Device testing on physical Android 4.x hardware is not available.
