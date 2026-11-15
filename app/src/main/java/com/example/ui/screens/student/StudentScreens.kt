package com.example.ui.screens.student

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
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.ui.components.AttendanceDonutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.screens.principal.AnnouncementPostCard
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentHomeScreen(
    state: HubUiState,
    onOpenNotifications: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenResults: () -> Unit
) {
    val school = state.school ?: return
    val stu = state.linkedStudentRecord
    val clsName = state.classes.find { it.id == stu?.classId }?.name.orEmpty()
    val secName = state.sections.find { it.id == stu?.sectionId }?.name.orEmpty()
    val sessName = state.sessions.find { it.id == stu?.sessionId }?.name.orEmpty()

    val attSummary = remember(stu?.id, state.attendanceSessions, state.attendanceRecords) {
        if (stu == null) null
        else AcademicCalculators.computeStudentAttendanceSummary(stu.id, state.attendanceSessions, state.attendanceRecords)
    }

    val publishedTests = remember(state.tests, stu?.sectionId) {
        state.tests.filter { it.status == "published" && (stu == null || it.sectionId == stu.sectionId) }
    }
    val myClassHomework = remember(state.homework, stu?.sectionId) {
        state.homework.filter { stu == null || it.sectionId == stu.sectionId }
    }
    val classAnnouncements = remember(state.announcements, stu?.sectionId) {
        state.announcements.filter { it.scope == "class" && (stu == null || it.sectionId == stu.sectionId) }
    }
    val myTimetable = remember(state.timetables, stu?.sectionId) {
        state.timetables.filter { stu == null || it.sectionId == stu.sectionId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 880.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SchoolAvatar(school.logoUrl, school.name, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stu?.fullName ?: state.currentUser?.fullName.orEmpty(), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(school.name, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        if (stu != null) {
                            Text("Class $clsName - Sec $secName • Roll #${stu.rollNumber} • $sessName", style = MaterialTheme.typography.bodySmall, color = SkySoft)
                        }
                    }
                    IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("student_notifications_btn")) {
                        Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                StatMetricCard(
                    title = "Daily Attendance",
                    value = String.format("%.1f%%", attSummary?.dailyStats?.percentage ?: 0.0),
                    icon = Icons.Rounded.CheckCircle,
                    accentColor = PresentGreen,
                    subtitle = "${attSummary?.dailyStats?.presentCount ?: 0}/${attSummary?.dailyStats?.totalCount ?: 0} days",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Published Results",
                    value = publishedTests.size.toString(),
                    icon = Icons.Rounded.Assignment,
                    accentColor = RoyalBlue,
                    subtitle = "Tap Results tab",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenAttendance, modifier = Modifier.weight(1f)) { Text("My Attendance") }
                Button(onClick = onOpenResults, modifier = Modifier.weight(1f)) { Text("My Results") }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(title = "Homework (${myClassHomework.size})") {
                if (myClassHomework.isEmpty()) {
                    Text("No homework assigned yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    myClassHomework.take(5).forEach { hw ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${hw.subject}: ${hw.title}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Text(hw.instructions, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Due ${hw.dueDate}", style = MaterialTheme.typography.labelSmall, color = AbsentRed)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Class Announcements") {
                if (classAnnouncements.isEmpty()) {
                    Text("No class notices yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    classAnnouncements.take(4).forEach { a ->
                        Text("• ${a.title}: ${a.body}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Class Timetable") {
                if (myTimetable.isEmpty()) {
                    Text("No timetable slots posted yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    myTimetable.take(8).forEach { slot ->
                        Text("• ${slot.dayOfWeek}: ${slot.subject} (${slot.startTime}-${slot.endTime}) • Room ${slot.roomNumber}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StudentAttendanceScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    val stu = state.linkedStudentRecord

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 860.dp)) {
            if (stu == null) {
                EmptyStateCard(Icons.Rounded.School, "Student Record Pending", "Once your school links your account, your private attendance records will appear here.")
            } else {
                val summary = remember(stu.id, state.attendanceSessions, state.attendanceRecords) {
                    AcademicCalculators.computeStudentAttendanceSummary(stu.id, state.attendanceSessions, state.attendanceRecords)
                }

                SectionCard(title = "1. Daily School Attendance") {
                    Text(
                        text = "Counts unique school days (multiple subject sessions on the same day count as one school day).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    AttendanceDonutChart(summary.dailyStats, "${stu.fullName} — Daily Rate")
                }

                Spacer(Modifier.height(12.dp))
                SectionCard(title = "2. Period / Subject Attendance") {
                    AttendanceDonutChart(summary.periodStats, "Individual Subject Sessions")
                    if (summary.subjectBreakdown.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        summary.subjectBreakdown.forEach { (subj, st) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(subj, style = MaterialTheme.typography.titleSmall)
                                Text("${st.presentCount}/${st.totalCount} (${String.format("%.1f%%", st.percentage)})", style = MaterialTheme.typography.bodySmall, color = RoyalBlue)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val headers = listOf("Type", "Present", "Absent", "Leave", "Percentage")
                        val rows = listOf(
                            listOf("Daily Days", "${summary.dailyStats.presentCount}", "${summary.dailyStats.absentCount}", "${summary.dailyStats.leaveCount}", String.format("%.2f%%", summary.dailyStats.percentage)),
                            listOf("Subject Sessions", "${summary.periodStats.presentCount}", "${summary.periodStats.absentCount}", "${summary.periodStats.leaveCount}", String.format("%.2f%%", summary.periodStats.percentage))
                        )
                        viewModel.exportPdfAndShare(context, "My Attendance Report", "${stu.fullName} • Roll #${stu.rollNumber}", headers, rows)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download My Attendance PDF")
                }
            }
        }
    }
}

@Composable
fun StudentResultsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    val stu = state.linkedStudentRecord
    // Privacy rule: ONLY published tests and ONLY this student's own results
    val publishedTests = remember(state.tests, stu?.sectionId) {
        state.tests.filter { it.status == "published" && (stu == null || it.sectionId == stu.sectionId) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 860.dp)) {
            if (stu == null || publishedTests.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Assignment,
                    title = "No Published Results Yet",
                    description = "Only official results approved and published by the Principal appear here."
                )
            } else {
                publishedTests.forEach { test ->
                    val myResult = state.testResults.find { it.testId == test.id && it.studentId == stu.id }
                    SectionCard(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(test.name, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                Text("${test.subject} • ${test.testDate}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (myResult != null) {
                                StatusPill(
                                    label = if (myResult.passed) "PASSED (${myResult.grade})" else "FAILED",
                                    textColor = if (myResult.passed) PresentGreen else AbsentRed,
                                    backgroundColor = if (myResult.passed) PresentGreenBg else AbsentRedBg
                                )
                            }
                        }
                        if (myResult != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Score: ${myResult.obtainedMarks} / ${test.totalMarks} (${myResult.percentage}%) • Class Position: #${myResult.rank ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoyalBlue
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val headers = listOf("Student", "Roll", "Subject", "Obtained", "Total", "%", "Grade", "Rank")
                                    val rows = listOf(
                                        listOf(stu.fullName, stu.rollNumber, test.subject, "${myResult.obtainedMarks}", "${test.totalMarks}", "${myResult.percentage}%", myResult.grade, "#${myResult.rank ?: "-"}")
                                    )
                                    viewModel.exportPdfAndShare(context, "${test.name} - Result Card", "${stu.fullName} (Roll #${stu.rollNumber})", headers, rows)
                                }
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Download Result Card PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentMySchoolScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val school = state.school ?: return
    val schoolPosts = state.announcements.filter { it.scope == "school" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 860.dp)) {
            SectionCard(title = school.name) {
                Text("${school.schoolType} • ${school.city} • ID: ${school.schoolCode}", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
                Spacer(Modifier.height(6.dp))
                Text(school.description.ifBlank { "Welcome to ${school.name}" }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("Contact: ${school.contactNumber} • ${school.email}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            Text("School Posts & Notices", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
            Spacer(Modifier.height(8.dp))

            if (schoolPosts.isEmpty()) {
                EmptyStateCard(Icons.Rounded.Campaign, "No School Posts Yet", "Official notices and school updates will appear here.")
            } else {
                schoolPosts.forEach { post ->
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
