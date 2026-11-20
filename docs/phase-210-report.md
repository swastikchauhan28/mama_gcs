# Phase 210 — Accessible field-outline vertex removal

## Goal and baseline

The user approved a bounded software-only field-outline accessibility phase on 2026-11-20. Baseline `37faf36`, clean `main`; model/version unavailable. RC and Cube work remained paused. The outline's Edit and Move buttons already named their vertex, while each Remove button had only a generic name and immediately dispatched a local removal. See D-210-01.

## Change and execution path

`MissionScreen` reads the current local `MissionDraft.keepInFence` list (at most 64 vertices). Each row's Remove button now exposes its one-based vertex number to accessibility services and opens a volatile confirmation containing the target's stable ID and a snapshot of the full working draft. Cancel dispatches nothing. Confirmation is enabled only while the draft is still equal to the snapshot, editable and contains that ID; the click handler checks again before sending the existing `MissionPlanAction.RemoveFenceVertex`. The view model's existing local draft/recovery path performs the mutation and invalidates the volatile coverage preview; the saved draft changes only after an explicit Save draft. The dialog closes on rotation because its pending snapshot is not saveable, which fails closed. No network, dependency, permission, account, telemetry or vehicle command path changed. The UI guard does not constrain direct internal action callers or authenticate a user. See [security boundaries](security.md).

The prior real-activity crossing-outline test expected immediate removal; it now confirms the dialog. A new 2x-font synthetic Compose test checks the numbered action descriptions and native Android accessibility order, confirms cancel and changed-draft guards dispatch nothing, and verifies one explicit remove action. It stores screenshots under ignored `app/build/` through the disposable emulator's test files.

## Verification

- `git diff --check` passed before the final documentation update; Git emitted only LF/CRLF conversion warnings. No new production dependency, destination, credential or permission was introduced. The full diff and changed-file list were reviewed for scope.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — `BUILD SUCCESSFUL` in 6m 21s. JVM XML: 533 tests in 84 suites, zero failures/errors/skips; debug app/test APKs built; lint XML: zero errors, 27 warnings. This built the tested code before the final comment-only clarification.
- The previously created disposable AVD restarted with an `Application Not Responding: com.android.systemui` dialog before testing. Local ignored logs: `app/build/phase210-emulator-first-boot-crash.txt` and `phase210-emulator-first-boot-events.txt`. It was stopped without wiping. A new empty `MamaGcs_Phase210_API34` AVD from the installed API 34 image had first-run Google service ANRs during its first preflight; these were preserved in `app/build/phase210-fresh-avd-initial-events.txt`. A later 30-second quiet preflight passed. No error dialog was dismissed or test assertion weakened.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageUiTest#removingOutlineVertexHasNativeNameAndRequiresCurrentDraftConfirmationAtLargeText'` — `OK (1 test)` and Android health gate passed with zero new crash/ANR events. Evidence: `app/build/acceptance-20261120-120814-3bc6aff9/`.
- `scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -TestClass 'com.mamadrones.gcs.FieldCoverageAppTest#fieldEditorRecoversFromCrossingOutlineAndKeepsRoute'` — `OK (1 test)` and Android health gate passed with zero new crash/ANR events. The test used a disposable saved draft and restored its prior saved state. Evidence: `app/build/acceptance-20261120-121016-04280282/`.
- Visual inspection of `app/build/phase210-outline-actions.png` and `phase210-outline-remove-confirm.png` at 2x font showed the row controls, numbered target, warning text, Cancel and Remove vertex legible. Synthetic fixture coordinates are local test data, not a real field.

## Remaining limits and recovery

The native Android tree assertion verifies visible action names and traversal order, but spoken TalkBack or Switch Access on a physical phone remains untested. A confirmation reduces accidental working-draft removal; it cannot prove agricultural coverage, vehicle safety or saved-route recovery. Owner diff review and the broader release gate remain open. Source rollback would review and reverse only the Phase 210 diff from `37faf36`, rebuild and rerun these checks; no rollback or production-qualified artifact was verified. Commit/push only on request.
