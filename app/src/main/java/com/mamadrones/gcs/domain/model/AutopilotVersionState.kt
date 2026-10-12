package com.mamadrones.gcs.domain.model

/** Last protocol-reported metadata, not authenticated identity or compatibility approval. */
data class AutopilotVersionState(
    val firmwareVersion: Long,
    val middlewareVersion: Long,
    val osVersion: Long,
    val boardVersion: Long,
    val vendorId: Int,
    val productId: Int,
    val receivedAtEpochMillis: Long,
)
