package com.mamadrones.gcs.core.result

/** Contract only. Success must mean confirmed vehicle state, never just bytes sent. */
sealed interface CommandResult {
    data object Success : CommandResult
    data class Rejected(val reason: String) : CommandResult
    data object Timeout : CommandResult
    data object NotConnected : CommandResult
    data object Unauthorized : CommandResult
    data object NotImplemented : CommandResult
    data class Failed(val error: AppError) : CommandResult
}

enum class AppError { TRANSPORT, INVALID_DATA, STORAGE, HARDWARE_UNAVAILABLE }
