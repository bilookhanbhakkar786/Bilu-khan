package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.School
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.LoadingOverlay
import com.example.ui.screens.principal.ClassesAndSessionsScreen
import com.example.ui.screens.principal.PrincipalAndTeacherDashboardScreen
import com.example.ui.screens.principal.SchoolProfileAndPostsScreen
import com.example.ui.screens.principal.StudentsManagementScreen
import com.example.ui.screens.principal.TeachersAndApprovalsScreen
import com.example.ui.screens.principal.WorkspaceSectionRoute
import com.example.ui.screens.shared.AttendanceWorkspacePanel
import com.example.ui.screens.shared.GlobalSearchScreen
import com.example.ui.screens.shared.NotificationsScreen
import com.example.ui.screens.shared.ProfileAndSettingsScreen
import com.example.ui.screens.shared.ReportsCenterScreen
import com.example.ui.screens.shared.TestsAndResultsPanel
import com.example.ui.screens.shared.TimetableAndHomeworkPanel
import com.example.ui.screens.student.StudentAttendanceScreen
import com.example.ui.screens.student.StudentHomeScreen
import com.example.ui.screens.student.StudentMySchoolScreen
import com.example.ui.screens.student.StudentResultsScreen
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

private data class NavTabItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedWorkspaceHost(
    school: School,
    user: UserProfile,
    role: UserRole,
    hubState: HubUiState,
    hubViewModel: HubViewModel,
    authViewModel: AuthViewModel
) {
    LaunchedEffect(school.id, user.id, role) {
        hubViewModel.loadWorkspace(school, user, role)
    }

    var selectedBottomTab by remember(role) { mutableStateOf("home") }
    var activeSubRoute by remember { mutableStateOf<WorkspaceSectionRoute?>(null) }

    if (activeSubRoute != null) {
        BackHandler { activeSubRoute = null }
    }

    val navItems = remember(role, hubState.unreadNotificationsCount) {
        when (role) {
            UserRole.PRINCIPAL -> listOf(
                NavTabItem("home", "Home", Icons.Rounded.Home),
                NavTabItem("classes", "Classes", Icons.Rounded.Class),
                NavTabItem("attendance", "Attendance", Icons.Rounded.EventAvailable),
                NavTabItem("notifications", "Alerts", Icons.Rounded.Notifications, hubState.unreadNotificationsCount),
                NavTabItem("profile", "Profile", Icons.Rounded.Person)
            )
            UserRole.TEACHER -> listOf(
                NavTabItem("home", "Home", Icons.Rounded.Home),
                NavTabItem("classes", "My Classes", Icons.Rounded.Class),
                NavTabItem("attendance", "Attendance", Icons.Rounded.EventAvailable),
                NavTabItem("notifications", "Alerts", Icons.Rounded.Notifications, hubState.unreadNotificationsCount),
                NavTabItem("profile", "Profile", Icons.Rounded.Person)
            )
            UserRole.STUDENT -> listOf(
                NavTabItem("home", "Home", Icons.Rounded.Home),
                NavTabItem("my_school", "My School", Icons.Rounded.Business),
                NavTabItem("attendance", "Attendance", Icons.Rounded.EventAvailable),
                NavTabItem("results", "Results", Icons.Rounded.Assignment),
                NavTabItem("profile", "Profile", Icons.Rounded.Person)
            )
        }
    }

    val topTitle = when (activeSubRoute) {
        WorkspaceSectionRoute.SCHOOL_PROFILE_POSTS -> "School Profile & Posts"
        WorkspaceSectionRoute.CLASSES_SESSIONS -> "Academic Sessions & Classes"
        WorkspaceSectionRoute.STUDENTS -> "Students"
        WorkspaceSectionRoute.TEACHERS -> "Teachers & Approvals"
        WorkspaceSectionRoute.ATTENDANCE -> "Attendance"
        WorkspaceSectionRoute.TESTS_RESULTS -> "Tests & Results"
        WorkspaceSectionRoute.ANNOUNCEMENTS -> "Announcements"
        WorkspaceSectionRoute.REPORTS -> "Reports & Exports"
        WorkspaceSectionRoute.SETTINGS_PROFILE -> "Settings & Profile"
        WorkspaceSectionRoute.GLOBAL_SEARCH -> "Global Search"
        WorkspaceSectionRoute.TIMETABLE_HOMEWORK -> "Timetable & Homework"
        WorkspaceSectionRoute.NOTIFICATIONS -> "Notifications"
        null -> when (selectedBottomTab) {
            "home" -> "BL School Hub"
            "classes" -> if (role == UserRole.PRINCIPAL) "Classes & Sessions" else "My Classes"
            "my_school" -> "My School"
            "attendance" -> "Attendance"
            "results" -> "My Results"
            "notifications" -> "Notifications"
            "profile" -> "Profile"
            else -> "BL School Hub"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = topTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NavyPrimary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        if (activeSubRoute != null) {
                            IconButton(onClick = { activeSubRoute = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (activeSubRoute != WorkspaceSectionRoute.GLOBAL_SEARCH) {
                            IconButton(
                                onClick = { activeSubRoute = WorkspaceSectionRoute.GLOBAL_SEARCH },
                                modifier = Modifier.testTag("topbar_search_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Global Search")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = activeSubRoute == null && selectedBottomTab == item.id
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    activeSubRoute = null
                                    selectedBottomTab = item.id
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (item.badgeCount > 0) Badge { Text(item.badgeCount.toString()) }
                                    }) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_${item.id}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail {
                        navItems.forEach { item ->
                            val selected = activeSubRoute == null && selectedBottomTab == item.id
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    activeSubRoute = null
                                    selectedBottomTab = item.id
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (item.badgeCount > 0) Badge { Text(item.badgeCount.toString()) }
                                    }) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("rail_${item.id}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FeedbackBanner(
                            errorMessage = hubState.errorMessage,
                            successMessage = hubState.successMessage,
                            onDismiss = hubViewModel::clearFeedback
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (activeSubRoute != null) {
                                when (activeSubRoute!!) {
                                    WorkspaceSectionRoute.SCHOOL_PROFILE_POSTS ->
                                        SchoolProfileAndPostsScreen(hubState, hubViewModel, initialTab = 1)
                                    WorkspaceSectionRoute.ANNOUNCEMENTS ->
                                        SchoolProfileAndPostsScreen(hubState, hubViewModel, initialTab = 0)
                                    WorkspaceSectionRoute.CLASSES_SESSIONS ->
                                        ClassesAndSessionsScreen(hubState, hubViewModel)
                                    WorkspaceSectionRoute.STUDENTS ->
                                        StudentsManagementScreen(hubState, hubViewModel)
                                    WorkspaceSectionRoute.TEACHERS ->
                                        TeachersAndApprovalsScreen(hubState, hubViewModel)
                                    WorkspaceSectionRoute.ATTENDANCE ->
                                        AttendanceWorkspacePanel(hubState, hubViewModel)
                                    WorkspaceSectionRoute.TESTS_RESULTS ->
                                        TestsAndResultsPanel(hubState, hubViewModel)
                                    WorkspaceSectionRoute.REPORTS ->
                                        ReportsCenterScreen(hubState, hubViewModel)
                                    WorkspaceSectionRoute.SETTINGS_PROFILE ->
                                        ProfileAndSettingsScreen(hubState, authViewModel)
                                    WorkspaceSectionRoute.GLOBAL_SEARCH ->
                                        GlobalSearchScreen(hubState)
                                    WorkspaceSectionRoute.TIMETABLE_HOMEWORK ->
                                        TimetableAndHomeworkPanel(hubState, hubViewModel)
                                    WorkspaceSectionRoute.NOTIFICATIONS ->
                                        NotificationsScreen(hubState, hubViewModel)
                                }
                            } else {
                                when (role) {
                                    UserRole.PRINCIPAL, UserRole.TEACHER -> when (selectedBottomTab) {
                                        "home" -> PrincipalAndTeacherDashboardScreen(
                                            state = hubState,
                                            onSelectSession = hubViewModel::selectAcademicSession,
                                            onRefresh = hubViewModel::refreshWorkspace,
                                            onNavigate = { route -> activeSubRoute = route }
                                        )
                                        "classes" -> ClassesAndSessionsScreen(hubState, hubViewModel)
                                        "attendance" -> AttendanceWorkspacePanel(hubState, hubViewModel)
                                        "notifications" -> NotificationsScreen(hubState, hubViewModel)
                                        "profile" -> ProfileAndSettingsScreen(hubState, authViewModel)
                                    }
                                    UserRole.STUDENT -> when (selectedBottomTab) {
                                        "home" -> StudentHomeScreen(
                                            state = hubState,
                                            onOpenNotifications = { activeSubRoute = WorkspaceSectionRoute.NOTIFICATIONS },
                                            onOpenAttendance = { selectedBottomTab = "attendance" },
                                            onOpenResults = { selectedBottomTab = "results" }
                                        )
                                        "my_school" -> StudentMySchoolScreen(hubState, hubViewModel)
                                        "attendance" -> StudentAttendanceScreen(hubState, hubViewModel)
                                        "results" -> StudentResultsScreen(hubState, hubViewModel)
                                        "profile" -> ProfileAndSettingsScreen(hubState, authViewModel)
                                    }
                                }
                            }
                        }
                    }

                    LoadingOverlay(visible = hubState.isLoading)
                }
            }
        }
    }
}
