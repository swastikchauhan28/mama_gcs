# Phase 50 — radio-only RADIO_STATUS diagnostics

Date: 2026-10-23. User approved this bounded software phase. Starting source revision: `7535d0d17b1c088b82b823e5fddec2a44749a730` on `main`. At start, `docs/HANDOVER.md` and `docs/test-report-2026-10-23.md` were already modified by the preceding emulator work; those edits were preserved. No commit, push, deployment or physical hardware change was authorized by this phase.

## Goal and boundary

The 22 October QGroundControl screenshots showed `RADIO_STATUS (109)` on a T12 Bluetooth session, but no observed Cube `HEARTBEAT`. Mama GCS previously treated ID 109 as an unsupported candidate. This phase identifies checksum-valid radio frames in the current receive session without turning them into vehicle evidence. It does not repair or locate the missing Cube heartbeat.

The input path is transport bytes → `MavlinkSession` → `MavlinkParser` → `MavlinkDiagnosticsAccumulator` → session `MavlinkDiagnostics` → Classic/decoder UI. `RADIO_STATUS` exits the parser as a dedicated `MavlinkParseResult.RadioStatus`, not `MavlinkMessage`; `routeSelectedAutopilot` therefore returns before autopilot selection or `MavlinkMessageRouter`. The accumulator retains only one latest field sample and a session total, with no raw frames, addresses, persistence or export. A new receive session resets both; a closed session retains them as explicitly historical in the existing decoder panel. The UI labels RSSI/noise as device-specific raw values, counts radio errors as since radio boot, and states that radio status is not Cube heartbeat, RC or health evidence.

The MAVLink generated [common RADIO_STATUS header](https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_radio_status.h) defines ID 109, a 9-byte payload, CRC extra 185 and the wire offsets used here. Both MAVLink 1 and 2 frames are checked using the existing CRC logic. MAVLink 2 trailing-zero truncation follows the parser's existing rule; signed candidates remain rejected. CRC checks corruption, not sender identity or authorization.

## Risk and failure handling

- A forged unsigned radio frame can pass CRC. It is displayed only as unauthenticated radio observation and cannot change vehicle state or command gates.
- Partial/corrupt/wrong-length frames do not become radio observations; parser rejection counters continue to record their respective outcomes. The existing bounded parser buffer and session ownership remain unchanged.
- Device-specific signal scales and 16-bit error-counter wrap prevent interpretation as dBm, packet-loss rate or reliable radio health. Receipt age and current/closed session labels prevent a stale value being presented as current.
- No parameter, permission, network destination, dependency, schema, migration, credential, report allowlist or hardware configuration was changed. A report test checks that radio raw values remain outside the existing export.

Decision and alternatives: [D-050-01](DECISIONS.md#d-050-01-decode-radio_status-only-into-a-radio-diagnostic-channel). Rollback is a review of this focused parser/result/diagnostic/UI/test change against the starting revision; no data migration or device rollback is involved. Do not execute a Git or hardware rollback without separate authority.

## Verification

- Focused JVM parser/session/Classic/panel tests: passed (Gradle `:app:testDebugUnitTest` with five `--tests` filters). Valid MAVLink 1/2 radio fields, corrupt CRC, invalid lengths, signed rejection, session reset, non-selection of a radio source, later Cube heartbeat admission, and radio-only Classic gate were covered by fixtures.
- The first sandboxed Gradle attempt could not access the wrapper cache lock outside the workspace (`Access denied`); rerunning with approved host-context cache access proceeded. This was an environment permission failure, not a passing test.
- `./gradlew.bat :app:testDebugUnitTest --rerun :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL`; 97 tasks, 21 executed/76 up-to-date. The fresh JVM XML then contained 388 tests in 64 suites, 0 failures/errors/skips. Lint output: 0 errors, 32 warnings (warnings not individually dispositioned). The later export-boundary test addition required the final JVM rerun below; the debug/test APK and lint results are for the code and Android tests before that JVM-only addition.
- On verified `emulator-5554` (Pixel 3a API 34, `-read-only -no-snapshot-load -no-snapshot-save`), `adb` boot returned `1`; pre/post `dumpsys window` showed no ANR/crash dialog. `install -r` installed debug and Android-test APKs without clearing data. The first focused `ClassicConnectionCheckUiTest` run passed 4/4; after one more decoder-label visual assertion was added and the test APK rebuilt, the final focused run passed 5/5. Evidence: ignored local `app/build/acceptance-20261023-132026-eea66b8f/instrumentation.txt`.
- Visually inspected the generated `phase50-radio-only-detail.png` and `phase50-radio-decoder.png` from that focused run. The Classic panel keeps Cube MAVLink/heartbeat/RC unconfirmed while displaying radio-only diagnosis; the decoder note labels radio evidence and raw/device-specific scales. These are synthetic Compose fixtures, not a live radio/phone test. Captures remain ignored under the same local acceptance folder.
- Related software-only Android screen batch (`RoverWorkspaceUiTest`, `MavlinkInspectorUiTest`, `FoundationUiTest`, `ClassicConnectionPlainLanguageUiTest`, `ClassicBluetoothScreenTest`): 27/27 passed. Evidence: ignored local `app/build/acceptance-20261023-132244-69f8ad15/instrumentation.txt`. No Android crash/ANR dialog afterward. The physical BLE-remote class and full Android inventory were not run in this phase.
- Final full fresh JVM rerun after the export-boundary test: `./gradlew.bat :app:testDebugUnitTest --rerun --no-daemon --max-workers=1 --console=plain` returned `BUILD SUCCESSFUL` (35 tasks: 5 executed, 30 up-to-date). Final XML contained 389 tests in 64 suites, 0 failures/errors/skips. Build/lint and Android results above were not rerun after this JVM-test-only addition.

## Remaining work

The hardware team still needs a secured, read-only same-setup trace of Cube-origin heartbeat at TELEM2, R12 UART2 and the T12 Bluetooth phone-side stream. Radio-only status does not answer which hop is missing. Physical acceptance, independent owner review/teach-back and the release gate remain open. Vehicle controls stay unavailable.

## Closing review

The reviewed change is limited to parser result handling, bounded in-memory diagnostic state, Classic/decoder display copy, tests and linked project records. No manifest permission, dependency, endpoint, command sender, persistence schema, report allowlist or hardware setting changed. Existing uncommitted 23 October handover/test-report work was retained; this phase remains uncommitted and unpushed. `git diff --check` reported no whitespace errors (only the repository's LF→CRLF working-copy warnings). A targeted scan of changed source and documentation found no new credential or private device-address literals; this is not a whole-history secret audit. The human owner has not yet reviewed the final diff or performed release teach-back.
