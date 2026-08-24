package com.credence.mobile.ui.reports

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.credence.mobile.data.ExpenseCategorySummary
import com.credence.mobile.data.LoginUser
import com.credence.mobile.data.MonthlySummaryRow
import com.credence.mobile.data.StaleSessionEntry
import com.credence.mobile.ui.components.EmptyState
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.formatMoney
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val isLoading: Boolean = true,
    val staleSessions: List<StaleSessionEntry> = emptyList(),
    val expenseCategorySummary: ExpenseCategorySummary? = null,
    val errorMessage: String? = null,
    val isLoadingMonthly: Boolean = false,
    val monthlySummary: List<MonthlySummaryRow> = emptyList(),
    val monthlyError: String? = null
)

class ReportsViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val stale = repository.getStaleSessionReport(username)
                val expenseSummary = repository.getExpenseCategorySummary(username)
                _uiState.value = _uiState.value.copy(isLoading = false, staleSessions = stale, expenseCategorySummary = expenseSummary)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun loadMonthlySummary(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMonthly = true, monthlyError = null)
            try {
                val rows = repository.getMonthlySummary(username, startYear, startMonth, endYear, endMonth)
                _uiState.value = _uiState.value.copy(isLoadingMonthly = false, monthlySummary = rows)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoadingMonthly = false, monthlyError = e.message)
            }
        }
    }

    class Factory(
        private val repository: CredenceRepository,
        private val username: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(repository, username) as T
        }
    }
}

@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {}
) {
    val viewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isAdmin = user.role.lowercase() == "admin"
    val tabs = if (isAdmin) listOf("Sessions", "Expenses", "Monthly") else listOf("Sessions", "Expenses")
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(label) })
                }
            }

            val error = state.errorMessage
            when {
                state.isLoading -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                else -> when (tabs.getOrNull(tabIndex)) {
                    "Sessions" -> StaleSessionsTab(entries = state.staleSessions)
                    "Expenses" -> ExpenseCategoryTab(summary = state.expenseCategorySummary)
                    "Monthly" -> MonthlySummaryTab(
                        rows = state.monthlySummary,
                        isLoading = state.isLoadingMonthly,
                        error = state.monthlyError,
                        onRun = { sy, sm, ey, em -> viewModel.loadMonthlySummary(sy, sm, ey, em) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaleSessionsTab(entries: List<StaleSessionEntry>) {
    if (entries.isEmpty()) {
        EmptyState(message = "Every active child has a recent session — nothing stale.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(entries, key = { it.studentId }) { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.studentName, style = MaterialTheme.typography.bodyLarge)
                    val days = entry.daysSinceLastSession
                    Text(
                        if (days != null) "$days day(s)" else "Never",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${entry.therapies.ifBlank { "—" }} · ${entry.parentMobile.ifBlank { "No mobile" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Last session: ${entry.lastSessionDate.ifBlank { "Never" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExpenseCategoryTab(summary: ExpenseCategorySummary?) {
    if (summary == null) {
        EmptyState(message = "No expense data yet.", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(summary.monthLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Total: ${formatMoney(summary.total)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Salary: ${formatMoney(summary.salaryTotal)} · Non-salary: ${formatMoney(summary.nonSalaryTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
        val categories = summary.byCategory.entries.sortedByDescending { it.value.total }
        items(categories.toList()) { (category, breakdown) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$category (${breakdown.count})", style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(breakdown.total), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MonthlySummaryTab(
    rows: List<MonthlySummaryRow>,
    isLoading: Boolean,
    error: String?,
    onRun: (Int, Int, Int, Int) -> Unit
) {
    val today = remember { LocalDate.now() }
    var startYear by remember { mutableStateOf(today.minusMonths(5).year.toString()) }
    var startMonth by remember { mutableStateOf(today.minusMonths(5).monthValue.toString()) }
    var endYear by remember { mutableStateOf(today.year.toString()) }
    var endMonth by remember { mutableStateOf(today.monthValue.toString()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Date range (year / month)", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = startYear, onValueChange = { startYear = it }, label = { Text("From year") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = startMonth, onValueChange = { startMonth = it }, label = { Text("Month") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = endYear, onValueChange = { endYear = it }, label = { Text("To year") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = endMonth, onValueChange = { endMonth = it }, label = { Text("Month") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val sy = startYear.toIntOrNull()
                val sm = startMonth.toIntOrNull()
                val ey = endYear.toIntOrNull()
                val em = endMonth.toIntOrNull()
                if (sy != null && sm != null && ey != null && em != null) onRun(sy, sm, ey, em)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run report")
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(message = error)
        }

        Spacer(Modifier.height(16.dp))
        when {
            isLoading -> LoadingOverlay(modifier = Modifier.fillMaxWidth().height(120.dp))
            rows.isEmpty() -> Text(
                "Run the report to see monthly collection, expenses, and net figures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(rows) { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.month, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Collection: ${formatMoney(row.totalCollection)} (Cash ${formatMoney(row.cashCollection)} · Online ${formatMoney(row.onlineCollection)})", style = MaterialTheme.typography.bodyMedium)
                            Text("Expenses: ${formatMoney(row.totalExpenses)} · Net: ${formatMoney(row.net)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Fee due: ${formatMoney(row.feeDue)} · New children: ${row.newStudents} · Exited: ${row.exitedStudents}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
