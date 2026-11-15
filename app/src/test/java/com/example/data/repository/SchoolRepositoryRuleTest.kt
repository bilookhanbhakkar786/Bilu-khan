package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AttendanceStatus
import com.example.data.model.TeacherPermissions
import com.example.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SchoolRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}@schooltest.edu"

    @Test
    fun createSchool_validPrincipal_createsOnceAndRejectsSecondSchool() = runBlocking {
        val principalUid = signInTestUser(uniqueEmail("principal"))
        val repo = SchoolRepository(firestore)

        val firstRes = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Beacon Light Academy",
                schoolType = "Private",
                address = "123 Campus Road",
                city = "Lahore",
                contactNumber = "+923001234567",
                email = "info@beacon.edu",
                description = "Model School",
                establishedYear = 2008,
                website = "https://beacon.edu",
                logoUrl = null,
                bannerUrl = null
            )
        }
        assertTrue("Expected first school creation to succeed: ${firstRes.exceptionOrNull()}", firstRes.isSuccess)
        val created = firstRes.getOrThrow()
        assertTrue(created.id.isNotBlank())
        assertTrue(created.schoolCode.startsWith("BL-"))

        // Second school creation by same principal MUST fail via principal_locks
        val secondRes = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Duplicate School",
                schoolType = "Private",
                address = "456 Road",
                city = "Lahore",
                contactNumber = "+923001234568",
                email = "dup@beacon.edu",
                description = "Blocked",
                establishedYear = 2020,
                website = null,
                logoUrl = null,
                bannerUrl = null
            )
        }
        assertTrue("Expected second school creation to fail", secondRes.isFailure)
        val fsErr = secondRes.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun principalWorkflow_sessionsClassesStudentsTeacherInviteAndStudentApproval_succeedsAndRespectsIsolation() = runBlocking {
        val principalEmail = uniqueEmail("principal")
        val principalUid = signInTestUser(principalEmail)
        val schoolRepo = SchoolRepository(firestore)
        val authRepo = AuthRepository(auth, firestore)

        val school = withTimeout(TIMEOUT_MS) {
            schoolRepo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Horizon Grammar",
                schoolType = "Private",
                address = "Main Blvd",
                city = "Islamabad",
                contactNumber = "+923009998888",
                email = principalEmail,
                description = "Campus Hub",
                establishedYear = 2012,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        // Academic Session, Class, Section, Student creation
        val session = withTimeout(TIMEOUT_MS) {
            schoolRepo.createAcademicSession(school.id, "2025-2026", "2025-04-01", "2026-03-31", true).getOrThrow()
        }
        val section = withTimeout(TIMEOUT_MS) {
            schoolRepo.createClassWithSection(school.id, session.id, "8th", "A", null, "Section A", null).getOrThrow()
        }
        val student = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id,
                existingId = null,
                sessionId = session.id,
                classId = section.classId,
                sectionId = section.id,
                rollNumber = "101",
                fullName = "Zainab Tariq",
                fatherName = "Tariq Mahmood",
                dateOfBirth = "2012-05-10",
                gender = "Female",
                parentName = "Tariq Mahmood",
                parentContact = "+923001112233",
                address = "House 12",
                admissionDate = "2025-04-02",
                email = null,
                avatarUrl = null
            ).getOrThrow()
        }

        // Principal creates Attendance, Test, and Announcement
        val attSession = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveAttendanceSessionAndRecords(
                existingSessionId = null,
                schoolId = school.id,
                sessionId = session.id,
                classId = section.classId,
                sectionId = section.id,
                attendanceDate = "2025-09-01",
                subject = "Mathematics",
                sessionSlot = "Morning",
                teacherId = principalUid,
                teacherName = "Principal",
                statusByStudentId = mapOf(student.id to AttendanceStatus.PRESENT),
                existingRecords = emptyList(),
                editorName = "Principal"
            ).getOrThrow()
        }
        assertTrue(attSession.id.isNotBlank())

        val post = withTimeout(TIMEOUT_MS) {
            schoolRepo.createAnnouncement(
                schoolId = school.id,
                sessionId = null,
                classId = null,
                sectionId = null,
                scope = "school",
                category = "Announcement",
                title = "Welcome Back",
                body = "New term begins Monday.",
                imageUrl = null,
                allowComments = true,
                publisherId = principalUid,
                publisherName = "Principal"
            ).getOrThrow()
        }
        assertEquals(0, post.likesCount)

        // Invite Teacher
        val teacherEmail = uniqueEmail("teacher")
        val invite = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacher(
                schoolId = school.id,
                invitedBy = principalUid,
                fullName = "Mr. Bilal",
                email = teacherEmail,
                phone = "03007778888",
                permissions = TeacherPermissions(markAttendance = true, viewStudents = true)
            ).getOrThrow()
        }
        assertEquals("pending", invite.status)

        // Teacher signs in and accepts invite
        val teacherUid = signInTestUser(teacherEmail)
        val teacherProfile = withTimeout(TIMEOUT_MS) {
            schoolRepo.acceptTeacherInvitation(invite.inviteCode, teacherUid, teacherEmail, "Mr. Bilal").getOrThrow()
        }
        assertEquals(teacherUid, teacherProfile.userId)

        val teacherCtx = withTimeout(TIMEOUT_MS) { authRepo.ensureCurrentAccountContext(UserRole.TEACHER).getOrThrow() }
        assertEquals(UserRole.TEACHER, teacherCtx?.activeRole)
        assertEquals(school.id, teacherCtx?.activeSchool?.id)

        val teacherBundle = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, teacherUid).getOrThrow() }
        assertTrue(teacherBundle.sessions.any { it.id == session.id })
        assertTrue(teacherBundle.sections.any { it.id == section.id })

        // Student searches public school directory and submits connection request
        val studentEmail = uniqueEmail("student")
        val studentUid = signInTestUser(studentEmail)
        val foundSchools = withTimeout(TIMEOUT_MS) { schoolRepo.searchSchools(school.schoolCode).getOrThrow() }
        assertTrue(foundSchools.any { it.id == school.id })

        val req = withTimeout(TIMEOUT_MS) {
            schoolRepo.requestStudentConnection(
                schoolId = school.id,
                schoolOwnerId = school.ownerId,
                schoolName = school.name,
                userId = studentUid,
                rollNumber = "101",
                fullName = "Zainab Tariq",
                fatherName = "Tariq Mahmood",
                sessionName = session.name,
                className = "8th",
                sectionName = "A"
            ).getOrThrow()
        }
        assertEquals("pending", req.status)

        // Principal approves connection request
        signInTestUser(principalEmail)
        val approvedReq = withTimeout(TIMEOUT_MS) {
            schoolRepo.approveStudentConnection(school.id, req.id, student.id).getOrThrow()
        }
        assertEquals("approved", approvedReq.status)

        // Student loads workspace and can read their attendance & school announcements, and like post
        signInTestUser(studentEmail)
        val studentBundle = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid).getOrThrow() }
        assertTrue(studentBundle.announcements.any { it.id == post.id })
        assertTrue(studentBundle.attendanceRecords.any { it.studentId == student.id })

        val newLikeCount = withTimeout(TIMEOUT_MS) { schoolRepo.toggleLike(school.id, post.id, studentUid).getOrThrow() }
        assertEquals(1, newLikeCount)

        // Outsider CANNOT read school or load workspace
        val outsiderUid = signInTestUser(uniqueEmail("outsider"))
        val outsiderBundleRes = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, outsiderUid) }
        assertTrue("Outsider must not load school workspace", outsiderBundleRes.isFailure)
        val outsiderFsErr = outsiderBundleRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, outsiderFsErr?.code)
    }

    @Test
    fun unauthenticatedCaller_queriesFailWithPermissionDenied() = runBlocking {
        auth.signOut()
        val schoolRepo = SchoolRepository(firestore)
        val authRepo = AuthRepository(auth, firestore)

        val searchRes = withTimeout(TIMEOUT_MS) { schoolRepo.searchSchools("") }
        assertTrue(searchRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            searchRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val ctxRes = withTimeout(TIMEOUT_MS) { authRepo.resolveAccountContext("fake_uid") }
        assertTrue(ctxRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            ctxRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
