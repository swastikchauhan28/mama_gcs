# Phase 53 — home-marker visual acceptance

Date: 23 October 2026. Starting revision: `0917dab5d934576a10a3e55d8fc8b4c128d5bd95` on `main`, with uncommitted Phase 52 map/test/documentation changes preserved. The user approved this bounded software-only visual gate. No production code, test assertion, build configuration, dependency, hardware setting or physical device was changed in this phase. Exact AI model/version unavailable.

## Goal and path

Determine whether the enlarged local H icon in Phase 52 actually renders over the purple home circle and whether existing home freshness/removal checks pass. The path under test is synthetic `HomePositionEvidence.visibleOnMap` → in-memory `VehicleMap` GeoJSON source and symbol/circle layers → MapLibre rendered pixels. The test uses a disposable offline-style fixture; it does not contact a map provider, receive rover telemetry, persist a home point or send commands.

## Attempts and evidence

- The first existing-AVD boot in this phase reached Android, and both prebuilt APKs installed, but `Application Not Responding: com.android.systemui` and then `Application Not Responding: system` appeared. The acceptance runner refused to start. No dialog was dismissed. This failure was retained; it was not counted as an app test failure or pass.
- A later boot reached a clean launcher, but the checkout was moved during the attempt from `C:\Users\swastik\OneDrive\Documents\ChatGPT\mama_gcs` to `C:\Users\swastik\OneDrive\Desktop\mama_gcs`. The earlier emulator was no longer connected afterward. The moved checkout had the same Git revision and Phase 52 dirty files. `desktop.ini` appeared untracked and was left untouched.
- From the moved checkout, the established disposable `Pixel_3a_API_34_extension_level_7_x86_64` AVD booted with no data wipe or snapshot load/save. Both existing APKs installed successfully using `adb install -r`. A pre-test window check showed the launcher and no ANR/error dialog.
- Exact focused command: `./scripts/Test-AndroidAcceptance.ps1 -Serial emulator-5554 -Adb 'C:\Users\swastik\AppData\Local\Android\Sdk\platform-tools\adb.exe' -TestClass 'com.mamadrones.gcs.MapLoadRecoveryTest#homeMarkerIsSeparateFromVehicleAndClearsWhenEvidenceExpires'`. Result: `OK (1 test)` in 23.216 seconds. Log: ignored `app/build/acceptance-20261023-163704-512510de/instrumentation.txt`. This one test includes purple and white rendered-pixel assertions plus marker freshness, disconnect and removal checks; it is not the full Android suite.
- The synthetic screenshot was pulled from the disposable test app into ignored `app/build/acceptance-20261023-163704-512510de/phase52-home-marker.png` and visually inspected. It shows a distinct white H centered on a purple home marker, separate from the green vehicle marker. The H approaches the circle edge at this emulator size but remains legible. Post-test `dumpsys window` showed the launcher and no ANR/error dialog. The task-started emulator was closed.

## Status and limits

The **Phase 52 home-marker visual gate is accepted for this disposable emulator fixture**. Earlier System UI ANRs remain environmental failures in the record. Phase 52's 389/389 JVM, debug APK builds and lint 0 errors/28 warnings are historical checks, not rerun in Phase 53; no source or test code changed after them. The screenshot and instrumentation log are local ignored evidence and may be lost if `app/build` is cleaned. This result does not validate every density/theme, a real map provider, live Cube telemetry, rover operation, or production readiness. Physical Cube-origin heartbeat isolation and owner/release gates remain open. Do not treat emulator success as hardware verification. No commit or push was requested.
