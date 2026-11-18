package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.PrincipalInvitation
import com.example.data.model.School
import com.example.data.model.SchoolMembership
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherInvitation
import com.example.data.model.TeacherJoinRequest
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.OperationType
import com.example.data.remote.friendlyFirestoreMessage
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ResolvedAccountContext(
    val user: UserProfile,
    val memberships: List<SchoolMembership>,
    val activeSchool: School?,
    val activeRole: UserRole?,
    val pendingTeacherInvites: List<TeacherInvitation>,
    val pendingPrincipalInvites: List<PrincipalInvitation> = emptyList(),
    val teacherRequests: List<TeacherJoinRequest> = emptyList(),
    val studentRequests: List<StudentAccountRequest> = emptyList(),
    val hiddenActiveSchoolBlocked: Boolean = false,
    val pinnedSchools: List<School> = emptyList()
)

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        auth = Firebase.auth,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        preferredRoleIntent: UserRole
    ): Result<ResolvedAccountContext> = withContext(Dispatchers.IO) {
        try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(cred).await()
            val firebaseUser = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Google authentication failed."))

            ensureUserProfileExists(
                uid = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                displayName = firebaseUser.displayName.orEmpty(),
                photoUrl = firebaseUser.photoUrl?.toString(),
                defaultIntent = preferredRoleIntent
            )
            resolveAccountContext(firebaseUser.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Google Sign-In failed."), e))
        }
    }

    suspend fun reauthenticateWithGoogleIdToken(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("No signed-in user."))
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            firebaseUser.reauthenticate(cred).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Google re-authentication failed."), e))
        }
    }

    fun currentLastSignInTimestamp(): Long = auth.currentUser?.metadata?.lastSignInTimestamp ?: 0L

    suspend fun ensureCurrentAccountContext(defaultIntent: UserRole = UserRole.PRINCIPAL): Result<ResolvedAccountContext?> =
        withContext(Dispatchers.IO) {
            val firebaseUser = auth.currentUser ?: return@withContext Result.success(null)
            try {
                ensureUserProfileExists(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email.orEmpty(),
                    displayName = firebaseUser.displayName.orEmpty(),
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    defaultIntent = defaultIntent
                )
                val resolved = resolveAccountContext(firebaseUser.uid)
                resolved.map { it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun ensureUserProfileExists(
        uid: String,
        email: String,
        displayName: String,
        photoUrl: String?,
        defaultIntent: UserRole
    ) {
        val userRef = db.collection("users").document(uid)
        try {
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank {
                    email.substringBefore('@').ifBlank { "School Member" }
                }.take(120)
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "fullName" to safeName,
                    "email" to email.trim().take(160),
                    "phone" to "",
                    "accountIntent" to defaultIntent.dbValue,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val safePhoto = photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 400000 }
                if (safePhoto != null) {
                    payload["avatarUrl"] = safePhoto
                }
                userRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    suspend fun resolveAccountContext(userId: String): Result<ResolvedAccountContext> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        try {
            val userSnap = userRef.get().await()
            val firebaseUser = auth.currentUser
            val userProfile = userSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(
                id = userId,
                userId = userId,
                fullName = firebaseUser?.displayName?.ifBlank { null }
                    ?: firebaseUser?.email?.substringBefore('@')
                    ?: "School User",
                email = firebaseUser?.email.orEmpty(),
                accountIntent = UserRole.PRINCIPAL.dbValue
            )

            // Query schools where user is listed in memberUids (Principal / Teacher)
            val schoolsQuery = db.collection("schools")
                .whereArrayContains("memberUids", userId)
            val candidateSchoolsMap = try {
                schoolsQuery.get().await().toObjects(
                    School::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).associateBy { it.id }.toMutableMap()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "schools")
                mutableMapOf()
            }

            // Also query memberships via collectionGroup so instant public-school Student entries are included
            val groupMemberships = try {
                db.collectionGroup("members")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .toObjects(
                        SchoolMembership::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    .filter { it.status == "active" }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "collectionGroup/members")
                emptyList()
            }

            val mergedMembershipsBySchool = LinkedHashMap<String, SchoolMembership>()
            groupMemberships.forEach { m ->
                if (m.schoolId.isNotBlank()) {
                    mergedMembershipsBySchool[m.schoolId] = m
                }
            }
            for (school in candidateSchoolsMap.values) {
                if (!mergedMembershipsBySchool.containsKey(school.id)) {
                    val memRef = db.collection("schools").document(school.id)
                        .collection("members").document(userId)
                    try {
                        val memSnap = memRef.get().await()
                        val m = memSnap.toObject(
                            SchoolMembership::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (m != null && m.status == "active") {
                            mergedMembershipsBySchool[school.id] = m
                        }
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.GET, memRef.path)
                    }
                }
            }

            val memberships = mutableListOf<SchoolMembership>()
            var hiddenActiveSchoolBlocked = false
            for (m in mergedMembershipsBySchool.values) {
                val role = UserRole.fromDb(m.role)
                val cachedSchool = candidateSchoolsMap[m.schoolId]
                val resolvedSchool = cachedSchool ?: try {
                    db.collection("schools").document(m.schoolId).get().await().toObject(
                        School::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.GET, "schools/${m.schoolId}")
                    null
                }
                if (resolvedSchool != null) {
                    candidateSchoolsMap[resolvedSchool.id] = resolvedSchool
                }

                val isHiddenForStudent = role == UserRole.STUDENT &&
                    (resolvedSchool == null || !resolvedSchool.isPublic)
                if (isHiddenForStudent) {
                    if (userProfile.activeSchoolId == m.schoolId) {
                        hiddenActiveSchoolBlocked = true
                    }
                } else if (resolvedSchool != null) {
                    memberships.add(m)
                }
            }

            val isLockedStudent = userProfile.accountIntent == UserRole.STUDENT.dbValue

            // Load pinned schools (up to 3) keeping user order & filtering out hidden schools
            val pinnedSchools = mutableListOf<School>()
            for (pid in userProfile.pinnedSchoolIds.distinct().take(3)) {
                val candidate = candidateSchoolsMap[pid] ?: try {
                    db.collection("schools").document(pid).get().await().toObject(
                        School::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.GET, "schools/$pid")
                    null
                }
                if (candidate != null && candidate.isPublic) {
                    candidateSchoolsMap[candidate.id] = candidate
                    pinnedSchools.add(candidate)
                }
            }

            // Priority: Principal > Teacher > Student (locked Students stay Student only)
            val primaryMembership: SchoolMembership?
            var effectiveUserProfile = userProfile
            if (isLockedStudent) {
                val studentMemberships = memberships.filter { it.role == UserRole.STUDENT.dbValue }
                val targetSchoolId = when {
                    pinnedSchools.isNotEmpty() -> {
                        userProfile.activeSchoolId?.takeIf { activeId ->
                            pinnedSchools.any { it.id == activeId }
                        } ?: pinnedSchools.first().id
                    }
                    !userProfile.activeSchoolId.isNullOrBlank() -> userProfile.activeSchoolId
                    else -> null
                }
                val targetSchool = targetSchoolId?.let { candidateSchoolsMap[it] }?.takeIf { it.isPublic }
                var chosenMembership = targetSchool?.let { ts ->
                    studentMemberships.firstOrNull { it.schoolId == ts.id }
                }
                if (targetSchool != null && chosenMembership == null) {
                    val memRef = db.collection("schools").document(targetSchool.id)
                        .collection("members").document(userId)
                    try {
                        memRef.set(
                            mapOf(
                                "schoolId" to targetSchool.id,
                                "schoolOwnerId" to targetSchool.ownerId,
                                "userId" to userId,
                                "role" to "student",
                                "status" to "active",
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        chosenMembership = SchoolMembership(
                            id = userId,
                            schoolId = targetSchool.id,
                            schoolOwnerId = targetSchool.ownerId,
                            userId = userId,
                            role = UserRole.STUDENT.dbValue,
                            status = "active"
                        )
                        memberships.add(chosenMembership)
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.WRITE, memRef.path)
                    }
                }
                if (targetSchool != null && chosenMembership != null && userProfile.activeSchoolId != targetSchool.id) {
                    try {
                        userRef.update(
                            mapOf(
                                "activeSchoolId" to targetSchool.id,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        effectiveUserProfile = userProfile.copy(activeSchoolId = targetSchool.id)
                    } catch (_: Exception) {
                    }
                }
                primaryMembership = chosenMembership
            } else {
                primaryMembership = memberships.firstOrNull { it.role == UserRole.PRINCIPAL.dbValue }
                    ?: memberships.firstOrNull { it.role == UserRole.TEACHER.dbValue }
                    ?: userProfile.activeSchoolId?.takeIf { it.isNotBlank() }?.let { activeId ->
                        memberships.firstOrNull { it.role == UserRole.STUDENT.dbValue && it.schoolId == activeId }
                    }
            }

            val activeSchool = primaryMembership?.let { pm ->
                candidateSchoolsMap[pm.schoolId]
            }

            // Query pending teacher & principal invitations only when non-student and current token has a verified email
            val emailVerified = firebaseUser?.isEmailVerified == true
            val verifiedEmail = firebaseUser?.email?.trim()?.lowercase().orEmpty()
            val invites = if (!isLockedStudent && emailVerified && verifiedEmail.isNotBlank()) {
                try {
                    db.collection("teacher_invitations")
                        .whereEqualTo("emailLower", verifiedEmail)
                        .get()
                        .await()
                        .toObjects(
                            TeacherInvitation::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        .filter { it.status == "pending" }
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "teacher_invitations")
                    emptyList()
                }
            } else {
                emptyList()
            }

            val principalInvites = if (!isLockedStudent && emailVerified && verifiedEmail.isNotBlank()) {
                try {
                    db.collection("principal_invitations")
                        .whereEqualTo("emailLower", verifiedEmail)
                        .get()
                        .await()
                        .toObjects(
                            PrincipalInvitation::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        .filter { it.status == "pending" }
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "principal_invitations")
                    emptyList()
                }
            } else {
                emptyList()
            }

            Result.success(
                ResolvedAccountContext(
                    user = effectiveUserProfile,
                    memberships = memberships,
                    activeSchool = activeSchool,
                    activeRole = if (isLockedStudent && activeSchool != null) UserRole.STUDENT else primaryMembership?.let { UserRole.fromDb(it.role) },
                    pendingTeacherInvites = invites,
                    pendingPrincipalInvites = principalInvites,
                    teacherRequests = emptyList(),
                    studentRequests = emptyList(),
                    hiddenActiveSchoolBlocked = hiddenActiveSchoolBlocked,
                    pinnedSchools = pinnedSchools
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Failed to load account context."), e))
        }
    }

    suspend fun updateAccountIntent(userId: String, role: UserRole): Result<Unit> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        try {
            val snap = userRef.get().await()
            val existingIntent = snap.getString("accountIntent")
            if (existingIntent == UserRole.STUDENT.dbValue && role != UserRole.STUDENT) {
                return@withContext Result.failure(
                    IllegalStateException("Student accounts stay Student permanently and cannot switch to Teacher or Principal.")
                )
            }
            userRef.update(
                mapOf(
                    "accountIntent" to role.dbValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Could not switch role."), e))
        }
    }

    suspend fun updateMyProfile(
        userId: String,
        fullName: String,
        phone: String,
        avatarUrl: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(userId)
        try {
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim().take(120),
                "phone" to phone.trim().take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarUrl.isNullOrBlank()) {
                updates["avatarUrl"] = avatarUrl
            }
            userRef.update(updates).await()
            val updatedSnap = userRef.get().await()
            val updated = updatedSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.failure(IllegalStateException("Profile update returned empty."))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(IllegalStateException(friendlyFirestoreMessage(e, "Profile update failed."), e))
        }
    }

    suspend fun logout(clearCredentialState: suspend () -> Unit = {}) {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                clearCredentialState()
            } catch (_: Exception) {
            }
            try {
                auth.signOut()
            } catch (_: Exception) {
            }
        }
    }
}
