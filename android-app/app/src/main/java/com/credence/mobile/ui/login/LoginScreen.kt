package com.credence.mobile.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credence.mobile.data.ApiException
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.SessionStore
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val checkingSavedSession: Boolean = true,
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * On first composition, tries a silent restoreSession() using whatever
 * username SessionStore remembers from last time — the same "stay
 * signed in across refreshes" pattern Index.html already implements
 * with localStorage for the web app (see restoreSession() in Code.gs).
 * Falls back to the plain sign-in form if there's nothing remembered,
 * the server rejects it (deactivated account, role changed), or the
 * network is unreachable.
 */
class LoginViewModel(
    private val repository: CredenceRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun tryRestoreSession(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val savedUsername = sessionStore.currentUsername()
            if (savedUsername.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(checkingSavedSession = false)
                return@launch
            }
            try {
                val result = repository.restoreSession(savedUsername)
                val user = result.user
                if (result.success && user != null) {
                    sessionStore.save(user)
                    onSuccess()
                } else {
                    sessionStore.clear()
                    _uiState.value = _uiState.value.copy(checkingSavedSession = false)
                }
            } catch (e: ApiException) {
                // Couldn't reach the server to confirm — fall back to the
                // sign-in form rather than getting stuck on a spinner;
                // the saved username stays put, so a normal password
                // sign-in still works once connectivity is back.
                _uiState.value = _uiState.value.copy(checkingSavedSession = false)
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun submit(onSuccess: () -> Unit) {
        val current = _uiState.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Enter your username and password.")
            return
        }
        _uiState.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = repository.login(current.username.trim(), current.password)
                val user = result.user
                if (result.success && user != null) {
                    sessionStore.save(user)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = result.message.ifBlank { "Invalid username or password." }
                    )
                }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.message)
            }
        }
    }

    class Factory(
        private val repository: CredenceRepository,
        private val sessionStore: SessionStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository, sessionStore) as T
        }
    }
}

@Composable
fun LoginScreen(
    repository: CredenceRepository,
    sessionStore: SessionStore,
    onLoginSuccess: () -> Unit
) {
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(repository, sessionStore)
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.tryRestoreSession(onSuccess = onLoginSuccess)
    }

    if (state.checkingSavedSession) {
        LoadingOverlay(modifier = Modifier.fillMaxSize())
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Credence",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.submit(onSuccess = onLoginSuccess) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Sign in")
                }
            }
        }
    }
}
