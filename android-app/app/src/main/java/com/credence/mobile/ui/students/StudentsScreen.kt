package com.credence.mobile.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credence.mobile.data.ApiException
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.Student
import com.credence.mobile.data.StudentInput
import com.credence.mobile.data.TherapyOption
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StudentsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val therapyOptions: List<TherapyOption> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

class StudentsViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentsUiState())
    val uiState: StateFlow<StudentsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val students = repository.getStudents(username)
                val therapies = repository.getTherapyOptions(username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    students = students,
                    therapyOptions = therapies
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    /** Duplicate-name rejections (checkDuplicateStudentName_ in Code.gs)
     * and every other saveStudent error arrive here as an ApiException
     * with a message already written to be shown to a user as-is —
     * saveError is that message, shown by ErrorBanner in the form. */
    fun saveStudent(input: StudentInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveStudent(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
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
            return StudentsViewModel(repository, username) as T
        }
    }
}

@Composable
fun StudentsScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: StudentsViewModel = viewModel(
        factory = StudentsViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var query by remember { mutableStateOf("") }

    if (showForm) {
        StudentFormScreen(
            student = editingStudent,
            therapyOptions = state.therapyOptions,
            isSaving = state.isSaving,
            errorMessage = state.saveError,
            onDismiss = {
                showForm = false
                editingStudent = null
                viewModel.clearSaveError()
            },
            onSave = { input ->
                viewModel.saveStudent(input) {
                    showForm = false
                    editingStudent = null
                }
            },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Students") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingStudent = null; showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add child")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or ID") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            val error = state.errorMessage
            when {
                state.isLoading && state.students.isEmpty() ->
                    LoadingOverlay(modifier = Modifier.fillMaxSize())

                error != null && state.students.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                else -> {
                    val filtered = remember(query, state.students) {
                        if (query.isBlank()) {
                            state.students
                        } else {
                            state.students.filter {
                                it.studentName.contains(query, ignoreCase = true) ||
                                    it.studentId.contains(query, ignoreCase = true)
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(message = "No children found.", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filtered, key = { it.studentId }) { student ->
                                StudentRow(
                                    student = student,
                                    onClick = { editingStudent = student; showForm = true }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRow(student: Student, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = student.studentName, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        val mobileLabel = student.parentMobile.ifBlank { "No mobile on file" }
        Text(
            text = "${student.studentId} · ${student.status} · $mobileLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val STUDENT_STATUS_OPTIONS = listOf("Active", "Inactive", "Exited")

@Composable
private fun StudentFormScreen(
    student: Student?,
    therapyOptions: List<TherapyOption>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (StudentInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(student) { mutableStateOf(student?.studentName ?: "") }
    var dob by remember(student) { mutableStateOf(student?.dateOfBirth ?: "") }
    var gender by remember(student) { mutableStateOf(student?.gender ?: "") }
    var fatherName by remember(student) { mutableStateOf(student?.fatherName ?: "") }
    var motherName by remember(student) { mutableStateOf(student?.motherName ?: "") }
    var guardianName by remember(student) { mutableStateOf(student?.guardianName ?: "") }
    var parentMobile by remember(student) { mutableStateOf(student?.parentMobile ?: "") }
    var altMobile by remember(student) { mutableStateOf(student?.altMobile ?: "") }
    var parentEmail by remember(student) { mutableStateOf(student?.parentEmail ?: "") }
    var parentsOccupation by remember(student) { mutableStateOf(student?.parentsOccupation ?: "") }
    var address by remember(student) { mutableStateOf(student?.address ?: "") }
    var city by remember(student) { mutableStateOf(student?.city ?: "") }
    var joiningDate by remember(student) { mutableStateOf(student?.joiningDate ?: "") }
    var exitDate by remember(student) { mutableStateOf(student?.exitDate ?: "") }
    var status by remember(student) {
        mutableStateOf(student?.status?.ifBlank { "Active" } ?: "Active")
    }
    var notes by remember(student) { mutableStateOf(student?.notes ?: "") }

    val initialTherapies = remember(student) {
        student?.therapiesTaking
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }
    var selectedTherapies by remember(student) { mutableStateOf(initialTherapies) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (student == null) "Add child" else "Edit child") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Child name *") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date of birth (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = fatherName, onValueChange = { fatherName = it }, label = { Text("Father's name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = motherName, onValueChange = { motherName = it }, label = { Text("Mother's name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = guardianName, onValueChange = { guardianName = it }, label = { Text("Parent/Guardian name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = parentMobile, onValueChange = { parentMobile = it }, label = { Text("Parent mobile") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = altMobile, onValueChange = { altMobile = it }, label = { Text("Alternate mobile") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = { Text("Parent email") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = parentsOccupation, onValueChange = { parentsOccupation = it }, label = { Text("Parents' occupation") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = joiningDate, onValueChange = { joiningDate = it }, label = { Text("Joining date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = exitDate, onValueChange = { exitDate = it }, label = { Text("Exit date (yyyy-MM-dd, optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            SectionHeader("Status")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STUDENT_STATUS_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = { Text(option) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Therapies")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                therapyOptions.forEach { option ->
                    val isSelected = option.code in selectedTherapies
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTherapies = if (isSelected) {
                                selectedTherapies - option.code
                            } else {
                                selectedTherapies + option.code
                            }
                        },
                        label = { Text(option.code) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        StudentInput(
                            studentId = student?.studentId,
                            name = name.trim(),
                            dob = dob.trim(),
                            gender = gender.trim(),
                            fatherName = fatherName.trim(),
                            motherName = motherName.trim(),
                            guardianName = guardianName.trim(),
                            parentMobile = parentMobile.trim(),
                            altMobile = altMobile.trim(),
                            parentEmail = parentEmail.trim(),
                            parentsOccupation = parentsOccupation.trim(),
                            address = address.trim(),
                            city = city.trim(),
                            joiningDate = joiningDate.trim(),
                            exitDate = exitDate.trim(),
                            status = status,
                            therapies = selectedTherapies.toList(),
                            notes = notes.trim()
                        )
                    )
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (student == null) "Add child" else "Save changes")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
