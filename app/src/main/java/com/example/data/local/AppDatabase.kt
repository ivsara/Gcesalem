package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.data.security.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [
        UserEntity::class,
        DepartmentEntity::class,
        NoticeEntity::class,
        AttachmentEntity::class,
        UserPreferenceEntity::class,
        CollegeSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun noticeDao(): NoticeDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun collegeSettingsDao(): CollegeSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val seedMutex = Mutex()

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gce_salem_noticeboard_v2.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun ensureSeeded(db: AppDatabase) {
            seedMutex.withLock {
                populateInitialData(db)
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val userCount = db.userDao().countUsers()
            if (userCount > 0) return

            // 1. Initial College Settings
            db.collegeSettingsDao().saveSettings(
                CollegeSettingsEntity(
                    id = 1,
                    collegeName = "Government College of Engineering, Salem",
                    collegeShortName = "GCE Salem",
                    defaultThemeDark = false,
                    defaultAccentColor = "#0B3C5D",
                    bannerNotice = "Autonomous Institution Affiliated to Anna University | Approved by AICTE, New Delhi | Accredited by NAAC",
                    refreshIntervalSeconds = 10
                )
            )

            // 2. Initial 7 Departments (CIVIL, MECH, ECE, EEE, CSE, METT, AIML)
            val departments = listOf(
                DepartmentEntity(name = "Civil Engineering", code = "CIVIL", description = "Department of Civil Engineering & Structural Engineering"),
                DepartmentEntity(name = "Mechanical Engineering", code = "MECH", description = "Department of Mechanical Engineering, Thermal & Manufacturing"),
                DepartmentEntity(name = "Electronics and Communication Engineering", code = "ECE", description = "Department of ECE, VLSI & Communication Systems"),
                DepartmentEntity(name = "Electrical and Electronics Engineering", code = "EEE", description = "Department of EEE, Power Systems & Control"),
                DepartmentEntity(name = "Computer Science and Engineering", code = "CSE", description = "Department of Computer Science and Engineering"),
                DepartmentEntity(name = "Metallurgical Engineering", code = "METT", description = "Department of Metallurgical Engineering & Materials Science"),
                DepartmentEntity(name = "Artificial Intelligence and Machine Learning", code = "AIML", description = "Department of Artificial Intelligence and Machine Learning")
            )
            departments.forEach { db.departmentDao().insertDepartment(it) }

            // 3. Initial Role-Only Users (No Personal Names)
            // Admin Principal (plus 'admin' alias)
            val principalHash = SecurityUtils.hashPassword("principal123")
            val adminHash = SecurityUtils.hashPassword("admin123")
            val hodHash = SecurityUtils.hashPassword("hod123")
            val facultyHash = SecurityUtils.hashPassword("faculty123")
            val studentHash = SecurityUtils.hashPassword("student123")

            val principalUser = UserEntity(
                loginId = "principal",
                passwordHash = principalHash,
                fullName = "Admin Principal",
                role = UserRole.ADMIN.name,
                departmentCode = "ALL",
                email = "principal@gcesalem.edu.in"
            )
            val principalId = db.userDao().insertUser(principalUser)

            db.userDao().insertUser(
                UserEntity(
                    loginId = "admin",
                    passwordHash = adminHash,
                    fullName = "Admin Principal",
                    role = UserRole.ADMIN.name,
                    departmentCode = "ALL",
                    email = "admin@gcesalem.edu.in"
                )
            )

            var hodCseId = principalId
            var facultyCse1Id = principalId
            var hodEceId = principalId

            departments.forEach { dept ->
                val dLower = dept.code.lowercase()

                // 1 HOD per department
                val hodId = db.userDao().insertUser(
                    UserEntity(
                        loginId = "hod_$dLower",
                        passwordHash = hodHash,
                        fullName = "HOD - ${dept.code}",
                        role = UserRole.HOD.name,
                        departmentCode = dept.code,
                        email = "hod.$dLower@gcesalem.edu.in"
                    )
                )
                if (dept.code == "CSE") hodCseId = hodId
                if (dept.code == "ECE") hodEceId = hodId

                // 3 Faculty per department
                for (i in 1..3) {
                    val facId = db.userDao().insertUser(
                        UserEntity(
                            loginId = "faculty_${dLower}$i",
                            passwordHash = facultyHash,
                            fullName = "Faculty $i - ${dept.code}",
                            role = UserRole.FACULTY.name,
                            departmentCode = dept.code,
                            email = "faculty$i.$dLower@gcesalem.edu.in"
                        )
                    )
                    if (dept.code == "CSE" && i == 1) facultyCse1Id = facId
                }

                // 10 Students per department
                for (s in 1..10) {
                    db.userDao().insertUser(
                        UserEntity(
                            loginId = "student_${dLower}$s",
                            passwordHash = studentHash,
                            fullName = "Student $s - ${dept.code}",
                            role = UserRole.STUDENT.name,
                            departmentCode = dept.code,
                            email = "student$s.$dLower@student.gcesalem.edu.in"
                        )
                    )
                }
            }

            // Legacy login aliases without numeric suffix for convenience
            departments.forEach { dept ->
                val dLower = dept.code.lowercase()
                db.userDao().insertUser(
                    UserEntity(
                        loginId = "faculty_$dLower",
                        passwordHash = facultyHash,
                        fullName = "Faculty 1 - ${dept.code}",
                        role = UserRole.FACULTY.name,
                        departmentCode = dept.code,
                        email = "faculty.$dLower@gcesalem.edu.in"
                    )
                )
                db.userDao().insertUser(
                    UserEntity(
                        loginId = "student_$dLower",
                        passwordHash = studentHash,
                        fullName = "Student 1 - ${dept.code}",
                        role = UserRole.STUDENT.name,
                        departmentCode = dept.code,
                        email = "student.$dLower@student.gcesalem.edu.in"
                    )
                )
            }

            // 4. Initial Sample Notices (Role-Only Authors)
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000

            // Notice 1: Exam Timetable (Pinned, High priority, PDF attachment)
            val n1 = NoticeEntity(
                id = 1,
                title = "End Semester Examination Schedule - Nov/Dec 2026 (Autonomous)",
                description = "Controller of Examinations (Autonomous) announces the official timetable for 3rd, 5th, and 7th Semester B.E. Degree Examinations across CIVIL, MECH, ECE, EEE, CSE, METT, and AIML. Hall tickets will be issued from department offices.",
                departmentCode = "ALL",
                category = NoticeCategory.EXAMINATION.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = principalId,
                authorName = "Admin Principal",
                authorRole = UserRole.ADMIN.name,
                publishedAt = now - (2 * dayMs),
                expiresAt = now + (30 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = true,
                visibility = NoticeVisibility.PUBLIC.name
            )
            val n1Id = db.noticeDao().insertNotice(n1)
            db.attachmentDao().insertAttachment(
                AttachmentEntity(
                    noticeId = n1Id,
                    originalFilename = "GCE_Salem_End_Sem_NovDec_2026_Schedule.pdf",
                    storedFilename = "attach_n1_timetable.pdf",
                    fileType = "PDF",
                    fileSizeBytes = 0L,
                    uploadedBy = "principal",
                    previewSnippet = "CONTROLLER OF EXAMINATIONS, GCE SALEM\nTheory Exam Starts: 15-Nov-2026\nForenoon Session: 09:30 AM to 12:30 PM\nAfternoon Session: 01:30 PM to 04:30 PM"
                )
            )

            // Notice 2: Placement Drive (Pinned, High, XLS attachment)
            val n2 = NoticeEntity(
                id = 2,
                title = "Mega Campus Placement Drive 2026: Zoho, TCS & Caterpillar",
                description = "Training & Placement Cell invites eligible Final Year B.E. students (CIVIL, MECH, ECE, EEE, CSE, METT, AIML) with 6.5+ CGPA and no standing arrears. Technical round and coding assessments will commence on Monday at the Central Computing Centre.",
                departmentCode = "ALL",
                category = NoticeCategory.PLACEMENTS.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = principalId,
                authorName = "Admin Principal",
                authorRole = UserRole.ADMIN.name,
                publishedAt = now - (1 * dayMs),
                expiresAt = now + (15 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = true,
                visibility = NoticeVisibility.PUBLIC.name
            )
            val n2Id = db.noticeDao().insertNotice(n2)
            db.attachmentDao().insertAttachment(
                AttachmentEntity(
                    noticeId = n2Id,
                    originalFilename = "Eligible_Candidates_Zoho_TCS_Drive_2026.xlsx",
                    storedFilename = "attach_n2_placements.xlsx",
                    fileType = "XLS",
                    fileSizeBytes = 0L,
                    uploadedBy = "principal",
                    previewSnippet = "Sheet1: Shortlisted Students List\nBranches: CIVIL, MECH, ECE, EEE, CSE, METT, AIML\nTotal Eligible: 342 Students"
                )
            )

            // Notice 3: SIH Hackathon (PDF circular)
            val n3 = NoticeEntity(
                id = 3,
                title = "Smart India Hackathon (SIH 2026) - Internal College Evaluation",
                description = "Department of CSE & AIML is hosting the Internal SIH 2026 Hackathon on Saturday at Seminar Hall 1. Teams of 6 members must submit problem statement solution presentations by Friday evening.",
                departmentCode = "CSE",
                category = NoticeCategory.EVENTS.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = hodCseId,
                authorName = "HOD - CSE",
                authorRole = UserRole.HOD.name,
                publishedAt = now - (3 * 3600 * 1000),
                expiresAt = now + (7 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = false,
                visibility = NoticeVisibility.PUBLIC.name
            )
            val n3Id = db.noticeDao().insertNotice(n3)
            db.attachmentDao().insertAttachment(
                AttachmentEntity(
                    noticeId = n3Id,
                    originalFilename = "SIH_2026_Internal_Evaluation_Circular.pdf",
                    storedFilename = "attach_n3_sih.pdf",
                    fileType = "PDF",
                    fileSizeBytes = 0L,
                    uploadedBy = "hod_cse",
                    previewSnippet = "SIH 2026 Internal Screening Circular\nVenue: CSE Lab 3 & Seminar Hall\nCoordinator: HOD - CSE"
                )
            )

            // Notice 4: Department Circular (Restricted, DOC attachment)
            val n4 = NoticeEntity(
                id = 4,
                title = "Lab Exam Cycle 2 & Model Practical Evaluation Schedule - CSE",
                description = "All 2nd and 3rd year CSE students are hereby informed that Model Practical Examinations for Data Structures, Database Systems, and Compiler Design will be held strictly according to the attached batch allotment.",
                departmentCode = "CSE",
                category = NoticeCategory.DEPARTMENT_CIRCULARS.displayName,
                priority = NoticePriority.MEDIUM.name,
                authorId = facultyCse1Id,
                authorName = "Faculty 1 - CSE",
                authorRole = UserRole.FACULTY.name,
                publishedAt = now - (12 * 3600 * 1000),
                expiresAt = now + (10 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = false,
                visibility = NoticeVisibility.RESTRICTED.name
            )
            val n4Id = db.noticeDao().insertNotice(n4)
            db.attachmentDao().insertAttachment(
                AttachmentEntity(
                    noticeId = n4Id,
                    originalFilename = "CSE_Model_Lab_Schedule_Batches.docx",
                    storedFilename = "attach_n4_labs.docx",
                    fileType = "DOC",
                    fileSizeBytes = 0L,
                    uploadedBy = "faculty_cse1",
                    previewSnippet = "Batch A: Student 1 - CSE to Student 5 - CSE\nBatch B: Student 6 - CSE to Student 10 - CSE\nLab Attire: Formal dress & Lab Coat mandatory."
                )
            )

            // Notice 5: ECE VLSI Workshop
            val n5 = NoticeEntity(
                id = 5,
                title = "Hands-on Workshop on FPGA & VLSI Design Flow - ECE",
                description = "Department of ECE is organizing a two-day practical workshop on Xilinx Vivado & ASIC design flow at the VLSI Design Laboratory.",
                departmentCode = "ECE",
                category = NoticeCategory.EVENTS.displayName,
                priority = NoticePriority.MEDIUM.name,
                authorId = hodEceId,
                authorName = "HOD - ECE",
                authorRole = UserRole.HOD.name,
                publishedAt = now - (20 * 3600 * 1000),
                expiresAt = now + (5 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = false,
                visibility = NoticeVisibility.PUBLIC.name
            )
            db.noticeDao().insertNotice(n5)

            // Notice 6: EXPIRED notice for demonstrating automatic notice expiry filtering
            val expiredNotice = NoticeEntity(
                id = 6,
                title = "Notice: Odd Semester Tuition Fee Payment (Without Late Fine)",
                description = "All students are instructed to pay their semester fee online via SBI Collect before the due date. Receipts must be produced during course registration.",
                departmentCode = "ALL",
                category = NoticeCategory.IMPORTANT_ANNOUNCEMENTS.displayName,
                priority = NoticePriority.MEDIUM.name,
                authorId = principalId,
                authorName = "Admin Principal",
                authorRole = UserRole.ADMIN.name,
                publishedAt = now - (20 * dayMs),
                expiresAt = now - (3 * dayMs), // EXPIRED 3 days ago
                status = NoticeStatus.PUBLISHED.name,
                isPinned = false,
                visibility = NoticeVisibility.PUBLIC.name
            )
            db.noticeDao().insertNotice(expiredNotice)
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    ensureSeeded(database)
                }
            }
        }
    }
}
