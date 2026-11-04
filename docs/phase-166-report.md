# Phase 166 — field coverage recovery by editing a vertex

Date: 2026-11-04. User approved Phase 166 and disposable-emulator testing. Clean baseline `42ba0e5d3413e99fe296170c4341b80178f47492`; model/version unavailable. Decision D-166-01.

## Scope and test path

Added a focused real-activity test, without changing production code. A temporary synthetic field is previewed; vertex 2 is edited so the outline crosses itself; the test checks the old preview clears and the geometry error appears. It edits the vertex again to correct the field, checks the error/preview state clears, regenerates a preview, saves and reopens the draft. Saved-draft equality checks include route points, vertex order and stable IDs. The previous disposable draft and recovery are restored in `finally` and compared afterward; interruption can prevent cleanup. No phone or physical vehicle was involved. This is a local planning model, not evidence of actual coverage.

## Verification

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL; 88 tasks, 10 executed, 78 up-to-date. Lint: 0 errors, 27 warnings. JVM tests were not run in this phase; the previous phase's results remain historical.
- Existing disposable emulator image booted, then passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30` (no new crash/ANR during observation).
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#editingCrossingVertexRecoversCoverageAndPersists'`: `OK (1 test)` in 59.008 seconds; Android healthy. Evidence `app/build/acceptance-20261104-143003-5d5a659b/` and `app/build/phase166/test.txt` (ignored local build output).
- Reviewed `app/build/phase166/crossing.png` showing the error and `corrected.png` showing the resulting 13-pass preview summary. The test asserted the diagram was displayed; the corrected screenshot does not show the whole diagram. This is one normal-size emulator configuration, not a full accessibility review.

## Remaining and review

Review the synthetic coordinate changes, geometry-error assertion and persistence/cleanup checks. The test covers one scenario, not a full Android regression or physical field coverage. Prior emulator startup ANRs and the Cube heartbeat issue remain unresolved. Owner release review and a validated rollback remain outstanding; a reviewed inverse source change is the test-only rollback approach. Commit/push require explicit authorization.
