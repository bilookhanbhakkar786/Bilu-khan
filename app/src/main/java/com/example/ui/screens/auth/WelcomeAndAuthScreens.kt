package com.example.ui.screens.auth

import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.School
import com.example.data.model.UserRole
import com.example.ui.components.BLSchoolHubLogo
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.LoadingOverlay
import com.example.ui.components.SchoolAvatar
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkySoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.viewmodel.AuthDestination
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun UnauthenticatedWelcomeScreen(
    selectedRoleIntent: UserRole,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSelectRoleIntent: (UserRole) -> Unit,
    onDismissError: () -> Unit,
    onContinueWithGoogle: (UserRole) -> Unit
) {
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

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "What type of account do you want to use?",
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sign in securely with Google. Principal and Teacher permissions are enforced by Firebase Cloud security rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                RoleChoiceCard(
                    title = "Principal",
                    subtitle = "Create and manage your school, academic sessions, classes, teachers, students, and reports.",
                    icon = Icons.Rounded.AccountBalance,
                    selected = selectedRoleIntent == UserRole.PRINCIPAL,
                    testTag = "role_card_principal",
                    onClick = { onSelectRoleIntent(UserRole.PRINCIPAL) }
                )
                Spacer(Modifier.height(10.dp))
                RoleChoiceCard(
                    title = "Teacher",
                    subtitle = "Join your school via verified invite code, mark multi-session attendance, enter marks, and assign homework.",
                    icon = Icons.Rounded.Badge,
                    selected = selectedRoleIntent == UserRole.TEACHER,
                    testTag = "role_card_teacher",
                    onClick = { onSelectRoleIntent(UserRole.TEACHER) }
                )
                Spacer(Modifier.height(10.dp))
                RoleChoiceCard(
                    title = "Student",
                    subtitle = "Connect to your school, track your attendance, published results, timetable, and school announcements.",
                    icon = Icons.Rounded.School,
                    selected = selectedRoleIntent == UserRole.STUDENT,
                    testTag = "role_card_student",
                    onClick = { onSelectRoleIntent(UserRole.STUDENT) }
                )

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { onContinueWithGoogle(selectedRoleIntent) },
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
                        text = "Continue with Google as ${selectedRoleIntent.label}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Returning users who already belong to a school are automatically signed directly into their school dashboard.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LoadingOverlay(visible = isSigningIn)
    }
}

@Composable
fun AuthFlowHost(
    state: AuthUiState,
    viewModel: AuthViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state.destination) {
            AuthDestination.SPLASH -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BLSchoolHubLogo(size = 84.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("BL School Hub", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
                        Text(
                            "Loading your school workspace...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL -> PrincipalCreateSchoolScreen(state, viewModel)
            AuthDestination.ONBOARD_TEACHER_INVITE -> TeacherOnboardingScreen(state, viewModel)
            AuthDestination.ONBOARD_STUDENT_CONNECT -> StudentConnectSchoolScreen(state, viewModel)
            AuthDestination.AUTHENTICATED_WORKSPACE -> Unit
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
            UserRole.entries.forEach { role ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipalCreateSchoolScreen(state: AuthUiState, viewModel: AuthViewModel) {
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
                OnboardingRoleSwitchStrip(selected = state.selectedRoleIntent, onSelect = viewModel::switchRoleIntent)
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherOnboardingScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var inviteCode by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Verification") },
                actions = {
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
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                OnboardingRoleSwitchStrip(selected = state.selectedRoleIntent, onSelect = viewModel::switchRoleIntent)
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)
                SectionCard(title = "Accept School Invitation") {
                    Text(
                        text = "Signed in as ${state.user?.email}. Teachers join a school only through a verified Principal invitation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text("Invitation Code (e.g. TCH-XXXXXX)") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_invite_code_input")
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.acceptTeacherInvitation(inviteCode) },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("accept_invite_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Join School")
                    }
                }

                if (state.pendingTeacherInvites.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionCard(title = "Pending Invitations for Your Email") {
                        state.pendingTeacherInvites.forEach { inv ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentConnectSchoolScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSchool by remember { mutableStateOf<School?>(null) }
    var rollNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf(state.user?.fullName.orEmpty()) }
    var fatherName by remember { mutableStateOf("") }
    var sessionName by remember { mutableStateOf("2025-2026") }
    var className by remember { mutableStateOf("") }
    var sectionName by remember { mutableStateOf("A") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Your School") },
                actions = {
                    IconButton(onClick = viewModel::refreshContext) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh status")
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
                OnboardingRoleSwitchStrip(selected = state.selectedRoleIntent, onSelect = viewModel::switchRoleIntent)
                FeedbackBanner(state.errorMessage, state.successMessage, viewModel::clearMessages)

                if (state.studentRequests.isNotEmpty()) {
                    SectionCard(title = "Your Connection Requests") {
                        state.studentRequests.forEach { req ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${req.requestedFullName} (Roll #${req.requestedRollNumber})", style = MaterialTheme.typography.titleSmall)
                                    Text("${req.schoolName} • ${req.requestedClassName} ${req.requestedSectionName}", style = MaterialTheme.typography.bodySmall)
                                }
                                StatusPill(
                                    label = req.status.uppercase(),
                                    textColor = if (req.status == "approved") PresentGreen else WarningAmber,
                                    backgroundColor = if (req.status == "approved") PresentGreenBg else WarningAmberBg
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (selectedSchool == null) {
                    SectionCard(title = "Search School") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("School Name, City, or ID (BL-XXXXXX)") },
                                singleLine = true,
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
                        if (state.searchedSchools.isEmpty()) {
                            Text(
                                text = "No matching schools found yet. Try searching by School Name or School ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.searchedSchools.forEach { school ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { selectedSchool = school },
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
                                            Text("${school.city} • ID: ${school.schoolCode}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Icon(Icons.Rounded.Business, contentDescription = "Select School", tint = RoyalBlue)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val school = selectedSchool!!
                    SectionCard(
                        title = "Request Connection to ${school.name}",
                        trailing = {
                            TextButton(onClick = { selectedSchool = null }) { Text("Change") }
                        }
                    ) {
                        Text("School ID: ${school.schoolCode} • ${school.city}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = rollNumber,
                            onValueChange = { rollNumber = it },
                            label = { Text("Student ID / Roll Number *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("connect_roll_number")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Student Full Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fatherName,
                            onValueChange = { fatherName = it },
                            label = { Text("Father's Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sessionName,
                                onValueChange = { sessionName = it },
                                label = { Text("Session") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = className,
                                onValueChange = { className = it },
                                label = { Text("Class (e.g. 8th)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = sectionName,
                                onValueChange = { sectionName = it },
                                label = { Text("Section") },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.requestStudentConnection(
                                    school, rollNumber, fullName, fatherName, sessionName, className, sectionName
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_student_connection"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Send Verification Request")
                        }
                    }
                }
            }
        }
    }
}
