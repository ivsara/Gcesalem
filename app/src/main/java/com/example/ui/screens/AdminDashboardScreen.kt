package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DepartmentEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.ui.theme.GceBluePrimary
import com.example.ui.theme.GceGoldAccent
import com.example.ui.theme.PriorityHighRed
import com.example.ui.theme.PriorityLowGreen
import com.example.ui.theme.PriorityMedAmber
import com.example.ui.viewmodel.NoticeBoardViewModel

@Composable
fun AdminDashboardScreen(
    viewModel: NoticeBoardViewModel,
    modifier: Modifier = Modifier
) {
    val allUsers by viewModel.allUsers.collectAsState()
    val allDepartments by viewModel.allDepartments.collectAsState()
    val collegeSettings by viewModel.collegeSettings.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Users & Roles", "Departments", "Portal Settings")

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
                            0 -> Icon(Icons.Default.ManageAccounts, contentDescription = null)
                            1 -> Icon(Icons.Default.Apartment, contentDescription = null)
                            else -> Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> UserManagementTab(viewModel = viewModel, users = allUsers, departments = allDepartments)
            1 -> DepartmentManagementTab(viewModel = viewModel, departments = allDepartments)
            else -> CollegeSettingsTab(viewModel = viewModel, settings = collegeSettings)
        }
    }
}

@Composable
fun UserManagementTab(
    viewModel: NoticeBoardViewModel,
    users: List<UserEntity>,
    departments: List<DepartmentEntity>
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "User Accounts (${users.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage Admin Principal, 7 HODs, 21 Faculty & 70 Students",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("admin_add_user_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New User")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(user = user, viewModel = viewModel, departments = departments)
            }
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            departments = departments,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun UserCard(
    user: UserEntity,
    viewModel: NoticeBoardViewModel,
    departments: List<DepartmentEntity>
) {
    var showEditRoleDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = when (user.role) {
                        UserRole.ADMIN.name -> PriorityHighRed.copy(alpha = 0.15f)
                        UserRole.FACULTY.name -> MaterialTheme.colorScheme.primaryContainer
                        else -> GceGoldAccent.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = when (user.role) {
                                UserRole.ADMIN.name -> PriorityHighRed
                                UserRole.FACULTY.name -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFF8A6500)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = user.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "ID: ${user.loginId} • Dept: ${user.departmentCode}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            color = when (user.role) {
                                UserRole.ADMIN.name -> PriorityHighRed.copy(alpha = 0.1f)
                                UserRole.FACULTY.name -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color(0xFFF1F5F9)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = user.role,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (user.role) {
                                    UserRole.ADMIN.name -> PriorityHighRed
                                    UserRole.FACULTY.name -> MaterialTheme.colorScheme.primary
                                    else -> Color(0xFF334155)
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Surface(
                            color = if (user.isActive) PriorityLowGreen.copy(alpha = 0.1f) else PriorityHighRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (user.isActive) "Active" else "Deactivated",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (user.isActive) PriorityLowGreen else PriorityHighRed,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showEditRoleDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Role",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Switch(
                    checked = user.isActive,
                    onCheckedChange = { viewModel.toggleUserActive(user) },
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }

    if (showEditRoleDialog) {
        var newRole by remember { mutableStateOf(user.role) }
        var newDept by remember { mutableStateOf(user.departmentCode) }

        AlertDialog(
            onDismissRequest = { showEditRoleDialog = false },
            title = { Text("Update Role: ${user.loginId}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select User Role:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    listOf(UserRole.ADMIN.name, UserRole.HOD.name, UserRole.FACULTY.name, UserRole.STUDENT.name).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = newRole == r,
                                onClick = { newRole = r }
                            )
                            Text(r, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Department Assignment:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ALL", "CIVIL", "MECH", "ECE", "EEE", "CSE", "METT", "AIML").forEach { d ->
                            androidx.compose.material3.FilterChip(
                                selected = newDept == d,
                                onClick = { newDept = d },
                                label = { Text(d, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserRole(user, newRole, newDept)
                        showEditRoleDialog = false
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditRoleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserDialog(
    departments: List<DepartmentEntity>,
    viewModel: NoticeBoardViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.FACULTY.name) }
    var selectedDept by remember { mutableStateOf("CSE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New User Account", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = loginId,
                    onValueChange = { loginId = it },
                    label = { Text("Login ID *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("User Role:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(UserRole.ADMIN.name, UserRole.HOD.name, UserRole.FACULTY.name, UserRole.STUDENT.name).forEach { r ->
                        androidx.compose.material3.FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Department:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALL", "CIVIL", "MECH", "ECE", "EEE", "CSE", "METT", "AIML").forEach { d ->
                        androidx.compose.material3.FilterChip(
                            selected = selectedDept == d,
                            onClick = { selectedDept = d },
                            label = { Text(d, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loginId.isBlank() || password.isBlank() || fullName.isBlank()) {
                        Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.createUser(
                        loginId = loginId,
                        passwordPlain = password,
                        fullName = fullName,
                        role = role,
                        deptCode = selectedDept,
                        email = email
                    ) { success, errorMsg ->
                        if (success) {
                            Toast.makeText(context, "User account created!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, errorMsg ?: "Creation error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text("Create Account")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DepartmentManagementTab(
    viewModel: NoticeBoardViewModel,
    departments: List<DepartmentEntity>
) {
    val context = LocalContext.current
    var showAddDeptDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Academic Departments (${departments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Government College of Engineering, Salem",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showAddDeptDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Dept")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(departments, key = { it.id }) { dept ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = dept.code,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dept.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dept.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = dept.isActive,
                            onCheckedChange = { viewModel.toggleDepartmentActive(dept) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDeptDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDeptDialog = false },
            title = { Text("Add Department", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Code (e.g. MECH, CIVIL, AI)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Department Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Short Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || code.isBlank()) {
                            Toast.makeText(context, "Name and Code required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.createDepartment(name, code, desc) { success ->
                            if (success) {
                                Toast.makeText(context, "Department added!", Toast.LENGTH_SHORT).show()
                                showAddDeptDialog = false
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDeptDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CollegeSettingsTab(
    viewModel: NoticeBoardViewModel,
    settings: com.example.data.model.CollegeSettingsEntity?
) {
    val context = LocalContext.current
    var bannerText by remember(settings) {
        mutableStateOf(
            settings?.bannerNotice ?: "Autonomous Institution Affiliated to Anna University | Approved by AICTE, New Delhi"
        )
    }
    var refreshSec by remember(settings) {
        mutableStateOf((settings?.refreshIntervalSeconds ?: 10).toString())
    }
    var defaultDark by remember(settings) {
        mutableStateOf(settings?.defaultThemeDark ?: false)
    }
    var defaultAccent by remember(settings) {
        mutableStateOf(settings?.defaultAccentColor ?: "#0B3C5D")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Institutional Portal Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure administrator default parameters for Government College of Engineering, Salem.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Banner Notice Field
        OutlinedTextField(
            value = bannerText,
            onValueChange = { bannerText = it },
            label = { Text("Campus Ticker Banner Notice") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        // Refresh Interval Field
        OutlinedTextField(
            value = refreshSec,
            onValueChange = { refreshSec = it },
            label = { Text("Live Auto-Update Interval (Seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Default Theme Dark mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Default Night/Dark Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Default display style for non-logged in guests", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = defaultDark,
                onCheckedChange = { defaultDark = it }
            )
        }

        Button(
            onClick = {
                val interval = refreshSec.toIntOrNull() ?: 10
                viewModel.updateCollegeSettings(
                    banner = bannerText,
                    refreshInterval = interval.coerceIn(5, 60),
                    defaultDark = defaultDark,
                    defaultAccent = defaultAccent
                )
                Toast.makeText(context, "Portal settings saved successfully!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Global Settings")
        }
    }
}
