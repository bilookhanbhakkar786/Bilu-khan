package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminDashboardStats
import com.example.data.model.AppVisit
import com.example.data.model.DailyTrafficBucket
import com.example.data.model.PrincipalInvitation
import com.example.data.model.School
import com.example.data.model.SchoolAdminSummary
import com.example.data.model.SchoolDirectoryEntry
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherJoinRequest
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.isSuperAdminEmail
import com.example.data.remote.OperationType
import com.example.data.remote.friendlyFirestoreMessage
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AdminRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        auth = Firebase.auth,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private val recordedSessions = ConcurrentHashMap.newKeySet<String>()

        fun resetSessionCacheForTests() {
            recordedSessions.clear()
        }
    }

    suspend fun recordAppVisit(
        user: UserProfile,
        activeSchool: School?,
        activeRole: UserRole?,
        force: Boolean = false
    ): Result<AppVisit?> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: user.userId.ifBlank { user.id }
        if (uid.isBlank()) return@withContext Result.success(null)

        val sessionKey = "${uid}_${activeSchool?.id.orEmpty()}_${activeRole?.dbValue ?: user.accountIntent}"
        if (!force && !recordedSessions.add(sessionKey)) {
            return@withContext Result.success(null)
        }

        val now = Date()
        val cal = Calendar.getInstance().apply { time = now }
        val dayKey = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(now)
        val monthKey = SimpleDateFormat("MM-yyyy", Locale.US).format(now)
        val dateSortKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val hourBucket = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        val roleValue = when (activeRole?.dbValue ?: user.accountIntent) {
            "principal", "teacher", "student" -> activeRole?.dbValue ?: user.accountIntent
            else -> "student"
        }

        val visitId = "vis_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val visitRef = db.collection("app_visits").document(visitId)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "userEmail" to user.email.ifBlank { auth.currentUser?.email.orEmpty() }.take(160),
            "userName" to user.fullName.ifBlank { "School Member" }.take(120),
            "roleIntent" to roleValue,
            "dayKey" to dayKey,
            "monthKey" to monthKey,
            "dateSortKey" to dateSortKey,
            "hourBucket" to hourBucket,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (activeSchool != null) {
            payload["activeSchoolId"] = activeSchool.id.take(128)
            payload["activeSchoolName"] = activeSchool.name.take(160)
        }

        try {
            visitRef.set(payload).await()
            Result.success(
                AppVisit(
                    id = visitId,
                    userId = uid,
                    userEmail = payload["userEmail"] as String,
                    userName = payload["userName"] as String,
                    roleIntent = roleValue,
                    activeSchoolId = activeSchool?.id,
                    activeSchoolName = activeSchool?.name,
                    dayKey = dayKey,
                    monthKey = monthKey,
                    dateSortKey = dateSortKey,
                    hourBucket = hourBucket
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Do not crash normal user UX if telemetry fails, but log and return failure
            handleFirestoreError(e, OperationType.WRITE, visitRef.path)
            Result.failure(e)
        }
    }

    suspend fun loadAdminDashboard(): Result<AdminDashboardStats> = withContext(Dispatchers.IO) {
        val currentEmail = auth.currentUser?.email
        if (!isSuperAdminEmail(currentEmail)) {
            return@withContext Result.failure(
                SecurityException("Access denied. Super Admin privileges are restricted to bilookhanbhakkar@gmail.com.")
            )
        }

        try {
            val (visits, users, schools, directoryEntries, teacherInvites, principalInvites) = coroutineScope {
                val visitsDeferred = async {
                    db.collection("app_visits").get().await()
                        .toObjects(AppVisit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                val usersDeferred = async {
                    db.collection("users").get().await()
                        .toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                val schoolsDeferred = async {
                    db.collection("schools").get().await()
                        .toObjects(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                val dirDeferred = async {
                    db.collection("school_directory").get().await()
                        .toObjects(SchoolDirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                val tInvDeferred = async {
                    db.collection("teacher_invitations").get().await()
                        .toObjects(TeacherInvitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                val pInvDeferred = async {
                    db.collection("principal_invitations").get().await()
                        .toObjects(PrincipalInvitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                Sextuple(
                    visitsDeferred.await(),
                    usersDeferred.await(),
                    schoolsDeferred.await(),
                    dirDeferred.await(),
                    tInvDeferred.await(),
                    pInvDeferred.await()
                )
            }

            val userById = users.associateBy { it.userId.ifBlank { it.id } }
            val dirBySchoolCode = directoryEntries.associateBy { it.schoolCode }

            // Aggregate each school's subcollections in parallel without needing CollectionGroup indexes
            val schoolSummaries = coroutineScope {
                schools.map { school ->
                    async {
                        val sRef = db.collection("schools").document(school.id)
                        val teachersSnap = async { sRef.collection("teachers").get().await() }
                        val studentsSnap = async { sRef.collection("students").get().await() }
                        val classesSnap = async { sRef.collection("classes").get().await() }
                        val sectionsSnap = async { sRef.collection("sections").get().await() }
                        val attSnap = async { sRef.collection("attendance_sessions").get().await() }
                        val testsSnap = async { sRef.collection("tests").get().await() }
                        val annSnap = async { sRef.collection("announcements").get().await() }
                        val tReqSnap = async {
                            sRef.collection("teacher_requests").get().await()
                                .toObjects(TeacherJoinRequest::class.java)
                        }
                        val sReqSnap = async {
                            sRef.collection("student_requests").get().await()
                                .toObjects(StudentAccountRequest::class.java)
                        }

                        val owner = userById[school.ownerId]
                        val dir = dirBySchoolCode[school.schoolCode]

                        SchoolAdminSummary(
                            school = school,
                            isPublicListed = dir?.isPublic ?: true,
                            ownerName = owner?.fullName?.ifBlank { "Principal" } ?: "Principal",
                            ownerEmail = owner?.email?.ifBlank { school.email } ?: school.email,
                            teachersCount = teachersSnap.await().size(),
                            studentsCount = studentsSnap.await().size(),
                            classesCount = classesSnap.await().size(),
                            sectionsCount = sectionsSnap.await().size(),
                            attendanceSessionsCount = attSnap.await().size(),
                            testsCount = testsSnap.await().size(),
                            announcementsCount = annSnap.await().size(),
                            pendingTeacherRequestsCount = tReqSnap.await().count { it.status == "pending" },
                            pendingStudentRequestsCount = sReqSnap.await().count { it.status == "pending" }
                        )
                    }
                }.awaitAll()
            }.sortedByDescending { it.studentsCount + it.teachersCount }

            val now = Date()
            val todayDayKey = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(now)
            val displayNow = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(now)

            // Build chronological last 7 days buckets (DD-MM-YYYY)
            val dayBuckets = (6 downTo 0).map { offset ->
                val c = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -offset)
                }
                val label = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(c.time)
                val sortKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
                val dayVisits = visits.filter { it.dayKey == label || it.dateSortKey == sortKey }
                DailyTrafficBucket(
                    dateLabel = label,
                    sortKey = sortKey,
                    visits = dayVisits.size,
                    uniqueVisitors = dayVisits.map { it.userId }.distinct().size
                )
            }

            val thirtyDaysAgoCal = Calendar.getInstance().apply {
                time = now
                add(Calendar.DAY_OF_YEAR, -30)
            }
            val thirtyDaysSortMin = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(thirtyDaysAgoCal.time)

            val peakHourEntry = visits.groupingBy { it.hourBucket }.eachCount().maxByOrNull { it.value }
            val peakHourLabel = peakHourEntry?.let {
                val h = it.key
                val next = (h + 1) % 24
                String.format(Locale.US, "%02d:00–%02d:00 (%d)", h, next, it.value)
            } ?: "No traffic yet"

            val sortedVisits = visits.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            val sortedUsers = users.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }

            val stats = AdminDashboardStats(
                lastSyncedDisplay = displayNow,
                totalVisits = visits.size,
                uniqueVisitors = visits.map { it.userId }.distinct().size,
                todayVisits = visits.count { it.dayKey == todayDayKey },
                last7DaysVisits = dayBuckets.sumOf { it.visits },
                last30DaysVisits = visits.count { it.dateSortKey >= thirtyDaysSortMin },
                todayDateLabel = todayDayKey,
                peakHourLabel = peakHourLabel,
                dailyTraffic = dayBuckets,
                recentVisits = sortedVisits.take(50),
                totalUsers = users.size,
                principalUsersCount = users.count { it.accountIntent == UserRole.PRINCIPAL.dbValue },
                teacherUsersCount = users.count { it.accountIntent == UserRole.TEACHER.dbValue },
                studentUsersCount = users.count { it.accountIntent == UserRole.STUDENT.dbValue },
                visitsByRole = visits.groupingBy { it.roleIntent }.eachCount(),
                totalSchools = schools.size,
                publicSchoolsCount = schoolSummaries.count { it.isPublicListed },
                schoolsByType = schools.groupingBy { it.schoolType.ifBlank { "Other" } }.eachCount(),
                totalTeachers = schoolSummaries.sumOf { it.teachersCount },
                totalStudents = schoolSummaries.sumOf { it.studentsCount },
                totalClasses = schoolSummaries.sumOf { it.classesCount },
                totalSections = schoolSummaries.sumOf { it.sectionsCount },
                totalAttendanceSessions = schoolSummaries.sumOf { it.attendanceSessionsCount },
                totalTests = schoolSummaries.sumOf { it.testsCount },
                totalAnnouncements = schoolSummaries.sumOf { it.announcementsCount },
                totalPendingRequests = schoolSummaries.sumOf {
                    it.pendingTeacherRequestsCount + it.pendingStudentRequestsCount
                },
                totalTeacherInvites = teacherInvites.size,
                totalPrincipalInvites = principalInvites.size,
                schoolSummaries = schoolSummaries,
                users = sortedUsers
            )

            Result.success(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "admin_dashboard")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to load admin analytics."), e))
        }
    }

    suspend fun setSchoolDirectoryVisibility(
        schoolCode: String,
        isPublic: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dirRef = db.collection("school_directory").document(schoolCode)
        try {
            dirRef.update(
                mapOf(
                    "isPublic" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, dirRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not update school visibility."), e))
        }
    }

    private data class Sextuple<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F
    )
}
