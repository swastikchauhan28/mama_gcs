package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.AccessState
import com.mamadrones.gcs.domain.model.UserRole
import com.mamadrones.gcs.domain.model.UserSession
import com.mamadrones.gcs.domain.model.UdpEndpoint
import kotlinx.coroutines.flow.StateFlow

/** Local, device-bound identity store. It never supplies vehicle identity or command readiness. */
interface AccessRepository {
    val state: StateFlow<AccessState>
    suspend fun initializeAdministrator(username: String, password: CharArray)
    suspend fun signIn(username: String, password: CharArray)
    suspend fun signOut()
    suspend fun createAccount(actor: UserSession, username: String, password: CharArray, role: UserRole)
    suspend fun setAccountEnabled(actor: UserSession, accountId: String, enabled: Boolean)
    suspend fun recordEndpointChangeRequest(actor: UserSession, endpoint: UdpEndpoint?): Boolean
    fun clearMessage()
}
