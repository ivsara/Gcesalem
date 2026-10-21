package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.theme.GceBluePrimary
import com.example.ui.theme.GceGoldAccent
import com.example.ui.theme.parseColorOrFallback
import com.example.ui.viewmodel.NoticeBoardViewModel

data class ColorOption(val name: String, val hex: String, val previewColor: Color)

@Composable
fun ThemeSettingsScreen(
    viewModel: NoticeBoardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pref by viewModel.userPreference.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val collegeSettings by viewModel.collegeSettings.collectAsState()

    var isDark by remember(pref) { mutableStateOf(pref.isDarkMode) }
    var selectedAccentHex by remember(pref) { mutableStateOf(pref.accentColorHex) }
    var fontScale by remember(pref) { mutableFloatStateOf(pref.fontSizeScale) }
    var cardStyle by remember(pref) { mutableStateOf(pref.cardStyle) }

    val colorOptions = listOf(
        ColorOption("GCE Navy", "#0B3C5D", Color(0xFF0B3C5D)),
        ColorOption("Emerald", "#00796B", Color(0xFF00796B)),
        ColorOption("Crimson", "#C2185B", Color(0xFFC2185B)),
        ColorOption("Amber", "#E65100", Color(0xFFE65100)),
        ColorOption("Deep Slate", "#37474F", Color(0xFF37474F))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Dashboard Theme & Display Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Customize your visual experience. Preferences are saved automatically to the database.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Dark Mode / Light Mode
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Dark Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (isDark) "Night time high-contrast palette active" else "Standard bright daytime theme",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isDark,
                    onCheckedChange = {
                        isDark = it
                        viewModel.updatePreferences(it, selectedAccentHex, fontScale, cardStyle)
                    }
                )
            }
        }

        // Section 2: Accent Color Palette
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("College Accent Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { opt ->
                        val isSelected = selectedAccentHex.equals(opt.hex, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    selectedAccentHex = opt.hex
                                    viewModel.updatePreferences(isDark, opt.hex, fontScale, cardStyle)
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(opt.previewColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = opt.name, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // Section 3: Notice Card Style
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Notice Card Appearance", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ELEVATED" to "Elevated Shadow", "OUTLINED" to "Outlined Border", "COMPACT" to "Compact Slate").forEach { (style, label) ->
                        FilterChip(
                            selected = cardStyle == style,
                            onClick = {
                                cardStyle = style
                                viewModel.updatePreferences(isDark, selectedAccentHex, fontScale, style)
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section 4: Live Theme Preview Card
        Text("Theme Live Preview:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        val previewPrimary = parseColorOrFallback(selectedAccentHex, GceBluePrimary)

        Surface(
            color = if (isDark) Color(0xFF172230) else Color.White,
            shape = RoundedCornerShape(10.dp),
            border = if (cardStyle == "OUTLINED") androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB0BEC5)) else null,
            shadowElevation = if (cardStyle == "ELEVATED") 3.dp else 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = previewPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "PREVIEW ANNOUNCEMENT",
                            color = previewPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(text = "Just Now", fontSize = 10.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "GCE Salem Digital Notice Board Theme Test",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "This preview demonstrates font contrast, badge accents, and card styling before applying globally.",
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64)
                )
            }
        }

        // Section 5: Reset & Admin Global Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.resetPreferencesToDefault()
                    Toast.makeText(context, "Reset to Institutional Defaults!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Defaults", fontSize = 12.sp)
            }

            if (currentUser?.role == UserRole.ADMIN.name) {
                Button(
                    onClick = {
                        val currentBanner = collegeSettings?.bannerNotice ?: "Government College of Engineering, Salem"
                        val currentRefresh = collegeSettings?.refreshIntervalSeconds ?: 10
                        viewModel.updateCollegeSettings(
                            banner = currentBanner,
                            refreshInterval = currentRefresh,
                            defaultDark = isDark,
                            defaultAccent = selectedAccentHex
                        )
                        Toast.makeText(context, "Current theme set as college default for all guests!", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Set as College Default", fontSize = 12.sp)
                }
            }
        }
    }
}
