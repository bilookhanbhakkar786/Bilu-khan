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
import com.example.data.model.PrincipalInvitation
import com.example.data.model.School
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherInviteLinks
import com.example.data.model.TeacherJoinRequest
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.AdminRepository
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
    val pendingPrincipalInvites: List<PrincipalInvitation> = emptyList(),
    val teacherRequests: List<TeacherJoinRequest> = emptyList(),
    val studentRequests: List<StudentAccountRequest> = emptyList(),
    val searchedSchools: List<School> = emptyList(),
    val pinnedSchools: List<School> = emptyList(),
    val isBrowsingSchools: Boolean = false,
    val isSearchingSchools: Boolean = false,
    val incomingTeacherInviteCode: String? = null,
    val inviteBlockedPrincipalCode: String? = null,
    val stagedTeacherCredentials: AuthViewModel.PendingTeacherCredentials? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val schoolRepository: SchoolRepository,
    private val initialRoleIntent: UserRole = UserRole.PRINCIPAL,
    private val onClearCredentialState: suspend () -> Unit = {},
    private val adminRepository: AdminRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(selectedRoleIntent = initialRoleIntent))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var hasRecordedSessionVisit = false
    private var pendingInviteToAutoAccept: String? = null
    private var pendingCredentialsToAutoAccept: PendingTeacherCredentials? = null

    data class PendingTeacherCredentials(
        val username: String,
        val accessPassword: String,
        val fullName: String = "",
        val phone: String = ""
    )

    init {
        loadCurrentUserContext()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun dismissInviteBlockedPrompt() {
        _uiState.update { it.copy(inviteBlockedPrincipalCode = null, errorMessage = null) }
    }

    fun switchRoleIntent(role: UserRole) {
        val user = _uiState.value.user ?: return
        if (user.accountIntent == UserRole.STUDENT.dbValue && role != UserRole.STUDENT) {
            _uiState.update {
                it.copy(
                    errorMessage = "Student accounts stay Student permanently and cannot switch to Teacher or Principal."
                )
            }
            return
        }
        val allowedRole = role
        val nextDest = when (allowedRole) {
            UserRole.PRINCIPAL -> AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL
            UserRole.TEACHER -> AuthDestination.ONBOARD_TEACHER_INVITE
            UserRole.STUDENT -> AuthDestination.ONBOARD_STUDENT_CONNECT
        }
        _uiState.update {
            it.copy(
                selectedRoleIntent = allowedRole,
                destination = nextDest,
                errorMessage = null,
                successMessage = null
            )
        }
        if (nextDest == AuthDestination.ONBOARD_STUDENT_CONNECT) {
            searchSchools("")
        }
        viewModelScope.launch {
            authRepository.updateAccountIntent(user.id, allowedRole)
        }
    }

    fun handleIncomingTeacherInvite(rawCodeOrUrl: String) {
        val code = TeacherInviteLinks.extractInviteCode(rawCodeOrUrl) ?: return
        _uiState.update { it.copy(incomingTeacherInviteCode = code) }
        val user = _uiState.value.user
        if (user == null) {
            pendingInviteToAutoAccept = code
            return
        }
        if (user.accountIntent == UserRole.STUDENT.dbValue || _uiState.value.activeRole == UserRole.STUDENT) {
            _uiState.update {
                it.copy(
                    inviteBlockedPrincipalCode = code,
                    errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to accept this Teacher invite."
                )
            }
            return
        }
        if (_uiState.value.activeRole == UserRole.PRINCIPAL && _uiState.value.activeSchool != null) {
            _uiState.update {
                it.copy(
                    inviteBlockedPrincipalCode = code,
                    errorMessage = "You already own ${_uiState.value.activeSchool?.name} as Principal. Sign out and sign in with another Gmail account to join as a Teacher."
                )
            }
            return
        }
        pendingInviteToAutoAccept = null
        acceptTeacherInvitation(code)
    }

    fun handleIncomingTeacherCredentials(
        username: String,
        accessPassword: String,
        fullName: String = "",
        phone: String = ""
    ) {
        val creds = PendingTeacherCredentials(
            username = username.trim().removePrefix("@").trim().take(40),
            accessPassword = accessPassword.trim().take(100),
            fullName = fullName.trim().take(120),
            phone = phone.trim().take(40)
        )
        _uiState.update { it.copy(stagedTeacherCredentials = creds) }
        val user = _uiState.value.user
        if (user == null) {
            pendingCredentialsToAutoAccept = creds
            return
        }
        if (user.accountIntent == UserRole.STUDENT.dbValue || _uiState.value.activeRole == UserRole.STUDENT) {
            _uiState.update {
                it.copy(
                    errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to join with Teacher credentials."
                )
            }
            return
        }
        if (_uiState.value.activeRole == UserRole.PRINCIPAL && _uiState.value.activeSchool != null) {
            _uiState.update {
                it.copy(
                    errorMessage = "You already own ${_uiState.value.activeSchool?.name} as Principal. Sign out and sign in with another Gmail account to join as a Teacher."
                )
            }
            return
        }
        pendingCredentialsToAutoAccept = null
        acceptTeacherByCredentials(
            username = creds.username,
            accessPassword = creds.accessPassword,
            fullName = creds.fullName,
            phone = creds.phone
        )
    }

    private fun loadCurrentUserContext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.ensureCurrentAccountContext(initialRoleIntent)
                .onSuccess { ctx ->
                    if (ctx != null) {
                        applyResolvedContext(ctx)
                        val queuedCreds = pendingCredentialsToAutoAccept
                        val queuedInvite = pendingInviteToAutoAccept
                        if (queuedCreds != null) {
                            pendingCredentialsToAutoAccept = null
                            if (ctx.user.accountIntent == UserRole.STUDENT.dbValue || ctx.activeRole == UserRole.STUDENT) {
                                _uiState.update {
                                    it.copy(
                                        errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to join with Teacher credentials."
                                    )
                                }
                            } else if (ctx.activeRole != UserRole.PRINCIPAL) {
                                acceptTeacherByCredentials(
                                    username = queuedCreds.username,
                                    accessPassword = queuedCreds.accessPassword,
                                    fullName = queuedCreds.fullName,
                                    phone = queuedCreds.phone
                                )
                            } else {
                                _uiState.update {
                                    it.copy(
                                        errorMessage = "You already own ${ctx.activeSchool?.name} as Principal. Sign out and sign in with another Gmail account to join as a Teacher."
                                    )
                                }
                            }
                        } else if (!queuedInvite.isNullOrBlank()) {
                            pendingInviteToAutoAccept = null
                            if (ctx.user.accountIntent == UserRole.STUDENT.dbValue || ctx.activeRole == UserRole.STUDENT) {
                                _uiState.update {
                                    it.copy(
                                        inviteBlockedPrincipalCode = queuedInvite,
                                        errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to accept this Teacher invite."
                                    )
                                }
                            } else if (ctx.activeRole != UserRole.PRINCIPAL) {
                                acceptTeacherInvitation(queuedInvite)
                            } else {
                                _uiState.update {
                                    it.copy(
                                        inviteBlockedPrincipalCode = queuedInvite,
                                        errorMessage = "You already own ${ctx.activeSchool?.name} as Principal. Sign out and sign in with another Gmail account to join as a Teacher."
                                    )
                                }
                            }
                        }
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
        val isLockedStudent = ctx.user.accountIntent == UserRole.STUDENT.dbValue
        val hasInviteOrStagedCreds = !isLockedStudent && (
            _uiState.value.incomingTeacherInviteCode != null ||
                ctx.pendingTeacherInvites.isNotEmpty() ||
                _uiState.value.stagedTeacherCredentials != null ||
                ctx.user.accountIntent == UserRole.TEACHER.dbValue
            )
        val nextDest = when {
            ctx.activeSchool != null && ctx.activeRole != null -> AuthDestination.AUTHENTICATED_WORKSPACE
            hasInviteOrStagedCreds -> AuthDestination.ONBOARD_TEACHER_INVITE
            !isLockedStudent && ctx.user.accountIntent == UserRole.PRINCIPAL.dbValue -> AuthDestination.ONBOARD_PRINCIPAL_CREATE_SCHOOL
            else -> AuthDestination.ONBOARD_STUDENT_CONNECT
        }
        val normalizedIntent = when {
            isLockedStudent -> UserRole.STUDENT
            nextDest == AuthDestination.ONBOARD_TEACHER_INVITE -> UserRole.TEACHER
            ctx.user.accountIntent == UserRole.PRINCIPAL.dbValue -> UserRole.PRINCIPAL
            else -> UserRole.STUDENT
        }
        _uiState.update {
            it.copy(
                destination = nextDest,
                isLoading = false,
                isBrowsingSchools = if (ctx.activeSchool == null) false else it.isBrowsingSchools,
                user = ctx.user,
                activeSchool = ctx.activeSchool,
                activeRole = ctx.activeRole,
                pinnedSchools = ctx.pinnedSchools,
                pendingTeacherInvites = if (isLockedStudent) emptyList() else ctx.pendingTeacherInvites,
                pendingPrincipalInvites = if (isLockedStudent) emptyList() else ctx.pendingPrincipalInvites,
                teacherRequests = emptyList(),
                studentRequests = emptyList(),
                selectedRoleIntent = normalizedIntent,
                errorMessage = if (ctx.hiddenActiveSchoolBlocked && ctx.activeSchool == null) {
                    "Your previously selected school was hidden by its Principal and is only accessible to its staff."
                } else {
                    it.errorMessage
                }
            )
        }
        if (!hasRecordedSessionVisit && adminRepository != null) {
            hasRecordedSessionVisit = true
            viewModelScope.launch {
                adminRepository.recordAppVisit(ctx.user, ctx.activeSchool, ctx.activeRole)
            }
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
        val user = _uiState.value.user ?: return
        if (user.accountIntent == UserRole.STUDENT.dbValue) {
            _uiState.update { it.copy(errorMessage = "Student accounts stay Student permanently and cannot create a school as Principal.") }
            return
        }
        val uid = user.id
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

    fun stageTeacherCredentials(username: String, accessPassword: String, fullName: String = "", phone: String = "") {
        val creds = PendingTeacherCredentials(
            username = username.trim().removePrefix("@").trim().take(40),
            accessPassword = accessPassword.trim().take(100),
            fullName = fullName.trim().take(120),
            phone = phone.trim().take(40)
        )
        pendingCredentialsToAutoAccept = creds
        _uiState.update { it.copy(stagedTeacherCredentials = creds) }
    }

    fun acceptTeacherInvitation(inviteCode: String, teacherName: String = "", teacherPhone: String = "") {
        val cleanCode = TeacherInviteLinks.extractInviteCode(inviteCode) ?: inviteCode.trim()
        if (cleanCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please open a valid Teacher invite link or paste the invite code.") }
            return
        }
        val user = _uiState.value.user ?: return
        if (user.accountIntent == UserRole.STUDENT.dbValue) {
            _uiState.update {
                it.copy(
                    inviteBlockedPrincipalCode = cleanCode,
                    errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to accept this invite."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            schoolRepository.acceptTeacherInvitation(
                inviteCode = cleanCode,
                userId = user.id,
                fallbackEmail = user.email,
                fallbackName = teacherName.ifBlank { user.fullName },
                fallbackPhone = teacherPhone.ifBlank { user.phone }
            ).onSuccess {
                _uiState.update { it.copy(incomingTeacherInviteCode = null) }
                authRepository.resolveAccountContext(user.id)
                    .onSuccess { ctx ->
                        applyResolvedContext(ctx)
                        _uiState.update {
                            it.copy(successMessage = "Welcome! You joined ${ctx.activeSchool?.name ?: "the school"} as Teacher.")
                        }
                    }
                    .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        destination = if (it.activeSchool == null) AuthDestination.ONBOARD_TEACHER_INVITE else it.destination,
                        errorMessage = err.message ?: "Invalid invite link or code."
                    )
                }
            }
        }
    }

    fun acceptTeacherByCredentials(
        username: String,
        accessPassword: String,
        fullName: String = "",
        phone: String = ""
    ) {
        pendingCredentialsToAutoAccept = null
        val cleanUsername = username.trim().removePrefix("@").trim().take(40)
        val cleanPassword = accessPassword.trim().take(100)
        if (cleanUsername.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your username.") }
            return
        }
        if (cleanPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your password or access code.") }
            return
        }
        val user = _uiState.value.user ?: return
        if (user.accountIntent == UserRole.STUDENT.dbValue) {
            _uiState.update {
                it.copy(
                    errorMessage = "Student accounts stay Student permanently and cannot become a Teacher. Sign out and sign in with another Gmail account to join as Teacher."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            schoolRepository.acceptTeacherByCredentials(
                username = cleanUsername,
                accessPassword = cleanPassword,
                userId = user.id,
                fallbackEmail = user.email,
                teacherEnteredName = fullName.ifBlank { user.fullName },
                teacherEnteredPhone = phone.ifBlank { user.phone }
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        incomingTeacherInviteCode = null,
                        stagedTeacherCredentials = null
                    )
                }
                authRepository.resolveAccountContext(user.id)
                    .onSuccess { ctx ->
                        applyResolvedContext(ctx)
                        _uiState.update {
                            it.copy(successMessage = "Welcome! You joined ${ctx.activeSchool?.name ?: "the school"} as Teacher.")
                        }
                    }
                    .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        destination = if (it.activeSchool == null) AuthDestination.ONBOARD_TEACHER_INVITE else it.destination,
                        errorMessage = err.message ?: "Invalid username or password."
                    )
                }
            }
        }
    }

    fun acceptPrincipalInvitation(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a Principal transfer invite code.") }
            return
        }
        val user = _uiState.value.user ?: return
        if (user.accountIntent == UserRole.STUDENT.dbValue) {
            _uiState.update { it.copy(errorMessage = "Student accounts stay Student permanently and cannot become a Principal.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            schoolRepository.acceptPrincipalInvitation(
                inviteCode = inviteCode,
                newPrincipalUid = user.id,
                fallbackEmail = user.email,
                fallbackName = user.fullName
            ).onSuccess { school ->
                authRepository.resolveAccountContext(user.id)
                    .onSuccess { ctx ->
                        applyResolvedContext(ctx)
                        _uiState.update {
                            it.copy(successMessage = "You are now the Principal of ${school.name}!")
                        }
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                    }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Could not accept Principal invite.") }
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

    fun enterPublicSchoolAsStudent(school: School) {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            schoolRepository.enterSchoolAsStudent(school.id, user.id)
                .onSuccess { enteredSchool ->
                    _uiState.update { it.copy(isBrowsingSchools = false) }
                    authRepository.resolveAccountContext(user.id)
                        .onSuccess { ctx ->
                            applyResolvedContext(ctx)
                            _uiState.update {
                                it.copy(
                                    isBrowsingSchools = false,
                                    successMessage = "Opened ${enteredSchool.name}."
                                )
                            }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                        }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "This school could not be opened."
                        )
                    }
                    searchSchools("")
                }
        }
    }

    fun switchSchoolAsStudent() {
        _uiState.update {
            it.copy(
                isBrowsingSchools = true,
                selectedRoleIntent = UserRole.STUDENT,
                errorMessage = null,
                successMessage = null
            )
        }
        searchSchools("")
    }

    fun returnToActiveStudentSchool() {
        _uiState.update {
            it.copy(
                isBrowsingSchools = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun togglePinSchool(school: School) {
        val user = _uiState.value.user ?: return
        val currentlyPinned = _uiState.value.pinnedSchools.any { it.id == school.id } ||
            user.pinnedSchoolIds.contains(school.id)
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, successMessage = null) }
            if (currentlyPinned) {
                schoolRepository.unpinSchoolForStudent(user.id, school.id)
                    .onSuccess { newIds ->
                        _uiState.update { state ->
                            val newPinned = state.pinnedSchools.filterNot { it.id == school.id }
                            state.copy(
                                user = state.user?.copy(pinnedSchoolIds = newIds),
                                pinnedSchools = newPinned,
                                successMessage = "Unpinned ${school.name}."
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(errorMessage = err.message ?: "Could not unpin school.") }
                    }
            } else {
                schoolRepository.pinSchoolForStudent(user.id, school.id)
                    .onSuccess { newIds ->
                        _uiState.update { state ->
                            val lookup = (state.pinnedSchools + state.searchedSchools + listOfNotNull(state.activeSchool))
                                .associateBy { it.id }
                            val newPinned = newIds.mapNotNull { id -> lookup[id] }
                            state.copy(
                                user = state.user?.copy(
                                    pinnedSchoolIds = newIds,
                                    activeSchoolId = state.user.activeSchoolId ?: school.id
                                ),
                                pinnedSchools = newPinned,
                                successMessage = "Pinned ${school.name} (${newIds.size}/3)."
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(errorMessage = err.message ?: "You can pin up to 3 schools only.") }
                    }
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

    fun deleteMyNonPrincipalAccount(activityContext: Context?, onComplete: () -> Unit = {}) {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (activityContext != null) {
                val lastSignIn = authRepository.currentLastSignInTimestamp()
                if (System.currentTimeMillis() - lastSignIn > 4 * 60 * 1000L) {
                    val reauth = reauthenticateInteractiveWithGoogle(activityContext, authRepository)
                    if (reauth.isFailure) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = reauth.exceptionOrNull()?.message ?: "Please confirm Google Sign-In to delete your account."
                            )
                        }
                        return@launch
                    }
                }
            }
            schoolRepository.deleteNonPrincipalAccountAndData(user.id)
                .onSuccess {
                    authRepository.logout(onClearCredentialState)
                    onComplete()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Could not delete your account."
                        )
                    }
                }
        }
    }

    fun ensureFreshAuthThenRun(activityContext: Context?, action: (() -> Unit) -> Unit) {
        viewModelScope.launch {
            if (activityContext != null) {
                val lastSignIn = authRepository.currentLastSignInTimestamp()
                if (System.currentTimeMillis() - lastSignIn > 4 * 60 * 1000L) {
                    val reauth = reauthenticateInteractiveWithGoogle(activityContext, authRepository)
                    if (reauth.isFailure) {
                        _uiState.update {
                            it.copy(errorMessage = reauth.exceptionOrNull()?.message ?: "Please confirm Google Sign-In to continue.")
                        }
                        return@launch
                    }
                }
            }
            action { logout() }
        }
    }

    companion object {
        suspend fun reauthenticateInteractiveWithGoogle(
            activityContext: Context,
            authRepository: AuthRepository
        ): Result<Unit> {
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
                    authRepository.reauthenticateWithGoogleIdToken(googleCred.idToken)
                } else {
                    Result.failure(IllegalStateException("Unsupported Google credential type."))
                }
            } catch (_: GetCredentialCancellationException) {
                Result.failure(IllegalStateException("Verification cancelled."))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.localizedMessage ?: "Re-authentication failed.", e))
            }
        }

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
