package com.credence.mobile.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.credence.mobile.data.ApiException
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.DashboardStats
import com.credence.mobile.data.EnquiryDashboardStats
import com.credence.mobile.data.LeaveDashboardStats
import com.credence.mobile.data.LoginUser
import com.credence.mobile.ui.components.ErrorBanner
import com.credence.mobile.ui.components.LoadingOverlay
import com.credence.mobile.ui.components.StatCard
import com.credence.mobile.ui.components.formatMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val isLoading: Boolean = true,
    val stats: DashboardStats? = null,
    val enquiryPipeline: EnquiryDashboardStats? = null,
    val leaveStatus: LeaveDashboardStats? = null,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val repository: CredenceRepository,
    private val username: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val stats = repository.getDashboardStats(username)
                _uiState.value = DashboardUiState(isLoading = false, stats = stats)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                return@launch
            }

            // Mini-analytics widgets — fetched separately from the main
            // stats above and allowed to fail quietly (they're a bonus
            // "at a glance" addition, not core to the Dashboard's job)
            // rather than blocking the rest of the Dashboard on their
            // own errors.
            try {
                val monthStart = LocalDate.now().withDayOfMonth(1).toString()
                val today = LocalDate.now().toString()
                val enquiryPipeline = repository.getEnquiryDashboardStats(username, monthStart, today)
                _uiState.value = _uiState.value.copy(enquiryPipeline = enquiryPipeline)
            } catch (_: ApiException) {
                // Leave enquiryPipeline null — the card for it just won't render.
            }
            try {
                val leaveStatus = repository.getLeaveDashboardStats(username)
                _uiState.value = _uiState.value.copy(leaveStatus = leaveStatus)
            } catch (_: ApiException) {
                // Leave leaveStatus null — the card for it just won't render.
            }
        }
    }

    class Factory(
        private val repository: CredenceRepository,
        private val username: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository, username) as T
        }
    }
}

/**
 * Card set and order deliberately mirror Index.html's renderDashboard()
 * exactly — Admin/Manager/CenterHead get the full set (role check
 * matches Index.html's isAdmin()/isManager()), Coordinator gets the
 * reduced set. "Last updated" sits last, right after the No new session
 * tiles, matching where it was placed on the web dashboard.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    repository: CredenceRepository,
    user: LoginUser,
    onMenuClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(repository, user.username)
    )
    val state by viewModel.uiState.collectAsState()
    val isAdminTier = user.role.lowercase() in listOf("admin", "manager", "centerhead")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                }
            )
        }
    ) { innerPadding ->
        val stats = state.stats
        val error = state.errorMessage
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                state.isLoading && stats == null -> LoadingOverlay(modifier = Modifier.fillMaxSize())
                error != null && stats == null -> ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                stats != null -> DashboardContent(
                    stats = stats,
                    enquiryPipeline = state.enquiryPipeline,
                    leaveStatus = state.leaveStatus,
                    isAdminTier = isAdminTier,
                    welcomeName = user.fullName.ifBlank { user.username }
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    enquiryPipeline: EnquiryDashboardStats?,
    leaveStatus: LeaveDashboardStats?,
    isAdminTier: Boolean,
    welcomeName: String
) {
    val cards = if (isAdminTier) adminCards(stats) else coordinatorCards(stats)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "Welcome, $welcomeName",
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (enquiryPipeline != null || leaveStatus != null) {
            item(span = { GridItemSpan(2) }) {
                MiniAnalyticsRow(enquiryPipeline, leaveStatus)
            }
        }
        items(cards) { card ->
            StatCard(label = card.first, value = card.second, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * "At a glance" strip surfacing numbers that otherwise only live inside
 * the Enquiries and Staff Management screens — the enquiry pipeline for
 * this month (Enquiries screen's own Dashboard sub-view on the web) and
 * today's leave status (Staff Management → Leaves tab's stat grid).
 * Deliberately compact: full breakdowns (source/service/staff, balances,
 * bonus grants) stay in their own screens, not duplicated here.
 */
@Composable
private fun MiniAnalyticsRow(enquiryPipeline: EnquiryDashboardStats?, leaveStatus: LeaveDashboardStats?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (enquiryPipeline != null) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Enquiry pipeline (this month)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${enquiryPipeline.totalEnquiries} total", style = MaterialTheme.typography.bodyMedium)
                    Text("${enquiryPipeline.converted} converted · ${enquiryPipeline.lost} lost", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${enquiryPipeline.overdueFollowUps} overdue, ${enquiryPipeline.followUpsDueToday} due today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (leaveStatus != null) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Leave status (today)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${leaveStatus.onLeaveToday} on leave today", style = MaterialTheme.typography.bodyMedium)
                    Text("${leaveStatus.pendingCount} pending approval", style = MaterialTheme.typography.bodyMedium)
                    if (leaveStatus.onLeaveToday > 0 && leaveStatus.onLeaveTodayNames.isNotEmpty()) {
                        Text(
                            leaveStatus.onLeaveTodayNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun adminCards(s: DashboardStats): List<Pair<String, String>> = listOf(
    "Total enquiries (this month)" to s.totalEnquiriesMonth.toString(),
    "New children (this month)" to s.newStudents.toString(),
    "Exited children (this month)" to s.exitedStudents.toString(),
    "Upcoming exit children" to s.upcomingExitStudents.toString(),
    "Upcoming leaves (7 days)" to s.upcomingLeaveCount.toString(),
    "Total expenses (this month)" to formatMoney(s.totalExpensesMonth),
    "Total collection (this month)" to formatMoney(s.totalCollectionMonth),
    "Net (this month)" to formatMoney(s.netThisMonth),
    "Total fee due" to formatMoney(s.totalFeeDue),
    "Children with dues" to s.dueChildrenCount.toString(),
    "Online collection (this month)" to formatMoney(s.onlineCollectionMonth),
    "Cash collection (this month)" to formatMoney(s.cashCollectionMonth),
    "No new session (40+ days)" to s.noSession40.toString(),
    "No new session (60+ days)" to s.noSession60.toString(),
    "Last updated" to s.lastActivityAt
)

private fun coordinatorCards(s: DashboardStats): List<Pair<String, String>> = listOf(
    "Cash collection (this month)" to formatMoney(s.cashCollectionMonth),
    "Online collection (this month)" to formatMoney(s.onlineCollectionMonth),
    "Total fee due" to formatMoney(s.totalFeeDue),
    "Children with dues" to s.dueChildrenCount.toString(),
    "New children (this month)" to s.newStudents.toString(),
    "Exited children (this month)" to s.exitedStudents.toString(),
    "Upcoming exit children" to s.upcomingExitStudents.toString(),
    "Upcoming leaves (7 days)" to s.upcomingLeaveCount.toString(),
    "No new session (40+ days)" to s.noSession40.toString(),
    "No new session (60+ days)" to s.noSession60.toString()
)
