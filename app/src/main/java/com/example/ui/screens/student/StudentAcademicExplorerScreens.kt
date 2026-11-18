package com.example.ui.screens.student

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceSession
import com.example.data.model.ClassSection
import com.example.data.model.HomeworkItem
import com.example.data.model.StudentRecord
import com.example.data.model.TestExam
import com.example.data.model.TimetableSlot
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.screens.principal.AnnouncementPostCard
import com.example.ui.screens.shared.ClassFeesPanel
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel
import java.time.LocalDate
import java.util.Locale

private val WEEKDAY_ORDER = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentResultsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    var selectedSectionId by remember(state.sessionSections) { mutableStateOf<String?>(null) }
    var selectedSubject by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Test Sheets, 1 = Student Scoreboard
    var activeTest by remember { mutableStateOf<TestExam?>(null) }
    var detailStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val availableSubjects = remember(state.tests, state.activeAcademicSession) {
        val sid = state.activeAcademicSession?.id
        state.tests.filter { sid == null || it.sessionId == sid }
            .map { it.subject.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val visibleTests = remember(
        state.tests, state.activeAcademicSession, selectedSectionId, selectedSubject, searchQuery, state.testResults, state.students
    ) {
        val sid = state.activeAcademicSession?.id
        val q = searchQuery.trim()
        state.tests.filter { test ->
            val sessionOk = sid == null || test.sessionId == sid
            val sectionOk = selectedSectionId == null || test.sectionId == selectedSectionId
            val subjectOk = selectedSubject == null || test.subject.equals(selectedSubject, ignoreCase = true)
            val queryOk = if (q.isBlank()) true else {
                test.name.contains(q, ignoreCase = true) ||
                    test.subject.contains(q, ignoreCase = true) ||
                    state.testResults.any { r ->
                        if (r.testId != test.id) false else {
                            val stu = state.students.find { it.id == r.studentId }
                            stu != null && (stu.fullName.contains(q, ignoreCase = true) || stu.rollNumber.contains(q, ignoreCase = true))
                        }
                    }
            }
            sessionOk && sectionOk && subjectOk && queryOk
        }
    }

    val visibleTestIds = remember(visibleTests) { visibleTests.map { it.id }.toSet() }
    val matchingResults = remember(state.testResults, visibleTestIds) {
        state.testResults.filter { it.testId in visibleTestIds }
    }
    val overallPassPct = if (matchingResults.isEmpty()) 0.0 else (matchingResults.count { it.passed } * 100.0) / matchingResults.size
    val overallAvgPct = if (matchingResults.isEmpty()) 0.0 else matchingResults.map { it.percentage }.average()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search test, subject, student name, or roll #") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_results_search")
            )
            Spacer(Modifier.height(8.dp))

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

            if (availableSubjects.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSubject == null,
                        onClick = { selectedSubject = null },
                        label = { Text("All Subjects") }
                    )
                    availableSubjects.forEach { subj ->
                        FilterChip(
                            selected = selectedSubject == subj,
                            onClick = { selectedSubject = subj },
                            label = { Text(subj) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                StatMetricCard(
                    title = "Tests",
                    value = visibleTests.size.toString(),
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    accentColor = RoyalBlue,
                    subtitle = "${matchingResults.size} graded entries",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Pass Rate & Avg",
                    value = String.format(Locale.US, "%.1f%%", overallPassPct),
                    icon = Icons.Rounded.CheckCircle,
                    accentColor = PresentGreen,
                    subtitle = "Avg Score: ${String.format(Locale.US, "%.1f%%", overallAvgPct)}",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))

            SecondaryTabRow(selectedTabIndex = viewMode) {
                Tab(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    text = { Text("Test Sheets (${visibleTests.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_results_tab_tests")
                )
                Tab(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    text = { Text("Student Scoreboard", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("student_results_tab_students")
                )
            }
            Spacer(Modifier.height(10.dp))

            if (viewMode == 0) {
                if (visibleTests.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.Assignment,
                        title = "No Exam Results Yet",
                        description = "Tests and marks across all classes appear here once created by staff."
                    )
                } else {
                    visibleTests.forEach { test ->
                        val cls = state.classes.find { it.id == test.classId }?.name.orEmpty()
                        val sec = state.sections.find { it.id == test.sectionId }?.name.orEmpty()
                        val results = state.testResults.filter { it.testId == test.id }.sortedBy { it.rank ?: 999 }
                        val passedCount = results.count { it.passed }
                        val avg = if (results.isEmpty()) 0.0 else results.map { it.percentage }.average()
                        val topResult = results.firstOrNull()
                        val topStudent = topResult?.let { tr -> state.students.find { it.id == tr.studentId } }
                        val (statusLabel, statusFg, statusBg) = when (test.status) {
                            "published" -> Triple("PUBLISHED", PresentGreen, PresentGreenBg)
                            "submitted" -> Triple("GRADED", RoyalBlue, SkySoft)
                            else -> Triple("UPCOMING", WarningAmber, WarningAmberBg)
                        }

                        SectionCard(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .clickable { activeTest = test }
                                .testTag("student_test_card_${test.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.name, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                    Text(
                                        "$cls-$sec • ${test.subject} • ${AcademicCalculators.formatDisplayDate(test.testDate)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Total: ${test.totalMarks} • Passing: ${test.passingMarks} • Graded: ${results.size} • Passed: $passedCount • Avg: ${String.format(Locale.US, "%.1f%%", avg)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoyalBlue
                                    )
                                    if (topStudent != null && topResult != null) {
                                        Text(
                                            text = "Top: Roll #${topStudent.rollNumber} ${topStudent.fullName} (${topResult.obtainedMarks}/${test.totalMarks})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PresentGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                StatusPill(statusLabel, statusFg, statusBg)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { activeTest = test },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("View Full Marks")
                                }
                                if (results.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val headers = listOf("Rank", "Roll", "Student", "Marks", "%", "Grade", "Status")
                                            val rows = results.map { r ->
                                                val stu = state.students.find { it.id == r.studentId }
                                                listOf(
                                                    "#${r.rank ?: "-"}",
                                                    stu?.rollNumber.orEmpty(),
                                                    stu?.fullName.orEmpty(),
                                                    "${r.obtainedMarks}/${test.totalMarks}",
                                                    "${r.percentage}%",
                                                    r.grade,
                                                    if (r.passed) "PASS" else "FAIL"
                                                )
                                            }
                                            viewModel.exportPdfAndShare(
                                                context,
                                                "${test.name} - Result Sheet",
                                                "$cls-$sec • ${test.subject} • ${AcademicCalculators.formatDisplayDate(test.testDate)}",
                                                headers,
                                                rows
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val scoreboardStudents = remember(state.sessionStudents, state.classes, state.sections, selectedSectionId, searchQuery) {
                    val q = searchQuery.trim()
                    AcademicCalculators.sortStudentsByClassAndRoll(
                        state.sessionStudents.filter { stu ->
                            (selectedSectionId == null || stu.sectionId == selectedSectionId) &&
                                (q.isBlank() || stu.fullName.contains(q, true) || stu.rollNumber.contains(q, true))
                        },
                        state.classes,
                        state.sections
                    )
                }
                if (scoreboardStudents.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Groups,
                        title = "No Students Found",
                        description = "Clear the search or select All Classes to see every student's exam performance."
                    )
                } else {
                    scoreboardStudents.forEach { stu ->
                        val cls = state.classes.find { it.id == stu.classId }?.name.orEmpty()
                        val sec = state.sections.find { it.id == stu.sectionId }?.name.orEmpty()
                        val stuRes = matchingResults.filter { it.studentId == stu.id }
                        val avg = if (stuRes.isEmpty()) 0.0 else stuRes.map { it.percentage }.average()
                        val passed = stuRes.count { it.passed }
                        SectionCard(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable { detailStudent = stu }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Roll #${stu.rollNumber} • ${stu.fullName}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                    Text("$cls-$sec • Tests Taken: ${stuRes.size} • Passed: $passed", style = MaterialTheme.typography.bodySmall)
                                }
                                StatusPill(
                                    label = if (stuRes.isEmpty()) "No Marks" else String.format(Locale.US, "%.1f%% Avg", avg),
                                    textColor = if (stuRes.isEmpty() || avg >= 50.0) PresentGreen else AbsentRed,
                                    backgroundColor = if (stuRes.isEmpty() || avg >= 50.0) PresentGreenBg else AbsentRedBg
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeTest != null) {
        TestResultSheetDialog(
            test = activeTest!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                activeTest = null
                detailStudent = stu
            },
            onDismiss = { activeTest = null }
        )
    }

    if (detailStudent != null) {
        StudentReadOnlyDetailDialog(
            student = detailStudent!!,
            state = state,
            viewModel = viewModel,
            onDismiss = { detailStudent = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentMySchoolScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    initialTab: Int = 0
) {
    val school = state.school ?: return
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var selectedSection by remember { mutableStateOf<ClassSection?>(null) }
    var classDetailTab by remember(selectedSection?.id) { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var hwSearchQuery by remember { mutableStateOf("") }
    var hwSectionFilter by remember { mutableStateOf<String?>(null) }
    var detailStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var activeClassTest by remember { mutableStateOf<TestExam?>(null) }
    var activeAttSession by remember { mutableStateOf<AttendanceSession?>(null) }
    var activeHomework by remember { mutableStateOf<HomeworkItem?>(null) }
    var previewPhotoUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            SecondaryTabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Classes", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Timetable & HW", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("School & Staff", maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            Spacer(Modifier.height(12.dp))

            when (activeTab) {
                0 -> {
                    if (state.sessions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.sessions.forEach { sess ->
                                FilterChip(
                                    selected = state.activeAcademicSession?.id == sess.id,
                                    onClick = {
                                        selectedSection = null
                                        viewModel.selectAcademicSession(sess.id)
                                    },
                                    label = { Text(if (sess.isCurrent) "${sess.name} (Current)" else sess.name) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (selectedSection == null) {
                        if (state.sessionSections.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Class,
                                title = "No Classes Found",
                                description = "Classes and student rosters in ${state.activeAcademicSession?.name ?: "the school"} will appear here."
                            )
                        } else {
                            val byClass = state.sessionSections.groupBy { it.classId }
                            state.sessionClasses.forEach { cls ->
                                val secs = byClass[cls.id].orEmpty()
                                if (secs.isNotEmpty()) {
                                    Text(cls.name, style = MaterialTheme.typography.titleMedium, color = NavyPrimary, modifier = Modifier.padding(vertical = 4.dp))
                                    secs.forEach { sec ->
                                        val stuCount = state.sessionStudents.count { it.sectionId == sec.id }
                                        val attCount = state.attendanceSessions.count { it.sectionId == sec.id }
                                        val testCount = state.tests.count { it.sectionId == sec.id }
                                        val hwCount = state.homework.count { it.sectionId == sec.id }
                                        val teacherName = state.teachers.find { it.id == sec.classTeacherId || it.userId == sec.classTeacherId }?.fullName ?: "Staff Team"
                                        val subjectTeachers = state.teacherAssignments
                                            .filter { it.sectionId == sec.id }
                                            .mapNotNull { assign ->
                                                val tName = state.teachers.find { it.id == assign.teacherProfileId || it.userId == assign.teacherProfileId }?.fullName
                                                if (tName != null) "${assign.subject}: $tName" else null
                                            }
                                            .distinct()

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { selectedSection = sec }
                                                .testTag("student_class_card_${sec.id}"),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = SkySoft.copy(alpha = 0.45f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${cls.name} — Section ${sec.name}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                                    Text("$stuCount Students • Class Teacher: $teacherName", style = MaterialTheme.typography.bodySmall)
                                                    Text(
                                                        "Attendance: $attCount • Tests: $testCount • HW: $hwCount",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = RoyalBlue
                                                    )
                                                    if (subjectTeachers.isNotEmpty()) {
                                                        Text(
                                                            text = subjectTeachers.joinToString(" • "),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                StatusPill("Explore", RoyalBlue, SkySoft)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val sec = selectedSection!!
                        val cls = state.classes.find { it.id == sec.classId }
                        val sectionStudents = remember(state.students, sec.id, searchQuery) {
                            val q = searchQuery.trim()
                            AcademicCalculators.sortStudentsByRoll(
                                state.students.filter {
                                    it.sectionId == sec.id && it.status == "active" &&
                                        (q.isBlank() || it.fullName.contains(q, true) || it.rollNumber.contains(q, true))
                                }
                            )
                        }
                        val secAttendance = remember(state.attendanceSessions, sec.id) {
                            state.attendanceSessions.filter { it.sectionId == sec.id }
                        }
                        val secTests = remember(state.tests, sec.id) {
                            state.tests.filter { it.sectionId == sec.id }
                        }
                        val secHw = remember(state.homework, sec.id) {
                            state.homework.filter { it.sectionId == sec.id }
                        }
                        val secTimetable = remember(state.timetables, state.classes, state.sections, sec.id) {
                            AcademicCalculators.sortTimetableSlots(
                                state.timetables.filter { it.sectionId == sec.id },
                                state.classes,
                                state.sections
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${cls?.name.orEmpty()} — Section ${sec.name}", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
                            TextButton(onClick = { selectedSection = null }) { Text("All Classes") }
                        }
                        if (sec.description.isNotBlank()) {
                            Text(sec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))

                        SecondaryTabRow(selectedTabIndex = classDetailTab) {
                            Tab(selected = classDetailTab == 0, onClick = { classDetailTab = 0 }, text = { Text("Students", maxLines = 1) })
                            Tab(selected = classDetailTab == 1, onClick = { classDetailTab = 1 }, text = { Text("Attendance", maxLines = 1) })
                            Tab(selected = classDetailTab == 2, onClick = { classDetailTab = 2 }, text = { Text("Fees", maxLines = 1) })
                            Tab(selected = classDetailTab == 3, onClick = { classDetailTab = 3 }, text = { Text("Results", maxLines = 1) })
                            Tab(selected = classDetailTab == 4, onClick = { classDetailTab = 4 }, text = { Text("HW & Time", maxLines = 1) })
                        }
                        Spacer(Modifier.height(10.dp))

                        when (classDetailTab) {
                            0 -> {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search students by name or roll #") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                Text("Students (${sectionStudents.size}) — tap for complete Attendance & Results", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Spacer(Modifier.height(6.dp))
                                if (sectionStudents.isEmpty()) {
                                    EmptyStateCard(Icons.Rounded.Groups, "No Students Found", "No matching students in this class section.")
                                } else {
                                    sectionStudents.forEach { stu ->
                                        val sum = remember(stu.id, state.attendanceSessions, state.attendanceRecords) {
                                            AcademicCalculators.computeStudentAttendanceSummary(stu.id, state.attendanceSessions, state.attendanceRecords)
                                        }
                                        val testCount = state.testResults.count { it.studentId == stu.id }
                                        SectionCard(
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .clickable { detailStudent = stu }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Roll #${stu.rollNumber} • ${stu.fullName}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                                    Text("Father: ${stu.fatherName} • Tests: $testCount", style = MaterialTheme.typography.bodySmall)
                                                    Text(
                                                        "Present: ${sum.dailyStats.presentCount}  Absent: ${sum.dailyStats.absentCount}  Leave: ${sum.dailyStats.leaveCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = RoyalBlue
                                                    )
                                                }
                                                StatusPill(
                                                    label = String.format(Locale.US, "%.1f%%", sum.dailyStats.percentage),
                                                    textColor = if (sum.dailyStats.percentage >= 75.0 || sum.dailyStats.totalCount == 0) PresentGreen else AbsentRed,
                                                    backgroundColor = if (sum.dailyStats.percentage >= 75.0 || sum.dailyStats.totalCount == 0) PresentGreenBg else AbsentRedBg
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                if (secAttendance.isEmpty()) {
                                    EmptyStateCard(Icons.Rounded.Schedule, "No Attendance Recorded", "Attendance sheets marked for this class will appear here.")
                                } else {
                                    secAttendance.forEachIndexed { idx, attSess ->
                                        ExpandableAttendanceSessionCard(
                                            session = attSess,
                                            state = state,
                                            defaultExpanded = idx == 0,
                                            onOpenFullSheet = { activeAttSession = it },
                                            onStudentClick = { detailStudent = it }
                                        )
                                    }
                                }
                            }

                            2 -> {
                                ClassFeesPanel(
                                    state = state,
                                    viewModel = viewModel,
                                    schoolClass = cls,
                                    section = sec,
                                    scrollable = false
                                )
                            }

                            3 -> {
                                if (secTests.isEmpty()) {
                                    EmptyStateCard(Icons.AutoMirrored.Rounded.Assignment, "No Tests Found", "Tests and marks for this class will appear here.")
                                } else {
                                    secTests.forEach { t ->
                                        val resList = state.testResults.filter { it.testId == t.id }
                                        val passed = resList.count { it.passed }
                                        SectionCard(
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .clickable { activeClassTest = t }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${t.name} (${t.subject})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                                    Text(
                                                        "${AcademicCalculators.formatDisplayDate(t.testDate)} • Total ${t.totalMarks} • Graded: ${resList.size} • Passed: $passed",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                StatusPill(
                                                    label = when (t.status) {
                                                        "published" -> "PUBLISHED"
                                                        "submitted" -> "GRADED"
                                                        else -> "UPCOMING"
                                                    },
                                                    textColor = when (t.status) {
                                                        "published" -> PresentGreen
                                                        "submitted" -> RoyalBlue
                                                        else -> WarningAmber
                                                    },
                                                    backgroundColor = when (t.status) {
                                                        "published" -> PresentGreenBg
                                                        "submitted" -> SkySoft
                                                        else -> WarningAmberBg
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                Text("Homework (${secHw.size})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Spacer(Modifier.height(6.dp))
                                if (secHw.isEmpty()) {
                                    EmptyStateCard(Icons.Rounded.MenuBook, "No Homework Assigned", "Homework assigned for this class will appear here.")
                                } else {
                                    secHw.forEach { hw ->
                                        HomeworkSummaryCard(
                                            hw = hw,
                                            className = cls?.name.orEmpty(),
                                            sectionName = sec.name,
                                            onClick = { activeHomework = hw }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("Class Timetable (${secTimetable.size})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Spacer(Modifier.height(6.dp))
                                if (secTimetable.isEmpty()) {
                                    EmptyStateCard(Icons.Rounded.Schedule, "No Timetable Slots", "Class timetable periods will appear here.")
                                } else {
                                    TimetableGroupedView(slots = secTimetable, state = state, showClassLabel = false)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    OutlinedTextField(
                        value = hwSearchQuery,
                        onValueChange = { hwSearchQuery = it },
                        label = { Text("Search homework or timetable by subject, title, or teacher") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_hw_search")
                    )
                    Spacer(Modifier.height(8.dp))

                    if (state.sessionSections.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(selected = hwSectionFilter == null, onClick = { hwSectionFilter = null }, label = { Text("All Classes") })
                            state.sessionSections.forEach { sec ->
                                val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                                FilterChip(selected = hwSectionFilter == sec.id, onClick = { hwSectionFilter = sec.id }, label = { Text("$cls ${sec.name}") })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    val hq = hwSearchQuery.trim()
                    val hwList = remember(state.homework, hwSectionFilter, hq) {
                        state.homework.filter { hw ->
                            (hwSectionFilter == null || hw.sectionId == hwSectionFilter) &&
                                (hq.isBlank() || hw.subject.contains(hq, true) || hw.title.contains(hq, true) ||
                                    hw.instructions.contains(hq, true) || hw.teacherName.contains(hq, true))
                        }
                    }
                    Text("Homework (${hwList.size} — tap any card for full details)", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    Spacer(Modifier.height(6.dp))
                    if (hwList.isEmpty()) {
                        EmptyStateCard(Icons.Rounded.MenuBook, "No Homework Posted", "Assigned class homework and due dates will appear here.")
                    } else {
                        hwList.forEach { hw ->
                            val sec = state.sections.find { it.id == hw.sectionId }
                            val cls = state.classes.find { it.id == sec?.classId }
                            HomeworkSummaryCard(
                                hw = hw,
                                className = cls?.name.orEmpty(),
                                sectionName = sec?.name.orEmpty(),
                                onClick = { activeHomework = hw }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    val ttList = remember(state.timetables, state.classes, state.sections, hwSectionFilter, hq) {
                        AcademicCalculators.sortTimetableSlots(
                            state.timetables.filter { slot ->
                                (hwSectionFilter == null || slot.sectionId == hwSectionFilter) &&
                                    (hq.isBlank() || slot.subject.contains(hq, true) || slot.teacherName.contains(hq, true) || slot.dayOfWeek.contains(hq, true))
                            },
                            state.classes,
                            state.sections
                        )
                    }
                    Text("Class Timetables (${ttList.size})", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    Spacer(Modifier.height(6.dp))
                    if (ttList.isEmpty()) {
                        EmptyStateCard(Icons.Rounded.Schedule, "No Timetable Slots", "Daily lecture schedules will appear here.")
                    } else {
                        TimetableGroupedView(slots = ttList, state = state, showClassLabel = hwSectionFilter == null)
                    }
                }

                2 -> {
                    SectionCard(title = school.name) {
                        Text("${school.schoolType} • ${school.city} • ID: ${school.schoolCode}", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
                        Spacer(Modifier.height(6.dp))
                        Text(school.description.ifBlank { "Welcome to ${school.name}" }, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        if (school.establishedYear != null) {
                            Text("Established: ${school.establishedYear}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Address: ${school.address}, ${school.city}", style = MaterialTheme.typography.bodySmall)
                        Text("Contact: ${school.contactNumber} • ${school.email}", style = MaterialTheme.typography.bodySmall)
                        if (!school.website.isNullOrBlank()) {
                            Text("Website: ${school.website}", style = MaterialTheme.typography.bodySmall, color = RoyalBlue)
                        }
                        if (school.galleryUrls.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(school.galleryUrls) { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "School gallery photo",
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { previewPhotoUrl = url },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SectionCard(title = "Teachers & Subject Assignments (${state.teachers.size})") {
                        if (state.teachers.isEmpty()) {
                            Text("No teachers listed yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.teachers.forEachIndexed { idx, t ->
                                val assignments = state.teacherAssignments
                                    .filter { it.teacherProfileId == t.id || it.teacherProfileId == t.userId }
                                    .map { assign ->
                                        val sec = state.sections.find { it.id == assign.sectionId }
                                        val cls = state.classes.find { it.id == sec?.classId }
                                        "${assign.subject} (${cls?.name.orEmpty()}-${sec?.name.orEmpty()})"
                                    }
                                    .distinct()
                                val classTeacherOf = state.sections
                                    .filter { it.classTeacherId == t.id || it.classTeacherId == t.userId }
                                    .map { sec ->
                                        val cls = state.classes.find { it.id == sec.classId }
                                        "${cls?.name.orEmpty()}-${sec.name}"
                                    }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(CircleShape).background(SkySoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Badge, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(t.fullName, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                            StatusPill(
                                                label = if (t.isActive) "Active" else "Inactive",
                                                textColor = if (t.isActive) PresentGreen else AbsentRed,
                                                backgroundColor = if (t.isActive) PresentGreenBg else AbsentRedBg
                                            )
                                        }
                                        Text("${t.email} • Joined ${AcademicCalculators.formatDisplayDate(t.joiningDate)}", style = MaterialTheme.typography.bodySmall)
                                        if (t.phone.isNotBlank()) {
                                            Text("Phone: ${t.phone}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (classTeacherOf.isNotEmpty()) {
                                            Text("Class Teacher: ${classTeacherOf.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                                        }
                                        if (assignments.isNotEmpty()) {
                                            Text("Subjects: ${assignments.joinToString(" • ")}", style = MaterialTheme.typography.labelSmall, color = NavyPrimary)
                                        }
                                    }
                                }
                                if (idx < state.teachers.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("School Announcements (${state.announcements.size})", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    Spacer(Modifier.height(8.dp))
                    if (state.announcements.isEmpty()) {
                        EmptyStateCard(Icons.Rounded.Campaign, "No School Posts Yet", "Official notices and school updates will appear here.")
                    } else {
                        state.announcements.forEach { post ->
                            AnnouncementPostCard(
                                post = post,
                                comments = state.commentsByAnnouncement[post.id].orEmpty(),
                                canDelete = false,
                                onLike = { viewModel.toggleAnnouncementLike(post.id) },
                                onLoadComments = { viewModel.loadComments(post.id) },
                                onAddComment = { viewModel.addComment(post.id, it) },
                                onDelete = {}
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
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

    if (activeClassTest != null) {
        TestResultSheetDialog(
            test = activeClassTest!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                activeClassTest = null
                detailStudent = stu
            },
            onDismiss = { activeClassTest = null }
        )
    }

    if (activeAttSession != null) {
        AttendanceSessionSheetDialog(
            session = activeAttSession!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                activeAttSession = null
                detailStudent = stu
            },
            onDismiss = { activeAttSession = null }
        )
    }

    if (activeHomework != null) {
        val hw = activeHomework!!
        val sec = state.sections.find { it.id == hw.sectionId }
        val cls = state.classes.find { it.id == sec?.classId }
        HomeworkReadOnlyDetailDialog(
            homework = hw,
            className = cls?.name.orEmpty(),
            sectionName = sec?.name.orEmpty(),
            onDismiss = { activeHomework = null }
        )
    }

    if (previewPhotoUrl != null) {
        AlertDialog(
            onDismissRequest = { previewPhotoUrl = null },
            title = { Text("School Gallery") },
            text = {
                AsyncImage(
                    model = previewPhotoUrl,
                    contentDescription = "School gallery photo",
                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = { TextButton(onClick = { previewPhotoUrl = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun HomeworkSummaryCard(
    hw: HomeworkItem,
    className: String,
    sectionName: String,
    onClick: () -> Unit
) {
    val dueParsed = AcademicCalculators.parseFlexibleDate(hw.dueDate)
    val today = LocalDate.now()
    val (dueLabel, dueFg, dueBg) = when {
        dueParsed == null -> Triple("DUE", RoyalBlue, SkySoft)
        dueParsed.isBefore(today) -> Triple("OVERDUE", AbsentRed, AbsentRedBg)
        dueParsed.isEqual(today) -> Triple("DUE TODAY", WarningAmber, WarningAmberBg)
        else -> Triple("ACTIVE", PresentGreen, PresentGreenBg)
    }

    SectionCard(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill("${hw.subject} • $className-$sectionName", RoyalBlue, SkySoft)
            StatusPill(dueLabel, dueFg, dueBg)
        }
        Spacer(Modifier.height(6.dp))
        Text(hw.title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = hw.instructions,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "By ${hw.teacherName.ifBlank { "Teacher" }} • Due: ${AcademicCalculators.formatDisplayDate(hw.dueDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hw.attachmentUrl.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Has Attachment", tint = RoyalBlue, modifier = Modifier.size(15.dp))
                    Text("Attachment", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                }
            }
        }
    }
}

@Composable
private fun TimetableGroupedView(
    slots: List<TimetableSlot>,
    state: HubUiState,
    showClassLabel: Boolean
) {
    val grouped = slots.groupBy { it.dayOfWeek }
    val orderedDays = grouped.keys.sortedBy { day ->
        val idx = WEEKDAY_ORDER.indexOfFirst { it.equals(day, ignoreCase = true) }
        if (idx >= 0) idx else Int.MAX_VALUE
    }
    orderedDays.forEach { day ->
        val daySlots = grouped[day].orEmpty()
        SectionCard(title = day, modifier = Modifier.padding(vertical = 4.dp)) {
            daySlots.forEachIndexed { idx, slot ->
                val sec = state.sections.find { it.id == slot.sectionId }
                val cls = state.classes.find { it.id == sec?.classId }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val titlePrefix = if (showClassLabel) "${cls?.name.orEmpty()}-${sec?.name.orEmpty()} • " else ""
                        Text("$titlePrefix${slot.subject}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                        Text(
                            text = "Teacher: ${slot.teacherName.ifBlank { "Staff" }} • Room ${slot.roomNumber.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill("${slot.startTime} - ${slot.endTime}", RoyalBlue, SkySoft)
                }
                if (idx < daySlots.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
