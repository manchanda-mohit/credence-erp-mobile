package com.credence.mobile.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credence.mobile.data.ApiException
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.ToDoItem
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ToDoUiState(
    val isLoading: Boolean = true,
    val todos: List<ToDoItem> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

class ToDoViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(ToDoUiState())
    val uiState: StateFlow<ToDoUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val todos = repository.getToDos(username)
                _uiState.value = _uiState.value.copy(isLoading = false, todos = todos)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun addToDo(title: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                repository.addToDo(username, title)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message)
            }
        }
    }

    fun completeToDo(taskId: String) {
        viewModelScope.launch {
            try {
                repository.completeToDo(username, taskId)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun reopenToDo(taskId: String) {
        viewModelScope.launch {
            try {
                repository.reopenToDo(username, taskId)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun updateComment(taskId: String, comment: String) {
        viewModelScope.launch {
            try {
                repository.updateToDoComment(username, taskId, comment)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    class Factory(
        private val repository: CredenceRepository,
        private val username: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ToDoViewModel(repository, username) as T
        }
    }
}

@Composable
fun ToDoScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: ToDoViewModel = viewModel(
        factory = ToDoViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("To-Do") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("New task") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(0.dp))
                Button(
                    onClick = {
                        val title = newTitle.trim()
                        if (title.isNotBlank()) {
                            viewModel.addToDo(title) { newTitle = "" }
                        }
                    },
                    enabled = !state.isSaving && newTitle.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Add")
                }
            }

            val error = state.errorMessage
            when {
                state.isLoading && state.todos.isEmpty() -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && state.todos.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                state.todos.isEmpty() -> EmptyState(message = "No tasks yet — add one above.", modifier = Modifier.fillMaxSize())
                else -> {
                    if (error != null) {
                        ErrorBanner(message = error, onDismiss = { viewModel.load() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(state.todos, key = { it.taskId }) { task ->
                            ToDoRow(
                                task = task,
                                expanded = expandedTaskId == task.taskId,
                                onToggleExpand = {
                                    expandedTaskId = if (expandedTaskId == task.taskId) null else task.taskId
                                },
                                onToggleComplete = {
                                    if (task.status == "Completed") viewModel.reopenToDo(task.taskId) else viewModel.completeToDo(task.taskId)
                                },
                                onSaveComment = { comment -> viewModel.updateComment(task.taskId, comment) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToDoRow(
    task: ToDoItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
    onSaveComment: (String) -> Unit
) {
    val isCompleted = task.status == "Completed"
    var commentDraft by remember(task.taskId, expanded) { mutableStateOf(task.comment) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onToggleExpand),
        colors = if (isCompleted) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onToggleComplete) {
                    Text(if (isCompleted) "Reopen" else "Complete")
                }
            }
            val subtitle = if (isCompleted) {
                "Completed by ${task.completedBy.ifBlank { "—" }} on ${task.completedDate.ifBlank { "—" }}"
            } else {
                "Added by ${task.createdBy.ifBlank { "—" }} on ${task.createdDate.ifBlank { "—" }}"
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (task.comment.isNotBlank() && !expanded) {
                Spacer(Modifier.height(4.dp))
                Text("Note: ${task.comment}", style = MaterialTheme.typography.bodySmall)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onSaveComment(commentDraft.trim()) }) {
                        Text("Save comment")
                    }
                }
            }
        }
    }
}
