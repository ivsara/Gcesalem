package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AttachmentEntity
import com.example.data.model.CollegeSettingsEntity
import com.example.data.model.DepartmentEntity
import com.example.data.model.NoticeCategory
import com.example.data.model.NoticeEntity
import com.example.data.model.NoticePriority
import com.example.data.model.NoticeStatus
import com.example.data.model.NoticeVisibility
import com.example.data.model.UserEntity
import com.example.data.model.UserPreferenceEntity
import com.example.data.model.UserRole
import com.example.data.remote.CloudNoticeSync
import com.example.data.repository.NoticeBoardRepository
import com.example.data.security.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoticeBoardViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application, viewModelScope)
    private val repository: NoticeBoardRepository = NoticeBoardRepository(database)
    private var pollingJob: Job? = null

    // Auth State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Login Error Message
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Filters and Search
    val searchQuery = MutableStateFlow("")
    val selectedDepartmentFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter = MutableStateFlow("ALL")
    val showExpiredOnly = MutableStateFlow(false)

    // Polling & Live Updates
    private val _lastRefreshedAt = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshedAt: StateFlow<Long> = _lastRefreshedAt.asStateFlow()

    private val _isPollingActive = MutableStateFlow(true)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    // College Settings
    val collegeSettings: StateFlow<CollegeSettingsEntity?> = repository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Departments
    val allDepartments: StateFlow<List<DepartmentEntity>> = repository.getAllDepartments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDepartments: StateFlow<List<DepartmentEntity>> = repository.getActiveDepartments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Users (for admin)
    val allUsers: StateFlow<List<UserEntity>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Raw Notices Flow
    private val rawNotices: StateFlow<List<NoticeEntity>> = repository.getAllNotices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class FilterCriteria(
        val user: UserEntity?,
        val query: String,
        val deptFilter: String,
        val catFilter: String,
        val expiredOnly: Boolean
    )

    private val filterCriteria = combine(
        _currentUser,
        searchQuery,
        selectedDepartmentFilter,
        selectedCategoryFilter,
        showExpiredOnly
    ) { user, query, deptFilter, catFilter, expiredOnly ->
        FilterCriteria(user, query, deptFilter, catFilter, expiredOnly)
    }

    // Filtered Notices Flow based on Role, Department, Category, Search, and Expiry
    val filteredNotices: StateFlow<List<NoticeEntity>> = combine(
        rawNotices,
        filterCriteria,
        _lastRefreshedAt
    ) { notices, criteria, currentTime ->
        notices.filter { notice ->
            val isExpired = notice.expiresAt in 1..currentTime
            val user = criteria.user

            // Expiry filter rule
            if (criteria.expiredOnly) {
                if (!isExpired) return@filter false
            } else {
                if (isExpired && (user?.role != UserRole.ADMIN.name)) {
                    // Non-admins never see expired notices in active listings
                    return@filter false
                }
            }

            // Role & Visibility Permission Rule
            if (user == null) {
                // Public user: Only PUBLIC notices
                if (notice.visibility != NoticeVisibility.PUBLIC.name) return@filter false
            } else if (user.role == UserRole.STUDENT.name) {
                // Student: sees PUBLIC notices OR RESTRICTED notices targeted to their department / ALL
                val canView = notice.visibility == NoticeVisibility.PUBLIC.name ||
                        notice.departmentCode == "ALL" ||
                        notice.departmentCode == user.departmentCode
                if (!canView) return@filter false
            } else if (user.role == UserRole.FACULTY.name || user.role == UserRole.HOD.name) {
                // HOD & Faculty: sees PUBLIC, ALL dept, their dept notices, or notices they authored
                val canView = notice.visibility == NoticeVisibility.PUBLIC.name ||
                        notice.departmentCode == "ALL" ||
                        notice.departmentCode == user.departmentCode ||
                        notice.authorId == user.id
                if (!canView) return@filter false
            }
            // Admin Principal can view all notices

            // Department filter
            if (criteria.deptFilter != "ALL" && notice.departmentCode != criteria.deptFilter && notice.departmentCode != "ALL") {
                return@filter false
            }

            // Category filter
            if (criteria.catFilter != "ALL" && notice.category != criteria.catFilter) {
                return@filter false
            }

            // Keyword Search (title, description, author, category)
            if (criteria.query.isNotBlank()) {
                val q = criteria.query.trim().lowercase()
                val match = notice.title.lowercase().contains(q) ||
                        notice.description.lowercase().contains(q) ||
                        notice.authorName.lowercase().contains(q) ||
                        notice.category.lowercase().contains(q) ||
                        notice.departmentCode.lowercase().contains(q)
                if (!match) return@filter false
            }

            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User Preferences (theme, colors, font scale)
    private val _userPreference = MutableStateFlow(
        UserPreferenceEntity(
            userId = 0,
            isDarkMode = false,
            accentColorHex = "#0B3C5D",
            fontSizeScale = 1.0f,
            cardStyle = "ELEVATED"
        )
    )
    val userPreference: StateFlow<UserPreferenceEntity> = _userPreference.asStateFlow()

    init {
        CloudNoticeSync.init(application)
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureSeeded(database)
            CloudNoticeSync.pullAndApplyRemoteNotices(repository)
            _lastRefreshedAt.value = System.currentTimeMillis()
        }
        startPolling()
    }

    // ---------------- AUTHENTICATION ----------------

    fun login(loginId: String, passwordPlain: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            val user = repository.authenticate(loginId, passwordPlain)
            if (user != null) {
                _currentUser.value = user
                loadUserPreferences(user.id)
                onResult(true)
            } else {
                _authError.value = "Invalid login ID or password. Please try again."
                onResult(false)
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun resetPassword(
        loginId: String,
        newPasswordPlain: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _authError.value = null
            val success = repository.resetPassword(loginId, newPasswordPlain)
            if (success) {
                CloudNoticeSync.pushPasswordReset(loginId.trim(), newPasswordPlain)
                onResult(true, "✓ Password reset successful for ${loginId.trim()}. Please sign in with your new password.")
            } else {
                onResult(false, "Account not found or inactive for Login ID '${loginId.trim()}'.")
            }
        }
    }

    fun switchToUser(user: UserEntity?) {
        _currentUser.value = user
        if (user != null) {
            loadUserPreferences(user.id)
        }
    }

    fun logout() {
        _currentUser.value = null
        _authError.value = null
        _userPreference.value = UserPreferenceEntity(
            userId = 0,
            isDarkMode = collegeSettings.value?.defaultThemeDark ?: false,
            accentColorHex = collegeSettings.value?.defaultAccentColor ?: "#0B3C5D",
            fontSizeScale = 1.0f,
            cardStyle = "ELEVATED"
        )
    }

    private fun loadUserPreferences(userId: Long) {
        viewModelScope.launch {
            repository.getPreferenceForUser(userId).collect { pref ->
                if (pref != null) {
                    _userPreference.value = pref
                } else {
                    _userPreference.value = UserPreferenceEntity(
                        userId = userId,
                        isDarkMode = collegeSettings.value?.defaultThemeDark ?: false,
                        accentColorHex = collegeSettings.value?.defaultAccentColor ?: "#0B3C5D"
                    )
                }
            }
        }
    }

    // ---------------- REAL-TIME POLLING & CROSS-PHONE SYNC ----------------

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val intervalSec = (collegeSettings.value?.refreshIntervalSeconds ?: 10).coerceIn(5, 60)
                delay(intervalSec * 1000L)
                if (_isPollingActive.value) {
                    CloudNoticeSync.pullAndApplyRemoteNotices(repository)
                    _lastRefreshedAt.value = System.currentTimeMillis()
                }
            }
        }
    }

    fun togglePolling(enabled: Boolean) {
        _isPollingActive.value = enabled
        if (enabled) {
            manualRefresh()
        }
    }

    fun manualRefresh(onDone: ((Int) -> Unit)? = null) {
        _lastRefreshedAt.value = System.currentTimeMillis()
        viewModelScope.launch {
            val count = CloudNoticeSync.pullAndApplyRemoteNotices(repository)
            _lastRefreshedAt.value = System.currentTimeMillis()
            onDone?.invoke(count)
        }
    }

    // ---------------- NOTICE MANAGEMENT ----------------

    fun createNotice(
        title: String,
        description: String,
        departmentCode: String,
        category: String,
        priority: String,
        expiryDays: Int,
        isPinned: Boolean,
        visibility: String,
        attachments: List<AttachmentEntity>,
        onComplete: (Boolean) -> Unit
    ) {
        val user = _currentUser.value ?: return onComplete(false)

        // Validation & permissions check: ADMIN, HOD, and FACULTY can publish
        if (user.role == UserRole.STUDENT.name) return onComplete(false)
        if ((user.role == UserRole.FACULTY.name || user.role == UserRole.HOD.name) &&
            departmentCode != "ALL" && departmentCode != user.departmentCode
        ) {
            return onComplete(false)
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expiresAt = if (expiryDays > 0) now + (expiryDays * 24L * 60 * 60 * 1000) else 0L

            val notice = NoticeEntity(
                id = now,
                title = title.trim(),
                description = description.trim(),
                departmentCode = departmentCode,
                category = category,
                priority = priority,
                authorId = user.id,
                authorName = user.fullName,
                authorRole = user.role,
                publishedAt = now,
                expiresAt = expiresAt,
                status = NoticeStatus.PUBLISHED.name,
                isPinned = isPinned,
                visibility = visibility
            )
            val noticeId = repository.insertNotice(notice)
            val savedNotice = notice.copy(id = noticeId)

            val savedAttachments = attachments.map { att ->
                val toInsert = att.copy(noticeId = noticeId, uploadedBy = user.loginId)
                val attId = repository.insertAttachment(toInsert)
                toInsert.copy(id = attId)
            }

            CloudNoticeSync.pushUpsert(
                notice = savedNotice,
                authorLoginId = user.loginId,
                attachments = savedAttachments
            )

            _lastRefreshedAt.value = System.currentTimeMillis()
            onComplete(true)
        }
    }

    fun updateNotice(
        notice: NoticeEntity,
        newAttachments: List<AttachmentEntity>,
        onComplete: (Boolean) -> Unit
    ) {
        val user = _currentUser.value ?: return onComplete(false)

        // Permissions check: Admins can edit any; HOD can edit their department's notices; Faculty can edit their own
        if (user.role == UserRole.FACULTY.name && notice.authorId != user.id) {
            return onComplete(false)
        }
        if (user.role == UserRole.HOD.name && notice.departmentCode != user.departmentCode && notice.authorId != user.id) {
            return onComplete(false)
        }

        viewModelScope.launch {
            val updated = notice.copy(updatedAt = System.currentTimeMillis())
            repository.updateNotice(updated)
            val savedAtts = mutableListOf<AttachmentEntity>()
            newAttachments.forEach { att ->
                if (att.id == 0L) {
                    val toInsert = att.copy(noticeId = notice.id, uploadedBy = user.loginId)
                    val attId = repository.insertAttachment(toInsert)
                    savedAtts.add(toInsert.copy(id = attId))
                } else {
                    savedAtts.add(att)
                }
            }
            CloudNoticeSync.pushUpsert(
                notice = updated,
                authorLoginId = user.loginId,
                attachments = savedAtts
            )
            _lastRefreshedAt.value = System.currentTimeMillis()
            onComplete(true)
        }
    }

    fun deleteNotice(noticeId: Long, authorId: Long, onComplete: (Boolean) -> Unit) {
        val user = _currentUser.value ?: return onComplete(false)
        if (user.role == UserRole.FACULTY.name && authorId != user.id) {
            return onComplete(false)
        }
        viewModelScope.launch {
            repository.deleteNoticeById(noticeId)
            CloudNoticeSync.pushDelete(noticeId)
            _lastRefreshedAt.value = System.currentTimeMillis()
            onComplete(true)
        }
    }

    fun getAttachmentsForNotice(noticeId: Long) = repository.getAttachmentsForNotice(noticeId)

    fun deleteAttachment(attachmentId: Long) {
        viewModelScope.launch {
            repository.deleteAttachmentById(attachmentId)
        }
    }

    // ---------------- USER MANAGEMENT (ADMIN ONLY) ----------------

    fun createUser(
        loginId: String,
        passwordPlain: String,
        fullName: String,
        role: String,
        deptCode: String,
        email: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) {
            return onComplete(false, "Unauthorized. Only administrators can create users.")
        }

        viewModelScope.launch {
            try {
                val newUser = UserEntity(
                    loginId = loginId.trim(),
                    passwordHash = SecurityUtils.hashPassword(passwordPlain),
                    fullName = fullName.trim(),
                    role = role,
                    departmentCode = deptCode,
                    email = email.trim(),
                    isActive = true
                )
                repository.insertUser(newUser)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "User creation failed")
            }
        }
    }

    fun toggleUserActive(user: UserEntity) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repository.updateUser(user.copy(isActive = !user.isActive))
        }
    }

    fun updateUserRole(user: UserEntity, newRole: String, newDept: String) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repository.updateUser(user.copy(role = newRole, departmentCode = newDept))
        }
    }

    fun deleteUser(user: UserEntity) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repository.deleteUser(user)
        }
    }

    // ---------------- DEPARTMENT MANAGEMENT ----------------

    fun createDepartment(name: String, code: String, description: String, onComplete: (Boolean) -> Unit) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return onComplete(false)
        viewModelScope.launch {
            try {
                repository.insertDepartment(
                    DepartmentEntity(
                        name = name.trim(),
                        code = code.trim().uppercase(),
                        description = description.trim(),
                        isActive = true
                    )
                )
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }

    fun toggleDepartmentActive(dept: DepartmentEntity) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repository.updateDepartment(dept.copy(isActive = !dept.isActive))
        }
    }

    // ---------------- PREFERENCES & THEMES ----------------

    fun updatePreferences(
        isDark: Boolean,
        accentColorHex: String,
        fontSizeScale: Float,
        cardStyle: String
    ) {
        val currentUserId = _currentUser.value?.id ?: 0L
        val updated = UserPreferenceEntity(
            userId = currentUserId,
            isDarkMode = isDark,
            accentColorHex = accentColorHex,
            fontSizeScale = fontSizeScale,
            cardStyle = cardStyle,
            updatedAt = System.currentTimeMillis()
        )
        _userPreference.value = updated

        if (currentUserId > 0) {
            viewModelScope.launch {
                repository.savePreference(updated)
            }
        }
    }

    fun resetPreferencesToDefault() {
        val defaultDark = collegeSettings.value?.defaultThemeDark ?: false
        val defaultAccent = collegeSettings.value?.defaultAccentColor ?: "#0B3C5D"
        updatePreferences(
            isDark = defaultDark,
            accentColorHex = defaultAccent,
            fontSizeScale = 1.0f,
            cardStyle = "ELEVATED"
        )
    }

    fun updateCollegeSettings(
        banner: String,
        refreshInterval: Int,
        defaultDark: Boolean,
        defaultAccent: String
    ) {
        if (_currentUser.value?.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            val current = collegeSettings.value ?: CollegeSettingsEntity()
            repository.saveSettings(
                current.copy(
                    bannerNotice = banner,
                    refreshIntervalSeconds = refreshInterval,
                    defaultThemeDark = defaultDark,
                    defaultAccentColor = defaultAccent
                )
            )
            startPolling()
        }
    }
}
