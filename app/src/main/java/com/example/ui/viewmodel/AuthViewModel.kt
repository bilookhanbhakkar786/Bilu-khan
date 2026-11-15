package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.School
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherInvitation
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.AuthRepository
import com.example.data.repository.ResolvedAccountContext
import com.example.data.repository.SchoolRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthDestination {
    SPLASH,
    ONBOARD_PRINCIPAL_CREATE_SCHOOL,
    ONBOARD_TEACHER_INVITE,
    ONBOARD_STUDENT_CONNECT,
    AUTHENTICATED_WORKSPACE
}

data class AuthUiState(
    val destination: AuthDestination = AuthDestination.SPLASH,
    val selectedRoleIntent: UserRole = UserRole.PRINCIPAL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val user: UserProfile? = null,
    val activeSchool: School? = null,
    val activeRole: UserRole? = null,
    val pendingTeacherInvites: List<TeacherInvitation> = emptyList(),
    val studentRequests: List<StudentAccountRequest> = emptyList(),
    val searchedSchools: List<School> = emptyList(),
    val isSearchingSchools: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val schoolRepository: SchoolRepository,
    private val initialRoleIntent: UserRole = UserRole.PRINCIPAL,
    private val onClearCredentialState: suspend () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(selectedRoleIntent = initialRoleIntent))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUserContext()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun switchRoleIntent(role: UserRole) {
        val uid = _uiState.value.user?.id ?: return
        val nextDest = when (role) {
            UserRole.PRINCIPAL -> AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL
            UserRole.TEACHER -> AuthDestination.ONBOARD_TEACHER_INVITE
            UserRole.STUDENT -> AuthDestination.ONBOARD_STUDENT_CONNECT
        }
        _uiState.update {
            it.copy(
                selectedRoleIntent = role,
                destination = nextDest,
                errorMessage = null,
                successMessage = null
            )
        }
        if (nextDest == AuthDestination.ONBOARD_STUDENT_CONNECT) {
            searchSchools("")
        }
        viewModelScope.launch {
            authRepository.updateAccountIntent(uid, role)
        }
    }

    private fun loadCurrentUserContext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.ensureCurrentAccountContext(initialRoleIntent)
                .onSuccess { ctx ->
                    if (ctx != null) {
                        applyResolvedContext(ctx)
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            destination = AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL,
                            errorMessage = err.message ?: "Could not load account profile."
                        )
                    }
                }
        }
    }

    private fun applyResolvedContext(ctx: ResolvedAccountContext) {
        val nextDest = when {
            ctx.activeSchool != null && ctx.activeRole != null -> AuthDestination.AUTHENTICATED_WORKSPACE
            ctx.user.accountIntent == UserRole.PRINCIPAL.dbValue -> AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL
            ctx.user.accountIntent == UserRole.TEACHER.dbValue -> AuthDestination.ONBOARD_TEACHER_INVITE
            else -> AuthDestination.ONBOARD_STUDENT_CONNECT
        }
        _uiState.update {
            it.copy(
                destination = nextDest,
                isLoading = false,
                user = ctx.user,
                activeSchool = ctx.activeSchool,
                activeRole = ctx.activeRole,
                pendingTeacherInvites = ctx.pendingTeacherInvites,
                studentRequests = ctx.studentRequests,
                selectedRoleIntent = UserRole.fromDb(ctx.user.accountIntent)
            )
        }
        if (nextDest == AuthDestination.ONBOARD_STUDENT_CONNECT) {
            searchSchools("")
        }
    }

    fun createSchoolAsPrincipal(
        name: String,
        schoolType: String,
        address: String,
        city: String,
        contactNumber: String,
        email: String,
        description: String,
        establishedYear: Int?,
        website: String?,
        logoUri: Uri?,
        bannerUri: Uri?
    ) {
        if (name.trim().length < 2 || city.isBlank() || contactNumber.isBlank() || email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill School Name (2+ chars), City, Contact Number, and Email.") }
            return
        }
        if (establishedYear != null && establishedYear !in 1800..2100) {
            _uiState.update { it.copy(errorMessage = "Established year must be between 1800 and 2100.") }
            return
        }
        val uid = _uiState.value.user?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var logoUrl: String? = null
            var bannerUrl: String? = null

            if (logoUri != null) {
                schoolRepository.uploadMedia(logoUri, "logos/$uid")
                    .onSuccess { logoUrl = it }
                    .onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        return@launch
                    }
            }
            if (bannerUri != null) {
                schoolRepository.uploadMedia(bannerUri, "banners/$uid")
                    .onSuccess { bannerUrl = it }
                    .onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        return@launch
                    }
            }

            schoolRepository.createSchoolWithPrincipal(
                ownerId = uid,
                name = name,
                schoolType = schoolType,
                address = address,
                city = city,
                contactNumber = contactNumber,
                email = email,
                description = description,
                establishedYear = establishedYear,
                website = website,
                logoUrl = logoUrl,
                bannerUrl = bannerUrl
            ).onSuccess { school ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeSchool = school,
                        activeRole = UserRole.PRINCIPAL,
                        destination = AuthDestination.AUTHENTICATED_WORKSPACE,
                        successMessage = "${school.name} created! School ID: ${school.schoolCode}"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Could not create school.") }
            }
        }
    }

    fun acceptTeacherInvitation(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your teacher invitation code.") }
            return
        }
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            schoolRepository.acceptTeacherInvitation(
                inviteCode = inviteCode,
                userId = user.id,
                fallbackEmail = user.email,
                fallbackName = user.fullName
            ).onSuccess {
                authRepository.resolveAccountContext(user.id)
                    .onSuccess { ctx -> applyResolvedContext(ctx) }
                    .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Invalid invite code.") }
            }
        }
    }

    fun searchSchools(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSchools = true) }
            schoolRepository.searchSchools(query)
                .onSuccess { list -> _uiState.update { it.copy(isSearchingSchools = false, searchedSchools = list) } }
                .onFailure { _uiState.update { it.copy(isSearchingSchools = false) } }
        }
    }

    fun requestStudentConnection(
        school: School,
        rollNumber: String,
        fullName: String,
        fatherName: String,
        sessionName: String,
        className: String,
        sectionName: String
    ) {
        val user = _uiState.value.user ?: return
        if (rollNumber.isBlank() || fullName.isBlank() || fatherName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Roll Number, Full Name, and Father's Name are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            schoolRepository.requestStudentConnection(
                schoolId = school.id,
                schoolOwnerId = school.ownerId,
                schoolName = school.name,
                userId = user.id,
                rollNumber = rollNumber,
                fullName = fullName,
                fatherName = fatherName,
                sessionName = sessionName,
                className = className,
                sectionName = sectionName
            ).onSuccess { req ->
                val updated = listOf(req) + _uiState.value.studentRequests.filterNot { it.id == req.id }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        studentRequests = updated,
                        successMessage = "Connection request sent to ${school.name}! Waiting for Principal approval."
                    )
                }
                refreshContext()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Request failed.") }
            }
        }
    }

    fun refreshContext() {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.resolveAccountContext(user.id)
                .onSuccess { ctx -> applyResolvedContext(ctx) }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun updateMyProfile(fullName: String, phone: String, avatarUri: Uri?) {
        val user = _uiState.value.user ?: return
        if (fullName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Full name is required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var avatarUrl = user.avatarUrl
            if (avatarUri != null) {
                schoolRepository.uploadMedia(avatarUri, "avatars/${user.id}")
                    .onSuccess { avatarUrl = it }
                    .onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        return@launch
                    }
            }
            authRepository.updateMyProfile(user.id, fullName, phone, avatarUrl)
                .onSuccess { updated ->
                    _uiState.update { it.copy(isLoading = false, user = updated, successMessage = "Profile updated.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout(onClearCredentialState)
        }
    }

    companion object {
        suspend fun signInInteractiveWithGoogle(
            activityContext: Context,
            preferredRole: UserRole,
            authRepository: AuthRepository
        ): Result<ResolvedAccountContext> {
            return try {
                val credentialManager = CredentialManager.create(activityContext)
                val option = GetSignInWithGoogleOption.Builder(
                    activityContext.getString(R.string.default_web_client_id)
                ).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = credentialManager.getCredential(activityContext, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    authRepository.signInWithGoogleIdToken(googleCred.idToken, preferredRole)
                } else {
                    Result.failure(IllegalStateException("Unsupported Google credential type."))
                }
            } catch (_: GetCredentialCancellationException) {
                Result.failure(IllegalStateException("Sign-in cancelled."))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.localizedMessage ?: "Google Sign-In failed.", e))
            }
        }

        suspend fun attemptSilentGoogleSignIn(
            activityContext: Context,
            preferredRole: UserRole,
            authRepository: AuthRepository
        ): Result<ResolvedAccountContext?> {
            return try {
                val credentialManager = CredentialManager.create(activityContext)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(activityContext.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                val result = credentialManager.getCredential(activityContext, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    authRepository.signInWithGoogleIdToken(googleCred.idToken, preferredRole).map { it }
                } else {
                    Result.success(null)
                }
            } catch (_: Exception) {
                Result.success(null)
            }
        }
    }
}
