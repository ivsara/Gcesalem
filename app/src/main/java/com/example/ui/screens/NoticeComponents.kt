package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttachmentEntity
import com.example.data.model.NoticeCategory
import com.example.data.model.NoticeEntity
import com.example.data.model.NoticePriority
import com.example.data.model.NoticeVisibility
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.ui.theme.CatCircularColor
import com.example.ui.theme.CatEventColor
import com.example.ui.theme.CatExamColor
import com.example.ui.theme.CatGeneralColor
import com.example.ui.theme.CatPlacementColor
import com.example.ui.theme.GceBluePrimary
import com.example.ui.theme.GceGoldAccent
import com.example.ui.theme.PriorityHighRed
import com.example.ui.theme.PriorityHighRedBg
import com.example.ui.theme.PriorityLowGreen
import com.example.ui.theme.PriorityLowGreenBg
import com.example.ui.theme.PriorityMedAmber
import com.example.ui.theme.PriorityMedAmberBg
import com.example.ui.viewmodel.NoticeBoardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "Never"
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(ms))
}

fun formatShortDate(ms: Long): String {
    if (ms <= 0L) return "No Expiry"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(ms))
}

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (category) {
        NoticeCategory.EXAMINATION.displayName -> Color(0xFFF3E5F5) to CatExamColor
        NoticeCategory.PLACEMENTS.displayName -> Color(0xFFE0F2F1) to CatPlacementColor
        NoticeCategory.EVENTS.displayName -> Color(0xFFFFF3E0) to CatEventColor
        NoticeCategory.DEPARTMENT_CIRCULARS.displayName -> Color(0xFFE3F2FD) to CatCircularColor
        NoticeCategory.IMPORTANT_ANNOUNCEMENTS.displayName -> Color(0xFFFFEBEE) to PriorityHighRed
        else -> Color(0xFFECEFF1) to CatGeneralColor
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = category,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (priority) {
        NoticePriority.HIGH.name -> PriorityHighRedBg to PriorityHighRed
        NoticePriority.MEDIUM.name -> PriorityMedAmberBg to PriorityMedAmber
        else -> PriorityLowGreenBg to PriorityLowGreen
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = priority,
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoticeCard(
    notice: NoticeEntity,
    currentUser: UserEntity?,
    cardStyle: String = "ELEVATED",
    viewModel: NoticeBoardViewModel,
    onViewDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val attachments by viewModel.getAttachmentsForNotice(notice.id).collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val isExpired = notice.expiresAt in 1..now
    val canModify = currentUser?.role == UserRole.ADMIN.name ||
            (currentUser?.role == UserRole.HOD.name && (notice.departmentCode == currentUser.departmentCode || notice.authorId == currentUser.id)) ||
            (currentUser?.role == UserRole.FACULTY.name && notice.authorId == currentUser.id)

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Pinned indicator & Expiry status banner
            if (notice.isPinned || isExpired) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (notice.isPinned) {
                        Surface(
                            color = Color(0xFFFFF8E1),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, GceGoldAccent)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFFC49000),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PINNED ANNOUNCEMENT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8A6500)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (isExpired) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "EXPIRED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriorityHighRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Categories & Priority & Department Tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryBadge(category = notice.category)
                PriorityBadge(priority = notice.priority)

                // Department Tag
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (notice.departmentCode == "ALL") "All Depts" else "Dept: ${notice.departmentCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Visibility Tag
                if (notice.visibility == NoticeVisibility.RESTRICTED.name) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Restricted",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Restricted",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notice Title
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Notice Description snippet
            Text(
                text = notice.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Attachments Preview summary chips
            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attachments",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${attachments.size} Attachment${if (attachments.size > 1) "s" else ""}:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    attachments.take(2).forEach { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${att.fileType}: ${att.originalFilename.take(16)}...",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Publisher info & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Published by: ${notice.authorName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(notice.publishedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canModify) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("notice_edit_${notice.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Notice",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("notice_delete_${notice.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Notice",
                                tint = PriorityHighRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onViewDetail,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("notice_view_${notice.id}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("View Details", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    when (cardStyle) {
        "OUTLINED" -> {
            OutlinedCard(
                onClick = onViewDetail,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("notice_card_${notice.id}"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) { content() }
        }
        "COMPACT" -> {
            Card(
                onClick = onViewDetail,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("notice_card_${notice.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) { content() }
        }
        else -> {
            Card(
                onClick = onViewDetail,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("notice_card_${notice.id}"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) { content() }
        }
    }
}

@Composable
fun NoticeDetailDialog(
    notice: NoticeEntity,
    viewModel: NoticeBoardViewModel,
    onDismiss: () -> Unit
) {
    val attachments by viewModel.getAttachmentsForNotice(notice.id).collectAsState(initial = emptyList())
    val context = LocalContext.current
    var previewAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(category = notice.category)
                    PriorityBadge(priority = notice.priority)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Meta Info Table
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Department: ${if (notice.departmentCode == "ALL") "All College Departments" else notice.departmentCode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Published By: ${notice.authorName} (${notice.authorRole})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Date: ${formatTimestamp(notice.publishedAt)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (notice.expiresAt > 0L) {
                            Text(
                                text = "Valid Until: ${formatTimestamp(notice.expiresAt)}",
                                fontSize = 12.sp,
                                color = if (notice.expiresAt < System.currentTimeMillis()) PriorityHighRed else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Announcement Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notice.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Attachments Section
                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Official Circulars & Attachments (${attachments.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    attachments.forEach { att ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val icon = when (att.fileType) {
                                        "PDF" -> Icons.Default.Description
                                        "IMAGE" -> Icons.Default.Image
                                        "XLS" -> Icons.Default.TableChart
                                        else -> Icons.Default.AttachFile
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = att.fileType,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = att.originalFilename,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${att.fileType} • ${(att.fileSizeBytes / 1024)} KB",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { previewAttachment = att },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Preview",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(
                                                context,
                                                "Downloaded: ${att.originalFilename} (Saved to Downloads)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = GceBluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close")
            }
        }
    )

    // Sub-dialog: Attachment Content Previewer
    previewAttachment?.let { att ->
        AlertDialog(
            onDismissRequest = { previewAttachment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (att.fileType == "IMAGE") Icons.Default.Image else Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Document Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = att.originalFilename,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Format: ${att.fileType} • Size: ${(att.fileSizeBytes / 1024)} KB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (att.fileType == "IMAGE") {
                        // Simulated high-fidelity college graphic banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GceBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = GceGoldAccent,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "GCE SALEM OFFICIAL NOTICE IMAGE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = att.previewSnippet.ifEmpty { "High Resolution Image Attachment Verified" },
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        // Document content preview slate
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "DOCUMENT CONTENT SNIPPET:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = att.previewSnippet.ifEmpty {
                                        "Government College of Engineering, Salem (Autonomous)\nOfficial Circular Reference: GCE/OFFICE/NOTICES/2026\nAuthorized signatory: Head of Department & Principal"
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewAttachment = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeFormDialog(
    initialNotice: NoticeEntity? = null,
    departments: List<com.example.data.model.DepartmentEntity>,
    currentUser: UserEntity,
    viewModel: NoticeBoardViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialNotice?.title ?: "") }
    var description by remember { mutableStateOf(initialNotice?.description ?: "") }
    var selectedDept by remember {
        mutableStateOf(
            initialNotice?.departmentCode ?: if (currentUser.role == UserRole.ADMIN.name) "ALL" else currentUser.departmentCode
        )
    }
    var selectedCategory by remember {
        mutableStateOf(initialNotice?.category ?: NoticeCategory.GENERAL.displayName)
    }
    var selectedPriority by remember {
        mutableStateOf(initialNotice?.priority ?: NoticePriority.MEDIUM.name)
    }
    var expiryOption by remember { mutableStateOf("15") } // 0, 7, 15, 30
    var isPinned by remember { mutableStateOf(initialNotice?.isPinned ?: false) }
    var visibility by remember {
        mutableStateOf(initialNotice?.visibility ?: NoticeVisibility.PUBLIC.name)
    }

    val attachments = remember { mutableStateListOf<AttachmentEntity>() }

    // Dropdown expansion states
    var deptExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    val categories = listOf(
        NoticeCategory.EXAMINATION.displayName,
        NoticeCategory.EVENTS.displayName,
        NoticeCategory.PLACEMENTS.displayName,
        NoticeCategory.GENERAL.displayName,
        NoticeCategory.DEPARTMENT_CIRCULARS.displayName,
        NoticeCategory.IMPORTANT_ANNOUNCEMENTS.displayName
    )

    val priorities = listOf(
        NoticePriority.HIGH.name,
        NoticePriority.MEDIUM.name,
        NoticePriority.LOW.name
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNotice == null) "Publish New Notice" else "Edit Notice",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notice Title *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notice_title_input"),
                    singleLine = true
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Detailed Description *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("notice_desc_input"),
                    maxLines = 5
                )

                // Department Selector
                ExposedDropdownMenuBox(
                    expanded = deptExpanded,
                    onExpandedChange = { deptExpanded = !deptExpanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedDept == "ALL") "All College Departments" else selectedDept,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Department") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = deptExpanded,
                        onDismissRequest = { deptExpanded = false }
                    ) {
                        if (currentUser.role == UserRole.ADMIN.name) {
                            DropdownMenuItem(
                                text = { Text("All College Departments") },
                                onClick = {
                                    selectedDept = "ALL"
                                    deptExpanded = false
                                }
                            )
                        }
                        departments.forEach { dept ->
                            val canSelect = currentUser.role == UserRole.ADMIN.name || currentUser.departmentCode == dept.code
                            if (canSelect) {
                                DropdownMenuItem(
                                    text = { Text("${dept.code} - ${dept.name}") },
                                    onClick = {
                                        selectedDept = dept.code
                                        deptExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                // Priority Selector
                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { priorityExpanded = !priorityExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPriority,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        priorities.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    selectedPriority = p
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notice Expiry Options
                Text(text = "Expiry Duration:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("7" to "7 Days", "15" to "15 Days", "30" to "30 Days", "0" to "No Expiry").forEach { (valStr, label) ->
                        FilterChip(
                            selected = expiryOption == valStr,
                            onClick = { expiryOption = valStr },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Visibility Option (Public vs Restricted)
                Text(text = "Notice Visibility:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = visibility == NoticeVisibility.PUBLIC.name,
                        onClick = { visibility = NoticeVisibility.PUBLIC.name }
                    )
                    Text("Public (Homepage & Guest)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(
                        selected = visibility == NoticeVisibility.RESTRICTED.name,
                        onClick = { visibility = NoticeVisibility.RESTRICTED.name }
                    )
                    Text("Restricted (Login Req.)", fontSize = 13.sp)
                }

                // Pin Notice (Admin only)
                if (currentUser.role == UserRole.ADMIN.name) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it }
                        )
                        Text("Pin notice to top of board", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Attachments Simulation Manager
                Text(text = "Attachments (${attachments.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val count = attachments.size + 1
                            attachments.add(
                                AttachmentEntity(
                                    noticeId = 0,
                                    originalFilename = "Circular_Annexure_$count.pdf",
                                    storedFilename = "attach_pdf_$count.pdf",
                                    fileType = "PDF",
                                    fileSizeBytes = 720000,
                                    uploadedBy = currentUser.loginId,
                                    previewSnippet = "Official College PDF Annexure Document $count. Certified by Controller of Examinations & HOD."
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ PDF", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val count = attachments.size + 1
                            attachments.add(
                                AttachmentEntity(
                                    noticeId = 0,
                                    originalFilename = "Event_Banner_Photo_$count.png",
                                    storedFilename = "attach_img_$count.png",
                                    fileType = "IMAGE",
                                    fileSizeBytes = 1850000,
                                    uploadedBy = currentUser.loginId,
                                    previewSnippet = "Official High-Resolution College Event Banner & Poster $count."
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Image", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val count = attachments.size + 1
                            attachments.add(
                                AttachmentEntity(
                                    noticeId = 0,
                                    originalFilename = "Student_List_Schedule_$count.xlsx",
                                    storedFilename = "attach_xls_$count.xlsx",
                                    fileType = "XLS",
                                    fileSizeBytes = 340000,
                                    uploadedBy = currentUser.loginId,
                                    previewSnippet = "Spreadsheet containing registered candidate roll numbers, lab halls, and session timings."
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Excel", fontSize = 11.sp)
                    }
                }

                // List added attachments
                attachments.forEachIndexed { index, att ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${att.fileType}: ${att.originalFilename}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { attachments.removeAt(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank()) {
                        Toast.makeText(context, "Title and description are required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (initialNotice == null) {
                        viewModel.createNotice(
                            title = title,
                            description = description,
                            departmentCode = selectedDept,
                            category = selectedCategory,
                            priority = selectedPriority,
                            expiryDays = expiryOption.toIntOrNull() ?: 15,
                            isPinned = isPinned,
                            visibility = visibility,
                            attachments = attachments.toList()
                        ) { success ->
                            if (success) {
                                Toast.makeText(context, "Notice published successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Error publishing notice", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        viewModel.updateNotice(
                            notice = initialNotice.copy(
                                title = title,
                                description = description,
                                departmentCode = selectedDept,
                                category = selectedCategory,
                                priority = selectedPriority,
                                isPinned = isPinned,
                                visibility = visibility
                            ),
                            newAttachments = attachments.toList()
                        ) { success ->
                            if (success) {
                                Toast.makeText(context, "Notice updated successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Error updating notice", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("submit_notice_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (initialNotice == null) "Publish" else "Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LoginDialog(
    viewModel: NoticeBoardViewModel,
    allUsers: List<UserEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val authError by viewModel.authError.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Portal Sign In", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enter your college Login ID and password to access restricted department circulars or manage notices.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Error alert
                if (authError != null) {
                    Surface(
                        color = PriorityHighRedBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authError ?: "",
                            color = PriorityHighRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Login ID Input
                OutlinedTextField(
                    value = loginId,
                    onValueChange = { loginId = it },
                    label = { Text("Login ID / Roll No") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_id_input"),
                    singleLine = true
                )

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loginId.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter login ID and password", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.login(loginId, password) { success ->
                        if (success) {
                            Toast.makeText(context, "Welcome to GCE Notice Board!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("login_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Sign In")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
