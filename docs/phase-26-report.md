# Phase 26 — Local route travel-time estimates

## Delivered

Open **Plan → Review** and scroll below the existing structural review to **Travel-time estimate**. Enter a planning speed, then inspect the total and numbered waypoint legs. No phone radio or rover is needed.

- The calculator accepts decimal dot or comma, with at most two decimal places, from **0.01 to 8 km/h**. It starts blank. Out-of-range, non-finite, exponent-form and malformed input is rejected rather than clamped. The 8 km/h upper bound comes from the operator's confirmed requirement; it is not a vehicle setting or proof of enforcement. The lower bound/precision are calculator limits, not validated minimum rover speed.
- Each leg uses the same spherical surface-distance calculation as the existing draft total. Time is distance divided by the explicitly entered speed, converted from km/h to m/s. Values display rounded up to whole seconds and include days for long drafts.
- Legs follow the current waypoint order. Editing, adding, removing, reordering, loading another draft or clearing the route recalculates results. Empty/one-point routes have no time estimate. Repeated coordinates retain a zero-length leg and a review note, not an invented travel time.
- Invalid speed, loading and load-failure states hide calculated results. Existing structural review stays above the calculator; existing mission transfer/execution controls remain unavailable.
- The speed is a screen-local `rememberSaveable` calculation input. It can survive activity state restoration but is not route metadata, does not mark the draft dirty, and is not stored in the draft, recovery copy, library, GeoJSON or GPX. It is retained while reviewing another draft in the same screen; the screen explicitly labels it as a calculator input.
- A themed `Surface` around the mission editor provides an explicit background and matching content colour, addressing the inherited-colour risk behind the earlier light-theme contrast finding.

## Boundaries

This assumes constant speed only between the listed coordinates. It excludes travel to the first point, unlisted return travel, turns, acceleration, stops, terrain, obstacles and spray/hydraulic work. It does not model a feasible rover trajectory or predict real mission completion time. Very large routes remain numerical estimates, not suitable operational plans.

No telemetry transport, vehicle command, parameter file, motor calibration, safety-profile approval or drive gate is changed. The accepted parameter file and its unresolved cruise-speed versus 8 km/h requirement remain unchanged. Existing README/hardware-record edits from prior user instructions are preserved.

## Validation

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain`: **BUILD SUCCESSFUL in 5m 47s**. Debug and instrumentation APKs built.
- **253 JVM tests, zero failures/errors/skips**, including 13 new calculation and input/format tests. Existing draft, storage and import/export tests pass.
- Lint passed with **0 errors and 30 existing warnings**, unchanged from Phase 25.
- Initial validation caught an incorrect chained Compose test call and an annotation incompatible with the current kapt setup. Both were corrected before the successful build.
- Targeted API 34 emulator suite: **5/5 passed in 195.603 seconds** (four new timing UI tests and the existing mission-edit/reorder/save/relaunch/rotation regression). Evidence: `app/build/acceptance-20261013-103816-94133448/instrumentation.txt`, ending `OK (5 tests)`. Synthetic local routes only; no vehicle link or commands. The full Android suite was not rerun.
- Visually inspected `app/build/qa-repair2/phase26-timing-light.png` and `phase26-timing-dark.png`: the calculator input, result and limitations are readable without overlap in the captured landscape fixture. Inspected `phase26-mission-portrait.png` and `phase26-mission-landscape.png`: editor text has an explicit readable background; layout and map controls remain separated from attribution. The portrait basemap loaded, but the landscape capture was blank at capture time. Landscape basemap readiness remains unverified; these tests do not assert tile loading. Large-font/accessibility and physical-phone review remain outstanding.
- The isolated emulator required one observed **Wait** action for an Android System UI startup ANR, before instrumentation. The first runner invocation correctly refused to start tests. Screenshot: `app/build/qa-repair2/phase26-startup.png`. After recovery the runner passed its readiness preflight. No dialog was dismissed or competing UI automation attached during the successful run. The emulator was shut down afterward.
- `git diff --check` passed. No physical rover validation, live parameter changes, radio connection, arming, movement, equipment control or mission execution was performed.

## Manual review

1. Open a local route with two or more points, then Plan → Review. Speed must start blank on a fresh screen; entering a speed must not enable Save draft or mission execution.
2. Enter `8`, then `4`. Distances must stay the same; unrounded travel time at 4 km/h should be twice that at 8 km/h (displayed rounded values may differ by a second).
3. Enter `14.4`, `0`, or malformed input. Previous totals/legs must disappear and guidance must appear. `4,5` and `4.5` should calculate identically.
4. Edit/reorder/remove waypoints. Verify the listed legs and total update. Clearing the route must show the two-waypoint requirement rather than an old estimate.
5. Review Light and Dark themes, portrait/landscape, and large-font/touch behaviour. A screen-local estimate is not a control command or speed limit.
