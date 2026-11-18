package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Locale

enum class UserRole(val dbValue: String, val label: String) {
    PRINCIPAL("principal", "Principal"),
    TEACHER("teacher", "Teacher"),
    STUDENT("student", "Student");

    companion object {
        fun fromDb(value: String?): UserRole = entries.find { it.dbValue == value } ?: STUDENT
    }
}

enum class AttendanceStatus(val dbValue: String, val label: String) {
    PRESENT("present", "Present"),
    ABSENT("absent", "Absent"),
    LEAVE("leave", "Leave");

    companion object {
        fun fromDb(value: String?): AttendanceStatus = entries.find { it.dbValue == value } ?: PRESENT
    }
}

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Timestamp?.toDisplayDateTime(): String {
    val date = this?.toDate() ?: return ""
    return SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(date)
}

fun Timestamp?.toDateOnlyString(): String {
    val date = this?.toDate() ?: return ""
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).format(date)
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val avatarUrl: String? = null,
    val accountIntent: String = "student",
    val activeSchoolId: String? = null,
    val pinnedSchoolIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolDirectoryEntry(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolCode: String = "",
    val ownerId: String = "",
    val name: String = "",
    val schoolType: String = "Private",
    val city: String = "",
    @get:PropertyName("isPublic") @field:PropertyName("isPublic")
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val schoolCode: String = "",
    val ownerId: String = "",
    val name: String = "",
    val schoolType: String = "Private",
    val address: String = "",
    val city: String = "",
    val contactNumber: String = "",
    val email: String = "",
    val description: String = "",
    val establishedYear: Int? = null,
    val website: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val galleryUrls: List<String> = emptyList(),
    val timezone: String = "UTC",
    val memberUids: List<String> = emptyList(),
    val staffUids: List<String> = emptyList(),
    @get:PropertyName("isPublic") @field:PropertyName("isPublic")
    val isPublic: Boolean = true,
    val sessionIds: List<String> = emptyList(),
    val classIds: List<String> = emptyList(),
    val sectionIds: List<String> = emptyList(),
    val lastAcceptedPrincipalInviteCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object TeacherInviteLinks {
    private val codeRegex = Regex("""\b(TCH-\d{4,10})\b""", RegexOption.IGNORE_CASE)

    fun buildAppDeepLink(inviteCode: String): String =
        "blschoolhub://invite/teacher?code=${inviteCode.trim().uppercase()}"

    fun buildWebInviteLink(inviteCode: String): String =
        "https://blschoolhub.app/teacher-invite?code=${inviteCode.trim().uppercase()}"

    fun buildShareMessage(schoolName: String, schoolCode: String, inviteCode: String): String {
        val code = inviteCode.trim().uppercase()
        return buildString {
            appendLine("You are invited to join *$schoolName* ($schoolCode) as a Teacher on *BL School Hub*!")
            appendLine()
            appendLine("Tap link to open app & sign in with Gmail:")
            appendLine(buildAppDeepLink(code))
            appendLine()
            append("Invite Code (or paste this message in BL School Hub): $code")
        }
    }

    fun extractInviteCode(rawInput: String?): String? {
        val text = rawInput?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = codeRegex.find(text)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        val uri = try { android.net.Uri.parse(text) } catch (_: Exception) { null }
        val queryCode = uri?.getQueryParameter("code")
        return queryCode?.takeIf { it.isNotEmpty() }?.uppercase()
    }

    fun buildCredentialShareMessage(
        schoolName: String,
        schoolCode: String,
        username: String,
        accessPassword: String
    ): String = buildString {
        appendLine("You are invited to join *$schoolName* ($schoolCode) as a Teacher on *BL School Hub*!")
        appendLine()
        appendLine("Teacher Credentials:")
        appendLine("• *Username:* $username")
        appendLine("• *Password / Code:* $accessPassword")
        appendLine()
        appendLine("To join:")
        appendLine("1. Open BL School Hub app")
        appendLine("2. Tap 'Join as Teacher' > 'Join via Username & Password'")
        appendLine("3. Enter your Username & Password and sign in with Gmail to join.")
    }
}

@IgnoreExtraProperties
data class PrincipalInvitation(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolCode: String = "",
    val schoolOwnerId: String = "",
    val email: String = "",
    val emailLower: String = "",
    val fullName: String = "",
    val inviteCode: String = "",
    val status: String = "pending",
    val invitedBy: String = "",
    val acceptedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolMembership(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val userId: String = "",
    val role: String = "student",
    val status: String = "active",
    val inviteCode: String? = null,
    val linkedStudentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherPermissions(
    val viewStudents: Boolean = true,
    val editStudents: Boolean = true,
    val markAttendance: Boolean = true,
    val editAttendance: Boolean = true,
    val createTests: Boolean = true,
    val enterMarks: Boolean = true,
    val publishAnnouncements: Boolean = true,
    val viewReports: Boolean = true
) {
    fun toMap(): Map<String, Boolean> = mapOf(
        "viewStudents" to viewStudents,
        "editStudents" to editStudents,
        "markAttendance" to markAttendance,
        "editAttendance" to editAttendance,
        "createTests" to createTests,
        "enterMarks" to enterMarks,
        "publishAnnouncements" to publishAnnouncements,
        "viewReports" to viewReports
    )
}

@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val avatarUrl: String? = null,
    val joiningDate: String = "",
    @get:PropertyName("isActive") @field:PropertyName("isActive")
    val isActive: Boolean = true,
    val permissions: TeacherPermissions = TeacherPermissions(),
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherCredential(
    @DocumentId val id: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val accessPassword: String = "",
    val inviteCode: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolOwnerId: String = "",
    val status: String = "pending",
    val invitedBy: String = "",
    val acceptedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherInvitation(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolOwnerId: String = "",
    val email: String = "",
    val emailLower: String = "",
    val fullName: String = "",
    val phone: String = "",
    val inviteCode: String = "",
    val permissions: TeacherPermissions = TeacherPermissions(),
    val status: String = "pending",
    val invitedBy: String = "",
    val acceptedByUid: String? = null,
    val authType: String = "link",
    val username: String? = null,
    val usernameLower: String? = null,
    val accessPassword: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherJoinRequest(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val schoolName: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val subjectSpecialty: String = "",
    val note: String = "",
    val status: String = "pending",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherAssignment(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val teacherProfileId: String = "",
    val sectionId: String = "",
    val subject: String = "General",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AcademicSession(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val name: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    @get:PropertyName("isCurrent") @field:PropertyName("isCurrent")
    val isCurrent: Boolean = false,
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val name: String = "",
    val description: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClassSection(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val name: String = "",
    val classTeacherId: String? = null,
    val description: String = "",
    val customLectures: List<String> = emptyList(),
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val studentCode: String = "",
    val rollNumber: String = "",
    val fullName: String = "",
    val fatherName: String = "",
    val dateOfBirth: String? = null,
    val gender: String = "Male",
    val parentName: String = "",
    val parentContact: String = "",
    val address: String = "",
    val admissionDate: String = "",
    val email: String? = null,
    val avatarUrl: String? = null,
    val status: String = "active",
    val previousRecordId: String? = null,
    val linkedUserId: String? = null,
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentAccountRequest(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val schoolName: String = "",
    val userId: String = "",
    val studentId: String? = null,
    val sessionId: String? = null,
    val classId: String? = null,
    val sectionId: String? = null,
    val requestedRollNumber: String = "",
    val requestedFullName: String = "",
    val requestedFatherName: String = "",
    val requestedSessionName: String = "",
    val requestedClassName: String = "",
    val requestedSectionName: String = "",
    val status: String = "pending",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceSession(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val attendanceDate: String = "",
    val subject: String = "General",
    val sessionSlot: String = "Morning",
    val teacherId: String? = null,
    val teacherName: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceEditEntry(
    val editedBy: String = "",
    val editedAt: String = "",
    val fromStatus: String = "",
    val toStatus: String = ""
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val attendanceSessionId: String = "",
    val studentId: String = "",
    val linkedUserId: String? = null,
    val status: String = "present",
    val note: String = "",
    val updatedBy: String? = null,
    val editHistory: List<AttendanceEditEntry> = emptyList(),
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TestExam(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val name: String = "",
    val subject: String = "",
    val testDate: String = "",
    val totalMarks: Double = 100.0,
    val passingMarks: Double = 40.0,
    val description: String = "",
    val status: String = "draft",
    val createdBy: String? = null,
    val approvedBy: String? = null,
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TestResult(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val testId: String = "",
    val studentId: String = "",
    val linkedUserId: String? = null,
    val obtainedMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "F",
    val passed: Boolean = false,
    val rank: Int? = null,
    val enteredBy: String? = null,
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AnnouncementPost(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String? = null,
    val classId: String? = null,
    val sectionId: String? = null,
    val scope: String = "school",
    val category: String = "Announcement",
    val title: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val allowComments: Boolean = true,
    val likesCount: Int = 0,
    val publisherId: String = "",
    val publisherName: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AnnouncementComment(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val announcementId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val body: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TimetableSlot(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val sectionId: String = "",
    val dayOfWeek: String = "Monday",
    val subject: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val teacherName: String = "",
    val roomNumber: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HomeworkItem(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val sectionId: String = "",
    val subject: String = "",
    val title: String = "",
    val instructions: String = "",
    val dueDate: String = "",
    val attachmentUrl: String? = null,
    val teacherId: String? = null,
    val teacherName: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClassFeeDrive(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val title: String = "",
    val description: String = "",
    val amountText: String = "",
    val feeDate: String = "",
    val paidStudentIds: List<String> = emptyList(),
    val paidDetails: Map<String, String> = emptyMap(),
    val createdBy: String? = null,
    val createdByName: String = "",
    val updatedByName: String = "",
    val staffUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolOwnerId: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "notice",
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    val referenceId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AttendanceStats(
    val totalCount: Int,
    val presentCount: Int,
    val absentCount: Int,
    val leaveCount: Int
) {
    val percentage: Double
        get() = if (totalCount == 0) 0.0 else (presentCount.toDouble() / totalCount.toDouble()) * 100.0
}

data class StudentAttendanceSummary(
    val dailyStats: AttendanceStats,
    val periodStats: AttendanceStats,
    val subjectBreakdown: Map<String, AttendanceStats>
)

const val SUPER_ADMIN_EMAIL = "bilookhanbhakkar@gmail.com"

fun isSuperAdminEmail(email: String?): Boolean =
    email?.trim()?.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) == true

@IgnoreExtraProperties
data class AppVisit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val roleIntent: String = "student",
    val activeSchoolId: String? = null,
    val activeSchoolName: String? = null,
    val dayKey: String = "",
    val monthKey: String = "",
    val dateSortKey: String = "",
    val hourBucket: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class DailyTrafficBucket(
    val dateLabel: String,
    val sortKey: String,
    val visits: Int,
    val uniqueVisitors: Int
)

data class SchoolAdminSummary(
    val school: School,
    val isPublicListed: Boolean,
    val ownerName: String,
    val ownerEmail: String,
    val teachersCount: Int,
    val studentsCount: Int,
    val classesCount: Int,
    val sectionsCount: Int,
    val attendanceSessionsCount: Int,
    val testsCount: Int,
    val announcementsCount: Int,
    val pendingTeacherRequestsCount: Int,
    val pendingStudentRequestsCount: Int
)

data class AdminDashboardStats(
    val lastSyncedDisplay: String = "",
    val totalVisits: Int = 0,
    val uniqueVisitors: Int = 0,
    val todayVisits: Int = 0,
    val last7DaysVisits: Int = 0,
    val last30DaysVisits: Int = 0,
    val todayDateLabel: String = "",
    val peakHourLabel: String = "N/A",
    val dailyTraffic: List<DailyTrafficBucket> = emptyList(),
    val recentVisits: List<AppVisit> = emptyList(),
    val totalUsers: Int = 0,
    val principalUsersCount: Int = 0,
    val teacherUsersCount: Int = 0,
    val studentUsersCount: Int = 0,
    val visitsByRole: Map<String, Int> = emptyMap(),
    val totalSchools: Int = 0,
    val publicSchoolsCount: Int = 0,
    val schoolsByType: Map<String, Int> = emptyMap(),
    val totalTeachers: Int = 0,
    val totalStudents: Int = 0,
    val totalClasses: Int = 0,
    val totalSections: Int = 0,
    val totalAttendanceSessions: Int = 0,
    val totalTests: Int = 0,
    val totalAnnouncements: Int = 0,
    val totalPendingRequests: Int = 0,
    val totalTeacherInvites: Int = 0,
    val totalPrincipalInvites: Int = 0,
    val schoolSummaries: List<SchoolAdminSummary> = emptyList(),
    val users: List<UserProfile> = emptyList()
)

