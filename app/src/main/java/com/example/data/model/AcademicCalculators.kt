package com.example.data.model

import kotlin.math.roundToInt

object AcademicCalculators {

    fun calculatePercentage(obtained: Double, total: Double): Double {
        if (total <= 0.0) return 0.0
        val raw = (obtained / total) * 100.0
        return (raw * 100.0).roundToInt() / 100.0
    }

    fun calculateGrade(percentage: Double, passed: Boolean): String {
        if (!passed) return "F"
        return when {
            percentage >= 90.0 -> "A+"
            percentage >= 80.0 -> "A"
            percentage >= 70.0 -> "B"
            percentage >= 60.0 -> "C"
            percentage >= 50.0 -> "D"
            else -> "E"
        }
    }

    fun buildRankedResults(
        test: TestExam,
        marksByStudentId: Map<String, Double>,
        enteredBy: String?
    ): List<TestResult> {
        val prelim = marksByStudentId.map { (studentId, marks) ->
            val clamped = marks.coerceIn(0.0, test.totalMarks)
            val pct = calculatePercentage(clamped, test.totalMarks)
            val passed = clamped >= test.passingMarks
            val grade = calculateGrade(pct, passed)
            Triple(studentId, clamped, Pair(pct, Pair(grade, passed)))
        }.sortedByDescending { it.second }

        var currentRank = 0
        var lastMarks: Double? = null
        return prelim.mapIndexed { index, item ->
            if (lastMarks == null || item.second < lastMarks!!) {
                currentRank = index + 1
                lastMarks = item.second
            }
            TestResult(
                schoolId = test.schoolId,
                testId = test.id,
                studentId = item.first,
                obtainedMarks = item.second,
                percentage = item.third.first,
                grade = item.third.second.first,
                passed = item.third.second.second,
                rank = currentRank,
                enteredBy = enteredBy
            )
        }
    }

    /**
     * Computes TWO separate attendance statistics for a student as required:
     * 1. Daily Attendance: Aggregates all sessions on the same calendar date into ONE school day.
     *    Rule: If student was Present in at least 1 session that day -> Present day.
     *    Else if Leave -> Leave day. Else -> Absent day.
     *    Never counts 5 subject sessions on one day as 5 absent school days.
     * 2. Period/Subject Attendance: Counts every individual attendance session separately.
     */
    fun computeStudentAttendanceSummary(
        studentId: String,
        sessions: List<AttendanceSession>,
        records: List<AttendanceRecord>
    ): StudentAttendanceSummary {
        val sessionMap = sessions.associateBy { it.id }
        val studentRecords = records.filter { it.studentId == studentId && sessionMap.containsKey(it.attendanceSessionId) }

        // 1. Period / Subject stats (1 per record)
        var periodPresent = 0
        var periodAbsent = 0
        var periodLeave = 0
        val bySubject = mutableMapOf<String, MutableList<AttendanceStatus>>()

        for (rec in studentRecords) {
            val session = sessionMap[rec.attendanceSessionId] ?: continue
            val status = AttendanceStatus.fromDb(rec.status)
            when (status) {
                AttendanceStatus.PRESENT -> periodPresent++
                AttendanceStatus.ABSENT -> periodAbsent++
                AttendanceStatus.LEAVE -> periodLeave++
            }
            bySubject.getOrPut(session.subject) { mutableListOf() }.add(status)
        }

        val periodStats = AttendanceStats(
            totalCount = studentRecords.size,
            presentCount = periodPresent,
            absentCount = periodAbsent,
            leaveCount = periodLeave
        )

        // 2. Daily stats (group by unique attendance_date)
        val recordsByDate = studentRecords.groupBy { rec ->
            sessionMap[rec.attendanceSessionId]?.attendanceDate ?: ""
        }.filterKeys { it.isNotBlank() }

        var dailyPresent = 0
        var dailyAbsent = 0
        var dailyLeave = 0

        for ((_, dayRecords) in recordsByDate) {
            val statuses = dayRecords.map { AttendanceStatus.fromDb(it.status) }
            when {
                statuses.any { it == AttendanceStatus.PRESENT } -> dailyPresent++
                statuses.any { it == AttendanceStatus.LEAVE } -> dailyLeave++
                else -> dailyAbsent++
            }
        }

        val dailyStats = AttendanceStats(
            totalCount = recordsByDate.size,
            presentCount = dailyPresent,
            absentCount = dailyAbsent,
            leaveCount = dailyLeave
        )

        val subjectStats = bySubject.mapValues { (_, list) ->
            AttendanceStats(
                totalCount = list.size,
                presentCount = list.count { it == AttendanceStatus.PRESENT },
                absentCount = list.count { it == AttendanceStatus.ABSENT },
                leaveCount = list.count { it == AttendanceStatus.LEAVE }
            )
        }

        return StudentAttendanceSummary(
            dailyStats = dailyStats,
            periodStats = periodStats,
            subjectBreakdown = subjectStats
        )
    }
}
