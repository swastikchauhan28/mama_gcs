# Phase 60 — landscape screenshot timeout diagnostics

Date: 2026-10-26. Baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`.

## Goal and scope

Investigate Phase 59's intermittent timeout in the landscape workspace screenshot capture. Scope is Android UI test diagnostics and the associated records. The production app, build configuration, dependencies, permissions and hardware flows are unchanged. The pre-existing untracked `desktop.ini` was left alone.

## Path and decision

The test asks `UiAutomation` for a full-window bitmap after a render commit. `StationScreenshotStability` rejects blank station surfaces and requires a sampled top-of-screen signature to remain unchanged for 500 ms before `capture` saves the image. The former timeout did not distinguish a null screenshot from a blank or changing frame. See [D-060-01](DECISIONS.md#d-060-01-diagnose-landscape-screenshot-readiness-before-changing-its-gate).

The test now includes counts for null screenshots, rejected surfaces, changing signatures and unsettled frames in a timeout error. The acceptance predicate and 10-second limit are unchanged. These counts are transient test state, bounded by the number of capture attempts, never persisted in the app, and contain no image or device identifiers. The test writes only its existing local QA screenshots.

## Verification and attempts

- `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: passed, `BUILD SUCCESSFUL in 43s` (7 executed, 53 up-to-date). The temporary init script places generated app build output outside the OneDrive checkout; repository build configuration was not changed.
- `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.RoverWorkspaceUiTest#landscapeDaylightWorkspaceAndCategories'`: JUnit `OK (1 test)` in 43.05 s, but overall failed because Android logged two boot-service ANRs and displayed an error window. Evidence: `app/build/acceptance-20261026-113356-71734470/`.
- After restarting only the disposable SwiftShader emulator and allowing it to boot, the same command passed `OK (1 test)` in 28.265 s with a healthy Android gate. Evidence: `app/build/acceptance-20261026-113807-136cddab/` (`instrumentation.txt`, `android-health.txt`). No physical phone or vehicle was used.
- Visually inspected the generated `station-operate-landscape-light.png`: the wide title, disconnected status, map, instruments and safety footer are visible without clipping. The screenshot is emulator output, not physical hardware evidence.
- `git diff --check`: no whitespace errors; Git reported only line-ending conversion warnings on Windows.

## Result and open risk

The Phase 59 screenshot timeout did not reproduce in the two focused attempts. The diagnostic change is ready to identify its failure mode if it recurs; it is not a proven timing repair. The first attempt's Android ANRs remain recorded and were not reclassified as a pass. The full Android UI suite was not rerun. No Cube/R12/T12 telemetry or RC behavior was verified.

Next: if the timeout recurs, use the new counts and saved Android health evidence to isolate the failing condition before changing the gate. Review this test-only diff; commit and push require a separate user request.
