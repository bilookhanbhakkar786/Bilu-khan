package com.example

import com.example.data.model.AcademicCalculators
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.TestExam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalculatorsTest {

    @Test
    fun dailyAttendance_aggregatesFiveSubjectSessionsOnSameDayIntoOneDay() {
        val sessions = listOf(
            AttendanceSession(id = "s1", attendanceDate = "2026-11-15", subject = "Morning", sessionSlot = "Morning"),
            AttendanceSession(id = "s2", attendanceDate = "2026-11-15", subject = "Math", sessionSlot = "Period 1"),
            AttendanceSession(id = "s3", attendanceDate = "2026-11-15", subject = "English", sessionSlot = "Period 2"),
            AttendanceSession(id = "s4", attendanceDate = "2026-11-15", subject = "Science", sessionSlot = "Period 3"),
            AttendanceSession(id = "s5", attendanceDate = "2026-11-15", subject = "Afternoon", sessionSlot = "Afternoon")
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
}
