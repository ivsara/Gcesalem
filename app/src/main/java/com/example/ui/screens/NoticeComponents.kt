package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

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

private val FAKE_DEMO_SIZES = setOf(
    1420500L, 582400L, 420500L, 1890000L, 345000L, 2140000L, 720000L, 1850000L, 340000L, 102400L, 52L
)

fun formatFileSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1024L -> "$safeBytes B"
        safeBytes < 1048576L -> {
            val kb = safeBytes.toDouble() / 1024.0
            val formatted = String.format(Locale.US, "%.1f", kb).removeSuffix(".0")
            "$formatted KB"
        }
        else -> {
            val mb = safeBytes.toDouble() / 1048576.0
            val formatted = String.format(Locale.US, "%.2f", mb).removeSuffix(".00")
            "$formatted MB"
        }
    }
}

private fun buildValidPdfBytes(title: String, snippet: String): ByteArray {
    val cleanTitle = title.replace(Regex("[()\\\\]"), "")
    val lines = listOf(
        "GOVERNMENT COLLEGE OF ENGINEERING, SALEM - 636011",
        "OFFICIAL DIGITAL NOTICE BOARD ATTACHMENT",
        "------------------------------------------------------------",
        "File: $cleanTitle",
        "------------------------------------------------------------"
    ) + snippet.lines().map { it.replace(Regex("[()\\\\]"), "").take(80) }

    var y = 760
    val streamBuilder = StringBuilder("BT\n/F1 11 Tf\n")
    lines.take(35).forEach { line ->
        streamBuilder.append("1 0 0 1 50 $y Tm ($line) Tj\n")
        y -= 18
    }
    streamBuilder.append("ET")
    val stream = streamBuilder.toString()

    val pdf = "%PDF-1.4\n" +
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n" +
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n" +
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n" +
            "4 0 obj << /Length ${stream.length} >> stream\n$stream\nendstream endobj\n" +
            "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n" +
            "trailer << /Root 1 0 R >>\n%%EOF"
    return pdf.toByteArray(Charsets.UTF_8)
}

fun getDownloadableBytes(context: Context, attachment: AttachmentEntity): ByteArray {
    val filename = attachment.originalFilename.ifBlank { "GCE_Salem_Circular.pdf" }
    val ext = filename.substringAfterLast('.', "pdf").lowercase()
    val localDir = File(context.filesDir, "attachments")
    val localFile = File(localDir, attachment.storedFilename)
    return if (localFile.exists() && localFile.isFile && localFile.length() > 0L) {
        localFile.readBytes()
    } else if (ext == "pdf") {
        buildValidPdfBytes(
            filename,
            attachment.previewSnippet.ifBlank { "Official GCE Salem Circular Document" }
        )
    } else {
        val content = "GOVERNMENT COLLEGE OF ENGINEERING, SALEM\n" +
                "Document: $filename\n\n" +
                attachment.previewSnippet.ifBlank { "Official GCE Salem Attachment" }
        content.toByteArray(Charsets.UTF_8)
    }
}

fun getRealAttachmentSize(context: Context, attachment: AttachmentEntity): Long {
    val localDir = File(context.filesDir, "attachments")
    val localFile = File(localDir, attachment.storedFilename)
    if (localFile.exists() && localFile.isFile && localFile.length() > 0L) {
        val diskLen = localFile.length()
        if (diskLen >= 4000L || attachment.fileSizeBytes <= 0L || FAKE_DEMO_SIZES.contains(attachment.fileSizeBytes)) {
            return diskLen
        }
    }
    if (attachment.fileSizeBytes > 0L && !FAKE_DEMO_SIZES.contains(attachment.fileSizeBytes)) {
        return attachment.fileSizeBytes
    }
    return getDownloadableBytes(context, attachment).size.toLong()
}

fun saveAttachmentToDownloads(context: Context, attachment: AttachmentEntity) {
    try {
        val filename = attachment.originalFilename.ifBlank { "GCE_Salem_Circular.pdf" }
        val ext = filename.substringAfterLast('.', "pdf").lowercase()
        val mimeType = when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "xls", "xlsx", "csv" -> "text/csv"
            "doc", "docx" -> "application/msword"
            else -> "application/octet-stream"
        }

        val bytesToWrite: ByteArray = getDownloadableBytes(context, attachment)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytesToWrite)
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Toast.makeText(
                    context,
                    "Downloaded '$filename' to Downloads folder (${formatFileSize(bytesToWrite.size.toLong())})",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // Fallback to app external Downloads directory
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!targetDir.exists()) targetDir.mkdirs()
        val outFile = File(targetDir, filename)
        FileOutputStream(outFile).use { it.write(bytesToWrite) }
        Toast.makeText(
            context,
            "Downloaded '$filename' (${outFile.absolutePath})",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Download failed: ${e.localizedMessage ?: "Unable to write file"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun generateCaptchaCode(length: Int = 6): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
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
    var cardPreviewAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }
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

            // Attachments Preview & Direct Download chips
            if (attachments.isNotEmpty()) {
                val cardContext = LocalContext.current
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    attachments.forEach { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { cardPreviewAttachment = att }
                                        .testTag("card_preview_att_${att.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Preview ${att.originalFilename}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${att.originalFilename.take(20)} (${formatFileSize(getRealAttachmentSize(cardContext, att))})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { saveAttachmentToDownloads(cardContext, att) }
                                        .testTag("card_download_att_${att.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download ${att.originalFilename}",
                                        tint = GceBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
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

    cardPreviewAttachment?.let { att ->
        AttachmentPreviewDialog(
            att = att,
            onDismiss = { cardPreviewAttachment = null }
        )
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
                                            text = "${att.fileType} • ${formatFileSize(getRealAttachmentSize(context, att))}",
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
                                            saveAttachmentToDownloads(context, att)
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("detail_download_att_${att.id}")
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
        AttachmentPreviewDialog(
            att = att,
            onDismiss = { previewAttachment = null }
        )
    }
}

@Composable
fun AttachmentPreviewDialog(
    att: AttachmentEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localFile = remember(att.storedFilename) {
        File(File(context.filesDir, "attachments"), att.storedFilename)
    }

    val imageBitmap: Bitmap? = remember(att.storedFilename, localFile.exists(), localFile.length()) {
        if (att.fileType == "IMAGE" && localFile.exists() && localFile.length() > 0L) {
            runCatching { BitmapFactory.decodeFile(localFile.absolutePath) }.getOrNull()
        } else null
    }

    val pdfBitmap: Bitmap? = remember(att.storedFilename, localFile.exists(), localFile.length()) {
        if (att.fileType == "PDF") {
            runCatching {
                val targetPdf = if (localFile.exists() && localFile.length() > 10L) {
                    localFile
                } else {
                    val fallbackPdf = File(context.cacheDir, "preview_${att.id}_${att.storedFilename.ifBlank { "doc.pdf" }}")
                    fallbackPdf.writeBytes(
                        buildValidPdfBytes(
                            att.originalFilename.ifBlank { "GCE_Salem_Circular.pdf" },
                            att.previewSnippet.ifBlank { "Official GCE Salem Circular Document" }
                        )
                    )
                    fallbackPdf
                }
                ParcelFileDescriptor.open(targetPdf, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val bmp = Bitmap.createBitmap(
                                    page.width.coerceAtLeast(400),
                                    page.height.coerceAtLeast(560),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        } else null
                    }
                }
            }.getOrNull()
        } else null
    }

    val textPreviewContent: String = remember(att.storedFilename, localFile.exists(), localFile.length()) {
        if (att.fileType != "IMAGE" && att.fileType != "PDF" && localFile.exists() && localFile.length() in 1..65536) {
            runCatching {
                val raw = localFile.readText(Charsets.UTF_8)
                if (raw.none { it == '\u0000' }) raw.take(1500) else att.previewSnippet
            }.getOrDefault(att.previewSnippet)
        } else {
            att.previewSnippet
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (att.fileType == "IMAGE") Icons.Default.Image else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Attachment Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = att.originalFilename,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Format: ${att.fileType} • Size: ${formatFileSize(getRealAttachmentSize(context, att))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    att.fileType == "IMAGE" && imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap.asImageBitmap(),
                            contentDescription = att.originalFilename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                        )
                    }
                    att.fileType == "IMAGE" -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = att.originalFilename,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Syncing image attachment from cloud storage...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    att.fileType == "PDF" && pdfBitmap != null -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = pdfBitmap.asImageBitmap(),
                                contentDescription = "PDF Page Preview",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "DOCUMENT CONTENT PREVIEW:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = textPreviewContent.ifEmpty {
                                        "Government College of Engineering, Salem (Autonomous)\nOfficial Circular Reference: GCE/OFFICE/NOTICES/2026\nAuthorized signatory: Head of Department & Principal"
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saveAttachmentToDownloads(context, att)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download File")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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

                // Upload Real File from Device
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            var displayName = "Uploaded_File_${System.currentTimeMillis()}.pdf"
                            var fileSize = 0L
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (cursor.moveToFirst()) {
                                    if (nameIdx >= 0) {
                                        displayName = cursor.getString(nameIdx) ?: displayName
                                    }
                                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                        fileSize = cursor.getLong(sizeIdx)
                                    }
                                }
                            }
                            val storedName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                            val attachDir = File(context.filesDir, "attachments")
                            if (!attachDir.exists()) attachDir.mkdirs()
                            val destFile = File(attachDir, storedName)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (destFile.exists() && destFile.length() > 0L) {
                                fileSize = destFile.length()
                            }
                            val ext = displayName.substringAfterLast('.', "PDF").uppercase()
                            val normalizedType = when (ext) {
                                "JPG", "JPEG", "PNG", "WEBP" -> "IMAGE"
                                "XLS", "XLSX", "CSV" -> "XLS"
                                "DOC", "DOCX" -> "DOC"
                                else -> "PDF"
                            }
                            attachments.add(
                                AttachmentEntity(
                                    noticeId = 0,
                                    originalFilename = displayName,
                                    storedFilename = storedName,
                                    fileType = normalizedType,
                                    fileSizeBytes = fileSize,
                                    uploadedBy = currentUser.loginId,
                                    previewSnippet = "Uploaded document '$displayName' (${formatFileSize(fileSize)}) by ${currentUser.fullName}."
                                )
                            )
                            Toast.makeText(context, "Attached: $displayName (${formatFileSize(fileSize)})", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not attach file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Text(text = "Attachments (${attachments.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_device_file_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Upload File", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload File / Document from Device", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                            text = "${att.fileType}: ${att.originalFilename} (${formatFileSize(att.fileSizeBytes)})",
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
fun CaptchaChallengeBox(
    captchaCode: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color(0xFFEEF4FF),
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(54.dp)) {
                val w = size.width
                val h = size.height

                // Security noise lines
                for (i in 0..4) {
                    val startY = (h * ((i * 23) % 100)) / 100f
                    val endY = (h * (((i + 2) * 37) % 100)) / 100f
                    drawLine(
                        color = if (i % 2 == 0) Color(0x55155EEF) else Color(0x440F172A),
                        start = Offset(12f, startY),
                        end = Offset(w - 12f, endY),
                        strokeWidth = 3f
                    )
                }

                // Draw CAPTCHA characters with slight vertical offset
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 46f
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.MONOSPACE,
                        android.graphics.Typeface.BOLD
                    )
                }

                val step = w / (captchaCode.length + 1)
                captchaCode.forEachIndexed { index, ch ->
                    paint.color = if (index % 2 == 0) {
                        android.graphics.Color.rgb(15, 23, 42)
                    } else {
                        android.graphics.Color.rgb(21, 94, 239)
                    }
                    val x = step * (index + 0.7f)
                    val yOffset = if (index % 2 == 0) -6f else 8f
                    val y = (h / 2f) + 14f + yOffset
                    drawContext.canvas.nativeCanvas.drawText(ch.toString(), x, y, paint)
                }
            }
        }

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .height(54.dp)
                .testTag("refresh_captcha_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "New CAPTCHA Code",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("New", fontSize = 12.sp)
        }
    }
}

@Composable
fun LoginDialog(
    viewModel: NoticeBoardViewModel,
    allUsers: List<UserEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isForgotMode by remember { mutableStateOf(false) }

    // Sign-In state
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var resetSuccessBanner by remember { mutableStateOf<String?>(null) }

    // Forgot Password + CAPTCHA state
    var forgotLoginId by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf(generateCaptchaCode()) }
    var captchaInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var forgotError by remember { mutableStateOf<String?>(null) }

    val authError by viewModel.authError.collectAsState()
    val matchedForgotUser = remember(forgotLoginId, allUsers) {
        val clean = forgotLoginId.trim()
        if (clean.isEmpty()) null else allUsers.find { it.loginId.equals(clean, ignoreCase = true) }
    }
    val isCaptchaVerified = captchaInput.trim().isNotEmpty() &&
            captchaInput.trim().equals(captchaCode, ignoreCase = true)

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
                Text(
                    text = if (isForgotMode) "Forgot Password (CAPTCHA)" else "Portal Sign In",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 2-Tab Mode Switcher: Sign In | Forgot Password (CAPTCHA)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                forgotError = null
                                isForgotMode = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("tab_signin_mode"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isForgotMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isForgotMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Sign In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.clearAuthError()
                                if (forgotLoginId.isBlank() && loginId.isNotBlank()) {
                                    forgotLoginId = loginId.trim()
                                }
                                captchaCode = generateCaptchaCode()
                                captchaInput = ""
                                forgotError = null
                                isForgotMode = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("tab_forgot_mode"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForgotMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isForgotMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Forgot Password", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!isForgotMode) {
                    Text(
                        text = "Enter your college Login ID and password to access restricted department circulars or manage notices.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (resetSuccessBanner != null) {
                        Surface(
                            color = PriorityLowGreenBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resetSuccessBanner ?: "",
                                color = PriorityLowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.clearAuthError()
                                forgotLoginId = loginId.trim()
                                captchaCode = generateCaptchaCode()
                                captchaInput = ""
                                newPassword = ""
                                confirmPassword = ""
                                forgotError = null
                                isForgotMode = true
                            },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = "Forgot Password? Reset with CAPTCHA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Step 1: Enter your Login ID. Step 2: Solve the security CAPTCHA. Step 3: Set your new password.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (forgotError != null) {
                        Surface(
                            color = PriorityHighRedBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = forgotError ?: "",
                                color = PriorityHighRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = forgotLoginId,
                        onValueChange = { forgotLoginId = it },
                        label = { Text("1. Login ID (e.g. principal, hod_cse, cse_fac1)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_login_id_input"),
                        singleLine = true
                    )

                    if (forgotLoginId.trim().isNotEmpty()) {
                        if (matchedForgotUser != null) {
                            Text(
                                text = "✓ Account verified: ${matchedForgotUser.fullName} (${matchedForgotUser.role} • ${matchedForgotUser.departmentCode})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriorityLowGreen
                            )
                        } else {
                            Text(
                                text = "✗ No account found matching '${forgotLoginId.trim()}'",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriorityHighRed
                            )
                        }
                    }

                    Text(
                        text = "2. Security CAPTCHA Verification",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    CaptchaChallengeBox(
                        captchaCode = captchaCode,
                        onRefresh = {
                            captchaCode = generateCaptchaCode()
                            captchaInput = ""
                        }
                    )

                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = { captchaInput = it.uppercase() },
                        label = { Text("Enter 6-Character CAPTCHA Shown Above") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_captcha_input"),
                        singleLine = true
                    )

                    if (captchaInput.trim().isNotEmpty()) {
                        Text(
                            text = if (isCaptchaVerified) "✓ CAPTCHA security code verified!" else "Keep typing the 6-character code shown above...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCaptchaVerified) PriorityLowGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("3. New Password (min. 4 chars)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle new password"
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_new_password_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("4. Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle confirm password"
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_confirm_password_input"),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (!isForgotMode) {
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
            } else {
                Button(
                    onClick = {
                        forgotError = null
                        if (forgotLoginId.isBlank()) {
                            forgotError = "Please enter your Login ID."
                            return@Button
                        }
                        if (captchaInput.trim().uppercase() != captchaCode.uppercase()) {
                            forgotError = "Incorrect CAPTCHA code. Please try the new code generated."
                            captchaCode = generateCaptchaCode()
                            captchaInput = ""
                            return@Button
                        }
                        if (newPassword.length < 4) {
                            forgotError = "New password must be at least 4 characters."
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            forgotError = "New password and confirm password do not match."
                            return@Button
                        }
                        viewModel.resetPassword(forgotLoginId, newPassword) { success, msg ->
                            if (success) {
                                loginId = forgotLoginId.trim()
                                password = ""
                                resetSuccessBanner = msg
                                isForgotMode = false
                            } else {
                                forgotError = msg
                                captchaCode = generateCaptchaCode()
                                captchaInput = ""
                            }
                        }
                    },
                    modifier = Modifier.testTag("forgot_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Verify & Reset Password")
                }
            }
        },
        dismissButton = {
            if (!isForgotMode) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        forgotError = null
                        isForgotMode = false
                    },
                    modifier = Modifier.testTag("back_to_signin_button")
                ) {
                    Text("Back to Sign In")
                }
            }
        }
    )
}
