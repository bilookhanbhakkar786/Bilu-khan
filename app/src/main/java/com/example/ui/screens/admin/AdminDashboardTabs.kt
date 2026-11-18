package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardStats
import com.example.data.model.AppVisit
import com.example.data.model.DailyTrafficBucket
import com.example.data.model.SchoolAdminSummary
import com.example.data.model.UserProfile
import com.example.data.model.toDateOnlyString
import com.example.data.model.toDisplayDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveOrange
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg

@Composable
fun AdminOverviewTab(stats: AdminDashboardStats) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "App Visitors & Traffic",
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Visits",
                    value = stats.totalVisits.toString(),
                    icon = Icons.Rounded.TrendingUp,
                    accentColor = RoyalBlue,
                    subtitle = "All tracked sessions",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Unique Visitors",
                    value = stats.uniqueVisitors.toString(),
                    icon = Icons.Rounded.Groups,
                    accentColor = PresentGreen,
                    subtitle = "Distinct accounts",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Today's Visits",
                    value = stats.todayVisits.toString(),
                    icon = Icons.Rounded.Today,
                    accentColor = SkyAccent,
                    subtitle = stats.todayDateLabel.ifBlank { "Today" },
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "7-Day Traffic",
                    value = stats.last7DaysVisits.toString(),
                    icon = Icons.Rounded.DateRange,
                    accentColor = LeaveOrange,
                    subtitle = "30d: ${stats.last30DaysVisits}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            DailyTrafficBarChartCard(
                buckets = stats.dailyTraffic,
                peakHourLabel = stats.peakHourLabel
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Platform Academic Inventory",
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Schools",
                    value = stats.totalSchools.toString(),
                    icon = Icons.Rounded.AccountBalance,
                    accentColor = NavyPrimary,
                    subtitle = "${stats.publicSchoolsCount} public listed",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Registered Users",
                    value = stats.totalUsers.toString(),
                    icon = Icons.Rounded.Person,
                    accentColor = RoyalBlue,
                    subtitle = "${stats.principalUsersCount}P • ${stats.teacherUsersCount}T • ${stats.studentUsersCount}S",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "All Teachers",
                    value = stats.totalTeachers.toString(),
                    icon = Icons.Rounded.Badge,
                    accentColor = SkyAccent,
                    subtitle = "${stats.totalTeacherInvites} invites sent",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "All Students",
                    value = stats.totalStudents.toString(),
                    icon = Icons.Rounded.School,
                    accentColor = PresentGreen,
                    subtitle = "${stats.totalClasses} classes • ${stats.totalSections} sec",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Attendance Logs",
                    value = stats.totalAttendanceSessions.toString(),
                    icon = Icons.Rounded.EventAvailable,
                    accentColor = PresentGreen,
                    subtitle = "Marked sessions",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Tests & Exams",
                    value = stats.totalTests.toString(),
                    icon = Icons.Rounded.Assignment,
                    accentColor = WarningAmber,
                    subtitle = "${stats.totalAnnouncements} announcements",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Pending Requests",
                    value = stats.totalPendingRequests.toString(),
                    icon = Icons.Rounded.HourglassTop,
                    accentColor = LeaveOrange,
                    subtitle = "Awaiting principal",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Principal Transfers",
                    value = stats.totalPrincipalInvites.toString(),
                    icon = Icons.Rounded.SwapHoriz,
                    accentColor = NavyPrimary,
                    subtitle = "PRN handover codes",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(title = "Audience & School Breakdown") {
                val totalRoleVisits = stats.visitsByRole.values.sum().coerceAtLeast(1)
                Text("Visits by Account Role", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                Spacer(Modifier.height(8.dp))
                listOf("principal" to "Principals", "teacher" to "Teachers", "student" to "Students").forEach { (key, label) ->
                    val count = stats.visitsByRole[key] ?: 0
                    val ratio = count.toFloat() / totalRoleVisits.toFloat()
                    ProgressBreakdownRow(label = label, countText = "$count visits", progress = ratio)
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                val totalSchoolCount = stats.totalSchools.coerceAtLeast(1)
                Text("Schools by Category", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                Spacer(Modifier.height(8.dp))
                listOf("Private", "Government", "Other").forEach { type ->
                    val count = stats.schoolsByType[type] ?: 0
                    val ratio = count.toFloat() / totalSchoolCount.toFloat()
                    ProgressBreakdownRow(label = type, countText = "$count schools", progress = ratio)
                }
            }
        }

        item {
            SectionCard(
                title = "Recent App Visits (${stats.recentVisits.size})",
                trailing = {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(18.dp))
                }
            ) {
                if (stats.recentVisits.isEmpty()) {
                    Text(
                        text = "No visits recorded yet. Visits log automatically when users open or sign into the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    stats.recentVisits.take(25).forEachIndexed { idx, visit ->
                        VisitFeedRow(visit = visit)
                        if (idx < minOf(24, stats.recentVisits.lastIndex)) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTrafficBarChartCard(
    buckets: List<DailyTrafficBucket>,
    peakHourLabel: String
) {
    SectionCard(
        title = "7-Day Traffic Overview",
        trailing = {
            StatusPill(label = "DD-MM", textColor = RoyalBlue, backgroundColor = SkySoft)
        }
    ) {
        val maxVisits = (buckets.maxOfOrNull { it.visits } ?: 0).coerceAtLeast(1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { bucket ->
                val heightFraction = (bucket.visits.toFloat() / maxVisits.toFloat()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = bucket.visits.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (bucket.visits > 0) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height((84f * heightFraction).coerceAtLeast(6f).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                Brush.verticalGradient(
                                    if (bucket.visits > 0) listOf(RoyalBlue, SkyAccent)
                                    else listOf(Color(0xFFE2E8F0), Color(0xFFE2E8F0))
                                )
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bucket.dateLabel.take(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Peak Traffic Hour: $peakHourLabel",
            style = MaterialTheme.typography.bodySmall,
            color = NavyPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProgressBreakdownRow(
    label: String,
    countText: String,
    progress: Float
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(countText, style = MaterialTheme.typography.bodySmall, color = NavyPrimary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = RoyalBlue,
            trackColor = SkySoft
        )
    }
}

@Composable
private fun VisitFeedRow(visit: AppVisit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SkySoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = visit.userName.trim().take(1).uppercase().ifBlank { "U" },
                style = MaterialTheme.typography.labelLarge,
                color = NavyPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = visit.userName.ifBlank { visit.userEmail },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    visit.userEmail.takeIf { it.isNotBlank() },
                    visit.activeSchoolName?.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val timeStr = visit.createdAt?.toDisplayDateTime()?.takeIf { it.isNotBlank() } ?: visit.dayKey
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = RoyalBlue
            )
        }
        Spacer(Modifier.width(6.dp))
        StatusPill(
            label = visit.roleIntent.replaceFirstChar { it.uppercase() },
            textColor = NavyPrimary,
            backgroundColor = SkySoft
        )
    }
}

@Composable
fun AdminSchoolsTab(
    stats: AdminDashboardStats,
    onToggleVisibility: (schoolCode: String, currentlyPublic: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var inspectingSchool by remember { mutableStateOf<SchoolAdminSummary?>(null) }

    val filtered = remember(stats.schoolSummaries, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) stats.schoolSummaries
        else stats.schoolSummaries.filter { s ->
            s.school.name.lowercase().contains(q) ||
                s.school.schoolCode.lowercase().contains(q) ||
                s.school.city.lowercase().contains(q) ||
                s.ownerName.lowercase().contains(q) ||
                s.ownerEmail.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search schools by name, code, city, or principal") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_school_search")
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.AccountBalance,
                    title = "No Schools Found",
                    description = "Registered schools on BL School Hub will appear here with live teacher, student, and activity totals."
                )
            }
        } else {
            items(filtered, key = { it.school.id }) { summary ->
                val school = summary.school
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SchoolAvatar(school.logoUrl, school.name, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = school.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${school.schoolCode} • ${school.city} • ${school.schoolType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = if (summary.isPublicListed) "PUBLIC" else "HIDDEN",
                                textColor = if (summary.isPublicListed) PresentGreen else AbsentRed,
                                backgroundColor = if (summary.isPublicListed) PresentGreenBg else AbsentRedBg
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Principal: ${summary.ownerName} (${summary.ownerEmail})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("${summary.teachersCount} Teachers", NavyPrimary, SkySoft)
                            StatusPill("${summary.studentsCount} Students", PresentGreen, PresentGreenBg)
                            StatusPill("${summary.classesCount} Classes", RoyalBlue, SkySoft)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Attendance: ${summary.attendanceSessionsCount} • Tests: ${summary.testsCount} • Posts: ${summary.announcementsCount} • Pending Req: ${summary.pendingTeacherRequestsCount + summary.pendingStudentRequestsCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { inspectingSchool = summary },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Inspect")
                            }
                            OutlinedButton(
                                onClick = { onToggleVisibility(school.schoolCode, summary.isPublicListed) },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (summary.isPublicListed) WarningAmber else PresentGreen
                                )
                            ) {
                                Icon(
                                    imageVector = if (summary.isPublicListed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (summary.isPublicListed) "Hide Listing" else "Make Public")
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingSchool?.let { details ->
        val s = details.school
        AlertDialog(
            onDismissRequest = { inspectingSchool = null },
            title = { Text(s.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("School Code: ${s.schoolCode}")
                    Text("Type: ${s.schoolType} • City: ${s.city}")
                    Text("Principal: ${details.ownerName}")
                    Text("Owner Email: ${details.ownerEmail}")
                    Text("Contact: ${s.contactNumber} • ${s.email}")
                    if (s.address.isNotBlank()) Text("Address: ${s.address}")
                    Text("Created: ${s.createdAt?.toDateOnlyString()?.ifBlank { "Recent" } ?: "Recent"}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Teachers: ${details.teachersCount} • Students: ${details.studentsCount}")
                    Text("Classes: ${details.classesCount} • Sections: ${details.sectionsCount}")
                    Text("Attendance Sessions: ${details.attendanceSessionsCount} • Tests: ${details.testsCount}")
                    Text("Announcements: ${details.announcementsCount}")
                    Text("Pending Teacher Req: ${details.pendingTeacherRequestsCount} • Pending Student Req: ${details.pendingStudentRequestsCount}")
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingSchool = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun AdminUsersTab(stats: AdminDashboardStats) {
    var query by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf("all") }

    val filtered = remember(stats.users, query, selectedRoleFilter) {
        val q = query.trim().lowercase()
        stats.users.filter { user ->
            val roleMatches = selectedRoleFilter == "all" || user.accountIntent == selectedRoleFilter
            val textMatches = q.isBlank() ||
                user.fullName.lowercase().contains(q) ||
                user.email.lowercase().contains(q) ||
                user.phone.lowercase().contains(q)
            roleMatches && textMatches
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search users by name, email, or phone") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_user_search")
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                listOf("all" to "All", "principal" to "Principals", "teacher" to "Teachers", "student" to "Students").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedRoleFilter == key,
                        onClick = { selectedRoleFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Person,
                    title = "No Users Match Filter",
                    description = "Try clearing the search query or selecting All roles."
                )
            }
        } else {
            items(filtered, key = { it.id.ifBlank { it.userId } }) { user ->
                UserDirectoryCard(user = user)
            }
        }
    }
}

@Composable
private fun UserDirectoryCard(user: UserProfile) {
    val (roleFg, roleBg) = when (user.accountIntent) {
        "principal" -> NavyPrimary to SkySoft
        "teacher" -> RoyalBlue to SkySoft
        else -> PresentGreen to PresentGreenBg
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(roleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.fullName.trim().take(1).uppercase().ifBlank { "U" },
                    style = MaterialTheme.typography.titleMedium,
                    color = roleFg,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.fullName.ifBlank { "Unnamed User" },
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.email.ifBlank { "No email" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val joinedDate = user.createdAt?.toDateOnlyString()?.takeIf { it.isNotBlank() } ?: "Recent"
                Text(
                    text = listOfNotNull(
                        user.phone.takeIf { it.isNotBlank() },
                        "Joined: $joinedDate"
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(
                    label = user.accountIntent.uppercase(),
                    textColor = roleFg,
                    backgroundColor = roleBg
                )
                if (!user.activeSchoolId.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        label = "In School",
                        textColor = WarningAmber,
                        backgroundColor = WarningAmberBg
                    )
                }
            }
        }
    }
}
