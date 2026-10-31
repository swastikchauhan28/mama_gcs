# Current handover

## 2026-10-31 — Phase 136 map recreation test

- Done: added a synthetic map creation, source-query and removal/recreation test using a uniquely created temporary style. See [Phase 136](phase-136-report.md).
- Verification: debug and test APK build and lint passed (10 tasks executed, 87 up-to-date; 27 lint warnings); unit tests were up-to-date. After reconnection, the new synthetic test passed on the phone; a bounded post-check found no matching app crash/ANR indicators.
- In progress: configured-map visual tests and a controlled baseline comparison remain open; the new test does not save routes or settings.
- Broken or risky: the test is not a controlled reproduction of the prior query/teardown race; MapLibre crash, emulator ANRs, hardware and release acceptance remain open.
- Next: review the candidate and remaining checks; do not claim the previous crash fixed without stronger evidence.
- Do not touch: saved phone data, user settings, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 135 MapLibre candidate evaluation

- Done: user approved evaluating MapLibre 13.7.1 with OkHttp 4.12.0; candidate catalog change was prepared for evaluation. See [Phase 135](phase-135-report.md); preserve the pre-existing Phase 134 documents.
- Verification: fresh build, 478/478 unit tests, lint zero errors/27 warnings; debug and release runtime graphs resolve candidate and OkHttp 4.12.0. Four selected synthetic map fixture tests passed on the user phone after a data-preserving update; no app crash/ANR indicators were found in the bounded post-check. Both task emulators had System UI ANRs.
- In progress: selected phone map tests passed; configured-map visual tests and controlled teardown/query baseline comparison remain open. The document-picker test replaces a saved route, so do not run it against user phone data. No baseline crash reproduction or runtime fix evidence exists.
- Broken or risky: emulator startup, earlier MapLibre native crash, network/security review, field and release acceptance remain unresolved. The candidate remains an evaluation, not a release.
- Next: restore Android health, run focused map tests and review candidate before any deployment. Roll back only the catalog entry if directed or needed; preserve existing documents and data.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 134 MapLibre and OkHttp investigation

- Done: reviewed the resolved debug map/network graph, prior crash stack, upstream 13.7.1 notes and repository advisory pages; see [Phase 134](phase-134-report.md).
- Verification: offline Gradle dependency report succeeded. MapLibre 13.6.1 and OkHttp 4.12.0 resolve together; no build, lint, instrumentation, vulnerability scan or hardware tests ran. No dependency or code changes.
- Broken or risky: 13.7.1 describes a feature-query lifecycle race fix that overlaps the saved crash API, but the crash cause and candidate compatibility are unproven. Emulator ANRs, security exposure review, field and release acceptance remain open.
- Next: separately scope and seek specific approval for an isolated MapLibre candidate evaluation; retain OkHttp for that comparison and do not claim a fix without evidence.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 133 dependency warning review

- Done: mapped all 26 Phase 132 version warnings to 15 catalog/wrapper groups, documented roles, risks, priorities and future test plans; see [Phase 133](phase-133-report.md).
- Verification: reconciled the saved lint XML and reviewed current declarations and selected vendor documentation. No fresh build/tests, dependency changes, security scan or hardware checks were performed.
- Broken or risky: advisory/transitive review remains incomplete; MapLibre crash and emulator startup failures are unresolved. Suggested versions are historical lint observations, not approved updates. Field and release acceptance remain open.
- Next: investigate MapLibre/OkHttp compatibility and upstream advisories; separately scope any update and obtain specific approval before changes.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 132 lint warning review

- Done: reviewed the Bluetooth permission attribute and debug landscape fixture; retained both declarations and warnings without suppression. See [Phase 132](phase-132-report.md).
- Verification: fresh Gradle unit tests 478/478, lint zero errors and 28 warnings; 45 tasks executed. No app code, permission, test, dependency or lint changes. Android UI and hardware checks were not run.
- Broken or risky: Android 16 large-screen debug fixture orientation is not verified; Bluetooth beacon filtering and runtime behavior remain hardware-dependent. Emulator ANRs, prior MapLibre crash, field and release acceptance remain open.
- Next: separately review the 26 version warnings and design an adaptive large-screen fixture when prioritized; investigate emulator reliability or use a healthy authorized device.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 131 host and emulator investigation

- Done: checked host resources and emulator acceleration; repeated one no-wipe startup on the Phase 130 emulator. See [Phase 131](phase-131-report.md).
- Verification: the later boot showed a System UI ANR and Bluetooth HCI startup abort, so the map-recovery and document-picker tests were not run. No build, lint or unit checks were run.
- Broken or risky: startup failures persist beyond first boot; cause and prior MapLibre crash remain unproven. Hardware, field accuracy and release acceptance remain open.
- Next: targeted emulator/host profiling or focused tests on a healthy authorized device; retain crash and ANR health gates.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 130 fresh-emulator comparison

- Done: created and observed a separate task-owned API 34 emulator with fresh data and baseline launch settings; see [Phase 130](phase-130-report.md).
- Verification: boot reached launcher and crash buffer was empty, but Android event logs recorded Google Play services and Bluetooth ANRs. Map-recovery and document-picker tests were not run; no build, lint or unit checks were run.
- Broken or risky: startup instability also appeared on fresh emulator data; root cause and prior MapLibre crash remain unproven. Hardware, field accuracy and release acceptance remain open.
- Next: inspect host/emulator evidence or use a healthy authorized test device for the focused tests; keep health checks enabled.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 129 emulator startup investigation

- Done: reviewed earlier errors and observed two no-wipe starts of the task-owned emulator. Both encountered System UI ANRs before focused tests; see [Phase 129](phase-129-report.md).
- Verification: startup crash buffers were empty; Android ANR events and dialogs were recorded. Map and document-picker tests, builds and unit tests were not run in this phase. No app or configuration changes.
- Broken or risky: emulator startup remains unreliable; Bluetooth HCI and MapLibre crash causes remain unproven. Hardware, field accuracy and release acceptance remain open.
- Next: investigate Android/host startup evidence or run focused checks on a healthy authorized device; retain the health gate.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 128 complete software regression

- Done: completed the software regression on a disposable emulator; no application or hardware changes. See [Phase 128](phase-128-report.md).
- Verification: fresh build, 478/478 JVM tests, and all 206 software Android methods passed across 11 healthy batches; lint had 28 warnings and no errors. Four hardware-only methods were excluded. Font setting restored.
- Broken or risky: two earlier emulator startups showed System UI ANR and Bluetooth service error; the successful suite does not prove those or the earlier MapLibre native crash fixed. Hardware, field accuracy and release acceptance remain open.
- Next: investigate recurring emulator failures and review the saved evidence; conduct hardware acceptance separately when available.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 127 emulator investigation

- Done: reviewed prior Android service/MapLibre failure logs; observed two no-wipe cold starts and ran the real map-recovery plus document-picker group after each. See [Phase 127](phase-127-report.md).
- Verification: both targeted runs passed 13/13 tests with healthy Android gates; startup crash and ANR buffers were empty at observation points. Existing APKs were reused; no fresh build, full regression, physical device or production code changes.
- Broken or risky: intermittent Bluetooth HCI startup, System UI/media-provider ANRs and the earlier native MapLibre SIGSEGV remain unexplained. Two clean runs and a 40-second observation period do not establish a repair or prove a cause.
- Next: review saved logs; investigate recurrence with narrowly scoped, privacy-aware emulator diagnostics; separately rerun the complete software gate when appropriate.
- Do not touch: user data, existing emulator contents, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 126 affected-test fixes and grouped reruns

- Done: test-only changes add a bounded real-keyboard refocus and system-screenshot readiness, plus refresh RC synthetic evidence after scrolling. The five earlier failed methods and subsequently observed RC/PWM failures passed in their affected grouped retests. See [Phase 126](phase-126-report.md) and D-126-01.
- Verification: the original affected groups passed 40, 27, 21 and 28 tests with healthy Android gates; the last group used a rebuilt test APK after the separate RC fixture adjustment. Real keyboard screenshots were inspected. App/test builds and lint passed with most tasks up-to-date; the full current inventory was not rerun.
- Broken or risky: prior Android Bluetooth/System UI/media-provider failures and MapLibre native SIGSEGV remain unexplained. An intermediate 28-test run failed a new PWM visibility check, an isolated retest passed, and a subsequent full-group attempt stalled; the final group passed. Emulator startup and sequence reliability are not proven fixed by a healthy rerun. Hardware and release gates remain open.
- Next: review test-only retry behavior and evidence; separately rerun the complete software gate and investigate emulator/MapLibre root causes.
- Do not touch: existing emulator data, user phone data, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 126 continued on fresh emulator

- Done: fresh emulator restarted and the 206-method software Android inventory ran across batches and retries. All 206 methods emitted a passing status at least once, including isolated retests; see [Phase 126](phase-126-report.md).
- Verification: fresh JVM 478/478, debug builds and lint passed (28 warnings). The grouped Android regression was not clean: five UI tests failed then passed individually; the document-picker batch also had an Android media-provider ANR, and one navigation batch was interrupted by ADB before a healthy 11-test rerun. Some passing method statuses occurred within an unhealthy batch.
- Broken or risky: no clean full grouped Android acceptance; prior MapLibre native crash, Android system-service instability, grouped test timeouts, physical hardware telemetry and field/release acceptance remain open.
- Next: investigate grouped planner, keyboard, document-picker and Android media-service reliability; reproduce on a healthy emulator without weakening assertions, then rerun the affected sequence and full gate.
- Do not touch: user phone data, existing emulator data, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 126 regression blocked by emulator startup

- Done: fresh JVM, debug APK and lint build passed; current Android inventory reviewed. See [Phase 126](phase-126-report.md).
- Verification: 478 JVM tests passed with no skips; 97 Gradle tasks executed and lint had 28 warnings. No Android test batch began: repeated system-service errors and ANRs failed the emulator health precheck, including on a new isolated AVD.
- In progress: restore a healthy Android emulator environment and rerun all 206 software Android methods; four physical-only methods remain excluded.
- Broken or risky: Android software regression incomplete; prior native MapLibre crash remains unexplained, and hardware, field accuracy and release gates remain open.
- Next: diagnose the Android Bluetooth/System UI startup failures without dismissing dialogs, then rerun the software inventory with healthy gates.
- Do not touch: old emulator data, user phone data, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 125 map-test stability

- Done: ran the affected 27-test group three consecutive times on the disposable emulator; all passed with healthy Android gates. See [Phase 125](phase-125-report.md).
- Verification: three runs of 27 tests, no recorded failures, skips, crashes or ANRs; build verification succeeded with 79 tasks up-to-date. The original emulator font setting remained unset. No application code changed.
- Broken or risky: the earlier native MapLibre SIGSEGV remains unexplained; three healthy repetitions do not prove it cannot recur. Full current software regression after the Phase 124 test changes, physical telemetry, real field accuracy and release gates remain open.
- Next: review the stability evidence; separately scope full software regression or physical hardware acceptance.
- Do not touch: user phone data, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-31 — Phase 124 grouped-test repair and retest

- Done: test-only synchronization refreshes simulated position, waits for real screenshots and rendered coverage pixels, and waits for a MapView frame before dense source queries. Existing assertions remain. See D-124-01 and the follow-up in [Phase 124](phase-124-report.md).
- Verification: test APK and lint builds succeeded; most Gradle tasks were up-to-date. The original affected groups passed 27 and 17 tests respectively with healthy Android gates on the disposable emulator. Cyan coverage line and invalid-coordinate screenshots were visually inspected.
- Broken or risky: previous native MapLibre SIGSEGV remains unexplained; the guard and one grouped pass do not prove it cannot recur. Two startup System UI freezes and an unhealthy grouped run with System UI/Google services ANRs occurred before the passing run. Full current software suite was not rerun after this test-only revision; hardware and release gates remain open.
- Next: review the test-only changes and evidence; further stress or full regression should be scoped separately. Preserve original failure logs.
- Do not touch: user phone data, hardware, parameters or vehicle commands. Commit/push only when requested.

## 2026-10-30 — Phase 124 full software regression

- Done: reviewed the Android inventory and ran all 206 software methods on a disposable emulator, separating actual normal and 2x Android font tests. All 206 passed at least once, including isolated retests. See [Phase 124](phase-124-report.md).
- Verification: 478 fresh JVM tests passed; debug APKs and lint succeeded with 28 warnings and 97 Gradle tasks executed. The uninterrupted emulator run was not clean: two map assertions failed, the app crashed in a MapLibre native source-feature query, and a coordinate-test screenshot returned null after restart. Each affected test passed individually; failures and crash remain open evidence.
- In progress: investigate the grouped-run map crash, map assertion failures and screenshot reliability; passing isolated retries do not establish sequence stability.
- Broken or risky: no clean full-sequence Android acceptance, full device/locale coverage, provider outage validation, Cube heartbeat, physical field accuracy or release gate.
- Next: inspect saved crash/assertion logs and reproduce the sequence before proposing a scoped fix; preserve all test assertions.
- Do not touch: user phone data, hardware or vehicle commands. Commit/push only when requested.

## 2026-10-30 — Phase 123 enlarged-text header

- Done: at large system font on wide screens, the header shows the full workspace title and moves the rover selector into the existing menu; normal wide layout is retained. See [Phase 123](phase-123-report.md) and D-123-01.
- Verification: Gradle unit-test, app/test APK and lint tasks succeeded (27 executed, 70 up-to-date). Actual 2x header and one/two/three-marker portrait/landscape map tests passed on a healthy emulator; two normal-font checks also passed. The relocated selector dialog also passed a fresh targeted test; screenshots reviewed. A prior two-row map regression, a system-service ANR and font-restoration retry are recorded.
- In progress: owner review of the diff and the extra menu tap to select a rover.
- Broken or risky: untested screen sizes, languages and physical devices, full Android regression, provider outages, prior native MapLibre crash, Cube heartbeat, field accuracy and release gates remain open.
- Next: human review; separately scope additional accessibility or hardware acceptance.
- Do not touch: user phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 122 system-font mission fit

- Done: large-text planning fit padding adapts to short map dimensions, preventing tested landscape route markers from overlapping. Actual Android 2x font setting and real mission activity tested; normal-text behavior checked. See [Phase 122](phase-122-report.md), D-122-01 through D-122-03.
- Verification: 478 fresh JVM tests, app/test builds and lint passed. Actual 2x one/two/three-point portrait and landscape test, normal-scale control test and three map regressions passed with healthy emulator gates. Screenshot review confirmed separation. Original emulator font setting restored to unset; earlier test-navigation failure, overlapping screenshot, restoration retry and startup ANRs are retained.
- In progress: human review of the uncommitted diff and map-fitting trade-off.
- Broken or risky: landscape workspace title truncates at 2x font (`Pl...`); full Android regression, other device/font configurations, provider outages, prior native MapLibre crash cause, hardware telemetry and physical accuracy remain unverified.
- Next: review the fit change and evidence, then address landscape title truncation or pursue separately scoped real-device/hardware acceptance.
- Do not touch: user phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 121 large-text map fitting

- Done: at font scale >= 1.8 planning map controls start collapsed and close after Fit draft, keeping the route clear and avoiding excessive right-inset zoom-out. See [Phase 121](phase-121-report.md), D-121-01 and D-121-02.
- Verification: 478 fresh JVM tests, app/test builds and lint passed. Two constrained-map emulator tests passed for one/two/three points and 2x text in both fixture shapes and control modes; three normal/large-text coverage and map-dialog regressions passed. Reviewed screenshots; earlier overlapping-marker failures and Android startup ANRs remain documented.
- In progress: review the uncommitted diff and the additional-tap/auto-collapse behavior.
- Broken or risky: actual device/system font scaling, smaller viewports, provider outages, full Android regression, native MapLibre crash cause, hardware telemetry and physical coverage remain unverified.
- Next: human review and, when available, real-device accessibility and hardware acceptance.
- Do not touch: user phone data, vehicle hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 120 targeted emulator retest passed

- Done: Fit draft reserves the measured width of its map controls; real mission editor tests cover add, edit, reorder, remove, portrait/landscape fit and style change. See [Phase 120](phase-120-report.md) and decisions D-120-01 through D-120-03.
- Verification: 478 fresh JVM tests and app/test build/lint passed. Three targeted emulator tests passed after a test-side frame guard; the stricter workflow passed again, and portrait and Hybrid screenshots show numbered markers clear of controls. Earlier hidden marker, startup ANRs, native test-query crash and early black screenshot are retained in the report.
- In progress: review the uncommitted diff; native MapLibre crash cause is not fully established despite the guarded passing retest.
- Broken or risky: full Android regression, tiny viewports/large fonts, provider outages, hardware heartbeat, physical field accuracy and release gates remain unverified.
- Next: human review of the fit behavior and evidence; separately plan any broader device or hardware acceptance.
- Do not touch: user phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 120 mission fit fix awaiting emulator retest

- Done: real-editor test exposed a portrait waypoint hidden under map controls; implemented measured control-width fit padding and updated the existing camera expectation. See [Phase 120](phase-120-report.md), D-120-01 and D-120-02.
- Verification: 478 fresh JVM tests, app/test APK builds and lint passed. Initial Android workflow failed before the fix; the Android test did not run against the fix because repeated emulator startups hit System UI ANRs or a snapshot timeout.
- In progress: the production fit change, edited workflow, rotation/style behavior and related map dialog require a healthy emulator retest and screenshot review.
- Broken or risky: no visual/runtime acceptance of this fix; narrow layouts and large text remain unverified. Hardware heartbeat, physical coverage and release gates remain open.
- Next: follow the exact targeted emulator tests in the Phase 120 report; investigate any failures rather than weakening assertions.
- Do not touch: user phone data, physical hardware, parameters or commands. Do not commit/push without explicit request.

## 2026-10-30 — Phase 119 configured-map visual follow-up

- Done: replaced the immediate configured-map screenshot with checks for rendered label features, marker pixels and nonblack map pixels, retaining the previous source assertions. No production map behavior changed.
- Verification: the revised configured-style test passed on the disposable emulator, with both numbered markers visible in initial, edited and Hybrid images; see [Phase 119](phase-119-report.md) for commands, evidence and prior black screenshots. Some Hybrid edge imagery remains low-resolution.
- In progress: provider availability and full-resolution tile settlement on other devices are not established.
- Broken or risky: emulator startup can freeze; full Android regression, Cube heartbeat, physical field accuracy and release gates remain unverified.
- Next: review the evidence and uncommitted Phase 119 diff; separately address any further map/provider or hardware acceptance requirements.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 119 waypoint source synchronization

- Done: added separate offline-circle and configured-style circle/label checks for synthetic route add, edit, reorder, remove, clear and style reload; no production behavior changed. See [Phase 119](phase-119-report.md) and D-119-01.
- Verification: both new instrumentation cases passed together; the existing offline two-marker pixel regression passed separately. App and test APK builds and lint succeeded; JVM tests were up-to-date, not rerun.
- In progress: configured-style screenshot captured a black map, so source-query success does not establish visible numbered labels or tiles. Investigate on a settled configured style before claiming visual acceptance.
- Broken or risky: an initial System UI ANR blocked instrumentation; a clean restart and test-package reinstall recovered it. Full Android regression, Cube heartbeat, physical coverage and release gates remain unverified.
- Next: independently investigate the black configured-map capture and check visible numbered markers after edits/reload; keep offline and online evidence separate.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 waypoint source fix

- Done: separated waypoint circle and label GeoJSON sources so a glyphless style no longer hides the tested circles. See [Phase 118](phase-118-report.md), D-118-04 and D-118-05.
- Verification: original two-marker test passed; three related map/mission checks passed; final marker/style-reload batch passed both after a fresh synthetic telemetry fixture update. 478 JVM tests passed; app/test builds and lint succeeded. Earlier failed attempts remain recorded.
- In progress: labels still require glyphs; only one configured-map screenshot and local-style scenarios were reviewed. Full landscape tile settling and broader provider availability remain unverified.
- Broken or risky: emulator startup ANRs, full Android regression, intermittent menu failure, Cube heartbeat, physical coverage and release gates remain unverified.
- Next: review the source split and test evidence, then broaden map/provider testing on a stable emulator as needed.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 waypoint geometry comparison

- Done: compared original and offset-longitude synthetic route geometry on the disposable emulator. See [Phase 118](phase-118-report.md) and D-118-03.
- Verification: both marker checks failed with empty local point-source queries; Android health was clear during that batch. The independent-source follow-up was blocked by an emulator System UI ANR.
- In progress: the original test remains failed and the cause is unknown; the geometry comparison did not identify a fix.
- Broken or risky: emulator startup, local/online marker behavior, full Android regression, hardware telemetry and physical coverage remain unverified.
- Next: inspect the local source against an independent source and representative style on a stable emulator, then fix the established cause and rerun the original assertion.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 source comparisons

- Done: ran failure-preserving local-style comparisons after recovering an emulator session. See [Phase 118](phase-118-report.md) and D-118-02.
- Verification: original marker test continued to fail with empty point-source queries after hiding/removing labels and replacing point features. Health gate reported no new errors during those tests. A nonvertical geometry comparison was blocked by a later emulator ANR and removed without changing the failed assertion.
- In progress: waypoint-source/rendering cause remains unknown; no production fix or passing retest yet.
- Broken or risky: local style lacks glyphs, emulator startup is unreliable, online map behavior and physical hardware remain unverified.
- Next: test geometry and a representative style on a stable disposable emulator, identify the cause, then repair and rerun focused regression.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 failed-test follow-up blocked

- Done: prepared and built a failure-preserving local-style comparison; it was removed after the emulator blocked execution. See [Phase 118](phase-118-report.md) and D-118-02.
- Verification: diagnostic test APK build passed; neither retry ran instrumentation because both emulator starts had System UI ANRs, and the runner refused the error dialog.
- In progress: the existing offline marker test remains failed; the empty point-source cause and glyph hypothesis remain unverified.
- Broken or risky: emulator reliability, landscape marker rendering, online map tiles, full Android regression, Cube heartbeat and physical coverage remain unverified.
- Next: use a healthy disposable emulator to run the retained test and compare a representative style; preserve the earlier failures and avoid weakening the assertion.
- Do not touch: phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 diagnostic follow-up

- Done: captured the failed offline landscape test and checked its projected positions and waypoint source. See [Phase 118](phase-118-report.md).
- Verification: diagnostic test failed again with an empty queried waypoint source; its screenshot shows the route line without visible circles. Android health was clear during the test, though startup service ANRs occurred. A later label-layer experiment was blocked by an Android ANR and removed without changing the original assertion.
- In progress: determine why the local-style point source is empty; the lack of glyphs is only a hypothesis. The configured online style has not been compared.
- Broken or risky: no verified landscape marker rendering or online provider tiles; emulator reliability, full Android regression, Cube heartbeat and physical coverage remain unverified.
- Next: use a stable disposable emulator to distinguish local style/glyph behavior from app rendering, then fix and rerun the failed test and regression.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 118 landscape map investigation incomplete

- Done: added an independent offline-style landscape route test and failure diagnostics; no production code changed. See [Phase 118](phase-118-report.md) and D-118-01.
- Verification: test APK built; first test reached camera idle and projected separation, then failed its two-marker pixel check with Android healthy. Diagnostic revision built but did not run because two later emulator starts showed System UI ANRs.
- In progress: rerun diagnostics on a stable disposable emulator and determine whether the original failure is rendering, projection, occlusion or test sampling.
- Broken or risky: no fully verified landscape map or online provider tiles; full Android regression, prior intermittent menu failure, Cube heartbeat and physical coverage remain unverified.
- Next: inspect failure screenshot and coordinates, fix the established cause and run the targeted regression without weakening assertions.
- Do not touch: saved phone data, physical hardware, parameters or commands. Commit/push only when requested.

## 2026-10-30 — Phase 117 mission test follow-up

- Done: the revised landscape route-pixel test passed on the isolated emulator. See [Phase 117](phase-117-report.md) and D-117-01.
- Verification: targeted mission test passed (1) with no new crash/ANR in the health gate; prior mission and coverage tests also passed. The new screenshot shows overlapping route markers on a blank-looking basemap.
- In progress: confirm the fully rendered landscape route and basemap after camera and tile loading; the pixel heuristic alone does not establish this.
- Broken or risky: emulator stability and earlier Android ANRs remain unresolved; full Android regression, intermittent menu failure, Cube heartbeat and physical coverage remain unverified.
- Next: diagnose capture timing/map rendering with independent evidence, preserving all failures and user data.
- Do not touch: saved phone data, physical hardware, parameters or vehicle commands. Commit/push only when requested.

## 2026-10-30 — Phase 117 mission visual review incomplete

- Done: original mission and coverage emulator tests passed and portrait/landscape screenshots were reviewed; coverage diagrams were visible. Added a test-only route-render check for landscape capture. See [Phase 117](phase-117-report.md) and D-117-01.
- Verification: original tests passed with healthy gate; revised test APK and lint passed after correcting a dp/pixel compilation error. The revised test was blocked by emulator startup ANRs and a Bluetooth crash and has not run.
- In progress: verify the revised test and inspect its landscape screenshot on a stable emulator. Original landscape map capture was blank; its cause is unconfirmed.
- Broken or risky: full Android regression, intermittent menu failure, Cube heartbeat, real coverage accuracy and release gates remain unverified.
- Next: restore a healthy disposable emulator without dismissing errors or wiping data; run the revised mission test and investigate failures.
- Do not touch: saved phone data, physical hardware, parameters or vehicle commands. Commit/push only when requested.

## 2026-10-29 — Phase 116 emulator follow-up

- Done: all eleven previously excluded data-changing methods passed on the isolated synthetic emulator after using 3 GB RAM and four CPU cores, without concurrent Gradle work. See [Phase 116](phase-116-report.md) and D-116-02.
- Verification: four health-gated batches passed 4, 3, 2 and 2 tests, with zero newly observed crash/ANR events in each. Settings screenshot was reviewed; the mission screenshot was obscured by an Android full-screen notice.
- In progress: emulator resource tuning worked for this run; the earlier ANRs' exact cause and long-term stability remain unproven.
- Broken or risky: full current Android regression, intermittent menu failure, Cube heartbeat and release gates remain unverified.
- Next: review the test evidence; use a stable disposable emulator for broader software coverage and separately resume hardware diagnosis when evidence is available.
- Do not touch: saved phone data, physical hardware, firmware, parameters or vehicle commands. Commit/push only when requested.

## 2026-10-29 — Phase 116 emulator testing incomplete

- Done: reconciled the eleven excluded methods and reviewed their storage/file side effects. See [Phase 116](phase-116-report.md) and D-116-01.
- Verification: both APK build tasks were up to date. Three task-started emulator attempts encountered Android ANRs; the health gate refused instrumentation on the fresh AVD. None of the eleven methods ran.
- In progress: Phase 116 awaits a stable disposable emulator; do not treat startup or APK installation as a test pass.
- Broken or risky: full Android regression, the intermittent menu failure and Cube heartbeat remain unverified. Startup and error evidence is in ignored build output and may disappear on clean.
- Next: fix or replace the emulator environment without wiping user data, then run the listed methods under the health gate and record results.
- Do not touch: saved phone data, physical hardware, firmware, parameters or vehicle commands. Commit/push only when requested.

## 2026-10-29 — Phase 115 menu investigation

- Done: added isolated repeated menu navigation checks, with no production behavior change. See [Phase 115](phase-115-report.md) and D-115-01.
- Verification: test APK build and lint succeeded; phone fixture passed 12 cycles each in portrait and landscape, and the formerly failing Foundation flow passed twice. The earlier failure remains recorded.
- In progress: the original intermittent failure was not reproduced and its cause remains unknown; passing repetitions do not prove it cannot recur.
- Broken or risky: eleven phone tests that mutate user data remain excluded; full Android regression and the Cube heartbeat remain unverified.
- Next: capture popup and rotation evidence if the failure returns; run excluded tests only in a disposable environment and resume hardware diagnosis with new evidence.
- Do not touch: saved user data, physical hardware, parameters or command paths. Commit/push only when requested.

## 2026-10-29 — Phase 114 header screenshot investigation

- Done: confirmed the landscape fixture header overlay follows visible Android status icons; the targeted light screenshot now temporarily hides the status bar. No production UI was changed. See [Phase 114](phase-114-report.md) and D-114-01.
- Verification: two comparison tests passed on the phone; the targeted Foundation test failed once on missing `menu-more`, then passed on retry with a clear landscape screenshot. Android test build and lint succeeded; JVM tests and production assembly were up to date.
- In progress: the first navigation failure is unexplained; portrait screenshots showed system icons even when insets reported hidden. Live production light theme was not checked.
- Broken or risky: eleven data-mutating phone tests remain excluded; complete Android regression and Cube heartbeat remain unverified.
- Next: investigate navigation failure if it recurs, run excluded checks only in a disposable environment, and resume heartbeat diagnosis when hardware evidence is available.
- Do not touch: saved user data, physical hardware, live parameters or command paths. Commit/push only when requested.

## 2026-10-29 — Phase 113 phone UI repairs

- Done: mobile workspace titles can wrap; the waypoint hint test scrolls its lazy list explicitly; the marker test waits for rendering without lowering white/purple thresholds. See [Phase 113](phase-113-report.md) and D-113-01.
- Verification: 478 JVM tests passed; debug and test APKs built; all 3 formerly failing tests plus 2 related layout checks passed on the user's phone, with clean observed health and synthetic screenshots reviewed. Lint passed (report task up-to-date).
- In progress: none for these three checks; preserve Phase 112's original failures and other uncommitted documentation.
- Broken or risky: eleven phone tests remain excluded by user request; broader Android regression and real Cube heartbeat remain unverified. Larger titles take more vertical space; the synthetic landscape settings capture had undiagnosed top-label artifacts. No hardware/safety claim follows from these checks.
- Next: review the full diff and test evidence; run excluded tests only in a safe disposable environment and continue hardware diagnosis separately.
- Do not touch: saved user data, physical hardware, live parameters, command paths or unrelated work. Commit/push only when requested.

## 2026-10-29 — Phase 112 phone software follow-up

- Done: with user approval, updated the phone app and test APK in place. Ran 182 distinct non-hardware software methods; 179 passed at least once. Six interrupted freshness/foundation checks and the RC expiry check passed on retry. See [Phase 112](phase-112-report.md) for all failures and evidence.
- In progress: 3 phone checks remain unresolved: home-marker white-pixel assertion/map retry, waypoint hint visibility and enlarged workspace title ellipsis. Eleven tests were not run because they temporarily replace saved routes or preferences; user chose to keep phone data unchanged.
- Broken or risky: the prior full emulator suite remains incomplete; synthetic phone results do not verify Cube telemetry, source authenticity or hardware safety. Test screenshots and a synthetic GeoJSON remain on the phone.
- Next: investigate the three phone failures and use a stable disposable environment for data-mutating tests; do not assume retries erase failed runs.
- Do not touch: saved user data, physical hardware, live parameters, outbound commands or unrelated work. Commit/push only when requested.

## 2026-10-29 — Phase 112 software regression incomplete

- Done: inventoried 44 software Android classes (193 declared tests), excluded the 4 physical BLE tests, reviewed emulator-only storage and existing map-provider side effects. Fresh JVM rerun: 478 tests passed; debug build and lint checks passed with tasks up-to-date. See [Phase 112](phase-112-report.md) and D-112-01.
- In progress: Android software regression has not started; the health-gated runner refused a System UI ANR dialog and later startup also recorded an ANR. Three no-wipe emulator boot attempts and logs are recorded; task-started emulator stopped.
- Broken or risky: no current full Android suite result; synthetic/unit results do not verify physical telemetry or safety. Hardware team's heartbeat investigation remains open.
- Next: restore a stable emulator environment, rerun the inventoried non-hardware batches and record all failures, skips and Android health.
- Do not touch: physical hardware, app data, live parameters, outbound commands or unrelated work; do not dismiss ANR dialogs or wipe data to force acceptance. Commit/push only when requested.

## 2026-10-29 — Phase 111 independent multi-source checks

- Done: added independent MAVLink 1/2 source fixtures and tests for pre-heartbeat rejection, system/component filtering and fresh-session selection. See [Phase 111](phase-111-report.md) and D-111-01.
- Verification: 478 JVM tests passed; Android test APK built; 3 targeted emulator tests passed, Android healthy and four connection panels visually reviewed. Production debug assembly up-to-date; lint passed (report task up-to-date).
- In progress: none in this phase.
- Broken or risky: simulated source IDs do not establish sender authentication or the physical Cube-to-phone heartbeat. Hardware-team investigation remains open.
- Next: review the test diff; resume hardware localization when the team has evidence.
- Do not touch: physical hardware, live parameters, outbound commands, user data or unrelated work. Commit/push only when requested.

## 2026-10-29 — Phase 110 independent radio status checks

- Done: added independent MAVLink 1/2 radio fixtures and parser/Classic-path tests distinguishing radio-only evidence from a separately received vehicle heartbeat. See [Phase 110](phase-110-report.md) and D-110-01.
- Verification: 477 JVM tests passed; Android test APK built; 14 targeted emulator tests passed with Android healthy and four synthetic panels visually reviewed. Production debug assembly up-to-date; lint passed (report task up-to-date).
- In progress: none in this phase.
- Broken or risky: synthetic fixtures do not confirm physical Cube-to-phone forwarding or trusted identity. The hardware team is investigating the missing Cube heartbeat.
- Next: review the test and documentation diff; resume read-only hardware localization when the team has evidence.
- Do not touch: physical hardware, live parameters, outbound commands, user data or unrelated work. Commit/push only when requested.

## 2026-10-29 — Phase 109 independent MAVLink fixtures

- Done: added synthetic PyMAVLink 2.4.49 MAVLink 1/2 golden vectors and parser/Classic-path tests for heartbeat, RC, system status, truncation and corruption. See [Phase 109](phase-109-report.md) and D-109-01.
- Verification: 476 JVM tests passed; Android test APK built; 2 targeted emulator tests passed, Android healthy and both panels visually reviewed. Production debug assembly up-to-date; lint passed (report task up-to-date).
- Broken or risky: fixtures do not verify the physical Cube-to-phone heartbeat, sensor accuracy or source authentication; hardware-team investigation remains open.
- Next: review these uncommitted test and documentation changes; investigate the physical telemetry path when hardware is available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.

## 2026-10-29 — Phase 108 synthetic Classic receive path

- Done: added test-only synthetic MAVLink integration through the Classic parser, session, selected-source routing, repository, assessment and connection panel, including expiry, recovery and reconnect. See [Phase 108](phase-108-report.md) and D-108-01.
- Verification: 473 JVM tests passed; Android test APK built; 2 targeted emulator tests passed with Android healthy and live/stale screenshots reviewed. Production debug app build was up to date; lint passed (report task up-to-date).
- Broken or risky: synthetic frames do not prove physical Cube-to-phone heartbeat, source authentication, sensor accuracy or safety; the five-second window is diagnostic only.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, commands, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 107 Classic battery-summary receipt timing

- Done: the Classic connection check uses elapsed current-session SYS_STATUS battery-summary sample and accepted SYS_STATUS evidence. Separate battery display and per-pack policies are unchanged. See [Phase 107](phase-107-report.md) and D-107-01.
- Verification: 472 JVM tests passed; both debug APKs built; 6 targeted emulator checks passed with Android healthy and stale screenshot reviewed. Initial lint passed; 472 unit tests and lint passed again after strengthening the pack-isolation test (lint report task up-to-date).
- Broken or risky: the five-second window is diagnostic only; message receipt does not establish battery health, source identity or hardware safety.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 106 Classic system-status receipt timing

- Done: the Classic connection check uses elapsed current-session system-status sample and accepted SYS_STATUS evidence. The separate battery check retains its calendar-time policy. See [Phase 106](phase-106-report.md) and D-106-01.
- Verification: 470 JVM tests passed; both debug APKs built; 6 targeted emulator checks passed with Android healthy and stale screenshot reviewed. Lint passed (report task up-to-date).
- Broken or risky: the five-second window is diagnostic only; message receipt does not establish sensor health, source identity or hardware safety.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 105 Classic Rover HUD receipt timing

- Done: the Classic connection check uses elapsed current-session Rover HUD sample and accepted VFR_HUD evidence. Other measurement checks retain their existing policies. See [Phase 105](phase-105-report.md) and D-105-01.
- Verification: 467 JVM tests passed; both debug APKs built; 6 targeted emulator checks passed with Android healthy and stale screenshot reviewed. Lint passed (report task up-to-date).
- Broken or risky: the five-second window is diagnostic only; message receipt does not establish speed, movement, source identity or hardware safety.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 104 Classic GPS receipt timing

- Done: the Classic connection check uses elapsed current-session GPS sample and accepted GPS_RAW_INT evidence, including messages reporting no fix. Other measurement checks retain their existing policies. See [Phase 104](phase-104-report.md) and D-104-01.
- Verification: 464 JVM tests passed; both debug APKs built; 6 targeted emulator checks passed with Android healthy and stale screenshot reviewed. Lint passed (report task up-to-date).
- Broken or risky: the five-second window is diagnostic only; message receipt does not establish a GPS fix, accuracy, source identity or hardware safety.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 103 Classic attitude receipt timing

- Done: the Classic connection check uses elapsed current-session attitude sample and accepted ATTITUDE evidence. Other measurement groups retain their existing policies. See [Phase 103](phase-103-report.md) and D-103-01.
- Verification: 461 JVM tests passed; both debug APKs built; 5 targeted controlled-clock emulator tests passed with Android healthy and stale screenshot reviewed. Lint passed (report task up-to-date); two real-clock screen/RC regressions passed with Android healthy.
- Broken or risky: the five-second window is diagnostic only. Message receipt does not verify sensor accuracy, source identity or hardware behavior.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, commands, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 102 Classic position receipt timing

- Done: the Classic connection check uses elapsed current-session position and accepted GLOBAL_POSITION_INT receipt evidence. Other measurement groups retain calendar-time checks. See [Phase 102](phase-102-report.md) and D-102-01.
- Verification: 458 JVM tests passed; both debug APKs built. First emulator run passed 8/9; the existing RC freshness test timed out waiting for initial FRESH, while the new position test passed. The exact RC test passed 1/1 on retry with Android healthy; lint passed (report task up-to-date).
- Broken or risky: the five-second window is a diagnostic, not a failsafe. Receipt does not verify GPS fix, position accuracy, source identity or hardware behavior.
- Next: review the diff and investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 101 PWM output timing

- Done: PWM display uses elapsed selected-source heartbeat and per-port receipt evidence; stale, missing or inconsistent timing hides values. See [Phase 101](phase-101-report.md) and D-101-01.
- Verification: 456 JVM tests and 12 targeted emulator tests passed; debug APKs built and stale-port screenshot inspected. The first unit-test compilation failed on a missing assertion import, corrected on rerun. Lint passed.
- Broken or risky: the five-second window is a diagnostic, not a physical failsafe. Emulator tests do not verify hardware, deep sleep, source identity or PWM-to-actuator mapping.
- Next: review the diff; investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: physical hardware, live parameters, command paths, persistent user data or unrelated work. Commit/push only when requested.


## 2026-10-29 — Phase 100 RC activity timing

- Done: RC activity observation now uses elapsed session, selected heartbeat and accepted RC evidence, retaining bounded historical ranges on interruption. PWM timing is unchanged. See [Phase 100](phase-100-report.md) and D-100-01.
- Verification: fresh 454 JVM tests passed; both debug APKs built; eight targeted emulator checks and Android health gate passed, with light/dark screenshot review; lint passed (report task up-to-date).
- Broken or risky: five-second timeout is diagnostic only; duplicate samples within one elapsed millisecond remain indistinguishable. Physical radio, RC input, deep sleep and safe operation are not verified.
- Next: review the phase diff; investigate the separate Cube-to-phone heartbeat path with the hardware team when available.
- Do not touch: hardware controls, live parameters, unrelated work or Git history. Commit/push only when requested.


Read this first, then the [project instructions](../AGENTS.md). Keep the newest session first; put detailed chronology in the linked phase report. Update status when evidence changes, without removing earlier failed-run evidence.

## 2026-10-29 — Phase 99 RC input display timing

### Done

- The RC input panel uses elapsed current-session, selected-heartbeat, RC sample and accepted-observation evidence before displaying channel values. PWM and the RC activity recorder retain their existing calendar-time policies. See [Phase 99](phase-99-report.md).

### In progress

- Phase 99 publication was requested; check Git for current commit and remote status. Fresh JVM run passed 452/452; debug builds and lint passed. Emulator retry passed 5/5 with a controlled RC clock; a separate actual RC timer check passed 1/1. The initial 4/5 run and test-only changes after lint are documented in the phase report.

### Broken or risky

- Five seconds remains an unverified diagnostic threshold. Receipt does not establish physical stick movement, calibration, wiring, vehicle identity or safety. PWM, RC activity and other measurement timing remain calendar-dependent; hardware and sleep/wake are unverified.

### Next

- Review the phase report and outstanding hardware evidence before field use.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 98 Classic RC receipt timing

### Done

- Classic RC_CHANNELS receipt requires independent current-session elapsed times for the RC sample and accepted selected-autopilot observation. The RC input measurement label uses the same evidence. Other Classic measurement groups remain calendar-based. See [Phase 98](phase-98-report.md).

### In progress

- Phase 97 and 98 publication was requested; check Git for current commit and remote status. Fresh JVM run passed 449/449; debug builds and lint passed; offline emulator passed 4/4. Phase 97 was uncommitted when Phase 98 began; its temporary recovery copy is identified in the phase report.

### Broken or risky

- Five seconds is an unverified diagnostic threshold. Fresh RC receipt does not establish stick movement, SBUS wiring, calibration, physical safety or trusted identity. Other Classic measurement groups and separate RC/PWM screens retain calendar-time behavior; hardware and sleep/wake remain unverified.

### Next

- Review both uncommitted phases and outstanding hardware evidence before field use.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 97 Classic elapsed receive evidence

### Done

- Classic session, byte, decoded-message and selected-heartbeat checks now use volatile elapsed receipt times, including the Classic evidence capture. Measurement and RC freshness remain calendar-based and are labeled accordingly. See [Phase 97](phase-97-report.md).

### In progress

- Fresh JVM run passed 447/447; build and lint passed. After a test-only addition, the targeted Classic controller suite passed 4/4. Emulator retry passed 3/3 after an outdated exact-text expectation was corrected; the initial 2/3 run remains in the phase report. Changes are uncommitted.

### Broken or risky

- Five seconds is an unverified diagnostic window. Millisecond timing is not a unique session identity, authentication, vehicle health or physical-safety proof. Measurement and RC freshness, other reports, hardware telemetry and device sleep/wake remain unverified or clock-dependent.

### Next

- Review the phase report and outstanding hardware evidence before field use; consider separately migrating measurement and RC freshness from calendar time.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 96 battery report freshness

### Done

- Telemetry report snapshots include elapsed-based system-summary and bounded per-pack receipt freshness; no battery values are exported. Existing calendar ages are labeled as clock-dependent. See [Phase 96](phase-96-report.md).

### In progress

- Phase 96 publication was requested; check Git for current commit and remote status. Fresh JVM run passed 442/442; build and lint passed; offline emulator passed 3/3. See the phase report for exact commands and evidence.

### Broken or risky

- Ten seconds remains an unverified diagnostic window. Fresh receipt and protocol pack ID do not establish sensor health, physical safety or identity. Existing report calendar ages and Classic checks remain wall-clock based; hardware and device sleep/wake behavior remain unverified.

### Next

- Review the report and outstanding hardware integration evidence before field use.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 95 battery health evidence

### Done

- The health screen distinguishes latest retained battery evidence from independent aggregate and per-pack receipt freshness, and retains a reported issue as historical evidence through link loss. Later samples overwrite earlier ones; a new session clears the data. See [Phase 95](phase-95-report.md).

### In progress

- Phase 95 publication was requested; check Git for the current commit and remote status. Fresh JVM run passed 437/437; debug builds and lint passed; offline emulator passed 3/3. See the phase report for exact commands and evidence.

### Broken or risky

- Freshness is a ten-second diagnostic assumption, not proof of battery health, physical safety or source identity. Retained evidence is not a fault history. Hardware reporting rates, device sleep/wake, and physical integration remain unverified; other health evidence and report-age behavior are outside this change.

### Next

- Review the phase report and outstanding hardware integration evidence before field use.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 94 independent battery freshness

### Done

- Per-pack BATTERY_STATUS and aggregate SYS_STATUS now retain independent in-memory elapsed receipt times; stale/unavailable values are hidden in battery panels and aggregate dashboard instruments. A one-second UI ticker expires readings even when no data arrives. See [Phase 94](phase-94-report.md) and D-094-01.

### In progress

- Phase 94 publication was requested; check Git for current commit and remote status. Final-source JVM tests passed 433/433; build and lint passed. Corrected emulator fixture passed 2/2; the initial failed attempt is retained in the phase report.

### Broken or risky

- Ten seconds is an unverified display diagnostic, not a battery-health or operational safety limit. Physical reporting rates, sleep/wake and actual multi-pack behavior remain unverified. General health evidence can be historical; Classic check and report ages still use wall time. Other telemetry freshness is outside this phase.

### Next

- Review the phase report and changes, then address outstanding hardware integration evidence before any field use.

### Do not touch

- Physical operation, Cube/radio settings, command paths, persistent user data and unrelated work remain outside this phase.

## 2026-10-28 — Phase 93 monotonic heartbeat and map position

### Done

- Heartbeat expiry and map-position freshness now use Android elapsedRealtime; calendar timestamps remain for display. Missing/future elapsed position evidence fails closed, and new sessions clear old position. Fresh JVM run passed 428 tests; build/lint succeeded and targeted offline emulator checks passed 3/3. See [Phase 93 report](phase-93-report.md).

### In progress

- Phase 93 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- Other telemetry, HOME_POSITION and report age policies still use wall-clock timestamps. Five-second windows and scheduling delays are diagnostics, not validated telemetry rates or safety limits. Real device sleep/wake, physical position, hardware telemetry and authenticated peer identity remain unverified; the earlier Compose crash remains unexplained.

### Next

- Review the elapsed-clock data flow and tests. Consider separately scoped freshness work for other measurements; validate actual rates and link behavior with the hardware team before field use.

### Do not touch

- Keep hardware on hold; do not infer physical safety, device identity or worked soil from timing or modeled coverage.
## 2026-10-28 — Phase 92 independent map position freshness

### Done

- Added a five-second app diagnostic position window independent of heartbeat. Stale/unavailable position clears map marker, heading and track and disables center/follow; the operation workspace labels stale position and fresh updates restore it. Fresh JVM run passed 426 tests; build/lint succeeded, focused emulator checks passed 2/2 and map regressions 4/4. Screenshot-enabled label retry passed 1/1; lint predates that test-only screenshot addition. See [Phase 92 report](phase-92-report.md).

### In progress

- Phase 92 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- The five-second window is not validated against actual telemetry rates or physical safety; a one-second tick and UI scheduling add delay. Existing wall-clock timestamps can be affected by clock changes; other telemetry freshness and source authentication remain unresolved. Real basemap, representative devices, physical positioning and hardware telemetry are unverified.

### Next

- Review the position policy and scope; consider a separately approved monotonic freshness design and validate real telemetry rates with the hardware team.

### Do not touch

- Keep hardware on hold; do not infer physical safety or worked soil from map freshness or coverage estimates.
## 2026-10-28 — Phase 91 advisory minimum overlap

### Done

- Added the Phase 91 advisory minimum-overlap estimate and repaired the audit-discovered silent input truncation. Overlength entries produce an error and cannot be calculated as a shortened valid value. Fresh JVM run passed 424 tests; build/lint succeeded and targeted emulator checks passed 2/2. See [Phase 91 report](phase-91-report.md).

### In progress

- Phase 91 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- The result is an ideal-spacing estimate, not verified soil coverage. Real tracking error, implement behavior, field boundaries, turns and hardware telemetry remain unverified. The earlier Compose lifecycle crash did not recur in this run; its cause is unknown. The longer comparison requires scrolling.

### Next

- Review the correction and combined Phase 91 diff; validate representative devices, accessibility and physical assumptions independently before field decisions.

### Do not touch

- Keep hardware on hold; do not upload previews or infer worked soil from the advisory calculation.
## 2026-10-28 — Phase 90 cancellation and enlarged-text checks

### Done

- Added a deterministic state test holding an older non-cooperative comparison while the sideways assumption changes, then checking the newer result survives. The default calculator still runs the existing planner off the main thread. Fresh JVM run passed 420 tests; build and lint succeeded. Enlarged-text emulator test passed 1/1 and the former combined scenario passed 2/2, with Android health gates passing. See [Phase 90 report](phase-90-report.md).

### In progress

- Phase 90 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- The Phase 89 Compose lifecycle crash did not recur, but its cause is unknown. The direct-state test simulates a setting change while busy; the current UI disables its settings dialog during calculation. Enlarged local font testing is not full accessibility validation. Actual positioning, implement behavior, coverage and hardware telemetry remain unverified.

### Next

- Review the internal test seam and evidence. Independently validate representative devices, accessibility and real positioning/implement assumptions before physical field decisions.

### Do not touch

- Keep hardware on hold; do not upload previews or infer worked soil from modeled results.
## 2026-10-28 — Phase 89 assumed sideways-error margin

### Done

- Added optional blank-by-default per-pass sideways error and an ideal overlap/spacing-gap margin shared by direction comparisons. A fresh JVM run passed 419 tests, build and lint succeeded, and the new emulator test plus existing comparison regression passed separately. See [Phase 89 report](phase-89-report.md).

### In progress

- Phase 89 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- Initial combined emulator run crashed during activity destruction with a Compose lifecycle exception; independent retry passed but root cause remains unknown. Input changes during an in-flight comparison were not independently exercised. Estimates cannot guarantee worked soil or real positioning accuracy; actual implement behavior, turns, boundaries and hardware telemetry remain unresolved.

### Next

- Review the calculation, input validation, labels and scoped test diff. Validate accessibility and use independently measured tracking and implement data before any physical field decisions.

### Do not touch

- Keep hardware on hold; do not upload previews or infer worked soil from the margin or samples.
## 2026-10-28 — Phase 88 dense map interactions

### Done

- Added an offline emulator test for three cycles of pan, zoom and switching a 16,384-point sampled-gap source, then clearing it. Screen-level native gestures passed with healthy Android; build and lint succeeded with JVM tests and lint report up-to-date. See [Phase 88 report](phase-88-report.md) for timings and the first failed gesture attempt.

### In progress

- Phase 88 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- The initial Compose swipe timed out; native screen-level input succeeded, so the exact first failure cause remains unproven. Emulator timings and source queries do not establish all markers, sustained frame rate, real-device performance or physical field accuracy. Hardware telemetry remains unresolved.

### Next

- Review the scoped test and documentation diff; validate representative phones and independently verify field geometry and positioning before physical use.

### Do not touch

- Keep hardware on hold; do not upload previews or infer worked soil from sampled data.
## 2026-10-28 — Phase 87 dense sampled-gap tests

### Done

- Added offline emulator tests for a 16,384-point map source switch and clearing, and for an in-memory field edit clearing its selected triangle preview. Both passed, including both triangle directions; build and lint succeeded with unit tests and lint report up-to-date. Timings, screenshot and limitations are in [Phase 87 report](phase-87-report.md).

### In progress

- Phase 87 publication was requested; check Git for the current commit and remote status.

### Broken or risky

- Source queries do not establish that every marker rendered, and the checks do not measure sustained responsiveness, real-device behavior or physical coverage. Hardware telemetry remains unresolved. See earlier failed-run evidence in Phase 86.

### Next

- Review the test diff; validate on representative devices and independently verify field geometry, positioning and physical coverage before real use.

### Do not touch

- Keep hardware on hold; do not upload coverage previews or infer physical work from sampled data.
## 2026-10-28 — Phase 86 sampled coverage gaps

### Done

- Selected coverage maps show bounded magenta main-field gap sample centers from the existing estimator; the legend distinguishes zero and unavailable and warns about missed gaps and marker appearance. A fresh JVM run passed 416 tests. Final build/lint succeeded with the JVM task and lint report up-to-date; targeted emulator runs passed 3/3 and 2/2 for layout, sample rendering and clearing. See [Phase 86 report](phase-86-report.md) for the interrupted run, failed selector and failed compilations.

### In progress

- Phase 86 publication was requested; check Git for current commit and remote status.

### Broken or risky

- Samples are not exact gap boundaries or actual worked soil; small gaps can be missed, dots can merge or extend past the outline, and reserved headland is excluded. Maximum 16,384-point renderer load, real basemap, screen readers, actual positioning and physical coverage remain unverified. The first emulator run ended without a result during low host memory; its cause was not established. The longer legend requires extra scrolling.

### Next

- Review sampled-gap visualization with representative fields and device performance; independently validate geometry and physical hardware before field use. Commit/push only on request.

### Do not touch

- Keep hardware on hold; do not upload coverage previews or infer physical work from sampled data.

## 2026-10-28 — Phase 85 enlarged-text coverage map layout

### Done

- Moved the compact portrait coverage legend and collapsed control toggle outside the map, with more height for enlarged text. Emulator checks exercised triangle and concave fields at normal and enlarged local font scale; the initial suite had two failures that passed on independent retries. Build and lint succeeded, and a fresh initial JVM run passed 414 tests. See [Phase 85 report](phase-85-report.md).

### In progress

- Phase 85 publication was requested; check Git for current commit and remote status.

### Broken or risky

- Expanded controls temporarily cover the map, and the taller layout requires scrolling. Initial Compose layout and blank map-capture failures remain unexplained despite retries. System-wide accessibility, screen readers, actual basemap, physical coverage and hardware are not validated.

### Next

- Review screenshots and test with representative devices, especially system-wide font scaling; scope physical validation separately. Commit/push only on request.

### Do not touch

- Keep hardware on hold; do not upload previews, operate the rover or infer physical coverage from emulator results.

## 2026-10-28 — Phase 84 irregular coverage map tests

### Done

- Added offline triangle and concave U-field UI-to-map checks in both directions, including split concave segments, bounded geometric comparison, rendering, clearing and unchanged draft. An enlarged-text triangle verifies control access. The targeted emulator run passed 3/3; build and lint succeeded, with JVM test and lint report tasks up-to-date. See [Phase 84 report](phase-84-report.md).

### In progress

- Phase 84 publication was requested; check Git for current commit and remote status. No production app behavior changed.

### Broken or risky

- The enlarged-text screenshot shows the legend and controls covering a significant portion of the field. The fixture uses local font scaling and a synthetic field, not system-wide accessibility settings, a screen reader, a real map or physical equipment. Landscape irregular-field integration remains unverified.

### Next

- Scope an enlarged-text map layout improvement and verify it visually; independently validate field geometry and hardware before any field use. Commit/push only when requested.

### Do not touch

- Keep hardware on hold; do not upload coverage previews or infer physical soil coverage from emulator tests.

## 2026-10-28 — Phase 83 coverage map controls and direction labels

### Done

- Portrait coverage map controls now collapse; the single-preview setting and the comparison shown on the map are labeled separately. Build and lint succeeded (lint report up-to-date); 414 JVM tests passed. Targeted portrait, landscape and native-map checks passed, and a comparison regression passed on retry after Android returned a null screenshot. See [Phase 83 report](phase-83-report.md).

### In progress

- Phase 83 publication was requested; check Git for current commit and remote status.

### Broken or risky

- The collapsed map control still overlaps a small portion of the sample field; expanded controls occupy more space. The real basemap, other field layouts, accessibility at large font sizes and physical coverage remain unverified. Earlier screenshot failure is recorded.

### Next

- Review UI and test evidence with representative fields; scope physical validation separately. Commit/push only when requested.

### Do not touch

- Keep hardware on hold; do not upload previews, operate a rover or infer physical coverage from emulator checks.

## 2026-10-27 — Phase 82 coverage-map integration tests

### Done

- Added offline end-to-end coverage selection, native geometry, rendered pixel, switch and clear tests in portrait and landscape. Final emulator run passed 2/2; build and lint succeeded with JVM and lint report tasks up-to-date. See [Phase 82 report](phase-82-report.md) for both earlier failures and evidence.

### In progress

- Phase 82 publication was requested; check Git for current commit and remote status. No production app behavior changed.

### Broken or risky

- Portrait map controls obscure a small part of the field; the single-preview direction summary can differ from the comparison selected for the map. The integration test covers one synthetic rectangle and an offline style; real basemap, other shape integration and physical coverage remain unverified.

### Next

- Scope a UI improvement for map control obstruction and direction labeling, then validate against representative fields. Commit and push only on request.

### Do not touch

- Keep hardware on hold; do not upload previews, operate a rover or infer actual soil coverage from emulator tests.

## 2026-10-27 — Phase 81 selected coverage map preview

### Done

- Added an explicitly selected cyan pass-centerline map overlay for either direction, separate from the route and cleared by input or outline changes. Build and lint succeeded (lint report up-to-date); the rebuilt JVM suite passed 414 tests, the fixed coverage/planner emulator suite passed 7/7 and the offline native map test passed 1/1. See [Phase 81 report](phase-81-report.md) for initial failures and evidence.

### In progress

- Phase 81 publication was requested; check Git for the current commit and remote status. Preserve unrelated `desktop.ini`.

### Broken or risky

- The first map screenshot was blank after a rotation-heavy test; the standalone retry passed but the earlier cause remains uncertain. Selection on a real basemap and physical field accuracy are unverified. Date-line-crossing fields cannot use this overlay; it depicts centerlines, not turns, implement footprint or actual soil coverage.

### Next

- Review the diff and test with representative field data; independently validate positioning, headlands, turns and implement behavior before field use. Commit and push only on request.

### Do not touch

- Keep hardware on hold; do not upload a preview, command a rover or modify unrelated `desktop.ini`.

## 2026-10-27 — Phase 80 direction comparison

### Done

- Added an explicit local comparison of north/south and east/west coverage for the same field and tool settings, with independent cards, diagrams, pass counts, lengths, modeled-gap ranges and direction-specific errors. It does not choose or save a direction. 412 JVM tests passed; final build/lint passed and the final targeted emulator run passed 7/7. Portrait, landscape and enlarged-text captures were inspected.

### In progress

- Initial emulator and build failures are retained in [Phase 80 report](phase-80-report.md), including a later Compose renderer exception whose unchanged test passed on retry. Publication of Phases 76–80 was requested; check Git for the current commit and remote status. Preserve unrelated `desktop.ini`.

### Broken or risky

- Comparison ranges are modeled and potentially overlapping; neither turns, physical soil, GPS error nor implement behavior is validated. Narrow/large-text layouts require sideways and vertical scrolling; full-width/system-wide enlarged-text and screen-reader accessibility remain unverified. Hardware work and prior account/UDP gaps remain open.

### Next

- Review the comparison with representative field/implement data and plan turns/headland work separately before physical use. Future commits and pushes require a request.

### Do not touch

- Keep hardware on hold; do not send commands, upload comparisons, replace saved routes or modify unrelated `desktop.ini`.

## 2026-10-27 — Phase 79 modeled uncertainty ranges

### Done

- Added separate modeled headland and unplanned-main area ranges, using conservative whole-cell classification and outward display rounding. Ranges accompany the existing sample estimates and explicitly describe modeled geometry rather than physical soil or positioning accuracy. Build/lint succeeded, 409 JVM tests and 5 targeted emulator tests passed, and the range screen was visually inspected. Tests include analytical fields and sample-center aliasing that hides a large gap; lint report was up-to-date rather than freshly regenerated.

### In progress

- Phases 76–79 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`. See [Phase 79 report](phase-79-report.md).

### Broken or risky

- Ranges may be wide, depend on numerical/model assumptions and are not independently certified. They do not include boundary-survey, tracking, implement or physical soil uncertainty. Headland work/turns and prior hardware/account/UDP acceptance gaps remain open.

### Next

- Review modeled ranges against representative field data; separately scope independent geometry review, headland/turn plans and physical validation before field operation. Commit/push only on request.

### Do not touch

- Keep hardware on hold; do not send commands, upload passes, replace saved routes or modify unrelated `desktop.ini`.

## 2026-10-27 — Phase 78 sampled coverage gaps

### Done

- Coverage preview estimates reserved headland and unplanned main area separately using a bounded 128 × 128 local grid. It displays sampling-cell dimensions and warns that estimates can be inaccurate and gaps can be missed. If no field samples are present the estimate is unavailable; zero gap samples mean none detected, not proven coverage. No saved route, networking or vehicle commands are changed.
- Final build and lint succeeded, 406 JVM tests passed, and the final emulator run passed 5/5 with healthy Android. Two initial test-only partial-text assertion failures and their correction are retained in the [Phase 78 report](phase-78-report.md). Final estimates/warning screenshot inspected; lint report was up-to-date, not a freshly regenerated warning inventory.

### In progress

- Phases 76–78 remain uncommitted and unpushed; preserve unrelated `desktop.ini`.

### Broken or risky

- Sampling has no guaranteed error bound and may miss narrow or regularly spaced gaps; it never establishes worked soil. Headland passes, turning paths, actual implement offset and tracking, physical validation, accessibility and prior hardware/account/UDP gaps remain open.

### Next

- Review with representative field/implement data and scope independently verified geometry and headland/turn planning before physical operation. Commit and push only on request.

### Do not touch

- Keep hardware on hold; do not send commands or replace saved routes with previews. Preserve `desktop.ini`.

## 2026-10-27 — Phase 77 headland preview

### Done

- Added a 0–100 m headland-width input to Coverage settings, defaulting to zero. A positive value reserves an amber boundary band and conservatively clips main-area working passes away from it. Invalid or too-large settings produce an error. It remains a volatile preview, not a route or headland/turn plan.
- 403/403 JVM tests and 5/5 targeted emulator tests passed; rectangular, triangular and concave diagrams were visually inspected. The real-app regression checked route preservation and portrait/landscape with the zero default. See [Phase 77 report](phase-77-report.md) and [decision](DECISIONS.md#d-077-01-reserve-an-interior-headland-in-the-local-coverage-preview).

### In progress

- Build and lint passed; the lint report was up-to-date rather than a fresh warning inventory. Phases 76 and 77 remain uncommitted and unpushed; base revision `847d891`. Preserve unrelated `desktop.ini`.

### Broken or risky

- Reserved headland is not a turn path or worked soil; conservative clearance can leave extra gaps. Width, vehicle dimensions, tool offset, GPS accuracy and true field coverage need physical validation. Positive-headland real-app layout, system-wide large text, other devices/IMEs/locales and accessibility remain unverified. Hardware and earlier account/external UDP gaps remain open.

### Next

- Review with a representative field and actual implement width; separately scope headland work, safe turning and boundary gaps before any machinery use. Commit/push only on request.

### Do not touch

- Keep hardware work on hold; do not send commands, upload passes, replace saved routes with previews, or modify unrelated `desktop.ini`.

## 2026-10-27 — Phase 76 field coverage preview

### Done

- Plan route → Coverage → Coverage settings now previews the current Field outline using tool working width, overlap and north/south or east/west passes. Displays spacing, field area, working length, direction arrows, ideal green tool strips and pink areas outside them. Preview is bounded, local and volatile; saved route/recovery data and vehicle commands are unaffected.
- 400/400 JVM tests passed. Both APKs and lint completed. All 4 new coverage tests and 30 related distinct Android tests passed across targeted runs, including one unchanged-test retry after Android returned a null screenshot. Final emulator health passed; portrait/landscape and narrow enlarged-planner captures inspected. Fixed landscape keyboard obstruction and diagram clipping found during acceptance. See [Phase 76 report](phase-76-report.md) for commands, retained failures, exact run boundaries and visual evidence.

### In progress

- Phase 76 is uncommitted and unpushed. Baseline `847d891` contains the previously pushed Phases 71–75 and full software test report. Preserve unrelated `desktop.ini`.

### Broken or risky

- No outstanding failure in the targeted coverage checks. This is not complete agricultural path planning: no headlands, turning paths, obstacles, tool offset, real position/implement tracking or proof of worked soil. Pink edge/corner gaps need extra planning. System-wide enlarged-dialog text, other phones/IMEs/locales and screen-reader acceptance remain unverified. Lint's existing 28 warnings remain; final warning report was up-to-date. Hardware heartbeat delivery and prior account/external UDP acceptance gaps remain open.

### Next

- Review the local preview using a representative field outline and actual tool width. Scope headland/turn and boundary-gap planning separately before considering a drivable route. Commit/push only on request.

### Do not touch

- Keep hardware work on hold. Do not upload/execute generated passes, send vehicle commands, replace saved drafts with previews, weaken checks, or modify `desktop.ini`.

## 2026-10-27 — Full non-hardware software test run

### Done

- Fresh Gradle build, Android test APK, 389/389 JVM tests and lint completed; lint reported 0 errors and 28 warnings. All 150 existing non-hardware Android tests passed in two batches (7 + 143) on the disposable API 34 emulator; both Android health gates passed with no new crash or ANR evidence. Online map and real Android document-picker flows were included. Representative full-display map, route-library and coordinate-keyboard captures were inspected. See [full software test report](test-report-2026-10-27-full-software.md).

### In progress

- Account-journey and external UDP acceptance remain open software coverage gaps. Hardware work remains on hold by user request. Preserve unrelated `desktop.ini`; check Git for the publication state of Phases 71–75 and this report.

### Broken or risky

- No failure remains in the existing non-hardware test inventory. Full account setup/login/logout through the real UI, a real external UDP peer, other devices/locales/IMEs, and physical Cube/radio acceptance remain unverified. Lint has 28 warnings; generated test evidence is in ignored build/temp directories.

### Next

- Review the combined Phase 71–75 diff and this test report. Add targeted account-journey and external UDP acceptance coverage if those integration gaps are required for release.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken validation, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-27 — Phase 75 signed decimal coordinate entry

### Done

- The shared waypoint/outline coordinate dialog requests a decimal keypad and offers a labelled `±` sign control per field. Negative coordinates remain enterable without relying on a keyboard's minus key; the existing parser, range limits and confirmation gate are unchanged. Fresh build, 389/389 JVM tests, lint and 11/11 targeted Android tests passed with a healthy emulator. Portrait and narrow-landscape full-display captures were inspected. See [Phase 75](phase-75-report.md) and [D-075-01](DECISIONS.md#d-075-01-offer-a-signed-decimal-keypad-for-local-coordinates).

### In progress

- Phases 71–75 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`. Hardware work remains on hold by user request.

### Broken or risky

- The first Phase 75 build failed on a missing Android-test import; the corrected build passed and the failure remains recorded. In the narrow landscape fixture the dialog footer still requires Done to reveal it. Other IMEs/locales, physical-phone input, screen-reader acceptance, the whole Android suite and physical Cube/radio acceptance remain unverified. Lint's warning-count report was up-to-date, not regenerated.

### Next

- Review the combined Phase 71–75 diff and evidence. If an operator phone becomes available, scope a signed-coordinate keyboard check there; keep hardware setup separate.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken coordinate or safety validation, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-27 — Phase 74 normal-app coordinate check

### Done

- Added a real `MainActivity` portrait acceptance case for Mission → Add coordinates, invalid range guidance with IME open, corrected coordinates after Done, and Cancel. It passed 1/1 on the disposable Pixel emulator with a healthy Android gate. Both 1080×2220 full-display captures were inspected; saved draft and recovery copy were unchanged. See [Phase 74](phase-74-report.md) and [D-074-01](DECISIONS.md#d-074-01-verify-coordinate-guidance-in-the-real-app-window). No production app code changed in this phase.

### In progress

- Phases 71–74 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`. Hardware work remains on hold by user request.

### Broken or risky

- Physical phone, other IMEs/locales, saving through the real app path, the whole Android suite, and physical Cube/radio acceptance are unverified. Phase 73's 389 JVM and lint results were not rerun in Phase 74.

### Next

- Review the combined Phase 71–74 diff and evidence. Scope any additional real-device check separately before release claims.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken coordinate or safety validation, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-27 — Phase 73 invalid coordinate guidance

### Done

- Added range guidance below invalid latitude/longitude fields in the shared local waypoint/outline dialog. The focused longitude error now scrolls above the IME in landscape. Final targeted Android checks passed 8/8 with a healthy emulator; 389/389 JVM tests passed and lint completed. Portrait and landscape full-display captures were inspected. See [Phase 73](phase-73-report.md) and [D-073-01](DECISIONS.md#d-073-01-explain-invalid-local-coordinate-entries).

### In progress

- Phases 71–73 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`. Hardware work remains on hold by user request.

### Broken or risky

- The first Phase 73 test APK failed to compile from a wrong test import, then a passing landscape test visually hid the longitude error behind the keyboard. Both failures and the corrected evidence are retained in the report. The lint XML warning count is from an up-to-date report, and physical phone/other IMEs/locales/full Android suite are unverified.

### Next

- Review the combined Phase 71–73 diff and evidence. Scope any physical-phone or other UI acceptance separately before release claims.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken coordinate or safety validation, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-27 — Phase 72 portrait waypoint keyboard check

### Done

- Added portrait Add/Edit variants alongside the existing landscape tests, with portrait configuration and full-display dimensions asserted. All four passed on the disposable emulator; the four portrait captures measured 1080×2220 and were visually inspected. Add/Apply/Cancel and fields were visible above the IME, and Done plus exact local callbacks worked. See [Phase 72](phase-72-report.md) and [D-072-01](DECISIONS.md#d-072-01-verify-waypoint-keyboard-flow-in-portrait). No production code change this phase.

### In progress

- Phase 71 and Phase 72 remain uncommitted and unpushed. Preserve unrelated `desktop.ini`. Hardware remains on hold by user request.

### Broken or risky

- The first Phase 72 run passed two tests but captured landscape because the debug fixture is fixed landscape; it is retained as a failed portrait-evidence attempt. The corrected portrait tests passed. The runner could not identify the GPU launch option. Physical phone, other IMEs/locales and the whole Android suite remain unverified.

### Next

- Review the combined Phase 71–72 diff and evidence. A physical-phone portrait/IME check or other feature work should be scoped separately.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 71 waypoint keyboard path

### Done

- Added an explicit Done action to the Add/Edit waypoint longitude field, with the keyboard controller resolved inside its dialog window. Done only hides the IME; existing coordinate validation and local Add/Edit actions are unchanged. The focused 2/2 Android tests, 13/13 related Android regressions, 389/389 JVM tests and lint (0 errors/28 baseline warnings) passed. Final full-display keyboard-open/after-Done captures were inspected. See [Phase 71](phase-71-report.md) and [D-071-01](DECISIONS.md#d-071-01-verify-waypoint-entry-with-the-android-keyboard).

### In progress

- Phase 71 is uncommitted and unpushed. Preserve unrelated `desktop.ini`. Hardware work remains on hold by user request.

### Broken or risky

- At 320 dp / 2× in the landscape fixture, the open IME covers the dialog footer; pressing Done reveals it. The first two failing test runs and their causes are retained in the report. Other IMEs, phones and locales, the whole Android suite, and physical acceptance were not checked this phase.

### Next

- Review the Phase 71 diff and evidence. A separately scoped portrait-phone/IME check would add real-device layout evidence; do not infer it from this emulator fixture.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken validation, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — UI Phases 60–70 verification

### Done

- Targeted verification of the uncommitted UI phases passed: 389/389 JVM tests, 43/43 emulator Android tests in two healthy batches, and lint with 0 errors/28 baseline warnings. Fresh waypoint and route-library screenshots were visually inspected. Exact commands, evidence and limits: [UI Phases 60–70 test report](test-report-2026-10-26-ui-phases-60-70.md).

### In progress

- The Phase 60–70 changes and verification record form one delivery; check Git history and remote state for its commit/push result. Preserve unrelated `desktop.ini`. Hardware work remains on hold by user request.

### Broken or risky

- The Phase 70 keyboard-open landscape occlusion remains: pressing Done reveals the filtered result. This targeted run does not cover every Android test class, other phones/locales/IMEs, or physical BLE/Cube/receiver/remote acceptance. The 28 lint warnings remain.

### Next

- Scope any real-phone portrait/IME or hardware acceptance separately. Keep the Phase 60–70 test evidence with the delivery record.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, or weaken safety/accessibility checks. Preserve `desktop.ini`. The earlier delivery request authorized only its scoped commit and push.

## 2026-10-26 — Phase 70 route search keyboard path

### Done

- Added an explicit Done action to the saved-route search field that hides the keyboard. Moved Clear into the field's trailing area so a matching card and its actions fit after Done at 320 dp / 2× text. Query/filtering and stable route IDs are unchanged. See [Phase 70](phase-70-report.md) and [D-070-01](DECISIONS.md#d-070-01-check-route-search-while-the-android-keyboard-is-visible).
- Final debug app and test APKs built; the two route-search UI tests passed `OK (2 tests)` with a healthy Android gate. Full-display keyboard-open and after-Done screenshots were inspected. No saved user route or hardware was used.

### In progress

- Phases 60–70 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- At 320 dp / 2× in the landscape fixture, the open IME covers result cards. Pressing Done reveals the matching card and Close. Earlier semantic-only assertions falsely marked covered controls as displayed; the first visual pass and later failing Done test are retained in the Phase 70 report. Other IMEs/devices, full suite, lint, persistence and physical operation were not checked this phase.

### Next

- Review the accumulated diff before a separately requested commit, or select another bounded UI task. A real-phone portrait/IME check can be scoped separately if available; do not infer it from the landscape fixture.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 69 pinned route-library search

### Done

- Kept the saved-route search field, Clear search and result count visible while route cards scroll in a separate bounded list. The existing route order and ID-based Open/Delete confirmations are unchanged. See [Phase 69](phase-69-report.md) and [D-069-01](DECISIONS.md#d-069-01-keep-library-search-visible-while-saved-routes-scroll).
- Built both debug APKs. The focused search/pinned-layout test and existing enlarged-text library regression each passed `OK (1 test)` with healthy Android gates. The scrolled 320 dp / 2× screenshot was visually inspected. No repository data or hardware was used.

### In progress

- Phases 60–69 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Fixed search controls reduce the route-list viewport; users may scroll farther to reach a card action at 2× text. Evidence covers one synthetic English fixture at 320 dp, not very narrow/other-language devices, persistence or physical operation. No full suite or lint ran this phase.

### Next

- Review the accumulated diff before a separately requested commit, or select another bounded UI task. Keep physical acceptance separate from emulator results.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 68 local route-name search

### Done

- Added an in-dialog, case-insensitive saved-route name filter with match count, clear and no-match states. It resets on reopening and preserves entry order and stable ID-based Open/Delete confirmations. See [Phase 68](phase-68-report.md) and [D-068-01](DECISIONS.md#d-068-01-filter-local-route-library-by-name-in-the-dialog).
- The new 320 dp / 2× emulator test and the existing large-text library regression each passed `OK (1 test)` against current APKs, with healthy Android gates. A screenshot of the filtered dialog was visually inspected. No repository data or hardware was used.

### In progress

- Phases 60–68 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Search controls use dialog height and route cards may require scrolling at 2× text. Evidence covers one synthetic English fixture at 320 dp, not persistence, other devices/locales or physical operation. No full suite or lint was run in this phase. The last source edit after the build was indentation only.

### Next

- Review the accumulated diff before a separately requested commit, or select another bounded UI task. Keep physical acceptance separate from emulator evidence.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 67 enlarged-text route library

### Done

- Added a focused 320 dp / 2× UI check for the local route-library dialog. A long synthetic route name wraps without truncation, dialog bounds stay in the window, scrolling reaches later entries, and Open/Delete/Close are reachable. Confirmed actions dispatch only to an in-memory callback. The screenshot was visually checked; existing layout was sufficient, so no production UI change was needed. See [Phase 67](phase-67-report.md) and [D-067-01](DECISIONS.md#d-067-01-check-local-route-library-dialog-at-enlarged-text).
- Fresh test APK build succeeded; focused emulator test passed `OK (1 test)` with a healthy Android gate. No repository data or hardware was used.

### In progress

- Phases 60–67 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Evidence covers one synthetic English library at 320 dp / 2× only. The library list requires scrolling at this scale. No full suite, lint, persistence check, or physical-device check was run. Early zero-test runs used the stale repository `app/build` APK instead of the configured temp build output; later test setup failures were corrected and retained in the [Phase 67 report](phase-67-report.md). Only the final fresh-APK run counts as a pass.

### Next

- Choose another bounded UI check or request a separate commit. The Phase 67 test did not change production UI; hardware remains parked unless separately requested.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 66 enlarged-text waypoint row actions

### Done

- Added a focused 320 dp / 2× text check for the route point Edit, Remove, Up and Down controls. Each action can be scrolled into view; labels remain on one line and within the window. First/middle/last move-button states match the route boundaries. Synthetic Move and Remove callbacks stay in memory. Visually inspected the final screenshot; existing FlowRow layout was sufficient, so no production UI change was needed. See [Phase 66](phase-66-report.md) and [D-066-01](DECISIONS.md#d-066-01-check-waypoint-row-actions-at-narrow-width-and-enlarged-text).
- Android test APK built and focused emulator test passed `OK (1 test)` with a healthy Android gate. No saved route or physical hardware was used.

### In progress

- Phases 60–66 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Each action requires the route list to scroll to it; this test covers one 320 dp English fixture at 2× text, not other devices/locales or route persistence. The first overlap assertion compared screen bounds across different scroll positions and failed; it was removed as invalid. The corrected focused test passed. No full suite or lint was run.

### Next

- The accumulated Phase 60–66 diff has been reviewed: changes stay within the listed UI, instrumentation tests and phase records; no new dependency, credential, network destination or hardware command was introduced. Select another bounded UI check or request a separate commit.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 65 enlarged-text waypoint entry

### Done

- Added a focused check for the Add coordinates dialog at a 320 dp window and 2× text. Both fields can be scrolled fully into view; Add and Cancel remain in the dialog, and synthetic valid coordinates dispatch only through an in-memory callback. Visually inspected the captured scrolled state. Existing dialog scrolling was sufficient, so no production UI change was needed. See [Phase 65](phase-65-report.md) and [D-065-01](DECISIONS.md#d-065-01-check-waypoint-entry-at-narrow-width-and-enlarged-text).
- Android test APK built and the focused emulator test passed `OK (1 test)` with the Android health gate healthy. No saved route or physical hardware was used.

### In progress

- Phases 60–65 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Evidence covers one synthetic 320 dp English dialog at 2× text; the coordinate form requires scrolling and this check does not cover all locales, narrower displays or a physical phone. The first build hit a test API mismatch and the first emulator attempt used an uncomposed lazy item; the corrected build/test passed. Full suite and lint were not run.

### Next

- Review the accumulated Phase 60–65 diff before selecting another bounded UI check or requesting a separate commit.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility checks, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 64 enlarged-text mission header

### Done

- Fixed the mission editor header at mobile widths below 680 dp when text scale is at least 1.8: File / Library and Save draft now use a wrapping action row, with the draft title and summary below at full width. The 320 dp / 2× test first reproduced the clipped title, then passed after the fix. Existing normal-text Save behavior also passed. See [Phase 64](phase-64-report.md) and [D-064-01](DECISIONS.md#d-064-01-check-route-editor-actions-at-enlarged-mobile-text).
- Both debug APKs built successfully. The focused emulator test passed `OK (1 test)` and reported a healthy Android gate. No app persistence or vehicle command was exercised.

### In progress

- Phases 60–64 remain uncommitted. Preserve unrelated `desktop.ini`. Physical hardware work remains on hold by user request.

### Broken or risky

- Evidence covers one synthetic 320 dp English layout at 2× text and one existing normal-text save-path test. No full suite, other locales/devices, real phone or hardware acceptance ran. The first test attempt used stale APKs and reported zero tests; this was corrected by installing Gradle's temp-output APKs, and the real assertion failure is retained in the phase report.

### Next

- Review the accumulated Phase 60–64 diff, then continue with another bounded mobile UI layout check or ask for a separate commit request.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken safety/accessibility assertions, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 63 enlarged mobile category tabs

### Done

- Extended the selected-tab regression to 320 dp / 2× text. Bluetooth / HC-05, Accounts & audit and RC & PWM remained fully inside the visible strip without truncation; exit and safety shortcuts stayed visible. Existing layout passed, so app UI code did not change. See [Phase 63](phase-63-report.md) and [D-063-01](DECISIONS.md#d-063-01-check-selected-mobile-tabs-at-enlarged-text).
- Built the Android test APK and passed the focused emulator test 1/1 with a healthy Android gate.

### In progress

- Physical hardware work remains on hold. Phases 60–63 remain uncommitted; Phase 63 added a test and records only.

### Broken or risky

- This test covers one emulator width and three English labels, not every device, language or larger accessibility scale. Phase 62 remains the last broader software-suite run.

### Next

- Review the Phase 60–63 worktree, then decide whether to request a focused commit or move to a distinct UI issue.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken accessibility/safety assertions, or commit/push without a separate request. Preserve `desktop.ini`.

## 2026-10-26 — Phase 62 software Android regression

### Done

- Ran the current 35-class software Android inventory in two disjoint batches against the uncommitted Phase 60/61 debug APKs, excluding the separate physical BLE class. Results: `OK (7 tests)` and `OK (127 tests)`, both with clean Android health gates (zero new crash/ANR events, boot complete, no error window). See [Phase 62](phase-62-report.md) and [D-062-01](DECISIONS.md#d-062-01-run-the-current-software-android-inventory-before-another-ui-change).
- The prior intermittent landscape screenshot test passed within batch two. Fresh landscape Operate and portrait Application settings screenshots were visually inspected; no new overlap was observed.

### In progress

- Physical hardware diagnosis remains on hold by user request. Phase 60/61 source and records remain uncommitted; Phase 62 added documentation only.

### Broken or risky

- The earlier screenshot timeout and emulator boot-service ANRs remain recorded. The full suite passed in two batches, not one uninterrupted run. Blank/neutral map screenshots are not evidence of live tile availability. No real phone, Cube, radio or rover acceptance was performed.

### Next

- Review the Phase 60/61 diff with Phase 62 JUnit/health evidence before deciding on another UI change or requesting a commit. Resume physical diagnosis only by separate user direction.

### Do not touch

- Do not change Cube/radio settings, send vehicle commands, weaken acceptance gates, or commit/push without a separate request. Preserve untracked `desktop.ini`.

## 2026-10-26 — Phase 61 enlarged mobile workspace text

### Done

- Expanded the mobile-header test at 320 dp / 2× text. The header passed, but a real Operate screenshot showed Plan/Drive splitting mid-word. The narrow, large-text map toolbar now widens, calls the third destination More, and places the status card beside it. Normal text keeps the original toolbar width and Systems label. See [Phase 61](phase-61-report.md) and [D-061-01](DECISIONS.md#d-061-01-check-enlarged-mobile-workspace-text-before-changing-layout).
- Both APKs built. The focused 2× toolbar test, 2× header test and normal portrait map/safety test each passed 1/1 with healthy Android state. Fresh JVM suite passed 389/389; lint had 0 errors and 28 warnings. Before/after emulator images were inspected and saved as ignored local evidence. Phase 60 test diagnostics remain separate uncommitted work.

### In progress

- Physical hardware diagnosis remains on hold by user request. The intermittent Phase 59/60 screenshot timeout remains unproven.

### Broken or risky

- Enlarged-text evidence is limited to this one emulator and 320 dp. The wider toolbar covers more map area; the full Android UI suite was not rerun and no physical hardware acceptance follows from these tests.

### Next

- Review Phase 61's UI/test/docs diff and before/after screenshots separately from Phase 60. Watch for overlay crowding on a supported narrower screen, another language or larger accessibility scale.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken UI/safety checks, or commit/push without a separate request. Preserve untracked `desktop.ini`.

## 2026-10-26 — Phase 60 landscape screenshot diagnostics

### Done

- Added diagnostic rejection counts to the existing landscape UI screenshot timeout without changing its nonblank/settled-frame gate. See [Phase 60](phase-60-report.md) and [D-060-01](DECISIONS.md#d-060-01-diagnose-landscape-screenshot-readiness-before-changing-its-gate).
- Built the Android test APK. The focused landscape test passed `OK (1 test)` twice; the settled disposable-emulator run also passed Android health. Visually inspected its real screenshot.

### In progress

- The Phase 59 timeout remains intermittent and its root cause is unproven. Physical hardware work remains on hold by user request.

### Broken or risky

- The first Phase 60 run failed overall because Android logged two boot-service ANRs despite a passing JUnit assertion. Its evidence is preserved. The full Android UI suite was not rerun.

### Next

- If the screenshot timeout recurs, inspect the new counts and Android health evidence before changing the capture gate. Review the Phase 60 test-only diff.

### Do not touch

- Do not change Cube/radio settings, send vehicle commands, weaken screenshot or safety checks, or commit/push without a separate request. Preserve untracked `desktop.ini`.

## 2026-10-26 — Phase 59 readable mobile header

### Done

- The phone header gives the workspace title a full row beside Menu and places the existing connection shortcut below it. The wide header retains its single-row layout. See [Phase 59](phase-59-report.md) and [D-059-01](DECISIONS.md#d-059-01-separate-mobile-workspace-title-from-link-shortcut).
- Fresh software checks passed 389 JVM tests, both APK builds and lint (0 errors, 28 warnings). Focused mobile-title, real-activity connection-shortcut and wide-header geometry tests each passed 1/1 with clean Android health. Normal and 1.3× text screenshots show the full Application settings title.

### In progress

- Physical hardware diagnosis remains on hold by user request. The Phase 59 UI change received a separate commit-and-push request after its initial review.

### Broken or risky

- The older screenshot-heavy landscape workspace test failed at its first screenshot stability wait before its category checks. A direct wide-header geometry test passed afterward, but the screenshot timeout remains unresolved. The taller mobile header reduces available content height. The full Android UI suite was not rerun.

### Next

- Review the Phase 59 UI and test diff. Investigate the separate landscape screenshot timeout if it repeats in a later targeted run; keep its failure evidence. Resume Cube/radio work only if the user requests it.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken screenshot or safety tests, or commit/push without a separate request. Preserve untracked `desktop.ini`.

## 2026-10-26 — Phase 58 mobile tab visibility

### Done

- Narrow-screen workspace navigation now brings the selected tab into view when its route changes, while preserving manual horizontal scrolling and the wide-screen drawer. See [Phase 58](phase-58-report.md) and [D-058-01](DECISIONS.md#d-058-01-reveal-the-selected-mobile-workspace-tab).
- Fresh software verification with temporary local Gradle outputs passed 389 JVM tests, both APK builds, lint (0 errors, 28 warnings), and the focused emulator UI test with a clean Android health gate. Normal and 1.3× text-size screenshots show the selected “Accounts & audit” tab fully visible.

### In progress

- Physical hardware diagnosis remains on hold by user request. The Phase 58 UI change received a separate commit-and-push request after its initial review.

### Broken or risky

- The first focused emulator run passed its UI assertion but failed the health gate because Android boot services logged four ANRs. A settled rerun was clean; both results are preserved. OneDrive cloud placeholders prevented Gradle from snapshotting generated kapt output in the normal build directory, so the complete software check used an untracked temporary build-output override. The full Android UI suite was not rerun.

### Next

- Review the narrow navigation diff and, during a future UI change, watch for distracting tab-strip movement or clipping on still narrower devices. Resume Cube/radio work only when the user requests it.

### Do not touch

- Do not alter Cube/radio settings, send vehicle commands, weaken tests, or commit/push without a separate request. Preserve untracked `desktop.ini`.

## 2026-10-24 — Phase 57 emulator acceptance health gate

### Done

- The software-only acceptance runner now records the matched AVD's GPU launch option and rejects a green JUnit result if post-run Android crash/ANR logs, boot or window health fail. It preserves ignored per-run evidence without clearing Android logs. See [Phase 57](phase-57-report.md) and [D-057-01](DECISIONS.md#d-057-01-gate-emulator-acceptance-on-junit-and-android-health).
- On the disposable SwiftShader AVD, the same 35-class/130-method software suite passed in one sequence, `OK (130 tests)`, with zero new crash/ANR evidence and healthy post-run boot/window checks. A final evidence-file-only correction passed a focused 5/5 smoke run. No physical class ran, and no app or APK code changed.
- Controlled local fake-ADB checks accepted a clean fixture and rejected fresh crash and ANR fixtures; they did not exercise a physical device.

### In progress

- Observe repeatability on future software runs. The full 130-method suite was not repeated after the final empty-log-file marker correction.

### Broken or risky

- The earlier host-GPU System UI crash remains unresolved for that renderer. A software-only SwiftShader pass does not verify a real phone, Cube-origin heartbeat/RC, rover safety, owner review or production release. Android log-buffer rollover can hide earlier events in unusually long/noisy runs.

### Next

- Keep SwiftShader and the health-gated runner for disposable-emulator acceptance; investigate any new failure with the saved JUnit and Android evidence. Resume physical link diagnosis only under separate authorization and a secured bench setup.

### Do not touch

- Do not alter Cube/radio settings, send rover commands, clear real app data, weaken assertions, or commit/push without a separate request. Preserve untracked `desktop.ini` and earlier documentation edits.

## 2026-10-24 — Phase 56 emulator graphics diagnosis

### Done

- Captured a monolithic software-only UI run with filtered Android logs. Under the existing host-GPU renderer, System UI crashed in `HardwareRenderer` context destruction at method 30 and instrumentation aborted. The exact 46-method sequence had passed immediately before that run. See the [Phase 55 diagnostic follow-up](phase-55-report.md#2026-10-24-follow-up--phase-56-emulator-graphics-comparison).
- Restarted only the disposable AVD without wiping data using its installed SwiftShader renderer. The same 35-class, 130-method software-only suite passed in one sequence (`OK (130 tests)`), followed by a booted/no-error-window check. No app, test, dependency or build code changed; the physical-hardware class remained excluded.

### In progress

- Confirm renderer stability on future emulator runs and keep the host-GPU crash visible as a test-environment failure. This comparison supports a graphics-backend issue but does not prove a specific driver root cause.

### Broken or risky

- The host-GPU emulator cannot currently be trusted for long-run UI acceptance on this machine. The SwiftShader pass is software-only and says nothing about the physical Cube/R12/T12 telemetry path, rover stopping, production readiness, or the release gate.

### Next

- Use the documented disposable-AVD SwiftShader procedure for subsequent full UI acceptance; preserve failed logs and stop on any new ANR/crash. Resume separately authorized hardware diagnosis only when its setup is available.

### Do not touch

- Do not alter Cube/radio settings, send rover commands, clear a real app installation, weaken assertions, or commit/push without a separate request. Preserve pre-existing `desktop.ini` and prior documentation edits.

## 2026-10-24 — Phase 55 software UI retest and emulator-state diagnosis

### Done

- The exact previously crashing 52-method sequence passed 52/52, including `BleReceiveConfirmationTest` teardown. The other initial groups passed 7/7 and 25/25. The final 46-method group completed with 41 passes and five failures; the failures are preserved in [Phase 55](phase-55-report.md).
- After a no-wipe restart of only the disposable emulator, the five failed methods passed in focused checks, and the 46-method portion passed in three disjoint groups (23/23, 17/17, 6/6). Thus all 130 inventoried software-only UI methods have a passing bounded run, **not** a clean long-sequence run. No app or test assertions were changed.

### In progress

- Reproduce and diagnose the long-sequence Compose idle, PixelCopy, RC-readiness and Android document-picker window failures on a controlled emulator before changing code or declaring a clean full regression. The prior ActivityScenario/system crash did not recur, but its root cause is unproven.

### Broken or risky

- The first 46-method run failed 5/46, including a recurrent picker `foreground=null` timeout. A focused run on the same emulator still failed three methods; all three passed after the no-wipe restart. Sequence/emulator-state sensitivity is supported, but app/test defects are not ruled out. Physical Cube heartbeat, RC and safety acceptance remain unverified.

### Next

- Capture lifecycle, graphics and window diagnostics during a reproducible failing sequence, then make only a scoped, evidence-backed repair and rerun the affected and full software inventory. Do not treat bounded green groups as a production release gate.

### Do not touch

- No physical rover/phone commands, Cube/radio settings, real app data wipe, assertion weakening, or commit/push without separate authorization. Preserve the pre-existing untracked `desktop.ini`.

## 2026-10-24 — bounded screenshot-test repair attempt

### Done

- Added one test-only bounded Android screenshot helper and applied it to three BLE fixture tests that had null one-shot captures. The affected methods passed together (3/3); after a no-wipe emulator restart, the four related BLE classes passed 17/17. The BLE/map batch passed 7/7 before helper consolidation. Exact failures, corrections and evidence are in [Phase 54 follow-up](phase-54-report.md#2026-10-24-follow-up--bounded-system-screenshot-capture).

### In progress

- Full software UI regression remains **not clean**. The 52-method rerun passed the formerly failing BLE connection screenshot but hit an unrelated ActivityScenario teardown failure, then aborted at method 9 when Android reported a system crash. Other software batches were not rerun after the helper change.

### Broken or risky

- The emulator produced ADB input/output errors after the system crash; a no-wipe restart recovered it. The two screenshots were then retrieved and visually inspected, and the four related BLE fixture classes passed 17/17. Do not treat this as a completed 52-method run. Earlier home-marker/picker batch failures and physical Cube-origin heartbeat/RC verification remain open.

### Next

- Diagnose the separate lifecycle/system-crash failure, then rerun the reviewed software-only inventory on the recovered disposable emulator.

### Do not touch

- Do not declare physical or production readiness, change Cube/radio settings or command availability, clear populated app data, or commit/push without a separate request. Preserve `desktop.ini` and unrelated edits.

## 2026-10-24 — Phase 54 test-only follow-up

### Done

- Rebuilt and installed the restored-source Android test APK on the disposable emulator. Both previously failing home-marker/picker methods passed together (2/2). Fresh JVM tests passed 389/389; lint had 0 errors, 28 warnings. Details and evidence are appended to [Phase 54](phase-54-report.md#2026-10-24-follow-up--test-only-rerun-on-restored-source).

### In progress

- The software UI suite is still **not clean**. The seven-method map/BLE batch had a null Android screenshot failure (6/7); that method passed alone. The remaining 33 software UI classes were not rerun in this follow-up. Diagnose screenshot/window synchronization without weakening assertions, then rerun the entire software inventory.

### Broken or risky

- Prior batch home-marker/picker failures and the new screenshot failure remain recorded. Isolated passes do not establish sequence reliability. Physical Cube-origin heartbeat/RC and owner/release gates remain unverified.

### Next

- Scope a test-environment/screenshot synchronization investigation, preserving visual evidence and non-green runs. Then rerun all reviewed software UI classes. Hardware acceptance stays separate.

### Do not touch

- Do not declare physical or production readiness, change Cube/radio settings or command availability, clear populated app data, or commit/push without a separate request. Preserve `desktop.ini` and prior documentation edits.

## 2026-10-24 — Phase 54 synthetic UI-test reliability investigation

### Done

- Investigated two intermittent test failures. A scroll-before-visibility edit was reverted because it weakened the original assertion; a Compose screenshot replacement was reverted after a new PixelCopy failure. Final test and production code match `edda04a`; hardware was untouched. On the provisional APK, focused UI 4/4, map 7/7, and software batch 1 52/52 passed. All 130 reviewed software UI methods completed across four disjoint batches; exact checks, failures and later correction are in [Phase 54](phase-54-report.md).

### In progress

- The software UI regression is **not clean**: batch 2 was 24/25 and batch 3 was 45/46, although both failed methods passed alone. The final restored test source was not rerun through the full inventory. Diagnose home-marker rendering/capture, screenshot reliability and document-picker foreground behavior. Physical Cube-origin heartbeat/RC acceptance and owner/release gates remain open.

### Broken or risky

- A saved synthetic map screenshot shows the purple home point with white border but no white H; the pixel test correctly failed. This contradicts any claim of consistent home-marker legibility from the earlier focused pass. The picker foreground became `null` after Save in a long batch. The replacement Compose screenshot also failed in PixelCopy and was not retained. The previous long-batch ActivityScenario teardown timeout remains unresolved. Emulator tests do not establish physical safety.

### Next

- Scope a separate renderer/timing investigation for the intermittent missing H and a picker lifecycle/accessibility investigation, then rerun the full software inventory without weakening assertions. Separately await the hardware team's secured Cube-heartbeat trace.

### Do not touch

- Do not declare production or physical readiness; do not enable movement, arming, stop or actuator commands; do not alter Cube/radio settings, dismiss ANRs, clear populated app data, or commit/push without a separate request. Preserve `desktop.ini` and the pre-existing 23 October report edits.

## 2026-10-23 — Pre-Phase 54 broad software retest

### Done

- At `edda04a` on `main`, fresh JVM tests passed 389/389 (64 suites); debug app and Android-test APKs built; lint had 0 errors and 28 warnings. The map/UI batch passed 7/7. The other 33 software-only Android classes ran 123 tests, with 121 passes and 2 failures; both failures passed in a focused rerun. Exact commands, failures and ignored evidence paths are in [today's test report](test-report-2026-10-23.md#pre-phase-54-broad-software-retest--23-october-2026). No code or hardware was changed.

### In progress

- Identify and stabilize the sequence-dependent Android test failures before calling the full suite clean. Physical Cube-origin heartbeat and RC acceptance, independent owner review and release gates remain open.

### Broken or risky

- The first large Android batch had one home-position assertion failure and then an Android system crash; the full rerun had an activity-cleanup timeout and a null screenshot capture. Focused reruns passed but do not erase those failures. This is a software regression exercise on a disposable emulator, not physical acceptance. Existing untracked `desktop.ini` was left untouched.

### Next

- Diagnose the test isolation/lifecycle and screenshot-capture issues in a bounded phase if approved; then rerun the full suite. Separately await a secured Cube-heartbeat trace through R12/T12 before live telemetry acceptance.

### Do not touch

- Do not infer hardware readiness from emulator tests, enable movement/arming/stop controls, change Cube/radio settings, dismiss ANRs, clear populated app data, or commit/push without a separate request.

## 2026-10-23 — Phase 53 home-marker visual gate accepted on disposable emulator

### Done

- The focused Phase 52 home-marker Android test passed `OK (1 test)` on the disposable Android 34 emulator from the moved Desktop checkout. Its synthetic screenshot was visually inspected: a white H is legible on the purple home marker, separate from the green vehicle point. Post-test Android had no ANR/error dialog. See [Phase 53](phase-53-report.md) for the exact command and ignored evidence paths.

### In progress

- Physical Cube-origin heartbeat localization and independent owner/release gates remain open. The Phase 52/53 changes are uncommitted; no commit or push was requested.

### Broken or risky

- Earlier Android System UI ANRs remain recorded. This one offline fixture at one emulator size does not prove appearance across devices, live telemetry or physical safety. The H nearly reaches the purple circle edge. The original checkout was moved to `C:\Users\swastik\OneDrive\Desktop\mama_gcs`; untracked `desktop.ini` was not touched.

### Next

- Keep the monitoring-only boundary. Await the hardware team's secured Cube-heartbeat trace; before any release, complete independent owner review, recovery and physical acceptance. If the home marker is revised further, rerun its pixel/expiry test and inspect a new screenshot.

### Do not touch

- Do not infer hardware acceptance from the emulator, change Cube/radio settings, enable vehicle commands, clear a populated app, or commit/push without a separate request.

## 2026-10-23 — Phase 52 visual-gate completion attempt, emulator blocked

### Done

- Retried the focused home-marker acceptance on the disposable Android 34 emulator. The first boot showed an Android System UI ANR; the second did not complete Android boot. Post-run file timestamps indicate the second boot's `-wipe-data` reset the **disposable AVD's** data despite a separate `-datadir` argument. Neither task-started emulator dismissed an ANR. See the [Phase 52 follow-up](phase-52-report.md#23-october-follow-up--requested-visual-gate-completion-attempt).

### In progress

- The post-change purple-plus-white pixel test and screenshot visual review remain unrun; the Phase 52 icon-size change is still provisional. Hardware-team Cube-heartbeat localization and owner/release gates remain open.

### Broken or risky

- The disposable emulator is currently not a healthy UI-test environment; its data may have been reset by the second boot. Existing JVM/build/lint results do not prove a rendered H, and the earlier weaker UI pass predates the change. No physical hardware or operator phone was tested.

### Next

- Restore a healthy disposable emulator, then run only the focused home-marker test and inspect its synthetic screenshot. If the H fails, investigate the MapLibre symbol path without relaxing assertions.

### Do not touch

- Do not dismiss Android ANRs, repeat the `-datadir`/`-wipe-data` combination without verifying its effect, clear a populated app, use a physical phone as a substitute, change hardware settings or enable controls. Do not commit/push without a separate request.

## 2026-10-23 — Phase 52 home-marker legibility, visual acceptance pending

### Done

- Enlarged the local in-memory H icon on the offline map and added a focused Android assertion for both purple marker and white center pixels. The prior Phase 51 screenshot showed a purple point but no discernible H. Fresh post-source-change JVM/build/lint checks passed: 389/389 JVM tests, both debug APKs built, lint 0 errors/28 warnings. See [Phase 52](phase-52-report.md).

### In progress

- Post-change pixel test and screenshot visual review remain unaccepted. The disposable emulator repeatedly showed an Android System UI ANR before that test could start; no dialog was dismissed. Hardware-team Cube-origin heartbeat localization and owner/release review remain open.

### Broken or risky

- The H-size adjustment is provisional until rendered on a healthy emulator. A pre-change, weaker white-pixel test passed once, but its screenshot was unavailable afterward and it did not verify purple pixels. It cannot establish post-change visual acceptance. The 28 lint warnings remain. No physical hardware was tested.

### Next

- Run the focused Phase 52 offline-map test on a healthy disposable emulator, inspect its screenshot for H legibility at the purple home point and verify freshness removal. If the icon is still absent, diagnose MapLibre symbol rendering without relaxing the test. Independently await the hardware team's secured heartbeat trace.

### Do not touch

- Do not bypass/dismiss an emulator ANR, use a populated phone as a test substitute, alter Cube/radio wiring or settings, or enable vehicle commands. Do not commit/push without a separate request.

## 2026-10-23 — Phase 51 software-only lint cleanup

### Done

- Audited the prior 32 lint warnings and removed four source-level warnings through Compose optional-`modifier` ordering and the existing KTX bitmap helper. Fresh lint: 0 errors/28 warnings; fresh JVM: 389/389 passed; debug and Android-test APKs built. Focused read-only-emulator UI batches passed 9/9, 23/23 and 1/1. See [Phase 51](phase-51-report.md) for exact commands, warning categories and ignored evidence paths.

### In progress

- Hardware-team Cube-origin heartbeat localization, remaining lint disposition and independent owner/release review remain open.

### Broken or risky

- The local-style map screenshot shows the purple home point, but the small white H glyph is not discernible, so that visual detail is not accepted. The 28 remaining warnings include dependency/toolchain version notices and two policy/configuration warnings. None was suppressed. No physical hardware was tested.

### Next

- If UI polish is pursued, investigate the H glyph with a targeted visual test; otherwise await the hardware team's secured read-only heartbeat trace before repeating a phone-side telemetry check.

### Do not touch

- Do not upgrade dependencies, change Bluetooth permission/orientation policy, touch Cube/radio settings or wiring, or enable vehicle commands as part of lint cleanup. Do not commit/push without a separate request.

## 2026-10-23 — Phase 50 radio-status diagnostics

### Done

- Added CRC-validated MAVLink `RADIO_STATUS` (ID 109) parsing and a separate, session-scoped radio diagnostic. The app now labels radio-only reception without treating it as a Cube heartbeat, RC data or vehicle admission. See [Phase 50](phase-50-report.md).
- Final fresh JVM run: 389 tests in 64 suites, 0 failures/errors/skips. Debug APK, Android-test APK and lint passed before a JVM-test-only export-boundary addition; final focused emulator run passed 5/5 UI tests, and a related screen batch passed 27/27. Synthetic radio screens were visually inspected. Exact commands and evidence locations are in the phase report.

### In progress

- The hardware team is localizing where the Cube-origin heartbeat stops. Independent human review and release gates remain open.

### Broken or risky

- Physical Cube/R12/T12/Bluetooth behavior was not retested in this phase. A valid radio frame is unauthenticated radio evidence, not proof of vehicle telemetry, RC reception or safe operation. Lint's 32 warnings were not individually dispositioned.

### Next

- Obtain a secured read-only trace at Cube TELEM2, R12 UART2 and the T12 phone-side stream; compare valid Cube heartbeats at each hop. Keep vehicle controls unavailable.

### Do not touch

- Do not change wiring or Cube/radio parameters, run actuators, or treat radio-only status as hardware acceptance. The user separately authorized committing and pushing this phase on 23 October.

## 2026-10-23 — blank draft persisted across app restart

### Done

- With operator approval, created a new two-point synthetic local route from an empty draft on the read-only disposable emulator, saved it, force-stopped only Mama GCS, relaunched it and reopened Plan. The app process ID changed `4406` → `5208`; the two-point count, 15,724 m estimate and **No unsaved changes** remained. Android showed no ANR/crash dialog. See the [23 October follow-up](test-report-2026-10-23.md#blank-draft-and-process-restart-follow-up--23-october-2026).

### In progress

- Independent human operator acceptance and hardware-team Cube-origin heartbeat localization remain open.

### Broken or risky

- Synthetic emulator persistence does not establish geospatial safety, Cube telemetry, physical control acceptance or production readiness. Waypoint 2 was not separately re-inspected after restart.

### Next

- Await the secured, read-only Cube heartbeat trace across TELEM2, R12 UART2 and T12 Bluetooth; keep the monitoring-only boundary. If release work resumes, obtain independent operator review and the remaining defense/recovery gates.

### Do not touch

- Do not change Cube/radio settings, wiring, actuators or populated phone data to advance this software check.

## 2026-10-23 — manual local-route walkthrough

### Done

- On the read-only disposable emulator, manually reordered the two points in the existing saved fixture, saved, navigated away and reopened Plan, exported GeoJSON to local Downloads, previewed and—after explicit operator approval—imported the same file, then saved again. The two coordinates and their order matched the export. Route review and dark/light layouts were visually inspected; upload/start remained unavailable. The final Android window check showed no ANR/crash dialog. See the [23 October test report](test-report-2026-10-23.md#manual-local-route-walkthrough--23-october-2026).

### In progress

- Hardware-team Cube-origin heartbeat localization and independent human operator acceptance remain open.

### Broken or risky

- This reused an existing two-point fixture, reopened the Plan screen rather than restarting the app process, and did not validate blank-draft creation or physical telemetry. The emulator export is local test data, not a vehicle mission.

### Next

- Await the secured read-only heartbeat trace at Cube TELEM2, R12 UART2 and phone-side T12 Bluetooth. If needed for release, separately have an operator complete a blank-draft and process-restart path.

### Do not touch

- Do not alter Cube/radio settings, wiring, actuators, populated phone data or the monitoring-only command boundary to advance this test.

## 2026-10-23 — broad software regression

### Done

- Fresh JVM run: 383/383 passed, 0 skipped. Debug/test APK builds and lint passed (0 errors, 32 warnings). On a read-only emulator, both software-only Android batches passed: 7/7 map/BLE-report and 121/121 other tests, with no ANR/crash dialog before or after. Original-resolution map captures and a disconnected app-launch screenshot were inspected. See the [23 October test report](test-report-2026-10-23.md).

### In progress

- Physical Cube-origin heartbeat localization and an independent manual operator path remain unverified.

### Broken or risky

- Four physical BLE-remote methods were excluded, and emulator tests do not verify Cube/R12/T12 telemetry or physical safety. The full software result is not production readiness.

### Next

- Have the hardware team trace Cube heartbeat read-only through TELEM2 → R12 UART2 → T12 Bluetooth on the identified setup; then compare Mama GCS. Keep physical controls unavailable.

### Do not touch

- No Cube/radio settings, wiring, actuators, populated phone data, production secrets or historical test evidence should be altered as a shortcut.

## 2026-10-23 — Phase 49 emulator acceptance completed

### Done

- On a read-only disposable emulator, the focused `ClassicConnectionCheckUiTest` passed all 3 tests after correcting a test-only Compose semantics assertion. Light/dark diagnosis-card screenshots were visually inspected; pre/post Android window checks showed no ANR/crash dialog. The earlier ADB block and first failed assertion remain in the [Phase 49 report](phase-49-report.md).

### In progress

- Physical Cube-origin heartbeat tracing remains separate; synthetic UI acceptance does not resolve the hardware telemetry path.

### Broken or risky

- The full Android suite, manual installed-app path and physical acceptance were not run in Phase 49. No rover-control capability is approved.

### Next

- Perform the hardware-team-secured read-only comparison at Cube TELEM2, R12 UART2 and the phone-side T12 Bluetooth link. Consider radio-only `RADIO_STATUS` diagnostics only after separate review.

### Do not touch

- Do not change Cube/radio parameters or wiring, run actuator commands, clear the populated phone, or interpret synthetic test data as physical proof.

## 2026-10-23 — T12 radio-status comparison

### Done

- Reconciled the 22 October QGroundControl and Mama GCS screenshots in the [hardware record](hardware-integration.md#23-october-2026--phone-side-comparison-and-next-isolation-test). QGC parsed `RADIO_STATUS (109)` from component 68 on the tested Bluetooth link; the operator did not see a Cube `HEARTBEAT (0)`. Mama GCS's separate Classic session had 126,757 bytes but zero supported decoded messages. Its parser intentionally does not decode `RADIO_STATUS`, so those results are compatible and do not identify a failed hardware hop.
- Recorded the operator's R12 UART2, crossed TX/RX, 57,600 baud and forwarding reports as reports, alongside the earlier live Cube SERIAL2 readback. No app or hardware behavior changed.

### In progress

- Localize where Cube-origin heartbeat stops using a secured read-only comparison at Cube TELEM2, R12 UART2 and the phone-side T12 Bluetooth link. Phase 49 emulator visual acceptance remains open separately.

### Broken or risky

- QGC radio-status reception establishes a radio message, not Cube liveness. Raw start-byte and checksum counters are not validated packet-loss or fault-location measurements. No physical telemetry or control acceptance is established.

### Next

- Ask the hardware team for a bounded same-setup heartbeat trace with source IDs at each hop. If QGC receives a Cube heartbeat on the same Bluetooth path while Mama GCS does not, inspect app transport/parser/source filtering. A later approved app phase may add validated, radio-only `RADIO_STATUS` visibility without satisfying Cube heartbeat gates.

### Do not touch

- Preserve existing Phase 49 documentation edits and historical evidence. Do not change Cube/radio parameters or wiring, run actuator commands, or treat a radio message as an authenticated vehicle.

## 2026-10-22 — Phase 49 visual acceptance attempt

### Done

- Attempted the approved disposable-emulator visual acceptance for the Phase 48 Classic stream diagnosis. Existing debug and Android-test APKs were available.
- ADB could not start its server and listed no emulator/device, so no APK was installed and no synthetic UI or physical-phone test was run. See [Phase 49](phase-49-report.md).

### In progress

- Phase 49 remains open pending a healthy disposable emulator and functioning ADB server.

### Broken or risky

- This is an environment block, not visual acceptance. Do not claim the diagnosis card passed screenshot review. Do not dismiss Android ANRs or use the populated phone as a software fixture.

### Next

- Repair/start the disposable emulator and ADB server, then run `ClassicConnectionCheckUiTest` with pre/post Android health checks and inspect light/dark captures.

### Do not touch

- Do not alter Cube/R12/T12 wiring or parameters, operate the rover, clear app data, or use the physical phone for instrumentation.

## 2026-10-22 — Phase 48 Classic telemetry-stream diagnosis

### Done

- Added a read-only diagnosis card to **Application settings → Bluetooth / HC-05** only for fresh Classic bytes with no supported validated MAVLink. Existing counters select one bounded category: no MAVLink start-byte candidates, rejected frame candidates, unverified unsupported-ID candidates or no more-specific validated-frame evidence. It names a safe next check but explicitly cannot determine wrong baud, R12 UART, TX/RX, noise or any physical cause. See [Phase 48](phase-48-report.md) and [decision D-048-01](DECISIONS.md#d-048-01-classify-undecoded-classic-receive-evidence-without-identifying-a-physical-fault).
- Fresh verification on 22 October: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed in 7m57s. XML reports 383 JVM tests across 63 suites, 0 failures/errors/skips; both APKs compiled; lint reports 0 errors/32 warnings. The synthetic Android UI test compiled but did not execute because no verified disposable device was available in this session.
- Updated the hardware record with a user-supplied 21 October Mission Planner live readback: current `SERIAL2_PROTOCOL=2` and `SERIAL2_BAUD=57` (MAVLink 2 / 57600). The older accepted `.param` file differs and must not be written to the Cube as a reconciliation step.

### In progress

- Hardware acceptance remains blocked on the receiver side: identify the R12 UART carrying the Cube TELEM2 route, TX/RX/GND continuity, and its serial forwarding configuration. Current Classic bytes have not produced a supported MAVLink message, Cube heartbeat or RC sample.

### Broken or risky

- The Phase 48 diagnosis is a UI interpretation of existing counters, not a test of wiring, receiver configuration, baud, electrical signal quality, radio forwarding, Cube identity or safe operation. Movement, arming and app emergency-stop commands remain unavailable.
- Android visual acceptance for this new card remains open. The known disposable-emulator/System UI ANR history is unchanged; do not use a populated phone as an instrumented test fixture or dismiss an ANR to obtain a pass.

### Next

- Run the focused `ClassicConnectionCheckUiTest` on a healthy verified disposable emulator, inspect the diagnosis card in light/dark themes and record post-run Android health. Separately, after the R12 details are established with a secured read-only procedure, repeat the Classic receive test and require supported decoded MAVLink, a selected-autopilot heartbeat and fresh `RC_CHANNELS`.

### Do not touch

- Do not write Cube parameters, infer receiver pinout from wire colors, disconnect/reconnect live wiring, move sticks, arm, or issue vehicle commands. Preserve the older parameter export and historical bench evidence.

## 2026-10-21 — physical Classic telemetry follow-up

### Done

- The operator confirms receiver telemetry is wired to Cube **TELEM2**, separately from **SBUS → RCIN**. The supplied photos show TELEM2 occupied and the R12 Multi Link V1.0 UART1/UART2/SBUS labels, but the connector hides which UART pair and exact signal pin order are used.
- Read-only phone inspection during an active Classic session (`T12_131`) found 202 chunks / 53,084 bytes, 2,392 MAVLink 1 start-byte candidates, 0 MAVLink 2 candidates, 0 supported decoded messages, 782 checksum failures and 1,597 unverified unsupported-ID candidates. Last input was 0 seconds ago. Mama GCS showed `Link lost · Setup` and `No sample received`.

### In progress

- Hardware person to identify whether the TELEM2 cable uses R12 UART1 or UART2 and verify TX↔RX, RX↔TX and GND wiring with power off. No physical pin order is inferred from wire color or the label.
- The live Cube `SERIAL2_PROTOCOL=2` / `SERIAL2_BAUD=57` readback is recorded in the current hardware record. Preserve the different older parameter file, but do not write it to the Cube.

### Broken or risky

- Current app receive is byte-active but has no validated supported MAVLink, heartbeat or RC sample. This is not Cube-link acceptance and does not authorize movement, arming or any other hardware command.
- The Phase 47 implementation-time note that hardware was unavailable is historical; this later same-day bench observation supersedes it for current status. It does not retroactively validate the telemetry stream.

### Next

- Obtain the R12 UART choice/pin map and a fresh read-only SERIAL2 parameter readback; then repeat the bounded Classic receive check and require supported decoded messages, a selected-autopilot heartbeat, and fresh RC_CHANNELS before claiming telemetry acceptance.

### Do not touch

- Do not change Cube parameters, disconnect/reconnect live wiring, move sticks, arm, or issue vehicle commands as part of this diagnosis. Preserve the populated phone and all historical test evidence.

## 2026-10-21 — Phase 47 plain-language Classic UI

### Done

- Added a short stage-derived explanation above the existing Bluetooth Classic/HC-05 technical checklist, without changing receive assessment, parser, transport, trust filtering, storage or command behavior. See [Phase 47](phase-47-report.md) and [decision D-047-01](DECISIONS.md#d-047-01-add-plain-language-classic-receive-status-without-changing-evidence).
- Fresh final Gradle run: 382 JVM tests/63 suites, 0 failures/errors/skips; app and Android-test APKs built; lint 0 errors/32 warnings. The new synthetic Android test compiled but did not execute.

### In progress

- Visual acceptance of the new text in light/dark themes is pending a healthy verified disposable emulator. Phase 46's targeted visual check is separately recorded; broader UI visual acceptance remains open.

### Broken or risky

- The disposable emulator showed a `com.android.systemui` ANR before Phase 47 APK installation/instrumentation; it was stopped without dismissing the dialog or clearing data. This repeats the environmental risk already recorded in Phase 46 and is not a passing visual check.
- At implementation time, hardware was unavailable. Earlier Classic bytes were not validated as supported MAVLink. A prior read-only Mission Planner observation was recorded as TELEM1/MAVLink2/57600, but later user-confirmed wiring is TELEM2 and the accepted parameter export disagrees with the recorded SERIAL2 settings; see the newer physical telemetry follow-up above. Neither Cube identity nor physical control readiness is established.

### Next

- On a healthy verified disposable emulator, run the focused `ClassicConnectionPlainLanguageUiTest`, inspect its two theme captures and Android health, then run the broader current UI inventory before any release claim. Keep the separate secured hardware diagnosis for when the identified setup is available.

### Do not touch

- Preserve Phase 46's existing dirty files and the operator's populated phone. Do not weaken safety wording, dismiss ANRs, modify hardware settings, enable vehicle commands or commit/push without authorization.

## 2026-10-21 — Phase 46 visual acceptance attempt

### Done

- Created a fresh disposable API 34 `MamaPhase46` AVD under ignored `app/build/` and installed the Phase 45 app/test APKs on that AVD only. See the [Phase 46 report](phase-46-report.md) for the test choice, legacy AVD-manager failure and exact scope.
- The first acceptance attempt stopped before instrumentation because Android System UI showed an ANR. A clean lower-memory retry then passed the original `ClassicConnectionCheckUiTest` **3/3** with no post-run Android error; synthetic light/dark top-of-panel captures were inspected and showed legible checklist rows. The safety guidance was below the captured viewport.
- Strengthened only that synthetic UI test to assert/record bottom-of-page visibility in both themes. Test APK rebuild and lint passed (0 errors/32 warnings). An ANR blocked the first attempt with the updated APK, but a final clean boot without reinstall passed the strengthened fixture **3/3**; four light/dark top/bottom captures were inspected and post-run Android health was clean. Evidence and both earlier ANRs remain in [Phase 46](phase-46-report.md). The verified disposable emulator was stopped without clearing data or dismissing dialogs. Production app and hardware code did not change.

### In progress

- Targeted Phase 45 Classic checklist visual acceptance is complete. No further Phase 46 test is running. Broader Android suite and physical acceptance remain separate open work.

### Broken or risky

- `com.android.systemui` ANR recurred after APK installs, including once with about 2.4 GB host RAM free. Clean-boot tests passed, but causation and broader emulator stability are not established. Do not dismiss ANRs or treat this targeted pass as a full UI/endurance qualification.
- The latest physical Classic stream still lacks validated supported MAVLink, heartbeat and RC evidence. The safety/release gaps below are unchanged.

### Next

- Before a release claim, run the complete current UI inventory on a healthy disposable emulator and complete the release gate. Keep the separate secured read-only Cube/R12/T12 bench observation for when that setup is available; the latest real stream is still undecoded.

### Do not touch

- Do not run synthetic UI fixtures on a populated phone, clear operator data, weaken the acceptance runner, change Cube/radio/VESC settings or enable vehicle commands. No commit/push was requested for this phase.

## 2026-10-21 — Phase 45 Classic RC receive evidence

### Done

- From clean `main` at `ced11d690dbdad392bdb4b9ab465266feb59c3d7`, Phase 45 added a separate, read-only fresh `RC_CHANNELS` indication to the Classic checklist and explicit evidence report. It requires current Classic session, fresh selected-autopilot heartbeat, accepted RC observation and fresh RC state. Parser, transport, source selection, storage, parameters and command paths did not change. See [Phase 45](phase-45-report.md) and [decision D-045-01](DECISIONS.md).
- Fresh Gradle run completed: 381 JVM tests/62 suites, no failures/errors/skips; debug and Android-test APK builds passed; lint 0 errors/32 warnings. Instrumentation compiled but did not run. The user subsequently authorized a focused Phase 45 commit and push; check Git history and remote status for their actual outcome.

### In progress

- Emulator visual execution is open: ADB had no attached device and existing AVDs were not established as disposable. The new UI test compiled but has no executed screenshot review.

### Broken or risky

- Latest supplied physical bench session still shows Classic bytes but zero validated supported MAVLink; no current Cube heartbeat or RC has been independently observed. The new RC indicator will remain `NOT CONFIRMED` until the existing receive chain validates such data. Its `FRESH` state would still not prove physical stick movement, SBUS wiring or safe control.
- The Phase 44 release, speed/failsafe, authenticated-source, recovery and ANR caveats below remain unresolved. Do not label the app rover-control-ready.

### Next

- On a verified disposable emulator with adequate resources, run `ClassicConnectionCheckUiTest`, inspect the captured checklist in both themes, and record device health after the run. Do not use a populated phone as a test fixture.
- If the identified Cube/R12/T12 setup is available under a hardware-team-secured read-only procedure with outputs isolated, compare current Classic byte counts, checksum-validated supported messages, fresh selected-autopilot heartbeat and RC_CHANNELS. Preserve a sanitized evidence report. No parameter changes, arming or actuator commands.

### Do not touch

- Preserve the accepted [hardware record](hardware-integration.md) and all historical failures. Keep vehicle commands unavailable; do not infer hardware safety or identity from a receive indicator. Do not clear app data, operate hardware, or commit/push without the corresponding authorization.

## 2026-10-19 - Phase 44 documentation baseline

### Done

- Phase 44 adds repository instructions, this handover, [decisions](DECISIONS.md), [testing](TESTING.md) and [rollback](ROLLBACK.md) guidance; see the [phase report](phase-44-report.md) for validation and scope. No app, build, dependency, test or hardware behavior is changed.
- Starting source baseline: `c0406127ec3e2c84af60c514531924dc777c0816` on `main`, clean and synchronized with `origin/main` at inspection. The implementation task left Phase 44 uncommitted; the user subsequently authorized committing and pushing this seven-document change. Use Git history and remote status for the resulting revision/synchronization, not the starting baseline.
- Implemented app scope: read-only supported MAVLink reception over Classic/BLE/UDP; diagnostics and explicit report exports; RC/PWM and local RC activity observation; online maps; local mission drafting/recovery/library/exchange; local parameter-file review; local accounts and encrypted account/audit storage. See [README](../README.md) for precise limits.
- Historical Phase 43 evidence: 380 JVM tests in 62 suites passed; the final targeted UI run passed 14 tests, including repaired PWM switching. Lint: 0 errors, 32 warnings. These tests are not rerun by Phase 44; [TESTING](TESTING.md) separates their scope from earlier full-suite evidence.
- Phase 44 documentation checks passed: seven-document scope, 112 relative links, one heading fragment, seven PowerShell examples parsed without execution, whitespace/conflict/fence checks and scoped obvious-secret-pattern review. These are documentation checks, not app or hardware tests.

### In progress

- Phase 44 documentation implementation and verification are complete. The commit/push follow-up is authorized; that request does not establish human diff review, release approval or hardware acceptance. No application implementation or hardware test is running. A subsequent technical phase needs its own bounded proposal and approval.

### Broken or risky

- Latest supplied physical bench screenshots show Classic bytes but no validated supported MAVLink messages; current Cube heartbeat/RC reception is not established. Root cause is unproven. Earlier user-reported reception and remote-only GATT continuity are not acceptance of this failing session.
- The final 14-test UI run passed its assertions, but a post-run Android system ANR was observed. Do not call it an error-free stability soak or a full current-suite run.
- Actual speed enforcement, physical stop, link-loss response, manual takeover and motor behavior remain unverified. Controls remain unavailable. Local accounts and CRC do not authenticate the vehicle.
- The accepted file's cruise target conflicts with the confirmed 8 km/h maximum and its failsafe settings do not establish a guaranteed stop. Preserve these recorded gaps; do not silently change parameters.
- No production-qualified artifact/recovery drill or owner teach-back is established. Deeper flow/data/threat/failure/defense records and release security/device testing remain future work, not completed by these documents.

### Next

- Propose a read-only bench diagnosis of the current serial stream: distinguish transport bytes, validated frames, selected-autopilot heartbeat and fresh RC_CHANNELS using existing diagnostics. First obtain current setup availability/authorization, not already-recorded model/baud/speed answers. No arming, parameter changes or actuator commands.
- Before a release claim, run the complete current software inventory on a healthy environment, complete scoped hardware acceptance, preserve evidence and complete the [release gate](TESTING.md).

### Do not touch

- Preserve the [accepted hardware/parameter record](hardware-integration.md). It retains Cube Orange family, Skydroid T12/R12 Multi Link V1.0, remote-name history, user-confirmed TELEM2 plus separate SBUS/RCIN, the parameter-file values and 8 km/h maximum. Exact R12 UART pins and the current live SERIAL2 configuration remain unresolved. These are not independently verified physical behavior. Do not repeat old questions without a new conflict.
- Keep command paths unavailable; protect auth, trust filtering, storage and safety boundaries under [security](security.md) and [project instructions](../AGENTS.md).
- No raw telemetry or user secrets in Git; no app-data clearing/uninstall, destructive rollback, automatic commit/push or hardware operation. Preserve old reports and unrelated changes.
