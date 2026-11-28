# Phase 248 — Encrypt local mission routes at rest

## Goal and scope

Protect the working mission draft, unsaved recovery copy and saved-route library in app-private Preferences DataStore while preserving existing users' routes. No phone, hardware, caster, map key, network destination, mission command, or `output/` artifact is in scope. This is a local-storage change, not a production release.

## Baseline and design

- Baseline HEAD: `4ec99dc5853fd15f308a6149f87a331df56386e8`.
- Starting worktree already contained uncommitted Phase 247 demo changes and untracked APK/demo `output/` artifacts. Preserve them; this report does not claim they were reviewed or changed.
- Added a versioned AES-GCM envelope using an Android Keystore non-exportable key. The package and logical DataStore slot are authenticated as AAD, preventing ciphertext from being moved between draft, recovery and library slots.
- New writes are encrypted. On a legacy read, the existing codec must decode the value before DataStore atomically replaces it with ciphertext. Any cipher or codec failure aborts the transaction; existing repository/UI error handling leaves the value in place and reports the problem.
- No plaintext shadow, new dependency, permission or network destination was added.

## Data and limits

The `draft`, `working_copy` and `library` DataStore strings retain their existing codec payloads inside the encrypted layer. The repository continues to bound drafts, library entries and encoded values through the existing codecs; the cipher enforces a 4,000,000-byte plaintext ceiling and rejects oversized envelopes before decryption. One versioned string per logical slot keeps reads atomic and avoids a second plaintext copy. Plaintext necessarily exists transiently in the app process while a route is edited or decoded.

## Threat and failure notes

AES-GCM detects ciphertext alteration and slot-swapping. It does not protect against a rooted/compromised OS, runtime inspection, screenshots, exported GeoJSON/GPX files, or an attacker using the running app as a decryption oracle. Android Keystore key loss, app uninstall/data clearing, or a downgrade to a pre-encryption APK can prevent reading migrated routes. There is no rollback shadow by design. Before production distribution, validate encrypted-route export/recovery and compatible rollback on a disposable installation; do not downgrade or clear data as a recovery step.

## Verification

- `:app:testDebugUnitTest`: **passed** after one initial test-source compile error was fixed. `MissionStorageMigrationTest` passed 4 tests, 0 failures/errors/skips. Coverage includes legacy migration of draft/recovery/library, encrypted new writes and readback, recovery clearing, encryption/authentication failure preservation, and invalid legacy preservation.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug`: **passed**. Lint reported 0 errors and 28 warnings. Evidence: `app/build/test-results/testDebugUnitTest/TEST-com.mamadrones.gcs.data.local.datastore.MissionStorageMigrationTest.xml`, `app/build/reports/lint-results-debug.xml`, and the APK outputs under `app/build/outputs/apk/` (ignored build artifacts).
- Android instrumentation source `MissionStorageKeystoreTest` compiles into the Android test APK. Runtime execution is **blocked**: the retained `Pixel_3a_API_34_extension_level_7_x86_64` AVD appeared on ADB but `sys.boot_completed` remained blank after startup plus a 40-second wait. `Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -Adb <adb-path> -QuietSeconds 30` stopped before installation with “Emulator is not booted or cannot be inspected.” No test APK was installed, no data was cleared and no phone or hardware was used.

## Recovery

Source-level inverse changes must be scoped to this phase and reviewed; do not reset the worktree or revert Phase 247. An app downgrade is not a data recovery mechanism after migration. The supported recovery path must be verified route export/import on a compatible build before a production release. No rollback action was executed. The Android Keystore runtime test remains open and the code is not yet production-qualified.

## Owner review

Review the cipher, migration transaction, generated DataStore values, tests, and risks above. Commit and push were not requested for this phase.
