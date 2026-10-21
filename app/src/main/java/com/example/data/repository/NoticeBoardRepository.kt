package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.AttachmentEntity
import com.example.data.model.CollegeSettingsEntity
import com.example.data.model.DepartmentEntity
import com.example.data.model.NoticeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserPreferenceEntity
import com.example.data.security.SecurityUtils
import kotlinx.coroutines.flow.Flow

class NoticeBoardRepository(private val database: AppDatabase) {
    private val userDao = database.userDao()
    private val departmentDao = database.departmentDao()
    private val noticeDao = database.noticeDao()
    private val attachmentDao = database.attachmentDao()
    private val preferenceDao = database.preferenceDao()
    private val settingsDao = database.collegeSettingsDao()

    // ---------------- NOTICES ----------------
    fun getAllNotices(): Flow<List<NoticeEntity>> = noticeDao.getAllNotices()

    fun getActiveNotices(now: Long = System.currentTimeMillis()): Flow<List<NoticeEntity>> =
        noticeDao.getActiveNotices(now)

    fun getPublicActiveNotices(now: Long = System.currentTimeMillis()): Flow<List<NoticeEntity>> =
        noticeDao.getPublicActiveNotices(now)

    fun getStudentActiveNotices(deptCode: String, now: Long = System.currentTimeMillis()): Flow<List<NoticeEntity>> =
        noticeDao.getStudentActiveNotices(deptCode, now)

    fun getNoticesByAuthor(authorId: Long): Flow<List<NoticeEntity>> =
        noticeDao.getNoticesByAuthor(authorId)

    fun getNoticeById(id: Long): Flow<NoticeEntity?> = noticeDao.getNoticeById(id)

    suspend fun insertNotice(notice: NoticeEntity): Long = noticeDao.insertNotice(notice)

    suspend fun updateNotice(notice: NoticeEntity) = noticeDao.updateNotice(notice)

    suspend fun deleteNoticeById(id: Long) {
        attachmentDao.deleteAttachmentsForNotice(id)
        noticeDao.deleteNoticeById(id)
    }

    // ---------------- ATTACHMENTS ----------------
    fun getAttachmentsForNotice(noticeId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForNotice(noticeId)

    suspend fun insertAttachment(attachment: AttachmentEntity): Long =
        attachmentDao.insertAttachment(attachment)

    suspend fun deleteAttachmentById(id: Long) = attachmentDao.deleteAttachmentById(id)

    // ---------------- DEPARTMENTS ----------------
    fun getAllDepartments(): Flow<List<DepartmentEntity>> = departmentDao.getAllDepartments()

    fun getActiveDepartments(): Flow<List<DepartmentEntity>> = departmentDao.getActiveDepartments()

    suspend fun insertDepartment(dept: DepartmentEntity): Long = departmentDao.insertDepartment(dept)

    suspend fun updateDepartment(dept: DepartmentEntity) = departmentDao.updateDepartment(dept)

    suspend fun deleteDepartment(dept: DepartmentEntity) = departmentDao.deleteDepartment(dept)

    // ---------------- USERS & AUTH ----------------
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllUsers()

    fun getUserById(id: Long): Flow<UserEntity?> = userDao.getUserById(id)

    suspend fun authenticate(loginId: String, passwordPlain: String): UserEntity? {
        val user = userDao.getUserByLoginId(loginId.trim()) ?: return null
        if (!user.isActive) return null
        return if (SecurityUtils.verifyPassword(passwordPlain, user.passwordHash)) {
            user
        } else {
            null
        }
    }

    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)

    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)

    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)

    // ---------------- PREFERENCES ----------------
    fun getPreferenceForUser(userId: Long): Flow<UserPreferenceEntity?> =
        preferenceDao.getPreferenceForUser(userId)

    suspend fun savePreference(preference: UserPreferenceEntity) =
        preferenceDao.savePreference(preference)

    // ---------------- SETTINGS ----------------
    fun getSettings(): Flow<CollegeSettingsEntity?> = settingsDao.getSettings()

    suspend fun saveSettings(settings: CollegeSettingsEntity) =
        settingsDao.saveSettings(settings)
}
