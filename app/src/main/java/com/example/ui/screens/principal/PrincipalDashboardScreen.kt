package com.example.ui.screens.principal

import androidx.compose.foundation.Image
import com.example.data.model.toDateOnlyString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceStatus
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.ShortcutTile
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.ui.viewmodel.HubUiState
import java.time.LocalDate

enum class WorkspaceSectionRoute {
    SCHOOL_PROFILE_POSTS,
    CLASSES_SESSIONS,
    STUDENTS,
    TEACHERS,
    ATTENDANCE,
    ATTENDANCE_FILTER,
    TESTS_RESULTS,
    ANNOUNCEMENTS,
    REPORTS,
    SETTINGS_PROFILE,
    GLOBAL_SEARCH,
    TIMETABLE_HOMEWORK,
    NOTIFICATIONS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrincipalAndTeacherDashboardScreen(
    state: HubUiState,
    onSelectSession: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (WorkspaceSectionRoute) -> Unit,
    onEditSchool: (() -> Unit)? = null,
    onToggleVisibility: ((Boolean) -> Unit)? = null
) {
    val school = state.school ?: return
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val isStaff = isPrincipal || state.role == UserRole.TEACHER
    val todayDate = LocalDate.now()
    val todayDisplay = AcademicCalculators.todayDisplayDate()

    val todaySessionIds = remember(state.attendanceSessions, state.activeAcademicSession, todayDisplay) {
        val sid = state.activeAcademicSession?.id
        state.attendanceSessions
            .filter {
                AcademicCalculators.formatDisplayDate(it.attendanceDate) == todayDisplay &&
                    (sid == null || it.sessionId == sid)
            }
            .map { it.id }
            .toSet()
    }
    val todayRecords = remember(state.attendanceRecords, todaySessionIds) {
        state.attendanceRecords.filter { it.attendanceSessionId in todaySessionIds }
    }
    val presentStudentsCount = remember(todayRecords) {
        todayRecords.filter { it.status == AttendanceStatus.PRESENT.dbValue }.map { it.studentId }.distinct().size
    }
    val absentStudentsCount = remember(todayRecords) {
        todayRecords.filter { it.status == AttendanceStatus.ABSENT.dbValue }.map { it.studentId }.distinct().size
    }
    val todayAttendancePct = if (presentStudentsCount + absentStudentsCount == 0) "—" else {
        val pct = (presentStudentsCount * 100.0) / (presentStudentsCount + absentStudentsCount)
        String.format("%.0f%%", pct)
    }

    val upcomingTests = remember(state.tests, state.activeAcademicSession, todayDate) {
        val sid = state.activeAcademicSession?.id
        state.tests.filter {
            (sid == null || it.sessionId == sid) &&
                (AcademicCalculators.parseFlexibleDate(it.testDate)?.let { d -> !d.isBefore(todayDate) } ?: true)
        }.take(4)
    }
    val recentPosts = state.announcements.take(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            // School Hero Cover Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(175.dp)) {
                    if (!school.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = school.bannerUrl,
                            contentDescription = "${school.name} Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_default_school_banner),
                            contentDescription = "Default School Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(NavyDeep.copy(alpha = 0.25f), NavyDeep.copy(alpha = 0.88f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isStaff && onEditSchool != null) {
                                IconButton(
                                    onClick = onEditSchool,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dashboard_edit_school_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit School", tint = Color.White)
                                }
                            }
                            IconButton(
                                onClick = { onNavigate(WorkspaceSectionRoute.GLOBAL_SEARCH) },
                                modifier = Modifier.testTag("dashboard_search_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Global Search", tint = Color.White)
                            }
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("dashboard_refresh_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Workspace", tint = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SchoolAvatar(school.logoUrl, school.name, size = 58.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = school.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${if (isPrincipal) "Principal" else "Teacher"} • ID: ${school.schoolCode} • ${school.city}${if (!school.isPublic) " • HIDDEN" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.86f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Academic Session Selector Row
            var showSessionMenu by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    AssistChip(
                        onClick = { showSessionMenu = true },
                        label = {
                            Text(
                                text = "Session: ${state.activeAcademicSession?.name ?: "Create Session"}",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("session_selector_chip")
                    )
                    DropdownMenu(
                        expanded = showSessionMenu,
                        onDismissRequest = { showSessionMenu = false }
                    ) {
                        state.sessions.forEach { session ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (session.isCurrent) "${session.name} (Current)" else session.name)
                                },
                                onClick = {
                                    onSelectSession(session.id)
                                    showSessionMenu = false
                                }
                            )
                        }
                        if (isStaff) {
                            DropdownMenuItem(
                                text = { Text("+ Manage / New Session") },
                                onClick = {
                                    showSessionMenu = false
                                    onNavigate(WorkspaceSectionRoute.CLASSES_SESSIONS)
                                }
                            )
                        }
                    }
                }

                if (isPrincipal && onToggleVisibility != null) {
                    AssistChip(
                        onClick = { onToggleVisibility(!school.isPublic) },
                        label = {
                            Text(
                                text = if (school.isPublic) "Hide School" else "School Hidden (Unhide)",
                                color = if (school.isPublic) NavyPrimary else AbsentRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (school.isPublic) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (school.isPublic) "Hide School" else "Unhide School",
                                tint = if (school.isPublic) NavyPrimary else AbsentRed,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("dashboard_visibility_toggle")
                    )
                } else if (!school.isPublic) {
                    StatusPill(
                        label = "HIDDEN FROM STUDENTS",
                        textColor = AbsentRed,
                        backgroundColor = AbsentRedBg
                    )
                }
            }

            if (!school.isPublic) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AbsentRedBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "This school is hidden from students and public search. Only the Principal and school Teachers can open it.",
                        color = AbsentRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Key Metrics Grid (2x3)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                StatMetricCard(
                    title = "Total Students",
                    value = state.sessionStudents.size.toString(),
                    icon = Icons.Rounded.Groups,
                    accentColor = RoyalBlue,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Total Teachers",
                    value = state.teachers.size.toString(),
                    icon = Icons.Rounded.Badge,
                    accentColor = NavyPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Total Classes",
                    value = state.sessionSections.size.toString(),
                    icon = Icons.Rounded.Class,
                    accentColor = SkyAccent,
                    subtitle = "${state.sessionClasses.size} grades",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Today's Attendance",
                    value = todayAttendancePct,
                    icon = Icons.Rounded.EventAvailable,
                    accentColor = PresentGreen,
                    subtitle = "Tap for register",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(WorkspaceSectionRoute.ATTENDANCE) }
                )
                StatMetricCard(
                    title = "Present Today",
                    value = presentStudentsCount.toString(),
                    icon = Icons.Rounded.CheckCircle,
                    accentColor = PresentGreen,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Absent Today",
                    value = absentStudentsCount.toString(),
                    icon = Icons.Rounded.PersonOff,
                    accentColor = AbsentRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isPrincipal) "School Management Shortcuts" else "Teaching Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary
            )
            Spacer(Modifier.height(10.dp))

            // Shortcuts Grid
            val shortcuts = buildList {
                add(Triple("School Profile", Icons.Rounded.Business, WorkspaceSectionRoute.SCHOOL_PROFILE_POSTS))
                add(Triple("Classes", Icons.Rounded.Class, WorkspaceSectionRoute.CLASSES_SESSIONS))
                if (isPrincipal || state.currentTeacherPermissions.viewStudents) {
                    add(Triple("Students", Icons.Rounded.Groups, WorkspaceSectionRoute.STUDENTS))
                }
                if (isPrincipal) {
                    add(Triple("Teachers & Links", Icons.Rounded.HowToReg, WorkspaceSectionRoute.TEACHERS))
                }
                add(Triple("Attendance", Icons.Rounded.EventAvailable, WorkspaceSectionRoute.ATTENDANCE))
                add(Triple("Att. % Filter", Icons.Rounded.EventAvailable, WorkspaceSectionRoute.ATTENDANCE_FILTER))
                add(Triple("Tests & Results", Icons.Rounded.Assignment, WorkspaceSectionRoute.TESTS_RESULTS))
                add(Triple("Announcements", Icons.Rounded.Campaign, WorkspaceSectionRoute.ANNOUNCEMENTS))
                if (isPrincipal || state.currentTeacherPermissions.viewReports) {
                    add(Triple("Reports", Icons.Rounded.Assessment, WorkspaceSectionRoute.REPORTS))
                }
                add(Triple("Timetable & HW", Icons.Rounded.MenuBook, WorkspaceSectionRoute.TIMETABLE_HOMEWORK))
                add(Triple("Settings", Icons.Rounded.Settings, WorkspaceSectionRoute.SETTINGS_PROFILE))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                shortcuts.forEach { (title, icon, route) ->
                    ShortcutTile(
                        title = title,
                        icon = icon,
                        badgeCount = 0,
                        testTag = "shortcut_${title.lowercase().replace(" ", "_").replace(".", "").replace("&", "and")}",
                        onClick = { onNavigate(route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Upcoming Tests
            SectionCard(
                title = "Upcoming Tests",
                trailing = {
                    TextButton(onClick = { onNavigate(WorkspaceSectionRoute.TESTS_RESULTS) }) {
                        Text("See All")
                    }
                }
            ) {
                if (upcomingTests.isEmpty()) {
                    Text(
                        text = "No upcoming tests scheduled in ${state.activeAcademicSession?.name ?: "current session"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    upcomingTests.forEach { test ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(test.name, style = MaterialTheme.typography.titleSmall)
                                Text("${test.subject} • ${AcademicCalculators.formatDisplayDate(test.testDate)}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(
                                label = test.status.uppercase(),
                                textColor = RoyalBlue,
                                backgroundColor = SkySoft
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Recent Announcements
            SectionCard(
                title = "Recent Announcements",
                trailing = {
                    TextButton(onClick = { onNavigate(WorkspaceSectionRoute.ANNOUNCEMENTS) }) {
                        Text("Open Feed")
                    }
                }
            ) {
                if (recentPosts.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Campaign,
                        title = "No announcements yet",
                        description = "Publish school news, holiday notices, or class updates to notify teachers and students."
                    )
                } else {
                    recentPosts.forEach { post ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = SkySoft.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(post.title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                    StatusPill(post.category, RoyalBlue, SkySoft)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(post.body, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "By ${post.publisherName} • ${post.createdAt.toDateOnlyString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Recent Activities
            SectionCard(title = "Recent Activities") {
                val recentAttendance = state.attendanceSessions.take(3)
                val recentHomework = state.homework.take(2)
                if (recentAttendance.isEmpty() && recentHomework.isEmpty()) {
                    Text(
                        text = "Activities from attendance, tests, and homework will appear here as your school operates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    recentAttendance.forEach { att ->
                        Text(
                            text = "• Attendance recorded: ${att.subject} (${att.sessionSlot}) on ${AcademicCalculators.formatDisplayDate(att.attendanceDate)} by ${att.teacherName}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                    recentHomework.forEach { hw ->
                        Text(
                            text = "• Homework assigned: ${hw.subject} — ${hw.title} (Due ${AcademicCalculators.formatDisplayDate(hw.dueDate)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
