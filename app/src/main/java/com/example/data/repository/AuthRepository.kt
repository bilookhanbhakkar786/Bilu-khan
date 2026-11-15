package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.School
import com.example.data.model.SchoolMembership
import com.example.data.model.StudentAccountRequest
import com.example.data.model.TeacherInvitation
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
    val studentRequests: List<StudentAccountRequest>
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

            // Query schools where user is listed in memberUids
            val schoolsQuery = db.collection("schools")
                .whereArrayContains("memberUids", userId)
            val candidateSchools = try {
                schoolsQuery.get().await().toObjects(
                    School::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "schools")
                emptyList()
            }

            val memberships = mutableListOf<SchoolMembership>()
            for (school in candidateSchools) {
                val memRef = db.collection("schools").document(school.id)
                    .collection("members").document(userId)
                try {
                    val memSnap = memRef.get().await()
                    val m = memSnap.toObject(
                        SchoolMembership::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (m != null && m.status == "active") {
                        memberships.add(m)
                    }
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.GET, memRef.path)
                }
            }

            // Priority: Principal > Teacher > Student
            val primaryMembership = memberships.firstOrNull { it.role == UserRole.PRINCIPAL.dbValue }
                ?: memberships.firstOrNull { it.role == UserRole.TEACHER.dbValue }
                ?: memberships.firstOrNull()

            val activeSchool = primaryMembership?.let { pm ->
                candidateSchools.firstOrNull { it.id == pm.schoolId }
            }

            // Query pending teacher invitations only when current token has a verified email
            val emailVerified = firebaseUser?.isEmailVerified == true
            val verifiedEmail = firebaseUser?.email?.trim()?.lowercase().orEmpty()
            val invites = if (emailVerified && verifiedEmail.isNotBlank()) {
                try {
                    db.collection("teacher_invitations")
                        .whereEqualTo("emailLower", verifiedEmail)
                        .whereEqualTo("status", "pending")
                        .get()
                        .await()
                        .toObjects(
                            TeacherInvitation::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "teacher_invitations")
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Query user's student connection requests across schools via CollectionGroup
            val studentReqs = try {
                db.collectionGroup("student_requests")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .toObjects(
                        StudentAccountRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "collectionGroup/student_requests")
                emptyList()
            }

            Result.success(
                ResolvedAccountContext(
                    user = userProfile,
                    memberships = memberships,
                    activeSchool = activeSchool,
                    activeRole = primaryMembership?.let { UserRole.fromDb(it.role) },
                    pendingTeacherInvites = invites,
                    studentRequests = studentReqs
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
