# Security foundation and boundaries

## Implemented in revised Phase 1

- Pure, tested deny-by-default `AuthorizationPolicy`. Null, blank-identity, disabled and expired sessions are rejected; expiry uses an injected caller-supplied monotonic time. Admin has all modeled permissions; Operator lacks configuration/user management; Viewer has monitoring only.
- `UserSession`, `UserRole`, `Permission`, `AuditRecord` and repository contracts. There is no authentication provider or way to obtain a session in the app.
- No default password, hardcoded credential, role picker, automatic admin session, token, cloud endpoint or credential store.
- Theme only in a single application-scoped Preferences DataStore; the repository does I/O asynchronously. Unknown preference values fall back to dark. Read/write errors are shown; cancellation is not swallowed.
- Backups disabled; explicit cloud-backup and device-transfer exclusions for the app storage categories used by this project. No secret-bearing database or device-protected storage is introduced.
- Cleartext HTTP disabled, no new runtime permissions, only the necessary exported launcher activity. The existing `INTERNET` permission remains for the preserved UDP prototype.
- A Phase 2 UDP socket is opened only after the operator saves a host and ports then taps Open. Android's connected `DatagramSocket` accepts incoming datagrams only from that configured peer. The socket is closed when the app moves to the background; there is no automatic reconnect or background service.
- Phase 3 attaches a MAVLink receive session to that explicit UDP lifecycle. It validates MAVLink 2 HEARTBEAT CRC/payload, selects one autopilot system/component pair per session, ignores other identities, and degrades vehicle state when HEARTBEATs time out. CRC-valid unsigned HEARTBEAT is liveness only, not authentication. Signed packets are rejected because no signing-key verifier is provisioned.
- Machine-specific signing files, keys and environment configuration are ignored by Git. Release signing credentials are not provided.

## Not implemented / not a security claim

The policy function only evaluates roles and expiration. A caller-created `UserSession` is **not trusted authentication**. Role checks must be repeated at the use-case/repository command boundary after implementing a trusted identity provider; hiding a button is not authorization. No hardware commands exist in this foundation, so there is no operational authorization gate to bypass.

Secure local login, credential derivation/verification, lockout, protected credential storage, logout/session revocation, key rotation, persisted audit logs, user provisioning, and vehicle pairing belong to later phases. Do not store plaintext passwords or place credentials in the display DataStore. Design Android Keystore-backed key storage when there is an actual secret to protect; no custom encryption has been improvised here.

`usesCleartextTraffic=false` does **not** encrypt or authenticate UDP or MAVLink. Fixed-peer UDP filtering reduces accidental/unsolicited input but does not establish peer identity against network spoofing. MAVLink CRC is corruption detection, not sender authentication. The HEARTBEAT receiver remains deliberately limited: no signing verification, authenticated peer identity, replay protection, general telemetry, command handling, reconnect logic, or production hardening is implemented. Provision and verify MAVLink signing keys before treating packets as authenticated; review replay handling, packet validation, peer identity, timeout/reconnect concurrency and resource ownership before enabling commands.

The launcher activity contains no privileged deep-link or intent-command handler. The app currently exposes unavailable monitoring screens without login because they contain no actual vehicle data; revisiting read-access policy is required when real data is enabled. Release hardening, obfuscation, dependency/security review and adversarial testing are not complete. This is not production-qualified firmware-control software.

## Lifecycle review

Compose receives immutable state and callbacks; it never opens sockets. Activity-owned Hilt ViewModels survive configuration changes and collection is lifecycle-aware. Preference collection/saves use `viewModelScope`; the single DataStore uses the application context. The existing vehicle repository is singleton state for one selected vehicle only, not an active multi-vehicle manager. No new background service, session, socket, wake lock or repeating timer starts at launch.

## Guidance

Storage uses [Android DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore). Backup exclusions follow [Android backup documentation](https://developer.android.com/identity/data/autobackup); manufacturer/device-transfer behavior still requires device verification before storing secrets. These settings alone do not establish secure credential storage.
