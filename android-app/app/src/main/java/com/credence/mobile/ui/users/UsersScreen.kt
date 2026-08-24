package com.credence.mobile.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credence.mobile.data.ApiException
import com.credence.mobile.data.AppUser
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.UserInput
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val ROLE_OPTIONS = listOf("Admin", "Manager", "CenterHead", "Coordinator")

data class UsersUiState(
    val isLoading: Boolean = true,
    val users: List<AppUser> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

class UsersViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val users = repository.getUsers(username)
                _uiState.value = _uiState.value.copy(isLoading = false, users = users)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun saveUser(input: UserInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveUser(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deleteUser(targetUsername: String) {
        viewModelScope.launch {
            try {
                repository.deleteUser(username, targetUsername)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }

    class Factory(
        private val repository: CredenceRepository,
        private val username: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UsersViewModel(repository, username) as T
        }
    }
}

@Composable
fun UsersScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: UsersViewModel = viewModel(
        factory = UsersViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    var showForm by remember { mutableStateOf(false) }

    fun closeForm() {
        showForm = false
        viewModel.clearSaveError()
    }

    if (showForm) {
        UserFormScreen(
            isSaving = state.isSaving,
            errorMessage = state.saveError,
            onDismiss = { closeForm() },
            onSave = { input -> viewModel.saveUser(input) { closeForm() } },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add user")
            }
        }
    ) { innerPadding ->
        val error = state.errorMessage
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                state.isLoading && state.users.isEmpty() -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && state.users.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                state.users.isEmpty() -> EmptyState(message = "No users yet.", modifier = Modifier.fillMaxSize())
                else -> {
                    if (error != null) {
                        ErrorBanner(message = error, onDismiss = { viewModel.load() }, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(state.users, key = { it.username }) { appUser ->
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(appUser.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        val activeLabel = if (appUser.active.equals("YES", ignoreCase = true)) "Active" else "Inactive"
                                        Text(activeLabel, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${appUser.fullName.ifBlank { "—" }} · ${appUser.role}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { viewModel.deleteUser(appUser.username) }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserFormScreen(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (UserInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Coordinator") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add user") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password *") },
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
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Role", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ROLE_OPTIONS.forEach { r ->
                    FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(UserInput(username = username.trim(), password = password, fullName = fullName.trim(), role = role))
                },
                enabled = !isSaving && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Add user")
                }
            }
        }
    }
}
