# Phase 152 — recovery after a stopped app process

Date: 2026-11-03. Baseline: clean `main`, `49022493c0a15639345d7eb11c90d3266bae02ec`. Model/version unavailable. The user approved a synthetic process-stop recovery check on a disposable emulator. Decision: D-152-01.

## Scope and behavior

Two opt-in instrumentation methods in `MissionPlanningTest` exercise real app storage and UI. Preparation saves an empty synthetic baseline, enters two waypoints and three field-outline vertices, verifies the separate recovery copy exists while the committed route remains empty, writes a readiness marker and waits. A host observes the marker and live app PID, runs `am force-stop` and confirms that PID is gone. A separate instrumentation invocation launches the app in a fresh process, checks the recovered-unsaved status, route coordinates and outline, explicitly saves, and verifies the saved data and cleared recovery copy.

The stages require instrumentation argument `phase152=enabled` and intentionally skip without it. They replace local data and are for an isolated disposable emulator only; interruption may leave synthetic data there. The host clears the readiness marker before preparation to avoid accepting a stale marker. Force-stop terminates the instrumentation too: its `shortMsg=Process crashed` is expected from that deliberate stop, not a passing JUnit result. This controlled stop does not replicate every OS low-memory kill, reboot, storage failure or app crash. No production code, hardware or phone data changed.

## Verification and history

- `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain`: final guarded build succeeded; 69 tasks, 9 executed and 60 up-to-date. Existing lint report: 27 warnings. No full regression or fresh JVM suite ran.
- Used a copy of the Phase 148 isolated emulator data image under ignored `app/build/phase152/`, `Pixel_3a_API_34_extension_level_7_x86_64`, emulator-5554, `swiftshader_indirect`, no snapshots. The first boot attempt used an incorrect backing-image filename; corrected it on the copy. An immediate later restart failed on a pending emulator operation; retry started after shutdown. No emulator data was wiped.
- Before adding the guard, the process-stop journey passed its verification test (`OK (1 test)`, 33.757 s), with the acceptance runner's health gate passing. Preparation was deliberately interrupted; PID evidence and the additional full-interval health check showed no new ANRs/crashes. Evidence: ignored `app/build/phase152/{prepare.txt,process-stop.txt,full-interval-health.txt}` and `app/build/acceptance-20261103-131913-8035aafe/`.
- Guard check: invoking preparation without `-e phase152 enabled` produced assumption skip status `-4`; the runner also prints `OK (1 test)`, which must not be mistaken for an executed recovery check. Evidence: `app/build/phase152/guard-check.txt`. Android System UI subsequently showed an ANR, requiring a restart.
- First guarded journey: process-stop and recovery assertions succeeded (`OK (1 test)`, 30.243 s), but seven background-service ANR events occurred across the preparation/verification interval. It **failed** the health gate. Evidence: `app/build/phase152/guarded-{prepare.txt,process-stop.txt,verify.txt,health.txt}`.
- Final guarded retry: process PID 4579 was present after readiness and absent after force-stop. A fresh invocation verified recovery and save (`OK (1 test)`, 27.713 s). Across the full interval: boot complete, zero new ANR events, zero crash-buffer lines, and no ANR/error dialog. Evidence: `app/build/phase152/retry-{prepare.txt,process-stop.txt,verify.txt,health.txt}`. The emulator was stopped without wiping it.

## Reproduction on a disposable emulator

Build/install the debug and test APKs; use only an explicitly isolated emulator and preserve any data separately. Clear the test marker `adb -s emulator-5554 shell run-as com.mamadrones.gcs rm -f files/phase152-ready`. Start preparation asynchronously:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e phase152 enabled -e class 'com.mamadrones.gcs.MissionPlanningTest#phase152PrepareForProcessStop' com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner
```

Wait for `adb -s emulator-5554 shell run-as com.mamadrones.gcs cat files/phase152-ready` to return `ready`, verify `adb -s emulator-5554 shell pidof com.mamadrones.gcs` has a live PID, then use `adb -s emulator-5554 shell am force-stop com.mamadrones.gcs` and verify no PID remains. The preparation invocation is expected to be interrupted. Run the second stage and require a real pass, not an assumption skip:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e phase152 enabled -e class 'com.mamadrones.gcs.MissionPlanningTest#phase152VerifyAfterProcessStop' com.mamadrones.gcs.test/androidx.test.runner.AndroidJUnitRunner
```

Record device epoch before preparation; inspect crash and `am_anr` log buffers for new events and check boot/window health after verification. Never dismiss an Android error dialog to make the run pass. Ignored build evidence is local and may be removed by a build clean.

## Remaining

Prior emulator/Compose failures, physical hardware telemetry and physical field coverage remain unverified. The passing retry does not resolve earlier ANRs. Release and owner walkthrough remain open.
