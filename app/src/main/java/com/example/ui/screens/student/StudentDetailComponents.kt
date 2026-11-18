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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.HomeworkItem
import com.example.data.model.StudentRecord
import com.example.data.model.TestExam
import com.example.ui.components.AttendanceDonutChart
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveOrange
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

private data class SessionRecordItem(
    val record: AttendanceRecord,
    val student: StudentRecord?,
    val rollNumInt: Int,
    val rollDisplay: String,
    val studentName: String,
    val fatherName: String
)

private fun buildSessionRecordItems(
    sessionId: String,
    state: HubUiState
): List<SessionRecordItem> {
    val studentById = state.students.associateBy { it.id }
    return state.attendanceRecords
        .filter { it.attendanceSessionId == sessionId }
        .map { rec ->
            val stu = studentById[rec.studentId]
            SessionRecordItem(
                record = rec,
                student = stu,
                rollNumInt = stu?.rollNumber?.trim()?.toIntOrNull() ?: Int.MAX_VALUE,
                rollDisplay = stu?.rollNumber.orEmpty().ifBlank { "-" },
                studentName = stu?.fullName.orEmpty().ifBlank { "Student" },
                fatherName = stu?.fatherName.orEmpty()
            )
        }
        .sortedWith(compareBy({ it.rollNumInt }, { it.rollDisplay }, { it.studentName.lowercase(Locale.US) }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableAttendanceSessionCard(
    session: AttendanceSession,
    state: HubUiState,
    defaultExpanded: Boolean = false,
    onOpenFullSheet: (AttendanceSession) -> Unit,
    onStudentClick: (StudentRecord) -> Unit
) {
    var expanded by remember(session.id, defaultExpanded) { mutableStateOf(defaultExpanded) }
    var statusFilter by remember(session.id) { mutableStateOf("all") }

    val cls = state.classes.find { it.id == session.classId }?.name.orEmpty()
    val sec = state.sections.find { it.id == session.sectionId }?.name.orEmpty()
    val items = remember(session.id, state.attendanceRecords, state.students) {
        buildSessionRecordItems(session.id, state)
    }
    val pCount = items.count { it.record.status == AttendanceStatus.PRESENT.dbValue }
    val aCount = items.count { it.record.status == AttendanceStatus.ABSENT.dbValue }
    val lCount = items.count { it.record.status == AttendanceStatus.LEAVE.dbValue }

    val filteredItems = remember(items, statusFilter) {
        if (statusFilter == "all") items
        else items.filter { it.record.status == statusFilter }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("student_att_session_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .minimumInteractiveComponentSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$cls-$sec • ${session.subject} (${session.sessionSlot})",
                        style = MaterialTheme.typography.titleSmall,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${AcademicCalculators.formatDisplayDate(session.attendanceDate)} • Teacher: ${session.teacherName.ifBlank { "Staff" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse attendance roll list" else "Expand attendance roll list",
                    tint = NavyPrimary
                )
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill("Present: $pCount", PresentGreen, PresentGreenBg)
                StatusPill("Absent: $aCount", AbsentRed, AbsentRedBg)
                if (lCount > 0) {
                    StatusPill("Leave: $lCount", WarningAmber, WarningAmberBg)
                }
                StatusPill("Total: ${items.size}", NavyPrimary, SkySoft)
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == "all",
                        onClick = { statusFilter = "all" },
                        label = { Text("All (${items.size})") }
                    )
                    FilterChip(
                        selected = statusFilter == AttendanceStatus.PRESENT.dbValue,
                        onClick = { statusFilter = AttendanceStatus.PRESENT.dbValue },
                        label = { Text("Present ($pCount)") }
                    )
                    FilterChip(
                        selected = statusFilter == AttendanceStatus.ABSENT.dbValue,
                        onClick = { statusFilter = AttendanceStatus.ABSENT.dbValue },
                        label = { Text("Absent ($aCount)") }
                    )
                    FilterChip(
                        selected = statusFilter == AttendanceStatus.LEAVE.dbValue,
                        onClick = { statusFilter = AttendanceStatus.LEAVE.dbValue },
                        label = { Text("Leave ($lCount)") }
                    )
                }

                Spacer(Modifier.height(6.dp))
                if (filteredItems.isEmpty()) {
                    Text(
                        text = "No students with this status in this session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    filteredItems.forEach { item ->
                        val (fg, bg) = when (item.record.status) {
                            AttendanceStatus.PRESENT.dbValue -> PresentGreen to PresentGreenBg
                            AttendanceStatus.ABSENT.dbValue -> AbsentRed to AbsentRedBg
                            else -> WarningAmber to WarningAmberBg
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = item.student != null) {
                                    item.student?.let(onStudentClick)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SkySoft
                                ) {
                                    Text(
                                        text = "#${item.rollDisplay}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NavyPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = item.studentName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NavyPrimary
                                    )
                                    val sub = listOfNotNull(
                                        item.fatherName.takeIf { it.isNotBlank() }?.let { "S/O $it" },
                                        item.record.note.takeIf { it.isNotBlank() }?.let { "Note: $it" }
                                    ).joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            StatusPill(item.record.status.uppercase(), fg, bg)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onOpenFullSheet(session) },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Full Sheet & Download PDF")
                }
            }
        }
    }
}

@Composable
fun AttendanceSessionSheetDialog(
    session: AttendanceSession,
    state: HubUiState,
    viewModel: HubViewModel,
    onStudentClick: (StudentRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sec = state.sections.find { it.id == session.sectionId }
    val cls = state.classes.find { it.id == session.classId }
    var statusFilter by remember(session.id) { mutableStateOf("all") }
    var query by remember(session.id) { mutableStateOf("") }

    val allItems = remember(session.id, state.attendanceRecords, state.students) {
        buildSessionRecordItems(session.id, state)
    }
    val pCount = allItems.count { it.record.status == AttendanceStatus.PRESENT.dbValue }
    val aCount = allItems.count { it.record.status == AttendanceStatus.ABSENT.dbValue }
    val lCount = allItems.count { it.record.status == AttendanceStatus.LEAVE.dbValue }

    val visibleItems = remember(allItems, statusFilter, query) {
        val q = query.trim().lowercase(Locale.US)
        allItems.filter { item ->
            val stOk = statusFilter == "all" || item.record.status == statusFilter
            val qOk = q.isBlank() ||
                item.rollDisplay.lowercase(Locale.US).contains(q) ||
                item.studentName.lowercase(Locale.US).contains(q)
            stOk && qOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${cls?.name.orEmpty()}-${sec?.name.orEmpty()} Attendance Sheet") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${session.subject} (${session.sessionSlot}) • ${AcademicCalculators.formatDisplayDate(session.attendanceDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Marked by: ${session.teacherName.ifBlank { "Staff" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search roll # or student name") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = statusFilter == "all", onClick = { statusFilter = "all" }, label = { Text("All (${allItems.size})") })
                    FilterChip(selected = statusFilter == AttendanceStatus.PRESENT.dbValue, onClick = { statusFilter = AttendanceStatus.PRESENT.dbValue }, label = { Text("Present ($pCount)") })
                    FilterChip(selected = statusFilter == AttendanceStatus.ABSENT.dbValue, onClick = { statusFilter = AttendanceStatus.ABSENT.dbValue }, label = { Text("Absent ($aCount)") })
                    FilterChip(selected = statusFilter == AttendanceStatus.LEAVE.dbValue, onClick = { statusFilter = AttendanceStatus.LEAVE.dbValue }, label = { Text("Leave ($lCount)") })
                }
                Spacer(Modifier.height(8.dp))
                if (visibleItems.isEmpty()) {
                    Text("No matching attendance records found.", style = MaterialTheme.typography.bodySmall)
                } else {
                    visibleItems.forEach { item ->
                        val (fg, bg) = when (item.record.status) {
                            AttendanceStatus.PRESENT.dbValue -> PresentGreen to PresentGreenBg
                            AttendanceStatus.ABSENT.dbValue -> AbsentRed to AbsentRedBg
                            else -> WarningAmber to WarningAmberBg
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.student != null) {
                                    item.student?.let(onStudentClick)
                                }
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Roll #${item.rollDisplay} • ${item.studentName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NavyPrimary
                                )
                                if (item.record.note.isNotBlank()) {
                                    Text("Note: ${item.record.note}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusPill(item.record.status.uppercase(), fg, bg)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Roll #", "Student Name", "Father Name", "Status", "Note")
                            val rows = visibleItems.map { item ->
                                listOf(
                                    item.rollDisplay,
                                    item.studentName,
                                    item.fatherName,
                                    item.record.status.uppercase(),
                                    item.record.note
                                )
                            }
                            viewModel.exportPdfAndShare(
                                context,
                                "Attendance Sheet - ${cls?.name.orEmpty()}-${sec?.name.orEmpty()}",
                                "${session.subject} (${session.sessionSlot}) • ${AcademicCalculators.formatDisplayDate(session.attendanceDate)}",
                                headers,
                                rows
                            )
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Download Sheet PDF")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun TestResultSheetDialog(
    test: TestExam,
    state: HubUiState,
    viewModel: HubViewModel,
    onStudentClick: (StudentRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cls = state.classes.find { it.id == test.classId }?.name.orEmpty()
    val sec = state.sections.find { it.id == test.sectionId }?.name.orEmpty()
    var passFilter by remember(test.id) { mutableStateOf("all") }
    var query by remember(test.id) { mutableStateOf("") }

    val studentById = remember(state.students) { state.students.associateBy { it.id } }
    val allResults = remember(state.testResults, test.id) {
        state.testResults
            .filter { it.testId == test.id }
            .sortedWith(
                compareBy(
                    { it.rank ?: Int.MAX_VALUE },
                    { studentById[it.studentId]?.rollNumber?.trim()?.toIntOrNull() ?: Int.MAX_VALUE }
                )
            )
    }

    val passedCount = allResults.count { it.passed }
    val failedCount = allResults.size - passedCount
    val avgPct = if (allResults.isEmpty()) 0.0 else allResults.map { it.percentage }.average()
    val highestMarks = allResults.maxOfOrNull { it.obtainedMarks } ?: 0.0

    val visibleResults = remember(allResults, passFilter, query, studentById) {
        val q = query.trim().lowercase(Locale.US)
        allResults.filter { r ->
            val stu = studentById[r.studentId]
            val pOk = when (passFilter) {
                "pass" -> r.passed
                "fail" -> !r.passed
                else -> true
            }
            val qOk = q.isBlank() ||
                (stu?.fullName?.lowercase(Locale.US)?.contains(q) == true) ||
                (stu?.rollNumber?.lowercase(Locale.US)?.contains(q) == true)
            pOk && qOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${test.name} ($cls-$sec)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${test.subject} • ${AcademicCalculators.formatDisplayDate(test.testDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Total: ${test.totalMarks} • Passing: ${test.passingMarks} • Highest: $highestMarks • Avg: ${String.format(Locale.US, "%.1f%%", avgPct)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (test.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(test.description, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search roll # or student name") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = passFilter == "all", onClick = { passFilter = "all" }, label = { Text("All (${allResults.size})") })
                    FilterChip(selected = passFilter == "pass", onClick = { passFilter = "pass" }, label = { Text("Passed ($passedCount)") })
                    FilterChip(selected = passFilter == "fail", onClick = { passFilter = "fail" }, label = { Text("Failed ($failedCount)") })
                }
                Spacer(Modifier.height(8.dp))
                if (visibleResults.isEmpty()) {
                    Text("No student marks match this filter.", style = MaterialTheme.typography.bodySmall)
                } else {
                    visibleResults.forEach { r ->
                        val stu = studentById[r.studentId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = stu != null) { stu?.let(onStudentClick) }
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${r.rank ?: "-"} • Roll #${stu?.rollNumber ?: "-"} ${stu?.fullName ?: "Student"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "${r.obtainedMarks}/${test.totalMarks} • ${r.percentage}% • Grade ${r.grade}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            StatusPill(
                                label = if (r.passed) "PASS" else "FAIL",
                                textColor = if (r.passed) PresentGreen else AbsentRed,
                                backgroundColor = if (r.passed) PresentGreenBg else AbsentRedBg
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Rank", "Roll #", "Student", "Marks", "%", "Grade", "Status")
                            val rows = visibleResults.map { r ->
                                val stu = studentById[r.studentId]
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
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Download Result Sheet PDF")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentReadOnlyDetailDialog(
    student: StudentRecord,
    state: HubUiState,
    viewModel: HubViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cls = state.classes.find { it.id == student.classId }?.name.orEmpty()
    val sec = state.sections.find { it.id == student.sectionId }?.name.orEmpty()
    val summary = remember(student.id, state.attendanceSessions, state.attendanceRecords) {
        AcademicCalculators.computeStudentAttendanceSummary(student.id, state.attendanceSessions, state.attendanceRecords)
    }
    val sessionById = remember(state.attendanceSessions) { state.attendanceSessions.associateBy { it.id } }
    val studentAttendanceLog = remember(student.id, state.attendanceRecords, sessionById) {
        state.attendanceRecords
            .filter { it.studentId == student.id }
            .mapNotNull { rec ->
                val sess = sessionById[rec.attendanceSessionId] ?: return@mapNotNull null
                rec to sess
            }
            .sortedWith { a, b ->
                val d1 = AcademicCalculators.parseFlexibleDate(a.second.attendanceDate)?.toEpochDay() ?: 0L
                val d2 = AcademicCalculators.parseFlexibleDate(b.second.attendanceDate)?.toEpochDay() ?: 0L
                if (d1 != d2) d2.compareTo(d1)
                else AcademicCalculators.compareLectureSlots(a.second.sessionSlot, b.second.sessionSlot)
            }
    }
    val studentResults = remember(student.id, state.testResults, state.tests) {
        state.testResults
            .filter { it.studentId == student.id }
            .sortedByDescending { res ->
                val t = state.tests.find { it.id == res.testId }
                AcademicCalculators.parseFlexibleDate(t?.testDate)?.toEpochDay() ?: 0L
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${student.fullName} (Roll #${student.rollNumber})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    color = SkySoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Class: $cls-$sec • ID: ${student.studentCode}", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                        Text("Father: ${student.fatherName} • Gender: ${student.gender}", style = MaterialTheme.typography.bodySmall)
                        if (student.parentContact.isNotBlank()) {
                            Text("Guardian Contact: ${student.parentContact}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Admission Date: ${AcademicCalculators.formatDisplayDate(student.admissionDate)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    StatMetricCard("Present", summary.dailyStats.presentCount.toString(), Icons.Rounded.CheckCircle, PresentGreen, modifier = Modifier.weight(1f))
                    StatMetricCard("Absent", summary.dailyStats.absentCount.toString(), Icons.Rounded.PersonOff, AbsentRed, modifier = Modifier.weight(1f))
                    StatMetricCard("Leave", summary.dailyStats.leaveCount.toString(), Icons.Rounded.Schedule, LeaveOrange, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))

                AttendanceDonutChart(summary.dailyStats, "Daily Attendance %")
                Spacer(Modifier.height(8.dp))
                AttendanceDonutChart(summary.periodStats, "Period / Lecture Attendance %")

                if (summary.subjectBreakdown.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Subject-wise Attendance", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    summary.subjectBreakdown.forEach { (subj, st) ->
                        Text(
                            text = "• $subj: ${st.presentCount}/${st.totalCount} (${String.format(Locale.US, "%.1f%%", st.percentage)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Date-by-Date Attendance Log (${studentAttendanceLog.size})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                if (studentAttendanceLog.isEmpty()) {
                    Text("No attendance records yet for this student.", style = MaterialTheme.typography.bodySmall)
                } else {
                    studentAttendanceLog.take(30).forEach { (rec, sess) ->
                        val (fg, bg) = when (rec.status) {
                            AttendanceStatus.PRESENT.dbValue -> PresentGreen to PresentGreenBg
                            AttendanceStatus.ABSENT.dbValue -> AbsentRed to AbsentRedBg
                            else -> WarningAmber to WarningAmberBg
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${AcademicCalculators.formatDisplayDate(sess.attendanceDate)} • ${sess.subject} (${sess.sessionSlot})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                val notePart = rec.note.takeIf { it.isNotBlank() }?.let { " • Note: $it" }.orEmpty()
                                Text(
                                    text = "By ${sess.teacherName.ifBlank { "Staff" }}$notePart",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(rec.status.uppercase(), fg, bg)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("All Test Results (${studentResults.size})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                if (studentResults.isEmpty()) {
                    Text("No recorded test results for this student yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    studentResults.forEach { res ->
                        val test = state.tests.find { it.id == res.testId }
                        val statusTag = when (test?.status) {
                            "published" -> "Published"
                            "submitted" -> "Graded"
                            else -> "Draft"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${test?.name ?: "Test"} (${test?.subject.orEmpty()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "${AcademicCalculators.formatDisplayDate(test?.testDate)} • ${res.obtainedMarks}/${test?.totalMarks ?: 100} (${res.percentage}%) • Grade ${res.grade} • Rank #${res.rank ?: "-"} ($statusTag)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            StatusPill(
                                label = if (res.passed) "PASS" else "FAIL",
                                textColor = if (res.passed) PresentGreen else AbsentRed,
                                backgroundColor = if (res.passed) PresentGreenBg else AbsentRedBg
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val headers = listOf("Metric", "Present", "Absent", "Leave", "Percentage")
                        val rows = listOf(
                            listOf("Daily Days", "${summary.dailyStats.presentCount}", "${summary.dailyStats.absentCount}", "${summary.dailyStats.leaveCount}", String.format(Locale.US, "%.2f%%", summary.dailyStats.percentage)),
                            listOf("Lectures", "${summary.periodStats.presentCount}", "${summary.periodStats.absentCount}", "${summary.periodStats.leaveCount}", String.format(Locale.US, "%.2f%%", summary.periodStats.percentage))
                        )
                        viewModel.exportPdfAndShare(
                            context,
                            "Student Report",
                            "${student.fullName} • Roll #${student.rollNumber} ($cls-$sec)",
                            headers,
                            rows
                        )
                    },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download Report PDF")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun HomeworkReadOnlyDetailDialog(
    homework: HomeworkItem,
    className: String,
    sectionName: String,
    onDismiss: () -> Unit
) {
    val dueParsed = AcademicCalculators.parseFlexibleDate(homework.dueDate)
    val today = LocalDate.now()
    val (dueLabel, dueFg, dueBg) = when {
        dueParsed == null -> Triple("DUE ${AcademicCalculators.formatDisplayDate(homework.dueDate)}", RoyalBlue, SkySoft)
        dueParsed.isBefore(today) -> Triple("OVERDUE", AbsentRed, AbsentRedBg)
        dueParsed.isEqual(today) -> Triple("DUE TODAY", WarningAmber, WarningAmberBg)
        else -> Triple("ACTIVE", PresentGreen, PresentGreenBg)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(homework.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill("${homework.subject} • $className-$sectionName", RoyalBlue, SkySoft)
                    StatusPill(dueLabel, dueFg, dueBg)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Submission Date: ${AcademicCalculators.formatDisplayDate(homework.dueDate)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavyPrimary
                )
                Text(
                    text = "Assigned by: ${homework.teacherName.ifBlank { "Teacher" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("Instructions", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = homework.instructions.ifBlank { "No additional instructions provided." },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!homework.attachmentUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Attachment", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = homework.attachmentUrl,
                        contentDescription = "Homework Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SkySoft),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
