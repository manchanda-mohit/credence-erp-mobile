package com.credence.mobile.ui.enquiries

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.credence.mobile.data.DuplicateContactCheck
import com.credence.mobile.data.Enquiry
import com.credence.mobile.data.EnquiryFollowup
import com.credence.mobile.data.EnquiryInput
import com.credence.mobile.data.EnquiryOptions
import com.credence.mobile.data.FollowupInput
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.StudentInput
import com.credence.mobile.data.TherapyOption
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun splitList(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

data class EnquiriesUiState(
    val isLoading: Boolean = true,
    val enquiries: List<Enquiry> = emptyList(),
    val options: EnquiryOptions? = null,
    val therapyOptions: List<TherapyOption> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val duplicateCheck: DuplicateContactCheck? = null,
    val isCheckingDuplicate: Boolean = false,
    val followups: List<EnquiryFollowup> = emptyList(),
    val isLoadingFollowups: Boolean = false
)

class EnquiriesViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnquiriesUiState())
    val uiState: StateFlow<EnquiriesUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val enquiries = repository.getEnquiries(username)
                val options = repository.getEnquiryOptions(username)
                val therapyOptions = repository.getTherapyOptions(username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    enquiries = enquiries,
                    options = options,
                    therapyOptions = therapyOptions
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    /** Convert Enquiry → Student: creates the student record, then links
     * the enquiry to it and flips its status (markEnquiryConverted in
     * Code.gs) — same two-step sequence Index.html's openConvertModal()
     * success handler performs. onSuccess carries the new Student ID so
     * the caller can show it in a toast. */
    fun convertEnquiry(enquiryId: String, input: StudentInput, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                val result = repository.saveStudentAndGetId(username, input)
                repository.markEnquiryConverted(username, enquiryId, result.studentId)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess(result.studentId)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun checkDuplicate(mobile: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingDuplicate = true, duplicateCheck = null)
            try {
                val result = repository.checkDuplicateContact(username, mobile)
                _uiState.value = _uiState.value.copy(isCheckingDuplicate = false, duplicateCheck = result)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isCheckingDuplicate = false, saveError = e.message)
            }
        }
    }

    fun clearDuplicateCheck() {
        _uiState.value = _uiState.value.copy(duplicateCheck = null)
    }

    fun loadFollowups(enquiryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFollowups = true)
            try {
                val followups = repository.getFollowupsByEnquiry(username, enquiryId)
                _uiState.value = _uiState.value.copy(isLoadingFollowups = false, followups = followups)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoadingFollowups = false, errorMessage = e.message)
            }
        }
    }

    fun saveEnquiry(input: EnquiryInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveEnquiry(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun addFollowup(input: FollowupInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.addFollowup(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                loadFollowups(input.enquiryId)
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deleteEnquiry(enquiryId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteEnquiry(username, enquiryId, "")
                load()
                onSuccess()
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
            return EnquiriesViewModel(repository, username) as T
        }
    }
}

@Composable
fun EnquiriesScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: EnquiriesViewModel = viewModel(
        factory = EnquiriesViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isAdmin = user.role.lowercase() == "admin"

    var showForm by remember { mutableStateOf(false) }
    var editingEnquiry by remember { mutableStateOf<Enquiry?>(null) }
    var convertingEnquiry by remember { mutableStateOf<Enquiry?>(null) }
    var query by remember { mutableStateOf("") }

    fun closeForm() {
        showForm = false
        editingEnquiry = null
        viewModel.clearSaveError()
        viewModel.clearDuplicateCheck()
    }

    val toConvert = convertingEnquiry
    if (toConvert != null) {
        ConvertToStudentFormScreen(
            enquiry = toConvert,
            therapyOptions = state.therapyOptions,
            isSaving = state.isSaving,
            errorMessage = state.saveError,
            onDismiss = {
                convertingEnquiry = null
                viewModel.clearSaveError()
            },
            onSave = { input ->
                viewModel.convertEnquiry(toConvert.enquiryId, input) { convertingEnquiry = null }
            },
            modifier = modifier
        )
        return
    }

    if (showForm) {
        val options = state.options
        if (options != null) {
            EnquiryFormScreen(
                enquiry = editingEnquiry,
                options = options,
                isAdmin = isAdmin,
                isSaving = state.isSaving,
                saveError = state.saveError,
                duplicateCheck = state.duplicateCheck,
                isCheckingDuplicate = state.isCheckingDuplicate,
                followups = state.followups,
                isLoadingFollowups = state.isLoadingFollowups,
                onLoadFollowups = { id -> viewModel.loadFollowups(id) },
                onCheckDuplicate = { mobile -> viewModel.checkDuplicate(mobile) },
                onDismiss = { closeForm() },
                onSave = { input -> viewModel.saveEnquiry(input) {} },
                onAddFollowup = { input -> viewModel.addFollowup(input) {} },
                onDelete = { id -> viewModel.deleteEnquiry(id) { closeForm() } },
                modifier = modifier
            )
        }
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Enquiries") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingEnquiry = null; showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New enquiry")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or mobile") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            val error = state.errorMessage
            when {
                state.isLoading && state.enquiries.isEmpty() -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && state.enquiries.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                else -> {
                    val filtered = remember(query, state.enquiries) {
                        if (query.isBlank()) {
                            state.enquiries
                        } else {
                            state.enquiries.filter {
                                it.childName.contains(query, ignoreCase = true) ||
                                    it.parentName.contains(query, ignoreCase = true) ||
                                    it.mobile.contains(query, ignoreCase = true)
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(message = "No enquiries found.", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filtered, key = { it.enquiryId }) { enquiry ->
                                EnquiryRow(
                                    enquiry = enquiry,
                                    onClick = { editingEnquiry = enquiry; showForm = true },
                                    onConvert = { convertingEnquiry = enquiry }
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
private fun EnquiryRow(enquiry: Enquiry, onClick: () -> Unit, onConvert: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayName = enquiry.childName.ifBlank { enquiry.parentName.ifBlank { enquiry.mobile } }
            Text(displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(enquiry.status, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${enquiry.mobile} · ${enquiry.enquiryFor} · ${enquiry.priority}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // "Convert" is hidden once an enquiry is already Converted, same
        // as enquiryRowActions() in Index.html.
        if (enquiry.status != "Converted") {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onConvert) { Text("Convert to child") }
            }
        }
    }
}

@Composable
private fun EnquiryFormScreen(
    enquiry: Enquiry?,
    options: EnquiryOptions,
    isAdmin: Boolean,
    isSaving: Boolean,
    saveError: String?,
    duplicateCheck: DuplicateContactCheck?,
    isCheckingDuplicate: Boolean,
    followups: List<EnquiryFollowup>,
    isLoadingFollowups: Boolean,
    onLoadFollowups: (String) -> Unit,
    onCheckDuplicate: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EnquiryInput) -> Unit,
    onAddFollowup: (FollowupInput) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var childName by remember(enquiry) { mutableStateOf(enquiry?.childName ?: "") }
    var parentName by remember(enquiry) { mutableStateOf(enquiry?.parentName ?: "") }
    var mobile by remember(enquiry) { mutableStateOf(enquiry?.mobile ?: "") }
    var age by remember(enquiry) { mutableStateOf(enquiry?.age ?: "") }
    var city by remember(enquiry) { mutableStateOf(enquiry?.city ?: "") }
    var selectedServices by remember(enquiry) { mutableStateOf(enquiry?.let { splitList(it.enquiryFor) }?.toSet() ?: emptySet()) }
    var source by remember(enquiry) { mutableStateOf(enquiry?.source ?: "") }
    var sourceDetail by remember(enquiry) { mutableStateOf(enquiry?.sourceDetail ?: "") }
    var assignedTo by remember(enquiry) { mutableStateOf(enquiry?.assignedTo ?: "") }
    var remarks by remember(enquiry) { mutableStateOf(enquiry?.remarks ?: "") }
    var nextFollowUpDate by remember(enquiry) { mutableStateOf("") }

    // Follow-up sub-form (only shown once an enquiry already exists).
    var fuContactMode by remember(enquiry) { mutableStateOf("") }
    var fuRemarks by remember(enquiry) { mutableStateOf("") }
    var fuNextFollowUpDate by remember(enquiry) { mutableStateOf("") }
    var fuStatus by remember(enquiry) { mutableStateOf(enquiry?.status?.ifBlank { "New" } ?: "New") }
    var fuLostReason by remember(enquiry) { mutableStateOf("") }

    LaunchedEffect(enquiry?.enquiryId) {
        val id = enquiry?.enquiryId
        if (id != null) onLoadFollowups(id)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (enquiry == null) "New enquiry" else "Enquiry — ${enquiry.enquiryId}") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
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
            OutlinedTextField(value = childName, onValueChange = { childName = it }, label = { Text("Child name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = parentName, onValueChange = { parentName = it }, label = { Text("Parent/Guardian name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile number *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { onCheckDuplicate(mobile) }, enabled = mobile.isNotBlank() && !isCheckingDuplicate) {
                    Text(if (isCheckingDuplicate) "Checking…" else "Check for duplicate")
                }
            }
            if (duplicateCheck != null) {
                val dupEnquiry = duplicateCheck.enquiry
                val dupStudent = duplicateCheck.student
                if (dupEnquiry == null && dupStudent == null) {
                    Text("No existing enquiry or child found for this number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Column(Modifier.padding(top = 4.dp)) {
                        if (dupEnquiry != null) {
                            Text(
                                "Existing enquiry: ${dupEnquiry.childName} (${dupEnquiry.status})",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (dupStudent != null) {
                            Text(
                                "Existing child record: ${dupStudent.studentName} (${dupStudent.status})",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City/Area") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            SectionHeader("Enquiry for")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.services.forEach { service ->
                    FilterChip(
                        selected = service in selectedServices,
                        onClick = { selectedServices = if (service in selectedServices) selectedServices - service else selectedServices + service },
                        label = { Text(service) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Source")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.sources.forEach { s ->
                    FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = sourceDetail, onValueChange = { sourceDetail = it }, label = { Text("Source detail") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = assignedTo, onValueChange = { assignedTo = it }, label = { Text("Assigned to (username)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            if (enquiry == null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nextFollowUpDate,
                    onValueChange = { nextFollowUpDate = it },
                    label = { Text("Next follow-up date (yyyy-MM-dd, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (saveError != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = saveError)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        EnquiryInput(
                            enquiryId = enquiry?.enquiryId,
                            childName = childName.trim(),
                            parentName = parentName.trim(),
                            mobile = mobile.trim(),
                            age = age.trim(),
                            city = city.trim(),
                            enquiryFor = selectedServices.toList(),
                            source = source,
                            sourceDetail = sourceDetail.trim(),
                            assignedTo = assignedTo.trim(),
                            remarks = remarks.trim(),
                            nextFollowUpDate = nextFollowUpDate.trim()
                        )
                    )
                },
                enabled = !isSaving && mobile.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (enquiry == null) "Create enquiry" else "Save details")
                }
            }

            if (enquiry != null) {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                SectionHeader("Log a follow-up")

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.contactModes.forEach { mode ->
                        FilterChip(selected = fuContactMode == mode, onClick = { fuContactMode = mode }, label = { Text(mode) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = fuRemarks, onValueChange = { fuRemarks = it }, label = { Text("Follow-up notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = fuNextFollowUpDate,
                    onValueChange = { fuNextFollowUpDate = it },
                    label = { Text("Next follow-up date (yyyy-MM-dd, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                SectionHeader("Status")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.statuses.forEach { s ->
                        FilterChip(selected = fuStatus == s, onClick = { fuStatus = s }, label = { Text(s) })
                    }
                }
                if (fuStatus == "Lost") {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Lost reason *")
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.lostReasons.forEach { reasonOption ->
                            FilterChip(selected = fuLostReason == reasonOption, onClick = { fuLostReason = reasonOption }, label = { Text(reasonOption) })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onAddFollowup(
                            FollowupInput(
                                enquiryId = enquiry.enquiryId,
                                contactMode = fuContactMode,
                                remarks = fuRemarks.trim(),
                                nextFollowUpDate = fuNextFollowUpDate.trim(),
                                status = fuStatus,
                                lostReason = fuLostReason
                            )
                        )
                        fuRemarks = ""
                        fuNextFollowUpDate = ""
                    },
                    enabled = !isSaving && (fuStatus != "Lost" || fuLostReason.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save follow-up")
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("Follow-up history")
                if (isLoadingFollowups) {
                    LoadingOverlay(modifier = Modifier.fillMaxWidth().height(80.dp))
                } else if (followups.isEmpty()) {
                    Text("No follow-ups logged yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    followups.forEach { fu ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${fu.followUpDate} · ${fu.contactMode.ifBlank { "—" }} · ${fu.status}", style = MaterialTheme.typography.bodyMedium)
                                if (fu.remarks.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(fu.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (isAdmin) {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { onDelete(enquiry.enquiryId) }) {
                        Text("Delete enquiry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val CONVERT_STATUS_OPTIONS = listOf("Active", "Inactive", "Exited")

/**
 * Convert Enquiry → Student. Mirrors Index.html's openConvertModal():
 * opens the (full) Add Student form pre-filled from the enquiry's own
 * fields — Child Name, Parent/Guardian Name, Mobile Number, City/Area,
 * an approximate Date of Birth derived from Age, and whichever services
 * on the enquiry match a known therapy code (case-insensitively, since
 * ENQUIRY_FOR and THERAPIES are separate constants on the server that
 * don't always agree in case — e.g. 'SPED' vs 'SpEd' — and some enquiry
 * services like 'BrainGym/BodyGym' have no THERAPIES match at all and
 * are simply left unchecked, same as the web version). Everything else
 * starts blank and is filled in here, same as on the web. Saving calls
 * saveStudent (creating a brand-new student, never an update) and then
 * markEnquiryConverted to link the two records and flip the enquiry's
 * status — handled by EnquiriesViewModel.convertEnquiry().
 */
@Composable
private fun ConvertToStudentFormScreen(
    enquiry: Enquiry,
    therapyOptions: List<TherapyOption>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (StudentInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(enquiry) { mutableStateOf(enquiry.childName) }
    val approxDob = remember(enquiry) {
        val ageNum = enquiry.age.trim().toIntOrNull()
        if (ageNum != null && ageNum > 0) LocalDate.now().minusYears(ageNum.toLong()).toString() else ""
    }
    var dob by remember(enquiry) { mutableStateOf(approxDob) }
    var gender by remember(enquiry) { mutableStateOf("") }
    var fatherName by remember(enquiry) { mutableStateOf("") }
    var motherName by remember(enquiry) { mutableStateOf("") }
    var guardianName by remember(enquiry) { mutableStateOf(enquiry.parentName) }
    var parentMobile by remember(enquiry) { mutableStateOf(enquiry.mobile) }
    var altMobile by remember(enquiry) { mutableStateOf("") }
    var parentEmail by remember(enquiry) { mutableStateOf("") }
    var parentsOccupation by remember(enquiry) { mutableStateOf("") }
    var address by remember(enquiry) { mutableStateOf("") }
    var city by remember(enquiry) { mutableStateOf(enquiry.city) }
    var joiningDate by remember(enquiry) { mutableStateOf("") }
    var status by remember(enquiry) { mutableStateOf("Active") }
    var notes by remember(enquiry) { mutableStateOf("") }

    val enquiryServices = remember(enquiry) { splitList(enquiry.enquiryFor) }
    var selectedTherapies by remember(enquiry, therapyOptions) {
        mutableStateOf(
            therapyOptions
                .filter { opt -> enquiryServices.any { it.equals(opt.code, ignoreCase = true) } }
                .map { it.code }
                .toSet()
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Convert enquiry to child — ${enquiry.enquiryId}") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
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
            Text(
                "Pre-filled from the enquiry — review and complete before saving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(16.dp))

            SectionHeader("Status")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONVERT_STATUS_OPTIONS.forEach { option ->
                    FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option) })
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Therapies")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                therapyOptions.forEach { option ->
                    val isSelected = option.code in selectedTherapies
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTherapies = if (isSelected) selectedTherapies - option.code else selectedTherapies + option.code
                        },
                        label = { Text(option.code) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        StudentInput(
                            studentId = null,
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
                            exitDate = "",
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
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Convert to child")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
