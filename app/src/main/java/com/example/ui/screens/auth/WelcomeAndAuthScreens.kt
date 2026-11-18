package com.example.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TeacherInviteLinks
import com.example.data.model.UserRole
import com.example.ui.components.BLSchoolHubLogo
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.LoadingOverlay
import com.example.ui.components.RotatingHubSpinner
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.AuthDestination
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun UnauthenticatedWelcomeScreen(
    selectedRoleIntent: UserRole,
    pendingTeacherInviteCode: String? = null,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSelectRoleIntent: (UserRole) -> Unit,
    onApplyTeacherInviteCode: (String) -> Unit = {},
    onApplyTeacherCredentials: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onDismissTeacherInvite: () -> Unit = {},
    onDismissError: () -> Unit,
    onContinueWithGoogle: (UserRole) -> Unit
) {
    val activeRoleChoice = if (selectedRoleIntent == UserRole.STUDENT) UserRole.STUDENT else UserRole.PRINCIPAL
    var showPasteInviteBox by remember { mutableStateOf(false) }
    var teacherJoinTab by remember { mutableStateOf(0) }
    var manualInviteInput by remember { mutableStateOf("") }
    var teacherUsernameInput by remember { mutableStateOf("") }
    var teacherPasswordInput by remember { mutableStateOf("") }
    var teacherFullNameInput by remember { mutableStateOf("") }
    var teacherPhoneInput by remember { mutableStateOf("") }
    var manualInviteError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                FeedbackBanner(errorMessage = errorMessage, successMessage = null, onDismiss = onDismissError)
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_campus),
                        contentDescription = "BL School Hub Campus",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, NavyDeep.copy(alpha = 0.84f))
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BLSchoolHubLogo(size = 54.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Welcome to BL School Hub",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your School, One Digital Hub.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                if (!pendingTeacherInviteCode.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teacher_invite_received_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SkySoft),
                        border = BorderStroke(1.5.dp, RoyalBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = NavyPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Teacher Invite Received",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "You opened a Principal's Teacher invite (Code: $pendingTeacherInviteCode). Sign in with your Gmail account below to join the school immediately as a Teacher.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NavyDeep
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { onContinueWithGoogle(UserRole.TEACHER) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("accept_teacher_invite_google_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                            ) {
                                Text(
                                    text = "Sign in with Gmail to Join School",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = onDismissTeacherInvite,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Use Principal or Student account instead")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Create or open a Principal or Student account",
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sign in securely with Gmail. Accounts can be created directly by Principals and Students; Teachers join via a Principal's invite link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    RoleChoiceCard(
                        title = "Principal",
                        subtitle = "Create and manage your school, classes, students, attendance, results, visibility, and invite links for your teachers.",
                        icon = Icons.Rounded.AccountBalance,
                        selected = activeRoleChoice == UserRole.PRINCIPAL,
                        testTag = "role_card_principal",
                        onClick = { onSelectRoleIntent(UserRole.PRINCIPAL) }
                    )
                    Spacer(Modifier.height(10.dp))
                    RoleChoiceCard(
                        title = "Student",
                        subtitle = "Open any public school directly without approval to explore classes, attendance, results, homework, and announcements.",
                        icon = Icons.Rounded.School,
                        selected = activeRoleChoice == UserRole.STUDENT,
                        testTag = "role_card_student",
                        onClick = { onSelectRoleIntent(UserRole.STUDENT) }
                    )

                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { onContinueWithGoogle(activeRoleChoice) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("welcome_google_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                    ) {
                        Icon(Icons.Rounded.AccountBalance, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google as ${activeRoleChoice.label}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showPasteInviteBox = !showPasteInviteBox
                            manualInviteError = null
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("toggle_paste_teacher_invite")
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Invited as Teacher? Join via Link or Username")
                    }

                    if (showPasteInviteBox) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SkySoft.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                TabRow(selectedTabIndex = teacherJoinTab) {
                                    Tab(
                                        selected = teacherJoinTab == 0,
                                        onClick = { teacherJoinTab = 0; manualInviteError = null },
                                        text = { Text("Invite Link / Code") }
                                    )
                                    Tab(
                                        selected = teacherJoinTab == 1,
                                        onClick = { teacherJoinTab = 1; manualInviteError = null },
                                        text = { Text("Username & Password") }
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                if (teacherJoinTab == 0) {
                                    OutlinedTextField(
                                        value = manualInviteInput,
                                        onValueChange = {
                                            manualInviteInput = it
                                            manualInviteError = null
                                        },
                                        label = { Text("Paste Invite Link or Code (e.g. TCH-XXXXXX)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_paste_invite_input")
                                    )
                                    if (manualInviteError != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = manualInviteError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val code = TeacherInviteLinks.extractInviteCode(manualInviteInput)
                                            if (code.isNullOrBlank()) {
                                                manualInviteError = "Please paste a valid Teacher invite link or TCH-XXXXXX code."
                                            } else {
                                                onApplyTeacherInviteCode(code)
                                                showPasteInviteBox = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_apply_invite_button")
                                    ) {
                                        Text("Continue to Gmail Sign-In as Teacher")
                                    }
                                } else {
                                    Text(
                                        text = "Enter the Username and Password given by your school principal, and your name:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = teacherUsernameInput,
                                        onValueChange = {
                                            teacherUsernameInput = it.removePrefix("@").take(40)
                                            manualInviteError = null
                                        },
                                        label = { Text("Username (e.g. ali.science)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_teacher_username_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = teacherPasswordInput,
                                        onValueChange = {
                                            teacherPasswordInput = it.take(100)
                                            manualInviteError = null
                                        },
                                        label = { Text("Password / Code") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_teacher_password_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = teacherFullNameInput,
                                        onValueChange = {
                                            teacherFullNameInput = it.take(120)
                                            manualInviteError = null
                                        },
                                        label = { Text("Your Full Name (e.g. Sir Ali)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_teacher_fullname_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = teacherPhoneInput,
                                        onValueChange = { teacherPhoneInput = it.take(40) },
                                        label = { Text("Phone Number (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_teacher_phone_input")
                                    )
                                    if (manualInviteError != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = manualInviteError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val cleanUser = teacherUsernameInput.trim().removePrefix("@").trim()
                                            val cleanPass = teacherPasswordInput.trim()
                                            val cleanName = teacherFullNameInput.trim()
                                            if (cleanUser.isBlank()) {
                                                manualInviteError = "Please enter your teacher username."
                                            } else if (cleanPass.isBlank()) {
                                                manualInviteError = "Please enter your password / access code."
                                            } else if (cleanName.isBlank()) {
                                                manualInviteError = "Please enter your full name."
                                            } else {
                                                onApplyTeacherCredentials(cleanUser, cleanPass, cleanName, teacherPhoneInput.trim())
                                                showPasteInviteBox = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("welcome_apply_credentials_button")
                                    ) {
                                        Text("Continue to Gmail Sign-In as Teacher")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Returning Principals and invited Teachers are automatically signed into their school dashboard.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        LoadingOverlay(visible = isSigningIn)
    }
}

@Composable
fun AuthFlowHost(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onOpenAdminDashboard: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isBrowsingSchools) {
            StudentConnectSchoolScreen(state, viewModel, onOpenAdminDashboard)
        } else {
            when (state.destination) {
                AuthDestination.SPLASH -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BLSchoolHubLogo(size = 84.dp)
                            Spacer(Modifier.height(22.dp))
                            RotatingHubSpinner(size = 52.dp)
                        }
                    }
                }

                AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL ->
                    PrincipalCreateSchoolScreen(state, viewModel, onOpenAdminDashboard)
                AuthDestination.ONBOARD_TEACHER_INVITE ->
                    TeacherOnboardingScreen(state, viewModel, onOpenAdminDashboard)
                AuthDestination.ONBOARD_STUDENT_CONNECT ->
                    StudentConnectSchoolScreen(state, viewModel, onOpenAdminDashboard)
                AuthDestination.AUTHENTICATED_WORKSPACE -> Unit
            }
        }

        LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun OnboardingRoleSwitchStrip(
    selected: UserRole,
    onSelect: (UserRole) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Switch Role Setup",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.STUDENT).forEach { role ->
                FilterChip(
                    selected = selected == role,
                    onClick = { onSelect(role) },
                    label = { Text(role.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RoleChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SkySoft else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) RoyalBlue else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) NavyPrimary else SkySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) Color.White else NavyPrimary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = RoyalBlue)
            }
        }
    }
}

@Composable
private fun OnboardingTopBarAdminButton(onOpenAdminDashboard: (() -> Unit)?) {
    if (onOpenAdminDashboard != null) {
        Button(
            onClick = onOpenAdminDashboard,
            colors = ButtonDefaults.buttonColors(
                containerColor = NavyPrimary,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(end = 4.dp)
                .height(36.dp)
                .testTag("topbar_admin_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.AdminPanelSettings,
                contentDescription = "Super Admin Dashboard",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Admin", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipalCreateSchoolScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onOpenAdminDashboard: (() -> Unit)?
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var schoolType by remember { mutableStateOf("Private") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(state.user?.email.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var establishedYear by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) logoUri = uri
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) bannerUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Your School") },
                actions = {
                    OnboardingTopBarAdminButton(onOpenAdminDashboard)
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                if (state.user?.accountIntent != UserRole.STUDENT.dbValue) {
                    OnboardingRoleSwitchStrip(selected = state.selectedRoleIntent, onSelect = viewModel::switchRoleIntent)
                }
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)
                var principalCode by remember { mutableStateOf("") }
                if (state.pendingPrincipalInvites.isNotEmpty()) {
                    PendingPrincipalInvitesCard(state, viewModel)
                    Spacer(Modifier.height(14.dp))
                }
                SectionCard(title = "Accept Principal Transfer Invite Code") {
                    Text(
                        text = "Were you invited by an existing Principal to take over their school? Enter your handover code instead of creating a new school.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = principalCode,
                        onValueChange = { principalCode = it.uppercase() },
                        label = { Text("Principal Invite Code (e.g. PRN-XXXXXX)") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("principal_invite_code_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.acceptPrincipalInvitation(principalCode) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("accept_principal_invite_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Become Principal with Code")
                    }
                }
                Spacer(Modifier.height(14.dp))

                Surface(
                    color = NavyPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Welcome, Principal!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Let's create your school. Each Principal account is permanently linked as the owner of one school.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("pick_school_logo")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (logoUri == null) "Upload Logo" else "Logo Selected")
                    }
                    OutlinedButton(
                        onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("pick_school_banner")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (bannerUri == null) "Upload Banner" else "Banner Selected")
                    }
                }

                if (logoUri != null || bannerUri != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        logoUri?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = "Selected School Logo",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        bannerUri?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = "Selected Cover Banner",
                                modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("School Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_school_name")
                )
                Spacer(Modifier.height(10.dp))
                Text("School Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Private", "Government", "Other").forEach { type ->
                        FilterChip(
                            selected = schoolType == type,
                            onClick = { schoolType = type },
                            label = { Text(type) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("School Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("create_school_city")
                    )
                    OutlinedTextField(
                        value = establishedYear,
                        onValueChange = { establishedYear = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Est. Year") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = { contactNumber = it },
                    label = { Text("Contact Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_school_phone")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("School Email *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_school_email")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("School Website (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("School Description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        viewModel.createSchoolAsPrincipal(
                            name, schoolType, address, city, contactNumber, email,
                            description, establishedYear.toIntOrNull(), website, logoUri, bannerUri
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("create_school_submit"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Create School", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("onboarding_delete_account_btn")
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AbsentRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete My Account", color = AbsentRed)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?", color = AbsentRed) },
            text = { Text("This will permanently delete your BL School Hub profile and sign you out.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMyNonPrincipalAccount(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherOnboardingScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onOpenAdminDashboard: (() -> Unit)?
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val stagedCreds = state.stagedTeacherCredentials
    var joinMode by remember(stagedCreds) { mutableStateOf(if (stagedCreds != null) 1 else 0) }
    var inviteCodeOrLink by remember(state.incomingTeacherInviteCode) {
        mutableStateOf(state.incomingTeacherInviteCode.orEmpty())
    }
    var teacherUsername by remember(stagedCreds) { mutableStateOf(stagedCreds?.username.orEmpty()) }
    var teacherPassword by remember(stagedCreds) { mutableStateOf(stagedCreds?.accessPassword.orEmpty()) }
    var teacherFullName by remember(state.user, stagedCreds) {
        mutableStateOf(stagedCreds?.fullName?.takeIf { it.isNotBlank() } ?: state.user?.fullName.orEmpty())
    }
    var teacherPhone by remember(state.user, stagedCreds) {
        mutableStateOf(stagedCreds?.phone?.takeIf { it.isNotBlank() } ?: state.user?.phone.orEmpty())
    }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join School as Teacher") },
                actions = {
                    OnboardingTopBarAdminButton(onOpenAdminDashboard)
                    IconButton(onClick = viewModel::refreshContext) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                if (state.user?.accountIntent != UserRole.STUDENT.dbValue) {
                    OnboardingRoleSwitchStrip(selected = state.selectedRoleIntent, onSelect = viewModel::switchRoleIntent)
                }
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)
                if (state.pendingPrincipalInvites.isNotEmpty()) {
                    PendingPrincipalInvitesCard(state, viewModel)
                    Spacer(Modifier.height(14.dp))
                }

                SectionCard(title = "Join School as Teacher") {
                    TabRow(selectedTabIndex = joinMode) {
                        Tab(
                            selected = joinMode == 0,
                            onClick = { joinMode = 0; localError = null },
                            text = { Text("Join via Invite Link") }
                        )
                        Tab(
                            selected = joinMode == 1,
                            onClick = { joinMode = 1; localError = null },
                            text = { Text("Join via Username & Password") }
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    if (joinMode == 0) {
                        Text(
                            text = "Signed in as ${state.user?.email}. Paste the WhatsApp invite link or invite code sent by your School Principal to join that school as a Teacher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = inviteCodeOrLink,
                            onValueChange = {
                                inviteCodeOrLink = it
                                localError = null
                            },
                            label = { Text("Invite Link or Code (e.g. TCH-XXXXXX)") },
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_invite_code_input")
                        )
                        val parsedCode = TeacherInviteLinks.extractInviteCode(inviteCodeOrLink)
                        if (!parsedCode.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Detected Invite Code: $parsedCode",
                                style = MaterialTheme.typography.labelMedium,
                                color = RoyalBlue
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = teacherFullName,
                            onValueChange = { teacherFullName = it },
                            label = { Text("Your Full Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_fullname_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = teacherPhone,
                            onValueChange = { teacherPhone = it },
                            label = { Text("Phone Number (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_phone_input")
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.acceptTeacherInvitation(inviteCodeOrLink, teacherFullName, teacherPhone) },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("accept_invite_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                        ) {
                            Text("Join School as Teacher")
                        }
                    } else {
                        Text(
                            text = "Signed in as ${state.user?.email}. Enter the Username and Password generated by your School Principal, and confirm your full name to join as a Teacher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = teacherUsername,
                            onValueChange = {
                                teacherUsername = it.removePrefix("@").take(40)
                                localError = null
                            },
                            label = { Text("Teacher Username (e.g. ali.science)") },
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_username_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = teacherPassword,
                            onValueChange = {
                                teacherPassword = it.take(100)
                                localError = null
                            },
                            label = { Text("Password / Access Code") },
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_password_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = teacherFullName,
                            onValueChange = {
                                teacherFullName = it.take(120)
                                localError = null
                            },
                            label = { Text("Your Full Name (e.g. Sir Ali)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_credentials_fullname_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = teacherPhone,
                            onValueChange = { teacherPhone = it.take(40) },
                            label = { Text("Phone Number (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("teacher_credentials_phone_input")
                        )
                        if (localError != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = localError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val cleanUser = teacherUsername.trim().removePrefix("@").trim()
                                val cleanPass = teacherPassword.trim()
                                val cleanName = teacherFullName.trim()
                                if (cleanUser.isBlank()) {
                                    localError = "Please enter your teacher username."
                                } else if (cleanPass.isBlank()) {
                                    localError = "Please enter your password / access code."
                                } else if (cleanName.isBlank()) {
                                    localError = "Please enter your full name."
                                } else {
                                    viewModel.acceptTeacherByCredentials(cleanUser, cleanPass, cleanName, teacherPhone.trim())
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("accept_credentials_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                        ) {
                            Text("Join School with Credentials")
                        }
                    }
                }

                if (state.pendingTeacherInvites.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SectionCard(title = "Pending Invitations for Your Email") {
                        state.pendingTeacherInvites.forEach { inv ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${inv.fullName} (${inv.schoolName})", style = MaterialTheme.typography.titleSmall)
                                    Text("Code: ${inv.inviteCode}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { viewModel.acceptTeacherInvitation(inv.inviteCode) }) {
                                    Text("Accept")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { viewModel.switchRoleIntent(UserRole.STUDENT) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Switch to Student or Principal Account Instead")
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("teacher_onboarding_delete_account_btn")
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AbsentRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete My Account", color = AbsentRed)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?", color = AbsentRed) },
            text = { Text("This will permanently delete your BL School Hub profile and sign you out.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMyNonPrincipalAccount(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentConnectSchoolScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    onOpenAdminDashboard: (() -> Unit)?
) {
    val context = LocalContext.current
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val pinnedIds = state.user?.pinnedSchoolIds?.ifEmpty { state.pinnedSchools.map { it.id } }
        ?: state.pinnedSchools.map { it.id }
    val defaultSchoolId = state.user?.activeSchoolId?.takeIf { activeId ->
        state.pinnedSchools.any { it.id == activeId }
    } ?: state.pinnedSchools.firstOrNull()?.id ?: state.activeSchool?.id

    val filteredSchools = remember(state.searchedSchools, searchQuery) {
        val needle = searchQuery.trim().lowercase()
        if (needle.isBlank()) {
            state.searchedSchools
        } else {
            state.searchedSchools.filter {
                it.name.lowercase().contains(needle) ||
                    it.schoolCode.lowercase().contains(needle) ||
                    it.city.lowercase().contains(needle)
            }
        }
    }

    if (state.activeSchool != null && state.isBrowsingSchools) {
        BackHandler { viewModel.returnToActiveStudentSchool() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse & Open Schools") },
                navigationIcon = {
                    if (state.activeSchool != null && state.isBrowsingSchools) {
                        IconButton(
                            onClick = viewModel::returnToActiveStudentSchool,
                            modifier = Modifier.testTag("student_back_to_school_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to ${state.activeSchool.name}"
                            )
                        }
                    }
                },
                actions = {
                    OnboardingTopBarAdminButton(onOpenAdminDashboard)
                    IconButton(onClick = viewModel::refreshContext) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh schools")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SkySoft.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.School, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Student Account • ${state.user?.email.orEmpty()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Pin up to 3 schools so your default school opens automatically next time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)

                if (state.activeSchool != null && state.isBrowsingSchools) {
                    OutlinedButton(
                        onClick = viewModel::returnToActiveStudentSchool,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("return_to_active_school_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back to ${state.activeSchool.name}")
                    }
                    Spacer(Modifier.height(14.dp))
                }

                SectionCard(title = "Pinned Schools (${state.pinnedSchools.size}/3)") {
                    if (state.pinnedSchools.isEmpty()) {
                        Text(
                            text = "No schools pinned yet. Tap the Pin icon on any school below (up to 3) to save it here and auto-open it by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.pinnedSchools.forEach { pinnedSchool ->
                            val isDefault = pinnedSchool.id == defaultSchoolId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clickable { viewModel.enterPublicSchoolAsStudent(pinnedSchool) }
                                    .testTag("pinned_school_card_${pinnedSchool.schoolCode}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDefault) SkySoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = if (isDefault) BorderStroke(1.5.dp, RoyalBlue) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SchoolAvatar(pinnedSchool.logoUrl, pinnedSchool.name, size = 44.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = pinnedSchool.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isDefault) {
                                                Spacer(Modifier.width(6.dp))
                                                StatusPill("Default", PresentGreen, PresentGreenBg)
                                            }
                                        }
                                        Text(
                                            text = "${pinnedSchool.city} • ID: ${pinnedSchool.schoolCode}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.togglePinSchool(pinnedSchool) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("unpin_school_${pinnedSchool.schoolCode}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PushPin,
                                            contentDescription = "Unpin ${pinnedSchool.name}",
                                            tint = RoyalBlue
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.enterPublicSchoolAsStudent(pinnedSchool) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                        modifier = Modifier.testTag("open_pinned_school_${pinnedSchool.schoolCode}")
                                    ) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionCard(title = "Search & Open Any Public School") {
                    Text(
                        text = "No Principal approval is required! Search any public school below to open it immediately or pin it (max 3).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("School Name, City, or ID (BL-XXXXXX)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.searchSchools(searchQuery) }),
                            modifier = Modifier.weight(1f).testTag("student_school_search_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.searchSchools(searchQuery) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("student_school_search_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search schools", tint = NavyPrimary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (filteredSchools.isEmpty()) {
                        Text(
                            text = "No matching public schools found. Hidden schools only appear to their Principal and Teachers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredSchools.forEach { school ->
                            val isPinned = pinnedIds.contains(school.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { viewModel.enterPublicSchoolAsStudent(school) }
                                    .testTag("student_school_card_${school.schoolCode}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SkySoft.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SchoolAvatar(school.logoUrl, school.name, size = 48.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(school.name, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                        Text(
                                            text = "${school.city} • ${school.schoolType} • ID: ${school.schoolCode}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.togglePinSchool(school) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("pin_school_${school.schoolCode}")
                                    ) {
                                        Icon(
                                            imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = if (isPinned) "Unpin ${school.name}" else "Pin ${school.name}",
                                            tint = if (isPinned) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.enterPublicSchoolAsStudent(school) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                        modifier = Modifier.testTag("open_school_${school.schoolCode}")
                                    ) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("student_browse_delete_account_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AbsentRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete My Account", color = AbsentRed)
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Student Account?", color = AbsentRed) },
            text = {
                Text(
                    "This permanently deletes your Student profile, pinned schools, and school memberships from BL School Hub.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteMyNonPrincipalAccount(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PendingPrincipalInvitesCard(
    state: AuthUiState,
    viewModel: AuthViewModel
) {
    SectionCard(title = "Principal Handover Invitation for Your Email") {
        state.pendingPrincipalInvites.forEach { inv ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${inv.schoolName} (${inv.schoolCode})", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    Text("Invited: ${inv.fullName} • Code: ${inv.inviteCode}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.acceptPrincipalInvitation(inv.inviteCode) }) {
                    Text("Accept")
                }
            }
        }
    }
}
