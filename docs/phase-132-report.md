# Phase 132 — Android lint warning review

Date: 2026-10-31
Starting revision: `3fe2dfe4341201a1a5bfa9090b37528d34f2366a`, clean and synchronized. Model/version unavailable.

## Decision before verification

Review the two non-dependency lint findings without changing Bluetooth permissions, debug test orientation, dependencies or lint configuration. Alternatives considered: removing the permission flag would change Android 12+ privacy behavior; removing landscape from the debug fixture could alter many UI tests; suppressing either warning would hide useful information. Keep the declarations and warnings pending evidence for a safe behavioral change. This is a documentation-only decision, not approval to change permissions or test coverage.

The Bluetooth entry point is `AndroidManifest.xml` -> Android runtime permission checks and BLE scanning. The flag applies on API 31+ and is ignored on older OS versions, where fine location is separately requested. Google documents the declaration and warns that some beacons can be filtered: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions . The app's location use must be re-reviewed if scan processing changes.

The other warning is the debug-only `FixtureTestActivity`, declared landscape and used by multiple instrumented UI tests. Android 16 can ignore requested orientation on large screens (smallest width at least 600 dp): https://developer.android.com/about/versions/16/behavior-changes-16 . The main activity has no landscape lock. This fixture limitation remains open for large-screen testing and should be revisited with an explicit adaptive-test design rather than deleting the declaration or weakening assertions.

## Verification and remaining work

Fresh command: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --console=plain --rerun-tasks`. Result: BUILD SUCCESSFUL in 8m 46s; 45 actionable tasks executed. The fresh 77 JUnit XML files contain 478 tests, zero failures, errors or skips. The fresh lint XML reports zero errors and 28 warnings: 14 GradleDependency, 10 NewerVersionAvailable, 2 AndroidGradlePluginVersion, 1 DiscouragedApi and 1 UnusedAttribute. The 26 version warnings were catalogued, not investigated or upgraded. Evidence: `app/build/phase132/gradle.txt` and the configured temporary build directory's test-results and reports; ignored build artifacts may be cleaned.

Code trace: `VescBluetoothDiscoveryScreen.requiredScanPermissions` requests BLUETOOTH_SCAN and BLUETOOTH_CONNECT on API 31+, and ACCESS_FINE_LOCATION on older versions. The manifest marks the latter max SDK 30. This inspection does not prove the behavior of the full physical BLE path or the absence of every possible location use. The debug-only activity is referenced by numerous instrumented fixture tests; its large-screen Android 16 behavior was not tested in this phase.

No emulator, physical device, hardware or live data was used; Android UI tests were not rerun. Previous emulator startup ANRs and the MapLibre native crash remain unresolved. Revisit the fixture when adopting a verified large-screen adaptive testing approach; revisit scan permissions if the app derives location or permission requirements change. Hardware, field accuracy, owner review and release gates remain open. No application, test, dependency, manifest or lint rules were changed; no lint warning was suppressed.
