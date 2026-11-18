package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SUPER_ADMIN_EMAIL
import com.example.data.model.TeacherPermissions
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AdminRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}@schooltest.edu"

    @Test
    fun superAdmin_loadsPlatformTrafficSchoolsAndTogglesVisibility_nonAdminDenied() = runBlocking {
        AdminRepository.resetSessionCacheForTests()
        val schoolRepo = SchoolRepository(firestore)
        val authRepo = AuthRepository(auth, firestore)
        val adminRepo = AdminRepository(auth, firestore)

        // 1. Principal signs in, records a visit, creates school + teacher + student
        val principalEmail = uniqueEmail("adm_princ")
        val principalUid = signInTestUser(principalEmail, markEmailVerified = true)
        authRepo.ensureCurrentAccountContext(UserRole.PRINCIPAL).getOrThrow()

        val school = withTimeout(TIMEOUT_MS) {
            schoolRepo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Bhakkar Model School",
                schoolType = "Private",
                address = "Main Rd",
                city = "Bhakkar",
                contactNumber = "+923001234567",
                email = principalEmail,
                description = "Platform Test School",
                establishedYear = 2020,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        val session = withTimeout(TIMEOUT_MS) {
            schoolRepo.createAcademicSession(school.id, "2026-2027", "01-04-2026", "31-03-2027", true).getOrThrow()
        }
        val section = withTimeout(TIMEOUT_MS) {
            schoolRepo.createClassWithSection(school.id, session.id, "10th", "A", null, "Science", null).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "1",
                fullName = "Zain Ali", fatherName = "Tariq Ali", dateOfBirth = null,
                gender = "Male", parentName = "Tariq Ali", parentContact = "0300",
                address = "Bhakkar", admissionDate = "01-04-2026", email = null, avatarUrl = null
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacher(
                schoolId = school.id,
                invitedBy = principalUid,
                fullName = "Teacher One",
                email = uniqueEmail("adm_t"),
                phone = "0301",
                permissions = TeacherPermissions()
            ).getOrThrow()
        }

        val principalUser = UserProfile(
            id = principalUid,
            userId = principalUid,
            fullName = "Principal Bhakkar",
            email = principalEmail,
            accountIntent = UserRole.PRINCIPAL.dbValue
        )
        val visitRes = withTimeout(TIMEOUT_MS) {
            adminRepo.recordAppVisit(principalUser, school, UserRole.PRINCIPAL, force = true)
        }
        assertTrue("Normal user visit recording should succeed: ${visitRes.exceptionOrNull()}", visitRes.isSuccess)

        // 2. Non-admin principal CANNOT load AdminDashboard
        val nonAdminDashRes = withTimeout(TIMEOUT_MS) { adminRepo.loadAdminDashboard() }
        assertTrue("Non-admin must be denied", nonAdminDashRes.isFailure)

        // 3. Verified bilookhanbhakkar@gmail.com signs in, records a visit, and loads full dashboard
        val adminUid = signInTestUser(SUPER_ADMIN_EMAIL, markEmailVerified = true)
        authRepo.ensureCurrentAccountContext(UserRole.PRINCIPAL).getOrThrow()
        withTimeout(TIMEOUT_MS) {
            adminRepo.recordAppVisit(
                UserProfile(
                    id = adminUid,
                    userId = adminUid,
                    fullName = "Biloo Admin",
                    email = SUPER_ADMIN_EMAIL,
                    accountIntent = UserRole.PRINCIPAL.dbValue
                ),
                null,
                null,
                force = true
            ).getOrThrow()
        }

        val dash = withTimeout(TIMEOUT_MS) { adminRepo.loadAdminDashboard().getOrThrow() }
        assertTrue("Total visits should be >= 2", dash.totalVisits >= 2)
        assertTrue("Unique visitors should be >= 2", dash.uniqueVisitors >= 2)
        assertTrue("Today's visits should be >= 2", dash.todayVisits >= 2)
        assertEquals(7, dash.dailyTraffic.size)
        assertTrue("Should list Bhakkar Model School", dash.schoolSummaries.any { it.school.id == school.id && it.studentsCount >= 1 })
        assertTrue("Total students should be >= 1", dash.totalStudents >= 1)
        assertTrue("Total users should be >= 2", dash.totalUsers >= 2)

        // 4. Super Admin can hide/unhide a school directory entry
        withTimeout(TIMEOUT_MS) {
            adminRepo.setSchoolDirectoryVisibility(school.schoolCode, false).getOrThrow()
        }
        val dashAfterHide = withTimeout(TIMEOUT_MS) { adminRepo.loadAdminDashboard().getOrThrow() }
        val hiddenEntry = dashAfterHide.schoolSummaries.first { it.school.schoolCode == school.schoolCode }
        assertFalse("School directory listing should now be hidden", hiddenEntry.isPublicListed)
    }

    private companion object {
        const val TIMEOUT_MS = 12000L
    }
}
