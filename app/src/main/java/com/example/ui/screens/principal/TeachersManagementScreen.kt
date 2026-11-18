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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherInviteLinks
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
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachersAndApprovalsScreen(
    state: HubUiState,
    viewModel: HubViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var assigningTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var removingTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var deletingInvite by remember { mutableStateOf<TeacherInvitation?>(null) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val schoolName = state.school?.name ?: "BL School Hub"
    val schoolCode = state.school?.schoolCode.orEmpty()

    fun shareInvite(inv: TeacherInvitation) {
        val shareText = if (inv.authType == "credentials" || !inv.username.isNullOrBlank()) {
            TeacherInviteLinks.buildCredentialShareMessage(
                schoolName,
                schoolCode,
                inv.username.orEmpty(),
                inv.accessPassword.orEmpty().ifBlank { inv.inviteCode }
            )
        } else {
            TeacherInviteLinks.buildShareMessage(schoolName, schoolCode, inv.inviteCode)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, if (inv.authType == "credentials") "Teacher Credentials — $schoolName" else "Teacher Invite — $schoolName")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Teacher Credentials"))
    }

    fun copyInvite(inv: TeacherInvitation) {
        val isCred = inv.authType == "credentials" || !inv.username.isNullOrBlank()
        val shareText = if (isCred) {
            TeacherInviteLinks.buildCredentialShareMessage(
                schoolName,
                schoolCode,
                inv.username.orEmpty(),
                inv.accessPassword.orEmpty().ifBlank { inv.inviteCode }
            )
        } else {
            TeacherInviteLinks.buildShareMessage(schoolName, schoolCode, inv.inviteCode)
        }
        clipboard.setText(AnnotatedString(shareText))
        copiedNotice = if (isCred) {
            "Teacher credentials copied (${inv.username})! Paste it on WhatsApp or any messaging app."
        } else {
            "Invite link copied (${inv.inviteCode})! Paste it on WhatsApp or any messaging app."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 880.dp)) {
            Button(
                onClick = {
                    copiedNotice = null
                    showInviteDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("invite_teacher_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Teacher (Generate Username or Link)")
            }

            if (copiedNotice != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = PresentGreenBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = copiedNotice!!,
                        color = PresentGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val createdInvite = state.lastCreatedTeacherInvite
            if (createdInvite != null) {
                Spacer(Modifier.height(12.dp))
                val isCred = createdInvite.authType == "credentials" || !createdInvite.username.isNullOrBlank()
                SectionCard(
                    title = if (isCred) "Teacher Login Credentials Ready" else "Shareable Teacher Invite Link Ready",
                    trailing = {
                        TextButton(onClick = { viewModel.clearLastCreatedTeacherInvite() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    if (isCred) {
                        Text(
                            text = "Share these login credentials with your teacher. When they open BL School Hub and tap 'Join as Teacher' > 'Join via Username & Password', they can enter these details and specify their name to join $schoolName.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = SkySoft,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Username: ${createdInvite.username}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Password / Code: ${createdInvite.accessPassword}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RoyalBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Backup Invite Code: ${createdInvite.inviteCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val appInviteLink = TeacherInviteLinks.buildAppDeepLink(createdInvite.inviteCode)
                        Text(
                            text = "Send this invite on WhatsApp or any messaging platform. Tapping the app link opens BL School Hub directly so the teacher can sign in with Gmail and join $schoolName.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = SkySoft,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = appInviteLink,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Invite Code: ${createdInvite.inviteCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoyalBlue
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { shareInvite(createdInvite) },
                            modifier = Modifier.weight(1f).testTag("share_teacher_invite_whatsapp")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share on WhatsApp / Apps")
                        }
                        OutlinedButton(
                            onClick = { copyInvite(createdInvite) },
                            modifier = Modifier.testTag("copy_teacher_invite_link")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCred) "Copy Credentials" else "Copy Link")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            val visibleInvitations = state.teacherInvitations.filter { it.status == "pending" || it.status == "accepted" }
            if (visibleInvitations.isNotEmpty()) {
                SectionCard(title = "Teacher Invitations & Credentials (${visibleInvitations.size})") {
                    visibleInvitations.forEach { inv ->
                        val isPending = inv.status == "pending"
                        val isCred = inv.authType == "credentials" || !inv.username.isNullOrBlank()
                        val displayName = when {
                            isCred && inv.fullName.isNotBlank() && !inv.fullName.startsWith("Teacher (") -> "${inv.fullName} (@${inv.username})"
                            isCred -> "Teacher: @${inv.username}"
                            inv.fullName.isNotBlank() -> inv.fullName
                            else -> "Open Teacher Invite"
                        }
                        val subtitleParts = if (isCred) {
                            "Username: ${inv.username} • Password: ${inv.accessPassword ?: inv.inviteCode}"
                        } else {
                            listOfNotNull(
                                inv.email.takeIf { it.isNotBlank() },
                                "Code: ${inv.inviteCode}"
                            ).joinToString(" • ")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, style = MaterialTheme.typography.titleSmall)
                                Text(subtitleParts, style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(
                                label = if (isPending) "PENDING" else "JOINED",
                                textColor = if (isPending) WarningAmber else PresentGreen,
                                backgroundColor = if (isPending) WarningAmberBg else PresentGreenBg
                            )
                            if (isPending) {
                                IconButton(
                                    onClick = { shareInvite(inv) },
                                    modifier = Modifier.testTag("share_invite_${inv.inviteCode}")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share Invite", tint = RoyalBlue)
                                }
                                IconButton(
                                    onClick = { copyInvite(inv) },
                                    modifier = Modifier.testTag("copy_invite_${inv.inviteCode}")
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Details", tint = NavyPrimary)
                                }
                            }
                            IconButton(
                                onClick = { deletingInvite = inv },
                                modifier = Modifier.testTag("delete_teacher_invite_${inv.inviteCode}")
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete Teacher Invite", tint = AbsentRed)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                text = "School Teachers (${state.teachers.size})",
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary
            )
            Spacer(Modifier.height(8.dp))

            if (state.teachers.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.Badge,
                    title = "No teachers joined yet",
                    description = "Tap 'Add Teacher (Generate Username or Link)' above to create login credentials or an invite link for your teachers."
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
                                Text("${teacher.email} • ${teacher.phone.ifBlank { "No phone" }}", style = MaterialTheme.typography.bodySmall)
                                Text("Joined: ${AcademicCalculators.formatDisplayDate(teacher.joiningDate)}", style = MaterialTheme.typography.labelSmall)
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(onClick = { assigningTeacher = teacher }) {
                                Text("Assign Class")
                            }
                            Button(onClick = { editingTeacher = teacher }) {
                                Text("Permissions")
                            }
                            OutlinedButton(
                                onClick = { removingTeacher = teacher },
                                modifier = Modifier.testTag("remove_teacher_${teacher.id}")
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = AbsentRed)
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", color = AbsentRed)
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
            onInviteLink = { name, email, phone, perms ->
                viewModel.inviteTeacher(name, email, phone, perms)
                showInviteDialog = false
            },
            onInviteCredentials = { name, email, phone, username, password, perms ->
                viewModel.inviteTeacherWithCredentials(name, email, phone, username, password, perms)
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

    if (removingTeacher != null) {
        val t = removingTeacher!!
        AlertDialog(
            onDismissRequest = { removingTeacher = null },
            title = { Text("Remove Teacher?") },
            text = {
                Text("Removing ${t.fullName} will delete their teacher profile, clear their class assignments, and immediately revoke their school access.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeTeacher(t)
                        removingTeacher = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) {
                    Text("Remove Teacher")
                }
            },
            dismissButton = { TextButton(onClick = { removingTeacher = null }) { Text("Cancel") } }
        )
    }

    if (deletingInvite != null) {
        val inv = deletingInvite!!
        AlertDialog(
            onDismissRequest = { deletingInvite = null },
            title = { Text("Delete Invitation?") },
            text = {
                Text(
                    if (inv.status == "accepted") {
                        "This invitation for ${inv.fullName} (${inv.inviteCode}) was already accepted. Deleting it will also remove that teacher's profile and revoke their school access."
                    } else {
                        "Delete pending invitation ${inv.inviteCode}?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTeacherInvitation(inv)
                        deletingInvite = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deletingInvite = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InviteTeacherDialog(
    onDismiss: () -> Unit,
    onInviteLink: (String, String, String, TeacherPermissions) -> Unit,
    onInviteCredentials: (String, String, String, String, String, TeacherPermissions) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0 = Username & Code, 1 = Invite Link
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("teacher.${(100..999).random()}") }
    var password by remember { mutableStateOf("Pass${(1000..9999).random()}") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var perms by remember { mutableStateOf(TeacherPermissions()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Invite Teacher") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = mode) {
                    Tab(
                        selected = mode == 0,
                        onClick = { mode = 0; validationError = null },
                        text = { Text("Username & Code") }
                    )
                    Tab(
                        selected = mode == 1,
                        onClick = { mode = 1; validationError = null },
                        text = { Text("Invite Link") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (mode == 0) {
                    Text(
                        text = "Generate a Username and Password/Code for the teacher. When they open the app and tap 'Join as Teacher', they can enter these credentials and specify their name to join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' }.take(40)
                                validationError = null
                            },
                            label = { Text("Teacher Username (e.g. ali.science)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("dialog_teacher_username_input")
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { username = "teacher.${(100..999).random()}" }
                        ) {
                            Text("New")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it.take(100)
                                validationError = null
                            },
                            label = { Text("Password / Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("dialog_teacher_password_input")
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { password = "Pass${(1000..9999).random()}" }
                        ) {
                            Text("New")
                        }
                    }
                } else {
                    Text(
                        text = "Create an open shareable invite link for WhatsApp or any platform. Tapping the link opens BL School Hub directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text(if (mode == 0) "Teacher Full Name (Optional)" else "Teacher Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_teacher_name_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(160) },
                    label = { Text("Restrict to Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_teacher_email_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(40) },
                    label = { Text("Phone Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_teacher_phone_input")
                )

                if (validationError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(10.dp))
                PermissionSwitches(perms) { perms = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mode == 0) {
                        val cleanUser = username.trim().removePrefix("@").trim().lowercase()
                        val cleanPass = password.trim()
                        if (cleanUser.length < 3) {
                            validationError = "Username must be at least 3 characters."
                            return@Button
                        }
                        if (cleanUser.length > 40) {
                            validationError = "Username must be 40 characters or fewer."
                            return@Button
                        }
                        if (cleanPass.length < 4) {
                            validationError = "Password / Code must be at least 4 characters."
                            return@Button
                        }
                        if (cleanPass.length > 100) {
                            validationError = "Password / Code must be 100 characters or fewer."
                            return@Button
                        }
                        onInviteCredentials(name.trim(), email.trim(), phone.trim(), cleanUser, cleanPass, perms)
                    } else {
                        onInviteLink(name.trim(), email.trim(), phone.trim(), perms)
                    }
                },
                modifier = Modifier.testTag("confirm_create_teacher_invite")
            ) {
                Text(if (mode == 0) "Generate Username & Code" else "Create Invite Link")
            }
        },
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
