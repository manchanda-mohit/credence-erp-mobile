package com.credence.mobile.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.credence.mobile.data.CredenceRepository
import com.credence.mobile.data.SessionStore
import com.credence.mobile.ui.dashboard.DashboardScreen
import com.credence.mobile.ui.enquiries.EnquiriesScreen
import com.credence.mobile.ui.expenses.ExpensesScreen
import com.credence.mobile.ui.fees.FeeManagementScreen
import com.credence.mobile.ui.login.LoginScreen
import com.credence.mobile.ui.reports.ReportsScreen
import com.credence.mobile.ui.staff.StaffManagementScreen
import com.credence.mobile.ui.students.StudentsScreen
import com.credence.mobile.ui.todo.ToDoScreen
import com.credence.mobile.ui.users.UsersScreen
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

/**
 * Root composable — MainActivity's setContent{} calls this directly. Owns
 * the single shared CredenceRepository/SessionStore instance for the
 * whole app and the top-level Login ↔ Home navigation.
 */
@Composable
fun CredenceApp() {
    val context = LocalContext.current
    val repository = remember { CredenceRepository() }
    val sessionStore = remember { SessionStore(context) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                repository = repository,
                sessionStore = sessionStore,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                sessionStore = sessionStore,
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}

private data class DrawerSection(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false
)

// Order and set mirror Index.html's sidebar. Users is the one item the
// web app itself hides for non-Admin (navUsers.style.display = isAdmin()
// ? 'flex' : 'none') — every other section is visible to every logged-in
// role there, so that's the only adminOnly flag here too. Nine
// destinations is too many for a Material3 bottom NavigationBar
// (guidance caps that around 5), which is why this app uses a drawer
// instead — Dashboard/Students/Fee Management kept their own top bars
// from the first version, just gaining a hamburger button.
private val DRAWER_SECTIONS = listOf(
    DrawerSection("dashboard", "Dashboard", Icons.Filled.Home),
    DrawerSection("students", "Students", Icons.Filled.Person),
    DrawerSection("fees", "Fee Management", Icons.Filled.List),
    DrawerSection("enquiries", "Enquiries", Icons.Filled.ContactPhone),
    DrawerSection("staff", "Staff Management", Icons.Filled.Groups),
    DrawerSection("expenses", "Expenses", Icons.Filled.Receipt),
    DrawerSection("todo", "To-Do", Icons.Filled.Assignment),
    DrawerSection("reports", "Reports", Icons.Filled.Assessment),
    DrawerSection("users", "Users", Icons.Filled.SupervisorAccount, adminOnly = true)
)

@Composable
private fun HomeScreen(
    repository: CredenceRepository,
    sessionStore: SessionStore,
    onLoggedOut: () -> Unit
) {
    val user by sessionStore.savedUser.collectAsState(initial = null)
    var selectedSection by remember { mutableStateOf("dashboard") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentUser = user
    if (currentUser == null) {
        // Session was cleared (e.g. logged out from another screen) —
        // bounce back to Login rather than showing a blank Home.
        // LaunchedEffect (not a direct call) because navigating is a
        // side effect and must not run during composition itself.
        LaunchedEffect(Unit) { onLoggedOut() }
        return
    }

    val isAdmin = currentUser.role.lowercase() == "admin"
    val visibleSections = remember(isAdmin) { DRAWER_SECTIONS.filter { !it.adminOnly || isAdmin } }
    val onMenuClick: () -> Unit = { scope.launch { drawerState.open() } }
    val onLogout: () -> Unit = { scope.launch { sessionStore.clear(); onLoggedOut() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxHeight()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Credence", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            currentUser.fullName.ifBlank { currentUser.username },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            currentUser.role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        visibleSections.forEach { section ->
                            NavigationDrawerItem(
                                label = { Text(section.label) },
                                icon = { Icon(section.icon, contentDescription = null) },
                                selected = selectedSection == section.key,
                                onClick = {
                                    selectedSection = section.key
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Sign out") },
                        icon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; onLogout() },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    ) {
        when (selectedSection) {
            "dashboard" -> DashboardScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick, onLogout = onLogout)
            "students" -> StudentsScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "fees" -> FeeManagementScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "enquiries" -> EnquiriesScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "staff" -> StaffManagementScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "expenses" -> ExpensesScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "todo" -> ToDoScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "reports" -> ReportsScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
            "users" -> if (isAdmin) UsersScreen(repository = repository, user = currentUser, onMenuClick = onMenuClick)
        }
    }
}
