package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.School
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.LoadingOverlay
import com.example.ui.screens.principal.ClassesAndSessionsScreen
import com.example.ui.screens.principal.EditSchoolDialog
import com.example.ui.screens.principal.PrincipalAndTeacherDashboardScreen
import com.example.ui.screens.principal.SchoolProfileAndPostsScreen
import com.example.ui.screens.principal.StudentsManagementScreen
import com.example.ui.screens.principal.TeachersAndApprovalsScreen
import com.example.ui.screens.principal.WorkspaceSectionRoute
import com.example.ui.screens.shared.AttendancePercentageFilterScreen
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
    authViewModel: AuthViewModel,
    onOpenAdminDashboard: (() -> Unit)? = null
) {
    LaunchedEffect(school.id, user.id, role) {
        hubViewModel.loadWorkspace(school, user, role)
    }

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    var selectedBottomTab by remember(role) { mutableStateOf("home") }
    var activeSubRoute by remember { mutableStateOf<WorkspaceSectionRoute?>(null) }
    var studentSchoolInitialTab by remember { mutableIntStateOf(0) }
    var showQuickEditSchoolDialog by remember { mutableStateOf(false) }

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
                NavTabItem("classes", "Classes", Icons.Rounded.Class),
                NavTabItem("attendance", "Attendance", Icons.Rounded.EventAvailable),
                NavTabItem("notifications", "Alerts", Icons.Rounded.Notifications, hubState.unreadNotificationsCount),
                NavTabItem("profile", "Profile", Icons.Rounded.Person)
            )
            UserRole.STUDENT -> listOf(
                NavTabItem("home", "Home", Icons.Rounded.Home),
                NavTabItem("my_school", "School", Icons.Rounded.Business),
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
        WorkspaceSectionRoute.TEACHERS -> "Teachers & Invite Links"
        WorkspaceSectionRoute.ATTENDANCE -> "Attendance"
        WorkspaceSectionRoute.ATTENDANCE_FILTER -> "Attendance % Filter"
        WorkspaceSectionRoute.TESTS_RESULTS -> "Tests & Results"
        WorkspaceSectionRoute.ANNOUNCEMENTS -> "Announcements"
        WorkspaceSectionRoute.REPORTS -> "Reports & Exports"
        WorkspaceSectionRoute.SETTINGS_PROFILE -> "Settings & Profile"
        WorkspaceSectionRoute.GLOBAL_SEARCH -> "Global Search"
        WorkspaceSectionRoute.TIMETABLE_HOMEWORK -> "Timetable & Homework"
        WorkspaceSectionRoute.NOTIFICATIONS -> "Notifications"
        null -> when (selectedBottomTab) {
            "home" -> "BL School Hub"
            "classes" -> "Classes & Sessions"
            "my_school" -> "School Explorer"
            "attendance" -> "Attendance"
            "results" -> "Results"
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
                        if (onOpenAdminDashboard != null) {
                            FilledTonalButton(
                                onClick = onOpenAdminDashboard,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFEF3C7),
                                    contentColor = NavyPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .padding(end = 4.dp)
                                    .height(36.dp)
                                    .testTag("topbar_admin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = "Admin Dashboard",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Admin", style = MaterialTheme.typography.labelLarge)
                            }
                        }
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
                                    if (item.id == "my_school") studentSchoolInitialTab = 0
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
                                    if (item.id == "my_school") studentSchoolInitialTab = 0
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
                                    WorkspaceSectionRoute.ATTENDANCE_FILTER ->
                                        AttendancePercentageFilterScreen(
                                            state = hubState,
                                            onNavigateToAttendance = {
                                                activeSubRoute = if (role == UserRole.STUDENT) {
                                                    selectedBottomTab = "attendance"
                                                    null
                                                } else {
                                                    WorkspaceSectionRoute.ATTENDANCE
                                                }
                                            }
                                        )
                                    WorkspaceSectionRoute.TESTS_RESULTS ->
                                        TestsAndResultsPanel(hubState, hubViewModel)
                                    WorkspaceSectionRoute.REPORTS ->
                                        ReportsCenterScreen(hubState, hubViewModel)
                                    WorkspaceSectionRoute.SETTINGS_PROFILE ->
                                        ProfileAndSettingsScreen(hubState, hubViewModel, authViewModel)
                                    WorkspaceSectionRoute.GLOBAL_SEARCH ->
                                        GlobalSearchScreen(hubState, hubViewModel)
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
                                            onNavigate = { route -> activeSubRoute = route },
                                            onEditSchool = { showQuickEditSchoolDialog = true },
                                            onToggleVisibility = hubViewModel::toggleSchoolVisibility
                                        )
                                        "classes" -> ClassesAndSessionsScreen(hubState, hubViewModel)
                                        "attendance" -> AttendanceWorkspacePanel(hubState, hubViewModel)
                                        "notifications" -> NotificationsScreen(hubState, hubViewModel)
                                        "profile" -> ProfileAndSettingsScreen(hubState, hubViewModel, authViewModel)
                                    }
                                    UserRole.STUDENT -> when (selectedBottomTab) {
                                        "home" -> StudentHomeScreen(
                                            state = hubState,
                                            viewModel = hubViewModel,
                                            onOpenNotifications = { activeSubRoute = WorkspaceSectionRoute.NOTIFICATIONS },
                                            onOpenAttendance = { selectedBottomTab = "attendance" },
                                            onOpenAttendanceFilter = { activeSubRoute = WorkspaceSectionRoute.ATTENDANCE_FILTER },
                                            onOpenClasses = {
                                                studentSchoolInitialTab = 0
                                                selectedBottomTab = "my_school"
                                            },
                                            onOpenResults = { selectedBottomTab = "results" },
                                            onOpenTimetableHomework = { activeSubRoute = WorkspaceSectionRoute.TIMETABLE_HOMEWORK },
                                            onOpenTeachers = {
                                                studentSchoolInitialTab = 2
                                                selectedBottomTab = "my_school"
                                            },
                                            onSwitchSchool = authViewModel::switchSchoolAsStudent,
                                            pinnedSchools = authState.pinnedSchools,
                                            isCurrentSchoolPinned = authState.pinnedSchools.any { it.id == school.id } ||
                                                authState.user?.pinnedSchoolIds?.contains(school.id) == true,
                                            onTogglePinCurrentSchool = { authViewModel.togglePinSchool(school) },
                                            onSelectPinnedSchool = { pinned -> authViewModel.enterPublicSchoolAsStudent(pinned) }
                                        )
                                        "my_school" -> StudentMySchoolScreen(hubState, hubViewModel, initialTab = studentSchoolInitialTab)
                                        "attendance" -> StudentAttendanceScreen(hubState, hubViewModel)
                                        "results" -> StudentResultsScreen(hubState, hubViewModel)
                                        "profile" -> ProfileAndSettingsScreen(hubState, hubViewModel, authViewModel)
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

    if (showQuickEditSchoolDialog && (role == UserRole.PRINCIPAL || role == UserRole.TEACHER)) {
        EditSchoolDialog(
            state = hubState,
            onDismiss = { showQuickEditSchoolDialog = false },
            onSave = { name, type, address, city, phone, email, desc, year, web, logoUri, bannerUri, clearLogo, clearBanner ->
                hubViewModel.updateSchoolProfile(
                    name = name,
                    schoolType = type,
                    address = address,
                    city = city,
                    contactNumber = phone,
                    email = email,
                    description = desc,
                    establishedYear = year,
                    website = web,
                    logoUri = logoUri,
                    bannerUri = bannerUri,
                    galleryUriToAdd = null,
                    clearLogo = clearLogo,
                    clearBanner = clearBanner
                )
                showQuickEditSchoolDialog = false
            }
        )
    }
}
