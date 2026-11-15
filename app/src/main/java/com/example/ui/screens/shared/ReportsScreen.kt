package com.example.ui.screens.shared

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceStatus
import com.example.ui.components.SectionCard
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsCenterScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    val reportTypes = listOf(
        "Daily Attendance",
        "Weekly Attendance",
        "Monthly Attendance",
        "Annual Attendance",
        "Student-wise Attendance",
        "Class-wise Attendance",
        "Subject-wise Attendance",
        "Test Results Report",
        "Student Performance",
        "Teacher Activity"
    )
    var selectedReport by remember { mutableStateOf(reportTypes.first()) }
    val today = LocalDate.now()

    val reportData = remember(selectedReport, state) {
        buildReportTable(selectedReport, state, today)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 920.dp)) {
            SectionCard(title = "Select Report Type") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reportTypes.forEach { r ->
                        FilterChip(
                            selected = selectedReport == r,
                            onClick = { selectedReport = r },
                            label = { Text(r) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(
                title = selectedReport,
                trailing = {
                    Text("${reportData.rows.size} rows", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
                }
            ) {
                Text(reportData.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.exportPdfAndShare(
                                context,
                                selectedReport,
                                reportData.subtitle,
                                reportData.headers,
                                reportData.rows,
                                reportData.footer
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Download PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportCsvAndShare(context, selectedReport, reportData.headers, reportData.rows)
                        },
                        modifier = Modifier.weight(1f).testTag("export_csv_button")
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Excel / CSV")
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Header Row
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    reportData.headers.forEach { h ->
                        Text(h, style = MaterialTheme.typography.labelSmall, color = NavyPrimary, modifier = Modifier.weight(1f))
                    }
                }
                reportData.rows.take(40).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        row.forEach { cell ->
                            Text(cell, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class BuiltReport(
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val footer: String = ""
)

private fun buildReportTable(type: String, state: HubUiState, today: LocalDate): BuiltReport {
    val sessionId = state.activeAcademicSession?.id
    val sessName = state.activeAcademicSession?.name ?: "All Sessions"
    val sessionsInScope = state.attendanceSessions.filter { sessionId == null || it.sessionId == sessionId }

    return when (type) {
        "Daily Attendance", "Weekly Attendance", "Monthly Attendance", "Annual Attendance" -> {
            val filtered = when (type) {
                "Daily Attendance" -> sessionsInScope.filter { it.attendanceDate == today.toString() }
                "Weekly Attendance" -> sessionsInScope.filter { it.attendanceDate >= today.minusDays(7).toString() }
                "Monthly Attendance" -> sessionsInScope.filter { it.attendanceDate.startsWith(today.toString().take(7)) }
                else -> sessionsInScope
            }
            val rows = filtered.map { s ->
                val recs = state.attendanceRecords.filter { it.attendanceSessionId == s.id }
                val cls = state.classes.find { it.id == s.classId }?.name.orEmpty()
                val sec = state.sections.find { it.id == s.sectionId }?.name.orEmpty()
                listOf(
                    s.attendanceDate,
                    "$cls-$sec",
                    "${s.subject} (${s.sessionSlot})",
                    recs.count { it.status == AttendanceStatus.PRESENT.dbValue }.toString(),
                    recs.count { it.status == AttendanceStatus.ABSENT.dbValue }.toString()
                )
            }
            BuiltReport("Academic Session: $sessName", listOf("Date", "Class", "Session", "Present", "Absent"), rows)
        }

        "Student-wise Attendance" -> {
            val rows = state.sessionStudents.map { stu ->
                val sum = AcademicCalculators.computeStudentAttendanceSummary(stu.id, sessionsInScope, state.attendanceRecords)
                listOf(
                    stu.rollNumber,
                    stu.fullName,
                    "${sum.dailyStats.presentCount}/${sum.dailyStats.totalCount}",
                    String.format("%.1f%%", sum.dailyStats.percentage),
                    String.format("%.1f%%", sum.periodStats.percentage)
                )
            }
            BuiltReport("Session: $sessName", listOf("Roll", "Student", "Days P/T", "Daily %", "Period %"), rows)
        }

        "Class-wise Attendance" -> {
            val rows = state.sessionSections.map { sec ->
                val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                val secSessions = sessionsInScope.filter { it.sectionId == sec.id }.map { it.id }.toSet()
                val recs = state.attendanceRecords.filter { it.attendanceSessionId in secSessions }
                val p = recs.count { it.status == AttendanceStatus.PRESENT.dbValue }
                val rate = if (recs.isEmpty()) 0.0 else (p * 100.0) / recs.size
                listOf("$cls-$sec", secSessions.size.toString(), p.toString(), recs.size.toString(), String.format("%.1f%%", rate))
            }
            BuiltReport("Session: $sessName", listOf("Class", "Sessions", "Present", "Total Marks", "Rate"), rows)
        }

        "Subject-wise Attendance" -> {
            val bySubj = sessionsInScope.groupBy { it.subject }
            val rows = bySubj.map { (subj, list) ->
                val ids = list.map { it.id }.toSet()
                val recs = state.attendanceRecords.filter { it.attendanceSessionId in ids }
                val p = recs.count { it.status == AttendanceStatus.PRESENT.dbValue }
                val rate = if (recs.isEmpty()) 0.0 else (p * 100.0) / recs.size
                listOf(subj, list.size.toString(), p.toString(), recs.size.toString(), String.format("%.1f%%", rate))
            }
            BuiltReport("Session: $sessName", listOf("Subject", "Sessions", "Present", "Records", "Rate"), rows)
        }

        "Test Results Report" -> {
            val tests = state.tests.filter { sessionId == null || it.sessionId == sessionId }
            val rows = tests.map { t ->
                val res = state.testResults.filter { it.testId == t.id }
                val avg = if (res.isEmpty()) 0.0 else res.map { it.percentage }.average()
                listOf(t.name, t.subject, t.testDate, t.status.uppercase(), String.format("%.1f%%", avg))
            }
            BuiltReport("Session: $sessName", listOf("Test", "Subject", "Date", "Status", "Avg %"), rows)
        }

        "Student Performance" -> {
            val rows = state.sessionStudents.map { stu ->
                val res = state.testResults.filter { it.studentId == stu.id }
                val avg = if (res.isEmpty()) 0.0 else res.map { it.percentage }.average()
                val att = AcademicCalculators.computeStudentAttendanceSummary(stu.id, sessionsInScope, state.attendanceRecords)
                listOf(stu.rollNumber, stu.fullName, res.size.toString(), String.format("%.1f%%", avg), String.format("%.1f%%", att.dailyStats.percentage))
            }
            BuiltReport("Session: $sessName", listOf("Roll", "Student", "Tests", "Avg Score", "Daily Att%"), rows)
        }

        else -> {
            val rows = state.teachers.map { t ->
                val attCount = sessionsInScope.count { it.teacherId == t.userId }
                val testCount = state.tests.count { it.createdBy == t.userId }
                val hwCount = state.homework.count { it.teacherId == t.userId }
                listOf(t.fullName, if (t.isActive) "Active" else "Inactive", attCount.toString(), testCount.toString(), hwCount.toString())
            }
            BuiltReport("Session: $sessName", listOf("Teacher", "Status", "Attendance", "Tests", "Homework"), rows)
        }
    }
}
