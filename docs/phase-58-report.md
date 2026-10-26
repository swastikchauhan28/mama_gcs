# Phase 58 — reveal the selected mobile workspace tab

Date: 2026-10-26. User requested UI work while hardware diagnosis is on hold. Exact AI model/version unavailable. Starting revision `f241ba13636e9a626f1973d5f7a3f75d1a520b92`; pre-existing untracked `desktop.ini` was left untouched. Commit and push were requested separately after the UI verification.

## Scope and behavior

On a narrow screen, `StationShell` keeps its existing horizontally scrollable category row. A route change now requests that the selected chip be brought into view. The effect is keyed to the route, so ordinary recomposition and telemetry updates do not reset manual scrolling. The wide-screen navigation drawer, page content, persistence, Bluetooth/UDP links, vehicle commands, permissions and hardware configuration are unchanged. The local route string is the only input to this change; the requester and scroll offset are transient UI state.

Decision and alternative: [D-058-01](DECISIONS.md#d-058-01-reveal-the-selected-mobile-workspace-tab). The main visual trade-off is a tab-strip jump when navigation changes pages. The selected label might still exceed the strip on a narrower or larger-text configuration than checked here.

## Verification

- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` passed from the normal checkout. The first combined `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` run stopped before tests because OneDrive had converted all 352 generated kapt incremental files to reparse points; Gradle could not snapshot one generated `.class` file. This was an environment failure, not a source-test result.
- Re-ran those same four tasks with a temporary, untracked Gradle init script setting only the app's build output under `%TEMP%/mama-gcs-phase58-app-build`. `BUILD SUCCESSFUL`; the fresh JVM report shows **389 tests, 0 failures, 0 ignored**. Debug app and Android test APKs built. Lint reported **0 errors, 28 warnings**. The repository's build configuration was not edited.
- Installed those APKs only on the disposable Pixel 3a API 34 emulator using `adb -s emulator-5554 install -r`. The focused command was `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#selectedMobileWorkspaceTabIsRevealedAfterNavigation'`. Its first JUnit result passed `OK (1 test)`, but the acceptance gate failed because four fresh ANR events from Android boot services appeared during that run. No Mama GCS crash or error window was recorded. Evidence: `app/build/acceptance-20261026-105255-8bfd288b/`.
- After Android finished booting, the identical focused run passed `OK (1 test)` with zero fresh crash/ANR events, boot complete and no error window. Evidence: `app/build/acceptance-20261026-105430-a3b0c2ee/`. The runner could not identify the emulator GPU option from its process command line and recorded `unavailable`; the launch command requested `swiftshader_indirect`.
- Visually inspected the selected “Accounts & audit” chip on the disposable emulator at normal and 1.3× system text size. It was fully inside the tab strip in both captures: `app/build/phase58-admin.png` and `app/build/phase58-admin-large-text.png`. Restored system text size to 1.0 afterward. The focused test also checked “RC & PWM” and that manual scrolling to another tab remains available.

The screen test is a UI fixture; it does not establish physical Cube, R12, T12 or rover behavior. The full Android UI suite was not rerun for this narrow change. The Android boot-service ANRs remain recorded as a failed first acceptance attempt.

## Review and recovery

Review the selected-tab requester and route-keyed effect in `StationShell.kt`, the focused bounds assertion in `VisualLayoutRegressionTest.kt`, and the absence of transport or safety-boundary changes. No dependency, destination, permission, schema or credential was added. To reverse the change, review and revert only this UI/test/documentation diff; no data migration or hardware rollback is needed. Do not treat the starting revision as a verified production release.
