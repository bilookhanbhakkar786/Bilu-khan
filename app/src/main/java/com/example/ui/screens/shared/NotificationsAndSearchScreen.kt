package com.example.ui.screens.shared

import android.content.Intent
import android.net.Uri
import com.example.data.model.toDateOnlyString
import com.example.data.model.toDisplayDateTime
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.HomeworkItem
import com.example.data.model.StudentRecord
import com.example.data.model.TestExam
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.screens.student.AttendanceSessionSheetDialog
import com.example.ui.screens.student.HomeworkReadOnlyDetailDialog
import com.example.ui.screens.student.StudentReadOnlyDetailDialog
import com.example.ui.screens.student.TestResultSheetDialog
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@Composable
fun NotificationsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 860.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications (${state.unreadNotificationsCount} unread)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyPrimary
                )
                if (state.unreadNotificationsCount > 0) {
                    TextButton(onClick = viewModel::markAllNotificationsRead) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Mark all read")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (state.notifications.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Notifications,
                    title = "No notifications yet",
                    description = "You'll see updates about announcements, results, homework, attendance, and approvals here."
                )
            } else {
                state.notifications.forEach { n ->
                    SectionCard(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { if (!n.isRead) viewModel.markNotificationRead(n.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary, modifier = Modifier.weight(1f))
                            if (!n.isRead) {
                                StatusPill("NEW", RoyalBlue, SkySoft)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(n.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(n.createdAt.toDisplayDateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    var query by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var selectedSessionId by remember { mutableStateOf(state.activeAcademicSession?.id) }
    var selectedSectionId by remember { mutableStateOf<String?>(null) }
    var detailStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var detailTest by remember { mutableStateOf<TestExam?>(null) }
    var detailAttendance by remember { mutableStateOf<AttendanceSession?>(null) }
    var detailHomework by remember { mutableStateOf<HomeworkItem?>(null) }

    val isStaff = state.role == UserRole.PRINCIPAL || state.role == UserRole.TEACHER
    val canViewSchoolData = isStaff || state.role == UserRole.STUDENT
    val q = query.trim()

    val matchedStudents = remember(state.students, query, selectedSessionId, selectedSectionId, canViewSchoolData) {
        if (!canViewSchoolData) {
            emptyList()
        } else {
            AcademicCalculators.sortStudentsByClassAndRoll(
                state.students.filter { s ->
                    (selectedSessionId == null || s.sessionId == selectedSessionId) &&
                        (selectedSectionId == null || s.sectionId == selectedSectionId) &&
                        (q.isBlank() || s.fullName.contains(q, true) || s.rollNumber.contains(q, true) || s.studentCode.contains(q, true))
                },
                state.classes,
                state.sections
            )
        }
    }

    val matchedTeachers = remember(state.teachers, query) {
        state.teachers.filter { t ->
            q.isBlank() || t.fullName.contains(q, true) || t.email.contains(q, true)
        }
    }

    val matchedSections = remember(state.sections, state.classes, query, selectedSessionId) {
        state.sections.filter { sec ->
            val clsName = state.classes.find { it.id == sec.classId }?.name.orEmpty()
            (selectedSessionId == null || sec.sessionId == selectedSessionId) &&
                (q.isBlank() || clsName.contains(q, true) || sec.name.contains(q, true))
        }
    }

    val matchedTests = remember(state.tests, query, subjectFilter, dateFilter, selectedSessionId, selectedSectionId) {
        val cleanDateFilter = AcademicCalculators.formatDisplayDate(dateFilter)
        state.tests.filter { t ->
            (selectedSessionId == null || t.sessionId == selectedSessionId) &&
                (selectedSectionId == null || t.sectionId == selectedSectionId) &&
                (subjectFilter.isBlank() || t.subject.contains(subjectFilter, true)) &&
                (cleanDateFilter.isBlank() || AcademicCalculators.formatDisplayDate(t.testDate) == cleanDateFilter) &&
                (q.isBlank() || t.name.contains(q, true) || t.subject.contains(q, true))
        }
    }

    val matchedAttendance = remember(state.attendanceSessions, query, subjectFilter, dateFilter, selectedSessionId, selectedSectionId, canViewSchoolData) {
        if (!canViewSchoolData) emptyList() else {
            val cleanDateFilter = AcademicCalculators.formatDisplayDate(dateFilter)
            state.attendanceSessions.filter { sess ->
                (selectedSessionId == null || sess.sessionId == selectedSessionId) &&
                    (selectedSectionId == null || sess.sectionId == selectedSectionId) &&
                    (subjectFilter.isBlank() || sess.subject.contains(subjectFilter, true)) &&
                    (cleanDateFilter.isBlank() || AcademicCalculators.formatDisplayDate(sess.attendanceDate) == cleanDateFilter) &&
                    (q.isBlank() || sess.subject.contains(q, true) || sess.teacherName.contains(q, true) || sess.sessionSlot.contains(q, true))
            }
        }
    }

    val matchedHomework = remember(state.homework, query, subjectFilter, dateFilter, selectedSessionId, selectedSectionId, canViewSchoolData) {
        if (!canViewSchoolData) emptyList() else {
            val cleanDateFilter = AcademicCalculators.formatDisplayDate(dateFilter)
            state.homework.filter { hw ->
                (selectedSessionId == null || hw.sessionId == selectedSessionId) &&
                    (selectedSectionId == null || hw.sectionId == selectedSectionId) &&
                    (subjectFilter.isBlank() || hw.subject.contains(subjectFilter, true)) &&
                    (cleanDateFilter.isBlank() || AcademicCalculators.formatDisplayDate(hw.dueDate) == cleanDateFilter) &&
                    (q.isBlank() || hw.title.contains(q, true) || hw.subject.contains(q, true) || hw.instructions.contains(q, true) || hw.teacherName.contains(q, true))
            }
        }
    }

    val matchedAnnouncements = remember(state.announcements, query, dateFilter) {
        val cleanDateFilter = dateFilter.trim()
        state.announcements.filter { a ->
            (cleanDateFilter.isBlank() || a.createdAt.toDateOnlyString().startsWith(cleanDateFilter)) &&
                (q.isBlank() || a.title.contains(q, true) || a.body.contains(q, true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            SectionCard(title = "Global Search & Filters") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search students, roll #, attendance, homework, tests, teachers") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subjectFilter, onValueChange = { subjectFilter = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dateFilter, onValueChange = { dateFilter = it }, label = { Text("Date (DD-MM-YYYY)") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedSessionId == null, onClick = { selectedSessionId = null }, label = { Text("All Sessions") })
                    state.sessions.forEach { sess ->
                        FilterChip(selected = selectedSessionId == sess.id, onClick = { selectedSessionId = sess.id }, label = { Text(sess.name) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (matchedStudents.isNotEmpty()) {
                SectionCard(title = "Students (${matchedStudents.size} — tap for Attendance & Results)") {
                    matchedStudents.take(20).forEach { s ->
                        val cls = state.classes.find { it.id == s.classId }?.name.orEmpty()
                        val sec = state.sections.find { it.id == s.sectionId }?.name.orEmpty()
                        val sum = AcademicCalculators.computeStudentAttendanceSummary(s.id, state.attendanceSessions, state.attendanceRecords)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailStudent = s }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Roll #${s.rollNumber} • ${s.fullName} ($cls-$sec)",
                                style = MaterialTheme.typography.bodySmall,
                                color = NavyPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "P:${sum.dailyStats.presentCount} A:${sum.dailyStats.absentCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoyalBlue
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedAttendance.isNotEmpty()) {
                SectionCard(title = "Attendance Sheets (${matchedAttendance.size} — tap to inspect Roll #)") {
                    matchedAttendance.take(15).forEach { sess ->
                        val sec = state.sections.find { it.id == sess.sectionId }
                        val cls = state.classes.find { it.id == sess.classId }
                        val recs = state.attendanceRecords.filter { it.attendanceSessionId == sess.id }
                        val p = recs.count { it.status == AttendanceStatus.PRESENT.dbValue }
                        val a = recs.count { it.status == AttendanceStatus.ABSENT.dbValue }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailAttendance = sess }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cls?.name.orEmpty()}-${sec?.name.orEmpty()} • ${sess.subject} (${AcademicCalculators.formatDisplayDate(sess.attendanceDate)})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text("P:$p / A:$a", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedHomework.isNotEmpty()) {
                SectionCard(title = "Homework (${matchedHomework.size} — tap to view)") {
                    matchedHomework.take(15).forEach { hw ->
                        val sec = state.sections.find { it.id == hw.sectionId }
                        val cls = state.classes.find { it.id == sec?.classId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailHomework = hw }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${hw.subject} (${cls?.name.orEmpty()}-${sec?.name.orEmpty()}) • ${hw.title}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Due ${AcademicCalculators.formatDisplayDate(hw.dueDate)}", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedTests.isNotEmpty()) {
                SectionCard(title = "Tests (${matchedTests.size} — tap to view marks)") {
                    matchedTests.take(15).forEach { t ->
                        Text(
                            text = "• ${t.name} (${t.subject} • ${AcademicCalculators.formatDisplayDate(t.testDate)} • ${t.status})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailTest = t }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedTeachers.isNotEmpty()) {
                SectionCard(title = "Teachers (${matchedTeachers.size})") {
                    matchedTeachers.take(15).forEach { t ->
                        Text("• ${t.fullName} (${t.email})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedSections.isNotEmpty()) {
                SectionCard(title = "Classes & Sections (${matchedSections.size})") {
                    matchedSections.take(15).forEach { sec ->
                        val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                        Text("• $cls — Section ${sec.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (matchedAnnouncements.isNotEmpty()) {
                SectionCard(title = "Announcements (${matchedAnnouncements.size})") {
                    matchedAnnouncements.take(15).forEach { a ->
                        Text("• ${a.title} (${a.category})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
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

    if (detailTest != null) {
        TestResultSheetDialog(
            test = detailTest!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                detailTest = null
                detailStudent = stu
            },
            onDismiss = { detailTest = null }
        )
    }

    if (detailAttendance != null) {
        AttendanceSessionSheetDialog(
            session = detailAttendance!!,
            state = state,
            viewModel = viewModel,
            onStudentClick = { stu ->
                detailAttendance = null
                detailStudent = stu
            },
            onDismiss = { detailAttendance = null }
        )
    }

    if (detailHomework != null) {
        val hw = detailHomework!!
        val sec = state.sections.find { it.id == hw.sectionId }
        val cls = state.classes.find { it.id == sec?.classId }
        HomeworkReadOnlyDetailDialog(
            homework = hw,
            className = cls?.name.orEmpty(),
            sectionName = sec?.name.orEmpty(),
            onDismiss = { detailHomework = null }
        )
    }
}

@Composable
fun ProfileAndSettingsScreen(
    state: HubUiState,
    viewModel: HubViewModel,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val user = state.currentUser
    val school = state.school
    var fullName by remember(user?.fullName) { mutableStateOf(user?.fullName.orEmpty()) }
    var phone by remember(user?.phone) { mutableStateOf(user?.phone.orEmpty()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    var showInvitePrincipalDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) avatarUri = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 640.dp)) {
            SectionCard(title = "My Account & Profile") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarUri != null || !user?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUri ?: user?.avatarUrl,
                            contentDescription = "Profile Photo",
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = SkySoft) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.padding(14.dp), tint = NavyPrimary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user?.fullName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                        Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        StatusPill("${state.role.label} • ${school?.name.orEmpty()}", RoyalBlue, SkySoft)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (avatarUri == null) "Change Profile Photo" else "New Photo Selected")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { authViewModel.updateMyProfile(fullName, phone, avatarUri) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Profile")
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(title = "School & Session Info") {
                Text("School: ${school?.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                Text("Unique School ID: ${school?.schoolCode.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                Text("Timezone: ${school?.timezone ?: "UTC"}", style = MaterialTheme.typography.bodySmall)
                Text("Active Session: ${state.activeAcademicSession?.name ?: "None"}", style = MaterialTheme.typography.bodySmall)
                if (state.role == UserRole.STUDENT) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = authViewModel::switchSchoolAsStudent,
                        modifier = Modifier.fillMaxWidth().testTag("profile_switch_school_button")
                    ) {
                        Text("Browse / Open Another School")
                    }
                }
            }

            if (state.role == UserRole.PRINCIPAL) {
                Spacer(Modifier.height(14.dp))
                SectionCard(title = "Principal Handover & Account") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = NavyPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Invite another person to become Principal of this school in your place, or permanently delete your Principal account & school.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showInvitePrincipalDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("invite_replacement_principal_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Invite Replacement Principal")
                    }

                    if (state.principalInvitations.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Principal Handover Invitations", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                        Spacer(Modifier.height(6.dp))
                        state.principalInvitations.forEach { inv ->
                            val (fg, bg) = when (inv.status) {
                                "accepted" -> PresentGreen to PresentGreenBg
                                else -> WarningAmber to WarningAmberBg
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${inv.fullName} (${inv.email})", style = MaterialTheme.typography.titleSmall)
                                    Text("Code: ${inv.inviteCode}", style = MaterialTheme.typography.bodySmall, color = RoyalBlue)
                                }
                                StatusPill(inv.status.uppercase(), fg, bg)
                                if (inv.status == "pending") {
                                    IconButton(onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "You're invited to take over as Principal of ${school?.name} (${school?.schoolCode}) on BL School Hub! Sign in as Principal and accept code: ${inv.inviteCode}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Principal Invite"))
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share Principal Code", tint = RoyalBlue)
                                    }
                                }
                                IconButton(onClick = { viewModel.revokePrincipalInvitation(inv.inviteCode) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove Invite", tint = AbsentRed)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("delete_principal_account_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AbsentRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Principal Account & School", color = AbsentRed)
                    }
                }
            } else {
                Spacer(Modifier.height(14.dp))
                SectionCard(title = "Account Management") {
                    Text(
                        text = if (state.role == UserRole.TEACHER) {
                            "Remove your Teacher profile from ${school?.name ?: "this school"} and permanently delete your BL School Hub account."
                        } else {
                            "Permanently delete your BL School Hub student account, pinned schools, and memberships."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("delete_my_account_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AbsentRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete My Account", color = AbsentRed)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = authViewModel::logout,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("logout_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Secure Logout")
            }
        }
    }

    if (showInvitePrincipalDialog) {
        var newName by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInvitePrincipalDialog = false },
            title = { Text("Invite Replacement Principal") },
            text = {
                Column {
                    Text(
                        text = "Once the invited user accepts the transfer code (PRN-XXXXXX), they become the Principal of ${school?.name} and your Principal ownership will transfer to them.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Replacement Principal Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email Address *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.inviteReplacementPrincipal(newName, newEmail)
                    showInvitePrincipalDialog = false
                }) { Text("Create Invite") }
            },
            dismissButton = {
                TextButton(onClick = { showInvitePrincipalDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        val isPrincipal = state.role == UserRole.PRINCIPAL
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    if (isPrincipal) "Delete Principal Account?" else "Delete My Account?",
                    color = AbsentRed
                )
            },
            text = {
                Text(
                    when (state.role) {
                        UserRole.PRINCIPAL ->
                            "This permanently deletes your Principal account and erases ${school?.name} including all classes, students, teachers, attendance, tests, and posts.\n\nTip: If you want your school to keep running under someone else, cancel and use 'Invite Replacement Principal' first."
                        UserRole.TEACHER ->
                            "This permanently deletes your Teacher account from ${school?.name ?: "this school"}, removes your class assignments and school access, and erases your BL School Hub profile."
                        UserRole.STUDENT ->
                            "This permanently deletes your Student profile, pinned schools, and memberships from BL School Hub."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        if (isPrincipal) {
                            authViewModel.ensureFreshAuthThenRun(context) { onComplete ->
                                viewModel.deletePrincipalAccountAndSchool(onComplete)
                            }
                        } else {
                            authViewModel.deleteMyNonPrincipalAccount(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}
