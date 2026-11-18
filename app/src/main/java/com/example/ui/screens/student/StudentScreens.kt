package com.example.ui.screens.student

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.School
import com.example.data.model.StudentRecord
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.ShortcutTile
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.screens.principal.AnnouncementPostCard
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveOrange
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.screens.shared.AttendancePercentageFilterComponent
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentHomeScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    onOpenNotifications: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenAttendanceFilter: () -> Unit,
    onOpenClasses: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenTimetableHomework: () -> Unit,
    onOpenTeachers: () -> Unit,
    onSwitchSchool: (() -> Unit)? = null,
    pinnedSchools: List<School> = emptyList(),
    isCurrentSchoolPinned: Boolean = false,
    onTogglePinCurrentSchool: (() -> Unit)? = null,
    onSelectPinnedSchool: ((School) -> Unit)? = null
) {
    val school = state.school ?: return
    val totalTestsCount = state.tests.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            // Attractive Hero Card — intentionally omits student's own class/section assignment
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(165.dp)) {
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
                            contentDescription = "School Cover",
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
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .testTag("student_notifications_btn")
                        ) {
                            BadgedBox(badge = {
                                if (state.unreadNotificationsCount > 0) {
                                    Badge { Text(state.unreadNotificationsCount.toString()) }
                                }
                            }) {
                                Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = Color.White)
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
                                    text = state.currentUser?.fullName.orEmpty().ifBlank { "Student Portal" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${school.name} • ${school.city}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                                Text(
                                    text = "School ID: ${school.schoolCode} • Read-Only Explorer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkySoft
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onTogglePinCurrentSchool != null) {
                    AssistChip(
                        onClick = onTogglePinCurrentSchool,
                        label = {
                            Text(
                                if (isCurrentSchoolPinned) {
                                    "Pinned (${pinnedSchools.size}/3)"
                                } else {
                                    "Pin School (${pinnedSchools.size}/3)"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isCurrentSchoolPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isCurrentSchoolPinned) "Unpin current school" else "Pin current school",
                                tint = if (isCurrentSchoolPinned) RoyalBlue else NavyPrimary
                            )
                        },
                        modifier = Modifier.testTag("student_pin_current_school_button")
                    )
                }
                if (onSwitchSchool != null) {
                    AssistChip(
                        onClick = onSwitchSchool,
                        label = { Text("Search / Switch School") },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                        modifier = Modifier.testTag("student_switch_school_button")
                    )
                }
            }
            if (pinnedSchools.isNotEmpty() && onSelectPinnedSchool != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pinnedSchools.forEach { pinned ->
                        val isCurrent = pinned.id == school.id
                        FilterChip(
                            selected = isCurrent,
                            onClick = { if (!isCurrent) onSelectPinnedSchool(pinned) },
                            label = {
                                Text(
                                    text = pinned.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Rounded.CheckCircle else Icons.Rounded.PushPin,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.testTag("student_quick_pinned_${pinned.schoolCode}")
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Quick School Overview Metrics (read-only school-wide)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                StatMetricCard(
                    title = "Classes",
                    value = state.sessionSections.size.toString(),
                    icon = Icons.Rounded.Class,
                    accentColor = RoyalBlue,
                    subtitle = "${state.attendanceSessions.size} att. sheets",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Students",
                    value = state.sessionStudents.size.toString(),
                    icon = Icons.Rounded.Groups,
                    accentColor = SkyAccent,
                    subtitle = "${state.teachers.size} teachers",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Tests & HW",
                    value = totalTestsCount.toString(),
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    accentColor = PresentGreen,
                    subtitle = "${state.homework.size} homework",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Explore School",
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary
            )
            Spacer(Modifier.height(10.dp))

            // Colorful Icon Shortcut Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                ShortcutTile(
                    title = "Attendance",
                    icon = Icons.Rounded.EventAvailable,
                    badgeCount = state.attendanceSessions.size,
                    testTag = "student_shortcut_attendance",
                    onClick = onOpenAttendance,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "Att. % Filter",
                    icon = Icons.Rounded.EventAvailable,
                    badgeCount = 0,
                    testTag = "student_shortcut_att_filter",
                    onClick = onOpenAttendanceFilter,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "Classes & Students",
                    icon = Icons.Rounded.Groups,
                    badgeCount = state.sessionSections.size,
                    testTag = "student_shortcut_classes",
                    onClick = onOpenClasses,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "Results",
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    badgeCount = totalTestsCount,
                    testTag = "student_shortcut_results",
                    onClick = onOpenResults,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "Timetable & HW",
                    icon = Icons.Rounded.MenuBook,
                    badgeCount = state.homework.size,
                    testTag = "student_shortcut_timetable",
                    onClick = onOpenTimetableHomework,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "School & Teachers",
                    icon = Icons.Rounded.Business,
                    badgeCount = state.teachers.size,
                    testTag = "student_shortcut_school",
                    onClick = onOpenTeachers,
                    modifier = Modifier.weight(1f)
                )
                ShortcutTile(
                    title = "Notices",
                    icon = Icons.Rounded.Notifications,
                    badgeCount = state.unreadNotificationsCount,
                    testTag = "student_shortcut_notices",
                    onClick = onOpenNotifications,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Announcements & School Updates",
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyPrimary
                )
                Text(
                    text = "${state.announcements.size} posts",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoyalBlue
                )
            }
            Spacer(Modifier.height(8.dp))

            if (state.announcements.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Campaign,
                    title = "No Announcements Yet",
                    description = "School-wide notices, events, and class updates will appear here. You can like and comment on posts!"
                )
            } else {
                state.announcements.forEach { post ->
                    AnnouncementPostCard(
                        post = post,
                        comments = state.commentsByAnnouncement[post.id].orEmpty(),
                        canDelete = false,
                        onLike = { viewModel.toggleAnnouncementLike(post.id) },
                        onLoadComments = { viewModel.loadComments(post.id) },
                        onAddComment = { text -> viewModel.addComment(post.id, text) },
                        onDelete = {}
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private data class StudentAttendanceLogEntry(
    val record: AttendanceRecord,
    val session: AttendanceSession,
    val student: StudentRecord?,
    val className: String,
    val sectionName: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentAttendanceScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    var selectedSectionId by remember(state.sessionSections) { mutableStateOf<String?>(null) }
    var selectedDate by remember(state.activeAcademicSession?.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var viewTab by remember { mutableIntStateOf(0) } // 0 = Roll # Sheets, 1 = By Student, 2 = Status Log
    var logStatusFilter by remember { mutableStateOf("all") }
    var detailStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var selectedAttSession by remember { mutableStateOf<AttendanceSession?>(null) }

    val sessionFilteredAttSessions = remember(state.attendanceSessions, state.classes, state.sections, state.activeAcademicSession, selectedSectionId) {
        val sid = state.activeAcademicSession?.id
        AcademicCalculators.sortAttendanceSessions(
            state.attendanceSessions.filter { sess ->
                (sid == null || sess.sessionId == sid) &&
                    (selectedSectionId == null || sess.sectionId == selectedSectionId)
            },
            state.classes,
            state.sections
        )
    }

    val availableDates = remember(sessionFilteredAttSessions) {
        sessionFilteredAttSessions
            .map { AcademicCalculators.formatDisplayDate(it.attendanceDate) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val q = searchQuery.trim()
    val filteredSessions = remember(sessionFilteredAttSessions, selectedDate, q, state.attendanceRecords, state.students) {
        val studentById = state.students.associateBy { it.id }
        sessionFilteredAttSessions.filter { sess ->
            val displayDate = AcademicCalculators.formatDisplayDate(sess.attendanceDate)
            val dateOk = selectedDate == null || displayDate == selectedDate
            val qOk = if (q.isBlank()) true else {
                sess.subject.contains(q, true) ||
                    sess.sessionSlot.contains(q, true) ||
                    sess.teacherName.contains(q, true) ||
                    displayDate.contains(q, true) ||
                    state.attendanceRecords.any { rec ->
                        rec.attendanceSessionId == sess.id && studentById[rec.studentId]?.let { stu ->
                            stu.fullName.contains(q, true) || stu.rollNumber.contains(q, true)
                        } == true
                    }
            }
            dateOk && qOk
        }
    }

    val filteredSessionIds = remember(filteredSessions) { filteredSessions.map { it.id }.toSet() }
    val filteredRecords = remember(state.attendanceRecords, filteredSessionIds) {
        state.attendanceRecords.filter { it.attendanceSessionId in filteredSessionIds }
    }

    val visibleStudents = remember(state.sessionStudents, state.classes, state.sections, selectedSectionId, q) {
        AcademicCalculators.sortStudentsByClassAndRoll(
            state.sessionStudents.filter { stu ->
                (selectedSectionId == null || stu.sectionId == selectedSectionId) &&
                    (q.isBlank() || stu.fullName.contains(q, true) || stu.rollNumber.contains(q, true))
            },
            state.classes,
            state.sections
        )
    }

    val statusLogEntries = remember(filteredSessions, filteredRecords, state.students, state.classes, state.sections, logStatusFilter, q) {
        val sessById = filteredSessions.associateBy { it.id }
        val stuById = state.students.associateBy { it.id }
        val clsById = state.classes.associateBy { it.id }
        val secById = state.sections.associateBy { it.id }
        val secRanks = AcademicCalculators.sortSections(state.sections, state.classes)
            .withIndex().associate { (idx, sec) -> sec.id to idx }
        filteredRecords.mapNotNull { rec ->
            val sess = sessById[rec.attendanceSessionId] ?: return@mapNotNull null
            val stu = stuById[rec.studentId]
            val stOk = logStatusFilter == "all" || rec.status == logStatusFilter
            val qOk = if (q.isBlank()) true else {
                (stu?.fullName?.contains(q, true) == true) ||
                    (stu?.rollNumber?.contains(q, true) == true) ||
                    sess.subject.contains(q, true) ||
                    sess.teacherName.contains(q, true) ||
                    AcademicCalculators.formatDisplayDate(sess.attendanceDate).contains(q, true)
            }
            if (!stOk || !qOk) return@mapNotNull null
            StudentAttendanceLogEntry(
                record = rec,
                session = sess,
                student = stu,
                className = clsById[sess.classId]?.name.orEmpty(),
                sectionName = secById[sess.sectionId]?.name.orEmpty()
            )
        }.sortedWith { a, b ->
            val d1 = AcademicCalculators.parseFlexibleDate(a.session.attendanceDate)?.toEpochDay() ?: 0L
            val d2 = AcademicCalculators.parseFlexibleDate(b.session.attendanceDate)?.toEpochDay() ?: 0L
            if (d1 != d2) return@sortedWith d2.compareTo(d1)
            val secCmp = (secRanks[a.session.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.session.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            val slotCmp = AcademicCalculators.compareLectureSlots(a.session.sessionSlot, b.session.sessionSlot)
            if (slotCmp != 0) return@sortedWith slotCmp
            val r1 = a.student?.rollNumber?.trim()?.toIntOrNull() ?: Int.MAX_VALUE
            val r2 = b.student?.rollNumber?.trim()?.toIntOrNull() ?: Int.MAX_VALUE
            if (r1 != r2) return@sortedWith r1.compareTo(r2)
            AcademicCalculators.naturalCompare(a.student?.fullName.orEmpty(), b.student?.fullName.orEmpty())
        }
    }

    val classP = filteredRecords.count { it.status == AttendanceStatus.PRESENT.dbValue }
    val classA = filteredRecords.count { it.status == AttendanceStatus.ABSENT.dbValue }
    val classL = filteredRecords.count { it.status == AttendanceStatus.LEAVE.dbValue }
    val classTotal = filteredRecords.size
    val classPct = if (classTotal == 0) 0.0 else (classP * 100.0) / classTotal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            if (state.sessions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.sessions.forEach { sess ->
                        FilterChip(
                            selected = state.activeAcademicSession?.id == sess.id,
                            onClick = { viewModel.selectAcademicSession(sess.id) },
                            label = { Text(sess.name) },
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (state.sessionSections.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSectionId == null,
                        onClick = { selectedSectionId = null },
                        label = { Text("All Classes") }
                    )
                    state.sessionSections.forEach { sec ->
                        val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                        FilterChip(
                            selected = selectedSectionId == sec.id,
                            onClick = { selectedSectionId = sec.id },
                            label = { Text("$cls ${sec.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (availableDates.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedDate == null,
                        onClick = { selectedDate = null },
                        label = { Text("All Dates") }
                    )
                    availableDates.forEach { dt ->
                        FilterChip(
                            selected = selectedDate == dt,
                            onClick = { selectedDate = dt },
                            label = { Text(dt) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search student name, roll #, subject, or date") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_attendance_search")
            )
            Spacer(Modifier.height(10.dp))

            SectionCard(title = if (selectedSectionId == null) "School Attendance Summary" else "Selected Class Attendance Summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sheets: ${filteredSessions.size}", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                    Text("Present: $classP", style = MaterialTheme.typography.labelMedium, color = PresentGreen)
                    Text("Absent: $classA", style = MaterialTheme.typography.labelMedium, color = AbsentRed)
                    Text("Leave: $classL", style = MaterialTheme.typography.labelMedium, color = LeaveOrange)
                    StatusPill(
                        label = String.format(Locale.US, "%.1f%%", classPct),
                        textColor = if (classPct >= 75.0 || classTotal == 0) PresentGreen else AbsentRed,
                        backgroundColor = if (classPct >= 75.0 || classTotal == 0) PresentGreenBg else AbsentRedBg
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            SecondaryTabRow(selectedTabIndex = viewTab) {
                Tab(
                    selected = viewTab == 0,
                    onClick = { viewTab = 0 },
                    text = { Text("Roll # Sheets (${filteredSessions.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_att_tab_sheets")
                )
                Tab(
                    selected = viewTab == 1,
                    onClick = { viewTab = 1 },
                    text = { Text("By Student (${visibleStudents.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_att_tab_students")
                )
                Tab(
                    selected = viewTab == 2,
                    onClick = { viewTab = 2 },
                    text = { Text("Status Log", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_att_tab_log")
                )
                Tab(
                    selected = viewTab == 3,
                    onClick = { viewTab = 3 },
                    text = { Text("% Filter", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_att_tab_filter")
                )
            }
            Spacer(Modifier.height(10.dp))

            when (viewTab) {
                0 -> {
                    Surface(
                        color = SkySoft,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewTab = 3 }
                            .padding(bottom = 10.dp)
                            .testTag("student_open_percent_filter_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = RoyalBlue)
                                Column {
                                    Text("Attendance Percentage Filter (% Filter)", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                    Text("Filter students by % threshold (e.g. above/below 75%), class & roll #", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            TextButton(onClick = { viewTab = 3 }) {
                                Text("Open Filter →")
                            }
                        }
                    }

                    if (filteredSessions.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.EventAvailable,
                            title = "No Attendance Sheets Found",
                            description = "Daily & lecture attendance registers showing every Roll # Present, Absent, or Leave will appear here."
                        )
                    } else {
                        filteredSessions.forEachIndexed { idx, sess ->
                            ExpandableAttendanceSessionCard(
                                session = sess,
                                state = state,
                                defaultExpanded = idx == 0,
                                onOpenFullSheet = { selectedAttSession = it },
                                onStudentClick = { detailStudent = it }
                            )
                        }
                    }
                }

                1 -> {
                    if (visibleStudents.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Groups,
                            title = "No Student Records Found",
                            description = "Students and their attendance percentages across all classes will appear here."
                        )
                    } else {
                        visibleStudents.forEach { stu ->
                            val cls = state.classes.find { it.id == stu.classId }?.name.orEmpty()
                            val sec = state.sections.find { it.id == stu.sectionId }?.name.orEmpty()
                            val summary = remember(stu.id, state.attendanceSessions, state.attendanceRecords) {
                                AcademicCalculators.computeStudentAttendanceSummary(
                                    stu.id,
                                    state.attendanceSessions,
                                    state.attendanceRecords
                                )
                            }
                            SectionCard(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable { detailStudent = stu }
                                    .testTag("student_att_card_${stu.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Roll #${stu.rollNumber} • ${stu.fullName}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                        Text("$cls-$sec • Father: ${stu.fatherName}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill(
                                        label = String.format(Locale.US, "%.1f%% Present", summary.dailyStats.percentage),
                                        textColor = if (summary.dailyStats.percentage >= 75.0 || summary.dailyStats.totalCount == 0) PresentGreen else AbsentRed,
                                        backgroundColor = if (summary.dailyStats.percentage >= 75.0 || summary.dailyStats.totalCount == 0) PresentGreenBg else AbsentRedBg
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Present: ${summary.dailyStats.presentCount}", style = MaterialTheme.typography.labelSmall, color = PresentGreen)
                                    Text("Absent: ${summary.dailyStats.absentCount}", style = MaterialTheme.typography.labelSmall, color = AbsentRed)
                                    Text("Leave: ${summary.dailyStats.leaveCount}", style = MaterialTheme.typography.labelSmall, color = LeaveOrange)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(selected = logStatusFilter == "all", onClick = { logStatusFilter = "all" }, label = { Text("All") })
                        FilterChip(selected = logStatusFilter == AttendanceStatus.ABSENT.dbValue, onClick = { logStatusFilter = AttendanceStatus.ABSENT.dbValue }, label = { Text("Absent ($classA)") })
                        FilterChip(selected = logStatusFilter == AttendanceStatus.PRESENT.dbValue, onClick = { logStatusFilter = AttendanceStatus.PRESENT.dbValue }, label = { Text("Present ($classP)") })
                        FilterChip(selected = logStatusFilter == AttendanceStatus.LEAVE.dbValue, onClick = { logStatusFilter = AttendanceStatus.LEAVE.dbValue }, label = { Text("Leave ($classL)") })
                    }
                    Spacer(Modifier.height(8.dp))

                    if (statusLogEntries.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.EventAvailable,
                            title = "No Matching Entries",
                            description = "No individual student attendance entries match the selected filters."
                        )
                    } else {
                        statusLogEntries.forEach { entry ->
                            val (fg, bg) = when (entry.record.status) {
                                AttendanceStatus.PRESENT.dbValue -> PresentGreen to PresentGreenBg
                                AttendanceStatus.ABSENT.dbValue -> AbsentRed to AbsentRedBg
                                else -> WarningAmber to WarningAmberBg
                            }
                            SectionCard(
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .clickable(enabled = entry.student != null) {
                                        entry.student?.let { detailStudent = it }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = SkySoft) {
                                            Text(
                                                text = "#${entry.student?.rollNumber ?: "-"}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${entry.student?.fullName ?: "Student"} (${entry.className}-${entry.sectionName})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "${AcademicCalculators.formatDisplayDate(entry.session.attendanceDate)} • ${entry.session.subject} (${entry.session.sessionSlot})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            val extra = listOfNotNull(
                                                entry.session.teacherName.takeIf { it.isNotBlank() }?.let { "By $it" },
                                                entry.record.note.takeIf { it.isNotBlank() }?.let { "Note: $it" }
                                            ).joinToString(" • ")
                                            if (extra.isNotBlank()) {
                                                Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(entry.record.status.uppercase(), fg, bg)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    AttendancePercentageFilterComponent(state = state)
                }
            }
        }
    }

    if (detailStudent != null) {
        StudentReadOnlyDetailDialog(
            student = detailStudent!!,
            state = state,
            viewModel = viewModel,
            onDismiss = { detailStudent = null }
        )
    }

    if (selectedAttSession != null) {
        AttendanceSessionSheetDialog(
            session = selectedAttSession!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                selectedAttSession = null
                detailStudent = stu
            },
            onDismiss = { selectedAttSession = null }
        )
    }
}
