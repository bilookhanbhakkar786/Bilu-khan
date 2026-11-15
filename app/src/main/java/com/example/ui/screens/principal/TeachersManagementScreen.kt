package com.example.ui.screens.principal

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherPermissions
import com.example.data.model.TeacherProfile
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
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@Composable
fun TeachersAndApprovalsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var assigningTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var approvingRequest by remember { mutableStateOf<StudentAccountRequest?>(null) }

    val pendingStudentRequests = state.studentRequests.filter { it.status == "pending" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 880.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Teachers (${state.teachers.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Student Approvals (${pendingStudentRequests.size})") })
            }
            Spacer(Modifier.height(14.dp))

            if (tab == 0) {
                Button(
                    onClick = { showInviteDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("invite_teacher_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Invite Teacher by Email / Code")
                }

                Spacer(Modifier.height(14.dp))
                if (state.teacherInvitations.any { it.status == "pending" }) {
                    SectionCard(title = "Pending Invitations") {
                        state.teacherInvitations.filter { it.status == "pending" }.forEach { inv ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inv.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("${inv.email} • Invite Code: ${inv.inviteCode}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "You're invited to join ${state.school?.name} on BL School Hub! Sign up with ${inv.email} as a Teacher and enter invitation code: ${inv.inviteCode}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Teacher Invite"))
                                }) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share Invite Code", tint = RoyalBlue)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (state.teachers.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Badge,
                        title = "No teachers joined yet",
                        description = "Invite teachers using their email. They must accept the invitation to gain verified access."
                    )
                } else {
                    state.teachers.forEach { teacher ->
                        val assignments = state.teacherAssignments.filter { it.teacherProfileId == teacher.id }
                        SectionCard(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(teacher.fullName, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                    Text("${teacher.email} • ${teacher.phone}", style = MaterialTheme.typography.bodySmall)
                                    Text("Joined: ${teacher.joiningDate}", style = MaterialTheme.typography.labelSmall)
                                }
                                StatusPill(
                                    label = if (teacher.isActive) "Active" else "Deactivated",
                                    textColor = if (teacher.isActive) PresentGreen else AbsentRed,
                                    backgroundColor = if (teacher.isActive) PresentGreenBg else AbsentRedBg
                                )
                            }
                            if (assignments.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Assigned: " + assignments.joinToString { a ->
                                        val sec = state.sections.find { it.id == a.sectionId }
                                        val cls = state.classes.find { it.id == sec?.classId }
                                        "${cls?.name.orEmpty()}-${sec?.name.orEmpty()} (${a.subject})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoyalBlue
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { assigningTeacher = teacher }) {
                                    Text("Assign Class")
                                }
                                Button(onClick = { editingTeacher = teacher }) {
                                    Text("Permissions")
                                }
                            }
                        }
                    }
                }
            } else {
                if (pendingStudentRequests.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.CheckCircle,
                        title = "No pending student requests",
                        description = "When students search for your school and request connection, approve them here."
                    )
                } else {
                    pendingStudentRequests.forEach { req ->
                        SectionCard(modifier = Modifier.padding(vertical = 5.dp)) {
                            Text(req.requestedFullName, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                            Text("Father: ${req.requestedFatherName} • Roll #${req.requestedRollNumber}", style = MaterialTheme.typography.bodySmall)
                            Text("Requested: ${req.requestedSessionName} • ${req.requestedClassName} - ${req.requestedSectionName}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { approvingRequest = req }) {
                                Text("Verify & Link to Student Record")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteTeacherDialog(
            onDismiss = { showInviteDialog = false },
            onInvite = { name, email, phone, perms ->
                viewModel.inviteTeacher(name, email, phone, perms)
                showInviteDialog = false
            }
        )
    }

    if (editingTeacher != null) {
        EditTeacherPermissionsDialog(
            teacher = editingTeacher!!,
            onDismiss = { editingTeacher = null },
            onSave = { isActive, perms ->
                viewModel.updateTeacherPermissions(editingTeacher!!, isActive, perms)
                editingTeacher = null
            }
        )
    }

    if (assigningTeacher != null) {
        AssignTeacherSectionDialog(
            state = state,
            teacher = assigningTeacher!!,
            onDismiss = { assigningTeacher = null },
            onAssign = { secId, subject ->
                viewModel.assignTeacherToSection(assigningTeacher!!.id, secId, subject)
                assigningTeacher = null
            }
        )
    }

    if (approvingRequest != null) {
        ApproveStudentMatchDialog(
            state = state,
            request = approvingRequest!!,
            onDismiss = { approvingRequest = null },
            onApprove = { studentId ->
                viewModel.approveStudentConnection(approvingRequest!!.id, studentId)
                approvingRequest = null
            }
        )
    }
}

@Composable
private fun InviteTeacherDialog(
    onDismiss: () -> Unit,
    onInvite: (String, String, String, TeacherPermissions) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var perms by remember { mutableStateOf(TeacherPermissions()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Teacher") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Teacher Email *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PermissionSwitches(perms) { perms = it }
            }
        },
        confirmButton = { Button(onClick = { onInvite(name, email, phone, perms) }) { Text("Send Invite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditTeacherPermissionsDialog(
    teacher: TeacherProfile,
    onDismiss: () -> Unit,
    onSave: (Boolean, TeacherPermissions) -> Unit
) {
    var isActive by remember { mutableStateOf(teacher.isActive) }
    var perms by remember { mutableStateOf(teacher.permissions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${teacher.fullName} Permissions") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Active", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                Spacer(Modifier.height(8.dp))
                PermissionSwitches(perms) { perms = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(isActive, perms) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PermissionSwitches(perms: TeacherPermissions, onChange: (TeacherPermissions) -> Unit) {
    val rows = listOf(
        Triple("View Students", perms.viewStudents) { v: Boolean -> perms.copy(viewStudents = v) },
        Triple("Add / Edit Students", perms.editStudents) { v: Boolean -> perms.copy(editStudents = v) },
        Triple("Mark Attendance", perms.markAttendance) { v: Boolean -> perms.copy(markAttendance = v) },
        Triple("Edit Attendance", perms.editAttendance) { v: Boolean -> perms.copy(editAttendance = v) },
        Triple("Create Tests", perms.createTests) { v: Boolean -> perms.copy(createTests = v) },
        Triple("Enter Marks", perms.enterMarks) { v: Boolean -> perms.copy(enterMarks = v) },
        Triple("Publish Class Announcements", perms.publishAnnouncements) { v: Boolean -> perms.copy(publishAnnouncements = v) },
        Triple("View Reports", perms.viewReports) { v: Boolean -> perms.copy(viewReports = v) }
    )
    rows.forEach { (label, checked, updater) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Checkbox(checked = checked, onCheckedChange = { onChange(updater(it)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignTeacherSectionDialog(
    state: HubUiState,
    teacher: TeacherProfile,
    onDismiss: () -> Unit,
    onAssign: (String, String) -> Unit
) {
    var selectedSectionId by remember { mutableStateOf(state.sessionSections.firstOrNull()?.id.orEmpty()) }
    var subject by remember { mutableStateOf("Mathematics") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${teacher.fullName}") },
        text = {
            Column {
                Text("Select Class & Section", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.sessionSections.forEach { sec ->
                        val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                        FilterChip(
                            selected = selectedSectionId == sec.id,
                            onClick = { selectedSectionId = sec.id },
                            label = { Text("$cls ${sec.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (selectedSectionId.isNotBlank()) onAssign(selectedSectionId, subject) }) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApproveStudentMatchDialog(
    state: HubUiState,
    request: StudentAccountRequest,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit
) {
    val candidates = remember(state.students, request) {
        state.students.filter {
            it.rollNumber.equals(request.requestedRollNumber, ignoreCase = true) ||
                it.fullName.contains(request.requestedFullName, ignoreCase = true)
        }.ifEmpty { state.sessionStudents }
    }
    var selectedStudentId by remember { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Student Record") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Select the verified student record to link with ${request.requestedFullName} (Roll #${request.requestedRollNumber}):", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text("No students registered yet. Add the student to their class first.")
                } else {
                    candidates.forEach { stu ->
                        val cls = state.classes.find { it.id == stu.classId }?.name.orEmpty()
                        val sec = state.sections.find { it.id == stu.sectionId }?.name.orEmpty()
                        FilterChip(
                            selected = selectedStudentId == stu.id,
                            onClick = { selectedStudentId = stu.id },
                            label = { Text("${stu.fullName} (Roll #${stu.rollNumber}, $cls-$sec)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedStudentId.isNotBlank(),
                onClick = { onApprove(selectedStudentId) }
            ) { Text("Approve & Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
