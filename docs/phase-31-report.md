# Phase 31 — Read-only reported home position

## Delivered

- Existing UDP, BLE and Bluetooth Classic receivers decode MAVLink 1/2 `HOME_POSITION` (242), after the existing CRC, unsigned-frame and pinned autopilot source checks. Geographic latitude/longitude and MSL altitude are retained with the app's receipt time. Local NED coordinates, quaternion, approach vector and sender timestamp are not used.
- All telemetry → Reported home shows seven-decimal coordinates, MSL altitude, observation age, missing/invalid evidence and historical-link labels. The message inspector includes HOME_POSITION counters. The existing exported telemetry report still excludes coordinates; this feature does not persist home data.
- A purple **H** map marker is separate from the live vehicle, trail, heading and orange local route. Home alone never changes the camera or enables Center/Follow. Operate, Map, Drive and Plan use the shared map component.
- The H is a built-in line-drawn icon, not downloaded text glyphs. Home rendering does not require the basemap's font endpoint.
- Home is retained session metadata, not a continuously measured position with an assumed update interval. The marker requires a valid coordinate pair, active receive session, connected state, matching session receipt time and a heartbeat no more than five seconds old. The map rechecks time every second even without new messages. Future/previous-session observations are hidden. Disconnection hides the marker; the telemetry card explicitly labels retained coordinates as historical. New sessions reset the observation. Invalid replacements clear the previous coordinates.
- Latitude/longitude zero is valid geography, not an invented missing-value sentinel. Out-of-range coordinates are rejected together. The UI does not infer home from GPS, the first waypoint, the phone location or a parameter file.
- Receipt/heartbeat checks use the current clock on each telemetry change as well as periodic ticks. A newly received heartbeat is not compared with an older cached tick, avoiding a temporary false future-time rejection.

## Boundaries

No message request, Set Home, Return/RTL, parameter write, mission transfer or vehicle command is sent. Passive reception means home remains NOT RECEIVED unless the selected autopilot sends it after its heartbeat. A reported home is not an authenticated identity, verified safe destination or permission to drive. No distance-to-home, route feasibility, automatic zoom or safe-return calculation is included. Existing hardware and safety requirements remain unchanged.

Wire fields were checked against the official [MAVLink HOME_POSITION definition](https://mavlink.io/en/messages/common.html#HOME_POSITION) and [generated C header](https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_home_position.h): CRC extra 104, base length 52, extension length up to 60, signed geographic fields at offsets 0/4/8. No firmware-specific publication interval is assumed.

## Validation

- **312 JVM tests passed**, zero failures/errors/skips. Fourteen new tests cover wire offsets and version-specific payload bounds, truncation/fragmentation, CRC/signature rejection, selected-source routing with zero sends, session reset, scaling, invalid coordinates and display/heartbeat expiry.
- Final `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1`: **BUILD SUCCESSFUL in 10m 4s**, using a single-use 1 GB Gradle daemon and in-process Kotlin compilation. Lint: **0 errors, 31 warnings** (30 existing plus one `UseKtx` suggestion for the built-in bitmap icon). The initial pre-icon build also passed in 11m 21s.
- **12/12 targeted emulator tests passed** on API 34, `emulator-5556`, in 297.661 seconds: HomePositionUiTest, MapLoadRecoveryTest, MapAcceptanceTest, BasemapSettingsUiTest and SettingsPersistenceTest. Final evidence: `app/build/acceptance-20261014-115529-91ec15aa/instrumentation.txt`. This is a targeted group, not the entire Android test suite.
- Final screenshots in `app/build/qa-repair2/phase31-{SATELLITE,HYBRID,STREETS}.png` were inspected: purple H and white built-in label visible, orange route retained, controls and attribution visible. `phase31-home-{LIGHT,DARK}.png` confirms readable home-card fields and caution text in both themes. These are landscape fixtures, not full-app portrait/accessibility certification.
- No physical-phone, HC-05/T12/R12/Cube connection or command testing was performed. Actual Cube publication of HOME_POSITION, phone rendering and field behavior remain hands-on acceptance items.
- Early runs are retained as failed evidence: low-memory Android System UI ANR affected map gesture focus, followed by a home geometry test failure after emulator recovery. Positive rendering checks now refresh synthetic heartbeats without refreshing HOME_POSITION metadata; expiry checks stop refreshing them. The online acceptance test requires purple marker pixels in the captured framebuffer, not just source data. These checks do not use a rover transport.
- The persistent local-style failure was resolved by replacing the font-dependent H text layer with a built-in line-drawn icon. The same no-glyph local style test then passed, with both circle and H layers retained; expiry, disconnection, invalid replacement and session reset are asserted. Failed evidence is preserved under `app/build/acceptance-*`, not relabeled as passing.
- `git diff --check` passed. Changes are not committed or pushed in this phase's implementation turn.

## Manual acceptance

1. Open All telemetry without a receive session: Reported home must say NOT RECEIVED; maps must not show a home marker.
2. In an approved read-only session, wait for the Cube to send HOME_POSITION. Check its accepted counter in Message inspector. Not receiving this passive message does not prove the controller lacks home support.
3. Compare coordinates and altitude with the same Cube's connected ground-station view. The phone's own location and local route do not establish home.
4. View the purple H on each basemap with the camera positioned near the reported coordinates. No automatic centering is expected.
5. Close the receive session using the app: H disappears and the telemetry panel labels retained data as historical. Starting a new session clears it until another accepted HOME_POSITION arrives. Do not interrupt a moving rover's radio for this check.
6. Check phone portrait/landscape, large text and TalkBack separately. Emulator fixtures are not physical-rover validation.
