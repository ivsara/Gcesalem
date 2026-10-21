package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserRole
import com.example.ui.screens.AcademicDocsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.PublicNoticeScreen
import com.example.ui.screens.ThemeSettingsScreen
import com.example.ui.theme.GceNoticeBoardTheme
import com.example.ui.viewmodel.NoticeBoardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NoticeBoardViewModel = viewModel()
            val userPref by viewModel.userPreference.collectAsState()
            val currentUser by viewModel.currentUser.collectAsState()

            GceNoticeBoardTheme(
                darkTheme = userPref.isDarkMode,
                accentColorHex = userPref.accentColorHex
            ) {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Notice Board") },
                                label = { Text("Notices", fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_notices")
                            )

                            // Role-specific second navigation tab
                            val (roleTabLabel, roleTabIcon) = when (currentUser?.role) {
                                UserRole.ADMIN.name -> "Admin Console" to Icons.Default.SupervisorAccount
                                UserRole.FACULTY.name -> "Publish" to Icons.Default.Assignment
                                else -> "Departments" to Icons.Default.Apartment
                            }

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(roleTabIcon, contentDescription = roleTabLabel) },
                                label = { Text(roleTabLabel, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_role_console")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Palette, contentDescription = "Theme") },
                                label = { Text("Theme", fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_theme")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.MenuBook, contentDescription = "Viva & Docs") },
                                label = { Text("Viva & Docs", fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_docs")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> PublicNoticeScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> {
                            if (currentUser?.role == UserRole.ADMIN.name) {
                                AdminDashboardScreen(
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                // Filter by department / public view
                                PublicNoticeScreen(
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                        2 -> ThemeSettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> AcademicDocsScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
