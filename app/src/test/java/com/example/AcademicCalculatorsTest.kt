package com.example

import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherInviteLinks
import com.example.data.model.TestExam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalculatorsTest {

    @Test
    fun dailyAttendance_aggregatesFiveSubjectSessionsOnSameDayIntoOneDay() {
        val sessions = listOf(
            AttendanceSession(id = "s1", attendanceDate = "15-11-2026", subject = "Morning", sessionSlot = "Morning"),
            AttendanceSession(id = "s2", attendanceDate = "2026-11-15", subject = "Math", sessionSlot = "Lecture 1"),
            AttendanceSession(id = "s3", attendanceDate = "15-11-2026", subject = "English", sessionSlot = "Lecture 2"),
            AttendanceSession(id = "s4", attendanceDate = "15/11/2026", subject = "Science", sessionSlot = "Lecture 3"),
            AttendanceSession(id = "s5", attendanceDate = "15-11-2026", subject = "Urdu", sessionSlot = "Lecture 4")
        )
        val records = sessions.map {
            AttendanceRecord(attendanceSessionId = it.id, studentId = "stu1", status = "absent")
        }

        val summary = AcademicCalculators.computeStudentAttendanceSummary("stu1", sessions, records)

        // Never count 5 subject sessions on one day as 5 absent school days
        assertEquals(1, summary.dailyStats.totalCount)
        assertEquals(1, summary.dailyStats.absentCount)
        assertEquals(0, summary.dailyStats.presentCount)

        // Period/subject attendance counts all 5 individual sessions separately
        assertEquals(5, summary.periodStats.totalCount)
        assertEquals(5, summary.periodStats.absentCount)
    }

    @Test
    fun computeRollNumberShifts_insertsAtRoll2AndCascadesContiguousPeers() {
        val existing = listOf(
            StudentRecord(id = "id1", rollNumber = "1", fullName = "Ali", status = "active"),
            StudentRecord(id = "id2", rollNumber = "2", fullName = "Bilal", status = "active"),
            StudentRecord(id = "id3", rollNumber = "3", fullName = "Usman", status = "active"),
            StudentRecord(id = "id5", rollNumber = "5", fullName = "Zaid", status = "active")
        )

        val shifts = AcademicCalculators.computeRollNumberShifts(existing, "2")

        // Roll 2 -> 3, Roll 3 -> 4, gap before 5 so Roll 5 stays unchanged
        assertEquals(mapOf("id2" to "3", "id3" to "4"), shifts)
        assertFalse(shifts.containsKey("id1"))
        assertFalse(shifts.containsKey("id5"))
    }

    @Test
    fun sortStudentsByRoll_ordersNumericallyNotLexicographically() {
        val unsorted = listOf(
            StudentRecord(id = "s10", rollNumber = "10", fullName = "Student 10"),
            StudentRecord(id = "s2", rollNumber = "2", fullName = "Student 2"),
            StudentRecord(id = "s1", rollNumber = "1", fullName = "Student 1"),
            StudentRecord(id = "s20", rollNumber = "20", fullName = "Student 20")
        )
        val sorted = AcademicCalculators.sortStudentsByRoll(unsorted)
        assertEquals(listOf("1", "2", "10", "20"), sorted.map { it.rollNumber })
    }

    @Test
    fun dateFormattingAndDefaultLectures_followDDMMYYYYAndMorningPlusSixLectures() {
        assertEquals("05-04-2026", AcademicCalculators.formatDisplayDate("2026-04-05"))
        assertEquals("05-04-2026", AcademicCalculators.formatDisplayDate("05/04/2026"))
        assertEquals("05-04-2026", AcademicCalculators.formatDisplayDate("05-04-2026"))

        assertEquals(
            listOf("Morning", "Lecture 1", "Lecture 2", "Lecture 3", "Lecture 4", "Lecture 5", "Lecture 6"),
            AcademicCalculators.DEFAULT_LECTURE_SLOTS
        )
    }

    @Test
    fun buildRankedResults_calculatesPercentagesGradesPassFailAndTiedRanks() {
        val test = TestExam(
            id = "t1",
            schoolId = "sch1",
            name = "Midterm",
            subject = "Math",
            totalMarks = 100.0,
            passingMarks = 40.0
        )
        val results = AcademicCalculators.buildRankedResults(
            test = test,
            marksByStudentId = mapOf("stuA" to 91.0, "stuB" to 91.0, "stuC" to 72.0, "stuD" to 35.0),
            enteredBy = "teacher1"
        )

        val stuA = results.first { it.studentId == "stuA" }
        val stuB = results.first { it.studentId == "stuB" }
        val stuC = results.first { it.studentId == "stuC" }
        val stuD = results.first { it.studentId == "stuD" }

        assertEquals(1, stuA.rank)
        assertEquals(1, stuB.rank)
        assertEquals("A+", stuA.grade)
        assertTrue(stuA.passed)

        assertEquals(3, stuC.rank)
        assertEquals("B", stuC.grade)
        assertTrue(stuC.passed)

        assertEquals(4, stuD.rank)
        assertEquals("F", stuD.grade)
        assertFalse(stuD.passed)
    }

    @Test
    fun sortClassesAndSections_ordersGradesNaturallyPlaygroupTo12th() {
        val unsortedClasses = listOf(
            com.example.data.model.SchoolClass(id = "c10", name = "10th"),
            com.example.data.model.SchoolClass(id = "c2", name = "2nd"),
            com.example.data.model.SchoolClass(id = "cnur", name = "Nursery"),
            com.example.data.model.SchoolClass(id = "c1", name = "1st"),
            com.example.data.model.SchoolClass(id = "cpg", name = "Playgroup"),
            com.example.data.model.SchoolClass(id = "c12", name = "12th"),
            com.example.data.model.SchoolClass(id = "c3", name = "3rd"),
            com.example.data.model.SchoolClass(id = "ckg", name = "KG")
        )
        val sortedClasses = AcademicCalculators.sortClasses(unsortedClasses)
        assertEquals(
            listOf("Playgroup", "Nursery", "KG", "1st", "2nd", "3rd", "10th", "12th"),
            sortedClasses.map { it.name }
        )

        val unsortedSections = listOf(
            com.example.data.model.ClassSection(id = "s10b", classId = "c10", name = "B"),
            com.example.data.model.ClassSection(id = "s2a", classId = "c2", name = "A"),
            com.example.data.model.ClassSection(id = "s1b", classId = "c1", name = "B"),
            com.example.data.model.ClassSection(id = "s10a", classId = "c10", name = "A"),
            com.example.data.model.ClassSection(id = "s1a", classId = "c1", name = "A")
        )
        val sortedSections = AcademicCalculators.sortSections(unsortedSections, sortedClasses)
        assertEquals(
            listOf("s1a", "s1b", "s2a", "s10a", "s10b"),
            sortedSections.map { it.id }
        )
    }

    @Test
    fun sortStudentsByClassAndRoll_groupsByOrderedClassThenNumericRoll() {
        val classes = listOf(
            com.example.data.model.SchoolClass(id = "c10", name = "10th"),
            com.example.data.model.SchoolClass(id = "c1", name = "1st"),
            com.example.data.model.SchoolClass(id = "c2", name = "2nd")
        )
        val sections = listOf(
            com.example.data.model.ClassSection(id = "sec10", classId = "c10", name = "A"),
            com.example.data.model.ClassSection(id = "sec1", classId = "c1", name = "A"),
            com.example.data.model.ClassSection(id = "sec2", classId = "c2", name = "A")
        )
        val students = listOf(
            StudentRecord(id = "st10_1", classId = "c10", sectionId = "sec10", rollNumber = "1", fullName = "Tariq"),
            StudentRecord(id = "st1_10", classId = "c1", sectionId = "sec1", rollNumber = "10", fullName = "Amina"),
            StudentRecord(id = "st2_1", classId = "c2", sectionId = "sec2", rollNumber = "1", fullName = "Bilal"),
            StudentRecord(id = "st1_2", classId = "c1", sectionId = "sec1", rollNumber = "2", fullName = "Sara"),
            StudentRecord(id = "st1_1", classId = "c1", sectionId = "sec1", rollNumber = "1", fullName = "Ali")
        )
        val sorted = AcademicCalculators.sortStudentsByClassAndRoll(students, classes, sections)
        assertEquals(
            listOf("st1_1", "st1_2", "st1_10", "st2_1", "st10_1"),
            sorted.map { it.id }
        )
    }

    @Test
    fun sortLectureSlotsAndTimetable_ordersMorningLecturesAndClockTimesNaturally() {
        val slots = AcademicCalculators.sortLectureSlots(
            listOf("Lecture 10", "Lecture 2", "Morning", "Lecture 7", "Lecture 1", "Lab")
        )
        assertEquals(
            listOf("Morning", "Lecture 1", "Lecture 2", "Lecture 7", "Lecture 10", "Lab"),
            slots
        )

        val classes = listOf(
            com.example.data.model.SchoolClass(id = "c1", name = "1st"),
            com.example.data.model.SchoolClass(id = "c2", name = "2nd")
        )
        val sections = listOf(
            com.example.data.model.ClassSection(id = "s1", classId = "c1", name = "A"),
            com.example.data.model.ClassSection(id = "s2", classId = "c2", name = "A")
        )
        val timetable = AcademicCalculators.sortTimetableSlots(
            listOf(
                com.example.data.model.TimetableSlot(id = "t3", sectionId = "s1", dayOfWeek = "Monday", startTime = "10:00", subject = "English"),
                com.example.data.model.TimetableSlot(id = "t2", sectionId = "s2", dayOfWeek = "Monday", startTime = "8:30", subject = "Math"),
                com.example.data.model.TimetableSlot(id = "t1", sectionId = "s1", dayOfWeek = "Monday", startTime = "08:30", subject = "Urdu"),
                com.example.data.model.TimetableSlot(id = "t0", sectionId = "s1", dayOfWeek = "Tuesday", startTime = "08:00", subject = "Science")
            ),
            classes,
            sections
        )
        assertEquals(listOf("t1", "t2", "t3", "t0"), timetable.map { it.id })
    }

    @Test
    fun teacherInviteLinks_extractsCodeFromUrlsWhatsAppShareMessageAndRawCode() {
        val code = "TCH-482910"
        val webUrl = TeacherInviteLinks.buildWebInviteLink(code)
        val deepLink = TeacherInviteLinks.buildAppDeepLink(code)
        val shareMessage = TeacherInviteLinks.buildShareMessage("Beacon Light", "BL-102030", code)

        assertEquals(code, TeacherInviteLinks.extractInviteCode(webUrl))
        assertEquals(code, TeacherInviteLinks.extractInviteCode(deepLink))
        assertEquals(code, TeacherInviteLinks.extractInviteCode(shareMessage))
        assertEquals(code, TeacherInviteLinks.extractInviteCode("  tch-482910  "))
        assertTrue(shareMessage.contains("blschoolhub://invite/teacher?code=$code"))
        assertFalse(shareMessage.contains("https://blschoolhub.app"))
    }
}

