# Phase 241: no-fix map position

## Symptom and evidence

On 2026-11-27 the USB-connected phone showed Mama GCS connected to the Cube through the remote. Operate showed a vehicle marker and coordinate `0.0000000, 0.0000000` on blue ocean tiles. The read-only Instruments panel showed a fresh Cube heartbeat and global-position message, but `GPS_RAW_INT` reported **NO FIX**, zero satellites, and no GPS coordinate. This establishes the cause of this observed map view, not the condition of the antenna or GPS hardware. The phone screenshot remains in ignored local `app/build/` and is not committed.

## Scope and execution path

The operator approved a narrow position-filter change after reviewing the telemetry boundary. Accepted selected-source MAVLink `GPS_RAW_INT` updates `GpsState.fix`; `GLOBAL_POSITION_INT` reaches `VehicleRepositoryImpl.onGlobalPositionInt`, which stores the latest position and a bounded in-memory track (2,000 points). `MapPositionEvidence.status` gates both native and Google map markers; `MapWorkspace` displays the status. A 0,0 global position with UNKNOWN or NO FIX now becomes unavailable and is not appended to the track. A later NO FIX also hides an already held 0,0 live marker. Nonzero global positions and a GPS-fixed 0,0 remain supported. This display rule does not authenticate a vehicle or certify GPS accuracy.

No new dependency, permission, network destination, persistence, credential handling or vehicle command is added. On a GPS outage the map status explains why the rover position is absent; the app cannot repair the Cube's fix. The data-preserving update restarted the phone app session; software verification and the subsequent live screen observation are tracked separately.

## Verification

- Baseline: `711b8edb8400ab90601a58252ef07ffe7564efff`, with only older untracked `output/` artifacts before this change.
- `git diff --check`: passed before the full build; Git reported only line-ending conversion notices.
- New JVM cases cover 0,0 with unknown/no fix, both message arrival orders, track exclusion and 0,0 with a 3D fix.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: `BUILD SUCCESSFUL` in 7m 54s. The fresh JVM XML reports 557 tests across 89 suites, zero failures/errors/skips; 27 Gradle tasks executed and 70 were up to date. Lint reports zero errors and 27 warnings, matching the prior warning count. Both debug APK tasks succeeded.
- `adb -s <redacted USB serial> install -r app/build/outputs/apk/debug/app-debug.apk`: succeeded without clearing phone data. The app session restarted. Two read-only phone screenshots after the update showed the active link, `GPS no fix · rover position unavailable`, no false rover marker at 0,0, and land map tiles. The second was captured after additional live telemetry. Local ignored evidence: `app/build/mama-map-updated.png` and `app/build/mama-map-updated-followup.png`. These are visual observations, not a GPS/RTK fix or vehicle-location acceptance.
- No emulator Android instrumentation suite was run; the targeted live phone visual check and JVM tests cover this specific no-fix display symptom. Full Android UI regression remains a separate gate.

## Open items and recovery

The Cube still needs a real GPS fix and field coordinates before the live map can show its actual location. Validate this outdoors or have the hardware team inspect the GPS module and its telemetry if it continues to report zero satellites. No hardware settings or vehicle commands were changed. For a code-only reversal, review and reverse this scoped diff from the recorded baseline, then rerun the tests. This source revision is not a qualified physical-operation release. The owner has not yet reviewed the diff; nothing in this phase was committed or pushed.
