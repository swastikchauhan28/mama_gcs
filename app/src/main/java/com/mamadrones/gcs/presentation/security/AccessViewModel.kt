package com.mamadrones.gcs.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.domain.model.UserRole
import com.mamadrones.gcs.domain.repository.AccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AccessViewModel @Inject constructor(private val repository: AccessRepository) : ViewModel() {
    val state = repository.state

    fun initializeAdministrator(username: String, password: CharArray) = withSecrets(password) {
        repository.initializeAdministrator(username, password)
    }

    fun signIn(username: String, password: CharArray) = withSecrets(password) {
        repository.signIn(username, password)
    }

    fun signOut() = viewModelScope.launch { repository.signOut() }

    fun changePassword(currentPassword: CharArray, newPassword: CharArray) =
        withSecrets(currentPassword, newPassword) {
            state.value.session?.let { repository.changePassword(it, currentPassword, newPassword) }
        }

    fun createAccount(username: String, password: CharArray, role: UserRole) {
        val actor = state.value.session
        withSecrets(password) { if (actor != null) repository.createAccount(actor, username, password, role) }
    }

    fun setAccountEnabled(accountId: String, enabled: Boolean) {
        val actor = state.value.session ?: return
        viewModelScope.launch { repository.setAccountEnabled(actor, accountId, enabled) }
    }

    fun clearMessage() = repository.clearMessage()

    private fun withSecrets(vararg secrets: CharArray, block: suspend () -> Unit) =
        viewModelScope.launch { block() }.also { job ->
            // Also runs when this ViewModel is cleared before the coroutine starts.
            job.invokeOnCompletion { secrets.forEach { it.fill('\u0000') } }
        }
}
