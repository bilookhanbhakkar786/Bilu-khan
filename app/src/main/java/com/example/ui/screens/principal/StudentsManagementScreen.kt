package com.example.ui.screens.principal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AcademicCalculators
import com.example.data.model.ClassSection
import com.example.data.model.StudentRecord
import com.example.data.model.UserRole
import com.example.ui.components.AttendanceDonutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentsManagementScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    fixedSection: ClassSection? = null
) {
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val isStaff = isPrincipal || state.role == UserRole.TEACHER
    val canEdit = isPrincipal || state.currentTeacherPermissions.editStudents
    var searchQuery by remember { mutableStateOf("") }
    var filterSectionId by remember(fixedSection) { mutableStateOf(fixedSection?.id) }
    var editingStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var detailStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val filteredStudents = remember(state.sessionStudents, state.classes, state.sections, searchQuery, filterSectionId, fixedSection) {
        AcademicCalculators.sortStudentsByClassAndRoll(
            state.sessionStudents.filter { s ->
                val secOk = if (fixedSection != null) s.sectionId == fixedSection.id
                else filterSectionId == null || s.sectionId == filterSectionId
                val q = searchQuery.trim()
                val queryOk = q.isBlank() ||
                    s.fullName.contains(q, ignoreCase = true) ||
                    s.rollNumber.contains(q, ignoreCase = true) ||
                    s.studentCode.contains(q, ignoreCase = true)
                secOk && queryOk
            },
            state.classes,
            state.sections
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (fixedSection == null) 16.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 880.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search name, roll #, or ID") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("student_search_input")
                )
                if (canEdit && (fixedSection != null || state.sessionSections.isNotEmpty())) {
                    Button(
                        onClick = {
                            editingStudent = null
                            showAddDialog = true
                        },
                        modifier = Modifier.height(52.dp).testTag("add_student_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Student")
                    }
                }
            }

            if (fixedSection == null && state.sessionSections.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filterSectionId == null,
                        onClick = { filterSectionId = null },
                        label = { Text("All Classes") }
                    )
                    state.sessionSections.forEach { sec ->
                        val clsName = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                        FilterChip(
                            selected = filterSectionId == sec.id,
                            onClick = { filterSectionId = sec.id },
                            label = { Text("$clsName ${sec.name}") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (filteredStudents.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Groups,
                    title = "No students found",
                    description = "Add students to a class section in the active academic session."
                )
            } else {
                filteredStudents.forEach { stu ->
                    val clsName = state.classes.find { it.id == stu.classId }?.name.orEmpty()
                    val secName = state.sections.find { it.id == stu.sectionId }?.name.orEmpty()
                    SectionCard(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .clickable { detailStudent = stu }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!stu.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = stu.avatarUrl,
                                    contentDescription = stu.fullName,
                                    modifier = Modifier.size(46.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = CircleShape,
                                    color = SkySoft
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stu.rollNumber.take(3), style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stu.fullName, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Text("Roll #${stu.rollNumber} • ${stu.studentCode} • $clsName-$secName", style = MaterialTheme.typography.bodySmall)
                                Text("Father: ${stu.fatherName} • ${stu.parentContact}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canEdit) {
                                IconButton(onClick = {
                                    editingStudent = stu
                                    showAddDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit student", tint = RoyalBlue)
                                }
                                IconButton(
                                    onClick = { deletingStudent = stu },
                                    modifier = Modifier.testTag("delete_student_${stu.id}")
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove student", tint = AbsentRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        StudentFormDialog(
            state = state,
            existing = editingStudent,
            fixedSection = fixedSection,
            onDismiss = { showAddDialog = false },
            onSave = { sessId, clsId, secId, name, father, roll, dob, gender, pName, pContact, addr, admDate, email, uri ->
                viewModel.saveStudent(
                    editingStudent, sessId, clsId, secId, name, father, roll, dob, gender, pName, pContact, addr, admDate, email, uri
                )
                showAddDialog = false
            }
        )
    }

    if (detailStudent != null) {
        StudentProfileDetailDialog(
            student = detailStudent!!,
            state = state,
            viewModel = viewModel,
            canManage = isStaff,
            onDismiss = { detailStudent = null }
        )
    }

    if (deletingStudent != null) {
        val stu = deletingStudent!!
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete Student?") },
            text = {
                Text("Delete ${stu.fullName} (Roll #${stu.rollNumber})? This will also disconnect any linked student login account from accessing the school.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudent(stu.id)
                        deletingStudent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) {
                    Text("Delete Student")
                }
            },
            dismissButton = { TextButton(onClick = { deletingStudent = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentFormDialog(
    state: HubUiState,
    existing: StudentRecord?,
    fixedSection: ClassSection?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String?, String, String, String, String, String, String?, Uri?) -> Unit
) {
    val defaultSection = fixedSection ?: state.sections.find { it.id == existing?.sectionId } ?: state.sessionSections.firstOrNull()
    var selectedSection by remember { mutableStateOf(defaultSection) }
    var fullName by remember { mutableStateOf(existing?.fullName.orEmpty()) }
    var fatherName by remember { mutableStateOf(existing?.fatherName.orEmpty()) }
    var rollNumber by remember { mutableStateOf(existing?.rollNumber.orEmpty()) }
    var dob by remember { mutableStateOf(AcademicCalculators.formatDisplayDate(existing?.dateOfBirth)) }
    var gender by remember { mutableStateOf(existing?.gender ?: "Male") }
    var parentName by remember { mutableStateOf(existing?.parentName.orEmpty()) }
    var parentContact by remember { mutableStateOf(existing?.parentContact.orEmpty()) }
    var address by remember { mutableStateOf(existing?.address.orEmpty()) }
    var admissionDate by remember {
        mutableStateOf(
            AcademicCalculators.formatDisplayDate(existing?.admissionDate)
                .ifBlank { AcademicCalculators.todayDisplayDate() }
        )
    }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) avatarUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Register Student" else "Edit Student") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (fixedSection == null && state.sessionSections.isNotEmpty()) {
                    Text("Class & Section", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.sessionSections.forEach { sec ->
                            val cName = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                            FilterChip(
                                selected = selectedSection?.id == sec.id,
                                onClick = { selectedSection = sec },
                                label = { Text("$cName ${sec.name}") }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (avatarUri == null) "Profile Picture (Optional)" else "Photo Selected")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Student Full Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = fatherName, onValueChange = { fatherName = it }, label = { Text("Father's Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = rollNumber, onValueChange = { rollNumber = it }, label = { Text("Roll Number *") }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Existing colliding roll numbers shift up (+1) automatically (e.g. adding Roll #2 moves 2->3, 3->4).",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoyalBlue
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { g ->
                        FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g) })
                    }
                }
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date of Birth (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = parentName, onValueChange = { parentName = it }, label = { Text("Parent Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = parentContact, onValueChange = { parentContact = it }, label = { Text("Parent Contact") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = admissionDate, onValueChange = { admissionDate = it }, label = { Text("Admission Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Student Email (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val sec = selectedSection ?: return@Button
                onSave(sec.sessionId, sec.classId, sec.id, fullName, fatherName, rollNumber, dob, gender, parentName, parentContact, address, admissionDate, email, avatarUri)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentProfileDetailDialog(
    student: StudentRecord,
    state: HubUiState,
    viewModel: HubViewModel,
    canManage: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val summary = remember(student.id, state.attendanceSessions, state.attendanceRecords) {
        AcademicCalculators.computeStudentAttendanceSummary(student.id, state.attendanceSessions, state.attendanceRecords)
    }
    val studentResults = remember(student.id, state.testResults) {
        state.testResults.filter { it.studentId == student.id }
    }
    var showPromotePane by remember { mutableStateOf(false) }
    var targetSessionId by remember { mutableStateOf(state.sessions.firstOrNull()?.id ?: student.sessionId) }
    var targetSectionId by remember { mutableStateOf(student.sectionId) }
    var newRoll by remember { mutableStateOf(student.rollNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${student.fullName} (Roll #${student.rollNumber})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Student ID: ${student.studentCode}", style = MaterialTheme.typography.labelLarge, color = RoyalBlue)
                Text("Father: ${student.fatherName} • Parent: ${student.parentContact}", style = MaterialTheme.typography.bodySmall)
                Text("Admission Date: ${AcademicCalculators.formatDisplayDate(student.admissionDate)}", style = MaterialTheme.typography.bodySmall)
                if (!student.dateOfBirth.isNullOrBlank()) {
                    Text("Date of Birth: ${AcademicCalculators.formatDisplayDate(student.dateOfBirth)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Address: ${student.address}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                AttendanceDonutChart(summary.dailyStats, "Daily Attendance (Unique School Days)")
                Spacer(Modifier.height(10.dp))
                AttendanceDonutChart(summary.periodStats, "Period / Subject Attendance Sessions")

                if (summary.subjectBreakdown.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Subject-wise Breakdown", style = MaterialTheme.typography.titleSmall)
                    summary.subjectBreakdown.forEach { (subj, st) ->
                        Text(
                            text = "• $subj: ${st.presentCount}/${st.totalCount} (${String.format("%.1f%%", st.percentage)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val headers = listOf("Metric", "Present", "Absent", "Leave", "Rate")
                        val rows = listOf(
                            listOf("Daily Days", "${summary.dailyStats.presentCount}", "${summary.dailyStats.absentCount}", "${summary.dailyStats.leaveCount}", String.format("%.2f%%", summary.dailyStats.percentage)),
                            listOf("Period Sessions", "${summary.periodStats.presentCount}", "${summary.periodStats.absentCount}", "${summary.periodStats.leaveCount}", String.format("%.2f%%", summary.periodStats.percentage))
                        )
                        viewModel.exportPdfAndShare(
                            context,
                            "Student Attendance Report",
                            "${student.fullName} • Roll #${student.rollNumber} • ID: ${student.studentCode}",
                            headers,
                            rows
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download Attendance PDF")
                }

                if (studentResults.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Exam History", style = MaterialTheme.typography.titleSmall)
                    studentResults.forEach { res ->
                        val test = state.tests.find { it.id == res.testId }
                        Text(
                            text = "• ${test?.name ?: "Test"} (${test?.subject.orEmpty()}): ${res.obtainedMarks}/${test?.totalMarks ?: 100} (${res.grade})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (canManage) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showPromotePane = !showPromotePane }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Upgrade, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Transfer / Promote Student")
                    }
                    if (showPromotePane) {
                        Spacer(Modifier.height(8.dp))
                        Text("Target Session", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.sessions.forEach { sess ->
                                FilterChip(selected = targetSessionId == sess.id, onClick = { targetSessionId = sess.id }, label = { Text(sess.name) })
                            }
                        }
                        val targetSecs = AcademicCalculators.sortSections(
                            state.sections.filter { it.sessionId == targetSessionId },
                            state.classes
                        )
                        Text("Target Class & Section", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            targetSecs.forEach { sec ->
                                val cName = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                                FilterChip(selected = targetSectionId == sec.id, onClick = { targetSectionId = sec.id }, label = { Text("$cName ${sec.name}") })
                            }
                        }
                        OutlinedTextField(value = newRoll, onValueChange = { newRoll = it }, label = { Text("Roll Number") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val chosenSec = targetSecs.find { it.id == targetSectionId }
                            if (chosenSec != null) {
                                if (chosenSec.sessionId == student.sessionId) {
                                    Button(onClick = {
                                        viewModel.transferStudent(student, chosenSec.classId, chosenSec.id)
                                        onDismiss()
                                    }) { Text("Transfer Class") }
                                } else {
                                    Button(onClick = {
                                        viewModel.promoteStudent(student, chosenSec.sessionId, chosenSec.classId, chosenSec.id, newRoll)
                                        onDismiss()
                                    }) { Text("Promote to Session") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
