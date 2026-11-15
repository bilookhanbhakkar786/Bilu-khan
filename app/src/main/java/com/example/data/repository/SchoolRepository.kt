package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.model.AcademicCalculators
import com.example.data.model.AcademicSession
import com.example.data.model.AnnouncementComment
import com.example.data.model.AnnouncementPost
import com.example.data.model.AppNotification
import com.example.data.model.AttendanceEditEntry
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassSection
import com.example.data.model.HomeworkItem
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolDirectoryEntry
import com.example.data.model.SchoolMembership
import com.example.data.model.StudentAccountRequest
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherAssignment
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherPermissions
import com.example.data.model.TeacherProfile
import com.example.data.model.TestExam
import com.example.data.model.TestResult
import com.example.data.model.TimetableSlot
import com.example.data.model.UserRole
import com.example.data.remote.MediaUtils
import com.example.data.remote.OperationType
import com.example.data.remote.friendlyFirestoreMessage
import com.example.data.remote.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SchoolWorkspaceBundle(
    val school: School,
    val sessions: List<AcademicSession>,
    val classes: List<SchoolClass>,
    val sections: List<ClassSection>,
    val teachers: List<TeacherProfile>,
    val teacherInvitations: List<TeacherInvitation>,
    val teacherAssignments: List<TeacherAssignment>,
    val students: List<StudentRecord>,
    val studentRequests: List<StudentAccountRequest>,
    val attendanceSessions: List<AttendanceSession>,
    val attendanceRecords: List<AttendanceRecord>,
    val tests: List<TestExam>,
    val testResults: List<TestResult>,
    val announcements: List<AnnouncementPost>,
    val timetables: List<TimetableSlot>,
    val homework: List<HomeworkItem>,
    val notifications: List<AppNotification>
)

class SchoolRepository(
    private val db: FirebaseFirestore,
    private val mediaEncoder: (suspend (Uri, Int, Int) -> Result<String>)? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        mediaEncoder = { uri, maxDim, quality ->
            MediaUtils.encodeUriToDataUri(context.applicationContext, uri, maxDim, quality)
        }
    )

    private fun todayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun newCode(prefix: String): String =
        "$prefix-${(100000..999999).random()}"

    private fun schoolDoc(schoolId: String) = db.collection("schools").document(schoolId)

    suspend fun uploadMedia(uri: Uri, folder: String): Result<String> {
        val encoder = mediaEncoder
            ?: return Result.failure(IllegalStateException("Image encoder unavailable."))
        val maxDim = if (folder.contains("banner", ignoreCase = true) || folder.contains("announcement", ignoreCase = true)) {
            640
        } else {
            420
        }
        return encoder(uri, maxDim, 72)
    }

    suspend fun createSchoolWithPrincipal(
        ownerId: String,
        name: String,
        schoolType: String,
        address: String,
        city: String,
        contactNumber: String,
        email: String,
        description: String,
        establishedYear: Int?,
        website: String?,
        logoUrl: String?,
        bannerUrl: String?
    ): Result<School> = withContext(Dispatchers.IO) {
        val validType = if (schoolType in listOf("Private", "Government", "Other")) schoolType else "Private"
        val validYear = establishedYear?.takeIf { it in 1800..2100 }
        val schoolRef = db.collection("schools").document()
        val schoolId = schoolRef.id
        val schoolCode = newCode("BL")
        val lockRef = db.collection("principal_locks").document(ownerId)
        val memberRef = schoolRef.collection("members").document(ownerId)
        val dirRef = db.collection("school_directory").document(schoolCode)
        val tz = TimeZone.getDefault().id.ifBlank { "UTC" }.take(64)

        val schoolPayload = mutableMapOf<String, Any>(
            "schoolCode" to schoolCode,
            "ownerId" to ownerId,
            "name" to name.trim().take(160),
            "schoolType" to validType,
            "address" to address.trim().take(300),
            "city" to city.trim().take(100),
            "contactNumber" to contactNumber.trim().take(40),
            "email" to email.trim().take(160),
            "description" to description.trim().take(2000),
            "galleryUrls" to emptyList<String>(),
            "timezone" to tz,
            "memberUids" to listOf(ownerId),
            "staffUids" to listOf(ownerId),
            "sessionIds" to emptyList<String>(),
            "classIds" to emptyList<String>(),
            "sectionIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        validYear?.let { schoolPayload["establishedYear"] = it }
        website?.trim()?.takeIf { it.isNotBlank() }?.let { schoolPayload["website"] = it.take(300) }
        logoUrl?.takeIf { it.isNotBlank() }?.let { schoolPayload["logoUrl"] = it }
        bannerUrl?.takeIf { it.isNotBlank() }?.let { schoolPayload["bannerUrl"] = it }

        val lockPayload = mapOf(
            "userId" to ownerId,
            "schoolId" to schoolId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val memberPayload = mapOf(
            "schoolId" to schoolId,
            "schoolOwnerId" to ownerId,
            "userId" to ownerId,
            "role" to "principal",
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val dirPayload = mapOf(
            "schoolId" to schoolId,
            "schoolCode" to schoolCode,
            "ownerId" to ownerId,
            "name" to name.trim().take(160),
            "schoolType" to validType,
            "city" to city.trim().take(100),
            "isPublic" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            db.runBatch { batch ->
                batch.set(lockRef, lockPayload)
                batch.set(schoolRef, schoolPayload)
                batch.set(memberRef, memberPayload)
                batch.set(dirRef, dirPayload)
            }.await()

            // Link activeSchoolId on user profile if present
            try {
                db.collection("users").document(ownerId).update(
                    mapOf(
                        "accountIntent" to "principal",
                        "activeSchoolId" to schoolId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }

            val created = schoolRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, schoolRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not create school."), e))
        }
    }

    suspend fun updateSchool(
        schoolId: String,
        name: String,
        schoolType: String,
        address: String,
        city: String,
        contactNumber: String,
        email: String,
        description: String,
        establishedYear: Int?,
        website: String?,
        logoUrl: String?,
        bannerUrl: String?,
        galleryUrls: List<String>
    ): Result<School> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId)
        try {
            val currentSnap = ref.get().await()
            val current = currentSnap.toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val validType = if (schoolType in listOf("Private", "Government", "Other")) schoolType else "Private"
            val validYear = establishedYear?.takeIf { it in 1800..2100 }

            val updates = mutableMapOf<String, Any?>(
                "name" to name.trim().take(160),
                "schoolType" to validType,
                "address" to address.trim().take(300),
                "city" to city.trim().take(100),
                "contactNumber" to contactNumber.trim().take(40),
                "email" to email.trim().take(160),
                "description" to description.trim().take(2000),
                "establishedYear" to validYear,
                "website" to website?.trim()?.ifBlank { null }?.take(300),
                "logoUrl" to logoUrl,
                "bannerUrl" to bannerUrl,
                "galleryUrls" to galleryUrls.take(8),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.runBatch { batch ->
                batch.update(ref, updates)
                if (current.schoolCode.isNotBlank()) {
                    batch.update(
                        db.collection("school_directory").document(current.schoolCode),
                        mapOf(
                            "name" to name.trim().take(160),
                            "schoolType" to validType,
                            "city" to city.trim().take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            val updated = ref.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School update returned empty."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update school."), e))
        }
    }

    suspend fun searchSchools(query: String): Result<List<School>> = withContext(Dispatchers.IO) {
        try {
            val entries = db.collection("school_directory")
                .whereEqualTo("isPublic", true)
                .limit(100)
                .get()
                .await()
                .toObjects(SchoolDirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val needle = query.trim().lowercase()
            val filtered = if (needle.isBlank()) {
                entries
            } else {
                entries.filter {
                    it.name.lowercase().contains(needle) ||
                        it.schoolCode.lowercase().contains(needle) ||
                        it.city.lowercase().contains(needle)
                }
            }

            Result.success(
                filtered.map { entry ->
                    School(
                        id = entry.schoolId,
                        schoolCode = entry.schoolCode,
                        ownerId = entry.ownerId,
                        name = entry.name,
                        schoolType = entry.schoolType,
                        city = entry.city
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "school_directory")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "School search failed."), e))
        }
    }

    private suspend inline fun <reified T> safeListQuery(
        query: Query,
        pathForLog: String
    ): List<T> {
        return try {
            query.get().await().toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, pathForLog)
            emptyList()
        }
    }

    suspend fun loadSchoolWorkspace(schoolId: String, userId: String): Result<SchoolWorkspaceBundle> =
        withContext(Dispatchers.IO) {
            val sRef = schoolDoc(schoolId)
            try {
                val schoolSnap = sRef.get().await()
                val school = schoolSnap.toObject(
                    School::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@withContext Result.failure(IllegalStateException("School record not found."))

                val memberSnap = sRef.collection("members").document(userId).get().await()
                val member = memberSnap.toObject(
                    SchoolMembership::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val role = when {
                    school.ownerId == userId -> UserRole.PRINCIPAL
                    member != null && member.status == "active" -> UserRole.fromDb(member.role)
                    else -> return@withContext Result.failure(IllegalStateException("Active school membership required."))
                }

                fun scopedCollectionQuery(subcollection: String, memberArrayField: String): Query {
                    val col = sRef.collection(subcollection)
                    return when (role) {
                        UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                        UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                        UserRole.STUDENT -> col.whereArrayContains(memberArrayField, userId)
                    }
                }

                coroutineScope {
                    val sessionsDef = async {
                        safeListQuery<AcademicSession>(
                            scopedCollectionQuery("academic_sessions", "memberUids"),
                            "${sRef.path}/academic_sessions"
                        )
                    }
                    val classesDef = async {
                        safeListQuery<SchoolClass>(
                            scopedCollectionQuery("classes", "memberUids"),
                            "${sRef.path}/classes"
                        )
                    }
                    val sectionsDef = async {
                        safeListQuery<ClassSection>(
                            scopedCollectionQuery("sections", "memberUids"),
                            "${sRef.path}/sections"
                        )
                    }
                    val teachersDef = async {
                        when (role) {
                            UserRole.PRINCIPAL -> safeListQuery<TeacherProfile>(
                                sRef.collection("teachers").whereEqualTo("schoolOwnerId", school.ownerId),
                                "${sRef.path}/teachers"
                            )
                            UserRole.TEACHER -> safeListQuery<TeacherProfile>(
                                sRef.collection("teachers").whereEqualTo("userId", userId),
                                "${sRef.path}/teachers"
                            )
                            UserRole.STUDENT -> emptyList()
                        }
                    }
                    val invitesDef = async {
                        if (role == UserRole.PRINCIPAL) {
                            safeListQuery<TeacherInvitation>(
                                db.collection("teacher_invitations").whereEqualTo("schoolOwnerId", school.ownerId),
                                "teacher_invitations"
                            )
                        } else emptyList()
                    }
                    val assignDef = async {
                        when (role) {
                            UserRole.PRINCIPAL -> safeListQuery<TeacherAssignment>(
                                sRef.collection("teacher_assignments").whereEqualTo("schoolOwnerId", school.ownerId),
                                "${sRef.path}/teacher_assignments"
                            )
                            UserRole.TEACHER -> safeListQuery<TeacherAssignment>(
                                sRef.collection("teacher_assignments").whereArrayContains("staffUids", userId),
                                "${sRef.path}/teacher_assignments"
                            )
                            UserRole.STUDENT -> emptyList()
                        }
                    }
                    val studentsDef = async {
                        val col = sRef.collection("students")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereEqualTo("linkedUserId", userId)
                        }
                        safeListQuery<StudentRecord>(q, "${sRef.path}/students")
                    }
                    val reqsDef = async {
                        val col = sRef.collection("student_requests")
                        when (role) {
                            UserRole.PRINCIPAL -> safeListQuery<StudentAccountRequest>(
                                col.whereEqualTo("schoolOwnerId", school.ownerId),
                                "${sRef.path}/student_requests"
                            )
                            UserRole.TEACHER -> emptyList()
                            UserRole.STUDENT -> safeListQuery<StudentAccountRequest>(
                                col.whereEqualTo("userId", userId),
                                "${sRef.path}/student_requests"
                            )
                        }
                    }
                    val attSessionsDef = async {
                        safeListQuery<AttendanceSession>(
                            scopedCollectionQuery("attendance_sessions", "memberUids"),
                            "${sRef.path}/attendance_sessions"
                        )
                    }
                    val attRecsDef = async {
                        val col = sRef.collection("attendance_records")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereEqualTo("linkedUserId", userId)
                        }
                        safeListQuery<AttendanceRecord>(q, "${sRef.path}/attendance_records")
                    }
                    val testsDef = async {
                        val col = sRef.collection("tests")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereArrayContains("memberUids", userId).whereEqualTo("status", "published")
                        }
                        safeListQuery<TestExam>(q, "${sRef.path}/tests")
                    }
                    val testResDef = async {
                        val col = sRef.collection("test_results")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereEqualTo("linkedUserId", userId)
                        }
                        safeListQuery<TestResult>(q, "${sRef.path}/test_results")
                    }
                    val annDef = async {
                        safeListQuery<AnnouncementPost>(
                            scopedCollectionQuery("announcements", "memberUids"),
                            "${sRef.path}/announcements"
                        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    val ttDef = async {
                        safeListQuery<TimetableSlot>(
                            scopedCollectionQuery("timetables", "memberUids"),
                            "${sRef.path}/timetables"
                        )
                    }
                    val hwDef = async {
                        safeListQuery<HomeworkItem>(
                            scopedCollectionQuery("homework", "memberUids"),
                            "${sRef.path}/homework"
                        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    val notifDef = async {
                        safeListQuery<AppNotification>(
                            sRef.collection("notifications").whereEqualTo("userId", userId),
                            "${sRef.path}/notifications"
                        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }

                    Result.success(
                        SchoolWorkspaceBundle(
                            school = school,
                            sessions = sessionsDef.await().sortedByDescending { it.isCurrent },
                            classes = classesDef.await(),
                            sections = sectionsDef.await(),
                            teachers = teachersDef.await(),
                            teacherInvitations = invitesDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            teacherAssignments = assignDef.await(),
                            students = studentsDef.await(),
                            studentRequests = reqsDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            attendanceSessions = attSessionsDef.await().sortedByDescending { it.attendanceDate },
                            attendanceRecords = attRecsDef.await(),
                            tests = testsDef.await().sortedByDescending { it.testDate },
                            testResults = testResDef.await(),
                            announcements = annDef.await(),
                            timetables = ttDef.await(),
                            homework = hwDef.await(),
                            notifications = notifDef.await()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, sRef.path)
                Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to load workspace."), e))
            }
        }

    // --- SESSIONS, CLASSES, SECTIONS ---
    suspend fun createAcademicSession(
        schoolId: String,
        name: String,
        startDate: String?,
        endDate: String?,
        makeCurrent: Boolean
    ): Result<AcademicSession> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val sessionRef = sRef.collection("academic_sessions").document()
            val existingCurrent = if (makeCurrent) {
                sRef.collection("academic_sessions")
                    .whereEqualTo("schoolOwnerId", school.ownerId)
                    .whereEqualTo("isCurrent", true)
                    .get()
                    .await()
                    .documents
            } else emptyList()

            val payload = mutableMapOf<String, Any>(
                "schoolId" to schoolId,
                "schoolOwnerId" to school.ownerId,
                "name" to name.trim().take(60),
                "isCurrent" to makeCurrent,
                "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            startDate?.trim()?.takeIf { it.isNotBlank() }?.let { payload["startDate"] = it }
            endDate?.trim()?.takeIf { it.isNotBlank() }?.let { payload["endDate"] = it }

            db.runBatch { batch ->
                existingCurrent.forEach { doc ->
                    batch.update(doc.reference, mapOf("isCurrent" to false, "updatedAt" to FieldValue.serverTimestamp()))
                }
                batch.set(sessionRef, payload)
                batch.update(
                    sRef,
                    mapOf(
                        "sessionIds" to FieldValue.arrayUnion(sessionRef.id),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            val created = sessionRef.get().await().toObject(
                AcademicSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Session creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${sRef.path}/academic_sessions")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create session."), e))
        }
    }

    suspend fun setCurrentSession(schoolId: String, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val allSessions = sRef.collection("academic_sessions")
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get()
                .await()
                .documents
            db.runBatch { batch ->
                allSessions.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "isCurrent" to (doc.id == sessionId),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "${sRef.path}/academic_sessions/$sessionId")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update session."), e))
        }
    }

    suspend fun createClassWithSection(
        schoolId: String,
        sessionId: String,
        className: String,
        sectionName: String,
        classTeacherId: String?,
        description: String,
        existingClassId: String?
    ): Result<ClassSection> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val staffUids = school.staffUids.ifEmpty { listOf(school.ownerId) }
            val memberUids = school.memberUids.ifEmpty { listOf(school.ownerId) }

            val classRef = if (existingClassId != null) {
                sRef.collection("classes").document(existingClassId)
            } else {
                sRef.collection("classes").document()
            }
            val sectionRef = sRef.collection("sections").document()

            db.runBatch { batch ->
                if (existingClassId == null) {
                    batch.set(
                        classRef,
                        mapOf(
                            "schoolId" to schoolId,
                            "schoolOwnerId" to school.ownerId,
                            "sessionId" to sessionId,
                            "name" to className.trim().take(80),
                            "description" to description.trim().take(500),
                            "staffUids" to staffUids,
                            "memberUids" to memberUids,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val sectionPayload = mutableMapOf<String, Any>(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "classId" to classRef.id,
                    "name" to sectionName.trim().take(40),
                    "description" to description.trim().take(500),
                    "staffUids" to staffUids,
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                classTeacherId?.trim()?.takeIf { it.isNotBlank() }?.let { sectionPayload["classTeacherId"] = it }
                batch.set(sectionRef, sectionPayload)

                val schoolUpdates = mutableMapOf<String, Any>(
                    "sectionIds" to FieldValue.arrayUnion(sectionRef.id),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existingClassId == null) {
                    schoolUpdates["classIds"] = FieldValue.arrayUnion(classRef.id)
                }
                batch.update(sRef, schoolUpdates)
            }.await()

            val createdSection = sectionRef.get().await().toObject(
                ClassSection::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Section creation failed."))
            Result.success(createdSection)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${sRef.path}/sections")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create class/section."), e))
        }
    }

    suspend fun updateSection(
        schoolId: String,
        sectionId: String,
        name: String,
        description: String,
        classTeacherId: String?
    ): Result<ClassSection> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId).collection("sections").document(sectionId)
        try {
            ref.update(
                mapOf(
                    "name" to name.trim().take(40),
                    "description" to description.trim().take(500),
                    "classTeacherId" to classTeacherId?.trim()?.ifBlank { null },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val updated = ref.get().await().toObject(
                ClassSection::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Section update returned empty."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Section update failed."), e))
        }
    }

    // --- STUDENTS ---
    suspend fun saveStudent(
        schoolId: String,
        existingId: String?,
        sessionId: String,
        classId: String,
        sectionId: String,
        rollNumber: String,
        fullName: String,
        fatherName: String,
        dateOfBirth: String?,
        gender: String,
        parentName: String,
        parentContact: String,
        address: String,
        admissionDate: String,
        email: String?,
        avatarUrl: String?
    ): Result<StudentRecord> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val col = sRef.collection("students")
        try {
            if (existingId == null) {
                val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@withContext Result.failure(IllegalStateException("School not found."))
                val docRef = col.document()
                val payload = mutableMapOf<String, Any>(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "studentCode" to newCode("STU"),
                    "rollNumber" to rollNumber.trim().take(40),
                    "fullName" to fullName.trim().take(120),
                    "fatherName" to fatherName.trim().take(120),
                    "gender" to gender.ifBlank { "Male" }.take(20),
                    "parentName" to parentName.trim().take(120),
                    "parentContact" to parentContact.trim().take(40),
                    "address" to address.trim().take(300),
                    "admissionDate" to admissionDate.ifBlank { todayIso() }.take(40),
                    "status" to "active",
                    "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dateOfBirth?.trim()?.takeIf { it.isNotBlank() }?.let { payload["dateOfBirth"] = it }
                email?.trim()?.takeIf { it.isNotBlank() }?.let { payload["email"] = it.take(160) }
                avatarUrl?.takeIf { it.isNotBlank() }?.let { payload["avatarUrl"] = it }
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to create student."))
                Result.success(created)
            } else {
                val docRef = col.document(existingId)
                val updates = mutableMapOf<String, Any?>(
                    "sessionId" to sessionId,
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "rollNumber" to rollNumber.trim().take(40),
                    "fullName" to fullName.trim().take(120),
                    "fatherName" to fatherName.trim().take(120),
                    "dateOfBirth" to dateOfBirth?.trim()?.ifBlank { null },
                    "gender" to gender.ifBlank { "Male" }.take(20),
                    "parentName" to parentName.trim().take(120),
                    "parentContact" to parentContact.trim().take(40),
                    "address" to address.trim().take(300),
                    "admissionDate" to admissionDate.ifBlank { todayIso() }.take(40),
                    "email" to email?.trim()?.ifBlank { null }?.take(160),
                    "avatarUrl" to avatarUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                val updated = docRef.get().await().toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to update student."))
                Result.success(updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to save student."), e))
        }
    }

    suspend fun transferStudent(
        schoolId: String,
        studentId: String,
        targetClassId: String,
        targetSectionId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = schoolDoc(schoolId).collection("students").document(studentId)
        try {
            docRef.update(
                mapOf(
                    "classId" to targetClassId,
                    "sectionId" to targetSectionId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Transfer failed."), e))
        }
    }

    suspend fun deleteStudent(schoolId: String, studentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = schoolDoc(schoolId).collection("students").document(studentId)
        try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete student."), e))
        }
    }

    suspend fun promoteStudent(
        sourceStudent: StudentRecord,
        targetSessionId: String,
        targetClassId: String,
        targetSectionId: String,
        newRollNumber: String
    ): Result<StudentRecord> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(sourceStudent.schoolId)
        val col = sRef.collection("students")
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val newDocRef = col.document()
            val oldDocRef = col.document(sourceStudent.id)

            val newPayload = mutableMapOf<String, Any>(
                "schoolId" to sourceStudent.schoolId,
                "schoolOwnerId" to school.ownerId,
                "sessionId" to targetSessionId,
                "classId" to targetClassId,
                "sectionId" to targetSectionId,
                "studentCode" to newCode("STU"),
                "rollNumber" to newRollNumber.trim().take(40),
                "fullName" to sourceStudent.fullName,
                "fatherName" to sourceStudent.fatherName,
                "gender" to sourceStudent.gender.ifBlank { "Male" },
                "parentName" to sourceStudent.parentName,
                "parentContact" to sourceStudent.parentContact,
                "address" to sourceStudent.address,
                "admissionDate" to sourceStudent.admissionDate.ifBlank { todayIso() },
                "status" to "active",
                "previousRecordId" to sourceStudent.id,
                "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            sourceStudent.dateOfBirth?.takeIf { it.isNotBlank() }?.let { newPayload["dateOfBirth"] = it }
            sourceStudent.email?.takeIf { it.isNotBlank() }?.let { newPayload["email"] = it }
            sourceStudent.avatarUrl?.takeIf { it.isNotBlank() }?.let { newPayload["avatarUrl"] = it }
            sourceStudent.linkedUserId?.takeIf { it.isNotBlank() }?.let { newPayload["linkedUserId"] = it }

            db.runBatch { batch ->
                batch.update(oldDocRef, mapOf("status" to "promoted", "updatedAt" to FieldValue.serverTimestamp()))
                batch.set(newDocRef, newPayload)
            }.await()

            val created = newDocRef.get().await().toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Promotion failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Promotion failed."), e))
        }
    }

    suspend fun requestStudentConnection(
        schoolId: String,
        schoolOwnerId: String?,
        schoolName: String?,
        userId: String,
        rollNumber: String,
        fullName: String,
        fatherName: String,
        sessionName: String,
        className: String,
        sectionName: String
    ): Result<StudentAccountRequest> = withContext(Dispatchers.IO) {
        val reqRef = schoolDoc(schoolId).collection("student_requests").document(userId)
        try {
            var resolvedOwner = schoolOwnerId.orEmpty()
            var resolvedName = schoolName.orEmpty()
            if (resolvedOwner.isBlank() || resolvedName.isBlank()) {
                val entry = db.collection("school_directory")
                    .whereEqualTo("isPublic", true)
                    .whereEqualTo("schoolId", schoolId)
                    .limit(1)
                    .get()
                    .await()
                    .toObjects(SchoolDirectoryEntry::class.java)
                    .firstOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("Selected school directory entry not found."))
                resolvedOwner = entry.ownerId
                resolvedName = entry.name
            }

            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolOwnerId" to resolvedOwner,
                "schoolName" to resolvedName.take(160),
                "userId" to userId,
                "requestedRollNumber" to rollNumber.trim().take(40),
                "requestedFullName" to fullName.trim().take(120),
                "requestedFatherName" to fatherName.trim().take(120),
                "requestedSessionName" to sessionName.trim().take(60),
                "requestedClassName" to className.trim().take(80),
                "requestedSectionName" to sectionName.trim().take(40),
                "status" to "pending",
                "staffUids" to listOf(resolvedOwner),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            reqRef.set(payload).await()
            val saved = reqRef.get().await().toObject(
                StudentAccountRequest::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Request submission failed."))
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reqRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to submit connection request."), e))
        }
    }

    suspend fun approveStudentConnection(
        schoolId: String,
        requestId: String,
        studentId: String
    ): Result<StudentAccountRequest> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val reqRef = sRef.collection("student_requests").document(requestId)
        val studentRef = sRef.collection("students").document(studentId)
        try {
            val req = reqRef.get().await().toObject(StudentAccountRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Request not found."))
            val student = studentRef.get().await().toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Student record not found."))
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val studentUid = req.userId
            val memberRef = sRef.collection("members").document(studentUid)

            db.runBatch { batch ->
                batch.update(
                    reqRef,
                    mapOf(
                        "status" to "approved",
                        "studentId" to studentId,
                        "sessionId" to student.sessionId,
                        "classId" to student.classId,
                        "sectionId" to student.sectionId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    studentRef,
                    mapOf(
                        "linkedUserId" to studentUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    memberRef,
                    mapOf(
                        "schoolId" to schoolId,
                        "schoolOwnerId" to school.ownerId,
                        "userId" to studentUid,
                        "role" to "student",
                        "status" to "active",
                        "linkedStudentId" to studentId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    sRef,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(studentUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            // Backfill member access onto existing school content and student historical records
            syncMemberAccessAfterStudentApproval(schoolId, school.ownerId, studentId, studentUid)

            try {
                db.collection("users").document(studentUid).update(
                    mapOf("activeSchoolId" to schoolId, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            } catch (_: Exception) {
            }

            val updatedReq = reqRef.get().await().toObject(
                StudentAccountRequest::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: req.copy(status = "approved", studentId = studentId)
            Result.success(updatedReq)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not approve student request."), e))
        }
    }

    private suspend fun syncMemberAccessAfterStudentApproval(
        schoolId: String,
        ownerId: String,
        studentId: String,
        studentUid: String
    ) {
        val sRef = schoolDoc(schoolId)
        val memberCollections = listOf(
            "academic_sessions", "classes", "sections",
            "attendance_sessions", "tests", "announcements", "timetables", "homework"
        )
        val docUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
        for (colName in memberCollections) {
            val docs = sRef.collection(colName).whereEqualTo("schoolOwnerId", ownerId).get().await().documents
            docs.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "memberUids" to FieldValue.arrayUnion(studentUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        sRef.collection("attendance_records")
            .whereEqualTo("schoolOwnerId", ownerId)
            .whereEqualTo("studentId", studentId)
            .get().await().documents.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "linkedUserId" to studentUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        sRef.collection("test_results")
            .whereEqualTo("schoolOwnerId", ownerId)
            .whereEqualTo("studentId", studentId)
            .get().await().documents.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "linkedUserId" to studentUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        commitChunkedUpdates(docUpdates)
    }

    private suspend fun syncStaffAccessAfterTeacherChange(schoolId: String, ownerId: String, teacherUid: String) {
        val sRef = schoolDoc(schoolId)
        val staffOnlyCols = listOf("students", "teacher_assignments", "attendance_records", "test_results")
        val staffAndMemberCols = listOf(
            "academic_sessions", "classes", "sections",
            "attendance_sessions", "tests", "announcements", "timetables", "homework"
        )
        val docUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
        staffOnlyCols.forEach { col ->
            sRef.collection(col).whereEqualTo("schoolOwnerId", ownerId).get().await().documents.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "staffUids" to FieldValue.arrayUnion(teacherUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        staffAndMemberCols.forEach { col ->
            sRef.collection(col).whereEqualTo("schoolOwnerId", ownerId).get().await().documents.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "staffUids" to FieldValue.arrayUnion(teacherUid),
                        "memberUids" to FieldValue.arrayUnion(teacherUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        commitChunkedUpdates(docUpdates)
    }

    private suspend fun commitChunkedUpdates(
        updates: List<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>
    ) {
        updates.chunked(400).forEach { chunk ->
            db.runBatch { batch ->
                chunk.forEach { (ref, map) -> batch.update(ref, map) }
            }.await()
        }
    }

    // --- TEACHERS & INVITATIONS ---
    suspend fun inviteTeacher(
        schoolId: String,
        invitedBy: String,
        fullName: String,
        email: String,
        phone: String,
        permissions: TeacherPermissions
    ): Result<TeacherInvitation> = withContext(Dispatchers.IO) {
        val inviteCode = newCode("TCH")
        val invRef = db.collection("teacher_invitations").document(inviteCode)
        try {
            val school = schoolDoc(schoolId).get().await()
                .toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolName" to school.name.take(160),
                "schoolOwnerId" to school.ownerId,
                "email" to email.trim().take(160),
                "emailLower" to email.trim().lowercase().take(160),
                "fullName" to fullName.trim().take(120),
                "phone" to phone.trim().take(40),
                "inviteCode" to inviteCode,
                "permissions" to permissions.toMap(),
                "status" to "pending",
                "invitedBy" to invitedBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            invRef.set(payload).await()
            val created = invRef.get().await().toObject(
                TeacherInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Invitation creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create invitation."), e))
        }
    }

    suspend fun acceptTeacherInvitation(
        inviteCode: String,
        userId: String,
        fallbackEmail: String = "",
        fallbackName: String = ""
    ): Result<TeacherProfile> = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase()
        val invRef = db.collection("teacher_invitations").document(cleanCode)
        try {
            val inv = invRef.get().await().toObject(
                TeacherInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Invitation code '$cleanCode' not found."))

            if (inv.status != "pending") {
                return@withContext Result.failure(IllegalStateException("This invitation is already ${inv.status}."))
            }

            val sRef = schoolDoc(inv.schoolId)
            val memberRef = sRef.collection("members").document(userId)
            val teacherRef = sRef.collection("teachers").document(userId)

            db.runBatch { batch ->
                batch.update(
                    invRef,
                    mapOf(
                        "status" to "accepted",
                        "acceptedByUid" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    memberRef,
                    mapOf(
                        "schoolId" to inv.schoolId,
                        "schoolOwnerId" to inv.schoolOwnerId,
                        "userId" to userId,
                        "role" to "teacher",
                        "status" to "active",
                        "inviteCode" to cleanCode,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    teacherRef,
                    mapOf(
                        "schoolId" to inv.schoolId,
                        "schoolOwnerId" to inv.schoolOwnerId,
                        "userId" to userId,
                        "fullName" to inv.fullName.ifBlank { fallbackName.ifBlank { "Teacher" } }.take(120),
                        "email" to inv.email.ifBlank { fallbackEmail }.take(160),
                        "phone" to inv.phone.take(40),
                        "joiningDate" to todayIso(),
                        "isActive" to true,
                        "permissions" to inv.permissions.toMap(),
                        "staffUids" to listOf(inv.schoolOwnerId, userId).distinct(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            // Post-acceptance: teacher membership now exists, so teacher can add self to school/session/class/section arrays
            try {
                val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val arrayPatch = mapOf(
                    "memberUids" to FieldValue.arrayUnion(userId),
                    "staffUids" to FieldValue.arrayUnion(userId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                sRef.update(arrayPatch).await()
                if (school != null) {
                    val backfill = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
                    school.sessionIds.forEach { sid ->
                        backfill.add(sRef.collection("academic_sessions").document(sid) to arrayPatch)
                    }
                    school.classIds.forEach { cid ->
                        backfill.add(sRef.collection("classes").document(cid) to arrayPatch)
                    }
                    school.sectionIds.forEach { secId ->
                        backfill.add(sRef.collection("sections").document(secId) to arrayPatch)
                    }
                    commitChunkedUpdates(backfill)
                }
            } catch (_: Exception) {
            }

            try {
                db.collection("users").document(userId).update(
                    mapOf("activeSchoolId" to inv.schoolId, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            } catch (_: Exception) {
            }

            val profile = teacherRef.get().await().toObject(
                TeacherProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Teacher profile creation failed."))
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to accept invitation."), e))
        }
    }

    suspend fun updateTeacherPermissionsAndStatus(
        schoolId: String,
        teacherProfileId: String,
        isActive: Boolean,
        permissions: TeacherPermissions
    ): Result<TeacherProfile> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val tRef = sRef.collection("teachers").document(teacherProfileId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val teacherBefore = tRef.get().await().toObject(TeacherProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Teacher not found."))

            db.runBatch { batch ->
                batch.update(
                    tRef,
                    mapOf(
                        "isActive" to isActive,
                        "permissions" to permissions.toMap(),
                        "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId, teacherBefore.userId).distinct() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    sRef.collection("members").document(teacherBefore.userId),
                    mapOf(
                        "status" to if (isActive) "active" else "suspended",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            if (isActive) {
                syncStaffAccessAfterTeacherChange(schoolId, school.ownerId, teacherBefore.userId)
            }

            val updated = tRef.get().await().toObject(
                TeacherProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Teacher update failed."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, tRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update teacher permissions."), e))
        }
    }

    suspend fun assignTeacherToSection(
        schoolId: String,
        sessionId: String,
        teacherProfileId: String,
        sectionId: String,
        subject: String
    ): Result<TeacherAssignment> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val assignRef = sRef.collection("teacher_assignments").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolOwnerId" to school.ownerId,
                "sessionId" to sessionId,
                "teacherProfileId" to teacherProfileId,
                "sectionId" to sectionId,
                "subject" to subject.trim().ifBlank { "General" }.take(100),
                "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId, teacherProfileId).distinct() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            assignRef.set(payload).await()
            val created = assignRef.get().await().toObject(
                TeacherAssignment::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Assignment failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, assignRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to assign teacher."), e))
        }
    }

    // --- ATTENDANCE (MULTI-SESSION PER DAY + EDIT HISTORY) ---
    suspend fun saveAttendanceSessionAndRecords(
        existingSessionId: String?,
        schoolId: String,
        sessionId: String,
        classId: String,
        sectionId: String,
        attendanceDate: String,
        subject: String,
        sessionSlot: String,
        teacherId: String,
        teacherName: String,
        statusByStudentId: Map<String, AttendanceStatus>,
        existingRecords: List<AttendanceRecord>,
        editorName: String,
        linkedUsersByStudentId: Map<String, String?> = emptyMap()
    ): Result<AttendanceSession> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val staffUids = (school.staffUids + teacherId).distinct()
            val memberUids = school.memberUids.ifEmpty { staffUids }

            val targetAttRef = if (existingSessionId == null) {
                sRef.collection("attendance_sessions").document()
            } else {
                sRef.collection("attendance_sessions").document(existingSessionId)
            }

            if (existingSessionId == null) {
                targetAttRef.set(
                    mapOf(
                        "schoolId" to schoolId,
                        "schoolOwnerId" to school.ownerId,
                        "sessionId" to sessionId,
                        "classId" to classId,
                        "sectionId" to sectionId,
                        "attendanceDate" to attendanceDate,
                        "subject" to subject.trim().ifBlank { "General" }.take(100),
                        "sessionSlot" to sessionSlot.trim().ifBlank { "Morning" }.take(60),
                        "teacherId" to teacherId,
                        "teacherName" to teacherName.take(120),
                        "staffUids" to staffUids,
                        "memberUids" to memberUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                targetAttRef.update(
                    mapOf(
                        "subject" to subject.trim().ifBlank { "General" }.take(100),
                        "sessionSlot" to sessionSlot.trim().ifBlank { "Morning" }.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val existingMap = existingRecords.associateBy { it.studentId }
            val editIso = nowIso()

            statusByStudentId.entries.chunked(350).forEach { chunk ->
                // Check which docs already exist when updating a session
                val existingDocIds = if (existingSessionId != null) {
                    chunk.mapNotNull { (studentId, _) ->
                        val docId = "${targetAttRef.id}_$studentId"
                        if (existingMap.containsKey(studentId) ||
                            sRef.collection("attendance_records").document(docId).get().await().exists()
                        ) docId else null
                    }.toSet()
                } else emptySet()

                db.runBatch { batch ->
                    chunk.forEach { (studentId, newStatus) ->
                        val docId = "${targetAttRef.id}_$studentId"
                        val recRef = sRef.collection("attendance_records").document(docId)
                        val prev = existingMap[studentId]
                        val historyList = prev?.editHistory.orEmpty().toMutableList()
                        if (prev != null && prev.status != newStatus.dbValue) {
                            historyList.add(
                                AttendanceEditEntry(
                                    editedBy = editorName,
                                    editedAt = editIso,
                                    fromStatus = prev.status,
                                    toStatus = newStatus.dbValue
                                )
                            )
                        }
                        val historyMaps = historyList.map {
                            mapOf(
                                "editedBy" to it.editedBy,
                                "editedAt" to it.editedAt,
                                "fromStatus" to it.fromStatus,
                                "toStatus" to it.toStatus
                            )
                        }
                        val linkedUid = prev?.linkedUserId ?: linkedUsersByStudentId[studentId]

                        if (docId in existingDocIds) {
                            val updates = mutableMapOf<String, Any?>(
                                "status" to newStatus.dbValue,
                                "note" to (prev?.note ?: ""),
                                "updatedBy" to teacherId,
                                "editHistory" to historyMaps,
                                "staffUids" to staffUids,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (linkedUid != null) updates["linkedUserId"] = linkedUid
                            batch.update(recRef, updates)
                        } else {
                            val payload = mutableMapOf<String, Any>(
                                "schoolId" to schoolId,
                                "schoolOwnerId" to school.ownerId,
                                "attendanceSessionId" to targetAttRef.id,
                                "studentId" to studentId,
                                "status" to newStatus.dbValue,
                                "note" to (prev?.note ?: ""),
                                "updatedBy" to teacherId,
                                "editHistory" to historyMaps,
                                "staffUids" to staffUids,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (linkedUid != null) payload["linkedUserId"] = linkedUid
                            batch.set(recRef, payload)
                        }
                    }
                }.await()
            }

            val savedSession = targetAttRef.get().await().toObject(
                AttendanceSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Failed to load saved attendance session."))
            Result.success(savedSession)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${sRef.path}/attendance_sessions")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to save attendance."), e))
        }
    }

    // --- TESTS & RESULTS ---
    suspend fun createTest(
        schoolId: String,
        sessionId: String,
        classId: String,
        sectionId: String,
        name: String,
        subject: String,
        testDate: String,
        totalMarks: Double,
        passingMarks: Double,
        description: String,
        createdBy: String
    ): Result<TestExam> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val testRef = sRef.collection("tests").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val safeTotal = totalMarks.coerceIn(1.0, 10000.0)
            val safePassing = passingMarks.coerceIn(0.0, safeTotal)

            testRef.set(
                mapOf(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "name" to name.trim().take(140),
                    "subject" to subject.trim().take(100),
                    "testDate" to testDate.ifBlank { todayIso() }.take(40),
                    "totalMarks" to safeTotal,
                    "passingMarks" to safePassing,
                    "description" to description.trim().take(1000),
                    "status" to "draft",
                    "createdBy" to createdBy,
                    "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                    "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId) },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val created = testRef.get().await().toObject(
                TestExam::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Test creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, testRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create test."), e))
        }
    }

    suspend fun saveTestResults(
        test: TestExam,
        marksByStudentId: Map<String, Double>,
        enteredBy: String,
        linkedUsersByStudentId: Map<String, String?> = emptyMap()
    ): Result<List<TestResult>> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(test.schoolId)
        val col = sRef.collection("test_results")
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val staffUids = (school.staffUids + enteredBy).distinct()
            val ranked = AcademicCalculators.buildRankedResults(test, marksByStudentId, enteredBy)

            val savedIds = mutableListOf<String>()
            ranked.chunked(350).forEach { chunk ->
                val existingFlags = chunk.associate { res ->
                    val docId = "${test.id}_${res.studentId}"
                    docId to col.document(docId).get().await().exists()
                }
                db.runBatch { batch ->
                    chunk.forEach { res ->
                        val docId = "${test.id}_${res.studentId}"
                        savedIds.add(docId)
                        val recRef = col.document(docId)
                        val linkedUid = linkedUsersByStudentId[res.studentId]

                        if (existingFlags[docId] == true) {
                            val updates = mutableMapOf<String, Any?>(
                                "obtainedMarks" to res.obtainedMarks,
                                "percentage" to res.percentage,
                                "grade" to res.grade,
                                "passed" to res.passed,
                                "rank" to res.rank,
                                "enteredBy" to enteredBy,
                                "staffUids" to staffUids,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (linkedUid != null) updates["linkedUserId"] = linkedUid
                            batch.update(recRef, updates)
                        } else {
                            val payload = mutableMapOf<String, Any>(
                                "schoolId" to test.schoolId,
                                "schoolOwnerId" to school.ownerId,
                                "testId" to test.id,
                                "studentId" to res.studentId,
                                "obtainedMarks" to res.obtainedMarks,
                                "percentage" to res.percentage,
                                "grade" to res.grade,
                                "passed" to res.passed,
                                "enteredBy" to enteredBy,
                                "staffUids" to staffUids,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            res.rank?.let { payload["rank"] = it }
                            if (linkedUid != null) payload["linkedUserId"] = linkedUid
                            batch.set(recRef, payload)
                        }
                    }
                }.await()
            }

            val saved = savedIds.mapNotNull { id ->
                col.document(id).get().await().toObject(TestResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to save test marks."), e))
        }
    }

    suspend fun updateTestStatus(
        schoolId: String,
        testId: String,
        status: String,
        approvedBy: String?
    ): Result<TestExam> = withContext(Dispatchers.IO) {
        val testRef = schoolDoc(schoolId).collection("tests").document(testId)
        try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (approvedBy != null) {
                updates["approvedBy"] = approvedBy
            }
            testRef.update(updates).await()
            val updated = testRef.get().await().toObject(
                TestExam::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Test update failed."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, testRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update test status."), e))
        }
    }

    // --- ANNOUNCEMENTS, LIKES, COMMENTS ---
    suspend fun createAnnouncement(
        schoolId: String,
        sessionId: String?,
        classId: String?,
        sectionId: String?,
        scope: String,
        category: String,
        title: String,
        body: String,
        imageUrl: String?,
        allowComments: Boolean,
        publisherId: String,
        publisherName: String
    ): Result<AnnouncementPost> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val postRef = sRef.collection("announcements").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val payload = mutableMapOf<String, Any>(
                "schoolId" to schoolId,
                "schoolOwnerId" to school.ownerId,
                "scope" to if (scope == "class") "class" else "school",
                "category" to category.trim().ifBlank { "Announcement" }.take(60),
                "title" to title.trim().take(200),
                "body" to body.trim().take(4000),
                "allowComments" to allowComments,
                "likesCount" to 0,
                "publisherId" to publisherId,
                "publisherName" to publisherName.take(120),
                "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            sessionId?.takeIf { it.isNotBlank() }?.let { payload["sessionId"] = it }
            classId?.takeIf { it.isNotBlank() }?.let { payload["classId"] = it }
            sectionId?.takeIf { it.isNotBlank() }?.let { payload["sectionId"] = it }
            imageUrl?.takeIf { it.isNotBlank() }?.let { payload["imageUrl"] = it }

            postRef.set(payload).await()
            val created = postRef.get().await().toObject(
                AnnouncementPost::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Announcement creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to publish post."), e))
        }
    }

    suspend fun deleteAnnouncement(schoolId: String, announcementId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val postRef = schoolDoc(schoolId).collection("announcements").document(announcementId)
            try {
                postRef.delete().await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, postRef.path)
                Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete post."), e))
            }
        }

    suspend fun toggleLike(schoolId: String, announcementId: String, userId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val postRef = schoolDoc(schoolId).collection("announcements").document(announcementId)
            val likeRef = postRef.collection("likes").document(userId)
            try {
                val newCount = db.runTransaction { tx ->
                    val likeSnap = tx.get(likeRef)
                    val postSnap = tx.get(postRef)
                    val currentCount = (postSnap.getLong("likesCount") ?: 0L).toInt()
                    if (likeSnap.exists()) {
                        tx.delete(likeRef)
                        val updated = (currentCount - 1).coerceAtLeast(0)
                        if (updated != currentCount) {
                            tx.update(
                                postRef,
                                mapOf("likesCount" to updated, "updatedAt" to FieldValue.serverTimestamp())
                            )
                        }
                        updated
                    } else {
                        tx.set(
                            likeRef,
                            mapOf(
                                "schoolId" to schoolId,
                                "announcementId" to announcementId,
                                "userId" to userId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val updated = currentCount + 1
                        tx.update(
                            postRef,
                            mapOf("likesCount" to updated, "updatedAt" to FieldValue.serverTimestamp())
                        )
                        updated
                    }
                }.await()
                Result.success(newCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, postRef.path)
                Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update like."), e))
            }
        }

    suspend fun getComments(
        schoolId: String,
        announcementId: String,
        userId: String,
        isPrincipal: Boolean,
        schoolOwnerId: String
    ): Result<List<AnnouncementComment>> = withContext(Dispatchers.IO) {
        val commentsCol = schoolDoc(schoolId).collection("announcements").document(announcementId).collection("comments")
        try {
            val q = if (isPrincipal) {
                commentsCol.whereEqualTo("schoolOwnerId", schoolOwnerId)
            } else {
                commentsCol.whereArrayContains("memberUids", userId)
            }
            val list = q.get().await()
                .toObjects(AnnouncementComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, commentsCol.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to load comments."), e))
        }
    }

    suspend fun addComment(
        schoolId: String,
        announcementId: String,
        userId: String,
        authorName: String,
        body: String
    ): Result<AnnouncementComment> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val commentRef = sRef.collection("announcements").document(announcementId).collection("comments").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            commentRef.set(
                mapOf(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "announcementId" to announcementId,
                    "userId" to userId,
                    "authorName" to authorName.take(120),
                    "body" to body.trim().take(1000),
                    "memberUids" to (school.memberUids + userId).distinct(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val created = commentRef.get().await().toObject(
                AnnouncementComment::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Comment creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to post comment."), e))
        }
    }

    // --- TIMETABLE, HOMEWORK, NOTIFICATIONS ---
    suspend fun createTimetableSlot(
        schoolId: String,
        sessionId: String,
        sectionId: String,
        dayOfWeek: String,
        subject: String,
        startTime: String,
        endTime: String,
        teacherName: String,
        roomNumber: String
    ): Result<TimetableSlot> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val slotRef = sRef.collection("timetables").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            slotRef.set(
                mapOf(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "sectionId" to sectionId,
                    "dayOfWeek" to dayOfWeek.take(20),
                    "subject" to subject.trim().take(100),
                    "startTime" to startTime.trim().take(30),
                    "endTime" to endTime.trim().take(30),
                    "teacherName" to teacherName.trim().take(120),
                    "roomNumber" to roomNumber.trim().take(40),
                    "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                    "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId) },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val created = slotRef.get().await().toObject(
                TimetableSlot::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Timetable slot failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, slotRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to add timetable slot."), e))
        }
    }

    suspend fun deleteTimetableSlot(schoolId: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId).collection("timetables").document(id)
        try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete slot."), e))
        }
    }

    suspend fun createHomework(
        schoolId: String,
        sessionId: String,
        sectionId: String,
        subject: String,
        title: String,
        instructions: String,
        dueDate: String,
        attachmentUrl: String?,
        teacherId: String,
        teacherName: String
    ): Result<HomeworkItem> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val hwRef = sRef.collection("homework").document()
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val payload = mutableMapOf<String, Any>(
                "schoolId" to schoolId,
                "schoolOwnerId" to school.ownerId,
                "sessionId" to sessionId,
                "sectionId" to sectionId,
                "subject" to subject.trim().take(100),
                "title" to title.trim().take(160),
                "instructions" to instructions.trim().take(3000),
                "dueDate" to dueDate.ifBlank { todayIso() }.take(40),
                "teacherId" to teacherId,
                "teacherName" to teacherName.take(120),
                "staffUids" to school.staffUids.ifEmpty { listOf(school.ownerId) },
                "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            attachmentUrl?.takeIf { it.isNotBlank() }?.let { payload["attachmentUrl"] = it }
            hwRef.set(payload).await()
            val created = hwRef.get().await().toObject(
                HomeworkItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Homework creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, hwRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create homework."), e))
        }
    }

    suspend fun deleteHomework(schoolId: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId).collection("homework").document(id)
        try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete homework."), e))
        }
    }

    suspend fun dispatchNotifications(
        schoolId: String,
        userIds: List<String>,
        title: String,
        message: String,
        category: String,
        referenceId: String? = null
    ) = withContext(Dispatchers.IO) {
        val distinctIds = userIds.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return@withContext
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext
            distinctIds.chunked(400).forEach { chunk ->
                db.runBatch { batch ->
                    chunk.forEach { uid ->
                        val notifRef = sRef.collection("notifications").document(UUID.randomUUID().toString())
                        val payload = mutableMapOf<String, Any>(
                            "schoolId" to schoolId,
                            "schoolOwnerId" to school.ownerId,
                            "userId" to uid,
                            "title" to title.take(200),
                            "message" to message.take(1000),
                            "category" to category.take(60),
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        referenceId?.takeIf { it.isNotBlank() }?.let { payload["referenceId"] = it }
                        batch.set(notifRef, payload)
                    }
                }.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${sRef.path}/notifications")
        }
    }

    suspend fun markNotificationRead(schoolId: String, notificationId: String) = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId).collection("notifications").document(notificationId)
        try {
            ref.update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    suspend fun markAllNotificationsRead(schoolId: String, userId: String) = withContext(Dispatchers.IO) {
        val col = schoolDoc(schoolId).collection("notifications")
        try {
            val unread = col.whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
                .documents
            commitChunkedUpdates(
                unread.map { it.reference to mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, col.path)
        }
    }
}
