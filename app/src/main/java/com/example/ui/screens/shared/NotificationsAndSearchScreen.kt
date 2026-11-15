package com.example.ui.screens.shared

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
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
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
fun GlobalSearchScreen(state: HubUiState) {
    var query by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var selectedSessionId by remember { mutableStateOf(state.activeAcademicSession?.id) }
    var selectedSectionId by remember { mutableStateOf<String?>(null) }

    val isStaff = state.role == UserRole.PRINCIPAL || state.role == UserRole.TEACHER
    val q = query.trim()

    val matchedStudents = remember(state.students, query, selectedSessionId, selectedSectionId, isStaff) {
        if (!isStaff || (state.role == UserRole.TEACHER && !state.currentTeacherPermissions.viewStudents)) {
            emptyList()
        } else {
            state.students.filter { s ->
                (selectedSessionId == null || s.sessionId == selectedSessionId) &&
                    (selectedSectionId == null || s.sectionId == selectedSectionId) &&
                    (q.isBlank() || s.fullName.contains(q, true) || s.rollNumber.contains(q, true) || s.studentCode.contains(q, true))
            }
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

    val matchedTests = remember(state.tests, query, subjectFilter, dateFilter, selectedSessionId, selectedSectionId, isStaff) {
        state.tests.filter { t ->
            val roleAllowed = isStaff || (t.status == "published" && t.sectionId == state.linkedStudentRecord?.sectionId)
            roleAllowed &&
                (selectedSessionId == null || t.sessionId == selectedSessionId) &&
                (selectedSectionId == null || t.sectionId == selectedSectionId) &&
                (subjectFilter.isBlank() || t.subject.contains(subjectFilter, true)) &&
                (dateFilter.isBlank() || t.testDate == dateFilter) &&
                (q.isBlank() || t.name.contains(q, true) || t.subject.contains(q, true))
        }
    }

    val matchedAnnouncements = remember(state.announcements, query, dateFilter) {
        state.announcements.filter { a ->
            (dateFilter.isBlank() || a.createdAt.toDateOnlyString().startsWith(dateFilter)) &&
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
                    label = { Text("Search students, roll #, teachers, classes, tests, announcements") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subjectFilter, onValueChange = { subjectFilter = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dateFilter, onValueChange = { dateFilter = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
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
                SectionCard(title = "Students (${matchedStudents.size})") {
                    matchedStudents.take(15).forEach { s ->
                        Text("• ${s.fullName} (Roll #${s.rollNumber}, ID: ${s.studentCode})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
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
            if (matchedTests.isNotEmpty()) {
                SectionCard(title = "Tests (${matchedTests.size})") {
                    matchedTests.take(15).forEach { t ->
                        Text("• ${t.name} (${t.subject} • ${t.testDate} • ${t.status})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
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
}

@Composable
fun ProfileAndSettingsScreen(
    state: HubUiState,
    authViewModel: AuthViewModel
) {
    val user = state.currentUser
    val school = state.school
    var fullName by remember(user?.fullName) { mutableStateOf(user?.fullName.orEmpty()) }
    var phone by remember(user?.phone) { mutableStateOf(user?.phone.orEmpty()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

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
}
