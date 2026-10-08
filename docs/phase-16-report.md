# Phase 16 — local access foundation

## Delivered

- First-run administrator creation and local username/password sign-in from Systems → Admin.
- Administrator-created Operator and Viewer accounts; administrators can enable or disable those accounts. Additional administrator provisioning and password recovery are not supported yet.
- Password length policy: 12–128 characters. Password verifiers are salted PBKDF2-HMAC-SHA256 (600,000 iterations); password hashing runs on a worker dispatcher and input character arrays are cleared after use.
- Account records and a bounded 500-event audit history are serialized into a device-local DataStore value encrypted with AES-GCM and an Android Keystore AES key. The UI displays the newest 30 events.
- Five consecutive failed attempts produce a one-minute lockout; the lockout uses monotonic time and resets after a detected device reboot.
- Authorized UDP endpoint-change requests are written to the local audit store before the endpoint preference is changed. The audit event indicates an authorized request, not proof the preference write succeeded.
- Sessions are held in memory for up to 15 minutes and are lost on app process restart. Account store errors preserve data and do not trigger an automatic reset.
- The admin-only `CONFIGURE` policy is checked before saving or clearing the configured UDP peer, including again immediately before the asynchronous preference write. A previously saved UDP endpoint remains openable for read-only monitoring while signed out.

## Deliberately not delivered

This phase does not establish cloud/organization identity, vehicle pairing, Cube Orange/BLE peer authentication, command-boundary role enforcement, an authenticated MAVLink signing verifier, or command permissions for movement, arming, mode, missions, spray, hydraulics or VESC. Those features remain disabled. Audit is local and encrypted at rest but is not tamper-evident, remotely backed up, or a record of vehicle commands. There is no password-change, lost-admin recovery, second-admin setup, account deletion or audit export flow.

The physical Cube Orange → identified BLE radio → phone path remains unverified. The radio's exact model/name, service and characteristic UUIDs, security/pairing method, Cube telemetry port/baud/protocol and bench-safe test procedure still need confirmation from the hardware team. No vehicle control was exercised as part of this phase.

## Validation

The app Kotlin source compiled successfully with `:app:compileDebugKotlin`. No tests were added or run during this phase. Physical device testing and security/release review are still required; this report does not claim a production security certification.
