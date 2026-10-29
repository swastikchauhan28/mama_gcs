# Phase 116 — Previously excluded emulator tests (incomplete)

2026-10-29. User approved running the eleven previously excluded data-changing checks on a disposable emulator. Baseline `428790493292956c817931453303c7d9fe444696`, clean; model/version unavailable. See D-116-01.

## Inventory and boundaries

Reconciled declared methods with preserved Phase 112 phone instrumentation logs. The eleven unobserved methods are:

1. `FieldCoverageAppTest#realPlannerShowsCoverageInPortraitAndLandscapeWithoutSavingIt`
2. `LocalRepositoryTest#udpEndpointPersistsAndCanBeClearedWithoutChangingTheme`
3. `LocalRepositoryTest#libraryPersistsIndependentCopiesRejectsDuplicateNamesAndDeletesOnlySelectedEntry`
4. `MissionExchangeTest#recoveryCopyDoesNotReplaceSavedRouteAndCommitClearsIt`
5. `MissionLibraryUiTest#libraryCopyOpenDeleteAndNewDraftRespectConfirmationAndExplicitSave`
6. `MissionMapDialogTest#mapSelectionAndLongPressRequireConfirmationAndExplicitSave`
7. `MissionPlanningTest#editReorderAndSaveDraftSurvivesNewViewModel`
8. `RouteDocumentPickerTest#exportBothFormatsAndImportThroughSystemPickerWithCancellation`
9. `RoverWorkspaceUiTest#landscapeDaylightWorkspaceAndCategories`
10. `SettingsPersistenceTest#basemapSurvivesRecreationAndDoesNotChangeEndpointOrTheme`
11. `SettingsPersistenceTest#themeIsSavedAndSurvivesActivityRecreation`

These methods can temporarily overwrite emulator drafts/preferences; the picker test creates and removes uniquely named files. Some map flows may contact the existing map provider. Physical BLE tests and the populated phone were excluded. No app or test source was changed.

## Actual results

- `gradlew.bat -I "%TEMP%\mama-gcs-phase58-local-build.gradle" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain`: BUILD SUCCESSFUL, all 79 tasks up to date. This was not a fresh compilation or test run.
- Previously identified disposable Pixel API 34 AVD, `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-window -memory 1536 -cores 2`: System UI ANR during startup. No tests or installs. Evidence `app/build/phase116-emulator-20261029/first-boot-anr.txt`.
- Clean no-wipe retry with 2048 MB: System UI, phone and messaging ANRs; the System UI ANR dialog appeared. No tests or installs. Evidence `retry-boot-anr.txt` and `retry-window.txt` in the same ignored directory. Neither dialog was dismissed.
- The only installed system image is API 34 Google APIs x86_64. The installed legacy `avdmanager` could not run with Java 17 because JAXB was missing; no dependency was downloaded. Created a separate empty AVD under the ignored build directory using a copy of the existing device configuration (no app-data copy), and launched the same image with 2048 MB. It reached the launcher but recorded Google Play Services startup ANRs. Both current APKs were installed only on this fresh AVD. Before instrumentation, `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass "com.mamadrones.gcs.LocalRepositoryTest#udpEndpointPersistsAndCanBeClearedWithoutChangingTheme,com.mamadrones.gcs.LocalRepositoryTest#libraryPersistsIndependentCopiesRejectsDuplicateNamesAndDeletesOnlySelectedEntry,com.mamadrones.gcs.SettingsPersistenceTest#basemapSurvivesRecreationAndDoesNotChangeEndpointOrTheme,com.mamadrones.gcs.SettingsPersistenceTest#themeIsSavedAndSurvivesActivityRecreation"` refused to run because an Android system ANR dialog was present. `fresh-gate-window.txt` and `fresh-gate-anr.txt` record the system integrity-verification and Google services ANRs. No tests started; zero of eleven passed, failed, or were skipped by instrumentation. The three task-started emulators were stopped without wiping data or dismissing dialogs. The new AVD directory and synthetic installation remain in ignored build output.

## Remaining

Phase 116 remains incomplete. Restore a stable emulator environment and rerun all eleven exact methods with the existing health-gated runner; preserve any failed runs and confirm runtime test counts, assumptions and the Android health result. Do not use the user's phone for these mutating tests without a new decision about data. The full Android suite, intermittent menu failure, physical Cube heartbeat and production release gates remain unverified. This baseline is not a validated release. Commit/push require separate authorization.


## 2026-10-29 follow-up — Eleven tests passed after emulator resource tuning

The user requested an emulator repair and rerun. The host reported WHPX usable and approximately 5 GB free at investigation. The same synthetic `MamaPhase116` AVD was started with `-gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-window -memory 3072 -cores 4`; no Gradle build ran concurrently. It reached the launcher with no startup ANR observed in the checked event buffer. No Android service, test assertion, health gate, application code or phone data was changed. The earlier emulator failures above remain part of the evidence. This is a successful configuration for one run, not proof that resource starvation was the root cause or that the AVD will always be stable. See D-116-02.

The exact eleven methods listed above ran in four disjoint groups through `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass <comma-separated methods>`. Each runner produced a success result and passed its post-run Android health gate, which observed zero new crash lines, zero new ANR events and no error dialog. Runtime logs show 4, 3, 2 and 2 successful tests respectively, with no assumption-failure or ignored-test status codes. Evidence directories under `app/build/` are ignored and can be lost on clean:

- `acceptance-20261029-173037-d4f77279`: both LocalRepository and both SettingsPersistence methods (4).
- `acceptance-20261029-173135-47c65a8a`: MissionExchange, MissionPlanning and MissionMapDialog (3).
- `acceptance-20261029-173409-55bdca36`: MissionLibraryUi and RouteDocumentPicker (2).
- `acceptance-20261029-173624-321dfa08`: FieldCoverageApp and RoverWorkspaceUi (2).

The settings landscape screenshot was visually reviewed and had a readable header. The mission landscape screenshot was obscured by Android's first-use full-screen education overlay, so it does not establish visual quality for that screen. The tests' UI assertions still passed. Screenshots are in `phase116-emulator-20261029/` under ignored build output. The task-started emulator was stopped after the tests without wiping data.

The eleven previously excluded checks now have current emulator passes. This was targeted coverage, not a fresh complete Android regression; the intermittent menu failure, physical Cube heartbeat and release gates remain open. No commit or push was requested.
