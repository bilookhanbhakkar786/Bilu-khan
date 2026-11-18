package com.example.ui.screens.shared

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.ClassSection
import com.example.data.model.HomeworkItem
import com.example.data.model.TimetableSlot
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.screens.student.HomeworkReadOnlyDetailDialog
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
import java.time.LocalDate

private val WEEKDAY_SEQUENCE = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableAndHomeworkPanel(
    state: HubUiState,
    viewModel: HubViewModel,
    fixedSection: ClassSection? = null
) {
    val isStaff = state.role == UserRole.PRINCIPAL || state.role == UserRole.TEACHER
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedSection by remember(fixedSection, state.sessionSections, isStaff) {
        mutableStateOf(
            fixedSection ?: if (isStaff) state.sessionSections.firstOrNull() else null
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var activeHomework by remember { mutableStateOf<HomeworkItem?>(null) }
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var showAddHomeworkDialog by remember { mutableStateOf(false) }

    val activeSectionId = fixedSection?.id ?: selectedSection?.id
    val q = searchQuery.trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (fixedSection == null) 16.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Timetable") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Homework") })
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by subject, title, teacher, or weekday") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("timetable_hw_search")
            )
            Spacer(Modifier.height(8.dp))

            if (fixedSection == null && state.sessionSections.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == null,
                        onClick = { selectedSection = null },
                        label = { Text("All Classes") }
                    )
                    state.sessionSections.forEach { sec ->
                        val cls = state.classes.find { it.id == sec.classId }?.name.orEmpty()
                        FilterChip(
                            selected = selectedSection?.id == sec.id,
                            onClick = { selectedSection = sec },
                            label = { Text("$cls ${sec.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (tabIndex == 0) {
                if (isStaff && activeSectionId != null) {
                    Button(onClick = { showAddSlotDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Timetable Slot")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val slots = remember(state.timetables, state.classes, state.sections, activeSectionId, q) {
                    AcademicCalculators.sortTimetableSlots(
                        state.timetables.filter { slot ->
                            (activeSectionId == null || slot.sectionId == activeSectionId) &&
                                (q.isBlank() || slot.subject.contains(q, true) || slot.teacherName.contains(q, true) || slot.dayOfWeek.contains(q, true))
                        },
                        state.classes,
                        state.sections
                    )
                }
                if (slots.isEmpty()) {
                    EmptyStateCard(
                        Icons.Rounded.Schedule,
                        "No Timetable Slots",
                        if (isStaff) "Select a class above and add class periods by day, subject, time, teacher, and room."
                        else "Daily class timetable periods will appear here once scheduled."
                    )
                } else {
                    val grouped = slots.groupBy { it.dayOfWeek }
                    val orderedDays = grouped.keys.sortedBy { day ->
                        val idx = WEEKDAY_SEQUENCE.indexOfFirst { it.equals(day, ignoreCase = true) }
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }
                    orderedDays.forEach { day ->
                        val daySlots = grouped[day].orEmpty()
                        SectionCard(title = day, modifier = Modifier.padding(vertical = 4.dp)) {
                            daySlots.forEachIndexed { idx, slot ->
                                val sec = state.sections.find { it.id == slot.sectionId }
                                val cls = state.classes.find { it.id == sec?.classId }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val classPrefix = if (activeSectionId == null) "${cls?.name.orEmpty()}-${sec?.name.orEmpty()} • " else ""
                                        Text("$classPrefix${slot.subject}", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                        Text("Teacher: ${slot.teacherName.ifBlank { "Staff" }} • Room: ${slot.roomNumber.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill("${slot.startTime} - ${slot.endTime}", RoyalBlue, SkySoft)
                                    if (isStaff) {
                                        IconButton(onClick = { viewModel.deleteTimetableSlot(slot.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete slot", tint = AbsentRed)
                                        }
                                    }
                                }
                                if (idx < daySlots.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                if (isStaff && activeSectionId != null) {
                    Button(onClick = { showAddHomeworkDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Assign Homework")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val hwList = remember(state.homework, activeSectionId, q) {
                    state.homework.filter { hw ->
                        (activeSectionId == null || hw.sectionId == activeSectionId) &&
                            (q.isBlank() || hw.subject.contains(q, true) || hw.title.contains(q, true) ||
                                hw.instructions.contains(q, true) || hw.teacherName.contains(q, true))
                    }
                }
                if (hwList.isEmpty()) {
                    EmptyStateCard(Icons.Rounded.MenuBook, "No Homework Assigned", "Homework assignments with due dates and attachments appear here.")
                } else {
                    val today = remember { LocalDate.now() }
                    hwList.forEach { hw ->
                        val sec = state.sections.find { it.id == hw.sectionId }
                        val cls = state.classes.find { it.id == sec?.classId }
                        val dueParsed = AcademicCalculators.parseFlexibleDate(hw.dueDate)
                        val (dueLabel, dueFg, dueBg) = when {
                            dueParsed == null -> Triple("DUE", RoyalBlue, SkySoft)
                            dueParsed.isBefore(today) -> Triple("OVERDUE", AbsentRed, AbsentRedBg)
                            dueParsed.isEqual(today) -> Triple("DUE TODAY", WarningAmber, WarningAmberBg)
                            else -> Triple("ACTIVE", PresentGreen, PresentGreenBg)
                        }
                        SectionCard(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .clickable { activeHomework = hw }
                                .testTag("homework_card_${hw.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill("${hw.subject} • ${cls?.name.orEmpty()}-${sec?.name.orEmpty()}", RoyalBlue, SkySoft)
                                StatusPill(dueLabel, dueFg, dueBg)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(hw.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(hw.instructions, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "By ${hw.teacherName.ifBlank { "Teacher" }} • Due: ${AcademicCalculators.formatDisplayDate(hw.dueDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!hw.attachmentUrl.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.AttachFile, contentDescription = "Has Attachment", tint = RoyalBlue, modifier = Modifier.size(15.dp))
                                        Text("Tap for attachment", style = MaterialTheme.typography.labelSmall, color = RoyalBlue)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeHomework != null) {
        val hw = activeHomework!!
        val sec = state.sections.find { it.id == hw.sectionId }
        val cls = state.classes.find { it.id == sec?.classId }
        HomeworkReadOnlyDetailDialog(
            homework = hw,
            className = cls?.name.orEmpty(),
            sectionName = sec?.name.orEmpty(),
            onDismiss = { activeHomework = null }
        )
    }

    val targetSectionForAdd = fixedSection ?: selectedSection
    if (showAddSlotDialog && targetSectionForAdd != null) {
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        var day by remember { mutableStateOf("Monday") }
        var subject by remember { mutableStateOf("Mathematics") }
        var start by remember { mutableStateOf("08:30") }
        var end by remember { mutableStateOf("09:15") }
        var teacherName by remember { mutableStateOf(state.currentUser?.fullName.orEmpty()) }
        var room by remember { mutableStateOf("101") }

        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            title = { Text("Add Timetable Slot") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { d -> FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d.take(3)) }) }
                    }
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Teacher Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room Number") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createTimetableSlot(targetSectionForAdd, day, subject, start, end, teacherName, room)
                    showAddSlotDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddSlotDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddHomeworkDialog && targetSectionForAdd != null) {
        var subject by remember { mutableStateOf("English") }
        var title by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }
        var dueDate by remember { mutableStateOf(AcademicCalculators.plusDaysDisplayDate(2)) }
        var attachmentUri by remember { mutableStateOf<Uri?>(null) }

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) attachmentUri = it }

        AlertDialog(
            onDismissRequest = { showAddHomeworkDialog = false },
            title = { Text("Assign Homework") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("Instructions *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Submission Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (attachmentUri == null) "Attach File or Image (Optional)" else "Attachment Ready")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createHomework(targetSectionForAdd, subject, title, instructions, dueDate, attachmentUri)
                    showAddHomeworkDialog = false
                }) { Text("Assign") }
            },
            dismissButton = { TextButton(onClick = { showAddHomeworkDialog = false }) { Text("Cancel") } }
        )
    }
}
