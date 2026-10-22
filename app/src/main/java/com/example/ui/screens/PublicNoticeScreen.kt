package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoticeCategory
import com.example.data.model.NoticeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.ui.theme.GceBlueDark
import com.example.ui.theme.GceBluePrimary
import com.example.ui.theme.GceGoldAccent
import com.example.ui.theme.PriorityLowGreen
import com.example.ui.theme.PriorityMedAmber
import com.example.ui.viewmodel.NoticeBoardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicNoticeScreen(
    viewModel: NoticeBoardViewModel,
    modifier: Modifier = Modifier
) {
    val notices by viewModel.filteredNotices.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val departments by viewModel.activeDepartments.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val collegeSettings by viewModel.collegeSettings.collectAsState()
    val userPref by viewModel.userPreference.collectAsState()
    val isPolling by viewModel.isPollingActive.collectAsState()
    val lastRefreshed by viewModel.lastRefreshedAt.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDept by viewModel.selectedDepartmentFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val showExpiredOnly by viewModel.showExpiredOnly.collectAsState()

    // Dialog state controllers
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedNoticeForDetail by remember { mutableStateOf<NoticeEntity?>(null) }
    var selectedNoticeForEdit by remember { mutableStateOf<NoticeEntity?>(null) }
    var showCreateNoticeDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var noticeToDelete by remember { mutableStateOf<NoticeEntity?>(null) }

    val canPublishNotice = currentUser?.role == UserRole.ADMIN.name ||
            currentUser?.role == UserRole.HOD.name ||
            currentUser?.role == UserRole.FACULTY.name

    val categories = listOf(
        "ALL",
        NoticeCategory.EXAMINATION.displayName,
        NoticeCategory.EVENTS.displayName,
        NoticeCategory.PLACEMENTS.displayName,
        NoticeCategory.GENERAL.displayName,
        NoticeCategory.DEPARTMENT_CIRCULARS.displayName,
        NoticeCategory.IMPORTANT_ANNOUNCEMENTS.displayName
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. College Header Banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = GceGoldAccent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = "College Logo",
                                            tint = GceBlueDark,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = collegeSettings?.collegeShortName ?: "GCE Salem",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Government College of Engineering, Salem",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Live Auto-Update Indicator with Instant Refresh
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isPolling) PriorityLowGreen else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isPolling) "Live (10s)" else "Manual",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = {
                                        viewModel.manualRefresh {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Notices refreshed & synced across devices",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("refresh_notices_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Now",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Institutional Sub-banner Ticker
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = GceGoldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = collegeSettings?.bannerNotice ?: "Autonomous Institution Affiliated to Anna University | Approved by AICTE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. User Authentication & Role Status Bar
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (currentUser == null) "Browsing: Public Notice Board" else "Signed In: ${currentUser?.fullName}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (currentUser == null) "Login for restricted departmental circulars" else "Role: ${currentUser?.role} • Dept: ${currentUser?.departmentCode}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentUser == null) {
                                Button(
                                    onClick = { showLoginDialog = true },
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("sign_in_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Sign In", fontSize = 11.sp)
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.logout() },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Search & Filter Bar
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notice_search_bar"),
                        placeholder = { Text("Search notices by title, exam, placements, keyword...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Department Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "CIVIL", "MECH", "ECE", "EEE", "CSE", "METT", "AIML").forEach { deptCode ->
                            FilterChip(
                                selected = selectedDept == deptCode,
                                onClick = { viewModel.selectedDepartmentFilter.value = deptCode },
                                label = { Text(if (deptCode == "ALL") "All Departments" else deptCode, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { viewModel.selectedCategoryFilter.value = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Admin Expired Archive Switch
                    if (currentUser?.role == UserRole.ADMIN.name) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Expired Archive", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = showExpiredOnly,
                                onCheckedChange = { viewModel.showExpiredOnly.value = it },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // 4. Notice Listing
            if (notices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Announcements Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No notices match the selected department, category, or search keywords. Try clearing filters or checking back later.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            viewModel.searchQuery.value = ""
                            viewModel.selectedDepartmentFilter.value = "ALL"
                            viewModel.selectedCategoryFilter.value = "ALL"
                            viewModel.showExpiredOnly.value = false
                        }) {
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                items(notices, key = { it.id }) { notice ->
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        NoticeCard(
                            notice = notice,
                            currentUser = currentUser,
                            cardStyle = userPref.cardStyle,
                            viewModel = viewModel,
                            onViewDetail = { selectedNoticeForDetail = notice },
                            onEdit = { selectedNoticeForEdit = notice },
                            onDelete = { noticeToDelete = notice }
                        )
                    }
                }
            }
        }

        // Floating Action Button for Publishing Notices (Faculty and Admin)
        if (canPublishNotice) {
            FloatingActionButton(
                onClick = { showCreateNoticeDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("publish_notice_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Publish Notice")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Notice Detail Dialog
    selectedNoticeForDetail?.let { notice ->
        NoticeDetailDialog(
            notice = notice,
            viewModel = viewModel,
            onDismiss = { selectedNoticeForDetail = null }
        )
    }

    // Create Notice Dialog
    if (showCreateNoticeDialog && currentUser != null) {
        NoticeFormDialog(
            departments = departments,
            currentUser = currentUser!!,
            viewModel = viewModel,
            onDismiss = { showCreateNoticeDialog = false }
        )
    }

    // Edit Notice Dialog
    selectedNoticeForEdit?.let { notice ->
        if (currentUser != null) {
            NoticeFormDialog(
                initialNotice = notice,
                departments = departments,
                currentUser = currentUser!!,
                viewModel = viewModel,
                onDismiss = { selectedNoticeForEdit = null }
            )
        }
    }

    // Login Dialog
    if (showLoginDialog) {
        LoginDialog(
            viewModel = viewModel,
            allUsers = allUsers,
            onDismiss = { showLoginDialog = false }
        )
    }

    // Delete Confirmation Dialog
    noticeToDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { noticeToDelete = null },
            title = { Text("Delete Announcement?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${n.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNotice(n.id, n.authorId) {
                            noticeToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { noticeToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
