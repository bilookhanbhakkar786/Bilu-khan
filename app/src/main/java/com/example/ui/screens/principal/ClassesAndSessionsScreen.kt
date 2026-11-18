package com.example.ui.screens.principal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.ClassSection
import com.example.data.model.SchoolClass
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.ShortcutTile
import com.example.ui.components.StatusPill
import com.example.ui.screens.shared.AttendanceWorkspacePanel
import com.example.ui.screens.shared.ClassFeesPanel
import com.example.ui.screens.shared.TestsAndResultsPanel
import com.example.ui.screens.shared.TimetableAndHomeworkPanel
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassesAndSessionsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val canManageClasses = isPrincipal || state.role == UserRole.TEACHER
    var selectedSection by remember { mutableStateOf<ClassSection?>(null) }
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var showCreateClassDialog by remember { mutableStateOf(false) }

    if (selectedSection != null) {
        val currentSection = state.sections.find { it.id == selectedSection!!.id } ?: selectedSection!!
        val parentClass = state.classes.find { it.id == currentSection.classId }
        BackHandler { selectedSection = null }
        ClassDashboardScreen(
            state = state,
            viewModel = viewModel,
            schoolClass = parentClass,
            section = currentSection,
            onBack = { selectedSection = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 880.dp)) {
            // Academic Sessions Card
            SectionCard(
                title = "Academic Sessions",
                trailing = {
                    if (canManageClasses) {
                        TextButton(
                            onClick = { showCreateSessionDialog = true },
                            modifier = Modifier.testTag("new_session_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("New Session")
                        }
                    }
                }
            ) {
                if (state.sessions.isEmpty()) {
                    Text(
                        text = "Create an Academic Session first (e.g. 2025-2026) before adding classes. Each session keeps separate classes and historical records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.sessions.forEach { session ->
                            val isSelected = session.id == state.activeAcademicSession?.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectAcademicSession(session.id) },
                                label = {
                                    Text(if (session.isCurrent) "${session.name} (Current)" else session.name)
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                                }
                            )
                        }
                    }
                    if (canManageClasses && state.activeAcademicSession != null && !state.activeAcademicSession!!.isCurrent) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.setCurrentAcademicSession(state.activeAcademicSession!!.id) }
                        ) {
                            Text("Set ${state.activeAcademicSession!!.name} as Active Current Session")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Classes & Sections List for Selected Session
            SectionCard(
                title = "Classes (${state.activeAcademicSession?.name ?: "No Session"})",
                trailing = {
                    if (canManageClasses && state.activeAcademicSession != null) {
                        Button(
                            onClick = { showCreateClassDialog = true },
                            modifier = Modifier.testTag("create_class_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Create Class")
                        }
                    }
                }
            ) {
                if (state.activeAcademicSession == null) {
                    EmptyStateCard(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Select or Create an Academic Session",
                        description = "Every class belongs to an academic session so records never mix across years."
                    )
                } else if (state.sessionSections.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Class,
                        title = "No classes in ${state.activeAcademicSession?.name}",
                        description = "Tap Create Class to add classes (Playgroup to 12th) and sections (A, B, C, D)."
                    )
                } else {
                    val sectionsByClass = state.sessionSections.groupBy { it.classId }
                    state.sessionClasses.forEach { cls ->
                        val clsSections = sectionsByClass[cls.id].orEmpty()
                        if (clsSections.isNotEmpty() || canManageClasses) {
                            Text(
                                text = cls.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            clsSections.forEach { sec ->
                                val stuCount = state.sessionStudents.count { it.sectionId == sec.id }
                                val teacherName = state.teachers.find { it.id == sec.classTeacherId }?.fullName ?: "Unassigned"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedSection = sec }
                                        .testTag("class_section_card_${sec.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SkySoft.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${cls.name} — Section ${sec.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "Teacher: $teacherName • $stuCount Students",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill("Open Class", RoyalBlue, SkySoft)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSessionDialog) {
        var name by remember { mutableStateOf("2026-2027") }
        var startDate by remember { mutableStateOf("01-04-2026") }
        var endDate by remember { mutableStateOf("31-03-2027") }
        var makeCurrent by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            title = { Text("Create Academic Session") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Session Name (e.g. 2026-2027) *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = makeCurrent, onCheckedChange = { makeCurrent = it })
                        Text("Set as Current Session", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createAcademicSession(name, startDate, endDate, makeCurrent)
                    showCreateSessionDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateSessionDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateClassDialog && state.activeAcademicSession != null) {
        val gradeOptions = listOf("Playgroup", "Nursery", "1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th", "10th", "11th", "12th")
        val sectionOptions = listOf("A", "B", "C", "D")
        var className by remember { mutableStateOf("8th") }
        var sectionName by remember { mutableStateOf("A") }
        var description by remember { mutableStateOf("") }
        var selectedTeacherId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateClassDialog = false },
            title = { Text("Create Class & Section (${state.activeAcademicSession!!.name})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Class Name", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        gradeOptions.forEach { g ->
                            FilterChip(selected = className == g, onClick = { className = g }, label = { Text(g) })
                        }
                    }
                    OutlinedTextField(value = className, onValueChange = { className = it }, label = { Text("Or Custom Class Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Section", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sectionOptions.forEach { s ->
                            FilterChip(selected = sectionName == s, onClick = { sectionName = s }, label = { Text(s) })
                        }
                    }
                    OutlinedTextField(value = sectionName, onValueChange = { sectionName = it }, label = { Text("Section Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (state.teachers.isNotEmpty()) {
                        Text("Class Teacher (Optional)", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.teachers.forEach { t ->
                                FilterChip(
                                    selected = selectedTeacherId == t.id,
                                    onClick = { selectedTeacherId = if (selectedTeacherId == t.id) null else t.id },
                                    label = { Text(t.fullName) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createClassAndSection(
                        state.activeAcademicSession!!.id, className, sectionName, selectedTeacherId, description
                    )
                    showCreateClassDialog = false
                }) { Text("Create Class") }
            },
            dismissButton = { TextButton(onClick = { showCreateClassDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassDashboardScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    schoolClass: SchoolClass?,
    section: ClassSection,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val isStaff = isPrincipal || state.role == UserRole.TEACHER
    var activeTab by remember { mutableIntStateOf(0) }
    val sessionName = state.sessions.find { it.id == section.sessionId }?.name.orEmpty()
    val classTeacherName = state.teachers.find { it.id == section.classTeacherId }?.fullName ?: "Not Assigned"
    val sectionStudents = remember(state.students, section.id) {
        AcademicCalculators.sortStudentsByRoll(
            state.students.filter { it.sectionId == section.id && it.status == "active" }
        )
    }
    val sectionFeesCount = remember(state.classFees, section.id) {
        state.classFees.count { it.sectionId == section.id }
    }
    var showClassPostDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Overview", "Students", "Attendance", "Fees", "Tests & Results", "Announcements", "Timetable & HW", "Class Reports", "Settings")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 920.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Classes")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${schoolClass?.name ?: "Class"} — Section ${section.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary
                    )
                    Text(
                        text = "$sessionName • Class Teacher: $classTeacherName • ${sectionStudents.size} Students",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ScrollableTabRow(selectedTabIndex = activeTab, edgePadding = 4.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = activeTab == index, onClick = { activeTab = index }, text = { Text(title) })
                }
            }

            Spacer(Modifier.height(12.dp))

            when (activeTab) {
                0 -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = 3
                        ) {
                            ShortcutTile("Students", Icons.Rounded.Groups, sectionStudents.size, "class_opt_students", { activeTab = 1 }, Modifier.weight(1f))
                            ShortcutTile("Attendance", Icons.Rounded.EventAvailable, 0, "class_opt_attendance", { activeTab = 2 }, Modifier.weight(1f))
                            ShortcutTile("Fees", Icons.Rounded.Payments, sectionFeesCount, "class_opt_fees", { activeTab = 3 }, Modifier.weight(1f))
                            ShortcutTile("Tests & Results", Icons.Rounded.Assignment, 0, "class_opt_tests", { activeTab = 4 }, Modifier.weight(1f))
                            ShortcutTile("Announcements", Icons.Rounded.Campaign, 0, "class_opt_announcements", { activeTab = 5 }, Modifier.weight(1f))
                            ShortcutTile("Timetable", Icons.Rounded.Schedule, 0, "class_opt_timetable", { activeTab = 6 }, Modifier.weight(1f))
                            ShortcutTile("Class Reports", Icons.Rounded.Assessment, 0, "class_opt_reports", { activeTab = 7 }, Modifier.weight(1f))
                            ShortcutTile("Class Settings", Icons.Rounded.Settings, 0, "class_opt_settings", { activeTab = 8 }, Modifier.weight(1f))
                        }
                    }
                }
                1 -> StudentsManagementScreen(state = state, viewModel = viewModel, fixedSection = section)
                2 -> AttendanceWorkspacePanel(state = state, viewModel = viewModel, fixedSection = section)
                3 -> ClassFeesPanel(state = state, viewModel = viewModel, schoolClass = schoolClass, section = section)
                4 -> TestsAndResultsPanel(state = state, viewModel = viewModel, fixedSection = section)
                5 -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (isPrincipal || state.currentTeacherPermissions.publishAnnouncements) {
                            Button(onClick = { showClassPostDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Publish Class Announcement")
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        val classPosts = state.announcements.filter { it.sectionId == section.id }
                        if (classPosts.isEmpty()) {
                            EmptyStateCard(Icons.Rounded.Campaign, "No Class Announcements", "Post homework notices, test dates, or instructions for this section.")
                        } else {
                            classPosts.forEach { post ->
                                AnnouncementPostCard(
                                    post = post,
                                    comments = state.commentsByAnnouncement[post.id].orEmpty(),
                                    canDelete = isPrincipal || post.publisherId == state.currentUser?.id,
                                    onLike = { viewModel.toggleAnnouncementLike(post.id) },
                                    onLoadComments = { viewModel.loadComments(post.id) },
                                    onAddComment = { viewModel.addComment(post.id, it) },
                                    onDelete = { viewModel.deleteAnnouncement(post.id) }
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
                6 -> TimetableAndHomeworkPanel(state = state, viewModel = viewModel, fixedSection = section)
                7 -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        val sectionSessions = state.attendanceSessions.filter { it.sectionId == section.id }
                        SectionCard(title = "Class Reports (${schoolClass?.name} - ${section.name})") {
                            Text("Total Students: ${sectionStudents.size}")
                            Text("Attendance Sessions Recorded: ${sectionSessions.size}")
                            Spacer(Modifier.height(12.dp))
                            val headers = listOf("Roll No", "Student Name", "Daily %", "Present Days", "Period %")
                            val rows = sectionStudents.map { stu ->
                                val sum = AcademicCalculators.computeStudentAttendanceSummary(stu.id, sectionSessions, state.attendanceRecords)
                                listOf(
                                    stu.rollNumber,
                                    stu.fullName,
                                    String.format("%.1f%%", sum.dailyStats.percentage),
                                    "${sum.dailyStats.presentCount}/${sum.dailyStats.totalCount}",
                                    String.format("%.1f%%", sum.periodStats.percentage)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    viewModel.exportPdfAndShare(
                                        context,
                                        "Class Attendance Report",
                                        "${schoolClass?.name} Section ${section.name} ($sessionName)",
                                        headers,
                                        rows
                                    )
                                }) { Text("Download PDF") }
                                OutlinedButton(onClick = {
                                    viewModel.exportCsvAndShare(context, "class_attendance_${section.name}", headers, rows)
                                }) { Text("Export Excel/CSV") }
                            }
                        }
                    }
                }
                8 -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        var secName by remember(section.id) { mutableStateOf(section.name) }
                        var secDesc by remember(section.id) { mutableStateOf(section.description) }
                        var teacherId by remember(section.id) { mutableStateOf(section.classTeacherId) }

                        SectionCard(title = "Class Settings") {
                            OutlinedTextField(value = secName, onValueChange = { secName = it }, label = { Text("Section Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = secDesc, onValueChange = { secDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                            if (isStaff && state.teachers.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Class Teacher", style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.teachers.forEach { t ->
                                        FilterChip(
                                            selected = teacherId == t.id,
                                            onClick = { teacherId = if (teacherId == t.id) null else t.id },
                                            label = { Text(t.fullName) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (isStaff) {
                                Button(onClick = { viewModel.updateSectionSettings(section.id, secName, secDesc, teacherId) }) {
                                    Text("Save Class Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClassPostDialog) {
        CreateAnnouncementDialog(
            sections = listOf(section),
            canChooseScope = isStaff,
            defaultSection = section,
            onDismiss = { showClassPostDialog = false },
            onPublish = { scope, sec, cat, title, body, uri, allowComments ->
                viewModel.publishAnnouncement(scope, sec, cat, title, body, uri, allowComments)
                showClassPostDialog = false
            }
        )
    }
}
