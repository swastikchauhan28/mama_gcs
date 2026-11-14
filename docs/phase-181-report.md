# Phase 181 — Route readiness warning coverage

The Review tab's existing readiness projection now has focused coverage for duplicate consecutive points, outside-outline waypoints, legs that leave an outline, and invalid outline geometry. JVM assertions check the exact affected point or leg text. A real-app emulator flow creates a duplicate leg, corrects it, adds an outline and an outside point, then moves that point inside. It checks `NEEDS CHANGES` and `READY FOR REVIEW · LOCAL ONLY` transitions and the warning text. The emulator uses a synthetic local draft and restores its prior draft after the test.

Scope: `MissionDraftReviewTest`, `MissionPlanningTest` and project records. No production code, saved-data schema, dependency, permission, external destination, vehicle command or upload path changed. Baseline revision `fb1bb81`, clean worktree. See D-181-01.

Verification on 2026-11-14: `:app:testDebugUnitTest --tests '*MissionDraftReviewTest' :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1 --console=plain` passed in 8m 48s (80 tasks, 16 executed, 64 up-to-date). `MissionPlanningTest#localRouteReadinessWarningsClearAfterEdits` passed on the disposable emulator (1 test, 51.144 s). The test did not inspect physical route safety or upload compatibility. Rollback is a reviewed revert of test and record changes; no migration is needed.
