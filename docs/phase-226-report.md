# Phase 226 — Route Files panel in short landscape

## Goal and scope

The user approved making the route Files panel usable at 2× text in a short landscape window and testing real GeoJSON/GPX import and export on a disposable emulator. Baseline `f799757` on `main`; the existing APK/demo deliverables under `output/` were already untracked. No phone, Cube, radio, mission upload, or rover command was involved. See D-226-01. Model/version unavailable.

## Change and observed failures

- At 640 × 320 dp and 2× text, the original Files actions and GPX explanation lived in one oversized lazy-list item. Splitting them into separate items exposed a second cause: the fixed route header consumed the remaining editor height. The final short-landscape layout puts that header inside the scrollable editor, then places the heading, seven actions, and GPX note in their own rows. The waypoint and fence jump offsets include the added rows. Other layouts retain their existing header and grouped actions. File codecs, persistence, permissions, and network behavior did not change.
- Initial test attempts stalled while searching the oversized item or failed to display the note (`app/build/acceptance-20261121-164325-9151d013/`, `acceptance-20261121-165211-0c68c38b/`, `acceptance-20261121-165544-da60e423/`). After the layout fix, the real picker reached both exports and imports, but the test looked for Save draft while scrolled away from its new row (`acceptance-20261121-170029-a139ce70/`); the test now scrolls to it.
- A later 2× run left DocumentsUI open after one Back (`acceptance-20261121-171300-25a19429/`). The first Back can dismiss the picker keyboard, so the test now waits for the app and sends a second Back only if needed. A normal-text repeat then hit an Android input timeout at app startup (`acceptance-20261121-171751-479879e1/`); a no-wipe emulator restart and 30-second health check preceded the successful retry. These failed runs remain recorded.

## Verification

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded. Final `:app:testDebugUnitTest :app:lintDebug` succeeded: 533 JVM tests, zero failures/errors/skips; lint zero errors and 27 warnings.
- Real DocumentsUI acceptance at 2× text in short landscape: `RouteDocumentPickerTest#exportAndImportFromShortLandscapeFilesPanel` passed `OK (1 test)` with healthy Android gate (`app/build/acceptance-20261121-171635-0b73a32f/`). It verified a visible GPX explanation, export cancellation, GeoJSON and GPX saved contents, import cancellation, previews, replacement, and explicit Save draft. `app/build/phase226-files-notice.png` and `app/build/phase226-geojson-details.png` were visually reviewed; both are landscape and readable.
- The same complete landscape flow passed at normal text after a clean emulator restart (`app/build/acceptance-20261121-172100-d78578ff/`). The original portrait 2× picker test passed (`app/build/acceptance-20261121-172158-919382aa/`). These are separate targeted runs, not a full app regression.
- Follow-up completion: the final test scrolls to all seven Files actions in the short 2× landscape layout and requires each to be visible and enabled before exercising the real picker. An initial emulator start showed a System UI ANR, so no test was accepted from that boot. After a no-wipe restart and a passing 30-second health check, the complete action and picker flow passed `OK (1 test)` with a healthy gate (`app/build/acceptance-20261121-173117-5d565aee/`). This follow-up changed only the instrumentation test; the prior app build, JVM and lint results still apply.
- The test restores its previous emulator draft and recovery and removes only its exact generated files. The display overrides and font scale are restored after testing. Evidence remains under ignored `app/build/`.

## Status and limits

The Files panel and real picker flow are usable in the tested 640 × 320 dp, English, Android 31 layouts at 2× and normal text. Other window sizes, languages, and accessibility services remain unverified. This local file workflow does not prove mission upload or physical rover behavior. Owner diff review remains a release gate. Check Git history for the commit and push status.
