package com.mamadrones.gcs.domain.model

/** Phase 1 UI contract. Real connection state arrives in a later transport phase. */
enum class VehicleConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, ERROR }
