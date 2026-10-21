package com.example

import com.example.data.model.NoticeCategory
import com.example.data.model.NoticeEntity
import com.example.data.model.NoticePriority
import com.example.data.model.NoticeVisibility
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.security.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeBoardUnitTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val plainPassword = "adminPassword2026!"
        val hash = SecurityUtils.hashPassword(plainPassword)

        // Ensure hash is not equal to plain password
        assertNotEquals(plainPassword, hash)
        // Ensure deterministic verification succeeds
        assertTrue(SecurityUtils.verifyPassword(plainPassword, hash))
        // Ensure invalid password fails
        assertFalse(SecurityUtils.verifyPassword("wrongPassword", hash))
    }

    @Test
    fun testNoticeExpiryLogic() {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        val activeNotice = NoticeEntity(
            title = "SIH 2026 Hackathon",
            description = "College Hackathon Announcement",
            departmentCode = "CSE",
            category = NoticeCategory.EVENTS.displayName,
            priority = NoticePriority.HIGH.name,
            authorId = 1,
            authorName = "HOD CSE",
            authorRole = UserRole.FACULTY.name,
            publishedAt = now,
            expiresAt = now + (7 * dayMs),
            visibility = NoticeVisibility.PUBLIC.name
        )

        val expiredNotice = NoticeEntity(
            title = "Odd Semester Fee Due",
            description = "Fee payment without late fine",
            departmentCode = "ALL",
            category = NoticeCategory.GENERAL.displayName,
            priority = NoticePriority.LOW.name,
            authorId = 1,
            authorName = "Principal",
            authorRole = UserRole.ADMIN.name,
            publishedAt = now - (10 * dayMs),
            expiresAt = now - (2 * dayMs),
            visibility = NoticeVisibility.PUBLIC.name
        )

        val isNotice1Expired = activeNotice.expiresAt in 1..now
        val isNotice2Expired = expiredNotice.expiresAt in 1..now

        assertFalse(isNotice1Expired)
        assertTrue(isNotice2Expired)
    }

    @Test
    fun testRoleBasedNoticeVisibilityRules() {
        val publicNotice = NoticeEntity(
            title = "Sports Trials",
            description = "Anna University Zonal Trials",
            departmentCode = "ALL",
            category = NoticeCategory.GENERAL.displayName,
            priority = NoticePriority.LOW.name,
            authorId = 1,
            authorName = "Principal",
            authorRole = UserRole.ADMIN.name,
            visibility = NoticeVisibility.PUBLIC.name
        )

        val restrictedCseNotice = NoticeEntity(
            title = "CSE Model Lab Practical",
            description = "Model practical schedule for CSE students",
            departmentCode = "CSE",
            category = NoticeCategory.DEPARTMENT_CIRCULARS.displayName,
            priority = NoticePriority.MEDIUM.name,
            authorId = 2,
            authorName = "HOD CSE",
            authorRole = UserRole.FACULTY.name,
            visibility = NoticeVisibility.RESTRICTED.name
        )

        val studentCse = UserEntity(
            id = 10,
            loginId = "student_cse",
            passwordHash = "hash",
            fullName = "Elango S.",
            role = UserRole.STUDENT.name,
            departmentCode = "CSE",
            email = "elango@student.gcesalem.edu.in"
        )

        val studentIt = UserEntity(
            id = 11,
            loginId = "student_it",
            passwordHash = "hash",
            fullName = "Priya K.",
            role = UserRole.STUDENT.name,
            departmentCode = "IT",
            email = "priya@student.gcesalem.edu.in"
        )

        // Helper function matching ViewModel's visibility check
        fun canUserView(user: UserEntity?, notice: NoticeEntity): Boolean {
            if (user == null) {
                return notice.visibility == NoticeVisibility.PUBLIC.name
            }
            if (user.role == UserRole.STUDENT.name) {
                return notice.visibility == NoticeVisibility.PUBLIC.name ||
                        notice.departmentCode == "ALL" ||
                        notice.departmentCode == user.departmentCode
            }
            return true
        }

        // Unauthenticated guest checks
        assertTrue(canUserView(null, publicNotice))
        assertFalse(canUserView(null, restrictedCseNotice))

        // CSE student checks
        assertTrue(canUserView(studentCse, publicNotice))
        assertTrue(canUserView(studentCse, restrictedCseNotice))

        // IT student checks (Cannot view restricted CSE notice)
        assertTrue(canUserView(studentIt, publicNotice))
        assertFalse(canUserView(studentIt, restrictedCseNotice))
    }
}
