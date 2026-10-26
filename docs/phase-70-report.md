# Phase 70 — Route search with the Android keyboard

Date: 2026-10-26
Baseline: `6d0665d37e37cf157d5719d33b4781e2d55fcd01`
Scope: local route-library search field and synthetic Android UI test. Hardware remains on hold.

## Goal, finding and change

Check whether saved-route search is usable at 320 dp and 2× text when a real Android keyboard opens. The first test reported `OK (1 test)` and Compose marked the result and Close as displayed, but the full-display capture showed the IME covering both. Compose's app-window semantics did not account for a separate keyboard window. That early pass is not accepted as keyboard-open reachability.

The corrected test then failed because the search field exposed `ImeAction.Default`, so `performImeAction()` had no defined Done action. The app now specifies `ImeAction.Done` and hides the keyboard when Done is pressed. A later capture showed that the separate Clear search row left only the top of the result card visible after Done. Clear was moved into the search field's trailing area. The final capture shows the matching card, Open/Delete actions and Close fully visible after Done. The keyboard still covers results while it is open in this enlarged landscape fixture; users must press Done to view and act on a route.

## Data path and boundaries

The search field still reads and filters the current in-memory `MissionPlanUiState.library` by the existing bounded query. Done calls only the local software keyboard controller; Clear resets that same query. The route list and ID-based Open/Delete callbacks are unchanged. There is no new persistent data, network destination, permission, dependency, configuration, file write, telemetry path or vehicle command. The test uses two synthetic entries and an in-memory callback host. Its `FixtureTestActivity` window is set to adjust-resize within the test; the production `MainActivity` already declares adjust-resize in its manifest.

## Verification and failures retained

- Initial keyboard test build: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL**. Initial test `app/build/acceptance-20261026-163742-abdcab49/` reported `OK (1 test)` and healthy Android, but visual review of `app/build/phase70-library-ime.png` disproved its reachability claim.
- Corrected test build: same command — **BUILD SUCCESSFUL in 2m 32s**. `app/build/acceptance-20261026-164249-6c00d184/` failed at `performImeAction()` because the field reported `ImeAction.Default`; Android health remained good. This is a real test failure, not an environment pass.
- Explicit Done build: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL in 3m 11s**. The single corrected test passed `OK (1 test)` at `app/build/acceptance-20261026-164742-6e61779e/`, but visual review of its after-Done capture showed the card partly clipped by the dialog's small list viewport.
- Final compact-control build: same app-plus-test command — **BUILD SUCCESSFUL in 2m 24s**, `79 actionable tasks: 14 executed, 65 up-to-date`. Both APKs were installed from `%TEMP%\mama-gcs-phase58-app-build\outputs\apk` on disposable `emulator-5554`.
- Final acceptance: `.\scripts\Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb $adbPath -TestClass 'com.mamadrones.gcs.MissionLibrarySearchUiTest'` — **PASS, `OK (2 tests)`**, Android healthy, SwiftShader. It covers filter/clear/reset and ID-based Open/Delete, plus a visibly open IME, Done dismissal, a reachable matching Open action and Close. Evidence: `app/build/acceptance-20261026-165209-9d574a2f/`. `$adbPath` was the SDK `platform-tools\adb.exe` under `%LOCALAPPDATA%`.
- Final full-display images inspected: `app/build/phase70-library-ime-final.png` (keyboard open; results covered) and `app/build/phase70-library-after-done.png` (keyboard hidden; matching card actions and Close fully visible). The fixture uses a 320 dp window in landscape at 2× text. No full suite, lint, real-phone or physical-hardware test was run.

## Risks, review and status

Keyboard-open result interaction is still unavailable in this extreme landscape fixture. The Done key restores access; other IMEs, portrait phones, narrower screens and larger locales need separate evidence. Review `MissionScreen.kt`'s search `OutlinedTextField` and the new keyboard method in `MissionLibrarySearchUiTest.kt`. The current revision is a source baseline, not a production-qualified rollback artifact. A scoped inverse of the Phase 70 field/test/records is reversible; no data migration or hardware action applies. Phases 60–70 remain uncommitted and unpushed; preserve unrelated `desktop.ini`. The owner has not reviewed the diff.
