# Phase 249 — Portable encrypted mission route backup

## Goal and scope

Provide a portable, passphrase-protected file for moving or recovering the local saved draft, current editor state and complete saved-route library. Import must be preview-only until explicit confirmation. No vehicle upload, hardware, network destination, new permission or dependency is in scope.

## Baseline and design

- Baseline HEAD: `cfb469e1281324da6a7893678413ccecac751403`.
- The worktree already contained Phase 247 demo files and ignored/untracked `output/` artifacts. Those are outside this phase and were preserved.
- The backup file is versioned and uses a random 128-bit salt, PBKDF2-HMAC-SHA-256 at a fixed 600,000 iterations and AES-256-GCM. The fixed KDF setting and bounded input are checked before deriving a key; the header is authenticated as AAD.
- Android's Storage Access Framework handles user-selected input/output URIs. No broad storage permission or cloud endpoint is added.
- Restore decrypts and validates all three route stores before presenting a preview. Confirmation writes encrypted draft, recovery and library values in one DataStore edit. A failed edit leaves the existing transaction intact.

## Data and limits

The payload stores the last-saved draft, the current editor draft as an optional recovery copy when it differs from the saved draft, and the entire library (maximum 25 entries). The encrypted file is capped at 4,300,128 bytes; each saved/recovery draft field is capped at 128,000 UTF-8 bytes, library text at 4,000,000 bytes, and passphrases at 12–256 characters. Mutable passphrase, key material, plaintext and selected ciphertext buffers are cleared best-effort. UI passphrases and existing route-codec plaintext intermediates briefly exist as immutable JVM `String` values that cannot be reliably erased.

## Threat and failure notes

Wrong passphrases and authenticated-data/ciphertext tampering fail closed before route data is returned. A forgotten passphrase makes a portable backup unrecoverable. The app/device process can observe routes and the passphrase during use; encryption does not defend a compromised unlocked device, keyboard capture or screenshots. User-selected document providers can sync the encrypted backup to their own cloud storage. A provider write may be interrupted and leave a partial encrypted file; retry the export and verify the completed document by importing it before relying on it. Import and restore never contact a vehicle or issue mission commands.

## Verification

- Final-source command: `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` — **BUILD SUCCESSFUL** in 6m 42s. Test XML totals: 570 tests, 0 failures, 0 errors, 0 skipped. Lint: 0 errors, 28 warnings. The Android workflow test compiled into the test APK. Evidence: `app/build/test-results/testDebugUnitTest/` and `app/build/reports/lint-results-debug.txt`.
- Emulator preflight: retained `Pixel_3a_API_34_extension_level_7_x86_64` booted without wiping and passed `scripts/Wait-AndroidEmulatorHealthy.ps1 -Serial emulator-5554 -QuietSeconds 30`; no new crash/ANR was detected in that window.
- Runtime attempt: focused `:app:connectedDebugAndroidTest` installed the APKs but ADB property calls timed out before a test result appeared. The unresponsive bounded run was stopped; runtime preview/cancel/restore acceptance is **blocked**, not passed. No app data was cleared, and no phone, document provider or physical hardware was used.
- KDF time on Android and a real SAF export/import roundtrip remain unverified. Do not rely on the backup as the only recovery copy until a completed file has been imported successfully.
- Physical-phone, cloud-provider, and real route recovery acceptance are not claimed.

## Recovery

The change is additive. A failed/cancelled restore does not replace stored routes. Recovery from an existing encrypted local store still requires a compatible Phase 248 build; do not downgrade to a pre-Phase-248 APK or clear app data. No rollback action was executed. The backup format is version 1 and has no migration path yet; keep a known-compatible app build for future restores.

## Owner review

Review the binary header/payload bounds, cryptographic flow, destructive restore warning, DataStore atomicity, passphrase handling and verification limits. The owner requested commit and push after verification.
