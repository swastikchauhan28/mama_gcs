package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Integration contracts only. Future adapters feed the single authoritative VehicleRepository. */
interface MavlinkRepository { val connection: StateFlow<ConnectionState> }
interface VescRepository { val motors: StateFlow<List<MotorState>> }
interface BatteryRepository { val battery: StateFlow<BatteryState> }
interface SprayRepository { val spray: StateFlow<SprayState> }
interface HydraulicRepository { val hydraulic: StateFlow<HydraulicState> }
interface MissionRepository { val mission: StateFlow<MissionState> }
interface UserRepository { val session: StateFlow<UserSession?>; suspend fun logout() }
interface AuditRepository { suspend fun record(record: AuditRecord); fun recent(): Flow<List<AuditRecord>> }
interface DiagnosticsRepository { val diagnostics: StateFlow<DiagnosticsState> }

// Command methods are intentionally deferred until authorization, hardware mapping,
// readiness, acknowledgement, and resulting-state confirmation can be enforced together.
