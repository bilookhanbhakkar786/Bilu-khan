package com.example.ui.screens.shared

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassSection
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.HubUiState
import com.example.ui.viewmodel.HubViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableAndHomeworkPanel(
    state: HubUiState,
    viewModel: HubViewModel,
    fixedSection: ClassSection? = null
) {
    val isStaff = state.role == UserRole.PRINCIPAL || state.role == UserRole.TEACHER
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedSection by remember(fixedSection, state.sessionSections) {
        mutableStateOf(fixedSection ?: state.sessionSections.firstOrNull())
    }
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var showAddHomeworkDialog by remember { mutableStateOf(false) }

    val activeSectionId = fixedSection?.id ?: selectedSection?.id

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
            Spacer(Modifier.height(12.dp))

            if (fixedSection == null && state.sessionSections.isNotEmpty()) {
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
                val slots = state.timetables.filter { activeSectionId == null || it.sectionId == activeSectionId }
                if (slots.isEmpty()) {
                    EmptyStateCard(Icons.Rounded.Schedule, "No Timetable Slots", "Add class periods by day, subject, time, teacher, and room.")
                } else {
                    slots.groupBy { it.dayOfWeek }.forEach { (day, daySlots) ->
                        SectionCard(title = day, modifier = Modifier.padding(vertical = 4.dp)) {
                            daySlots.forEach { slot ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${slot.subject} (${slot.startTime} - ${slot.endTime})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                        Text("Teacher: ${slot.teacherName} • Room: ${slot.roomNumber}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (isStaff) {
                                        IconButton(onClick = { viewModel.deleteTimetableSlot(slot.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete slot", tint = AbsentRed)
                                        }
                                    }
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
                val hwList = state.homework.filter { activeSectionId == null || it.sectionId == activeSectionId }
                if (hwList.isEmpty()) {
                    EmptyStateCard(Icons.Rounded.MenuBook, "No Homework Assigned", "Homework assignments with due dates and attachments appear here.")
                } else {
                    hwList.forEach { hw ->
                        SectionCard(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusPill(hw.subject, RoyalBlue, SkySoft)
                                Text("Due: ${hw.dueDate}", style = MaterialTheme.typography.labelSmall, color = AbsentRed)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(hw.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                            Text(hw.instructions, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("Assigned by ${hw.teacherName}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddSlotDialog && selectedSection != null) {
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
                    viewModel.createTimetableSlot(selectedSection!!, day, subject, start, end, teacherName, room)
                    showAddSlotDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddSlotDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddHomeworkDialog && selectedSection != null) {
        var subject by remember { mutableStateOf("English") }
        var title by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }
        var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(2).toString()) }
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
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Submission Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (attachmentUri == null) "Attach File or Image (Optional)" else "Attachment Ready")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createHomework(selectedSection!!, subject, title, instructions, dueDate, attachmentUri)
                    showAddHomeworkDialog = false
                }) { Text("Assign") }
            },
            dismissButton = { TextButton(onClick = { showAddHomeworkDialog = false }) { Text("Cancel") } }
        )
    }
}
