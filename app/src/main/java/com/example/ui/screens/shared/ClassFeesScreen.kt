package com.example.ui.screens.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCalculators
import com.example.data.model.ClassFeeDrive
import com.example.data.model.ClassSection
import com.example.data.model.SchoolClass
import com.example.data.model.UserRole
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

private val QUICK_FEE_SUGGESTIONS = listOf(
    "Annual Function Fee",
    "Monthly Tuition Fee",
    "Exam Fee",
    "Sports Day Fee",
    "Picnic / Study Tour",
    "Books & Uniform"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassFeesPanel(
    state: HubUiState,
    viewModel: HubViewModel,
    schoolClass: SchoolClass?,
    section: ClassSection,
    scrollable: Boolean = true
) {
    val context = LocalContext.current
    val isStaff = state.role == UserRole.PRINCIPAL || state.role == UserRole.TEACHER

    val sectionFees = remember(state.classFees, section.id) {
        state.classFees.filter { it.sectionId == section.id }
    }
    val sectionStudents = remember(state.students, section.id) {
        AcademicCalculators.sortStudentsByRoll(
            state.students.filter { it.sectionId == section.id && it.status == "active" }
        )
    }

    var isCreatingNew by remember(section.id) { mutableStateOf(false) }
    var selectedFeeId by remember(section.id) { mutableStateOf<String?>(null) }

    val selectedFee: ClassFeeDrive? = remember(sectionFees, selectedFeeId, isCreatingNew) {
        if (isCreatingNew) {
            null
        } else {
            sectionFees.find { it.id == selectedFeeId } ?: sectionFees.firstOrNull()
        }
    }

    var feeTitle by remember(section.id, selectedFee?.id, isCreatingNew) {
        mutableStateOf(selectedFee?.title ?: "")
    }
    var feeAmount by remember(section.id, selectedFee?.id, isCreatingNew) {
        mutableStateOf(selectedFee?.amountText ?: "")
    }
    var feeDate by remember(section.id, selectedFee?.id, isCreatingNew) {
        mutableStateOf(selectedFee?.feeDate ?: AcademicCalculators.todayDisplayDate())
    }
    var feeDescription by remember(section.id, selectedFee?.id, isCreatingNew) {
        mutableStateOf(selectedFee?.description ?: "")
    }
    var inlineValidationError by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf("all") } // all | paid | unpaid
    var searchQuery by remember { mutableStateOf("") }
    var pendingDeleteFee by remember { mutableStateOf<ClassFeeDrive?>(null) }

    LaunchedEffect(sectionFees) {
        if (!isCreatingNew && selectedFeeId != null && sectionFees.none { it.id == selectedFeeId }) {
            selectedFeeId = sectionFees.firstOrNull()?.id
        }
    }

    val paidIdSet = remember(selectedFee?.paidStudentIds) {
        selectedFee?.paidStudentIds?.toSet().orEmpty()
    }
    val totalStudents = sectionStudents.size
    val paidCount = remember(sectionStudents, paidIdSet) {
        sectionStudents.count { it.id in paidIdSet }
    }
    val unpaidCount = (totalStudents - paidCount).coerceAtLeast(0)

    val filteredStudents = remember(sectionStudents, paidIdSet, statusFilter, searchQuery) {
        val q = searchQuery.trim()
        sectionStudents.filter { stu ->
            val isPaid = stu.id in paidIdSet
            val statusOk = when (statusFilter) {
                "paid" -> isPaid
                "unpaid" -> !isPaid
                else -> true
            }
            val queryOk = q.isBlank() ||
                stu.fullName.contains(q, ignoreCase = true) ||
                stu.rollNumber.contains(q, ignoreCase = true) ||
                stu.fatherName.contains(q, ignoreCase = true)
            statusOk && queryOk
        }
    }

    val containerModifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    }

    Column(modifier = containerModifier) {
        // Switch between existing fee drives for this class or start a new one
        if (sectionFees.isNotEmpty() || isStaff) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sectionFees.forEach { fee ->
                    val feePaid = sectionStudents.count { it.id in fee.paidStudentIds }
                    val isSelected = !isCreatingNew && selectedFee?.id == fee.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            isCreatingNew = false
                            selectedFeeId = fee.id
                            inlineValidationError = null
                        },
                        label = { Text("${fee.title} ($feePaid/$totalStudents)") },
                        leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("fee_drive_chip_${fee.id}")
                    )
                }
                if (isStaff && sectionFees.isNotEmpty()) {
                    FilterChip(
                        selected = isCreatingNew,
                        onClick = {
                            isCreatingNew = true
                            selectedFeeId = null
                            feeTitle = ""
                            feeAmount = ""
                            feeDate = AcademicCalculators.todayDisplayDate()
                            feeDescription = ""
                            inlineValidationError = null
                        },
                        label = { Text("New Fee") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("new_fee_drive_chip")
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Top Fee Details Card (Principal / Teacher writes which fee this is)
        if (isStaff) {
            SectionCard(
                title = if (selectedFee == null) {
                    "Fee Details — ${schoolClass?.name ?: "Class"} (${section.name})"
                } else {
                    "Fee Details: ${selectedFee.title}"
                },
                trailing = {
                    if (selectedFee != null) {
                        TextButton(
                            onClick = { pendingDeleteFee = selectedFee },
                            colors = ButtonDefaults.textButtonColors(contentColor = AbsentRed),
                            modifier = Modifier.testTag("delete_fee_drive_button")
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Fee", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            ) {
                Text(
                    text = "Write what this fee is for (e.g. Annual Function, Monthly Fee, Exam Fee), then mark Paid or Unpaid in front of each student below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QUICK_FEE_SUGGESTIONS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                feeTitle = preset
                                inlineValidationError = null
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = feeTitle,
                    onValueChange = {
                        feeTitle = it
                        if (it.isNotBlank()) inlineValidationError = null
                    },
                    label = { Text("Fee Purpose / Detail * (e.g. Annual Function)") },
                    isError = inlineValidationError != null,
                    supportingText = inlineValidationError?.let { err ->
                        { Text(err, color = AbsentRed) }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fee_title_input")
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = feeAmount,
                        onValueChange = { feeAmount = it },
                        label = { Text("Amount (Optional)") },
                        placeholder = { Text("e.g. Rs. 500") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fee_amount_input")
                    )
                    OutlinedTextField(
                        value = feeDate,
                        onValueChange = { feeDate = it },
                        label = { Text("Date") },
                        placeholder = { Text("DD-MM-YYYY") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fee_date_input")
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = feeDescription,
                    onValueChange = { feeDescription = it },
                    label = { Text("Extra Note / Details (Optional)") },
                    placeholder = { Text("Any additional instructions or due date info") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fee_description_input")
                )
                Spacer(Modifier.height(10.dp))

                val hasChanges = selectedFee == null ||
                    feeTitle.trim() != selectedFee.title ||
                    feeAmount.trim() != selectedFee.amountText ||
                    feeDate.trim() != selectedFee.feeDate ||
                    feeDescription.trim() != selectedFee.description

                Button(
                    onClick = {
                        if (feeTitle.trim().isBlank()) {
                            inlineValidationError = "Please enter what this fee is for (e.g. Annual Function)."
                        } else {
                            inlineValidationError = null
                            viewModel.saveClassFeeDrive(
                                existingFeeId = selectedFee?.id,
                                section = section,
                                title = feeTitle,
                                description = feeDescription,
                                amountText = feeAmount,
                                feeDate = feeDate
                            ) { saved ->
                                isCreatingNew = false
                                selectedFeeId = saved.id
                            }
                        }
                    },
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("save_fee_detail_button")
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (selectedFee == null) "Save Fee Detail" else "Update Fee Detail")
                }
            }
        } else {
            if (selectedFee == null) {
                EmptyStateCard(
                    icon = Icons.Rounded.Payments,
                    title = "No Class Fees Posted",
                    description = "Fee details created by the Principal or Teacher for ${schoolClass?.name ?: "this class"} — Section ${section.name} will appear here."
                )
                return@Column
            } else {
                SectionCard(title = selectedFee.title) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (selectedFee.amountText.isNotBlank()) {
                                Text(
                                    text = "Amount: ${selectedFee.amountText}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = RoyalBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Date: ${selectedFee.feeDate} • Added by ${selectedFee.createdByName.ifBlank { "Staff" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedFee.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(selectedFee.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StatusPill("READ ONLY", RoyalBlue, SkySoft)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Summary Bar & Search / Filter
        SectionCard(title = "Class Fee Status (${sectionStudents.size} Students)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill("Total: $totalStudents", NavyPrimary, SkySoft)
                StatusPill("Paid: $paidCount", PresentGreen, PresentGreenBg)
                StatusPill("Unpaid: $unpaidCount", AbsentRed, AbsentRedBg)
            }
            if (totalStudents > 0) {
                Spacer(Modifier.height(8.dp))
                val progress = paidCount.toFloat() / totalStudents.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = PresentGreen,
                    trackColor = AbsentRedBg
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search student name or roll #") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fee_student_search")
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = statusFilter == "all",
                    onClick = { statusFilter = "all" },
                    label = { Text("All ($totalStudents)") },
                    modifier = Modifier.testTag("fee_filter_all")
                )
                FilterChip(
                    selected = statusFilter == "unpaid",
                    onClick = { statusFilter = "unpaid" },
                    label = { Text("Unpaid ($unpaidCount)") },
                    modifier = Modifier.testTag("fee_filter_unpaid")
                )
                FilterChip(
                    selected = statusFilter == "paid",
                    onClick = { statusFilter = "paid" },
                    label = { Text("Paid ($paidCount)") },
                    modifier = Modifier.testTag("fee_filter_paid")
                )
            }

            if (isStaff && selectedFee != null && sectionStudents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Roll No", "Student Name", "Father Name", "Fee Status", "Details")
                            val rows = sectionStudents.map { stu ->
                                val paid = stu.id in paidIdSet
                                listOf(
                                    stu.rollNumber,
                                    stu.fullName,
                                    stu.fatherName,
                                    if (paid) "PAID" else "UNPAID",
                                    selectedFee.paidDetails[stu.id].orEmpty()
                                )
                            }
                            viewModel.exportPdfAndShare(
                                context = context,
                                title = "Class Fee Report: ${selectedFee.title}",
                                subtitle = "${schoolClass?.name ?: "Class"} Section ${section.name} • ${selectedFee.amountText.ifBlank { "Class Fee" }} • Paid $paidCount/$totalStudents",
                                headers = headers,
                                rows = rows
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Roll No", "Student Name", "Father Name", "Fee Status", "Details")
                            val rows = sectionStudents.map { stu ->
                                val paid = stu.id in paidIdSet
                                listOf(
                                    stu.rollNumber,
                                    stu.fullName,
                                    stu.fatherName,
                                    if (paid) "PAID" else "UNPAID",
                                    selectedFee.paidDetails[stu.id].orEmpty()
                                )
                            }
                            viewModel.exportCsvAndShare(
                                context = context,
                                title = "class_fee_${section.name}_${selectedFee.title}",
                                headers = headers,
                                rows = rows
                            )
                        }
                    ) {
                        Text("Excel/CSV")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Student Checklist
        if (sectionStudents.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Rounded.Groups,
                title = "No Students in This Class",
                description = "Add students in the Students tab first, then all students of this class will appear here to mark Paid / Unpaid."
            )
        } else if (filteredStudents.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Rounded.Search,
                title = "No Matching Students",
                description = "No students match your current filter or search."
            )
        } else {
            filteredStudents.forEach { stu ->
                val isPaid = stu.id in paidIdSet
                val paidInfo = selectedFee?.paidDetails?.get(stu.id).orEmpty()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("fee_student_row_${stu.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPaid) PresentGreenBg.copy(alpha = 0.45f) else Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isPaid) PresentGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SkySoft
                            ) {
                                Text(
                                    text = "#${stu.rollNumber}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stu.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Father: ${stu.fatherName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isPaid && paidInfo.isNotBlank()) {
                                    Text(
                                        text = paidInfo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PresentGreen
                                    )
                                } else if (!isPaid) {
                                    Text(
                                        text = "Unpaid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AbsentRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        if (isStaff) {
                            if (isPaid) {
                                Button(
                                    onClick = {
                                        selectedFee?.let { fee ->
                                            viewModel.toggleStudentFeePaidStatus(
                                                feeId = fee.id,
                                                studentId = stu.id,
                                                markPaid = false
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PresentGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("fee_toggle_${stu.id}")
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Paid", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Paid")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val activeFee = selectedFee
                                        if (activeFee != null) {
                                            viewModel.toggleStudentFeePaidStatus(
                                                feeId = activeFee.id,
                                                studentId = stu.id,
                                                markPaid = true
                                            )
                                        } else if (feeTitle.trim().isBlank()) {
                                            inlineValidationError = "First enter fee detail above (e.g. Annual Function) before marking Paid."
                                        } else {
                                            inlineValidationError = null
                                            viewModel.saveClassFeeDrive(
                                                existingFeeId = null,
                                                section = section,
                                                title = feeTitle,
                                                description = feeDescription,
                                                amountText = feeAmount,
                                                feeDate = feeDate,
                                                initialPaidStudentIds = listOf(stu.id)
                                            ) { saved ->
                                                isCreatingNew = false
                                                selectedFeeId = saved.id
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AbsentRed
                                    ),
                                    border = BorderStroke(1.dp, AbsentRed.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("fee_toggle_${stu.id}")
                                ) {
                                    Text("Unpaid • Mark Paid")
                                }
                            }
                        } else {
                            StatusPill(
                                label = if (isPaid) "PAID" else "UNPAID",
                                textColor = if (isPaid) PresentGreen else AbsentRed,
                                backgroundColor = if (isPaid) PresentGreenBg else AbsentRedBg
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteFee != null) {
        val target = pendingDeleteFee!!
        AlertDialog(
            onDismissRequest = { pendingDeleteFee = null },
            title = { Text("Delete Fee Record?") },
            text = { Text("Are you sure you want to delete '${target.title}' and its Paid/Unpaid tracking for this class?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClassFeeDrive(target.id)
                        pendingDeleteFee = null
                        selectedFeeId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFee = null }) { Text("Cancel") }
            }
        )
    }
}
