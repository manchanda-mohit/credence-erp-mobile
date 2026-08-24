package com.credence.mobile.ui.expenses

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
import androidx.compose.material3.TextButton
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
import com.credence.mobile.data.Expense
import com.credence.mobile.data.ExpenseInput
import com.credence.mobile.data.ExpenseOptions
import com.credence.mobile.data.LoginUser
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.SectionHeader
import com.credence.mobile.ui.components.formatMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val options: ExpenseOptions? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

class ExpensesViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val expenses = repository.getExpenses(username)
                val options = repository.getExpenseOptions(username)
                _uiState.value = _uiState.value.copy(isLoading = false, expenses = expenses, options = options)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun saveExpense(input: ExpenseInput, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.saveExpense(username, input)
                _uiState.value = _uiState.value.copy(isSaving = false)
                load()
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveError = e.message)
            }
        }
    }

    fun deleteExpense(expenseId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                repository.deleteExpense(username, expenseId, reason)
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
            return ExpensesViewModel(repository, username) as T
        }
    }
}

@Composable
fun ExpensesScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: ExpensesViewModel = viewModel(
        factory = ExpensesViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isAdmin = user.role.lowercase() == "admin"

    var showForm by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    fun closeForm() {
        showForm = false
        editingExpense = null
        viewModel.clearSaveError()
    }

    if (showForm) {
        val options = state.options
        if (options != null) {
            ExpenseFormScreen(
                expense = editingExpense,
                options = options,
                isAdmin = isAdmin,
                isSaving = state.isSaving,
                errorMessage = state.saveError,
                onDismiss = { closeForm() },
                onSave = { input -> viewModel.saveExpense(input) { closeForm() } },
                onDelete = { id, reason -> viewModel.deleteExpense(id, reason) { closeForm() } },
                modifier = modifier
            )
        }
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingExpense = null; showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New expense")
            }
        }
    ) { innerPadding ->
        val error = state.errorMessage
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                state.isLoading && state.expenses.isEmpty() -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && state.expenses.isEmpty() -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                state.expenses.isEmpty() -> EmptyState(message = "No expenses recorded yet.", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(state.expenses.asReversed(), key = { it.expenseId }) { expense ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingExpense = expense; showForm = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(expense.category, style = MaterialTheme.typography.bodyLarge)
                                Text(formatMoney(expense.amount), style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.height(2.dp))
                            val subtitle = listOf(expense.date, expense.paymentMode, expense.paidTo)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseFormScreen(
    expense: Expense?,
    options: ExpenseOptions,
    isAdmin: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (ExpenseInput) -> Unit,
    onDelete: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var date by remember(expense) { mutableStateOf(expense?.date ?: "") }
    var category by remember(expense) { mutableStateOf(expense?.category ?: options.categories.firstOrNull() ?: "") }
    var description by remember(expense) { mutableStateOf(expense?.description ?: "") }
    var amount by remember(expense) { mutableStateOf(if (expense != null) expense.amount.toString() else "") }
    var paymentMode by remember(expense) { mutableStateOf(expense?.paymentMode ?: "") }
    var paidTo by remember(expense) { mutableStateOf(expense?.paidTo ?: "") }
    var referenceNumber by remember(expense) { mutableStateOf(expense?.referenceNumber ?: "") }
    var remarks by remember(expense) { mutableStateOf(expense?.remarks ?: "") }
    var deleteReason by remember(expense) { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (expense == null) "New expense" else "Edit expense") },
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
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (yyyy-MM-dd) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SectionHeader("Category *")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.categories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SectionHeader("Payment mode")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.paymentModes.forEach { mode ->
                    FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = paidTo, onValueChange = { paidTo = it }, label = { Text("Paid to") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = referenceNumber, onValueChange = { referenceNumber = it }, label = { Text("Reference number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage)
            }

            Spacer(Modifier.height(20.dp))
            val amountValue = amount.toDoubleOrNull() ?: 0.0
            val canSubmit = date.isNotBlank() && category.isNotBlank() && amountValue > 0
            Button(
                onClick = {
                    onSave(
                        ExpenseInput(
                            expenseId = expense?.expenseId,
                            date = date.trim(),
                            category = category,
                            description = description.trim(),
                            amount = amountValue,
                            paymentMode = paymentMode,
                            paidTo = paidTo.trim(),
                            referenceNumber = referenceNumber.trim(),
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
                    Text(if (expense == null) "Record expense" else "Save changes")
                }
            }

            if (expense != null && isAdmin) {
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = deleteReason,
                    onValueChange = { deleteReason = it },
                    label = { Text("Reason for deletion (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onDelete(expense.expenseId, deleteReason.trim()) }) {
                    Text("Delete expense", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
