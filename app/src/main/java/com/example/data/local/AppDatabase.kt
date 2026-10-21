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

@Database(
    entities = [
        UserEntity::class,
        DepartmentEntity::class,
        NoticeEntity::class,
        AttachmentEntity::class,
        UserPreferenceEntity::class,
        CollegeSettingsEntity::class
    ],
    version = 1,
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

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gce_salem_noticeboard.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
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

            // 2. Initial Sample Departments
            val departments = listOf(
                DepartmentEntity(name = "Computer Science and Engineering", code = "CSE", description = "Department of CSE, AI & Data Science"),
                DepartmentEntity(name = "Information Technology", code = "IT", description = "Department of Information Technology"),
                DepartmentEntity(name = "Electronics and Communication Engineering", code = "ECE", description = "Department of ECE, VLSI & Embedded Systems"),
                DepartmentEntity(name = "Electrical and Electronics Engineering", code = "EEE", description = "Department of EEE, Power Systems"),
                DepartmentEntity(name = "Mechanical Engineering", code = "ME", description = "Department of Mechanical Engineering, Robotics & CAD/CAM"),
                DepartmentEntity(name = "Civil Engineering", code = "CE", description = "Department of Civil Engineering & Structural Analysis")
            )
            departments.forEach { db.departmentDao().insertDepartment(it) }

            // 3. Initial Users with Secure Hashed Passwords
            val adminUser = UserEntity(
                loginId = "admin",
                passwordHash = SecurityUtils.hashPassword("admin123"),
                fullName = "Dr. R. Vijayakumar (Principal & Admin)",
                role = UserRole.ADMIN.name,
                departmentCode = "ALL",
                email = "principal@gcesalem.edu.in"
            )
            val adminId = db.userDao().insertUser(adminUser)

            val facultyCse = UserEntity(
                loginId = "faculty_cse",
                passwordHash = SecurityUtils.hashPassword("faculty123"),
                fullName = "Prof. K. Sundararajan (HOD CSE)",
                role = UserRole.FACULTY.name,
                departmentCode = "CSE",
                email = "hod.cse@gcesalem.edu.in"
            )
            val facultyCseId = db.userDao().insertUser(facultyCse)

            val facultyEce = UserEntity(
                loginId = "faculty_ece",
                passwordHash = SecurityUtils.hashPassword("faculty123"),
                fullName = "Dr. M. Geetha (Associate Prof. ECE)",
                role = UserRole.FACULTY.name,
                departmentCode = "ECE",
                email = "geetha.ece@gcesalem.edu.in"
            )
            db.userDao().insertUser(facultyEce)

            val studentCse = UserEntity(
                loginId = "student_cse",
                passwordHash = SecurityUtils.hashPassword("student123"),
                fullName = "Elango S. (Reg: 737722104012)",
                role = UserRole.STUDENT.name,
                departmentCode = "CSE",
                email = "elango.cse@student.gcesalem.edu.in"
            )
            db.userDao().insertUser(studentCse)

            val studentIt = UserEntity(
                loginId = "student_it",
                passwordHash = SecurityUtils.hashPassword("student123"),
                fullName = "Priya Dharshini K. (Reg: 737722105028)",
                role = UserRole.STUDENT.name,
                departmentCode = "IT",
                email = "priya.it@student.gcesalem.edu.in"
            )
            db.userDao().insertUser(studentIt)

            // 4. Initial Sample Notices
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000

            // Notice 1: Exam Timetable (Pinned, High priority, PDF attachment)
            val n1 = NoticeEntity(
                title = "End Semester Examination Schedule - Nov/Dec 2026 (Autonomous)",
                description = "Controller of Examinations (Autonomous) announces the official timetable for 3rd, 5th, and 7th Semester B.E. Degree Examinations. Hall tickets will be issued from department offices. Students must carry college ID card and hall ticket to all examinations.",
                departmentCode = "ALL",
                category = NoticeCategory.EXAMINATION.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = adminId,
                authorName = "Dr. R. Vijayakumar (Principal & Admin)",
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
                    fileSizeBytes = 1420500, // 1.4 MB
                    uploadedBy = "admin",
                    previewSnippet = "CONTROLLER OF EXAMINATIONS, GCE SALEM\nTheory Exam Starts: 15-Nov-2026\nForenoon Session: 09:30 AM to 12:30 PM\nAfternoon Session: 01:30 PM to 04:30 PM"
                )
            )

            // Notice 2: Placement Drive (Pinned, High, XLS attachment)
            val n2 = NoticeEntity(
                title = "Mega Campus Placement Drive 2026: Zoho, TCS & Caterpillar",
                description = "Training & Placement Cell invites eligible Final Year B.E. students (CSE, IT, ECE, EEE, ME, CE) with 6.5+ CGPA and no standing arrears. Technical round and coding assessments will commence on Monday at the Central Computing Centre.",
                departmentCode = "ALL",
                category = NoticeCategory.PLACEMENTS.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = adminId,
                authorName = "Dr. R. Vijayakumar (Principal & Admin)",
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
                    fileSizeBytes = 582400, // 582 KB
                    uploadedBy = "admin",
                    previewSnippet = "Sheet1: Shortlisted Students List\nColumns: RegNo, Name, Branch, CGPA, TestSlot\nTotal Eligible: 342 Students"
                )
            )

            // Notice 3: SIH Hackathon (Image poster)
            val n3 = NoticeEntity(
                title = "Smart India Hackathon (SIH 2026) - Internal College Evaluation",
                description = "Department of CSE is hosting the Internal SIH 2026 Hackathon on Saturday at Seminar Hall 1. Teams of 6 members (with at least one female teammate) must submit problem statement solution presentations by Friday evening.",
                departmentCode = "CSE",
                category = NoticeCategory.EVENTS.displayName,
                priority = NoticePriority.HIGH.name,
                authorId = facultyCseId,
                authorName = "Prof. K. Sundararajan (HOD CSE)",
                authorRole = UserRole.FACULTY.name,
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
                    originalFilename = "SIH_2026_Internal_Poster.png",
                    storedFilename = "attach_n3_sih.png",
                    fileType = "IMAGE",
                    fileSizeBytes = 2140000,
                    uploadedBy = "faculty_cse",
                    previewSnippet = "SIH 2026 Internal Screening Poster\nVenue: CSE Lab 3 & Seminar Hall\nJudges: Alumni Industry Experts"
                )
            )

            // Notice 4: Department Circular (Restricted, DOC attachment)
            val n4 = NoticeEntity(
                title = "Lab Exam Cycle 2 & Model Practical Evaluation Schedule - CSE",
                description = "All 2nd and 3rd year CSE students are hereby informed that Model Practical Examinations for Data Structures, Database Systems, and Compiler Design will be held strictly according to the attached batch allotment.",
                departmentCode = "CSE",
                category = NoticeCategory.DEPARTMENT_CIRCULARS.displayName,
                priority = NoticePriority.MEDIUM.name,
                authorId = facultyCseId,
                authorName = "Prof. K. Sundararajan (HOD CSE)",
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
                    fileSizeBytes = 345000,
                    uploadedBy = "faculty_cse",
                    previewSnippet = "Batch A: Reg 737722104001 to 737722104030\nBatch B: Reg 737722104031 to 737722104060\nLab Attire: Formal dress & Lab Coat mandatory."
                )
            )

            // Notice 5: Anna University Sports Zonal
            val n5 = NoticeEntity(
                title = "Anna University Zonal Inter-Collegiate Cricket & Badminton Trials",
                description = "Physical Education Department will conduct selection trials for the College Men & Women teams on Monday 4:00 PM at the College Sports Complex. Interested sports enthusiasts are requested to report with sports kit.",
                departmentCode = "ALL",
                category = NoticeCategory.GENERAL.displayName,
                priority = NoticePriority.LOW.name,
                authorId = adminId,
                authorName = "Dr. R. Vijayakumar (Principal & Admin)",
                authorRole = UserRole.ADMIN.name,
                publishedAt = now - (20 * 3600 * 1000),
                expiresAt = now + (5 * dayMs),
                status = NoticeStatus.PUBLISHED.name,
                isPinned = false,
                visibility = NoticeVisibility.PUBLIC.name
            )
            db.noticeDao().insertNotice(n5)

            // Notice 6: EXPIRED notice for demonstrating automatic notice expiry filtering
            val expiredNotice = NoticeEntity(
                title = "Notice: Odd Semester Tuition Fee Payment (Without Late Fine)",
                description = "All students are instructed to pay their semester fee online via SBI Collect before the due date. Receipts must be produced during course registration.",
                departmentCode = "ALL",
                category = NoticeCategory.IMPORTANT_ANNOUNCEMENTS.displayName,
                priority = NoticePriority.MEDIUM.name,
                authorId = adminId,
                authorName = "Dr. R. Vijayakumar (Principal & Admin)",
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
}
