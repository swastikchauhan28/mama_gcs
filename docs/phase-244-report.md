# Phase 244: Operate saved field map offline verification

## Scope and decision

The owner approved a software-only, disposable-emulator check of Operate → Browse areas → a persisted saved field pack. D-244-01 records the choice and alternatives. Baseline HEAD `33d1e797b4890e65e99fecf3491c165029f68edd`; Phase 243 test/docs and older `output/` artifacts were already uncommitted and were preserved. No production app code, dependency, permission, schema, or vehicle command path changed.

## Path and data

`OfflineFieldMaps.download` writes one uniquely named, small synthetic four-vertex field to MapLibre app-private offline-region storage using the existing OpenFreeMap source and download budget. After a separate app process starts with Android networking disabled, `MamaGcsApp` navigates from Operate through `MapAreaBrowserScreen` to `SavedPackMapScreen`, which reads the persisted pack and renders it through `VehicleMap`. The working `MissionPlanUiState` remains in memory; the test checks that it is unchanged and that no `MissionPlanAction` callback fires. The test deletes only its `Phase 244 synthetic field` pack in `finally`; offline tiles and screenshots under ignored `app/build/` are test evidence, not operator data.

## Verification, 2026-11-28

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, 97 tasks (9 executed, 88 up-to-date). Existing JVM XML totals 557 tests; `testDebugUnitTest` was UP-TO-DATE. Lint reported 27 warnings, zero errors. After the final test-only pixel assertion, `:app:assembleDebugAndroidTest` rebuilt successfully (60 tasks, 7 executed, 53 up-to-date), and `:app:lintDebug` passed again (34 tasks, 3 executed, 31 up-to-date; zero errors/27 warnings).
- Existing registered `MamaGcs_Phase238_AOSP31` emulator booted without a wipe despite only about 2.9 GB free on C:. The required 30-second `Wait-AndroidEmulatorHealthy.ps1` preflight passed. Debug app and test APK installed with `adb install -r`.
- Online preparation passed, `OK (1 test)`, with a native-complete persisted region. Final preparation evidence: `app/build/acceptance-20261128-103915-b1a8b0c5/`.
- Host set airplane mode on, disabled Wi-Fi/data and verified `Active default network: none`. Final offline navigation/render test passed, `OK (1 test)`; Android post-run health passed with no new crash or ANR. Evidence: `app/build/acceptance-20261128-103937-1ab9b406/`. Networking was restored (`airplane_mode_on=0`). The test removed its synthetic pack.
- `app/build/phase244-operate-saved-field-offline.png` was visually inspected: street tiles and the blue saved-area border render; no rover position is shown. The view correctly says `VIEW NOT FULLY SAVED OFFLINE` because the viewport extends outside this small pack. The test asserted the native map camera contains the pack bounds, boundary layer exists, rendered central map pixels have detail, and the working route/callback remain unchanged.

## Failed attempts retained

The first test APK did not compile because `fetchSemanticsNodeOrNull` was unavailable in this Compose version; the test uses `onAllNodesWithTag(...).fetchSemanticsNodes()` now. The first offline run reached the saved screen but asserted the boundary legend before asynchronous map load; Android health still passed. A subsequent run passed behavioral assertions, but its immediate screenshot showed a blank canvas. The final test waits for actual rendered pixels and retains a screenshot, and the final offline run passed with visible tiles/border. The PowerShell host's first no-network predicate incorrectly applied `-notmatch` to an array of lines; a direct read showed `Active default network: none`, and the corrected joined-string check preceded the final run. No failed run is counted as acceptance.

## Limits and next step

A complete MapLibre pack and visible screenshot do not prove every padded viewport tile is saved, field surveying, rover GPS fix, RTK precision or physical operation. This test uses a synthetic field and no live Cube, phone or caster. The viewport warning is appropriate for the small fixture. Owner diff/release review is still open; this phase is uncommitted and unpushed. Review the focused test and report, then check an actual field and rover position separately under a safe hardware scope when available.
