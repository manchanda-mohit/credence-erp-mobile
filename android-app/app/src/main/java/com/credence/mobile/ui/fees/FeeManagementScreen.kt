package com.credence.mobile.ui.fees

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
import androidx.compose.runtime.mutableStateMapOf
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
import com.credence.mobile.data.Fee
import com.credence.mobile.data.FeeInput
import com.credence.mobile.data.FeeRate
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.Payment
import com.credence.mobile.data.PaymentEditInput
import com.credence.mobile.data.PaymentInput
import com.credence.mobile.data.Student
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.SectionHeader
import com.credence.mobile.ui.components.formatMoney
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val SESSION_TYPES = listOf("20 Sessions", "Monthly [M-F]", "Monthly [M-S]")
private val PAYMENT_MODES = listOf("Cash", "UPI", "Bank Transfer")

/** yyyy-MM-dd text -> (Month name, Year), or null if it doesn't parse yet
 * — the same "Billing Month/Year set from Session start date" behaviour
 * as Index.html's syncFeeBillingPeriod(). */
private fun billingPeriodFromDate(dateText: String): Pair<String, String>? {
    return try {
        val date = LocalDate.parse(dateText.trim())
        MONTH_NAMES[date.monthValue - 1] to date.year.toString()
    } catch (e: Exception) {
        null
    }
}

private fun splitTherapies(raw: String): List<String> =
    raw.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }

data class FeeManagementUiState(
    val isLoading: Boolean = true,
    val fees: List<Fee> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val dueList: List<Fee> = emptyList(),
    val students: List<Student> = emptyList(),
    val feeRates: List<FeeRate> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

class FeeManagementViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeeManagementUiState())
    val uiState: StateFlow<FeeManagementUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val fees = repository.getFees(username)
                val payments = repository.getPayments(username)
                val dueList = repository.getDueList(username)
                val students = repository.getStudents(username)
                val rates = repository.getFeeRates(username)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fees = fees,
                    payments = payments,
                    dueList = dueList,
                    students = students,
                    feeRates = rates
                )
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun saveFee(input: FeeInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveFee(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deleteFee(feeId: String) {
        viewModelScope.launch {
            try {
                repository.deleteFee(username, feeId)
                load()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun recordPayment(input: PaymentInput, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                val result = repository.recordPayment(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess(result.receiptNumber)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun updatePayment(input: PaymentEditInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.updatePayment(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            try {
                repository.deletePayment(username, paymentId)
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
            return FeeManagementViewModel(repository, username) as T
        }
    }
}

private enum class FeeFormMode { NONE, NEW_FEE, EDIT_FEE, NEW_PAYMENT, EDIT_PAYMENT }

@Composable
fun FeeManagementScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: FeeManagementViewModel = viewModel(
        factory = FeeManagementViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isAdmin = user.role.lowercase() == "admin"

    var tabIndex by remember { mutableIntStateOf(0) }
    var formMode by remember { mutableStateOf(FeeFormMode.NONE) }
    var editingFee by remember { mutableStateOf<Fee?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }

    fun closeForm() {
        formMode = FeeFormMode.NONE
        editingFee = null
        editingPayment = null
        viewModel.clearSaveError()
    }

    when (formMode) {
        FeeFormMode.NEW_FEE, FeeFormMode.EDIT_FEE -> {
            FeeFormScreen(
                fee = editingFee,
                students = state.students,
                feeRates = state.feeRates,
                isSaving = state.isSaving,
                errorMessage = state.saveError,
                onDismiss = { closeForm() },
                onSave = { input -> viewModel.saveFee(input) { closeForm() } },
                modifier = modifier
            )
            return
        }
        FeeFormMode.NEW_PAYMENT -> {
            PaymentFormScreen(
                students = state.students,
                fees = state.fees,
                isSaving = state.isSaving,
                errorMessage = state.saveError,
                onDismiss = { closeForm() },
                onSave = { input -> viewModel.recordPayment(input) { closeForm() } },
                modifier = modifier
            )
            return
        }
        FeeFormMode.EDIT_PAYMENT -> {
            val payment = editingPayment
            if (payment != null) {
                PaymentEditFormScreen(
                    payment = payment,
                    isSaving = state.isSaving,
                    errorMessage = state.saveError,
                    onDismiss = { closeForm() },
                    onSave = { input -> viewModel.updatePayment(input) { closeForm() } },
                    modifier = modifier
                )
                return
            }
        }
        FeeFormMode.NONE -> Unit
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Fee Management") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            when (tabIndex) {
                0 -> FloatingActionButton(onClick = { editingFee = null; formMode = FeeFormMode.NEW_FEE }) {
                    Icon(Icons.Filled.Add, contentDescription = "New fee record")
                }
                1 -> FloatingActionButton(onClick = { formMode = FeeFormMode.NEW_PAYMENT }) {
                    Icon(Icons.Filled.Add, contentDescription = "Record payment")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Records") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Payments") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Due Tracking") })
            }

            val error = state.errorMessage
            when {
                state.isLoading && state.fees.isEmpty() && state.payments.isEmpty() ->
                    LoadingOverlay(modifier = Modifier.fillMaxSize())

                error != null && state.fees.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                else -> when (tabIndex) {
                    0 -> RecordsTab(
                        fees = state.fees,
                        isAdmin = isAdmin,
                        onEdit = { fee -> editingFee = fee; formMode = FeeFormMode.EDIT_FEE },
                        onDelete = { fee -> viewModel.deleteFee(fee.feeId) }
                    )
                    1 -> PaymentsTab(
                        payments = state.payments,
                        isAdmin = isAdmin,
                        onEdit = { payment -> editingPayment = payment; formMode = FeeFormMode.EDIT_PAYMENT },
                        onDelete = { payment -> viewModel.deletePayment(payment.paymentId) }
                    )
                    2 -> DueTab(dueList = state.dueList)
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(
    fees: List<Fee>,
    isAdmin: Boolean,
    onEdit: (Fee) -> Unit,
    onDelete: (Fee) -> Unit
) {
    if (fees.isEmpty()) {
        EmptyState(message = "No fee records yet.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(fees.asReversed(), key = { it.feeId }) { fee ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onEdit(fee) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fee.studentName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(fee.paymentStatus, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${fee.therapy} · ${fee.billingMonth} ${fee.billingYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Net ${formatMoney(fee.netAmount)} · Paid ${formatMoney(fee.amountPaid)} · Due ${formatMoney(fee.balanceDue)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isAdmin) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onDelete(fee) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsTab(
    payments: List<Payment>,
    isAdmin: Boolean,
    onEdit: (Payment) -> Unit,
    onDelete: (Payment) -> Unit
) {
    if (payments.isEmpty()) {
        EmptyState(message = "No payments recorded yet.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(payments.asReversed(), key = { it.paymentId }) { payment ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(payment.studentName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney(payment.amountReceived), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${payment.therapy} · ${payment.billingMonth} ${payment.billingYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Receipt ${payment.receiptNumber} · ${payment.paymentMode} · ${payment.paymentDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAdmin) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onEdit(payment) }) { Text("Edit") }
                            TextButton(onClick = { onDelete(payment) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DueTab(dueList: List<Fee>) {
    if (dueList.isEmpty()) {
        EmptyState(message = "Nothing outstanding — every fee record is fully paid.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(dueList, key = { it.feeId }) { fee ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(fee.studentName, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${fee.therapy} · ${fee.billingMonth} ${fee.billingYear} · Session start ${fee.sessionStartDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Due ${formatMoney(fee.balanceDue)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * Shared "search a child, pick from matches" widget used by both the fee
 * form and the payment form — mirrors Index.html's onFeeStudentSearch()/
 * onPayStudentSearch() (type-ahead over the already-loaded student list,
 * top 6 matches, name or ID).
 */
@Composable
private fun StudentPicker(
    students: List<Student>,
    selected: Student?,
    onSelect: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember(selected) { mutableStateOf(selected?.let { "${it.studentName} (${it.studentId})" } ?: "") }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Child *") },
            placeholder = { Text("Type name or ID…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val showResults = query.isNotBlank() && (selected == null || query != "${selected.studentName} (${selected.studentId})")
        if (showResults) {
            val matches = remember(query, students) {
                students.filter {
                    it.studentName.contains(query, ignoreCase = true) || it.studentId.contains(query, ignoreCase = true)
                }.take(6)
            }
            Column(Modifier.padding(top = 4.dp)) {
                if (matches.isEmpty()) {
                    Text("No matches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    matches.forEach { student ->
                        Text(
                            text = "${student.studentName} (${student.studentId})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(student)
                                    query = "${student.studentName} (${student.studentId})"
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeFormScreen(
    fee: Fee?,
    students: List<Student>,
    feeRates: List<FeeRate>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (FeeInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudent by remember(fee) {
        mutableStateOf(students.find { it.studentId == fee?.studentId })
    }
    val feeTherapies = remember(fee) { fee?.let { splitTherapies(it.therapy) } ?: emptyList() }
    val availableTherapies = remember(selectedStudent, feeTherapies) {
        val studentTherapies = selectedStudent?.let { splitTherapies(it.therapiesTaking) } ?: emptyList()
        (studentTherapies + feeTherapies).distinct()
    }
    var selectedTherapies by remember(fee) { mutableStateOf(feeTherapies.toSet()) }
    var sessionType by remember(fee) { mutableStateOf(fee?.sessionType?.ifBlank { SESSION_TYPES[0] } ?: SESSION_TYPES[0]) }
    var sessionStartDate by remember(fee) { mutableStateOf(fee?.sessionStartDate ?: "") }
    var discount by remember(fee) { mutableStateOf(if (fee != null) fee.discount.toString() else "0") }
    var amountPaid by remember(fee) { mutableStateOf(if (fee != null) fee.amountPaid.toString() else "0") }

    // One editable amount per selected therapy — a stable map (not keyed
    // by selection) so toggling one therapy off/on doesn't wipe what was
    // typed for the others, matching the web form's behaviour.
    val rateAmounts = remember(fee) {
        val seed = mutableStateMapOf<String, String>()
        if (fee != null && feeTherapies.isNotEmpty()) {
            val share = fee.feeAmount / feeTherapies.size
            feeTherapies.forEach { seed[it] = String.format(Locale.US, "%.2f", share) }
        }
        seed
    }

    fun rateFor(therapy: String, type: String): FeeRate? {
        val matches = feeRates.filter { it.therapy == therapy && it.sessionType == type }
        return if (matches.size == 1) matches[0] else null
    }

    fun onToggleTherapy(t: String) {
        if (t in selectedTherapies) {
            selectedTherapies = selectedTherapies - t
        } else {
            selectedTherapies = selectedTherapies + t
            if (rateAmounts[t].isNullOrBlank()) {
                val rate = rateFor(t, sessionType)
                if (rate != null) rateAmounts[t] = rate.feeAmount
            }
        }
    }

    val total = selectedTherapies.sumOf { rateAmounts[it]?.toDoubleOrNull() ?: 0.0 }
    val billingPeriod = billingPeriodFromDate(sessionStartDate)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (fee == null) "New fee record" else "Edit fee record") },
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
            StudentPicker(
                students = students,
                selected = selectedStudent,
                onSelect = { student ->
                    selectedStudent = student
                    if (fee == null) selectedTherapies = emptySet()
                }
            )

            Spacer(Modifier.height(16.dp))
            SectionHeader("Therapy (select one or more) *")
            if (availableTherapies.isEmpty()) {
                Text(
                    "Select a child first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTherapies.forEach { t ->
                        FilterChip(selected = t in selectedTherapies, onClick = { onToggleTherapy(t) }, label = { Text(t) })
                    }
                }
                Text(
                    "Selecting more than one combines them into a single fee record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Session type *")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SESSION_TYPES.forEach { type ->
                    FilterChip(selected = sessionType == type, onClick = { sessionType = type }, label = { Text(type) })
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = sessionStartDate,
                onValueChange = { sessionStartDate = it },
                label = { Text("Session start date (yyyy-MM-dd) *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val period = billingPeriod
            Text(
                if (period != null) "Billing period: ${period.first} ${period.second}" else "Billing month/year is set from the session start date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = discount,
                onValueChange = { discount = it },
                label = { Text("Discount (combined, applied once)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (fee != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountPaid,
                    onValueChange = { amountPaid = it },
                    label = { Text("Amount paid") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedTherapies.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Fee amount per selected therapy")
                selectedTherapies.forEach { t ->
                    val rate = rateFor(t, sessionType)
                    OutlinedTextField(
                        value = rateAmounts[t] ?: "",
                        onValueChange = { rateAmounts[t] = it },
                        label = { Text(t) },
                        singleLine = true,
                        readOnly = rate != null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                if (selectedTherapies.size > 1) {
                    Text("Combined fee amount: ${formatMoney(total)}", fontWeight = FontWeight.Bold)
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            val student = selectedStudent
            Button(
                onClick = {
                    if (student != null && selectedTherapies.isNotEmpty() && sessionStartDate.isNotBlank()) {
                        onSave(
                            FeeInput(
                                feeId = fee?.feeId,
                                studentId = student.studentId,
                                studentName = student.studentName,
                                therapy = selectedTherapies.joinToString(", "),
                                sessionType = sessionType,
                                sessionStartDate = sessionStartDate.trim(),
                                feeAmount = total,
                                discount = discount.toDoubleOrNull() ?: 0.0,
                                amountPaid = amountPaid.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                },
                enabled = !isSaving && student != null && selectedTherapies.isNotEmpty() && sessionStartDate.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (fee == null) "Create fee record" else "Save changes")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PaymentFormScreen(
    students: List<Student>,
    fees: List<Fee>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (PaymentInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var selectedFee by remember { mutableStateOf<Fee?>(null) }
    var amountReceived by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val openFees = remember(selectedStudent, fees) {
        val id = selectedStudent?.studentId
        if (id == null) emptyList() else fees.filter { it.studentId == id && it.balanceDue > 0 }
    }

    fun onSelectFee(f: Fee) {
        selectedFee = f
        amountReceived = f.balanceDue.toString()
        discount = "0"
        paymentDate = todayDateText()
    }

    val currentPayable = remember(selectedFee, discount) {
        val fee = selectedFee
        if (fee == null) 0.0 else (fee.balanceDue - (discount.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Record a payment") },
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
            StudentPicker(
                students = students,
                selected = selectedStudent,
                onSelect = { student ->
                    selectedStudent = student
                    selectedFee = null
                    amountReceived = ""
                }
            )

            val student = selectedStudent
            if (student != null) {
                Spacer(Modifier.height(16.dp))
                if (openFees.isEmpty()) {
                    Text(
                        "This child has no outstanding fee records.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    SectionHeader("Outstanding fee record *")
                    Column {
                        openFees.forEach { f ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable { onSelectFee(f) },
                                colors = if (selectedFee?.feeId == f.feeId) {
                                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    CardDefaults.cardColors()
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${f.therapy} — ${f.billingMonth} ${f.billingYear}")
                                    Text("Due ${formatMoney(f.balanceDue)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            val fee = selectedFee
            if (fee != null) {
                Spacer(Modifier.height(16.dp))
                Text("Fee amount: ${formatMoney(fee.netAmount)}", style = MaterialTheme.typography.bodyMedium)
                Text("Previous due: ${formatMoney(fee.balanceDue)}", style = MaterialTheme.typography.bodyMedium)
                Text("Current amount payable: ${formatMoney(currentPayable)}", style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountReceived,
                    onValueChange = { amountReceived = it },
                    label = { Text("Amount received") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = discount,
                    onValueChange = { discount = it },
                    label = { Text("Discount (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Waives part of the balance on top of received amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                SectionHeader("Payment mode *")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PAYMENT_MODES.forEach { mode ->
                        FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode) })
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Payment date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            val amountValue = amountReceived.toDoubleOrNull() ?: 0.0
            val discountValue = discount.toDoubleOrNull() ?: 0.0
            val canSubmit = fee != null && paymentMode.isNotBlank() && (amountValue > 0 || discountValue > 0)
            Button(
                onClick = {
                    val f = fee
                    val s = student
                    if (f != null && s != null && canSubmit) {
                        onSave(
                            PaymentInput(
                                feeId = f.feeId,
                                studentId = s.studentId,
                                studentName = s.studentName,
                                therapy = f.therapy,
                                billingMonth = f.billingMonth,
                                billingYear = f.billingYear,
                                amountReceived = amountValue,
                                discount = discountValue,
                                paymentMode = paymentMode,
                                paymentDate = paymentDate.trim(),
                                receiptNumber = "",
                                remarks = remarks.trim()
                            )
                        )
                    }
                },
                enabled = !isSaving && canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Record payment")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun todayDateText(): String = LocalDate.now().toString()

@Composable
private fun PaymentEditFormScreen(
    payment: Payment,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (PaymentEditInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var amountReceived by remember(payment) { mutableStateOf(payment.amountReceived.toString()) }
    var discount by remember(payment) { mutableStateOf(payment.discountGiven.toString()) }
    var paymentMode by remember(payment) { mutableStateOf(payment.paymentMode) }
    var paymentDate by remember(payment) { mutableStateOf(payment.paymentDate) }
    var receiptNumber by remember(payment) { mutableStateOf(payment.receiptNumber) }
    var remarks by remember(payment) { mutableStateOf(payment.remarks) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit payment — ${payment.paymentId}") },
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
                value = payment.studentName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Child") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = receiptNumber,
                onValueChange = { receiptNumber = it },
                label = { Text("Receipt number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountReceived,
                onValueChange = { amountReceived = it },
                label = { Text("Amount received *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = discount,
                onValueChange = { discount = it },
                label = { Text("Discount") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            SectionHeader("Payment mode *")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PAYMENT_MODES.forEach { mode ->
                    FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode) })
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = paymentDate,
                onValueChange = { paymentDate = it },
                label = { Text("Payment date (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            val amountValue = amountReceived.toDoubleOrNull() ?: 0.0
            val canSubmit = amountValue > 0 && paymentMode.isNotBlank()
            Button(
                onClick = {
                    if (canSubmit) {
                        onSave(
                            PaymentEditInput(
                                paymentId = payment.paymentId,
                                amountReceived = amountValue,
                                discount = discount.toDoubleOrNull() ?: 0.0,
                                paymentMode = paymentMode,
                                paymentDate = paymentDate.trim(),
                                receiptNumber = receiptNumber.trim(),
                                remarks = remarks.trim()
                            )
                        )
                    }
                },
                enabled = !isSaving && canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save changes")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
