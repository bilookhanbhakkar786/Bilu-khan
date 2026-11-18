package com.example.ui.screens.shared

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.ClassSection
import com.example.data.model.TestExam
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestsAndResultsPanel(
    state: HubUiState,
    viewModel: HubViewModel,
    fixedSection: ClassSection? = null
) {
    val context = LocalContext.current
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val isStaff = isPrincipal || state.role == UserRole.TEACHER
    val canCreateTest = isPrincipal || state.currentTeacherPermissions.createTests
    val canEnterMarks = isPrincipal || state.currentTeacherPermissions.enterMarks

    var showCreateTestDialog by remember { mutableStateOf(false) }
    var activeTestForMarks by remember { mutableStateOf<TestExam?>(null) }
    var activeTestSummary by remember { mutableStateOf<TestExam?>(null) }

    val visibleTests = remember(state.tests, state.activeAcademicSession, fixedSection, state.sessionSections) {
        val sid = state.activeAcademicSession?.id
        val allowedSecIds = state.sessionSections.map { it.id }.toSet()
        state.tests.filter { t ->
            (sid == null || t.sessionId == sid) &&
                (fixedSection == null || t.sectionId == fixedSection.id) &&
                (isPrincipal || t.sectionId in allowedSecIds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (fixedSection == null) 16.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            if (canCreateTest && (fixedSection != null || state.sessionSections.isNotEmpty())) {
                Button(
                    onClick = { showCreateTestDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_test_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Test")
                }
                Spacer(Modifier.height(14.dp))
            }

            if (visibleTests.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Assignment,
                    title = "No tests created yet",
                    description = "Create a test to enter student marks, calculate grades/ranks automatically, and publish results."
                )
            } else {
                visibleTests.forEach { test ->
                    val cls = state.classes.find { it.id == test.classId }?.name.orEmpty()
                    val sec = state.sections.find { it.id == test.sectionId }?.name.orEmpty()
                    val results = state.testResults.filter { it.testId == test.id }
                    val passedCount = results.count { it.passed }

                    SectionCard(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .clickable { activeTestSummary = test }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(test.name, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                Text("${test.subject} • $cls-$sec • ${AcademicCalculators.formatDisplayDate(test.testDate)}", style = MaterialTheme.typography.bodySmall)
                                Text("Total: ${test.totalMarks} • Passing: ${test.passingMarks} • Graded: ${results.size} (Passed: $passedCount)", style = MaterialTheme.typography.labelSmall)
                            }
                            StatusPill(
                                label = test.status.uppercase(),
                                textColor = when (test.status) {
                                    "published" -> PresentGreen
                                    "submitted" -> RoyalBlue
                                    else -> WarningAmber
                                },
                                backgroundColor = when (test.status) {
                                    "published" -> PresentGreenBg
                                    "submitted" -> SkySoft
                                    else -> WarningAmberBg
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canEnterMarks) {
                                OutlinedButton(onClick = { activeTestForMarks = test }) {
                                    Text("Enter / Edit Marks")
                                }
                            }
                            OutlinedButton(onClick = { activeTestSummary = test }) {
                                Text("Result Summary")
                            }
                            if (isStaff && test.status != "published") {
                                Button(onClick = { viewModel.publishTestResults(test) }) {
                                    Icon(Icons.Rounded.Publish, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Approve & Publish")
                                }
                            }
                            if (results.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    val headers = listOf("Rank", "Roll", "Student", "Marks", "%", "Grade", "Status")
                                    val rows = results.sortedBy { it.rank ?: 999 }.map { r ->
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
                                        "${test.name} - Class Result",
                                        "${test.subject} • $cls-$sec • ${AcademicCalculators.formatDisplayDate(test.testDate)}",
                                        headers,
                                        rows
                                    )
                                }) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTestDialog) {
        CreateTestDialog(
            state = state,
            fixedSection = fixedSection,
            onDismiss = { showCreateTestDialog = false },
            onCreate = { sec, name, subj, date, total, passing, desc ->
                viewModel.createTest(sec, name, subj, date, total, passing, desc)
                showCreateTestDialog = false
            }
        )
    }

    if (activeTestForMarks != null) {
        EnterTestMarksDialog(
            test = activeTestForMarks!!,
            state = state,
            onDismiss = { activeTestForMarks = null },
            onSave = { marksMap ->
                viewModel.saveTestMarks(activeTestForMarks!!, marksMap)
                activeTestForMarks = null
            }
        )
    }

    if (activeTestSummary != null) {
        TestSummaryDialog(
            test = activeTestSummary!!,
            state = state,
            viewModel = viewModel,
            onDismiss = { activeTestSummary = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateTestDialog(
    state: HubUiState,
    fixedSection: ClassSection?,
    onDismiss: () -> Unit,
    onCreate: (ClassSection, String, String, String, Double, Double, String) -> Unit
) {
    var selectedSection by remember { mutableStateOf(fixedSection ?: state.sessionSections.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Mathematics") }
    var testDate by remember { mutableStateOf(AcademicCalculators.todayDisplayDate()) }
    var totalMarks by remember { mutableStateOf("100") }
    var passingMarks by remember { mutableStateOf("40") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Test") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (fixedSection == null && state.sessionSections.isNotEmpty()) {
                    Text("Class & Section", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.sessionSections.forEach { sec ->
                            val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                            FilterChip(
                                selected = selectedSection?.id == sec.id,
                                onClick = { selectedSection = sec },
                                label = { Text("$cls ${sec.name}") }
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Test Name (e.g. Midterm Exam) *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = testDate, onValueChange = { testDate = it }, label = { Text("Test Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = totalMarks, onValueChange = { totalMarks = it }, label = { Text("Total Marks") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = passingMarks, onValueChange = { passingMarks = it }, label = { Text("Passing") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val sec = selectedSection ?: return@Button
                onCreate(sec, name, subject, testDate, totalMarks.toDoubleOrNull() ?: 100.0, passingMarks.toDoubleOrNull() ?: 40.0, desc)
            }) { Text("Create Test") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EnterTestMarksDialog(
    test: TestExam,
    state: HubUiState,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit
) {
    val sectionStudents = remember(state.students, test.sectionId) {
        AcademicCalculators.sortStudentsByRoll(
            state.students.filter { it.sectionId == test.sectionId && it.status == "active" }
        )
    }
    val existingMap = remember(state.testResults, test.id) {
        state.testResults.filter { it.testId == test.id }.associateBy { it.studentId }
    }
    val marksText = remember(sectionStudents, existingMap) {
        mutableStateMapOf<String, String>().apply {
            sectionStudents.forEach { stu ->
                this[stu.id] = existingMap[stu.id]?.obtainedMarks?.toString() ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Marks: ${test.name} (/${test.totalMarks})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (sectionStudents.isEmpty()) {
                    Text("No active students in this section.")
                } else {
                    sectionStudents.forEach { stu ->
                        val raw = marksText[stu.id].orEmpty()
                        val num = raw.toDoubleOrNull()
                        val preview = if (num != null) {
                            val pct = AcademicCalculators.calculatePercentage(num, test.totalMarks)
                            val pass = num >= test.passingMarks
                            val grade = AcademicCalculators.calculateGrade(pct, pass)
                            "$pct% • $grade • ${if (pass) "PASS" else "FAIL"}"
                        } else ""

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${stu.fullName} (#${stu.rollNumber})", style = MaterialTheme.typography.titleSmall)
                                if (preview.isNotBlank()) {
                                    Text(preview, style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                                }
                            }
                            OutlinedTextField(
                                value = raw,
                                onValueChange = { marksText[stu.id] = it },
                                label = { Text("Marks") },
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = marksText.mapNotNull { (id, v) ->
                    v.toDoubleOrNull()?.let { id to it }
                }.toMap()
                onSave(parsed)
            }) { Text("Save Results") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TestSummaryDialog(
    test: TestExam,
    state: HubUiState,
    viewModel: HubViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val results = state.testResults.filter { it.testId == test.id }.sortedBy { it.rank ?: 999 }
    val avg = if (results.isEmpty()) 0.0 else results.map { it.percentage }.average()
    val passRate = if (results.isEmpty()) 0.0 else (results.count { it.passed } * 100.0) / results.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${test.name} Summary") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Subject: ${test.subject} • Total: ${test.totalMarks} • Passing: ${test.passingMarks}")
                Text("Class Average: ${String.format("%.1f%%", avg)} • Pass Rate: ${String.format("%.1f%%", passRate)}", color = RoyalBlue)
                Spacer(Modifier.height(10.dp))
                if (results.isEmpty()) {
                    Text("No marks entered yet.")
                } else {
                    results.forEach { r ->
                        val stu = state.students.find { it.id == r.studentId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("#${r.rank ?: "-"} ${stu?.fullName.orEmpty()} (Roll ${stu?.rollNumber.orEmpty()})", style = MaterialTheme.typography.titleSmall)
                                Text("${r.obtainedMarks}/${test.totalMarks} • ${r.percentage}% • Grade ${r.grade}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(
                                label = if (r.passed) "PASS" else "FAIL",
                                textColor = if (r.passed) PresentGreen else AbsentRed,
                                backgroundColor = if (r.passed) PresentGreenBg else AbsentRedBg
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (results.isNotEmpty()) {
                Button(onClick = {
                    val headers = listOf("Rank", "Roll", "Name", "Marks", "%", "Grade", "Result")
                    val rows = results.map { r ->
                        val stu = state.students.find { it.id == r.studentId }
                        listOf("#${r.rank ?: "-"}", stu?.rollNumber.orEmpty(), stu?.fullName.orEmpty(), "${r.obtainedMarks}/${test.totalMarks}", "${r.percentage}%", r.grade, if (r.passed) "PASS" else "FAIL")
                    }
                    viewModel.exportCsvAndShare(context, "${test.name}_results", headers, rows)
                }) { Text("Export Excel/CSV") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
