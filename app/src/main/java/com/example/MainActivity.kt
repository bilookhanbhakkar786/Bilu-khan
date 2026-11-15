package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.data.model.UserRole
import com.example.data.remote.DataUriFetcher
import com.example.data.repository.AuthRepository
import com.example.data.repository.SchoolRepository
import com.example.ui.screens.AuthenticatedWorkspaceHost
import com.example.ui.screens.auth.AuthFlowHost
import com.example.ui.screens.auth.UnauthenticatedWelcomeScreen
import com.example.ui.theme.BLSchoolHubTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                AppRoot(auth = auth, firestore = firestore)
            }
        }
    }
}

@Composable
private fun AppRoot(
    auth: FirebaseAuth,
    firestore: FirebaseFirestore
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var preferredRoleIntent by rememberSaveable { mutableStateOf(UserRole.PRINCIPAL) }
    val user = currentUser

    if (user == null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val authRepository = remember(auth, firestore) { AuthRepository(auth, firestore) }
        var isSigningIn by remember { mutableStateOf(false) }
        var signInError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            // Silent check for previously authorized Google accounts without error popup
            AuthViewModel.attemptSilentGoogleSignIn(context, preferredRoleIntent, authRepository)
        }

        UnauthenticatedWelcomeScreen(
            selectedRoleIntent = preferredRoleIntent,
            isSigningIn = isSigningIn,
            errorMessage = signInError,
            onSelectRoleIntent = { preferredRoleIntent = it },
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
                preferredRoleIntent = preferredRoleIntent
            )
        }
    }
}

@Composable
private fun AuthenticatedSessionHost(
    uid: String,
    preferredRoleIntent: UserRole
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
                    }
                )
            }
        }
    )

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    val activeSchool = authState.activeSchool
    val userProfile = authState.user
    val activeRole = authState.activeRole

    if (authState.destination == AuthDestination.AUTHENTICATED_WORKSPACE &&
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
                authViewModel = authViewModel
            )
        }
    } else {
        AuthFlowHost(
            state = authState,
            viewModel = authViewModel
        )
    }
}
