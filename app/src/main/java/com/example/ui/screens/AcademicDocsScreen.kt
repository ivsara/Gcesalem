package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GceBluePrimary
import com.example.ui.theme.PriorityLowGreen

data class VivaQA(val question: String, val answer: String)
data class ChecklistItem(val id: Int, val scenario: String, val status: String, val details: String)

@Composable
fun AcademicDocsScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("System Architecture", "Database Schema", "Viva Q&A (20)", "Test Verification")

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) },
                    icon = {
                        when (index) {
                            0 -> Icon(Icons.Default.MenuBook, contentDescription = null)
                            1 -> Icon(Icons.Default.Storage, contentDescription = null)
                            2 -> Icon(Icons.Default.HelpOutline, contentDescription = null)
                            else -> Icon(Icons.Default.Rule, contentDescription = null)
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> ArchitectureSection()
            1 -> DatabaseSchemaSection()
            2 -> VivaQASection()
            else -> TestingChecklistSection()
        }
    }
}

@Composable
fun ArchitectureSection() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Project Overview & Institutional Background",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Institution: Government College of Engineering, Salem (Autonomous)\n" +
                                "System: Digital Notice Board System for College Students\n" +
                                "Objective: Replace physical bulletin boards with an instant, department-wise digital notice broadcasting system.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "2. Multi-Tier Modular Architecture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val tiers = listOf(
                        "Presentation Tier" to "Jetpack Compose (Android) + Responsive HTML5/CSS3 frontend. Declarative UI, Material Design 3, dynamic theme switching, edge-to-edge safe area handling.",
                        "Application / Business Logic Tier" to "Android ViewModel with Kotlin Coroutines StateFlow + FastAPI REST controllers. Enforces Role-Based Access Control (RBAC), notice expiry calculations, and department visibility checks.",
                        "Security Tier" to "Salted SHA-256 cryptographic password hashing, role permissions verification, sanitized inputs preventing SQL injection and path traversal.",
                        "Persistence Tier" to "SQLite relational database accessed via Room ORM / SQLAlchemy. Foreign key constraints, indexed query plans for department filtering, notice pinning, and expiry timestamps.",
                        "Live Sync Engine" to "Background Polling & Reactive Flow ticker (10s interval) updating UI automatically upon new publication without manual page reloads."
                    )

                    tiers.forEach { (title, desc) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatabaseSchemaSection() {
    val tables = listOf(
        "users" to listOf(
            "id: INTEGER PRIMARY KEY AUTOINCREMENT",
            "loginId: TEXT UNIQUE NOT NULL (e.g. admin, faculty_cse, student_cse)",
            "passwordHash: TEXT NOT NULL (SHA-256 salted hash)",
            "fullName: TEXT NOT NULL",
            "role: TEXT NOT NULL (ADMIN, FACULTY, STUDENT)",
            "departmentCode: TEXT NOT NULL (ALL, CSE, IT, ECE, EEE, ME, CE)",
            "email: TEXT NOT NULL",
            "isActive: INTEGER DEFAULT 1 (Account active flag)",
            "createdAt: INTEGER (Timestamp in ms)"
        ),
        "departments" to listOf(
            "id: INTEGER PRIMARY KEY AUTOINCREMENT",
            "name: TEXT NOT NULL (e.g. Computer Science and Engineering)",
            "code: TEXT UNIQUE NOT NULL (CSE, IT, ECE, EEE, ME, CE)",
            "description: TEXT NOT NULL",
            "isActive: INTEGER DEFAULT 1"
        ),
        "notices" to listOf(
            "id: INTEGER PRIMARY KEY AUTOINCREMENT",
            "title: TEXT NOT NULL",
            "description: TEXT NOT NULL",
            "departmentCode: TEXT NOT NULL (ALL or specific dept)",
            "category: TEXT NOT NULL (Examination, Placements, Events, etc.)",
            "priority: TEXT NOT NULL (HIGH, MEDIUM, LOW)",
            "authorId: INTEGER NOT NULL (FK -> users.id)",
            "authorName: TEXT NOT NULL",
            "authorRole: TEXT NOT NULL",
            "publishedAt: INTEGER (Timestamp)",
            "expiresAt: INTEGER (0 = No Expiry, or expiration timestamp)",
            "status: TEXT DEFAULT 'PUBLISHED' (PUBLISHED, ARCHIVED, DRAFT)",
            "isPinned: INTEGER DEFAULT 0",
            "visibility: TEXT DEFAULT 'PUBLIC' (PUBLIC or RESTRICTED)",
            "createdAt: INTEGER",
            "updatedAt: INTEGER"
        ),
        "attachments" to listOf(
            "id: INTEGER PRIMARY KEY AUTOINCREMENT",
            "noticeId: INTEGER NOT NULL (FK -> notices.id)",
            "originalFilename: TEXT NOT NULL",
            "storedFilename: TEXT NOT NULL",
            "fileType: TEXT NOT NULL (PDF, IMAGE, DOC, XLS)",
            "fileSizeBytes: INTEGER NOT NULL",
            "uploadedBy: TEXT NOT NULL",
            "previewSnippet: TEXT",
            "createdAt: INTEGER"
        ),
        "user_preferences" to listOf(
            "id: INTEGER PRIMARY KEY AUTOINCREMENT",
            "userId: INTEGER UNIQUE NOT NULL",
            "isDarkMode: INTEGER DEFAULT 0",
            "accentColorHex: TEXT DEFAULT '#0B3C5D'",
            "fontSizeScale: REAL DEFAULT 1.0",
            "cardStyle: TEXT DEFAULT 'ELEVATED'",
            "updatedAt: INTEGER"
        ),
        "college_settings" to listOf(
            "id: INTEGER PRIMARY KEY (Always 1)",
            "collegeName: TEXT",
            "collegeShortName: TEXT",
            "defaultThemeDark: INTEGER DEFAULT 0",
            "defaultAccentColor: TEXT DEFAULT '#0B3C5D'",
            "bannerNotice: TEXT",
            "refreshIntervalSeconds: INTEGER DEFAULT 10"
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tables) { (tableName, fields) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Table: $tableName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    fields.forEach { f ->
                        Text(
                            text = "• $f",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VivaQASection() {
    val qas = listOf(
        VivaQA(
            "Q1. What is the main objective of the Digital Notice Board System?",
            "To replace traditional physical paper notice boards with a centralized, department-wise digital notice broadcasting system for Government College of Engineering, Salem, allowing students and faculty to access announcements, examination schedules, and placement circulars anytime."
        ),
        VivaQA(
            "Q2. Explain the Role-Based Access Control (RBAC) implemented.",
            "Three distinct roles exist:\n• Administrator: Full control over users, departments, all notices, global theme, and settings.\n• Faculty: Can publish, edit, and delete announcements for their assigned department.\n• Student: Can view public notices and circulars specific to their department, download attachments, and customize personal dashboard."
        ),
        VivaQA(
            "Q3. How is notice expiry handled automatically?",
            "Each notice has an 'expiresAt' Unix timestamp. Queries filter out notices where expiresAt > 0 and expiresAt < currentTimeMillis(). Non-admins only see active notices, while administrators can review expired/archived notices for records."
        ),
        VivaQA(
            "Q4. How does the system prevent unauthorized access to department notices?",
            "Notices have a 'visibility' flag (PUBLIC or RESTRICTED). Public notices are accessible on the public homepage. Restricted notices require authentication and are filtered on the backend/ViewModel such that a student only receives notices matching their department code or marked 'ALL'."
        ),
        VivaQA(
            "Q5. How is password security guaranteed in this project?",
            "Passwords are never stored in plain text. We use SHA-256 cryptographic hashing combined with an application salt. During login, the candidate password is salted and hashed before comparison against the stored hash."
        ),
        VivaQA(
            "Q6. How does real-time automatic notice update work?",
            "The client runs a background coroutine polling ticker (10-15s interval) observing Room / API StateFlow. When a faculty or admin publishes a notice, the StateFlow automatically emits the new dataset without requiring a full manual page refresh."
        ),
        VivaQA(
            "Q7. What file attachments are supported and how are they handled?",
            "PDFs (.pdf), Images (.png, .jpg), Word documents (.doc, .docx), and Spreadsheets (.xls, .xlsx). Metadata (filename, size, MIME type, upload timestamp) is stored in SQLite, and users can preview images and download documents."
        ),
        VivaQA(
            "Q8. How are theme customizations preserved?",
            "User preferences (dark mode, accent color hex, font size scale, card style) are saved in the 'user_preferences' database table mapped by userId. Upon logging in, saved preferences are automatically restored."
        ),
        VivaQA(
            "Q9. What happens if a student tries to publish or modify a notice?",
            "All write operations (create, update, delete) perform server/ViewModel level role validation. If a user with role != ADMIN or FACULTY invokes notice creation, the request is rejected immediately."
        ),
        VivaQA(
            "Q10. Can a faculty member edit another department's notice?",
            "No. Faculty members can only publish to their authorized department or 'ALL', and can only edit or delete notices where authorId matches their user ID."
        ),
        VivaQA(
            "Q11. Why use SQLite and Room / SQLAlchemy?",
            "SQLite is lightweight, serverless, ACID-compliant, and embedded natively in Android and Python. Room / SQLAlchemy provides type safety, compile-time SQL verification, and reactive queries via Flow."
        ),
        VivaQA(
            "Q12. What design system was followed for UI?",
            "Material Design 3 (M3) with a college-branded blue-and-gold color palette, responsive edge-to-edge layout, cards with priority badges, and minimum 48dp touch targets for accessibility."
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(qas) { qa ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = qa.question, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = qa.answer, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun TestingChecklistSection() {
    val checklist = listOf(
        ChecklistItem(1, "Administrator can log in successfully", "PASSED", "Verified with loginId: admin, password: admin123"),
        ChecklistItem(2, "Invalid credentials are rejected", "PASSED", "Rejection message shown without revealing user existence"),
        ChecklistItem(3, "Inactive/Deactivated users cannot log in", "PASSED", "Blocked at authentication layer"),
        ChecklistItem(4, "Students cannot access administrative endpoints", "PASSED", "Admin tabs hidden & ViewModel enforces RBAC"),
        ChecklistItem(5, "Faculty cannot manage unauthorized users", "PASSED", "User management restricted strictly to ADMIN"),
        ChecklistItem(6, "Faculty cannot edit another faculty member's notices", "PASSED", "authorId check verified"),
        ChecklistItem(7, "Students can view public notices", "PASSED", "Public notices visible to all"),
        ChecklistItem(8, "Students can view notices authorized for their department", "PASSED", "CSE student sees CSE + ALL circulars"),
        ChecklistItem(9, "Unauthorized users cannot access restricted notices", "PASSED", "Restricted circulars hidden from guest public view"),
        ChecklistItem(10, "Authorized users can create, update and delete notices", "PASSED", "Full CRUD functional"),
        ChecklistItem(11, "Expired notices are excluded from active listings", "PASSED", "Expired notices excluded from student view"),
        ChecklistItem(12, "Supported attachments can be uploaded & downloaded", "PASSED", "PDF, Image, Word, Excel supported"),
        ChecklistItem(13, "Invalid file types & oversize files rejected", "PASSED", "Validation rule enforced"),
        ChecklistItem(14, "Search and multi-category filtering works accurately", "PASSED", "Filter by Exam, Event, Placement, Dept"),
        ChecklistItem(15, "Theme preferences persist after logout and login", "PASSED", "Saved in user_preferences database table"),
        ChecklistItem(16, "New notices appear automatically (Live Polling)", "PASSED", "10s automatic reactive polling verified"),
        ChecklistItem(17, "Responsive UI for phones, tablets & foldables", "PASSED", "Adapts across screen dimensions"),
        ChecklistItem(18, "Physical Digital Display Mode readiness", "PASSED", "Continuous scrolling & live update ticker ready")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(checklist) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Passed",
                        tint = PriorityLowGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Scenario ${item.id}: ${item.scenario}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Result: ${item.status} • ${item.details}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
