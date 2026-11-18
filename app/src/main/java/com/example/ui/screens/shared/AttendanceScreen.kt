package com.example.ui.screens.shared

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
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
import com.example.data.model.AcademicCalculators
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
    var attendanceDate by remember { mutableStateOf(AcademicCalculators.todayDisplayDate()) }
    var subject by remember { mutableStateOf("General") }
    var sessionSlot by remember { mutableStateOf("Morning") }
    var editingSession by remember { mutableStateOf<AttendanceSession?>(null) }
    var showAddLectureDialog by remember { mutableStateOf(false) }

    val sectionStudents = remember(state.students, selectedSection) {
        val secId = selectedSection?.id ?: return@remember emptyList()
        AcademicCalculators.sortStudentsByRoll(
            state.students.filter { it.sectionId == secId && it.status == "active" }
        )
    }

    val statusMap = remember { mutableStateMapOf<String, AttendanceStatus>() }

    // Sync default or editing session values
    LaunchedEffect(sectionStudents, editingSession) {
        statusMap.clear()
        val editSess = editingSession
        if (editSess != null) {
            attendanceDate = AcademicCalculators.formatDisplayDate(editSess.attendanceDate)
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
    val normalizedDate = AcademicCalculators.formatDisplayDate(attendanceDate)
    val exactMatchSession = remember(state.attendanceSessions, selectedSection, normalizedDate, subject, sessionSlot) {
        val secId = selectedSection?.id ?: return@remember null
        state.attendanceSessions.find {
            it.sectionId == secId &&
                AcademicCalculators.formatDisplayDate(it.attendanceDate) == normalizedDate &&
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
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("📊 % Filter") })
            }
            Spacer(Modifier.height(14.dp))

            when (tabIndex) {
                0 -> {
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
                                    label = { Text("Date (DD-MM-YYYY)") },
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
                            val currentSecObj = selectedSection?.let { sel -> state.sections.find { it.id == sel.id } ?: sel }
                            val knownSlots = remember(currentSecObj?.customLectures, sessionSlot) {
                                val combined = AcademicCalculators.DEFAULT_LECTURE_SLOTS +
                                    currentSecObj?.customLectures.orEmpty() +
                                    listOfNotNull(sessionSlot.trim().takeIf { it.isNotBlank() })
                                AcademicCalculators.sortLectureSlots(combined)
                            }
                            Text("Period / Lecture Slots (Morning + 6 Lectures default)", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                knownSlots.forEach { slot ->
                                    FilterChip(
                                        selected = sessionSlot.equals(slot, ignoreCase = true),
                                        onClick = { sessionSlot = slot },
                                        label = { Text(slot) }
                                    )
                                }
                                if (currentSecObj != null) {
                                    AssistChip(
                                        onClick = { showAddLectureDialog = true },
                                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                        label = { Text("Add More Lecture") },
                                        modifier = Modifier.testTag("add_more_lecture_button")
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = sessionSlot,
                                onValueChange = { sessionSlot = it },
                                label = { Text("Selected / Custom Lecture Slot") },
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
                }
                1 -> {
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
                2 -> {
                    AttendancePercentageFilterComponent(state = state)
                }
            }
        }
    }

    if (showAddLectureDialog && selectedSection != null) {
        val liveSec = state.sections.find { it.id == selectedSection!!.id } ?: selectedSection!!
        val nextNum = 7 + liveSec.customLectures.size
        var lectureName by remember(liveSec.id, nextNum) { mutableStateOf("Lecture $nextNum") }
        AlertDialog(
            onDismissRequest = { showAddLectureDialog = false },
            title = { Text("Add More Lecture") },
            text = {
                Column {
                    Text(
                        text = "Add another lecture period slot for ${liveSec.name} (default includes Morning and Lectures 1–6).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lectureName,
                        onValueChange = { lectureName = it },
                        label = { Text("Lecture Slot Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_lecture_name_input")
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = lectureName.trim()
                    if (clean.isNotBlank()) {
                        viewModel.addSectionLectureSlot(liveSec.id, clean)
                        sessionSlot = clean
                    }
                    showAddLectureDialog = false
                }) { Text("Add Lecture") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLectureDialog = false }) { Text("Cancel") }
            }
        )
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
        val parsedStart = AcademicCalculators.parseFlexibleDate(startDate)
        val parsedEnd = AcademicCalculators.parseFlexibleDate(endDate)
        state.attendanceSessions.filter { s ->
            val secOk = fixedSection == null || s.sectionId == fixedSection.id
            val sessOk = state.activeAcademicSession == null || s.sessionId == state.activeAcademicSession!!.id
            val sessionDate = AcademicCalculators.parseFlexibleDate(s.attendanceDate)
            val dateOk = when (datePreset) {
                "Today" -> sessionDate == today
                "Yesterday" -> sessionDate == today.minusDays(1)
                "This Week" -> sessionDate != null && !sessionDate.isBefore(today.minusDays(7))
                "This Month" -> AcademicCalculators.isSameMonth(s.attendanceDate, today)
                "Custom" -> (parsedStart == null || (sessionDate != null && !sessionDate.isBefore(parsedStart))) &&
                    (parsedEnd == null || (sessionDate != null && !sessionDate.isAfter(parsedEnd)))
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
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("From (DD-MM-YYYY)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("To (DD-MM-YYYY)") }, modifier = Modifier.weight(1f))
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
                        Text(
                            "${cls?.name.orEmpty()}-${sec?.name.orEmpty()} • ${AcademicCalculators.formatDisplayDate(sess.attendanceDate)} • By ${sess.teacherName}",
                            style = MaterialTheme.typography.bodySmall
                        )
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

@Composable
fun AttendancePercentageFilterScreen(
    state: HubUiState,
    onNavigateToAttendance: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            AttendancePercentageFilterComponent(
                state = state,
                onNavigateToAttendance = onNavigateToAttendance
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendancePercentageFilterComponent(
    state: HubUiState,
    modifier: Modifier = Modifier,
    onNavigateToAttendance: (() -> Unit)? = null
) {
    var thresholdStr by remember { mutableStateOf("75") }
    var aboveSelected by remember { mutableStateOf(true) } // true = above or equal, false = below
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val threshold = thresholdStr.toDoubleOrNull() ?: 75.0

    val studentsList = remember(
        state.sessionStudents, state.classes, state.sections,
        state.attendanceSessions, state.attendanceRecords,
        threshold, aboveSelected, selectedClassId, searchQuery
    ) {
        val q = searchQuery.trim().lowercase()
        val mapped = state.sessionStudents
            .filter { stu ->
                val classMatches = selectedClassId == null || stu.classId == selectedClassId
                val searchMatches = q.isBlank() ||
                    stu.fullName.lowercase().contains(q) ||
                    stu.rollNumber.lowercase().contains(q) ||
                    stu.parentName.lowercase().contains(q)
                classMatches && searchMatches
            }
            .map { stu ->
                val summary = AcademicCalculators.computeStudentAttendanceSummary(stu.id, state.attendanceSessions, state.attendanceRecords)
                val total = summary.periodStats.totalCount
                val present = summary.periodStats.presentCount
                val percentage = if (total == 0) 0.0 else (present * 100.0) / total
                Triple(stu, summary, percentage)
            }
            .filter { (_, _, percentage) ->
                if (aboveSelected) percentage >= threshold else percentage < threshold
            }

        if (aboveSelected) {
            mapped.sortedByDescending { it.third }
        } else {
            mapped.sortedBy { it.third }
        }
    }

    SectionCard(
        title = "Attendance Percentage Filter",
        trailing = {
            if (onNavigateToAttendance != null) {
                TextButton(onClick = onNavigateToAttendance) {
                    Text("Full Register")
                }
            }
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = thresholdStr,
                onValueChange = { thresholdStr = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Threshold %") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("attendance_threshold_input")
            )
            FilterChip(
                selected = aboveSelected,
                onClick = { aboveSelected = true },
                label = { Text("≥ Above") },
                modifier = Modifier.testTag("filter_above_chip")
            )
            FilterChip(
                selected = !aboveSelected,
                onClick = { aboveSelected = false },
                label = { Text("< Below") },
                modifier = Modifier.testTag("filter_below_chip")
            )
        }

        Spacer(Modifier.height(8.dp))

        // Quick Preset Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Presets:", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
            listOf("50", "75", "85", "90").forEach { p ->
                AssistChip(
                    onClick = { thresholdStr = p },
                    label = { Text("$p%") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by student name or roll #") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("attendance_filter_search_input")
        )

        Spacer(Modifier.height(8.dp))
        Text("Filter by Class", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedClassId == null,
                onClick = { selectedClassId = null },
                label = { Text("All Classes") }
            )
            state.sessionClasses.forEach { cls ->
                FilterChip(
                    selected = selectedClassId == cls.id,
                    onClick = { selectedClassId = cls.id },
                    label = { Text(cls.name) }
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    if (studentsList.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.EventAvailable,
            title = "No students match this filter",
            description = "No students found with attendance ${if (aboveSelected) "≥" else "<"} $threshold%. Try adjusting the threshold or class."
        )
    } else {
        Text(
            text = "Showing ${studentsList.size} student(s) matching criteria (${if (aboveSelected) "≥" else "<"} $threshold%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        studentsList.forEach { (stu, summary, percentage) ->
            val cls = state.classes.find { it.id == stu.classId }
            val sec = state.sections.find { it.id == stu.sectionId }
            val pctStr = if (summary.periodStats.totalCount == 0) "No Sessions" else String.format("%.1f%%", percentage)
            val badgeColor = when {
                summary.periodStats.totalCount == 0 -> MaterialTheme.colorScheme.outline
                percentage >= 75.0 -> PresentGreen
                percentage >= 50.0 -> LeaveOrange
                else -> AbsentRed
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stu.fullName, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                            StatusPill(pctStr, badgeColor, badgeColor.copy(alpha = 0.15f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Class: ${cls?.name.orEmpty()} ${sec?.name.orEmpty()} • Roll #${stu.rollNumber} • Guardian: ${stu.parentName.ifBlank { "N/A" }} (${stu.parentContact.ifBlank { "N/A" }})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("Present: ${summary.periodStats.presentCount}", PresentGreen, PresentGreenBg)
                            StatusPill("Absent: ${summary.periodStats.absentCount}", AbsentRed, AbsentRedBg)
                            StatusPill("Leave: ${summary.periodStats.leaveCount}", LeaveOrange, LeaveOrangeBg)
                            StatusPill("Total Sessions: ${summary.periodStats.totalCount}", RoyalBlue, SkySoft)
                        }
                    }
                }
            }
        }
    }
}
