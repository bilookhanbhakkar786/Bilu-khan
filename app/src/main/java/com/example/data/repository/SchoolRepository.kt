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
import com.example.data.model.ClassFeeDrive
import com.example.data.model.ClassSection
import com.example.data.model.HomeworkItem
import com.example.data.model.PrincipalInvitation
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolDirectoryEntry
import com.example.data.model.SchoolMembership
import com.example.data.model.StudentAccountRequest
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherAssignment
import com.example.data.model.TeacherCredential
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherJoinRequest
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
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
    val principalInvitations: List<PrincipalInvitation> = emptyList(),
    val teacherRequests: List<TeacherJoinRequest>,
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
    val classFees: List<ClassFeeDrive> = emptyList(),
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

    private fun todayDisplayDate(): String = AcademicCalculators.todayDisplayDate()

    private fun nowDisplayTimestamp(): String =
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())

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
        try {
            val userSnap = db.collection("users").document(ownerId).get().await()
            if (userSnap.exists() && userSnap.getString("accountIntent") == UserRole.STUDENT.dbValue) {
                return@withContext Result.failure(
                    IllegalStateException("Student accounts stay Student permanently and cannot create a school as Principal.")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
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
            "isPublic" to true,
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
            val cleanName = name.trim().take(160)
            val cleanCity = city.trim().take(100)
            val cleanPhone = contactNumber.trim().take(40)
            val cleanEmail = email.trim().take(160)
            if (cleanName.length < 2) {
                return@withContext Result.failure(IllegalArgumentException("School name must be at least 2 characters."))
            }
            if (cleanCity.isBlank() || cleanPhone.isBlank() || cleanEmail.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("City, contact number, and email are required."))
            }
            if (establishedYear != null && establishedYear !in 1800..2100) {
                return@withContext Result.failure(IllegalArgumentException("Established year must be between 1800 and 2100."))
            }
            val validType = if (schoolType in listOf("Private", "Government", "Other")) schoolType else "Private"
            val validYear = establishedYear?.takeIf { it in 1800..2100 }

            val updates = mutableMapOf<String, Any?>(
                "name" to cleanName,
                "schoolType" to validType,
                "address" to address.trim().take(300),
                "city" to cleanCity,
                "contactNumber" to cleanPhone,
                "email" to cleanEmail,
                "description" to description.trim().take(2000),
                "establishedYear" to validYear,
                "website" to website?.trim()?.ifBlank { null }?.take(300),
                "logoUrl" to logoUrl?.takeIf { it.isNotBlank() },
                "bannerUrl" to bannerUrl?.takeIf { it.isNotBlank() },
                "galleryUrls" to galleryUrls.filter { it.isNotBlank() }.take(8),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.runBatch { batch ->
                batch.update(ref, updates)
                if (current.schoolCode.isNotBlank()) {
                    batch.update(
                        db.collection("school_directory").document(current.schoolCode),
                        mapOf(
                            "name" to cleanName,
                            "schoolType" to validType,
                            "city" to cleanCity,
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

    suspend fun setSchoolVisibility(
        schoolId: String,
        isPublic: Boolean
    ): Result<School> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId)
        try {
            val current = ref.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School not found."))

            db.runBatch { batch ->
                batch.update(
                    ref,
                    mapOf(
                        "isPublic" to isPublic,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (current.schoolCode.isNotBlank()) {
                    batch.update(
                        db.collection("school_directory").document(current.schoolCode),
                        mapOf(
                            "isPublic" to isPublic,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            val updated = ref.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: current.copy(isPublic = isPublic)
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update school visibility."), e))
        }
    }

    suspend fun enterSchoolAsStudent(
        schoolId: String,
        userId: String
    ): Result<School> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val memberRef = sRef.collection("members").document(userId)
        val userRef = db.collection("users").document(userId)
        try {
            val school = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("This school is unavailable or hidden by its Principal."))

            if (!school.isPublic) {
                return@withContext Result.failure(
                    IllegalStateException("This school is hidden by the Principal and only visible to staff.")
                )
            }

            val existingMemberSnap = memberRef.get().await()
            val existingMember = existingMemberSnap.toObject(
                SchoolMembership::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val userSnap = userRef.get().await()

            db.runBatch { batch ->
                if (!existingMemberSnap.exists() || existingMember == null) {
                    batch.set(
                        memberRef,
                        mapOf(
                            "schoolId" to schoolId,
                            "schoolOwnerId" to school.ownerId,
                            "userId" to userId,
                            "role" to "student",
                            "status" to "active",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else if (existingMember.role == UserRole.STUDENT.dbValue && existingMember.status == "active") {
                    batch.update(
                        memberRef,
                        mapOf(
                            "status" to "active",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (userSnap.exists()) {
                    batch.update(
                        userRef,
                        mapOf(
                            "accountIntent" to "student",
                            "activeSchoolId" to schoolId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        userRef,
                        mapOf(
                            "userId" to userId,
                            "fullName" to "Student",
                            "email" to "",
                            "phone" to "",
                            "accountIntent" to "student",
                            "activeSchoolId" to schoolId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(school)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not open school."), e))
        }
    }

    suspend fun clearActiveSchoolForStudent(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        try {
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "accountIntent" to "student",
                        "activeSchoolId" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                userRef.set(
                    mapOf(
                        "userId" to userId,
                        "fullName" to "Student",
                        "email" to "",
                        "phone" to "",
                        "accountIntent" to "student",
                        "activeSchoolId" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not switch school."), e))
        }
    }

    suspend fun pinSchoolForStudent(
        userId: String,
        schoolId: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School not found or unavailable."))
            if (!school.isPublic) {
                return@withContext Result.failure(
                    IllegalStateException("This school is hidden by the Principal and cannot be pinned.")
                )
            }
            val userSnap = userRef.get().await()
            val currentPinned = (userSnap.get("pinnedSchoolIds") as? List<*>)
                ?.filterIsInstance<String>()
                ?.distinct()
                ?.take(3)
                .orEmpty()
            if (currentPinned.contains(schoolId)) {
                return@withContext Result.success(currentPinned)
            }
            if (currentPinned.size >= 3) {
                return@withContext Result.failure(
                    IllegalStateException("You can pin up to 3 schools only. Unpin one first to pin a new school.")
                )
            }
            val updatedPinned = (currentPinned + schoolId).take(3)
            val memberRef = sRef.collection("members").document(userId)
            val existingMemberSnap = memberRef.get().await()

            db.runBatch { batch ->
                if (!existingMemberSnap.exists()) {
                    batch.set(
                        memberRef,
                        mapOf(
                            "schoolId" to schoolId,
                            "schoolOwnerId" to school.ownerId,
                            "userId" to userId,
                            "role" to "student",
                            "status" to "active",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (userSnap.exists()) {
                    val userUpdates = mutableMapOf<String, Any>(
                        "pinnedSchoolIds" to updatedPinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (userSnap.getString("activeSchoolId").isNullOrBlank()) {
                        userUpdates["activeSchoolId"] = schoolId
                    }
                    batch.update(userRef, userUpdates)
                } else {
                    batch.set(
                        userRef,
                        mapOf(
                            "userId" to userId,
                            "fullName" to "Student",
                            "email" to "",
                            "phone" to "",
                            "accountIntent" to "student",
                            "activeSchoolId" to schoolId,
                            "pinnedSchoolIds" to updatedPinned,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(updatedPinned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not pin school."), e))
        }
    }

    suspend fun unpinSchoolForStudent(
        userId: String,
        schoolId: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        try {
            val userSnap = userRef.get().await()
            val currentPinned = (userSnap.get("pinnedSchoolIds") as? List<*>)
                ?.filterIsInstance<String>()
                ?.distinct()
                .orEmpty()
            val updatedPinned = currentPinned.filterNot { it == schoolId }.take(3)
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "pinnedSchoolIds" to updatedPinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(updatedPinned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not unpin school."), e))
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
                        city = entry.city,
                        isPublic = entry.isPublic
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

                if (role == UserRole.STUDENT && !school.isPublic) {
                    return@withContext Result.failure(
                        IllegalStateException("This school has been hidden by the Principal and is only visible to staff.")
                    )
                }

                fun scopedCollectionQuery(subcollection: String, memberArrayField: String = "memberUids"): Query {
                    val col = sRef.collection(subcollection)
                    return when (role) {
                        UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                        UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                        UserRole.STUDENT -> col.whereEqualTo("schoolId", schoolId)
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
                        val col = sRef.collection("teachers")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereEqualTo("schoolId", schoolId)
                        }
                        safeListQuery<TeacherProfile>(q, "${sRef.path}/teachers")
                    }
                    val invitesDef = async {
                        if (role == UserRole.PRINCIPAL) {
                            safeListQuery<TeacherInvitation>(
                                db.collection("teacher_invitations").whereEqualTo("schoolId", schoolId),
                                "teacher_invitations"
                            )
                        } else emptyList()
                    }
                    val principalInvitesDef = async {
                        if (role == UserRole.PRINCIPAL) {
                            safeListQuery<PrincipalInvitation>(
                                db.collection("principal_invitations").whereEqualTo("schoolId", schoolId),
                                "principal_invitations"
                            )
                        } else emptyList()
                    }
                    val teacherReqsDef = async {
                        val col = sRef.collection("teacher_requests")
                        when (role) {
                            UserRole.PRINCIPAL -> safeListQuery<TeacherJoinRequest>(
                                col.whereEqualTo("schoolOwnerId", school.ownerId),
                                "${sRef.path}/teacher_requests"
                            )
                            UserRole.TEACHER -> safeListQuery<TeacherJoinRequest>(
                                col.whereEqualTo("userId", userId),
                                "${sRef.path}/teacher_requests"
                            )
                            UserRole.STUDENT -> emptyList()
                        }
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
                            UserRole.STUDENT -> safeListQuery<TeacherAssignment>(
                                sRef.collection("teacher_assignments").whereEqualTo("schoolId", schoolId),
                                "${sRef.path}/teacher_assignments"
                            )
                        }
                    }
                    val studentsDef = async {
                        safeListQuery<StudentRecord>(
                            scopedCollectionQuery("students", "memberUids"),
                            "${sRef.path}/students"
                        )
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
                        safeListQuery<AttendanceRecord>(
                            scopedCollectionQuery("attendance_records", "memberUids"),
                            "${sRef.path}/attendance_records"
                        )
                    }
                    val testsDef = async {
                        val col = sRef.collection("tests")
                        val q = when (role) {
                            UserRole.PRINCIPAL -> col.whereEqualTo("schoolOwnerId", school.ownerId)
                            UserRole.TEACHER -> col.whereArrayContains("staffUids", userId)
                            UserRole.STUDENT -> col.whereEqualTo("schoolId", schoolId)
                        }
                        safeListQuery<TestExam>(q, "${sRef.path}/tests")
                    }
                    val testResDef = async {
                        safeListQuery<TestResult>(
                            scopedCollectionQuery("test_results", "memberUids"),
                            "${sRef.path}/test_results"
                        )
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
                    val feesDef = async {
                        safeListQuery<ClassFeeDrive>(
                            scopedCollectionQuery("class_fees", "memberUids"),
                            "${sRef.path}/class_fees"
                        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }
                    val notifDef = async {
                        safeListQuery<AppNotification>(
                            sRef.collection("notifications").whereEqualTo("userId", userId),
                            "${sRef.path}/notifications"
                        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                    }

                    val rawClasses = classesDef.await()
                    val sortedClasses = AcademicCalculators.sortClasses(rawClasses)
                    val sortedSections = AcademicCalculators.sortSections(sectionsDef.await(), sortedClasses)
                    val sortedTeachers = AcademicCalculators.sortTeachers(teachersDef.await())
                    val sortedAssignments = AcademicCalculators.sortTeacherAssignments(
                        assignDef.await(),
                        sortedClasses,
                        sortedSections
                    )
                    val sortedStudents = AcademicCalculators.sortStudentsByClassAndRoll(
                        studentsDef.await(),
                        sortedClasses,
                        sortedSections
                    )
                    val sortedAttSessions = AcademicCalculators.sortAttendanceSessions(
                        attSessionsDef.await(),
                        sortedClasses,
                        sortedSections
                    )
                    val sortedTests = AcademicCalculators.sortTests(
                        testsDef.await(),
                        sortedClasses,
                        sortedSections
                    )
                    val sortedTimetables = AcademicCalculators.sortTimetableSlots(
                        ttDef.await(),
                        sortedClasses,
                        sortedSections
                    )
                    val sortedHomework = AcademicCalculators.sortHomework(
                        hwDef.await(),
                        sortedClasses,
                        sortedSections
                    )

                    Result.success(
                        SchoolWorkspaceBundle(
                            school = school,
                            sessions = AcademicCalculators.sortSessions(sessionsDef.await()),
                            classes = sortedClasses,
                            sections = sortedSections,
                            teachers = sortedTeachers,
                            teacherInvitations = invitesDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            principalInvitations = principalInvitesDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            teacherRequests = teacherReqsDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            teacherAssignments = sortedAssignments,
                            students = sortedStudents,
                            studentRequests = reqsDef.await().sortedByDescending { it.createdAt?.seconds ?: 0L },
                            attendanceSessions = sortedAttSessions,
                            attendanceRecords = attRecsDef.await(),
                            tests = sortedTests,
                            testResults = testResDef.await(),
                            announcements = annDef.await(),
                            timetables = sortedTimetables,
                            homework = sortedHomework,
                            classFees = feesDef.await(),
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
                    .get()
                    .await()
                    .documents
                    .filter { it.getBoolean("isCurrent") == true }
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
            startDate?.trim()?.takeIf { it.isNotBlank() }?.let {
                payload["startDate"] = AcademicCalculators.formatDisplayDate(it)
            }
            endDate?.trim()?.takeIf { it.isNotBlank() }?.let {
                payload["endDate"] = AcademicCalculators.formatDisplayDate(it)
            }

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
                    "customLectures" to emptyList<String>(),
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

    suspend fun addSectionLectureSlot(
        schoolId: String,
        sectionId: String,
        lectureName: String
    ): Result<ClassSection> = withContext(Dispatchers.IO) {
        val clean = lectureName.trim().take(60)
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Lecture name cannot be blank."))
        }
        val ref = schoolDoc(schoolId).collection("sections").document(sectionId)
        try {
            val existing = ref.get().await().toObject(ClassSection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Section not found."))
            val alreadyExists = (AcademicCalculators.DEFAULT_LECTURE_SLOTS + existing.customLectures)
                .any { it.equals(clean, ignoreCase = true) }
            if (!alreadyExists) {
                val updatedList = AcademicCalculators.sortLectureSlots(existing.customLectures + clean).take(50)
                ref.update(
                    mapOf(
                        "customLectures" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val updated = ref.get().await().toObject(
                ClassSection::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: existing.copy(
                customLectures = AcademicCalculators.sortLectureSlots(existing.customLectures + clean)
            )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not add lecture slot."), e))
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
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val staffUids = school.staffUids.ifEmpty { listOf(school.ownerId) }
            val memberUids = school.memberUids.ifEmpty { staffUids }

            val sectionPeers = col
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get()
                .await()
                .toObjects(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.sessionId == sessionId && it.sectionId == sectionId && it.status == "active" }

            val shifts = AcademicCalculators.computeRollNumberShifts(
                existingInSection = sectionPeers,
                newRollNumber = rollNumber,
                excludeStudentId = existingId
            )

            val formattedDob = dateOfBirth?.trim()?.takeIf { it.isNotBlank() }?.let {
                AcademicCalculators.formatDisplayDate(it)
            }
            val formattedAdmDate = AcademicCalculators.formatDisplayDate(admissionDate)
                .ifBlank { todayDisplayDate() }
                .take(40)

            val targetRef = if (existingId == null) col.document() else col.document(existingId)

            db.runBatch { batch ->
                shifts.forEach { (peerId, newRoll) ->
                    batch.update(
                        col.document(peerId),
                        mapOf(
                            "rollNumber" to newRoll.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (existingId == null) {
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
                        "admissionDate" to formattedAdmDate,
                        "status" to "active",
                        "staffUids" to staffUids,
                        "memberUids" to memberUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    formattedDob?.let { payload["dateOfBirth"] = it }
                    email?.trim()?.takeIf { it.isNotBlank() }?.let { payload["email"] = it.take(160) }
                    avatarUrl?.takeIf { it.isNotBlank() }?.let { payload["avatarUrl"] = it }
                    batch.set(targetRef, payload)
                } else {
                    val updates = mutableMapOf<String, Any?>(
                        "sessionId" to sessionId,
                        "classId" to classId,
                        "sectionId" to sectionId,
                        "rollNumber" to rollNumber.trim().take(40),
                        "fullName" to fullName.trim().take(120),
                        "fatherName" to fatherName.trim().take(120),
                        "dateOfBirth" to formattedDob,
                        "gender" to gender.ifBlank { "Male" }.take(20),
                        "parentName" to parentName.trim().take(120),
                        "parentContact" to parentContact.trim().take(40),
                        "address" to address.trim().take(300),
                        "admissionDate" to formattedAdmDate,
                        "email" to email?.trim()?.ifBlank { null }?.take(160),
                        "avatarUrl" to avatarUrl,
                        "staffUids" to staffUids,
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.update(targetRef, updates)
                }
            }.await()

            val saved = targetRef.get().await().toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Failed to save student."))
            Result.success(saved)
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
        val sRef = schoolDoc(schoolId)
        val docRef = sRef.collection("students").document(studentId)
        try {
            val student = docRef.get().await().toObject(
                StudentRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            docRef.delete().await()

            val linkedUid = student?.linkedUserId?.trim()?.takeIf { it.isNotBlank() }
            if (linkedUid != null) {
                val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (school != null && linkedUid != school.ownerId) {
                    val memberRef = sRef.collection("members").document(linkedUid)
                    val memberSnap = memberRef.get().await()
                    if (!memberSnap.exists() || memberSnap.getString("role") == "student") {
                        try {
                            if (memberSnap.exists()) memberRef.delete().await()
                            val reqRef = sRef.collection("student_requests").document(linkedUid)
                            if (reqRef.get().await().exists()) reqRef.delete().await()
                            sRef.update(
                                mapOf(
                                    "memberUids" to FieldValue.arrayRemove(linkedUid),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            removeUidFromSubcollections(
                                schoolId = schoolId,
                                ownerId = school.ownerId,
                                uidToRemove = linkedUid,
                                removeFromStaff = false,
                                clearLinkedUserId = true
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            }
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
            val staffUids = school.staffUids.ifEmpty { listOf(school.ownerId) }
            val memberUids = school.memberUids.ifEmpty { staffUids }

            val targetPeers = col
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get()
                .await()
                .toObjects(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.sessionId == targetSessionId && it.sectionId == targetSectionId && it.status == "active" }

            val shifts = AcademicCalculators.computeRollNumberShifts(
                existingInSection = targetPeers,
                newRollNumber = newRollNumber,
                excludeStudentId = null
            )

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
                "admissionDate" to AcademicCalculators.formatDisplayDate(sourceStudent.admissionDate).ifBlank { todayDisplayDate() },
                "status" to "active",
                "previousRecordId" to sourceStudent.id,
                "staffUids" to staffUids,
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            sourceStudent.dateOfBirth?.takeIf { it.isNotBlank() }?.let {
                newPayload["dateOfBirth"] = AcademicCalculators.formatDisplayDate(it)
            }
            sourceStudent.email?.takeIf { it.isNotBlank() }?.let { newPayload["email"] = it }
            sourceStudent.avatarUrl?.takeIf { it.isNotBlank() }?.let { newPayload["avatarUrl"] = it }
            sourceStudent.linkedUserId?.takeIf { it.isNotBlank() }?.let { newPayload["linkedUserId"] = it }

            db.runBatch { batch ->
                shifts.forEach { (peerId, shiftedRoll) ->
                    batch.update(
                        col.document(peerId),
                        mapOf(
                            "rollNumber" to shiftedRoll.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
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
                    .get()
                    .await()
                    .toObjects(SchoolDirectoryEntry::class.java)
                    .firstOrNull { it.schoolId == schoolId }
                    ?: return@withContext Result.failure(IllegalStateException("Selected school directory entry not found."))
                resolvedOwner = entry.ownerId
                resolvedName = entry.name
            }

            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolOwnerId" to resolvedOwner,
                "schoolName" to resolvedName.take(160),
                "userId" to userId,
                "requestedRollNumber" to rollNumber.trim().ifBlank { "External" }.take(40),
                "requestedFullName" to fullName.trim().take(120),
                "requestedFatherName" to fatherName.trim().ifBlank { "N/A" }.take(120),
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
        studentId: String? = null
    ): Result<StudentAccountRequest> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val reqRef = sRef.collection("student_requests").document(requestId)
        val cleanStudentId = studentId?.trim()?.takeIf { it.isNotBlank() }
        try {
            val req = reqRef.get().await().toObject(StudentAccountRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Request not found."))
            val student = if (cleanStudentId != null) {
                sRef.collection("students").document(cleanStudentId).get().await()
                    .toObject(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@withContext Result.failure(IllegalStateException("Student record not found."))
            } else null

            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val studentUid = req.userId
            val memberRef = sRef.collection("members").document(studentUid)

            db.runBatch { batch ->
                val reqUpdates = mutableMapOf<String, Any?>(
                    "status" to "approved",
                    "studentId" to cleanStudentId,
                    "sessionId" to student?.sessionId,
                    "classId" to student?.classId,
                    "sectionId" to student?.sectionId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(reqRef, reqUpdates)
                if (cleanStudentId != null) {
                    batch.update(
                        sRef.collection("students").document(cleanStudentId),
                        mapOf(
                            "linkedUserId" to studentUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val memberPayload = mutableMapOf<String, Any>(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "userId" to studentUid,
                    "role" to "student",
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanStudentId != null) {
                    memberPayload["linkedStudentId"] = cleanStudentId
                }
                batch.set(memberRef, memberPayload)
                batch.update(
                    sRef,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(studentUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            syncMemberAccessAfterStudentApproval(schoolId, school.ownerId, cleanStudentId, studentUid)

            try {
                db.collection("users").document(studentUid).update(
                    mapOf("activeSchoolId" to schoolId, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            } catch (_: Exception) {
            }

            val updatedReq = reqRef.get().await().toObject(
                StudentAccountRequest::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: req.copy(status = "approved", studentId = cleanStudentId)
            Result.success(updatedReq)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not approve student request."), e))
        }
    }

    suspend fun rejectStudentConnection(schoolId: String, requestId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val reqRef = schoolDoc(schoolId).collection("student_requests").document(requestId)
            try {
                reqRef.update(
                    mapOf(
                        "status" to "rejected",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
                Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to reject request."), e))
            }
        }

    private suspend fun syncMemberAccessAfterStudentApproval(
        schoolId: String,
        ownerId: String,
        studentId: String?,
        memberUid: String
    ) {
        val sRef = schoolDoc(schoolId)
        val memberCollections = listOf(
            "academic_sessions", "classes", "sections", "students", "teachers",
            "attendance_sessions", "attendance_records", "tests", "test_results",
            "announcements", "timetables", "homework", "class_fees"
        )
        val docUpdates = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
        for (colName in memberCollections) {
            val docs = sRef.collection(colName).whereEqualTo("schoolOwnerId", ownerId).get().await().documents
            docs.forEach { d ->
                docUpdates.add(
                    d.reference to mapOf(
                        "memberUids" to FieldValue.arrayUnion(memberUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        if (!studentId.isNullOrBlank()) {
            sRef.collection("attendance_records")
                .whereEqualTo("schoolOwnerId", ownerId)
                .get().await().documents
                .filter { it.getString("studentId") == studentId }
                .forEach { d ->
                    docUpdates.add(
                        d.reference to mapOf(
                            "linkedUserId" to memberUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            sRef.collection("test_results")
                .whereEqualTo("schoolOwnerId", ownerId)
                .get().await().documents
                .filter { it.getString("studentId") == studentId }
                .forEach { d ->
                    docUpdates.add(
                        d.reference to mapOf(
                            "linkedUserId" to memberUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
        }
        commitChunkedUpdates(docUpdates)
    }

    private suspend fun syncStaffAccessAfterTeacherChange(schoolId: String, ownerId: String, teacherUid: String) {
        val sRef = schoolDoc(schoolId)
        val staffOnlyCols = listOf("teacher_assignments")
        val staffAndMemberCols = listOf(
            "academic_sessions", "classes", "sections", "students", "teachers",
            "attendance_sessions", "attendance_records", "tests", "test_results",
            "announcements", "timetables", "homework", "class_fees"
        )
        val docUpdates = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
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
        updates: List<Pair<DocumentReference, Map<String, Any?>>>
    ) {
        updates.chunked(400).forEach { chunk ->
            db.runBatch { batch ->
                chunk.forEach { (ref, map) -> batch.update(ref, map) }
            }.await()
        }
    }

    private suspend fun commitChunkedDeletes(refs: List<DocumentReference>) {
        refs.chunked(400).forEach { chunk ->
            db.runBatch { batch ->
                chunk.forEach { ref -> batch.delete(ref) }
            }.await()
        }
    }

    private suspend fun removeUidFromSubcollections(
        schoolId: String,
        ownerId: String,
        uidToRemove: String,
        removeFromStaff: Boolean,
        clearLinkedUserId: Boolean = false
    ) {
        val sRef = schoolDoc(schoolId)
        val docUpdates = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

        val memberCols = listOf(
            "academic_sessions", "classes", "sections", "students", "teachers",
            "attendance_sessions", "attendance_records", "tests", "test_results",
            "announcements", "timetables", "homework", "class_fees"
        )
        for (col in memberCols) {
            val docs = sRef.collection(col).whereEqualTo("schoolOwnerId", ownerId).get().await().documents
            docs.forEach { d ->
                val updates = mutableMapOf<String, Any?>()
                val members = d.get("memberUids") as? List<*>
                if (members?.contains(uidToRemove) == true) {
                    updates["memberUids"] = FieldValue.arrayRemove(uidToRemove)
                }
                if (removeFromStaff) {
                    val staff = d.get("staffUids") as? List<*>
                    if (staff?.contains(uidToRemove) == true) {
                        updates["staffUids"] = FieldValue.arrayRemove(uidToRemove)
                    }
                }
                if (clearLinkedUserId && col in listOf("students", "attendance_records", "test_results")) {
                    if (d.getString("linkedUserId") == uidToRemove) {
                        updates["linkedUserId"] = null
                    }
                }
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    docUpdates.add(d.reference to updates)
                }
            }
            if (col == "announcements") {
                docs.forEach { ann ->
                    val comments = ann.reference.collection("comments").get().await().documents
                    comments.forEach { c ->
                        val cMembers = c.get("memberUids") as? List<*>
                        if (cMembers?.contains(uidToRemove) == true) {
                            docUpdates.add(
                                c.reference to mapOf(
                                    "memberUids" to FieldValue.arrayRemove(uidToRemove),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }
            }
        }

        if (removeFromStaff) {
            listOf("teacher_requests", "student_requests").forEach { col ->
                sRef.collection(col).whereEqualTo("schoolOwnerId", ownerId).get().await().documents.forEach { d ->
                    val staff = d.get("staffUids") as? List<*>
                    if (staff?.contains(uidToRemove) == true) {
                        docUpdates.add(
                            d.reference to mapOf(
                                "staffUids" to FieldValue.arrayRemove(uidToRemove),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }
        }

        commitChunkedUpdates(docUpdates)
    }

    // --- TEACHERS, SELF-SERVICE JOIN REQUESTS & INVITATIONS ---
    suspend fun requestTeacherJoin(
        schoolId: String,
        schoolOwnerId: String?,
        schoolName: String?,
        userId: String,
        fullName: String,
        email: String,
        phone: String,
        subjectSpecialty: String,
        note: String
    ): Result<TeacherJoinRequest> = withContext(Dispatchers.IO) {
        val reqRef = schoolDoc(schoolId).collection("teacher_requests").document(userId)
        try {
            var resolvedOwner = schoolOwnerId.orEmpty()
            var resolvedName = schoolName.orEmpty()
            if (resolvedOwner.isBlank() || resolvedName.isBlank()) {
                val entry = db.collection("school_directory")
                    .whereEqualTo("isPublic", true)
                    .get()
                    .await()
                    .toObjects(SchoolDirectoryEntry::class.java)
                    .firstOrNull { it.schoolId == schoolId }
                    ?: return@withContext Result.failure(IllegalStateException("Selected school directory entry not found."))
                resolvedOwner = entry.ownerId
                resolvedName = entry.name
            }

            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolOwnerId" to resolvedOwner,
                "schoolName" to resolvedName.take(160),
                "userId" to userId,
                "fullName" to fullName.trim().take(120),
                "email" to email.trim().take(160),
                "phone" to phone.trim().take(40),
                "subjectSpecialty" to subjectSpecialty.trim().take(120),
                "note" to note.trim().take(500),
                "status" to "pending",
                "staffUids" to listOf(resolvedOwner),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            reqRef.set(payload).await()
            val saved = reqRef.get().await().toObject(
                TeacherJoinRequest::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Teacher request submission failed."))
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reqRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to submit teacher join request."), e))
        }
    }

    suspend fun approveTeacherRequest(
        schoolId: String,
        requestId: String
    ): Result<TeacherProfile> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        val reqRef = sRef.collection("teacher_requests").document(requestId)
        try {
            val req = reqRef.get().await().toObject(TeacherJoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Teacher request not found."))
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val teacherUid = req.userId
            val memberRef = sRef.collection("members").document(teacherUid)
            val teacherRef = sRef.collection("teachers").document(teacherUid)
            val staffUids = (school.staffUids + school.ownerId + teacherUid).distinct()
            val memberUids = (school.memberUids + teacherUid).distinct()
            val fullPerms = TeacherPermissions(
                viewStudents = true,
                editStudents = true,
                markAttendance = true,
                editAttendance = true,
                createTests = true,
                enterMarks = true,
                publishAnnouncements = true,
                viewReports = true
            )

            db.runBatch { batch ->
                batch.update(
                    reqRef,
                    mapOf(
                        "status" to "approved",
                        "staffUids" to staffUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    memberRef,
                    mapOf(
                        "schoolId" to schoolId,
                        "schoolOwnerId" to school.ownerId,
                        "userId" to teacherUid,
                        "role" to "teacher",
                        "status" to "active",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    teacherRef,
                    mapOf(
                        "schoolId" to schoolId,
                        "schoolOwnerId" to school.ownerId,
                        "userId" to teacherUid,
                        "fullName" to req.fullName.ifBlank { "Teacher" }.take(120),
                        "email" to req.email.take(160),
                        "phone" to req.phone.take(40),
                        "joiningDate" to todayDisplayDate(),
                        "isActive" to true,
                        "permissions" to fullPerms.toMap(),
                        "staffUids" to staffUids,
                        "memberUids" to memberUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    sRef,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(teacherUid),
                        "staffUids" to FieldValue.arrayUnion(teacherUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            syncStaffAccessAfterTeacherChange(schoolId, school.ownerId, teacherUid)

            val profile = teacherRef.get().await().toObject(
                TeacherProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Teacher approval failed."))
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to approve teacher request."), e))
        }
    }

    suspend fun rejectTeacherRequest(schoolId: String, requestId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val reqRef = schoolDoc(schoolId).collection("teacher_requests").document(requestId)
            try {
                reqRef.update(
                    mapOf(
                        "status" to "rejected",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
                Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to reject teacher request."), e))
            }
        }

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

            val cleanName = fullName.trim().ifBlank { "Invited Teacher" }.take(120)
            val cleanEmail = email.trim().take(160)
            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolName" to school.name.take(160),
                "schoolOwnerId" to school.ownerId,
                "email" to cleanEmail,
                "emailLower" to cleanEmail.lowercase().take(160),
                "fullName" to cleanName,
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

    suspend fun inviteTeacherWithCredentials(
        schoolId: String,
        invitedBy: String,
        fullName: String,
        email: String,
        phone: String,
        username: String,
        accessPassword: String,
        permissions: TeacherPermissions
    ): Result<TeacherInvitation> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@").trim()
        val cleanUsernameLower = cleanUsername.lowercase()
        val cleanPassword = accessPassword.trim()

        if (cleanUsernameLower.length < 3 || cleanUsernameLower.length > 40) {
            return@withContext Result.failure(IllegalArgumentException("Username must be between 3 and 40 characters."))
        }
        if (cleanPassword.length < 4 || cleanPassword.length > 100) {
            return@withContext Result.failure(IllegalArgumentException("Password / Code must be between 4 and 100 characters."))
        }
        if (!Regex("^[a-zA-Z0-9_.-]+$").matches(cleanUsernameLower)) {
            return@withContext Result.failure(
                IllegalArgumentException("Username can only contain letters, numbers, dots, hyphens, and underscores.")
            )
        }

        val credRef = db.collection("teacher_credentials").document(cleanUsernameLower)
        val inviteCode = newCode("TCH")
        val invRef = db.collection("teacher_invitations").document(inviteCode)

        try {
            val existingCred = credRef.get().await()
            if (existingCred.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Username '$cleanUsername' is already taken. Please choose another username.")
                )
            }

            val school = schoolDoc(schoolId).get().await()
                .toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val cleanName = fullName.trim().ifBlank { "Teacher ($cleanUsername)" }.take(120)
            val cleanEmail = email.trim().take(160)
            val cleanPhone = phone.trim().take(40)

            val invPayload = mapOf(
                "schoolId" to schoolId,
                "schoolName" to school.name.take(160),
                "schoolOwnerId" to school.ownerId,
                "email" to cleanEmail,
                "emailLower" to cleanEmail.lowercase().take(160),
                "fullName" to cleanName,
                "phone" to cleanPhone,
                "inviteCode" to inviteCode,
                "permissions" to permissions.toMap(),
                "status" to "pending",
                "invitedBy" to invitedBy,
                "authType" to "credentials",
                "username" to cleanUsername,
                "usernameLower" to cleanUsernameLower,
                "accessPassword" to cleanPassword,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val credPayload = mapOf(
                "username" to cleanUsername,
                "usernameLower" to cleanUsernameLower,
                "accessPassword" to cleanPassword,
                "inviteCode" to inviteCode,
                "schoolId" to schoolId,
                "schoolName" to school.name.take(160),
                "schoolOwnerId" to school.ownerId,
                "status" to "pending",
                "invitedBy" to invitedBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.set(invRef, invPayload)
                batch.set(credRef, credPayload)
            }.await()

            val created = invRef.get().await().toObject(
                TeacherInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Invitation creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create teacher credentials."), e))
        }
    }

    suspend fun verifyTeacherCredentials(
        username: String,
        accessPassword: String
    ): Result<TeacherCredential> = withContext(Dispatchers.IO) {
        val cleanUsernameLower = username.trim().removePrefix("@").trim().lowercase()
        val cleanPassword = accessPassword.trim()
        if (cleanUsernameLower.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your username."))
        }
        if (cleanPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password or code."))
        }
        val credRef = db.collection("teacher_credentials").document(cleanUsernameLower)
        try {
            val snap = credRef.get().await()
            if (!snap.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Username '$cleanUsernameLower' was not found. Please verify with your principal.")
                )
            }
            val cred = snap.toObject(TeacherCredential::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("Could not read credential details."))
            if (cred.status != "pending") {
                return@withContext Result.failure(IllegalStateException("This username has already been ${cred.status}."))
            }
            if (cred.accessPassword.trim() != cleanPassword) {
                return@withContext Result.failure(
                    IllegalStateException("Incorrect password or code. Please check with your principal.")
                )
            }
            Result.success(cred)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, credRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to verify credentials."), e))
        }
    }

    suspend fun acceptTeacherByCredentials(
        username: String,
        accessPassword: String,
        userId: String,
        fallbackEmail: String = "",
        teacherEnteredName: String = "",
        teacherEnteredPhone: String = ""
    ): Result<TeacherProfile> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@").trim()
        val verifyRes = verifyTeacherCredentials(cleanUsername, accessPassword)
        if (verifyRes.isFailure) {
            return@withContext Result.failure(verifyRes.exceptionOrNull() ?: IllegalStateException("Invalid credentials."))
        }
        val cred = verifyRes.getOrThrow()
        acceptTeacherInvitation(
            inviteCode = cred.inviteCode,
            userId = userId,
            fallbackEmail = fallbackEmail,
            fallbackName = teacherEnteredName.ifBlank { cred.username },
            fallbackPhone = teacherEnteredPhone
        )
    }

    suspend fun acceptTeacherInvitation(
        inviteCode: String,
        userId: String,
        fallbackEmail: String = "",
        fallbackName: String = "",
        fallbackPhone: String = ""
    ): Result<TeacherProfile> = withContext(Dispatchers.IO) {
        val cleanCode = (com.example.data.model.TeacherInviteLinks.extractInviteCode(inviteCode)
            ?: inviteCode.trim().uppercase())
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please enter or open a valid Teacher invite link or code."))
        }
        val invRef = db.collection("teacher_invitations").document(cleanCode)
        try {
            val userSnap = db.collection("users").document(userId).get().await()
            if (userSnap.exists() && userSnap.getString("accountIntent") == UserRole.STUDENT.dbValue) {
                return@withContext Result.failure(
                    IllegalStateException("Student accounts stay Student permanently and cannot become a Teacher.")
                )
            }
            val inv = invRef.get().await().toObject(
                TeacherInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Invitation '$cleanCode' not found."))

            if (inv.status != "pending") {
                return@withContext Result.failure(IllegalStateException("This invitation is already ${inv.status}."))
            }

            val sRef = schoolDoc(inv.schoolId)
            val memberRef = sRef.collection("members").document(userId)
            val teacherRef = sRef.collection("teachers").document(userId)
            val existingMemberSnap = memberRef.get().await()
            if (existingMemberSnap.exists() && existingMemberSnap.getString("role") == UserRole.STUDENT.dbValue) {
                return@withContext Result.failure(
                    IllegalStateException("Student accounts stay Student permanently and cannot become a Teacher.")
                )
            }
            val existingTeacherSnap = teacherRef.get().await()
            val resolvedName = when {
                fallbackName.isNotBlank() -> fallbackName.trim()
                inv.fullName.isNotBlank() && inv.fullName != "Invited Teacher" -> inv.fullName.trim()
                else -> "Teacher"
            }.take(120)
            val resolvedPhone = when {
                fallbackPhone.isNotBlank() -> fallbackPhone.trim()
                inv.phone.isNotBlank() -> inv.phone.trim()
                else -> ""
            }.take(40)
            val resolvedEmail = inv.email.ifBlank { fallbackEmail }.take(160)

            db.runBatch { batch ->
                batch.update(
                    invRef,
                    mapOf(
                        "status" to "accepted",
                        "acceptedByUid" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val usernameLower = inv.usernameLower?.trim()?.lowercase()
                if (!usernameLower.isNullOrBlank()) {
                    val credRef = db.collection("teacher_credentials").document(usernameLower)
                    batch.update(
                        credRef,
                        mapOf(
                            "status" to "accepted",
                            "acceptedByUid" to userId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (existingMemberSnap.exists()) {
                    batch.update(
                        memberRef,
                        mapOf(
                            "schoolOwnerId" to inv.schoolOwnerId,
                            "role" to "teacher",
                            "status" to "active",
                            "inviteCode" to cleanCode,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
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
                }
                if (existingTeacherSnap.exists()) {
                    batch.update(
                        teacherRef,
                        mapOf(
                            "schoolOwnerId" to inv.schoolOwnerId,
                            "fullName" to resolvedName,
                            "email" to resolvedEmail,
                            "phone" to resolvedPhone,
                            "joiningDate" to todayDisplayDate(),
                            "isActive" to true,
                            "permissions" to inv.permissions.toMap(),
                            "staffUids" to listOf(inv.schoolOwnerId, userId).distinct(),
                            "memberUids" to listOf(inv.schoolOwnerId, userId).distinct(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        teacherRef,
                        mapOf(
                            "schoolId" to inv.schoolId,
                            "schoolOwnerId" to inv.schoolOwnerId,
                            "userId" to userId,
                            "fullName" to resolvedName,
                            "email" to resolvedEmail,
                            "phone" to resolvedPhone,
                            "joiningDate" to todayDisplayDate(),
                            "isActive" to true,
                            "permissions" to inv.permissions.toMap(),
                            "staffUids" to listOf(inv.schoolOwnerId, userId).distinct(),
                            "memberUids" to listOf(inv.schoolOwnerId, userId).distinct(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            try {
                sRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(userId),
                        "staffUids" to FieldValue.arrayUnion(userId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                syncStaffAccessAfterTeacherChange(inv.schoolId, inv.schoolOwnerId, userId)
            } catch (_: Exception) {
            }

            try {
                val userRef = db.collection("users").document(userId)
                val userSnap = userRef.get().await()
                if (userSnap.exists()) {
                    userRef.update(
                        mapOf(
                            "fullName" to resolvedName,
                            "phone" to resolvedPhone,
                            "accountIntent" to "teacher",
                            "activeSchoolId" to inv.schoolId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    userRef.set(
                        mapOf(
                            "userId" to userId,
                            "fullName" to resolvedName,
                            "email" to resolvedEmail,
                            "phone" to resolvedPhone,
                            "accountIntent" to "teacher",
                            "activeSchoolId" to inv.schoolId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
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
                        "memberUids" to school.memberUids.ifEmpty { listOf(school.ownerId, teacherBefore.userId).distinct() },
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
            val normalizedDate = AcademicCalculators.formatDisplayDate(attendanceDate).ifBlank { todayDisplayDate() }

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
                        "attendanceDate" to normalizedDate,
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
                        "staffUids" to staffUids,
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val existingMap = existingRecords.associateBy { it.studentId }
            val editTs = nowDisplayTimestamp()

            statusByStudentId.entries.chunked(350).forEach { chunk ->
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
                                    editedAt = editTs,
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
                                "memberUids" to memberUids,
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
                                "memberUids" to memberUids,
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
            val formattedDate = AcademicCalculators.formatDisplayDate(testDate).ifBlank { todayDisplayDate() }

            testRef.set(
                mapOf(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "name" to name.trim().take(140),
                    "subject" to subject.trim().take(100),
                    "testDate" to formattedDate.take(40),
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
            val memberUids = school.memberUids.ifEmpty { staffUids }
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
                                "memberUids" to memberUids,
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
                                "memberUids" to memberUids,
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
                commentsCol.whereEqualTo("schoolId", schoolId)
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
            val formattedDue = AcademicCalculators.formatDisplayDate(dueDate)
                .ifBlank { AcademicCalculators.plusDaysDisplayDate(1) }
                .take(40)
            val payload = mutableMapOf<String, Any>(
                "schoolId" to schoolId,
                "schoolOwnerId" to school.ownerId,
                "sessionId" to sessionId,
                "sectionId" to sectionId,
                "subject" to subject.trim().take(100),
                "title" to title.trim().take(160),
                "instructions" to instructions.trim().take(3000),
                "dueDate" to formattedDue,
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

    suspend fun saveClassFeeDrive(
        existingFeeId: String?,
        schoolId: String,
        sessionId: String,
        classId: String,
        sectionId: String,
        title: String,
        description: String,
        amountText: String,
        feeDate: String,
        userId: String,
        userName: String,
        initialPaidStudentIds: List<String> = emptyList()
    ): Result<ClassFeeDrive> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter fee detail (e.g. Annual Function, Monthly Fee)."))
        }
        val sRef = schoolDoc(schoolId)
        val feeRef = if (existingFeeId.isNullOrBlank()) {
            sRef.collection("class_fees").document()
        } else {
            sRef.collection("class_fees").document(existingFeeId)
        }
        try {
            val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))
            val cleanDesc = description.trim().take(1000)
            val cleanAmount = amountText.trim().take(40)
            val cleanDate = AcademicCalculators.formatDisplayDate(feeDate).ifBlank { todayDisplayDate() }.take(40)
            val cleanUser = userName.trim().ifBlank { "Staff" }.take(120)

            if (existingFeeId.isNullOrBlank()) {
                val paidIds = initialPaidStudentIds.filter { it.isNotBlank() }.distinct()
                val stamp = nowDisplayTimestamp()
                val paidDetailsMap = paidIds.associateWith { "Paid • $stamp ($cleanUser)" }
                val payload = mapOf(
                    "schoolId" to schoolId,
                    "schoolOwnerId" to school.ownerId,
                    "sessionId" to sessionId,
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "amountText" to cleanAmount,
                    "feeDate" to cleanDate,
                    "paidStudentIds" to paidIds,
                    "paidDetails" to paidDetailsMap,
                    "createdBy" to userId,
                    "createdByName" to cleanUser,
                    "updatedByName" to cleanUser,
                    "staffUids" to school.staffUids.distinct(),
                    "memberUids" to school.memberUids.distinct(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                feeRef.set(payload).await()
            } else {
                feeRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "amountText" to cleanAmount,
                        "feeDate" to cleanDate,
                        "updatedByName" to cleanUser,
                        "schoolOwnerId" to school.ownerId,
                        "staffUids" to school.staffUids.distinct(),
                        "memberUids" to school.memberUids.distinct(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val saved = feeRef.get().await().toObject(
                ClassFeeDrive::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Could not save class fee."))
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingFeeId == null) OperationType.CREATE else OperationType.UPDATE, feeRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to save fee details."), e))
        }
    }

    suspend fun toggleStudentFeePaidStatus(
        schoolId: String,
        feeId: String,
        studentId: String,
        markPaid: Boolean,
        updatedByName: String
    ): Result<ClassFeeDrive> = withContext(Dispatchers.IO) {
        val feeRef = schoolDoc(schoolId).collection("class_fees").document(feeId)
        val cleanUser = updatedByName.trim().ifBlank { "Staff" }.take(120)
        try {
            val stamp = nowDisplayTimestamp()
            db.runTransaction { tx ->
                val snap = tx.get(feeRef)
                val existing = snap.toObject(
                    ClassFeeDrive::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Fee record not found.")

                val updatedPaidIds = if (markPaid) {
                    (existing.paidStudentIds + studentId).distinct()
                } else {
                    existing.paidStudentIds.filterNot { it == studentId }
                }
                val updatedDetails = existing.paidDetails.toMutableMap()
                if (markPaid) {
                    updatedDetails[studentId] = "Paid • $stamp ($cleanUser)"
                } else {
                    updatedDetails.remove(studentId)
                }

                tx.update(
                    feeRef,
                    mapOf(
                        "paidStudentIds" to updatedPaidIds,
                        "paidDetails" to updatedDetails,
                        "updatedByName" to cleanUser,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            val updated = feeRef.get().await().toObject(
                ClassFeeDrive::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Updated fee record not found."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, feeRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to update student fee status."), e))
        }
    }

    suspend fun deleteClassFeeDrive(schoolId: String, feeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = schoolDoc(schoolId).collection("class_fees").document(feeId)
        try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete fee record."), e))
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
                .get()
                .await()
                .documents
                .filter { it.getBoolean("isRead") == false }
            commitChunkedUpdates(
                unread.map { it.reference to mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, col.path)
        }
    }

    // --- PRINCIPAL HANDOVER, ACCOUNT DELETION & ACCEPTED MEMBER REMOVAL ---

    suspend fun inviteReplacementPrincipal(
        schoolId: String,
        invitedBy: String,
        fullName: String,
        email: String
    ): Result<PrincipalInvitation> = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim().take(120)
        val cleanEmail = email.trim().take(160)
        if (cleanName.isBlank() || cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Replacement Principal's full name and email are required."))
        }
        val inviteCode = newCode("PRN")
        val invRef = db.collection("principal_invitations").document(inviteCode)
        try {
            val school = schoolDoc(schoolId).get().await()
                .toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return@withContext Result.failure(IllegalStateException("School not found."))

            val payload = mapOf(
                "schoolId" to schoolId,
                "schoolName" to school.name.take(160),
                "schoolCode" to school.schoolCode.take(40),
                "schoolOwnerId" to school.ownerId,
                "email" to cleanEmail,
                "emailLower" to cleanEmail.lowercase(),
                "fullName" to cleanName,
                "inviteCode" to inviteCode,
                "status" to "pending",
                "invitedBy" to invitedBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            invRef.set(payload).await()
            val created = invRef.get().await().toObject(
                PrincipalInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Principal invitation creation failed."))
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to create principal invite."), e))
        }
    }

    suspend fun revokePrincipalInvitation(inviteCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        val invRef = db.collection("principal_invitations").document(inviteCode.trim().uppercase())
        try {
            invRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to remove principal invitation."), e))
        }
    }

    suspend fun acceptPrincipalInvitation(
        inviteCode: String,
        newPrincipalUid: String,
        fallbackEmail: String = "",
        fallbackName: String = ""
    ): Result<School> = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter a Principal transfer code."))
        }
        val invRef = db.collection("principal_invitations").document(cleanCode)
        try {
            val inv = invRef.get().await().toObject(
                PrincipalInvitation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Principal invite '$cleanCode' not found."))

            if (inv.status != "pending") {
                return@withContext Result.failure(IllegalStateException("This principal invitation is already ${inv.status}."))
            }
            if (inv.schoolOwnerId == newPrincipalUid) {
                return@withContext Result.failure(IllegalStateException("You are already the Principal of this school."))
            }

            val userSnap = db.collection("users").document(newPrincipalUid).get().await()
            if (userSnap.exists() && userSnap.getString("accountIntent") == UserRole.STUDENT.dbValue) {
                return@withContext Result.failure(
                    IllegalStateException("Student accounts stay Student permanently and cannot become a Principal.")
                )
            }

            val lockRef = db.collection("principal_locks").document(newPrincipalUid)
            if (lockRef.get().await().exists()) {
                return@withContext Result.failure(
                    IllegalStateException("This account already owns a school. Delete or transfer your current school before taking over another.")
                )
            }

            val oldOwnerId = inv.schoolOwnerId
            val sRef = schoolDoc(inv.schoolId)
            val newMemberRef = sRef.collection("members").document(newPrincipalUid)
            val existingMemberSnap = newMemberRef.get().await()
            val dirRef = db.collection("school_directory").document(inv.schoolCode)

            // Phase 1: Atomic principal ownership transfer
            db.runBatch { batch ->
                batch.update(
                    invRef,
                    mapOf(
                        "status" to "accepted",
                        "acceptedByUid" to newPrincipalUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    lockRef,
                    mapOf(
                        "userId" to newPrincipalUid,
                        "schoolId" to inv.schoolId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    sRef,
                    mapOf(
                        "ownerId" to newPrincipalUid,
                        "lastAcceptedPrincipalInviteCode" to cleanCode,
                        "memberUids" to FieldValue.arrayUnion(newPrincipalUid),
                        "staffUids" to FieldValue.arrayUnion(newPrincipalUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (existingMemberSnap.exists()) {
                    batch.update(
                        newMemberRef,
                        mapOf(
                            "schoolOwnerId" to newPrincipalUid,
                            "role" to "principal",
                            "status" to "active",
                            "inviteCode" to cleanCode,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        newMemberRef,
                        mapOf(
                            "schoolId" to inv.schoolId,
                            "schoolOwnerId" to newPrincipalUid,
                            "userId" to newPrincipalUid,
                            "role" to "principal",
                            "status" to "active",
                            "inviteCode" to cleanCode,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (inv.schoolCode.isNotBlank()) {
                    batch.update(
                        dirRef,
                        mapOf(
                            "ownerId" to newPrincipalUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            // Phase 2: Now running with newPrincipalUid == school.ownerId, clean up old principal & backfill schoolOwnerId
            try {
                val oldLockRef = db.collection("principal_locks").document(oldOwnerId)
                if (oldLockRef.get().await().exists()) {
                    oldLockRef.delete().await()
                }
            } catch (_: Exception) {
            }

            try {
                val oldMemberRef = sRef.collection("members").document(oldOwnerId)
                if (oldMemberRef.get().await().exists()) {
                    oldMemberRef.delete().await()
                }
                val oldTeacherRef = sRef.collection("teachers").document(oldOwnerId)
                if (oldTeacherRef.get().await().exists()) {
                    oldTeacherRef.delete().await()
                }
                sRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayRemove(oldOwnerId),
                        "staffUids" to FieldValue.arrayRemove(oldOwnerId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }

            val backfillUpdates = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

            // Update invitations belonging to this school so schoolOwnerId stays aligned with new principal
            db.collection("teacher_invitations")
                .whereEqualTo("schoolId", inv.schoolId)
                .get().await().documents
                .forEach { d ->
                    if (d.getString("schoolOwnerId") != newPrincipalUid) {
                        backfillUpdates.add(
                            d.reference to mapOf(
                                "schoolOwnerId" to newPrincipalUid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

            db.collection("principal_invitations")
                .whereEqualTo("schoolId", inv.schoolId)
                .get().await().documents
                .forEach { d ->
                    if (d.getString("schoolOwnerId") != newPrincipalUid) {
                        backfillUpdates.add(
                            d.reference to mapOf(
                                "schoolOwnerId" to newPrincipalUid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

            // Backfill remaining active members
            sRef.collection("members").get().await().documents.forEach { m ->
                if (m.getString("schoolOwnerId") != newPrincipalUid) {
                    backfillUpdates.add(
                        m.reference to mapOf(
                            "schoolOwnerId" to newPrincipalUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            val subColsWithMembersAndStaff = listOf(
                "academic_sessions", "classes", "sections", "students", "teachers",
                "attendance_sessions", "attendance_records", "tests", "test_results",
                "announcements", "timetables", "homework", "class_fees"
            )
            subColsWithMembersAndStaff.forEach { col ->
                val docs = sRef.collection(col).get().await().documents
                docs.forEach { d ->
                    val updates = mutableMapOf<String, Any?>(
                        "schoolOwnerId" to newPrincipalUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    val curMembers = (d.get("memberUids") as? List<*>)?.filterIsInstance<String>()
                    if (curMembers != null) {
                        updates["memberUids"] = (curMembers - oldOwnerId + newPrincipalUid).distinct()
                    }
                    val curStaff = (d.get("staffUids") as? List<*>)?.filterIsInstance<String>()
                    if (curStaff != null) {
                        updates["staffUids"] = (curStaff - oldOwnerId + newPrincipalUid).distinct()
                    }
                    backfillUpdates.add(d.reference to updates)
                }
                if (col == "announcements") {
                    docs.forEach { ann ->
                        ann.reference.collection("comments").get().await().documents.forEach { c ->
                            val curMembers = (c.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                            backfillUpdates.add(
                                c.reference to mapOf(
                                    "schoolOwnerId" to newPrincipalUid,
                                    "memberUids" to (curMembers - oldOwnerId + newPrincipalUid).distinct(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }
            }

            listOf("teacher_assignments", "teacher_requests", "student_requests").forEach { col ->
                sRef.collection(col).get().await().documents.forEach { d ->
                    val curStaff = (d.get("staffUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    backfillUpdates.add(
                        d.reference to mapOf(
                            "schoolOwnerId" to newPrincipalUid,
                            "staffUids" to (curStaff - oldOwnerId + newPrincipalUid).distinct(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            sRef.collection("notifications").get().await().documents.forEach { n ->
                if (n.getString("schoolOwnerId") != newPrincipalUid) {
                    backfillUpdates.add(
                        n.reference to mapOf(
                            "schoolOwnerId" to newPrincipalUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            commitChunkedUpdates(backfillUpdates)

            try {
                db.collection("users").document(newPrincipalUid).update(
                    mapOf(
                        "accountIntent" to "principal",
                        "activeSchoolId" to inv.schoolId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }

            val updatedSchool = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Principal transfer completed, could not reload school."))
            Result.success(updatedSchool)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to accept principal invitation."), e))
        }
    }

    suspend fun deletePrincipalAccountAndSchool(
        schoolId: String,
        ownerId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School not found."))

            if (school.ownerId != ownerId) {
                return@withContext Result.failure(IllegalStateException("Only the school owner can delete this Principal account."))
            }

            val refsToDelete = mutableListOf<DocumentReference>()

            // Nested likes & comments under announcements must be deleted before announcements/school
            val announcements = sRef.collection("announcements").get().await().documents
            for (ann in announcements) {
                ann.reference.collection("comments").get().await().documents.forEach { refsToDelete.add(it.reference) }
                ann.reference.collection("likes").get().await().documents.forEach { refsToDelete.add(it.reference) }
                refsToDelete.add(ann.reference)
            }

            val schoolSubCollections = listOf(
                "teacher_assignments", "teacher_requests", "teachers",
                "student_requests", "students",
                "attendance_records", "attendance_sessions",
                "test_results", "tests",
                "timetables", "homework", "class_fees", "notifications",
                "sections", "classes", "academic_sessions", "members"
            )
            for (col in schoolSubCollections) {
                sRef.collection(col).get().await().documents.forEach { refsToDelete.add(it.reference) }
            }

            db.collection("teacher_invitations")
                .whereEqualTo("schoolOwnerId", ownerId)
                .get().await().documents
                .filter { it.getString("schoolId") == schoolId }
                .forEach { refsToDelete.add(it.reference) }

            db.collection("teacher_credentials")
                .whereEqualTo("schoolOwnerId", ownerId)
                .get().await().documents
                .filter { it.getString("schoolId") == schoolId }
                .forEach { refsToDelete.add(it.reference) }

            db.collection("principal_invitations")
                .whereEqualTo("schoolOwnerId", ownerId)
                .get().await().documents
                .filter { it.getString("schoolId") == schoolId }
                .forEach { refsToDelete.add(it.reference) }

            if (school.schoolCode.isNotBlank()) {
                val dirRef = db.collection("school_directory").document(school.schoolCode)
                if (dirRef.get().await().exists()) {
                    refsToDelete.add(dirRef)
                }
            }

            commitChunkedDeletes(refsToDelete)

            // Final batch: delete school root doc (so isSchoolPrincipal isn't needed anymore), principal lock & user profile
            val finalRefs = mutableListOf<DocumentReference>(
                sRef,
                db.collection("principal_locks").document(ownerId)
            )
            val userRef = db.collection("users").document(ownerId)
            if (userRef.get().await().exists()) {
                finalRefs.add(userRef)
            }
            commitChunkedDeletes(finalRefs)

            try {
                Firebase.auth.currentUser?.delete()?.await()
            } catch (_: Exception) {
                // Recent-login check may require reauth on older tokens; sign-out still clears session cleanly
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete Principal account."), e))
        }
    }

    suspend fun removeTeacherFromSchool(
        schoolId: String,
        teacher: TeacherProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School not found."))

            if (teacher.userId == school.ownerId) {
                return@withContext Result.failure(IllegalStateException("Cannot remove the school owner."))
            }

            val refsToDelete = mutableListOf<DocumentReference>()
            val tRef = sRef.collection("teachers").document(teacher.id)
            if (tRef.get().await().exists()) refsToDelete.add(tRef)

            val memberRef = sRef.collection("members").document(teacher.userId)
            if (memberRef.get().await().exists()) refsToDelete.add(memberRef)

            val reqRef = sRef.collection("teacher_requests").document(teacher.userId)
            if (reqRef.get().await().exists()) refsToDelete.add(reqRef)

            sRef.collection("teacher_assignments")
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get().await().documents
                .filter {
                    val assignedId = it.getString("teacherProfileId")
                    assignedId == teacher.id || assignedId == teacher.userId
                }
                .forEach { refsToDelete.add(it.reference) }

            db.collection("teacher_invitations")
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get().await().documents
                .filter {
                    it.getString("schoolId") == schoolId && (
                        it.getString("acceptedByUid") == teacher.userId ||
                            (!teacher.email.isBlank() && it.getString("emailLower") == teacher.email.trim().lowercase())
                        )
                }
                .forEach { invDoc ->
                    refsToDelete.add(invDoc.reference)
                    val usernameLower = invDoc.getString("usernameLower")?.trim()?.lowercase()
                    if (!usernameLower.isNullOrBlank()) {
                        val credRef = db.collection("teacher_credentials").document(usernameLower)
                        if (credRef.get().await().exists()) {
                            refsToDelete.add(credRef)
                        }
                    }
                }

            db.collection("teacher_credentials")
                .whereEqualTo("schoolOwnerId", school.ownerId)
                .get().await().documents
                .filter {
                    it.getString("schoolId") == schoolId &&
                        it.getString("acceptedByUid") == teacher.userId
                }
                .forEach { credDoc ->
                    if (refsToDelete.none { it.path == credDoc.reference.path }) {
                        refsToDelete.add(credDoc.reference)
                    }
                }

            commitChunkedDeletes(refsToDelete)

            sRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(teacher.userId),
                    "staffUids" to FieldValue.arrayRemove(teacher.userId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            removeUidFromSubcollections(
                schoolId = schoolId,
                ownerId = school.ownerId,
                uidToRemove = teacher.userId,
                removeFromStaff = true,
                clearLinkedUserId = false
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${sRef.path}/teachers/${teacher.id}")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to remove teacher."), e))
        }
    }

    suspend fun deleteTeacherInvitation(
        schoolId: String,
        invite: TeacherInvitation
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val invRef = db.collection("teacher_invitations").document(invite.inviteCode)
        try {
            val sRef = schoolDoc(schoolId)
            val acceptedUid = invite.acceptedByUid?.trim()?.takeIf { it.isNotBlank() }
            if (acceptedUid != null) {
                val teacherDoc = sRef.collection("teachers").document(acceptedUid).get().await()
                    .toObject(TeacherProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (teacherDoc != null) {
                    return@withContext removeTeacherFromSchool(schoolId, teacherDoc)
                }
                val school = sRef.get().await().toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (school != null && acceptedUid != school.ownerId) {
                    val mRef = sRef.collection("members").document(acceptedUid)
                    if (mRef.get().await().exists()) mRef.delete().await()
                    sRef.update(
                        mapOf(
                            "memberUids" to FieldValue.arrayRemove(acceptedUid),
                            "staffUids" to FieldValue.arrayRemove(acceptedUid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    removeUidFromSubcollections(schoolId, school.ownerId, acceptedUid, removeFromStaff = true)
                }
            }
            if (invRef.get().await().exists()) {
                invRef.delete().await()
            }
            val usernameLower = invite.usernameLower?.trim()?.lowercase()
            if (!usernameLower.isNullOrBlank()) {
                try {
                    val credRef = db.collection("teacher_credentials").document(usernameLower)
                    if (credRef.get().await().exists()) {
                        credRef.delete().await()
                    }
                } catch (_: Exception) {
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, invRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete teacher invitation."), e))
        }
    }

    suspend fun removeApprovedStudentAccount(
        schoolId: String,
        request: StudentAccountRequest
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sRef = schoolDoc(schoolId)
        try {
            val school = sRef.get().await().toObject(
                School::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("School not found."))

            if (request.userId == school.ownerId) {
                return@withContext Result.failure(IllegalStateException("Cannot remove the school owner."))
            }

            val refsToDelete = mutableListOf<DocumentReference>()
            val memberRef = sRef.collection("members").document(request.userId)
            if (memberRef.get().await().exists()) refsToDelete.add(memberRef)

            val reqRef = sRef.collection("student_requests").document(request.id)
            if (reqRef.get().await().exists()) refsToDelete.add(reqRef)

            commitChunkedDeletes(refsToDelete)

            sRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(request.userId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            removeUidFromSubcollections(
                schoolId = schoolId,
                ownerId = school.ownerId,
                uidToRemove = request.userId,
                removeFromStaff = false,
                clearLinkedUserId = true
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${sRef.path}/student_requests/${request.id}")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to remove approved student account."), e))
        }
    }

    suspend fun deleteNonPrincipalAccountAndData(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val memberDocs = try {
                db.collectionGroup("members")
                    .whereEqualTo("userId", userId)
                    .get().await().documents
            } catch (_: Exception) {
                emptyList()
            }

            for (memDoc in memberDocs) {
                val schoolId = memDoc.getString("schoolId").orEmpty()
                val role = memDoc.getString("role").orEmpty()
                if (schoolId.isBlank() || role == UserRole.PRINCIPAL.dbValue) continue
                val sRef = schoolDoc(schoolId)

                if (role == UserRole.TEACHER.dbValue) {
                    // Step 1: While members/{userId} still exists, isSchoolTeacher passes for self-removal & assignments
                    try {
                        sRef.update(
                            mapOf(
                                "memberUids" to FieldValue.arrayRemove(userId),
                                "staffUids" to FieldValue.arrayRemove(userId),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } catch (_: Exception) {
                    }
                    try {
                        sRef.collection("teacher_assignments")
                            .whereEqualTo("teacherProfileId", userId)
                            .get().await().documents
                            .forEach { it.reference.delete().await() }
                    } catch (_: Exception) {
                    }
                    try {
                        val tRef = sRef.collection("teachers").document(userId)
                        if (tRef.get().await().exists()) tRef.delete().await()
                    } catch (_: Exception) {
                    }
                    try {
                        val reqRef = sRef.collection("teacher_requests").document(userId)
                        if (reqRef.get().await().exists()) reqRef.delete().await()
                    } catch (_: Exception) {
                    }
                    memDoc.reference.delete().await()
                } else if (role == UserRole.STUDENT.dbValue) {
                    try {
                        val sReqRef = sRef.collection("student_requests").document(userId)
                        if (sReqRef.get().await().exists()) sReqRef.delete().await()
                    } catch (_: Exception) {
                    }
                    memDoc.reference.delete().await()
                }
            }

            try {
                db.collectionGroup("student_requests")
                    .whereEqualTo("userId", userId)
                    .get().await().documents
                    .forEach { it.reference.delete().await() }
            } catch (_: Exception) {
            }
            try {
                db.collectionGroup("teacher_requests")
                    .whereEqualTo("userId", userId)
                    .get().await().documents
                    .forEach { it.reference.delete().await() }
            } catch (_: Exception) {
            }

            val userRef = db.collection("users").document(userId)
            if (userRef.get().await().exists()) {
                userRef.delete().await()
            }

            try {
                Firebase.auth.currentUser?.delete()?.await()
            } catch (_: Exception) {
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId")
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to delete your account."), e))
        }
    }
}
