package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.Coil
import coil.ImageLoader
import com.example.data.model.TeacherInviteLinks
import com.example.data.model.UserRole
import com.example.data.model.isSuperAdminEmail
import com.example.data.remote.DataUriFetcher
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.SchoolRepository
import com.example.ui.screens.AuthenticatedWorkspaceHost
import com.example.ui.screens.admin.SuperAdminDashboardScreen
import com.example.ui.screens.auth.AuthFlowHost
import com.example.ui.screens.auth.UnauthenticatedWelcomeScreen
import com.example.ui.theme.BLSchoolHubTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthDestination
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.HubViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var pendingTeacherInviteCode by mutableStateOf<String?>(null)

    private fun captureInviteFromIntent(intent: Intent?) {
        val candidates = listOfNotNull(
            intent?.data?.getQueryParameter("code"),
            intent?.dataString,
            intent?.getStringExtra(Intent.EXTRA_TEXT)
        )
        val extracted = candidates.firstNotNullOfOrNull { TeacherInviteLinks.extractInviteCode(it) }
        if (!extracted.isNullOrBlank()) {
            pendingTeacherInviteCode = extracted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureInviteFromIntent(intent)

        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .components { add(DataUriFetcher.Factory()) }
                .build()
        )

        val databaseId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val auth = Firebase.auth

        setContent {
            BLSchoolHubTheme {
                AppRoot(
                    auth = auth,
                    firestore = firestore,
                    pendingTeacherInviteCode = pendingTeacherInviteCode,
                    onSetPendingTeacherInvite = { code -> pendingTeacherInviteCode = code },
                    onConsumeTeacherInvite = { pendingTeacherInviteCode = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInviteFromIntent(intent)
    }
}

@Composable
private fun AppRoot(
    auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    pendingTeacherInviteCode: String?,
    onSetPendingTeacherInvite: (String) -> Unit,
    onConsumeTeacherInvite: () -> Unit
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var preferredRoleIntent by rememberSaveable { mutableStateOf(UserRole.PRINCIPAL) }
    var pendingTeacherCredentials by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val user = currentUser

    if (user == null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val authRepository = remember(auth, firestore) { AuthRepository(auth, firestore) }
        var isSigningIn by remember { mutableStateOf(false) }
        var signInError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val defaultSilentIntent = if (pendingTeacherInviteCode != null || pendingTeacherCredentials != null) UserRole.TEACHER else preferredRoleIntent
            AuthViewModel.attemptSilentGoogleSignIn(context, defaultSilentIntent, authRepository)
        }

        UnauthenticatedWelcomeScreen(
            selectedRoleIntent = if (preferredRoleIntent == UserRole.TEACHER && pendingTeacherInviteCode == null && pendingTeacherCredentials == null) {
                UserRole.PRINCIPAL
            } else {
                preferredRoleIntent
            },
            pendingTeacherInviteCode = pendingTeacherInviteCode,
            isSigningIn = isSigningIn,
            errorMessage = signInError,
            onSelectRoleIntent = { role ->
                if (role == UserRole.PRINCIPAL || role == UserRole.STUDENT) {
                    preferredRoleIntent = role
                }
            },
            onApplyTeacherInviteCode = onSetPendingTeacherInvite,
            onApplyTeacherCredentials = { username, password, name, phone ->
                pendingTeacherCredentials = listOf(username, password, name, phone)
                preferredRoleIntent = UserRole.TEACHER
                scope.launch {
                    isSigningIn = true
                    signInError = null
                    AuthViewModel.signInInteractiveWithGoogle(context, UserRole.TEACHER, authRepository)
                        .onFailure { err ->
                            signInError = err.message ?: "Google Sign-In failed."
                        }
                    isSigningIn = false
                }
            },
            onDismissTeacherInvite = onConsumeTeacherInvite,
            onDismissError = { signInError = null },
            onContinueWithGoogle = { roleForSignIn ->
                preferredRoleIntent = roleForSignIn
                scope.launch {
                    isSigningIn = true
                    signInError = null
                    AuthViewModel.signInInteractiveWithGoogle(context, roleForSignIn, authRepository)
                        .onFailure { err ->
                            signInError = err.message ?: "Google Sign-In failed."
                        }
                    isSigningIn = false
                }
            }
        )
    } else {
        key(user.uid) {
            AuthenticatedSessionHost(
                uid = user.uid,
                userEmail = user.email,
                preferredRoleIntent = if (pendingTeacherInviteCode != null || pendingTeacherCredentials != null) UserRole.TEACHER else preferredRoleIntent,
                pendingTeacherInviteCode = pendingTeacherInviteCode,
                pendingTeacherCredentials = pendingTeacherCredentials,
                onSwitchAccountWithInvite = onSetPendingTeacherInvite,
                onConsumeTeacherInvite = onConsumeTeacherInvite,
                onConsumeTeacherCredentials = { pendingTeacherCredentials = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedSessionHost(
    uid: String,
    userEmail: String?,
    preferredRoleIntent: UserRole,
    pendingTeacherInviteCode: String?,
    pendingTeacherCredentials: List<String>?,
    onSwitchAccountWithInvite: (String) -> Unit,
    onConsumeTeacherInvite: () -> Unit,
    onConsumeTeacherCredentials: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel(
        key = "auth_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AuthViewModel(
                    authRepository = AuthRepository(app),
                    schoolRepository = SchoolRepository(app),
                    initialRoleIntent = preferredRoleIntent,
                    onClearCredentialState = {
                        androidx.credentials.CredentialManager.create(app)
                            .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                    },
                    adminRepository = AdminRepository(app)
                )
            }
        }
    )

    LaunchedEffect(pendingTeacherInviteCode, uid) {
        val code = pendingTeacherInviteCode
        if (!code.isNullOrBlank()) {
            authViewModel.handleIncomingTeacherInvite(code)
            onConsumeTeacherInvite()
        }
    }

    LaunchedEffect(pendingTeacherCredentials, uid) {
        val creds = pendingTeacherCredentials
        if (creds != null && creds.size >= 4) {
            authViewModel.handleIncomingTeacherCredentials(creds[0], creds[1], creds[2], creds[3])
            onConsumeTeacherCredentials()
        }
    }

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isSuperAdmin = isSuperAdminEmail(authState.user?.email?.takeIf { it.isNotBlank() } ?: userEmail)
    var showAdminDashboard by rememberSaveable(uid) { mutableStateOf(false) }

    val activeSchool = authState.activeSchool
    val userProfile = authState.user
    val activeRole = authState.activeRole

    if (isSuperAdmin && showAdminDashboard) {
        val adminViewModel: AdminViewModel = viewModel(
            key = "admin_$uid",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    AdminViewModel(AdminRepository(app))
                }
            }
        )
        SuperAdminDashboardScreen(
            adminViewModel = adminViewModel,
            onClose = { showAdminDashboard = false }
        )
    } else if (!authState.isBrowsingSchools &&
        authState.destination == AuthDestination.AUTHENTICATED_WORKSPACE &&
        activeSchool != null &&
        userProfile != null &&
        activeRole != null
    ) {
        key("${uid}_${activeSchool.id}") {
            val hubViewModel: HubViewModel = viewModel(
                key = "hub_${uid}_${activeSchool.id}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        HubViewModel(SchoolRepository(app))
                    }
                }
            )
            val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()

            AuthenticatedWorkspaceHost(
                school = activeSchool,
                user = userProfile,
                role = activeRole,
                hubState = hubState,
                hubViewModel = hubViewModel,
                authViewModel = authViewModel,
                onOpenAdminDashboard = if (isSuperAdmin) ({ showAdminDashboard = true }) else null
            )
        }
    } else {
        AuthFlowHost(
            state = authState,
            viewModel = authViewModel,
            onOpenAdminDashboard = if (isSuperAdmin) ({ showAdminDashboard = true }) else null
        )
    }

    val blockedInviteCode = authState.inviteBlockedPrincipalCode
    if (!blockedInviteCode.isNullOrBlank()) {
        val isStudentLocked = userProfile?.accountIntent == UserRole.STUDENT.dbValue || activeRole == UserRole.STUDENT
        val dialogEmail = userProfile?.email ?: userEmail ?: "this account"
        AlertDialog(
            onDismissRequest = authViewModel::dismissInviteBlockedPrompt,
            title = { Text("Teacher Invite ($blockedInviteCode)") },
            text = {
                Text(
                    if (isStudentLocked) {
                        "This account ($dialogEmail) is permanently registered as a Student account and cannot become a Teacher or Principal. Sign out and sign in with another Gmail account to accept this Teacher invite."
                    } else {
                        "You are currently signed in as Principal ($dialogEmail) of ${activeSchool?.name ?: "your school"}. Sign out and sign in with another Gmail account to join via this Teacher invite link."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSwitchAccountWithInvite(blockedInviteCode)
                        authViewModel.dismissInviteBlockedPrompt()
                        authViewModel.logout()
                    }
                ) {
                    Text("Sign Out & Login with Gmail")
                }
            },
            dismissButton = {
                TextButton(onClick = authViewModel::dismissInviteBlockedPrompt) {
                    Text("Cancel")
                }
            }
        )
    } else if (authState.destination == AuthDestination.AUTHENTICATED_WORKSPACE && !authState.errorMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = authViewModel::clearMessages,
            title = { Text("Invite Notice") },
            text = { Text(authState.errorMessage ?: "") },
            confirmButton = {
                Button(onClick = authViewModel::clearMessages) {
                    Text("OK")
                }
            }
        )
    }
}
