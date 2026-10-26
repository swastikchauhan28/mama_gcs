# Phase 59 — readable mobile workspace header

Date: 2026-10-26. User requested the next UI phase while hardware diagnosis remains on hold. Exact AI model/version unavailable. Starting revision `3e767a05d1b6e3d20e612376cd5940fb8bc3d46b`; pre-existing untracked `desktop.ini` was preserved. Commit and push were requested separately after UI verification.

## Scope and choice

On a narrow screen, `StationShell` now shows the workspace title beside Menu and puts the existing connection shortcut on the row below. The title can use two lines. The wide layout still presents title and shortcut on the same row. Status wording, shortcut destination, safety banner, content routes and telemetry display are unchanged. The change reads only the existing route and connection display state; no new data is persisted or sent to a transport or vehicle. See [D-059-01](DECISIONS.md#d-059-01-separate-mobile-workspace-title-from-link-shortcut).

The trade-off is a taller mobile header, so less page content fits vertically. The connection shortcut stays at least 48 dp high and the menu/title row at least 52 dp. Future, longer labels or smaller screens may need another layout adjustment.

## Verification

- Fresh command: `./gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`. The temporary untracked init script places app build output outside OneDrive's cloud-placeholder directory; no repository build configuration changed. `BUILD SUCCESSFUL` in 7m 14s, 28 tasks executed and 69 up to date. The JVM report shows **389 tests, 0 failures, 0 ignored**. Both APKs built. Lint reports **0 errors, 28 warnings**.
- Installed the two APKs only on the disposable Pixel 3a API 34 emulator. `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.VisualLayoutRegressionTest#mobileHeaderShowsFullWorkspaceTitleAboveConnectionShortcut'` passed **1/1**, with no fresh crash or ANR and a healthy post-run Android state. Evidence: `app/build/acceptance-20261026-111502-9dc3ae00/`. The test checks full title text without ellipsis at 320 dp / 1.3× text, its position above the shortcut, shortcut navigation, and a 360 dp normal-text case.
- The existing real-activity shortcut test, `ImmersiveWindowTest#connectionSettingsRemainAccessibleInFullscreen`, passed **1/1** with a clean Android health gate. Evidence: `app/build/acceptance-20261026-111538-fa9cf791/`.
- The existing `RoverWorkspaceUiTest#landscapeDaylightWorkspaceAndCategories` **failed** before its category checks: its Android screenshot stability wait timed out at the first capture (`RoverWorkspaceUiTest.kt:133`). No app crash, ANR, or error window was recorded. Preserve the failed evidence: `app/build/acceptance-20261026-112031-4591d4b2/`. This is an unresolved screenshot-capture test result, not a wide-layout pass.
- Added a focused wide-header geometry test without changing the failed screenshot test or its assertions. Rebuilt only `:app:assembleDebugAndroidTest` successfully (7 tasks executed, 53 up to date), then `LandscapeOverlapTest#wideHeaderKeepsTitleAndConnectionOnOneRow` passed **1/1** with a clean Android health gate. It checked the title and shortcut share a row and the vehicle selector remains visible. Evidence: `app/build/acceptance-20261026-112508-c15efae5/`.
- Visually inspected Application settings at normal and 1.3× text size on the disposable emulator. The title and connection label are fully readable, and the safety banner remains visible. Ignored screenshots: `app/build/phase59-settings.png`, `app/build/phase59-settings-large-text.png`. Restored the emulator's text scale to 1.0 afterward and shut it down.

This was targeted UI coverage; the full Android UI suite and physical hardware path were not tested. The screenshots are layout evidence only, not proof of telemetry, authentication, or rover safety.

## Review and recovery

Review `StationShell.kt` for one connection shortcut per layout, unchanged label/destination, and the extra mobile row; review the new mobile and wide geometry assertions without weakening existing tests. No dependency, permission, schema, external destination, credential or vehicle-command path changed. A reviewed inverse patch of this UI/test/documentation diff is the source rollback; there is no app-data or hardware migration. The starting revision is not a production-qualified release.
