package com.example.ui.screens.shared

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassSection
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveOrange
import com.example.ui.theme.LeaveOrangeBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceWorkspacePanel(
    state: HubUiState,
    viewModel: HubViewModel,
    fixedSection: ClassSection? = null
) {
    val isPrincipal = state.role == UserRole.PRINCIPAL
    val canMark = isPrincipal || state.currentTeacherPermissions.markAttendance
    val canEdit = isPrincipal || state.currentTeacherPermissions.editAttendance

    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedSection by remember(fixedSection, state.sessionSections) {
        mutableStateOf(fixedSection ?: state.sessionSections.firstOrNull())
    }
    var attendanceDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var subject by remember { mutableStateOf("General") }
    var sessionSlot by remember { mutableStateOf("Morning") }
    var editingSession by remember { mutableStateOf<AttendanceSession?>(null) }

    val sectionStudents = remember(state.students, selectedSection) {
        val secId = selectedSection?.id ?: return@remember emptyList()
        state.students.filter { it.sectionId == secId && it.status == "active" }
    }

    val statusMap = remember { mutableStateMapOf<String, AttendanceStatus>() }

    // Sync default or editing session values
    LaunchedEffect(sectionStudents, editingSession) {
        statusMap.clear()
        val editSess = editingSession
        if (editSess != null) {
            attendanceDate = editSess.attendanceDate
            subject = editSess.subject
            sessionSlot = editSess.sessionSlot
            val existingRecs = state.attendanceRecords.filter { it.attendanceSessionId == editSess.id }
                .associateBy { it.studentId }
            sectionStudents.forEach { stu ->
                statusMap[stu.id] = existingRecs[stu.id]?.let { AttendanceStatus.fromDb(it.status) }
                    ?: AttendanceStatus.PRESENT
            }
        } else {
            sectionStudents.forEach { stu -> statusMap[stu.id] = AttendanceStatus.PRESENT }
        }
    }

    // Check if an accidental exact duplicate exists (same section + date + subject + slot) when creating new
    val exactMatchSession = remember(state.attendanceSessions, selectedSection, attendanceDate, subject, sessionSlot) {
        val secId = selectedSection?.id ?: return@remember null
        state.attendanceSessions.find {
            it.sectionId == secId &&
                it.attendanceDate == attendanceDate &&
                it.subject.equals(subject.trim(), ignoreCase = true) &&
                it.sessionSlot.equals(sessionSlot.trim(), ignoreCase = true)
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
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Mark Attendance") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Attendance History") })
            }
            Spacer(Modifier.height(14.dp))

            if (tabIndex == 0) {
                if (state.sessionSections.isEmpty() && fixedSection == null) {
                    EmptyStateCard(
                        icon = Icons.Rounded.EventAvailable,
                        title = "No Class Sections Found",
                        description = "Create a class and section in the active Academic Session to record attendance."
                    )
                } else {
                    SectionCard(
                        title = if (editingSession != null) "Editing Attendance Record" else "Attendance Session Setup",
                        trailing = {
                            if (editingSession != null) {
                                OutlinedButton(onClick = { editingSession = null }) { Text("New Session") }
                            }
                        }
                    ) {
                        if (fixedSection == null) {
                            Text("Class & Section", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.sessionSections.forEach { sec ->
                                    val cName = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                                    FilterChip(
                                        selected = selectedSection?.id == sec.id,
                                        onClick = {
                                            editingSession = null
                                            selectedSection = sec
                                        },
                                        label = { Text("$cName ${sec.name}") }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = attendanceDate,
                                onValueChange = { attendanceDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("attendance_date_input")
                            )
                            OutlinedTextField(
                                value = subject,
                                onValueChange = { subject = it },
                                label = { Text("Subject") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("attendance_subject_input")
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Attendance Session Slot (Multiple sessions per day supported)", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Morning", "Period 1", "Period 2", "Period 3", "Period 4", "Afternoon").forEach { slot ->
                                FilterChip(
                                    selected = sessionSlot.equals(slot, ignoreCase = true),
                                    onClick = { sessionSlot = slot },
                                    label = { Text(slot) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = sessionSlot,
                            onValueChange = { sessionSlot = it },
                            label = { Text("Custom Session Slot") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("attendance_slot_input")
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Recorded by: ${state.currentUser?.fullName.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalBlue
                        )

                        if (editingSession == null && exactMatchSession != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = LeaveOrangeBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "A record for $subject ($sessionSlot) on $attendanceDate already exists. Choose a different Subject/Slot for another session today, or edit it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LeaveOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (canEdit) {
                                        OutlinedButton(onClick = { editingSession = exactMatchSession }) {
                                            Text("Load & Edit")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    if (sectionStudents.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.EventAvailable,
                            title = "No students in this section",
                            description = "Register students into this section before taking attendance."
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { sectionStudents.forEach { statusMap[it.id] = AttendanceStatus.PRESENT } },
                                modifier = Modifier.weight(1f).testTag("mark_all_present")
                            ) { Text("Mark All Present", color = PresentGreen) }
                            OutlinedButton(
                                onClick = { sectionStudents.forEach { statusMap[it.id] = AttendanceStatus.ABSENT } },
                                modifier = Modifier.weight(1f).testTag("mark_all_absent")
                            ) { Text("Mark All Absent", color = AbsentRed) }
                        }

                        Spacer(Modifier.height(10.dp))
                        sectionStudents.forEach { stu ->
                            val currentStatus = statusMap[stu.id] ?: AttendanceStatus.PRESENT
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stu.fullName, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                        Text("Roll #${stu.rollNumber}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AttendanceTogglePill(
                                            label = "P",
                                            selected = currentStatus == AttendanceStatus.PRESENT,
                                            selectedColor = PresentGreen,
                                            onClick = { statusMap[stu.id] = AttendanceStatus.PRESENT }
                                        )
                                        AttendanceTogglePill(
                                            label = "A",
                                            selected = currentStatus == AttendanceStatus.ABSENT,
                                            selectedColor = AbsentRed,
                                            onClick = { statusMap[stu.id] = AttendanceStatus.ABSENT }
                                        )
                                        AttendanceTogglePill(
                                            label = "L",
                                            selected = currentStatus == AttendanceStatus.LEAVE,
                                            selectedColor = LeaveOrange,
                                            onClick = { statusMap[stu.id] = AttendanceStatus.LEAVE }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        val saveEnabled = (editingSession == null && canMark && exactMatchSession == null) ||
                            (editingSession != null && canEdit)
                        Button(
                            enabled = saveEnabled,
                            onClick = {
                                val sec = selectedSection ?: return@Button
                                viewModel.saveAttendance(
                                    existingSessionId = editingSession?.id,
                                    section = sec,
                                    attendanceDate = attendanceDate,
                                    subject = subject,
                                    sessionSlot = sessionSlot,
                                    statusByStudentId = statusMap.toMap()
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_attendance_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (editingSession != null) "Update Attendance" else "Save Attendance")
                        }
                    }
                }
            } else {
                // Attendance History with Filters
                AttendanceHistoryPanel(
                    state = state,
                    fixedSection = fixedSection,
                    canEdit = canEdit,
                    onEditSession = { sess ->
                        selectedSection = state.sections.find { it.id == sess.sectionId }
                        editingSession = sess
                        tabIndex = 0
                    }
                )
            }
        }
    }
}

@Composable
private fun AttendanceTogglePill(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColor,
            selectedLabelColor = Color.White
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceHistoryPanel(
    state: HubUiState,
    fixedSection: ClassSection?,
    canEdit: Boolean,
    onEditSession: (AttendanceSession) -> Unit
) {
    var datePreset by remember { mutableStateOf("All") }
    var subjectFilter by remember { mutableStateOf("") }
    var slotFilter by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    val today = LocalDate.now()
    val filteredSessions = remember(
        state.attendanceSessions, state.activeAcademicSession, fixedSection,
        datePreset, subjectFilter, slotFilter, startDate, endDate
    ) {
        state.attendanceSessions.filter { s ->
            val secOk = fixedSection == null || s.sectionId == fixedSection.id
            val sessOk = state.activeAcademicSession == null || s.sessionId == state.activeAcademicSession!!.id
            val dateOk = when (datePreset) {
                "Today" -> s.attendanceDate == today.toString()
                "Yesterday" -> s.attendanceDate == today.minusDays(1).toString()
                "This Week" -> s.attendanceDate >= today.minusDays(7).toString()
                "This Month" -> s.attendanceDate.startsWith(today.toString().take(7))
                "Custom" -> (startDate.isBlank() || s.attendanceDate >= startDate) &&
                    (endDate.isBlank() || s.attendanceDate <= endDate)
                else -> true
            }
            val subjOk = subjectFilter.isBlank() || s.subject.contains(subjectFilter, ignoreCase = true)
            val slotOk = slotFilter.isBlank() || s.sessionSlot.contains(slotFilter, ignoreCase = true)
            secOk && sessOk && dateOk && subjOk && slotOk
        }
    }

    SectionCard(title = "Filter History") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Today", "Yesterday", "This Week", "This Month", "Custom").forEach { preset ->
                FilterChip(selected = datePreset == preset, onClick = { datePreset = preset }, label = { Text(preset) })
            }
        }
        if (datePreset == "Custom") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("From (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("To (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = subjectFilter, onValueChange = { subjectFilter = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = slotFilter, onValueChange = { slotFilter = it }, label = { Text("Session Slot") }, modifier = Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(12.dp))
    if (filteredSessions.isEmpty()) {
        EmptyStateCard(Icons.Rounded.History, "No attendance sessions found", "Try adjusting your filters or record attendance.")
    } else {
        filteredSessions.forEach { sess ->
            val recs = state.attendanceRecords.filter { it.attendanceSessionId == sess.id }
            val p = recs.count { it.status == AttendanceStatus.PRESENT.dbValue }
            val a = recs.count { it.status == AttendanceStatus.ABSENT.dbValue }
            val l = recs.count { it.status == AttendanceStatus.LEAVE.dbValue }
            val editedCount = recs.sumOf { it.editHistory.size }
            val sec = state.sections.find { it.id == sess.sectionId }
            val cls = state.classes.find { it.id == sess.classId }

            SectionCard(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${sess.subject} • ${sess.sessionSlot}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                        Text("${cls?.name.orEmpty()}-${sec?.name.orEmpty()} • ${sess.attendanceDate} • By ${sess.teacherName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (canEdit) {
                        IconButton(onClick = { onEditSession(sess) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit attendance", tint = RoyalBlue)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill("Present: $p", PresentGreen, PresentGreenBg)
                    StatusPill("Absent: $a", AbsentRed, AbsentRedBg)
                    StatusPill("Leave: $l", LeaveOrange, LeaveOrangeBg)
                    if (editedCount > 0) {
                        StatusPill("$editedCount Edits", RoyalBlue, SkySoft)
                    }
                }
            }
        }
    }
}
