package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SUPER_ADMIN_EMAIL
import com.example.data.repository.ReportExporter
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.LoadingOverlay
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyAccent
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    adminViewModel: AdminViewModel,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    LaunchedEffect(Unit) {
        adminViewModel.refreshDashboard()
    }

    val state by adminViewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.stats
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var localExportError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Super Admin Dashboard",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDeep,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back from Admin Dashboard")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val headers = listOf("School", "Code", "City", "Type", "Teachers", "Students", "Classes", "Tests")
                            val rows = stats.schoolSummaries.map { s ->
                                listOf(
                                    s.school.name,
                                    s.school.schoolCode,
                                    s.school.city,
                                    s.school.schoolType,
                                    s.teachersCount.toString(),
                                    s.studentsCount.toString(),
                                    s.classesCount.toString(),
                                    s.testsCount.toString()
                                )
                            }
                            ReportExporter.exportCsvReport(
                                context = context,
                                reportTitle = "BL School Hub Admin Analytics",
                                headers = headers,
                                rows = rows
                            ).onSuccess { file ->
                                ReportExporter.shareReportFile(context, file, "text/csv", "Export Admin CSV")
                            }.onFailure { err ->
                                localExportError = err.message ?: "CSV export failed."
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_export_csv")
                    ) {
                        Icon(Icons.Rounded.TableView, contentDescription = "Export Platform CSV")
                    }
                    IconButton(
                        onClick = {
                            val headers = listOf("School", "Code", "City", "Teachers", "Students", "Att.")
                            val rows = stats.schoolSummaries.map { s ->
                                listOf(
                                    s.school.name,
                                    s.school.schoolCode,
                                    s.school.city,
                                    s.teachersCount.toString(),
                                    s.studentsCount.toString(),
                                    s.attendanceSessionsCount.toString()
                                )
                            }
                            ReportExporter.exportPdfReport(
                                context = context,
                                school = null,
                                reportTitle = "BL School Hub — Super Admin Summary",
                                subtitle = "Visits: ${stats.totalVisits} (Unique: ${stats.uniqueVisitors}) | Today: ${stats.todayVisits} | Synced: ${stats.lastSyncedDisplay}",
                                headers = headers,
                                rows = rows,
                                footerSummary = "Schools: ${stats.totalSchools} | Users: ${stats.totalUsers} | Teachers: ${stats.totalTeachers} | Students: ${stats.totalStudents}"
                            ).onSuccess { file ->
                                ReportExporter.shareReportFile(context, file, "application/pdf", "Export Admin PDF")
                            }.onFailure { err ->
                                localExportError = err.message ?: "PDF export failed."
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_export_pdf")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export Platform PDF")
                    }
                    IconButton(
                        onClick = adminViewModel::refreshDashboard,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Admin Stats")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                FeedbackBanner(
                    errorMessage = localExportError ?: state.errorMessage,
                    successMessage = state.successMessage,
                    onDismiss = {
                        localExportError = null
                        adminViewModel.clearMessages()
                    }
                )

                // Hero Admin Identity Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(NavyDeep, NavyPrimary, RoyalBlue))
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = SkyAccent.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(10.dp).size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Platform Control Center",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SUPER_ADMIN_EMAIL,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBAE6FD)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (stats.lastSyncedDisplay.isNotBlank()) "Synced: ${stats.lastSyncedDisplay}" else "Syncing platform telemetry...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${stats.totalVisits} Visits • ${stats.totalSchools} Schools",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val tabs = listOf(
                    "Traffic & Stats",
                    "Schools (${stats.totalSchools})",
                    "Users (${stats.totalUsers})"
                )
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("admin_tab_$index")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> AdminOverviewTab(stats = stats)
                        1 -> AdminSchoolsTab(
                            stats = stats,
                            onToggleVisibility = { code, current ->
                                adminViewModel.toggleSchoolVisibility(code, current)
                            }
                        )
                        else -> AdminUsersTab(stats = stats)
                    }
                }
            }

            LoadingOverlay(visible = state.isLoading)
        }
    }
}
