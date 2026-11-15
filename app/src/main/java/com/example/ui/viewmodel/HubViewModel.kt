package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AcademicSession
import com.example.data.model.AnnouncementComment
import com.example.data.model.AnnouncementPost
import com.example.data.model.AppNotification
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassSection
import com.example.data.model.HomeworkItem
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.StudentAccountRequest
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherAssignment
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherPermissions
import com.example.data.model.TeacherProfile
import com.example.data.model.TestExam
import com.example.data.model.TestResult
import com.example.data.model.TimetableSlot
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.ReportExporter
import com.example.data.repository.SchoolRepository
import com.example.data.repository.SchoolWorkspaceBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class HubUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val school: School? = null,
    val currentUser: UserProfile? = null,
    val role: UserRole = UserRole.STUDENT,
    val selectedSessionId: String? = null,
    val sessions: List<AcademicSession> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val sections: List<ClassSection> = emptyList(),
    val teachers: List<TeacherProfile> = emptyList(),
    val teacherInvitations: List<TeacherInvitation> = emptyList(),
    val teacherAssignments: List<TeacherAssignment> = emptyList(),
    val students: List<StudentRecord> = emptyList(),
    val studentRequests: List<StudentAccountRequest> = emptyList(),
    val attendanceSessions: List<AttendanceSession> = emptyList(),
    val attendanceRecords: List<AttendanceRecord> = emptyList(),
    val tests: List<TestExam> = emptyList(),
    val testResults: List<TestResult> = emptyList(),
    val announcements: List<AnnouncementPost> = emptyList(),
    val commentsByAnnouncement: Map<String, List<AnnouncementComment>> = emptyMap(),
    val timetables: List<TimetableSlot> = emptyList(),
    val homework: List<HomeworkItem> = emptyList(),
    val notifications: List<AppNotification> = emptyList()
) {
    val activeAcademicSession: AcademicSession?
        get() = sessions.find { it.id == selectedSessionId }
            ?: sessions.find { it.isCurrent }
            ?: sessions.firstOrNull()

    val currentTeacherProfile: TeacherProfile?
        get() = teachers.find { it.userId == currentUser?.id }

    val currentTeacherPermissions: TeacherPermissions
        get() = if (role == UserRole.PRINCIPAL) {
            TeacherPermissions(
                viewStudents = true,
                editStudents = true,
                markAttendance = true,
                editAttendance = true,
                createTests = true,
                enterMarks = true,
                publishAnnouncements = true,
                viewReports = true
            )
        } else {
            currentTeacherProfile?.permissions ?: TeacherPermissions(
                viewStudents = false,
                editStudents = false,
                markAttendance = false,
                editAttendance = false,
                createTests = false,
                enterMarks = false,
                publishAnnouncements = false,
                viewReports = false
            )
        }

    val linkedStudentRecord: StudentRecord?
        get() {
            val approvedReq = studentRequests.find {
                it.userId == currentUser?.id && it.status == "approved" && it.studentId != null
            } ?: return students.firstOrNull { role == UserRole.STUDENT && it.linkedUserId == currentUser?.id }
            return students.find { it.id == approvedReq.studentId }
                ?: students.firstOrNull { it.linkedUserId == currentUser?.id }
        }

    val sessionClasses: List<SchoolClass>
        get() {
            val sid = activeAcademicSession?.id ?: return emptyList()
            return classes.filter { it.sessionId == sid }
        }

    val sessionSections: List<ClassSection>
        get() {
            val sid = activeAcademicSession?.id ?: return emptyList()
            val allInSession = sections.filter { it.sessionId == sid }
            if (role == UserRole.TEACHER) {
                val tp = currentTeacherProfile ?: return emptyList()
                val assignedIds = teacherAssignments.filter { it.teacherProfileId == tp.id }.map { it.sectionId }.toSet()
                return allInSession.filter { it.id in assignedIds || it.classTeacherId == tp.id }
            }
            return allInSession
        }

    val sessionStudents: List<StudentRecord>
        get() {
            val sid = activeAcademicSession?.id ?: return emptyList()
            return students.filter { it.sessionId == sid && it.status == "active" }
        }

    val unreadNotificationsCount: Int
        get() = notifications.count { !it.isRead }
}

class HubViewModel(
    private val repository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    fun clearFeedback() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun loadWorkspace(school: School, user: UserProfile, role: UserRole) {
        _uiState.update {
            it.copy(
                isLoading = true,
                school = school,
                currentUser = user,
                role = role,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.loadSchoolWorkspace(school.id, user.id)
                .onSuccess { bundle -> applyBundle(bundle) }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to load school data.") }
                }
        }
    }

    fun refreshWorkspace() {
        val s = _uiState.value.school ?: return
        val u = _uiState.value.currentUser ?: return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            repository.loadSchoolWorkspace(s.id, u.id)
                .onSuccess { bundle -> applyBundle(bundle) }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshing = false, errorMessage = err.message) }
                }
        }
    }

    private fun applyBundle(bundle: SchoolWorkspaceBundle) {
        val currentSelected = _uiState.value.selectedSessionId
        val validSelected = bundle.sessions.find { it.id == currentSelected }?.id
            ?: bundle.sessions.find { it.isCurrent }?.id
            ?: bundle.sessions.firstOrNull()?.id

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                school = bundle.school,
                selectedSessionId = validSelected,
                sessions = bundle.sessions,
                classes = bundle.classes,
                sections = bundle.sections,
                teachers = bundle.teachers,
                teacherInvitations = bundle.teacherInvitations,
                teacherAssignments = bundle.teacherAssignments,
                students = bundle.students,
                studentRequests = bundle.studentRequests,
                attendanceSessions = bundle.attendanceSessions,
                attendanceRecords = bundle.attendanceRecords,
                tests = bundle.tests,
                testResults = bundle.testResults,
                announcements = bundle.announcements,
                timetables = bundle.timetables,
                homework = bundle.homework,
                notifications = bundle.notifications
            )
        }
    }

    fun selectAcademicSession(sessionId: String) {
        _uiState.update { it.copy(selectedSessionId = sessionId) }
    }

    fun createAcademicSession(name: String, startDate: String?, endDate: String?, makeCurrent: Boolean) {
        val schoolId = _uiState.value.school?.id ?: return
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Session name is required (e.g., 2026-2027).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.createAcademicSession(schoolId, name, startDate, endDate, makeCurrent)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            selectedSessionId = created.id,
                            successMessage = "Academic session ${created.name} created."
                        )
                    }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun setCurrentAcademicSession(sessionId: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.setCurrentSession(schoolId, sessionId)
                .onSuccess {
                    _uiState.update { it.copy(selectedSessionId = sessionId, successMessage = "Current academic session updated.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createClassAndSection(
        sessionId: String,
        className: String,
        sectionName: String,
        classTeacherId: String?,
        description: String
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        if (className.isBlank() || sectionName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Class name and Section are required.") }
            return
        }
        val existingClass = _uiState.value.classes.find {
            it.sessionId == sessionId && it.name.equals(className.trim(), ignoreCase = true)
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.createClassWithSection(
                schoolId, sessionId, className, sectionName, classTeacherId, description, existingClass?.id
            ).onSuccess {
                _uiState.update { it.copy(successMessage = "Class $className - Section $sectionName ready.") }
                refreshWorkspace()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun updateSectionSettings(sectionId: String, name: String, description: String, classTeacherId: String?) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.updateSection(schoolId, sectionId, name, description, classTeacherId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Section updated.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun updateSchoolProfile(
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
        bannerUri: Uri?,
        galleryUriToAdd: Uri?
    ) {
        val school = _uiState.value.school ?: return
        if (establishedYear != null && establishedYear !in 1800..2100) {
            _uiState.update { it.copy(errorMessage = "Established year must be between 1800 and 2100.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var logoUrl = school.logoUrl
            var bannerUrl = school.bannerUrl
            val gallery = school.galleryUrls.toMutableList()

            if (logoUri != null) {
                repository.uploadMedia(logoUri, "logos/${school.id}").onSuccess { logoUrl = it }
            }
            if (bannerUri != null) {
                repository.uploadMedia(bannerUri, "banners/${school.id}").onSuccess { bannerUrl = it }
            }
            if (galleryUriToAdd != null) {
                repository.uploadMedia(galleryUriToAdd, "gallery/${school.id}").onSuccess { gallery.add(it) }
            }

            repository.updateSchool(
                schoolId = school.id,
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
                bannerUrl = bannerUrl,
                galleryUrls = gallery
            ).onSuccess { updated ->
                _uiState.update { it.copy(isLoading = false, school = updated, successMessage = "School profile saved.") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun saveStudent(
        existingStudent: StudentRecord?,
        sessionId: String,
        classId: String,
        sectionId: String,
        fullName: String,
        fatherName: String,
        rollNumber: String,
        dateOfBirth: String?,
        gender: String,
        parentName: String,
        parentContact: String,
        address: String,
        admissionDate: String,
        email: String?,
        avatarUri: Uri?
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        if (fullName.isBlank() || fatherName.isBlank() || rollNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Student Name, Father's Name, and Roll Number are required.") }
            return
        }
        val duplicate = _uiState.value.students.any {
            it.id != existingStudent?.id &&
                it.sessionId == sessionId &&
                it.sectionId == sectionId &&
                it.rollNumber.equals(rollNumber.trim(), ignoreCase = true)
        }
        if (duplicate) {
            _uiState.update { it.copy(errorMessage = "Roll Number $rollNumber already exists in this section.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var avatarUrl = existingStudent?.avatarUrl
            if (avatarUri != null) {
                repository.uploadMedia(avatarUri, "students/$schoolId").onSuccess { avatarUrl = it }
            }
            repository.saveStudent(
                schoolId = schoolId,
                existingId = existingStudent?.id,
                sessionId = sessionId,
                classId = classId,
                sectionId = sectionId,
                rollNumber = rollNumber,
                fullName = fullName,
                fatherName = fatherName,
                dateOfBirth = dateOfBirth,
                gender = gender,
                parentName = parentName,
                parentContact = parentContact,
                address = address,
                admissionDate = admissionDate.ifBlank { LocalDate.now().toString() },
                email = email,
                avatarUrl = avatarUrl
            ).onSuccess { saved ->
                _uiState.update { it.copy(successMessage = "Saved student ${saved.fullName} (${saved.studentCode}).") }
                refreshWorkspace()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun deleteStudent(studentId: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.deleteStudent(schoolId, studentId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Student removed.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun transferStudent(student: StudentRecord, targetClassId: String, targetSectionId: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.transferStudent(schoolId, student.id, targetClassId, targetSectionId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "${student.fullName} transferred successfully.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun promoteStudent(
        student: StudentRecord,
        targetSessionId: String,
        targetClassId: String,
        targetSectionId: String,
        newRollNumber: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.promoteStudent(student, targetSessionId, targetClassId, targetSectionId, newRollNumber)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "${student.fullName} promoted while preserving historical records.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun approveStudentConnection(requestId: String, studentId: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.approveStudentConnection(schoolId, requestId, studentId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Student connection approved and verified!") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun inviteTeacher(fullName: String, email: String, phone: String, permissions: TeacherPermissions) {
        val schoolId = _uiState.value.school?.id ?: return
        val uid = _uiState.value.currentUser?.id ?: return
        if (fullName.isBlank() || email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Teacher name and email are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.inviteTeacher(schoolId, uid, fullName, email, phone, permissions)
                .onSuccess { inv ->
                    _uiState.update { it.copy(successMessage = "Invitation sent! Code: ${inv.inviteCode}") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun updateTeacherPermissions(teacher: TeacherProfile, isActive: Boolean, permissions: TeacherPermissions) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.updateTeacherPermissionsAndStatus(schoolId, teacher.id, isActive, permissions)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Updated ${teacher.fullName}'s permissions.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun assignTeacherToSection(teacherProfileId: String, sectionId: String, subject: String) {
        val schoolId = _uiState.value.school?.id ?: return
        val sessionId = _uiState.value.activeAcademicSession?.id ?: return
        viewModelScope.launch {
            repository.assignTeacherToSection(schoolId, sessionId, teacherProfileId, sectionId, subject)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Teacher assigned to section for $subject.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun saveAttendance(
        existingSessionId: String?,
        section: ClassSection,
        attendanceDate: String,
        subject: String,
        sessionSlot: String,
        statusByStudentId: Map<String, AttendanceStatus>
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        val user = _uiState.value.currentUser ?: return
        if (statusByStudentId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No students in this section to record attendance.") }
            return
        }
        val existingRecords = if (existingSessionId != null) {
            _uiState.value.attendanceRecords.filter { it.attendanceSessionId == existingSessionId }
        } else emptyList()

        val linkedUsers = _uiState.value.students.associate { it.id to it.linkedUserId }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.saveAttendanceSessionAndRecords(
                existingSessionId = existingSessionId,
                schoolId = schoolId,
                sessionId = section.sessionId,
                classId = section.classId,
                sectionId = section.id,
                attendanceDate = attendanceDate,
                subject = subject,
                sessionSlot = sessionSlot,
                teacherId = user.id,
                teacherName = user.fullName,
                statusByStudentId = statusByStudentId,
                existingRecords = existingRecords,
                editorName = user.fullName,
                linkedUsersByStudentId = linkedUsers
            ).onSuccess {
                val studentUserIds = _uiState.value.studentRequests
                    .filter { it.sectionId == section.id && it.status == "approved" }
                    .map { it.userId }
                repository.dispatchNotifications(
                    schoolId = schoolId,
                    userIds = studentUserIds,
                    title = "Attendance Recorded ($subject)",
                    message = "$sessionSlot attendance on $attendanceDate has been saved.",
                    category = "attendance"
                )
                _uiState.update { it.copy(successMessage = "Attendance saved for $subject ($sessionSlot).") }
                refreshWorkspace()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun createTest(
        section: ClassSection,
        name: String,
        subject: String,
        testDate: String,
        totalMarks: Double,
        passingMarks: Double,
        description: String
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        val uid = _uiState.value.currentUser?.id ?: return
        if (name.isBlank() || subject.isBlank() || totalMarks <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter valid test name, subject, and total marks.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.createTest(
                schoolId, section.sessionId, section.classId, section.id,
                name, subject, testDate, totalMarks, passingMarks, description, uid
            ).onSuccess {
                _uiState.update { it.copy(successMessage = "Test '$name' created. Enter student marks now.") }
                refreshWorkspace()
            }.onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun saveTestMarks(test: TestExam, marksByStudentId: Map<String, Double>) {
        val uid = _uiState.value.currentUser?.id ?: return
        val linkedUsers = _uiState.value.students.associate { it.id to it.linkedUserId }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.saveTestResults(test, marksByStudentId, uid, linkedUsers)
                .onSuccess {
                    if (test.status == "draft") {
                        repository.updateTestStatus(test.schoolId, test.id, "submitted", null)
                    }
                    _uiState.update { it.copy(successMessage = "Marks saved and grades/ranks calculated.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun publishTestResults(test: TestExam) {
        val uid = _uiState.value.currentUser?.id ?: return
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.updateTestStatus(schoolId, test.id, "published", uid)
                .onSuccess {
                    val studentUserIds = _uiState.value.studentRequests
                        .filter { it.sectionId == test.sectionId && it.status == "approved" }
                        .map { it.userId }
                    repository.dispatchNotifications(
                        schoolId = schoolId,
                        userIds = studentUserIds,
                        title = "Results Published: ${test.name}",
                        message = "${test.subject} test results are now available in My Results.",
                        category = "result",
                        referenceId = test.id
                    )
                    _uiState.update { it.copy(successMessage = "${test.name} published to students!") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun publishAnnouncement(
        scope: String,
        section: ClassSection?,
        category: String,
        title: String,
        body: String,
        imageUri: Uri?,
        allowComments: Boolean
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        val user = _uiState.value.currentUser ?: return
        if (title.isBlank() || body.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title and content are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var imageUrl: String? = null
            if (imageUri != null) {
                repository.uploadMedia(imageUri, "announcements/$schoolId").onSuccess { imageUrl = it }
            }
            repository.createAnnouncement(
                schoolId = schoolId,
                sessionId = section?.sessionId ?: _uiState.value.activeAcademicSession?.id,
                classId = section?.classId,
                sectionId = section?.id,
                scope = scope,
                category = category,
                title = title,
                body = body,
                imageUrl = imageUrl,
                allowComments = allowComments,
                publisherId = user.id,
                publisherName = user.fullName
            ).onSuccess { post ->
                val targetUserIds = if (scope == "class" && section != null) {
                    _uiState.value.studentRequests
                        .filter { it.sectionId == section.id && it.status == "approved" }
                        .map { it.userId }
                } else {
                    _uiState.value.studentRequests.filter { it.status == "approved" }.map { it.userId } +
                        _uiState.value.teachers.map { it.userId }
                }
                repository.dispatchNotifications(
                    schoolId = schoolId,
                    userIds = targetUserIds.filter { it != user.id },
                    title = "$category: ${post.title}",
                    message = post.body.take(100),
                    category = if (scope == "class") "class_announcement" else "school_announcement",
                    referenceId = post.id
                )
                _uiState.update { it.copy(successMessage = "Post published!") }
                refreshWorkspace()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.deleteAnnouncement(schoolId, id)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Post deleted.") }
                    refreshWorkspace()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun toggleAnnouncementLike(announcementId: String) {
        val schoolId = _uiState.value.school?.id ?: return
        val uid = _uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.toggleLike(schoolId, announcementId, uid)
                .onSuccess { newCount ->
                    _uiState.update { state ->
                        state.copy(
                            announcements = state.announcements.map {
                                if (it.id == announcementId) it.copy(likesCount = newCount) else it
                            }
                        )
                    }
                }
        }
    }

    fun loadComments(announcementId: String) {
        val school = _uiState.value.school ?: return
        val uid = _uiState.value.currentUser?.id ?: return
        val isPrincipal = _uiState.value.role == UserRole.PRINCIPAL
        viewModelScope.launch {
            repository.getComments(school.id, announcementId, uid, isPrincipal, school.ownerId)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(commentsByAnnouncement = it.commentsByAnnouncement + (announcementId to list))
                    }
                }
        }
    }

    fun addComment(announcementId: String, text: String) {
        val schoolId = _uiState.value.school?.id ?: return
        val user = _uiState.value.currentUser ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addComment(schoolId, announcementId, user.id, user.fullName, text)
                .onSuccess { comment ->
                    val existing = _uiState.value.commentsByAnnouncement[announcementId].orEmpty()
                    _uiState.update {
                        it.copy(
                            commentsByAnnouncement = it.commentsByAnnouncement + (announcementId to (existing + comment))
                        )
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createTimetableSlot(
        section: ClassSection,
        dayOfWeek: String,
        subject: String,
        startTime: String,
        endTime: String,
        teacherName: String,
        roomNumber: String
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        if (subject.isBlank() || startTime.isBlank() || endTime.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subject, Start Time, and End Time are required.") }
            return
        }
        viewModelScope.launch {
            repository.createTimetableSlot(
                schoolId = schoolId,
                sessionId = section.sessionId,
                sectionId = section.id,
                dayOfWeek = dayOfWeek,
                subject = subject,
                startTime = startTime,
                endTime = endTime,
                teacherName = teacherName,
                roomNumber = roomNumber
            ).onSuccess {
                _uiState.update { it.copy(successMessage = "Timetable slot added.") }
                refreshWorkspace()
            }.onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun deleteTimetableSlot(id: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.deleteTimetableSlot(schoolId, id).onSuccess { refreshWorkspace() }
        }
    }

    fun createHomework(
        section: ClassSection,
        subject: String,
        title: String,
        instructions: String,
        dueDate: String,
        attachmentUri: Uri?
    ) {
        val schoolId = _uiState.value.school?.id ?: return
        val user = _uiState.value.currentUser ?: return
        if (subject.isBlank() || title.isBlank() || instructions.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subject, title, and instructions are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var attachmentUrl: String? = null
            if (attachmentUri != null) {
                repository.uploadMedia(attachmentUri, "homework/$schoolId").onSuccess { attachmentUrl = it }
            }
            repository.createHomework(
                schoolId = schoolId,
                sessionId = section.sessionId,
                sectionId = section.id,
                subject = subject,
                title = title,
                instructions = instructions,
                dueDate = dueDate.ifBlank { LocalDate.now().plusDays(1).toString() },
                attachmentUrl = attachmentUrl,
                teacherId = user.id,
                teacherName = user.fullName
            ).onSuccess { hw ->
                val studentUserIds = _uiState.value.studentRequests
                    .filter { it.sectionId == section.id && it.status == "approved" }
                    .map { it.userId }
                repository.dispatchNotifications(
                    schoolId = schoolId,
                    userIds = studentUserIds,
                    title = "New Homework: ${hw.subject}",
                    message = "${hw.title} (Due ${hw.dueDate})",
                    category = "homework",
                    referenceId = hw.id
                )
                _uiState.update { it.copy(successMessage = "Homework assigned!") }
                refreshWorkspace()
            }.onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
        }
    }

    fun markNotificationRead(id: String) {
        val schoolId = _uiState.value.school?.id ?: return
        viewModelScope.launch {
            repository.markNotificationRead(schoolId, id)
            _uiState.update { state ->
                state.copy(
                    notifications = state.notifications.map {
                        if (it.id == id) it.copy(isRead = true) else it
                    }
                )
            }
        }
    }

    fun markAllNotificationsRead() {
        val schoolId = _uiState.value.school?.id ?: return
        val uid = _uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(schoolId, uid)
            _uiState.update { state ->
                state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
            }
        }
    }

    fun exportPdfAndShare(
        context: Context,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        footer: String = ""
    ) {
        ReportExporter.exportPdfReport(context, _uiState.value.school, title, subtitle, headers, rows, footer)
            .onSuccess { file: File ->
                ReportExporter.shareReportFile(context, file, "application/pdf", "Share / Print PDF Report")
                _uiState.update { it.copy(successMessage = "PDF generated: ${file.name}") }
            }
            .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message ?: "PDF export failed.") } }
    }

    fun exportCsvAndShare(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>
    ) {
        ReportExporter.exportCsvReport(context, title, headers, rows)
            .onSuccess { file: File ->
                ReportExporter.shareReportFile(context, file, "text/csv", "Export Excel/CSV Report")
                _uiState.update { it.copy(successMessage = "Excel/CSV exported: ${file.name}") }
            }
            .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message ?: "CSV export failed.") } }
    }
}
