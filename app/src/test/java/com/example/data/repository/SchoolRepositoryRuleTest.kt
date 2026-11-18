package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AttendanceStatus
import com.example.data.model.TeacherInviteLinks
import com.example.data.model.TeacherPermissions
import com.example.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Academic Session, Class, Section, Student creation & roll-number auto-shifting
        val session = withTimeout(TIMEOUT_MS) {
            schoolRepo.createAcademicSession(school.id, "2025-2026", "01-04-2025", "31-03-2026", true).getOrThrow()
        }
        val section = withTimeout(TIMEOUT_MS) {
            schoolRepo.createClassWithSection(school.id, session.id, "8th", "A", null, "Section A", null).getOrThrow()
        }
        val stu1 = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "1",
                fullName = "Zainab Tariq", fatherName = "Tariq Mahmood", dateOfBirth = "10-05-2012",
                gender = "Female", parentName = "Tariq Mahmood", parentContact = "+923001112233",
                address = "House 12", admissionDate = "02-04-2025", email = null, avatarUrl = null
            ).getOrThrow()
        }
        val stu2Orig = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "2",
                fullName = "Hamza Ali", fatherName = "Ali Raza", dateOfBirth = null,
                gender = "Male", parentName = "Ali Raza", parentContact = "+923001112244",
                address = "House 14", admissionDate = "02-04-2025", email = null, avatarUrl = null
            ).getOrThrow()
        }
        // Insert new student at roll "2" -> Zainab remains "1", new student gets "2", Hamza shifts to "3"
        val stuInserted = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "2",
                fullName = "Ayesha Khan", fatherName = "Imran Khan", dateOfBirth = null,
                gender = "Female", parentName = "Imran Khan", parentContact = "+923001112255",
                address = "House 15", admissionDate = "03-04-2025", email = null, avatarUrl = null
            ).getOrThrow()
        }
        assertEquals("2", stuInserted.rollNumber)

        // Principal creates Attendance, Test, and Announcement
        val attSession = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveAttendanceSessionAndRecords(
                existingSessionId = null,
                schoolId = school.id,
                sessionId = session.id,
                classId = section.classId,
                sectionId = section.id,
                attendanceDate = "01-09-2025",
                subject = "Mathematics",
                sessionSlot = "Morning",
                teacherId = principalUid,
                teacherName = "Principal",
                statusByStudentId = mapOf(stu1.id to AttendanceStatus.PRESENT, stu2Orig.id to AttendanceStatus.ABSENT),
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

        // Teacher #1 joins via Invite Code
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
        // Verify shifted roll number persisted & sorted
        assertEquals("3", teacherBundle.students.first { it.id == stu2Orig.id }.rollNumber)

        // Teacher #2 joins via Open Shareable WhatsApp Invite Link (blank email & name, extracted from https link)
        signInTestUser(principalEmail)
        val openInvite = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacher(
                schoolId = school.id,
                invitedBy = principalUid,
                fullName = "",
                email = "",
                phone = "",
                permissions = TeacherPermissions(markAttendance = true, viewStudents = true)
            ).getOrThrow()
        }
        val whatsappShareUrl = TeacherInviteLinks.buildWebInviteLink(openInvite.inviteCode)
        val extractedCode = TeacherInviteLinks.extractInviteCode(whatsappShareUrl)
        assertEquals(openInvite.inviteCode, extractedCode)

        val linkTeacherEmail = uniqueEmail("link_teacher")
        val linkTeacherUid = signInTestUser(linkTeacherEmail)
        val joinedViaLinkTeacher = withTimeout(TIMEOUT_MS) {
            schoolRepo.acceptTeacherInvitation(
                inviteCode = whatsappShareUrl,
                userId = linkTeacherUid,
                fallbackEmail = linkTeacherEmail,
                fallbackName = "Ms. Sana"
            ).getOrThrow()
        }
        assertEquals(linkTeacherUid, joinedViaLinkTeacher.userId)
        assertEquals("Ms. Sana", joinedViaLinkTeacher.fullName)

        val teacherCreatedSec = withTimeout(TIMEOUT_MS) {
            schoolRepo.createClassWithSection(school.id, session.id, "9th", "B", linkTeacherUid, "Created by teacher", null).getOrThrow()
        }
        val updatedSecWithLecture = withTimeout(TIMEOUT_MS) {
            schoolRepo.addSectionLectureSlot(school.id, teacherCreatedSec.id, "Lecture 7").getOrThrow()
        }
        assertTrue(updatedSecWithLecture.customLectures.contains("Lecture 7"))

        // Student searches public school directory and enters directly WITHOUT any approval request
        val studentEmail = uniqueEmail("student")
        val studentUid = signInTestUser(studentEmail)
        val foundSchools = withTimeout(TIMEOUT_MS) { schoolRepo.searchSchools(school.schoolCode).getOrThrow() }
        assertTrue(foundSchools.any { it.id == school.id })

        withTimeout(TIMEOUT_MS) {
            schoolRepo.enterSchoolAsStudent(school.id, studentUid).getOrThrow()
        }

        // Principal adds teacher assignment, timetable slot, and homework to verify school-wide student read access
        signInTestUser(principalEmail)
        withTimeout(TIMEOUT_MS) {
            schoolRepo.assignTeacherToSection(school.id, session.id, joinedViaLinkTeacher.id, teacherCreatedSec.id, "Science").getOrThrow()
            schoolRepo.createTimetableSlot(school.id, session.id, teacherCreatedSec.id, "Monday", "Science", "09:00", "09:45", "Ms. Sana", "202").getOrThrow()
            schoolRepo.createHomework(school.id, session.id, teacherCreatedSec.id, "Science", "Lab Worksheet", "Complete diagram on page 14", "22-11-2026", null, linkTeacherUid, "Ms. Sana").getOrThrow()
        }

        // Student loads school-wide read-only workspace immediately without approval & can like
        signInTestUser(studentEmail)
        val studentBundle = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid).getOrThrow() }
        assertTrue(studentBundle.announcements.any { it.id == post.id })
        assertTrue(studentBundle.students.any { it.id == stu1.id })
        assertTrue(studentBundle.students.any { it.id == stu2Orig.id })
        assertTrue(studentBundle.attendanceSessions.any { it.id == attSession.id })
        assertTrue(studentBundle.attendanceRecords.any { it.studentId == stu1.id })
        assertTrue(studentBundle.teacherAssignments.any { it.subject == "Science" && it.sectionId == teacherCreatedSec.id })
        assertTrue(studentBundle.timetables.any { it.subject == "Science" && it.sectionId == teacherCreatedSec.id })
        assertTrue(studentBundle.homework.any { it.title == "Lab Worksheet" && it.sectionId == teacherCreatedSec.id })

        // Student MUST be denied any academic writes (homework, attendance, tests)
        val studentHwWrite = withTimeout(TIMEOUT_MS) {
            schoolRepo.createHomework(school.id, session.id, teacherCreatedSec.id, "Science", "Fake HW", "Should fail", "22-11-2026", null, studentUid, "Student")
        }
        assertTrue("Student homework write must fail", studentHwWrite.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, studentHwWrite.exceptionOrNull()?.unwrapFirestoreException()?.code)

        val studentAttWrite = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveAttendanceSessionAndRecords(
                existingSessionId = null,
                schoolId = school.id,
                sessionId = session.id,
                classId = section.classId,
                sectionId = section.id,
                attendanceDate = "02-09-2025",
                subject = "Math",
                sessionSlot = "Morning",
                teacherId = studentUid,
                teacherName = "Student",
                statusByStudentId = mapOf(stu1.id to AttendanceStatus.PRESENT),
                existingRecords = emptyList(),
                editorName = "Student"
            )
        }
        assertTrue("Student attendance write must fail", studentAttWrite.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, studentAttWrite.exceptionOrNull()?.unwrapFirestoreException()?.code)

        val newLikeCount = withTimeout(TIMEOUT_MS) { schoolRepo.toggleLike(school.id, post.id, studentUid).getOrThrow() }
        assertEquals(1, newLikeCount)

        // Outsider who has NOT opened/joined the school cannot read workspace or subcollections before entering
        val outsiderEmail = uniqueEmail("outsider")
        val outsiderUid = signInTestUser(outsiderEmail)
        val outsiderBundleRes = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, outsiderUid) }
        assertTrue("Outsider must not load school workspace without entering", outsiderBundleRes.isFailure)
        val outsiderDirectRead = withTimeout(TIMEOUT_MS) {
            runCatching {
                firestore.collection("schools").document(school.id).collection("students").get().await()
            }
        }
        assertTrue("Outsider direct subcollection read must fail", outsiderDirectRead.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            outsiderDirectRead.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Principal hides school -> Students & public search immediately lose access while Principal & Teachers keep access!
        signInTestUser(principalEmail)
        val hiddenSchool = withTimeout(TIMEOUT_MS) { schoolRepo.setSchoolVisibility(school.id, false).getOrThrow() }
        assertFalse(hiddenSchool.isPublic)

        val principalStillLoads = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, principalUid) }
        assertTrue("Principal must keep access when school is hidden", principalStillLoads.isSuccess)

        signInTestUser(linkTeacherEmail)
        val teacherStillLoads = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, linkTeacherUid) }
        assertTrue("Teacher must keep access when school is hidden", teacherStillLoads.isSuccess)

        signInTestUser(studentEmail)
        val hiddenSearch = withTimeout(TIMEOUT_MS) { schoolRepo.searchSchools(school.schoolCode).getOrThrow() }
        assertFalse("Hidden school must not appear in student search", hiddenSearch.any { it.id == school.id })
        val hiddenStudentWorkspace = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid) }
        assertTrue("Student must lose workspace access when school is hidden", hiddenStudentWorkspace.isFailure)
        val hiddenStudentReenter = withTimeout(TIMEOUT_MS) { schoolRepo.enterSchoolAsStudent(school.id, studentUid) }
        assertTrue("Student must not enter hidden school", hiddenStudentReenter.isFailure)

        // Principal unhides school -> Student regains public access immediately
        signInTestUser(principalEmail)
        val unhiddenSchool = withTimeout(TIMEOUT_MS) { schoolRepo.setSchoolVisibility(school.id, true).getOrThrow() }
        assertTrue(unhiddenSchool.isPublic)

        signInTestUser(studentEmail)
        val publicSearchAgain = withTimeout(TIMEOUT_MS) { schoolRepo.searchSchools(school.schoolCode).getOrThrow() }
        assertTrue(publicSearchAgain.any { it.id == school.id })
        val unhiddenStudentWorkspace = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid) }
        assertTrue("Student must regain access when school is unhidden", unhiddenStudentWorkspace.isSuccess)
    }

    @Test
    fun studentViewsAllTestStatuses_principalRemovesAcceptedTeacherAndStudent_handoverAndDeletePrincipal() = runBlocking {
        val principalEmail = uniqueEmail("principal_a")
        val principalUid = signInTestUser(principalEmail)
        val schoolRepo = SchoolRepository(firestore)
        val authRepo = AuthRepository(auth, firestore)

        val school = withTimeout(TIMEOUT_MS) {
            schoolRepo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Transfer Grammar",
                schoolType = "Private",
                address = "Canal Rd",
                city = "Karachi",
                contactNumber = "+923001119999",
                email = principalEmail,
                description = "Full lifecycle",
                establishedYear = 2015,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        val session = withTimeout(TIMEOUT_MS) {
            schoolRepo.createAcademicSession(school.id, "2026-2027", "01-04-2026", "31-03-2027", true).getOrThrow()
        }
        val sec10 = withTimeout(TIMEOUT_MS) {
            schoolRepo.createClassWithSection(school.id, session.id, "10th", "A", null, "Grade 10", null).getOrThrow()
        }
        val stu = withTimeout(TIMEOUT_MS) {
            schoolRepo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = sec10.classId, sectionId = sec10.id, rollNumber = "5",
                fullName = "Bilal Ahmed", fatherName = "Ahmed Ali", dateOfBirth = null,
                gender = "Male", parentName = "Ahmed Ali", parentContact = "+923000001111",
                address = "Block B", admissionDate = "01-04-2026", email = null, avatarUrl = null
            ).getOrThrow()
        }

        // Create a test & enter marks (leaves status = submitted, NOT published)
        val test = withTimeout(TIMEOUT_MS) {
            schoolRepo.createTest(
                schoolId = school.id,
                sessionId = session.id,
                classId = sec10.classId,
                sectionId = sec10.id,
                name = "Monthly Math Quiz",
                subject = "Mathematics",
                testDate = "10-11-2026",
                totalMarks = 50.0,
                passingMarks = 20.0,
                description = "Algebra",
                createdBy = principalUid
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            schoolRepo.saveTestResults(test, mapOf(stu.id to 44.0), principalUid, emptyMap()).getOrThrow()
            schoolRepo.updateTestStatus(school.id, test.id, "submitted", null).getOrThrow()
        }

        // Teacher accepts invite -> Student approved
        val teacherEmail = uniqueEmail("teach_rem")
        val teacherInvite = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacher(school.id, principalUid, "Mr. Tariq", teacherEmail, "0300", TeacherPermissions()).getOrThrow()
        }
        val teacherUid = signInTestUser(teacherEmail)
        val teacherProfile = withTimeout(TIMEOUT_MS) {
            schoolRepo.acceptTeacherInvitation(teacherInvite.inviteCode, teacherUid, teacherEmail, "Mr. Tariq").getOrThrow()
        }

        val studentEmail = uniqueEmail("stu_viewer")
        val studentUid = signInTestUser(studentEmail)
        withTimeout(TIMEOUT_MS) {
            schoolRepo.enterSchoolAsStudent(school.id, studentUid).getOrThrow()
        }

        // Student CAN load submitted tests and testResults from any class directly without approval!
        val studentBundle = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid).getOrThrow() }
        assertTrue("Student should see non-published submitted tests", studentBundle.tests.any { it.id == test.id && it.status == "submitted" })
        assertTrue("Student should see test results across classes", studentBundle.testResults.any { it.testId == test.id && it.obtainedMarks == 44.0 })

        // Principal deletes accepted teacher & hides school -> teacher & student immediately lose access
        signInTestUser(principalEmail)
        withTimeout(TIMEOUT_MS) {
            schoolRepo.deleteTeacherInvitation(school.id, teacherInvite.copy(status = "accepted", acceptedByUid = teacherUid)).getOrThrow()
            schoolRepo.setSchoolVisibility(school.id, false).getOrThrow()
        }

        signInTestUser(teacherEmail)
        val removedTeacherRes = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, teacherUid) }
        assertTrue("Removed teacher must lose workspace access", removedTeacherRes.isFailure)

        signInTestUser(studentEmail)
        val hiddenStudentRes = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, studentUid) }
        assertTrue("Student must lose access when school is hidden", hiddenStudentRes.isFailure)

        // Principal invites replacement Principal -> new user accepts handover -> old Principal loses lock, new Principal becomes owner & deletes school
        signInTestUser(principalEmail)
        val newPrincipalEmail = uniqueEmail("principal_b")
        val handOverInvite = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteReplacementPrincipal(school.id, principalUid, "New Principal B", newPrincipalEmail).getOrThrow()
        }
        assertTrue(handOverInvite.inviteCode.startsWith("PRN-"))

        val newPrincipalUid = signInTestUser(newPrincipalEmail)
        val transferredSchool = withTimeout(TIMEOUT_MS) {
            schoolRepo.acceptPrincipalInvitation(handOverInvite.inviteCode, newPrincipalUid, newPrincipalEmail, "New Principal B").getOrThrow()
        }
        assertEquals(newPrincipalUid, transferredSchool.ownerId)

        val newPrincipalCtx = withTimeout(TIMEOUT_MS) {
            authRepo.ensureCurrentAccountContext(UserRole.PRINCIPAL).getOrThrow()
        }
        assertEquals(UserRole.PRINCIPAL, newPrincipalCtx?.activeRole)
        assertEquals(school.id, newPrincipalCtx?.activeSchool?.id)

        val newPrincipalBundle = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, newPrincipalUid).getOrThrow() }
        assertEquals(newPrincipalUid, newPrincipalBundle.school.ownerId)

        // New Principal deletes their Principal account & school cleanly
        withTimeout(TIMEOUT_MS) {
            schoolRepo.deletePrincipalAccountAndSchool(school.id, newPrincipalUid).getOrThrow()
        }
        val afterDeleteRes = withTimeout(TIMEOUT_MS) { schoolRepo.loadSchoolWorkspace(school.id, newPrincipalUid) }
        assertTrue("Deleted school must no longer load", afterDeleteRes.isFailure)
        val leftover = teacherProfile.id // keep reference clear
        assertTrue(leftover.isNotBlank())
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

    @Test
    fun principalAndTeacher_canUpdateSchoolDetailsAndDirectory_studentDenied() = runBlocking {
        val principalEmail = uniqueEmail("principal_edit")
        val principalUid = signInTestUser(principalEmail)
        val repo = SchoolRepository(firestore)

        val school = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Original School",
                schoolType = "Private",
                address = "Main Road",
                city = "Lahore",
                contactNumber = "+923001112233",
                email = principalEmail,
                description = "Initial bio",
                establishedYear = 2010,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        // Principal updates school name, type, logo, banner & gallery
        val byPrincipal = withTimeout(TIMEOUT_MS) {
            repo.updateSchool(
                schoolId = school.id,
                name = "Principal Updated School",
                schoolType = "Government",
                address = "New Campus Rd",
                city = "Karachi",
                contactNumber = "+923009991111",
                email = principalEmail,
                description = "Updated by principal",
                establishedYear = 2011,
                website = "https://updated.edu",
                logoUrl = "data:image/jpeg;base64,PLOGO",
                bannerUrl = "data:image/jpeg;base64,PBANNER",
                galleryUrls = listOf("data:image/jpeg;base64,PGAL1")
            ).getOrThrow()
        }
        assertEquals("Principal Updated School", byPrincipal.name)
        assertEquals("Government", byPrincipal.schoolType)
        assertEquals("Karachi", byPrincipal.city)
        assertEquals("data:image/jpeg;base64,PLOGO", byPrincipal.logoUrl)

        // Invite and accept Teacher
        val teacherEmail = uniqueEmail("teacher_edit")
        val invite = withTimeout(TIMEOUT_MS) {
            repo.inviteTeacher(school.id, principalUid, "Ms. Hina", teacherEmail, "0300", TeacherPermissions()).getOrThrow()
        }
        val teacherUid = signInTestUser(teacherEmail)
        withTimeout(TIMEOUT_MS) {
            repo.acceptTeacherInvitation(invite.inviteCode, teacherUid, teacherEmail, "Ms. Hina").getOrThrow()
        }

        // Teacher updates school details, logo, banner, and gallery
        val byTeacher = withTimeout(TIMEOUT_MS) {
            repo.updateSchool(
                schoolId = school.id,
                name = "Teacher Edited School",
                schoolType = "Other",
                address = "College Chowk",
                city = "Bhakkar",
                contactNumber = "+923008887777",
                email = principalEmail,
                description = "Updated by teacher",
                establishedYear = 2014,
                website = "https://teacher-edited.edu",
                logoUrl = "data:image/jpeg;base64,TLOGO",
                bannerUrl = "data:image/jpeg;base64,TBANNER",
                galleryUrls = listOf("data:image/jpeg;base64,PGAL1", "data:image/jpeg;base64,TGAL2")
            ).getOrThrow()
        }
        assertEquals("Teacher Edited School", byTeacher.name)
        assertEquals("Other", byTeacher.schoolType)
        assertEquals("Bhakkar", byTeacher.city)
        assertEquals("data:image/jpeg;base64,TLOGO", byTeacher.logoUrl)
        assertEquals(2, byTeacher.galleryUrls.size)

        // Public school directory also reflects the updated name & city
        val dirResults = withTimeout(TIMEOUT_MS) { repo.searchSchools("Teacher Edited School").getOrThrow() }
        assertTrue(dirResults.any { it.id == school.id && it.city == "Bhakkar" && it.schoolType == "Other" })

        // Public school student enters directly & MUST be denied updating the school
        val studentEmail = uniqueEmail("student_noedit")
        val studentUid = signInTestUser(studentEmail)
        withTimeout(TIMEOUT_MS) {
            repo.enterSchoolAsStudent(school.id, studentUid).getOrThrow()
        }

        signInTestUser(studentEmail)
        val studentUpdateRes = withTimeout(TIMEOUT_MS) {
            repo.updateSchool(
                schoolId = school.id,
                name = "Hacked Name",
                schoolType = "Private",
                address = "Hack",
                city = "Lahore",
                contactNumber = "123",
                email = studentEmail,
                description = "Hack",
                establishedYear = null,
                website = null,
                logoUrl = null,
                bannerUrl = null,
                galleryUrls = emptyList()
            )
        }
        assertTrue("Student updateSchool must fail", studentUpdateRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            studentUpdateRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun studentAccount_remainsStudentPermanently_cannotBecomeTeacherOrPrincipal_andPinsUpToThreeSchools() = runBlocking {
        val principal1Email = uniqueEmail("principal_pin_1")
        val principal1Uid = signInTestUser(principal1Email)
        val repo = SchoolRepository(firestore)
        val authRepo = AuthRepository(auth, firestore)

        val school1 = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                ownerId = principal1Uid,
                name = "Pin Academy One",
                schoolType = "Private",
                address = "Rd 1",
                city = "Multan",
                contactNumber = "+923001110001",
                email = principal1Email,
                description = "School 1",
                establishedYear = 2018,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }
        val teacherInvite = withTimeout(TIMEOUT_MS) {
            repo.inviteTeacher(
                schoolId = school1.id,
                invitedBy = principal1Uid,
                fullName = "",
                email = "",
                phone = "",
                permissions = TeacherPermissions(markAttendance = true, viewStudents = true)
            ).getOrThrow()
        }

        // Create 3 more schools under separate principals to test max-3 pinning (total 4 public schools)
        val school2 = withTimeout(TIMEOUT_MS) {
            val email2 = uniqueEmail("principal_pin_2")
            val uid2 = signInTestUser(email2)
            repo.createSchoolWithPrincipal(
                uid2, "Pin Academy Two", "Private", "Rd 2", "Lahore", "+923001110002",
                email2, "School 2", 2019, null, null, null
            ).getOrThrow()
        }
        val school3 = withTimeout(TIMEOUT_MS) {
            val email3 = uniqueEmail("principal_pin_3")
            val uid3 = signInTestUser(email3)
            repo.createSchoolWithPrincipal(
                uid3, "Pin Academy Three", "Government", "Rd 3", "Karachi", "+923001110003",
                email3, "School 3", 2020, null, null, null
            ).getOrThrow()
        }
        val school4 = withTimeout(TIMEOUT_MS) {
            val email4 = uniqueEmail("principal_pin_4")
            val uid4 = signInTestUser(email4)
            repo.createSchoolWithPrincipal(
                uid4, "Pin Academy Four", "Other", "Rd 4", "Bhakkar", "+923001110004",
                email4, "School 4", 2021, null, null, null
            ).getOrThrow()
        }

        // Create a Student account
        val studentEmail = uniqueEmail("student_locked")
        val studentUid = signInTestUser(studentEmail)
        val initialStudentCtx = withTimeout(TIMEOUT_MS) {
            authRepo.ensureCurrentAccountContext(UserRole.STUDENT).getOrThrow()!!
        }
        assertEquals(UserRole.STUDENT.dbValue, initialStudentCtx.user.accountIntent)

        // Student MUST NOT be able to switch accountIntent, create a school, or accept a Teacher invite
        val switchRes = withTimeout(TIMEOUT_MS) { authRepo.updateAccountIntent(studentUid, UserRole.PRINCIPAL) }
        assertTrue("Student role switch must fail", switchRes.isFailure)

        val createSchoolRes = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                studentUid, "Unauthorized School", "Private", "Rd", "Lahore", "+923000000000",
                studentEmail, "Blocked", 2022, null, null, null
            )
        }
        assertTrue("Student creating a school must fail", createSchoolRes.isFailure)

        val acceptInviteRes = withTimeout(TIMEOUT_MS) {
            repo.acceptTeacherInvitation(
                inviteCode = TeacherInviteLinks.buildAppDeepLink(teacherInvite.inviteCode),
                userId = studentUid,
                fallbackEmail = studentEmail,
                fallbackName = "Locked Student"
            )
        }
        assertTrue("Student accepting a teacher invite must fail", acceptInviteRes.isFailure)

        // Student pins 3 schools -> succeeds; pinning a 4th fails!
        val pin1 = withTimeout(TIMEOUT_MS) { repo.pinSchoolForStudent(studentUid, school1.id).getOrThrow() }
        assertEquals(listOf(school1.id), pin1)
        val pin2 = withTimeout(TIMEOUT_MS) { repo.pinSchoolForStudent(studentUid, school2.id).getOrThrow() }
        assertEquals(listOf(school1.id, school2.id), pin2)
        val pin3 = withTimeout(TIMEOUT_MS) { repo.pinSchoolForStudent(studentUid, school3.id).getOrThrow() }
        assertEquals(listOf(school1.id, school2.id, school3.id), pin3)

        val pin4Res = withTimeout(TIMEOUT_MS) { repo.pinSchoolForStudent(studentUid, school4.id) }
        assertTrue("Pinning a 4th school must fail", pin4Res.isFailure)

        // On next reopen, default pinned school opens automatically
        val reopenCtx1 = withTimeout(TIMEOUT_MS) { authRepo.resolveAccountContext(studentUid).getOrThrow() }
        assertEquals(3, reopenCtx1.pinnedSchools.size)
        assertEquals(UserRole.STUDENT, reopenCtx1.activeRole)
        assertEquals(school1.id, reopenCtx1.activeSchool?.id)

        // Student switches to pinned school2 -> becomes active default on next reopen
        withTimeout(TIMEOUT_MS) { repo.enterSchoolAsStudent(school2.id, studentUid).getOrThrow() }
        val reopenCtx2 = withTimeout(TIMEOUT_MS) { authRepo.resolveAccountContext(studentUid).getOrThrow() }
        assertEquals(school2.id, reopenCtx2.activeSchool?.id)

        // Student unpins school1 -> now can pin school4!
        val afterUnpin = withTimeout(TIMEOUT_MS) { repo.unpinSchoolForStudent(studentUid, school1.id).getOrThrow() }
        assertEquals(listOf(school2.id, school3.id), afterUnpin)
        val pin4AfterUnpin = withTimeout(TIMEOUT_MS) { repo.pinSchoolForStudent(studentUid, school4.id).getOrThrow() }
        assertEquals(listOf(school2.id, school3.id, school4.id), pin4AfterUnpin)

        // Student temporarily opens unpinned school1 -> next reopen restores pinned default (school2)
        withTimeout(TIMEOUT_MS) { repo.enterSchoolAsStudent(school1.id, studentUid).getOrThrow() }
        val reopenAfterTemp = withTimeout(TIMEOUT_MS) { authRepo.resolveAccountContext(studentUid).getOrThrow() }
        assertEquals(school2.id, reopenAfterTemp.activeSchool?.id)
    }

    @Test
    fun classFees_teacherCreatesAnnualFunctionFee_marksPaidAndUnpaid_studentReadOnly() = runBlocking {
        val principalEmail = uniqueEmail("principal_fee")
        val principalUid = signInTestUser(principalEmail)
        val repo = SchoolRepository(firestore)

        val school = withTimeout(TIMEOUT_MS) {
            repo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Fee Test Academy",
                schoolType = "Private",
                address = "College Rd",
                city = "Bhakkar",
                contactNumber = "+923001112233",
                email = principalEmail,
                description = "Class fee testing",
                establishedYear = 2016,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        val session = withTimeout(TIMEOUT_MS) {
            repo.createAcademicSession(school.id, "2026-2027", "01-04-2026", "31-03-2027", true).getOrThrow()
        }
        val section = withTimeout(TIMEOUT_MS) {
            repo.createClassWithSection(school.id, session.id, "9th", "A", null, "Science", null).getOrThrow()
        }
        val stu1 = withTimeout(TIMEOUT_MS) {
            repo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "1",
                fullName = "Hamza Tariq", fatherName = "Tariq", dateOfBirth = null,
                gender = "Male", parentName = "Tariq", parentContact = "0300",
                address = "City", admissionDate = "01-04-2026", email = null, avatarUrl = null
            ).getOrThrow()
        }
        val stu2 = withTimeout(TIMEOUT_MS) {
            repo.saveStudent(
                schoolId = school.id, existingId = null, sessionId = session.id,
                classId = section.classId, sectionId = section.id, rollNumber = "2",
                fullName = "Sana Ullah", fatherName = "Ullah", dateOfBirth = null,
                gender = "Male", parentName = "Ullah", parentContact = "0301",
                address = "City", admissionDate = "01-04-2026", email = null, avatarUrl = null
            ).getOrThrow()
        }

        // Principal creates a class fee drive ("Annual Function Fee")
        val createdFee = withTimeout(TIMEOUT_MS) {
            repo.saveClassFeeDrive(
                existingFeeId = null,
                schoolId = school.id,
                sessionId = session.id,
                classId = section.classId,
                sectionId = section.id,
                title = "Annual Function Fee",
                description = "Contribution for Annual Function",
                amountText = "Rs. 500",
                feeDate = "17-11-2026",
                userId = principalUid,
                userName = "Principal"
            ).getOrThrow()
        }
        assertTrue(createdFee.paidStudentIds.isEmpty())

        // Invite Teacher & Teacher marks stu1 as Paid while stu2 stays Unpaid
        val teacherEmail = uniqueEmail("teacher_fee")
        val invite = withTimeout(TIMEOUT_MS) {
            repo.inviteTeacher(school.id, principalUid, "Mr. Bilal", teacherEmail, "0300", TeacherPermissions()).getOrThrow()
        }
        val teacherUid = signInTestUser(teacherEmail)
        withTimeout(TIMEOUT_MS) {
            repo.acceptTeacherInvitation(invite.inviteCode, teacherUid, teacherEmail, "Mr. Bilal").getOrThrow()
        }

        val afterPaid = withTimeout(TIMEOUT_MS) {
            repo.toggleStudentFeePaidStatus(school.id, createdFee.id, stu1.id, markPaid = true, updatedByName = "Mr. Bilal").getOrThrow()
        }
        assertTrue(afterPaid.paidStudentIds.contains(stu1.id))
        assertFalse(afterPaid.paidStudentIds.contains(stu2.id))
        assertTrue(afterPaid.paidDetails.containsKey(stu1.id))

        // Student enters public school -> can load classFees read-only, CANNOT mark fee paid
        val studentEmail = uniqueEmail("student_fee")
        val studentUid = signInTestUser(studentEmail)
        withTimeout(TIMEOUT_MS) {
            repo.enterSchoolAsStudent(school.id, studentUid).getOrThrow()
        }
        val studentBundle = withTimeout(TIMEOUT_MS) {
            repo.loadSchoolWorkspace(school.id, studentUid).getOrThrow()
        }
        val loadedFee = studentBundle.classFees.find { it.id == createdFee.id }
        assertNotNull("Student should see classFees", loadedFee)
        assertEquals("Annual Function Fee", loadedFee?.title)
        assertEquals(listOf(stu1.id), loadedFee?.paidStudentIds)

        val studentWriteRes = withTimeout(TIMEOUT_MS) {
            repo.toggleStudentFeePaidStatus(school.id, createdFee.id, stu2.id, markPaid = true, updatedByName = "Student")
        }
        assertTrue("Student must not toggle fee status", studentWriteRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            studentWriteRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun principalGeneratesTeacherCredentials_teacherVerifiesAndJoinsSchoolWithCustomName() = runBlocking {
        val principalEmail = uniqueEmail("principal_cred")
        val principalUid = signInTestUser(principalEmail)
        val schoolRepo = SchoolRepository(firestore)

        val school = withTimeout(TIMEOUT_MS) {
            schoolRepo.createSchoolWithPrincipal(
                ownerId = principalUid,
                name = "Credentials Test Academy",
                schoolType = "Private",
                address = "Gulberg III",
                city = "Lahore",
                contactNumber = "+923001122334",
                email = principalEmail,
                description = "Teacher Credential Test",
                establishedYear = 2018,
                website = null,
                logoUrl = null,
                bannerUrl = null
            ).getOrThrow()
        }

        val username = "ali.science"
        val password = "Pass1234"
        val perms = TeacherPermissions(
            viewStudents = true,
            editStudents = true,
            markAttendance = true,
            editAttendance = true,
            createTests = true,
            enterMarks = true,
            publishAnnouncements = true,
            viewReports = true
        )

        // 1. Principal creates teacher credentials
        val credInviteRes = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacherWithCredentials(
                schoolId = school.id,
                invitedBy = principalUid,
                fullName = "Ali Raza",
                email = "ali.science@gmail.com",
                phone = "03001234567",
                username = username,
                accessPassword = password,
                permissions = perms
            )
        }
        assertTrue("Expected credential invite creation to succeed: ${credInviteRes.exceptionOrNull()}", credInviteRes.isSuccess)
        val invite = credInviteRes.getOrThrow()
        assertEquals("credentials", invite.authType)
        assertEquals(username, invite.username)
        assertEquals(password, invite.accessPassword)

        // 2. Duplicate username creation must fail
        val dupRes = withTimeout(TIMEOUT_MS) {
            schoolRepo.inviteTeacherWithCredentials(
                schoolId = school.id,
                invitedBy = principalUid,
                fullName = "Another Teacher",
                email = "another@gmail.com",
                phone = "03009999999",
                username = username,
                accessPassword = "Password99",
                permissions = perms
            )
        }
        assertTrue("Duplicate username should fail", dupRes.isFailure)

        // 3. Teacher signs in with Google and enters credentials & custom name
        val teacherEmail = uniqueEmail("teacher_cred")
        val teacherUid = signInTestUser(teacherEmail)

        val verifyRes = withTimeout(TIMEOUT_MS) {
            schoolRepo.verifyTeacherCredentials(username, password)
        }
        assertTrue("Credential verification should succeed: ${verifyRes.exceptionOrNull()}", verifyRes.isSuccess)
        val verifiedCred = verifyRes.getOrThrow()
        assertEquals("pending", verifiedCred.status)
        assertEquals(invite.inviteCode, verifiedCred.inviteCode)

        // Teacher accepts via credentials with their stated full name
        val acceptRes = withTimeout(TIMEOUT_MS) {
            schoolRepo.acceptTeacherByCredentials(
                username = username,
                accessPassword = password,
                userId = teacherUid,
                fallbackEmail = teacherEmail,
                teacherEnteredName = "Sir Ali Raza M.Sc",
                teacherEnteredPhone = "03007654321"
            )
        }
        assertTrue("Teacher acceptance should succeed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)
        val profile = acceptRes.getOrThrow()
        assertEquals("Sir Ali Raza M.Sc", profile.fullName)
        assertEquals("03007654321", profile.phone)
        assertTrue(profile.isActive)
        assertTrue(profile.staffUids.contains(teacherUid))
        assertTrue(profile.staffUids.contains(principalUid))

        // Check user document was updated
        val userSnap = firestore.collection("users").document(teacherUid).get().await()
        assertTrue(userSnap.exists())
        assertEquals("teacher", userSnap.getString("accountIntent"))
        assertEquals(school.id, userSnap.getString("activeSchoolId"))
        assertEquals("Sir Ali Raza M.Sc", userSnap.getString("fullName"))
    }

    private companion object {
        const val TIMEOUT_MS = 12000L
    }
}

