package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AttachmentEntity
import com.example.data.model.CollegeSettingsEntity
import com.example.data.model.DepartmentEntity
import com.example.data.model.NoticeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY fullName ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserById(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE loginId = :loginId LIMIT 1")
    suspend fun getUserByLoginId(loginId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int
}

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments ORDER BY code ASC")
    fun getAllDepartments(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE isActive = 1 ORDER BY code ASC")
    fun getActiveDepartments(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE code = :code LIMIT 1")
    suspend fun getDepartmentByCode(code: String): DepartmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(dept: DepartmentEntity): Long

    @Update
    suspend fun updateDepartment(dept: DepartmentEntity)

    @Delete
    suspend fun deleteDepartment(dept: DepartmentEntity)

    @Query("SELECT COUNT(*) FROM departments")
    suspend fun countDepartments(): Int
}

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY isPinned DESC, publishedAt DESC")
    fun getAllNotices(): Flow<List<NoticeEntity>>

    /**
     * Active notices: not expired (either expiresAt == 0 or expiresAt > now) and status == 'PUBLISHED'
     */
    @Query("""
        SELECT * FROM notices 
        WHERE status = 'PUBLISHED' 
          AND (expiresAt = 0 OR expiresAt > :currentTimeMs)
        ORDER BY isPinned DESC, publishedAt DESC
    """)
    fun getActiveNotices(currentTimeMs: Long): Flow<List<NoticeEntity>>

    /**
     * Public active notices (available without authentication)
     */
    @Query("""
        SELECT * FROM notices 
        WHERE status = 'PUBLISHED' 
          AND visibility = 'PUBLIC'
          AND (expiresAt = 0 OR expiresAt > :currentTimeMs)
        ORDER BY isPinned DESC, publishedAt DESC
    """)
    fun getPublicActiveNotices(currentTimeMs: Long): Flow<List<NoticeEntity>>

    /**
     * Notices for student of department: public notices OR notices for that specific dept or ALL
     */
    @Query("""
        SELECT * FROM notices 
        WHERE status = 'PUBLISHED' 
          AND (departmentCode = 'ALL' OR departmentCode = :deptCode)
          AND (expiresAt = 0 OR expiresAt > :currentTimeMs)
        ORDER BY isPinned DESC, publishedAt DESC
    """)
    fun getStudentActiveNotices(deptCode: String, currentTimeMs: Long): Flow<List<NoticeEntity>>

    @Query("SELECT * FROM notices WHERE authorId = :authorId ORDER BY publishedAt DESC")
    fun getNoticesByAuthor(authorId: Long): Flow<List<NoticeEntity>>

    @Query("SELECT * FROM notices WHERE id = :id LIMIT 1")
    fun getNoticeById(id: Long): Flow<NoticeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: NoticeEntity): Long

    @Update
    suspend fun updateNotice(notice: NoticeEntity)

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteNoticeById(id: Long)

    @Query("SELECT COUNT(*) FROM notices")
    suspend fun countNotices(): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noticeId = :noticeId ORDER BY id ASC")
    fun getAttachmentsForNotice(noticeId: Long): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)

    @Query("DELETE FROM attachments WHERE noticeId = :noticeId")
    suspend fun deleteAttachmentsForNotice(noticeId: Long)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    fun getPreferenceForUser(userId: Long): Flow<UserPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(preference: UserPreferenceEntity): Long
}

@Dao
interface CollegeSettingsDao {
    @Query("SELECT * FROM college_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<CollegeSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: CollegeSettingsEntity)

    @Query("SELECT COUNT(*) FROM college_settings")
    suspend fun countSettings(): Int
}
