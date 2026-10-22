package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole {
    ADMIN,
    HOD,
    FACULTY,
    STUDENT
}

enum class NoticeCategory(val displayName: String) {
    EXAMINATION("Examination"),
    EVENTS("Events"),
    PLACEMENTS("Placements"),
    GENERAL("General"),
    DEPARTMENT_CIRCULARS("Department Circulars"),
    IMPORTANT_ANNOUNCEMENTS("Important Announcements")
}

enum class NoticePriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class NoticeVisibility {
    PUBLIC,
    RESTRICTED
}

enum class NoticeStatus {
    PUBLISHED,
    ARCHIVED,
    DRAFT
}

enum class AttachmentType {
    PDF,
    IMAGE,
    DOC,
    XLS
}

@Entity(
    tableName = "users",
    indices = [Index(value = ["loginId"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loginId: String,
    val passwordHash: String,
    val fullName: String,
    val role: String, // ADMIN, HOD, FACULTY, STUDENT
    val departmentCode: String, // CIVIL, MECH, ECE, EEE, CSE, METT, AIML, or ALL
    val email: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "departments",
    indices = [Index(value = ["code"], unique = true)]
)
data class DepartmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String, // CIVIL, MECH, ECE, EEE, CSE, METT, AIML
    val description: String,
    val isActive: Boolean = true
)

@Entity(
    tableName = "notices",
    indices = [
        Index(value = ["departmentCode"]),
        Index(value = ["category"]),
        Index(value = ["isPinned"]),
        Index(value = ["publishedAt"])
    ]
)
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val departmentCode: String, // ALL or specific department
    val category: String, // NoticeCategory enum string
    val priority: String, // HIGH, MEDIUM, LOW
    val authorId: Long,
    val authorName: String,
    val authorRole: String,
    val publishedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L, // 0 means no expiry
    val status: String = NoticeStatus.PUBLISHED.name,
    val isPinned: Boolean = false,
    val visibility: String = NoticeVisibility.PUBLIC.name, // PUBLIC or RESTRICTED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["noticeId"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noticeId: Long,
    val originalFilename: String,
    val storedFilename: String,
    val fileType: String, // PDF, IMAGE, DOC, XLS
    val fileSizeBytes: Long,
    val uploadedBy: String,
    val previewSnippet: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_preferences",
    indices = [Index(value = ["userId"], unique = true)]
)
data class UserPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val isDarkMode: Boolean = false,
    val accentColorHex: String = "#0B3C5D", // GCE Blue
    val fontSizeScale: Float = 1.0f,
    val cardStyle: String = "ELEVATED", // ELEVATED, OUTLINED, COMPACT
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "college_settings")
data class CollegeSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val collegeName: String = "Government College of Engineering, Salem",
    val collegeShortName: String = "GCE Salem",
    val defaultThemeDark: Boolean = false,
    val defaultAccentColor: String = "#0B3C5D",
    val bannerNotice: String = "Autonomous Institution Affiliated to Anna University | Approved by AICTE",
    val refreshIntervalSeconds: Int = 10
)
