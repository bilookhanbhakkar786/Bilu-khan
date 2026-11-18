package com.example.data.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object AcademicCalculators {

    private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    private val LEGACY_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val SLASH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    val DEFAULT_LECTURE_SLOTS: List<String> = listOf(
        "Morning",
        "Lecture 1",
        "Lecture 2",
        "Lecture 3",
        "Lecture 4",
        "Lecture 5",
        "Lecture 6"
    )

    fun todayDisplayDate(): String = LocalDate.now().format(DISPLAY_DATE_FORMATTER)

    fun plusDaysDisplayDate(days: Long): String = LocalDate.now().plusDays(days).format(DISPLAY_DATE_FORMATTER)

    fun minusDaysDisplayDate(days: Long): String = LocalDate.now().minusDays(days).format(DISPLAY_DATE_FORMATTER)

    fun parseFlexibleDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        val formatters = listOf(DISPLAY_DATE_FORMATTER, LEGACY_ISO_FORMATTER, SLASH_DATE_FORMATTER)
        for (fmt in formatters) {
            try {
                return LocalDate.parse(text, fmt)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun formatDisplayDate(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return ""
        return parseFlexibleDate(text)?.format(DISPLAY_DATE_FORMATTER) ?: text
    }

    fun isSameMonth(dateStr: String?, referenceDate: LocalDate = LocalDate.now()): Boolean {
        val parsed = parseFlexibleDate(dateStr) ?: return false
        return parsed.year == referenceDate.year && parsed.month == referenceDate.month
    }

    private val WEEKDAY_SEQUENCE: List<String> = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val ROMAN_GRADE_MAP: Map<String, Int> = mapOf(
        "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4,
        "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8,
        "ix" to 9, "x" to 10, "xi" to 11, "xii" to 12
    )

    private val ROMAN_GRADE_REGEX = Regex("^(?:class|grade|std)?\\s*(i|ii|iii|iv|v|vi|vii|viii|ix|x|xi|xii)$")
    private val FIRST_INT_REGEX = Regex("\\d+")
    private val TIME_REGEX = Regex("(\\d{1,2})(?::(\\d{2}))?")

    fun naturalCompare(a: String, b: String): Int {
        val s1 = a.trim().lowercase(Locale.US)
        val s2 = b.trim().lowercase(Locale.US)
        var i = 0
        var j = 0
        while (i < s1.length && j < s2.length) {
            val c1 = s1[i]
            val c2 = s2[j]
            if (c1.isDigit() && c2.isDigit()) {
                val start1 = i
                while (i < s1.length && s1[i].isDigit()) i++
                val start2 = j
                while (j < s2.length && s2[j].isDigit()) j++
                val num1 = s1.substring(start1, i).trimStart('0')
                val num2 = s2.substring(start2, j).trimStart('0')
                if (num1.length != num2.length) {
                    return num1.length.compareTo(num2.length)
                }
                val cmp = num1.compareTo(num2)
                if (cmp != 0) return cmp
                // Tie-break shorter leading-zero representation if identical numerically
                val rawLenCmp = (i - start1).compareTo(j - start2)
                if (rawLenCmp != 0) return rawLenCmp
            } else {
                if (c1 != c2) return c1.compareTo(c2)
                i++
                j++
            }
        }
        return s1.length.compareTo(s2.length)
    }

    /**
     * Produces a canonical ordering key for school class names so Playgroup -> Nursery -> KG/Prep ->
     * 1st -> 2nd -> 3rd -> ... -> 10th -> 11th -> 12th -> custom classes stay strictly in order.
     */
    fun classOrderKey(className: String): Pair<Int, Int> {
        val n = className.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
        return when {
            n in setOf("playgroup", "play group", "pg", "pre school", "preschool") -> 0 to -10
            n in setOf("pre-nursery", "pre nursery", "prenursery") -> 0 to -9
            n in setOf("nursery", "nur") -> 0 to -8
            n in setOf("lkg", "lower kg", "kg-1", "kg 1", "kg i", "kg1") -> 0 to -7
            n in setOf("prep", "ukg", "upper kg", "kg-2", "kg 2", "kg ii", "kg2", "kg", "kindergarten") -> 0 to -6
            else -> {
                val digitMatch = FIRST_INT_REGEX.find(n)
                if (digitMatch != null) {
                    1 to (digitMatch.value.toIntOrNull() ?: Int.MAX_VALUE)
                } else {
                    val romanMatch = ROMAN_GRADE_REGEX.matchEntire(n)
                    val romanRank = romanMatch?.groupValues?.getOrNull(1)?.let { ROMAN_GRADE_MAP[it] }
                    if (romanRank != null) 1 to romanRank else 2 to Int.MAX_VALUE
                }
            }
        }
    }

    fun compareClassNames(a: String, b: String): Int {
        val k1 = classOrderKey(a)
        val k2 = classOrderKey(b)
        if (k1.first != k2.first) return k1.first.compareTo(k2.first)
        if (k1.second != k2.second) return k1.second.compareTo(k2.second)
        return naturalCompare(a, b)
    }

    fun sortClasses(classes: List<SchoolClass>): List<SchoolClass> =
        classes.sortedWith { a, b ->
            val cmp = compareClassNames(a.name, b.name)
            if (cmp != 0) cmp else a.id.compareTo(b.id)
        }

    fun sortSections(sections: List<ClassSection>, classes: List<SchoolClass>): List<ClassSection> {
        val classById = classes.associateBy { it.id }
        return sections.sortedWith { a, b ->
            val clsA = classById[a.classId]?.name
            val clsB = classById[b.classId]?.name
            val classCmp = when {
                clsA != null && clsB != null -> compareClassNames(clsA, clsB)
                clsA != null -> -1
                clsB != null -> 1
                else -> 0
            }
            if (classCmp != 0) return@sortedWith classCmp
            val secCmp = naturalCompare(a.name, b.name)
            if (secCmp != 0) secCmp else a.id.compareTo(b.id)
        }
    }

    fun sortSessions(sessions: List<AcademicSession>): List<AcademicSession> =
        sessions.sortedWith { a, b ->
            val currentCmp = b.isCurrent.compareTo(a.isCurrent)
            if (currentCmp != 0) return@sortedWith currentCmp
            val dA = parseFlexibleDate(a.startDate)?.toEpochDay() ?: Long.MIN_VALUE
            val dB = parseFlexibleDate(b.startDate)?.toEpochDay() ?: Long.MIN_VALUE
            if (dA != dB) return@sortedWith dB.compareTo(dA)
            val nameCmp = naturalCompare(b.name, a.name)
            if (nameCmp != 0) nameCmp else a.id.compareTo(b.id)
        }

    fun lectureSlotOrderKey(slot: String): Pair<Int, Int> {
        val n = slot.trim().lowercase(Locale.US)
        if (n == "morning" || n == "assembly" || n == "0") return 0 to 0
        val digit = FIRST_INT_REGEX.find(n)?.value?.toIntOrNull()
        return if (digit != null) 1 to digit else 2 to Int.MAX_VALUE
    }

    fun compareLectureSlots(a: String, b: String): Int {
        val k1 = lectureSlotOrderKey(a)
        val k2 = lectureSlotOrderKey(b)
        if (k1.first != k2.first) return k1.first.compareTo(k2.first)
        if (k1.second != k2.second) return k1.second.compareTo(k2.second)
        return naturalCompare(a, b)
    }

    fun sortLectureSlots(slots: List<String>): List<String> {
        val deduped = linkedMapOf<String, String>()
        for (raw in slots) {
            val clean = raw.trim()
            if (clean.isNotBlank()) {
                val key = clean.lowercase(Locale.US)
                if (!deduped.containsKey(key)) deduped[key] = clean
            }
        }
        return deduped.values.sortedWith(::compareLectureSlots)
    }

    fun parseTimeMinutes(raw: String): Int {
        val trimmed = raw.trim().lowercase(Locale.US)
        val match = TIME_REGEX.find(trimmed) ?: return Int.MAX_VALUE
        var hour = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (trimmed.contains("pm") && hour in 1..11) hour += 12
        if (trimmed.contains("am") && hour == 12) hour = 0
        return hour * 60 + minute
    }

    private fun sectionRankMap(classes: List<SchoolClass>, sections: List<ClassSection>): Map<String, Int> =
        sortSections(sections, classes).withIndex().associate { (idx, sec) -> sec.id to idx }

    private fun classRankMap(classes: List<SchoolClass>): Map<String, Int> =
        sortClasses(classes).withIndex().associate { (idx, cls) -> cls.id to idx }

    fun sortStudentsByRoll(students: List<StudentRecord>): List<StudentRecord> =
        students.sortedWith { a, b ->
            val r1 = a.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE
            val r2 = b.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE
            if (r1 != r2) return@sortedWith r1.compareTo(r2)
            val rollCmp = naturalCompare(a.rollNumber, b.rollNumber)
            if (rollCmp != 0) return@sortedWith rollCmp
            naturalCompare(a.fullName, b.fullName)
        }

    fun sortStudentsByClassAndRoll(
        students: List<StudentRecord>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<StudentRecord> {
        val secRanks = sectionRankMap(classes, sections)
        val clsRanks = classRankMap(classes)
        return students.sortedWith { a, b ->
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            val clsCmp = (clsRanks[a.classId] ?: Int.MAX_VALUE).compareTo(clsRanks[b.classId] ?: Int.MAX_VALUE)
            if (clsCmp != 0) return@sortedWith clsCmp
            val r1 = a.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE
            val r2 = b.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE
            if (r1 != r2) return@sortedWith r1.compareTo(r2)
            val rollCmp = naturalCompare(a.rollNumber, b.rollNumber)
            if (rollCmp != 0) return@sortedWith rollCmp
            naturalCompare(a.fullName, b.fullName)
        }
    }

    fun sortTeachers(teachers: List<TeacherProfile>): List<TeacherProfile> =
        teachers.sortedWith { a, b ->
            val activeCmp = b.isActive.compareTo(a.isActive)
            if (activeCmp != 0) return@sortedWith activeCmp
            val nameCmp = naturalCompare(a.fullName, b.fullName)
            if (nameCmp != 0) nameCmp else a.id.compareTo(b.id)
        }

    fun sortTeacherAssignments(
        assignments: List<TeacherAssignment>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<TeacherAssignment> {
        val secRanks = sectionRankMap(classes, sections)
        return assignments.sortedWith { a, b ->
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            naturalCompare(a.subject, b.subject)
        }
    }

    fun sortAttendanceSessions(
        sessions: List<AttendanceSession>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<AttendanceSession> {
        val secRanks = sectionRankMap(classes, sections)
        val clsRanks = classRankMap(classes)
        return sessions.sortedWith { a, b ->
            val d1 = parseFlexibleDate(a.attendanceDate)?.toEpochDay() ?: 0L
            val d2 = parseFlexibleDate(b.attendanceDate)?.toEpochDay() ?: 0L
            if (d1 != d2) return@sortedWith d2.compareTo(d1)
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            val clsCmp = (clsRanks[a.classId] ?: Int.MAX_VALUE).compareTo(clsRanks[b.classId] ?: Int.MAX_VALUE)
            if (clsCmp != 0) return@sortedWith clsCmp
            val slotCmp = compareLectureSlots(a.sessionSlot, b.sessionSlot)
            if (slotCmp != 0) return@sortedWith slotCmp
            naturalCompare(a.subject, b.subject)
        }
    }

    fun sortTests(
        tests: List<TestExam>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<TestExam> {
        val secRanks = sectionRankMap(classes, sections)
        val clsRanks = classRankMap(classes)
        return tests.sortedWith { a, b ->
            val d1 = parseFlexibleDate(a.testDate)?.toEpochDay() ?: 0L
            val d2 = parseFlexibleDate(b.testDate)?.toEpochDay() ?: 0L
            if (d1 != d2) return@sortedWith d2.compareTo(d1)
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            val clsCmp = (clsRanks[a.classId] ?: Int.MAX_VALUE).compareTo(clsRanks[b.classId] ?: Int.MAX_VALUE)
            if (clsCmp != 0) return@sortedWith clsCmp
            naturalCompare(a.name, b.name)
        }
    }

    fun sortHomework(
        homework: List<HomeworkItem>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<HomeworkItem> {
        val secRanks = sectionRankMap(classes, sections)
        return homework.sortedWith { a, b ->
            val c1 = a.createdAt?.seconds ?: 0L
            val c2 = b.createdAt?.seconds ?: 0L
            if (c1 != c2) return@sortedWith c2.compareTo(c1)
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            val subjCmp = naturalCompare(a.subject, b.subject)
            if (subjCmp != 0) subjCmp else naturalCompare(a.title, b.title)
        }
    }

    fun sortTimetableSlots(
        slots: List<TimetableSlot>,
        classes: List<SchoolClass>,
        sections: List<ClassSection>
    ): List<TimetableSlot> {
        val secRanks = sectionRankMap(classes, sections)
        return slots.sortedWith { a, b ->
            val dayA = WEEKDAY_SEQUENCE.indexOfFirst { it.equals(a.dayOfWeek, ignoreCase = true) }
                .let { if (it >= 0) it else Int.MAX_VALUE }
            val dayB = WEEKDAY_SEQUENCE.indexOfFirst { it.equals(b.dayOfWeek, ignoreCase = true) }
                .let { if (it >= 0) it else Int.MAX_VALUE }
            if (dayA != dayB) return@sortedWith dayA.compareTo(dayB)
            val tA = parseTimeMinutes(a.startTime)
            val tB = parseTimeMinutes(b.startTime)
            if (tA != tB) return@sortedWith tA.compareTo(tB)
            val secCmp = (secRanks[a.sectionId] ?: Int.MAX_VALUE).compareTo(secRanks[b.sectionId] ?: Int.MAX_VALUE)
            if (secCmp != 0) return@sortedWith secCmp
            naturalCompare(a.subject, b.subject)
        }
    }

    /**
     * Inserts a student at [newRollNumber] in numeric order and shifts any colliding or
     * contiguous subsequent students in the same section up by +1.
     * Example: existing [1, 2, 3], assigning 2 -> returns {student2Id: "3", student3Id: "4"}.
     */
    fun computeRollNumberShifts(
        existingInSection: List<StudentRecord>,
        newRollNumber: String,
        excludeStudentId: String? = null
    ): Map<String, String> {
        val targetNum = newRollNumber.trim().toIntOrNull() ?: return emptyMap()
        val activePeers = existingInSection
            .filter { it.id != excludeStudentId && it.status == "active" }
            .mapNotNull { stu ->
                val num = stu.rollNumber.trim().toIntOrNull()
                if (num != null && num >= targetNum) stu to num else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.fullName.lowercase(Locale.US) }))

        if (activePeers.isEmpty()) return emptyMap()

        val shifts = linkedMapOf<String, String>()
        var occupiedRoll = targetNum
        for ((stu, currentNum) in activePeers) {
            if (currentNum <= occupiedRoll) {
                val nextNum = occupiedRoll + 1
                shifts[stu.id] = nextNum.toString()
                occupiedRoll = nextNum
            } else {
                break
            }
        }
        return shifts
    }

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
     * Computes TWO separate attendance statistics for a student:
     * 1. Daily Attendance: Aggregates all sessions on the same calendar date into ONE school day.
     * 2. Period/Subject Attendance: Counts every individual attendance session separately.
     */
    fun computeStudentAttendanceSummary(
        studentId: String,
        sessions: List<AttendanceSession>,
        records: List<AttendanceRecord>
    ): StudentAttendanceSummary {
        val sessionMap = sessions.associateBy { it.id }
        val studentRecords = records.filter { it.studentId == studentId && sessionMap.containsKey(it.attendanceSessionId) }

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

        val recordsByDate = studentRecords.groupBy { rec ->
            formatDisplayDate(sessionMap[rec.attendanceSessionId]?.attendanceDate)
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
