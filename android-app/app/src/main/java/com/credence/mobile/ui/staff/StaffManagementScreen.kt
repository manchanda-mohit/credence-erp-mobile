package com.credence.mobile.ui.staff

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.credence.mobile.data.LeaveAdjustment
import com.credence.mobile.data.LeaveApplicationInput
import com.credence.mobile.data.LeaveBalance
import com.credence.mobile.data.LeaveDashboardStats
import com.credence.mobile.data.LeaveOptions
import com.credence.mobile.data.LeaveRequest
import com.credence.mobile.data.LeaveSummaryByTherapist
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.Therapist
import com.credence.mobile.data.TherapistInput
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val THERAPIST_STATUSES = listOf("Active", "Inactive")

data class StaffUiState(
    val isLoading: Boolean = true,
    val therapists: List<Therapist> = emptyList(),
    val services: List<String> = emptyList(),
    val leaveOptions: LeaveOptions? = null,
    val leaveRequests: List<LeaveRequest> = emptyList(),
    val leaveDashboardStats: LeaveDashboardStats? = null,
    val leaveBalances: List<LeaveBalance> = emptyList(),
    val leaveAdjustments: List<LeaveAdjustment> = emptyList(),
    val leaveSummary: List<LeaveSummaryByTherapist> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSavingRate: Boolean = false,
    val rateError: String? = null,
    val isGranting: Boolean = false,
    val grantError: String? = null
)

class StaffManagementViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffUiState())
    val uiState: StateFlow<StaffUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val staffPage = repository.getStaffPageData(username)
                val leaveOptions = repository.getLeaveOptions(username)
                val leaveRequests = repository.getTherapistLeaveRequests(username)
                val leaveDashboardStats = repository.getLeaveDashboardStats(username)
                val leaveBalances = repository.getLeaveBalances(username)
                val leaveAdjustments = repository.getLeaveAdjustments(username)
                val leaveSummary = repository.getLeaveSummaryByTherapist(username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    therapists = staffPage.therapists,
                    services = staffPage.services,
                    leaveOptions = leaveOptions,
                    leaveRequests = leaveRequests,
                    leaveDashboardStats = leaveDashboardStats,
                    leaveBalances = leaveBalances,
                    leaveAdjustments = leaveAdjustments,
                    leaveSummary = leaveSummary
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    /** Manager/Admin only (enforced server-side inside setAccrualRate) —
     * changes how many paid leave days accrue per month going forward. */
    fun setAccrualRate(rate: Double, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingRate = true, rateError = null)
            try {
                repository.setAccrualRate(username, rate)
                _uiState.value = _uiState.value.copy(isSavingRate = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSavingRate = false, rateError = e.message)
            }
        }
    }

    fun clearRateError() {
        _uiState.value = _uiState.value.copy(rateError = null)
    }

    /** Manager/Admin only (enforced server-side inside grantBonusLeave) —
     * adds extra paid days on top of a therapist's monthly accrual. */
    fun grantBonusLeave(therapistId: String, days: Double, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGranting = true, grantError = null)
            try {
                repository.grantBonusLeave(username, therapistId, days, reason)
                _uiState.value = _uiState.value.copy(isGranting = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isGranting = false, grantError = e.message)
            }
        }
    }

    fun clearGrantError() {
        _uiState.value = _uiState.value.copy(grantError = null)
    }

    fun saveTherapist(input: TherapistInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveTherapist(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deleteTherapist(therapistId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.deleteTherapist(username, therapistId, reason)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun applyForLeave(input: LeaveApplicationInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.applyForTherapistLeave(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun cancelLeave(leaveId: String) {
        viewModelScope.launch {
            try {
                repository.cancelTherapistLeaveRequest(username, leaveId)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun decideLeave(leaveId: String, decision: String) {
        viewModelScope.launch {
            try {
                repository.decideTherapistLeave(username, leaveId, decision, "")
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
            return StaffManagementViewModel(repository, username) as T
        }
    }
}

private enum class StaffFormMode { NONE, THERAPIST, LEAVE }

@Composable
fun StaffManagementScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: StaffManagementViewModel = viewModel(
        factory = StaffManagementViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isManagerTier = user.role.lowercase() in listOf("admin", "manager", "centerhead")

    var tabIndex by remember { mutableIntStateOf(0) }
    var formMode by remember { mutableStateOf(StaffFormMode.NONE) }
    var editingTherapist by remember { mutableStateOf<Therapist?>(null) }

    fun closeForm() {
        formMode = StaffFormMode.NONE
        editingTherapist = null
        viewModel.clearSaveError()
    }

    when (formMode) {
        StaffFormMode.THERAPIST -> {
            TherapistFormScreen(
                therapist = editingTherapist,
                services = state.services,
                isManagerTier = isManagerTier,
                isSaving = state.isSaving,
                errorMessage = state.saveError,
                onDismiss = { closeForm() },
                onSave = { input -> viewModel.saveTherapist(input) { closeForm() } },
                onDelete = { id, reason -> viewModel.deleteTherapist(id, reason) { closeForm() } },
                modifier = modifier
            )
            return
        }
        StaffFormMode.LEAVE -> {
            val options = state.leaveOptions
            if (options != null) {
                LeaveApplyFormScreen(
                    options = options,
                    isSaving = state.isSaving,
                    errorMessage = state.saveError,
                    onDismiss = { closeForm() },
                    onSave = { input -> viewModel.applyForLeave(input) { closeForm() } },
                    modifier = modifier
                )
                return
            }
        }
        StaffFormMode.NONE -> Unit
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Staff Management") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            when (tabIndex) {
                0 -> if (isManagerTier) {
                    FloatingActionButton(onClick = { editingTherapist = null; formMode = StaffFormMode.THERAPIST }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add therapist")
                    }
                }
                1 -> FloatingActionButton(onClick = { formMode = StaffFormMode.LEAVE }) {
                    Icon(Icons.Filled.Add, contentDescription = "Apply for leave")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Therapists") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Leaves") })
            }

            val error = state.errorMessage
            when {
                state.isLoading && state.therapists.isEmpty() -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && state.therapists.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                else -> when (tabIndex) {
                    0 -> TherapistsTab(
                        therapists = state.therapists,
                        onClick = { t -> editingTherapist = t; formMode = StaffFormMode.THERAPIST }
                    )
                    1 -> LeavesTab(
                        leaveRequests = state.leaveRequests,
                        leaveDashboardStats = state.leaveDashboardStats,
                        leaveBalances = state.leaveBalances,
                        leaveAdjustments = state.leaveAdjustments,
                        leaveSummary = state.leaveSummary,
                        accrualRate = state.leaveOptions?.accrualRate ?: 0.0,
                        currentUsername = user.username,
                        isManagerTier = isManagerTier,
                        isSavingRate = state.isSavingRate,
                        rateError = state.rateError,
                        isGranting = state.isGranting,
                        grantError = state.grantError,
                        onCancel = { id -> viewModel.cancelLeave(id) },
                        onDecide = { id, decision -> viewModel.decideLeave(id, decision) },
                        onSaveRate = { rate -> viewModel.setAccrualRate(rate) {} },
                        onClearRateError = { viewModel.clearRateError() },
                        onGrantBonus = { therapistId, days, reason, onDone -> viewModel.grantBonusLeave(therapistId, days, reason, onDone) },
                        onClearGrantError = { viewModel.clearGrantError() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TherapistsTab(therapists: List<Therapist>, onClick: (Therapist) -> Unit) {
    if (therapists.isEmpty()) {
        EmptyState(message = "No therapists on record yet.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(therapists, key = { it.therapistId }) { t ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                onClick = { onClick(t) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(t.status, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    val mobileText = t.mobile.ifBlank { "No mobile" }
                    val shiftText = t.shift.ifBlank { "No shift set" }
                    Text(
                        "${t.service} · $shiftText · $mobileText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (t.monthlySalary.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("Salary: ${t.monthlySalary}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * A single LazyColumn covers the whole Leaves tab — stat grid, paid
 * leave balances (+ accrual rate editor + bonus grants), bonus grant
 * history, per-therapist summary, then the full leave console — same
 * one-scrolling-page structure as Index.html's "sched-sub" leaves view,
 * just without the panel-by-panel visibility toggling the web version
 * doesn't need on a single continuous page. Everything below the stat
 * grid is purely additive to what round 1 shipped (the console list).
 */
@Composable
private fun LeavesTab(
    leaveRequests: List<LeaveRequest>,
    leaveDashboardStats: LeaveDashboardStats?,
    leaveBalances: List<LeaveBalance>,
    leaveAdjustments: List<LeaveAdjustment>,
    leaveSummary: List<LeaveSummaryByTherapist>,
    accrualRate: Double,
    currentUsername: String,
    isManagerTier: Boolean,
    isSavingRate: Boolean,
    rateError: String?,
    isGranting: Boolean,
    grantError: String?,
    onCancel: (String) -> Unit,
    onDecide: (String, String) -> Unit,
    onSaveRate: (Double) -> Unit,
    onClearRateError: () -> Unit,
    onGrantBonus: (String, Double, String, () -> Unit) -> Unit,
    onClearGrantError: () -> Unit
) {
    var grantingFor by remember { mutableStateOf<LeaveBalance?>(null) }
    val forGrant = grantingFor
    if (forGrant != null) {
        BonusLeaveDialog(
            therapistName = forGrant.therapistName,
            isSaving = isGranting,
            errorMessage = grantError,
            onDismiss = { grantingFor = null; onClearGrantError() },
            onGrant = { days, reason -> onGrantBonus(forGrant.therapistId, days, reason) { grantingFor = null } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (leaveDashboardStats != null) {
            item { LeaveStatGrid(leaveDashboardStats) }
        }

        item {
            SectionHeader("Paid leave balance (this year)", modifier = Modifier.padding(horizontal = 16.dp))
            if (isManagerTier) {
                var rateText by remember(accrualRate) { mutableStateOf(if (accrualRate > 0) accrualRate.toString() else "1") }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Accrual rate (paid leave/month)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { rateText.toDoubleOrNull()?.let { onSaveRate(it) } },
                        enabled = !isSavingRate && rateText.toDoubleOrNull() != null
                    ) { Text("Save") }
                }
                if (rateError != null) {
                    ErrorBanner(message = rateError, onDismiss = onClearRateError, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            Text(
                "Every active therapist accrues paid leave per month, resetting each January — accrued so far this year, plus any bonus grants, minus Paid leave taken.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (leaveBalances.isEmpty()) {
                Text("No active therapists.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            } else {
                Column {
                    leaveBalances.forEach { b ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(b.therapistName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${b.therapy} · Accrued ${fmtDays(b.accrued)} · Bonus ${fmtDays(b.bonusDays)} · Used ${fmtDays(b.used)} · Remaining ${fmtDays(b.remaining)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isManagerTier) {
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { grantingFor = b }) { Text("Grant bonus leave") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Bonus leave grants — history", modifier = Modifier.padding(horizontal = 16.dp))
            if (leaveAdjustments.isEmpty()) {
                Text("No bonus leave grants recorded yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            } else {
                Column {
                    leaveAdjustments.forEach { a ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${a.therapistName} · +${fmtDays(a.days)} day(s)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${a.reason.ifBlank { "No reason given" }} — by ${a.grantedBy.ifBlank { "—" }}, ${a.grantedDate.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Leave summary by therapist (this year)", modifier = Modifier.padding(horizontal = 16.dp))
            if (leaveSummary.isEmpty()) {
                Text("No approved leave recorded this year.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            } else {
                Column {
                    leaveSummary.forEach { s ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(s.therapistName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${s.therapy} · ${s.totalDays} day(s) total · ${s.paidDays} paid · ${s.unpaidDays} unpaid",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (s.byType.isNotEmpty()) {
                                    Text(
                                        s.byType.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Leave console — full history", modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (leaveRequests.isEmpty()) {
            item { EmptyState(message = "No leave requests yet.", modifier = Modifier.fillMaxWidth()) }
        } else {
            items(leaveRequests, key = { it.leaveId }) { leave ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(leave.therapistName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(leave.status, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${leave.leaveType} · ${leave.startDate} to ${leave.endDate} (${leave.numberOfDays} day(s)) · ${leave.paidOrUnpaid}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (leave.reason.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(leave.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (leave.status == "Pending") {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (isManagerTier) {
                                    TextButton(onClick = { onDecide(leave.leaveId, "Rejected") }) {
                                        Text("Reject", color = MaterialTheme.colorScheme.error)
                                    }
                                    TextButton(onClick = { onDecide(leave.leaveId, "Approved") }) { Text("Approve") }
                                }
                                if (isManagerTier || leave.createdBy.equals(currentUsername, ignoreCase = true)) {
                                    TextButton(onClick = { onCancel(leave.leaveId) }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Trims a whole-number balance to "3" instead of "3.0", but still
 * shows a fraction like "1.5" when the accrual rate isn't a whole
 * number — matches how a person would read either value. */
private fun fmtDays(value: Double): String =
    if (value == Math.floor(value)) value.toInt().toString() else value.toString()

@Composable
private fun LeaveStatGrid(stats: LeaveDashboardStats) {
    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("On leave today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.onLeaveToday.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Pending approvals", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.pendingCount.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Approved this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.approvedThisMonth.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Leave days this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.leaveDaysThisMonth.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (stats.onLeaveToday > 0 && stats.onLeaveTodayNames.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Today: ${stats.onLeaveTodayNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BonusLeaveDialog(
    therapistName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onGrant: (Double, String) -> Unit
) {
    var days by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val daysValue = days.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant bonus leave — $therapistName") },
        text = {
            Column {
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Days *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(message = errorMessage)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { daysValue?.let { onGrant(it, reason.trim()) } },
                enabled = !isSaving && daysValue != null && daysValue > 0
            ) { Text(if (isSaving) "Granting…" else "Grant leave") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TherapistFormScreen(
    therapist: Therapist?,
    services: List<String>,
    isManagerTier: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (TherapistInput) -> Unit,
    onDelete: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(therapist) { mutableStateOf(therapist?.name ?: "") }
    var service by remember(therapist) { mutableStateOf(therapist?.service ?: services.firstOrNull() ?: "") }
    var mobile by remember(therapist) { mutableStateOf(therapist?.mobile ?: "") }
    var status by remember(therapist) { mutableStateOf(therapist?.status?.ifBlank { "Active" } ?: "Active") }
    var shift by remember(therapist) { mutableStateOf(therapist?.shift ?: "") }
    var salary by remember(therapist) { mutableStateOf(therapist?.monthlySalary ?: "") }
    var joiningDate by remember(therapist) { mutableStateOf(therapist?.joiningDate ?: "") }
    var notes by remember(therapist) { mutableStateOf(therapist?.notes ?: "") }
    var deleteReason by remember(therapist) { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (therapist == null) "Add therapist" else "Edit therapist") },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Therapist name *") },
                enabled = isManagerTier,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            SectionHeader("Therapy/Service *")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.forEach { s ->
                    FilterChip(selected = service == s, onClick = { if (isManagerTier) service = s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile") }, enabled = isManagerTier, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SectionHeader("Status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THERAPIST_STATUSES.forEach { s ->
                    FilterChip(selected = status == s, onClick = { if (isManagerTier) status = s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = shift, onValueChange = { shift = it }, label = { Text("Shift") }, enabled = isManagerTier, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Monthly salary") }, enabled = isManagerTier, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = joiningDate, onValueChange = { joiningDate = it }, label = { Text("Joining date (yyyy-MM-dd)") }, enabled = isManagerTier, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, enabled = isManagerTier, minLines = 2, modifier = Modifier.fillMaxWidth())

            if (!isManagerTier) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Only a Manager, Center Head, or Admin can add or change therapist records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            if (isManagerTier) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        onSave(
                            TherapistInput(
                                therapistId = therapist?.therapistId,
                                name = name.trim(),
                                service = service,
                                mobile = mobile.trim(),
                                status = status,
                                notes = notes.trim(),
                                shift = shift.trim(),
                                salary = salary.trim(),
                                joiningDate = joiningDate.trim()
                            )
                        )
                    },
                    enabled = !isSaving && name.isNotBlank() && service.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (therapist == null) "Add therapist" else "Save changes")
                    }
                }

                if (therapist != null) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        label = { Text("Reason for deletion (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onDelete(therapist.therapistId, deleteReason.trim()) }) {
                        Text("Delete therapist", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LeaveApplyFormScreen(
    options: LeaveOptions,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (LeaveApplicationInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var therapistId by remember { mutableStateOf("") }
    var leaveType by remember { mutableStateOf(options.leaveTypes.firstOrNull() ?: "") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var paidStatus by remember { mutableStateOf(options.paidStatusOptions.firstOrNull() ?: "") }
    var reason by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Apply for leave") },
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
            SectionHeader("Therapist *")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.therapists.forEach { t ->
                    FilterChip(selected = therapistId == t.therapistId, onClick = { therapistId = t.therapistId }, label = { Text(t.therapistName) })
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionHeader("Leave type *")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.leaveTypes.forEach { type ->
                    FilterChip(selected = leaveType == type, onClick = { leaveType = type }, label = { Text(type) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start date (yyyy-MM-dd) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End date (yyyy-MM-dd) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SectionHeader("Paid or unpaid *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.paidStatusOptions.forEach { p ->
                    FilterChip(selected = paidStatus == p, onClick = { paidStatus = p }, label = { Text(p) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            val canSubmit = therapistId.isNotBlank() && leaveType.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank() && paidStatus.isNotBlank()
            Button(
                onClick = {
                    onSave(
                        LeaveApplicationInput(
                            therapistId = therapistId,
                            leaveType = leaveType,
                            startDate = startDate.trim(),
                            endDate = endDate.trim(),
                            paidStatus = paidStatus,
                            reason = reason.trim(),
                            remarks = remarks.trim()
                        )
                    )
                },
                enabled = !isSaving && canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Submit request")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
