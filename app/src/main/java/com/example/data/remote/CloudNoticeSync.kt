package com.example.data.remote

import android.content.Context
import android.util.Base64
import com.example.data.model.AttachmentEntity
import com.example.data.model.NoticeEntity
import com.example.data.model.NoticeStatus
import com.example.data.model.UserRole
import com.example.data.repository.NoticeBoardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object CloudNoticeSync {
    private const val SYNC_TOPIC = "gce_salem_notice_board_sync_v4"
    private const val BASE_URL = "https://ntfy.sh/$SYNC_TOPIC"
    private const val FILES_TOPIC = "gce_salem_notice_files_v4"
    private const val FILES_URL = "https://ntfy.sh/$FILES_TOPIC"
    private const val BLOBS_TOPIC = "gce_salem_notice_blobs_v4"
    private const val BLOBS_URL = "https://ntfy.sh/$BLOBS_TOPIC"

    private var attachmentsDir: File? = null
    private val cloudBlobChunks = mutableMapOf<Long, MutableMap<Int, String>>()
    private val cloudBlobTotals = mutableMapOf<Long, Int>()
    private val cloudBlobComplete = mutableMapOf<Long, String>()

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        attachmentsDir = dir
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val deletedIds = mutableSetOf<Long>()

    private fun mimeForType(filename: String, fileType: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || fileType == "PDF" -> "application/pdf"
            ext == "png" -> "image/png"
            ext == "jpg" || ext == "jpeg" || fileType == "IMAGE" -> "image/jpeg"
            ext == "webp" -> "image/webp"
            ext == "csv" || ext == "xls" || ext == "xlsx" -> "text/csv"
            else -> "application/octet-stream"
        }
    }

    private fun uploadAttachmentBinaryAndChunks(att: AttachmentEntity, attId: Long): String {
        var remoteUrl = ""
        try {
            val dir = attachmentsDir ?: return ""
            val localFile = File(dir, att.storedFilename).takeIf { it.exists() }
                ?: File(dir, att.originalFilename).takeIf { it.exists() }
                ?: return ""

            val bytes = localFile.readBytes()
            if (bytes.isEmpty()) return ""

            val safeName = att.originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment.bin" }
            val mime = mimeForType(att.originalFilename, att.fileType)

            // 1. Direct binary PUT to FILES_URL
            try {
                val putReq = Request.Builder()
                    .url(FILES_URL)
                    .header("Filename", safeName)
                    .put(bytes.toRequestBody(mime.toMediaType()))
                    .build()
                client.newCall(putReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        if (bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            remoteUrl = json.optJSONObject("attachment")?.optString("url", "") ?: ""
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Single-POST FILE_FULL upload to BLOBS_URL for files <= 180 KB so all devices can preview/download rapidly
            if (bytes.size <= 180 * 1024) {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUrl = "data:$mime;base64,$b64"
                val fullJson = JSONObject().apply {
                    put("action", "FILE_FULL")
                    put("attId", attId)
                    put("filename", att.originalFilename)
                    put("fileType", att.fileType)
                    put("dataUrl", dataUrl)
                }
                val postReq = Request.Builder()
                    .url(BLOBS_URL)
                    .post(fullJson.toString().toRequestBody("text/plain; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(postReq).execute().close()
            }
        } catch (_: Exception) {}
        return remoteUrl
    }

    private fun saveDataUrlToLocalFile(dataUrl: String, targetFilename: String): Boolean {
        try {
            val dir = attachmentsDir ?: return false
            if (!dir.exists()) dir.mkdirs()
            if (dataUrl.startsWith("data:") && dataUrl.contains(";base64,")) {
                val b64 = dataUrl.substringAfter(";base64,", "")
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    if (bytes.isNotEmpty()) {
                        File(dir, targetFilename).writeBytes(bytes)
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun downloadRemoteUrlToLocalFile(remoteUrl: String, targetFilename: String): Boolean {
        if (remoteUrl.isBlank()) return false
        try {
            val dir = attachmentsDir ?: return false
            if (!dir.exists()) dir.mkdirs()
            val req = Request.Builder().url(remoteUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        File(dir, targetFilename).writeBytes(bytes)
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun pullCloudBlobs() {
        try {
            val req = Request.Builder()
                .url("$BLOBS_URL/json?poll=1&since=24h")
                .get()
                .build()
            val text = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                resp.body?.string() ?: ""
            }
            if (text.isBlank()) return
            for (line in text.lines()) {
                if (line.isBlank()) continue
                try {
                    val env = JSONObject(line)
                    if (env.optString("event") != "message") continue
                    var msg = env.optString("message", "")
                    val attObj = env.optJSONObject("attachment")
                    if (attObj != null && attObj.optString("url").isNotBlank()) {
                        try {
                            val attReq = Request.Builder().url(attObj.optString("url")).get().build()
                            client.newCall(attReq).execute().use { r ->
                                if (r.isSuccessful) {
                                    msg = r.body?.string() ?: msg
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (msg.isBlank()) continue
                    val payload = JSONObject(msg)
                    val action = payload.optString("action")
                    if (action == "FILE_FULL") {
                        val attId = payload.optLong("attId", 0L)
                        val dataUrl = payload.optString("dataUrl", "")
                        if (attId > 0L && dataUrl.startsWith("data:")) {
                            cloudBlobComplete[attId] = dataUrl
                        }
                    } else if (action == "FILE_CHUNK") {
                        val attId = payload.optLong("attId", 0L)
                        if (attId <= 0L) continue
                        val idx = payload.optInt("idx", 0)
                        val total = payload.optInt("total", 1)
                        val chunk = payload.optString("chunk", "")
                        val map = cloudBlobChunks.getOrPut(attId) { mutableMapOf() }
                        map[idx] = chunk
                        cloudBlobTotals[attId] = total
                        if (map.size >= total) {
                            val sb = StringBuilder()
                            for (i in 0 until total) {
                                sb.append(map[i] ?: "")
                            }
                            val full = sb.toString()
                            if (full.startsWith("data:")) {
                                cloudBlobComplete[attId] = full
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    suspend fun pushUpsert(
        notice: NoticeEntity,
        authorLoginId: String,
        attachments: List<AttachmentEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val attArray = JSONArray()
            attachments.forEach { att ->
                val attId = if (att.id > 0) att.id else System.currentTimeMillis()
                val remoteUrl = uploadAttachmentBinaryAndChunks(att, attId)
                val attJson = JSONObject().apply {
                    put("id", attId)
                    put("filename", att.originalFilename)
                    put("fileType", att.fileType)
                    put("size", att.fileSizeBytes)
                    put("remoteUrl", remoteUrl)
                    put("previewText", att.previewSnippet.take(280))
                }
                attArray.put(attJson)
            }

            val noticeJson = JSONObject().apply {
                put("id", notice.id)
                put("title", notice.title)
                put("description", notice.description)
                put("department", notice.departmentCode)
                put("category", notice.category)
                put("priority", notice.priority)
                put("authorId", notice.authorId)
                put("authorLoginId", authorLoginId)
                put("authorName", notice.authorName)
                put("authorRole", notice.authorRole)
                put("publishedAt", notice.publishedAt)
                put("expiresAt", notice.expiresAt)
                put("isPinned", notice.isPinned)
                put("visibility", notice.visibility)
                put("attachments", attArray)
            }

            val payload = JSONObject().apply {
                put("action", "UPSERT")
                put("notice", noticeJson)
                put("ts", System.currentTimeMillis())
            }

            val body = payload.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(BASE_URL)
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pushDelete(noticeId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            deletedIds.add(noticeId)
            val payload = JSONObject().apply {
                put("action", "DELETE")
                put("noticeId", noticeId)
                put("ts", System.currentTimeMillis())
            }
            val body = payload.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(BASE_URL)
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pushPasswordReset(loginId: String, newPasswordPlain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "PASSWORD_RESET")
                put("loginId", loginId.trim())
                put("newPassword", newPasswordPlain)
                put("ts", System.currentTimeMillis())
            }
            val body = payload.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(BASE_URL)
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pullAndApplyRemoteNotices(repository: NoticeBoardRepository): Int = withContext(Dispatchers.IO) {
        var appliedCount = 0
        try {
            pullCloudBlobs()

            val req = Request.Builder()
                .url("$BASE_URL/json?poll=1&since=24h")
                .get()
                .build()

            val responseText = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 0
                resp.body?.string() ?: ""
            }

            if (responseText.isBlank()) return@withContext 0

            val lines = responseText.lines().filter { it.isNotBlank() }
            for (line in lines) {
                try {
                    val envelope = JSONObject(line)
                    if (envelope.optString("event") != "message") continue
                    var msgStr = envelope.optString("message", "")
                    val attObj = envelope.optJSONObject("attachment")
                    if (attObj != null && attObj.optString("url").isNotBlank()) {
                        try {
                            val attReq = Request.Builder().url(attObj.optString("url")).get().build()
                            client.newCall(attReq).execute().use { r ->
                                if (r.isSuccessful) {
                                    msgStr = r.body?.string() ?: msgStr
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (msgStr.isBlank()) continue

                    val payload = JSONObject(msgStr)
                    val action = payload.optString("action", "")

                    if (action == "DELETE") {
                        val noticeId = payload.optLong("noticeId", 0L)
                        if (noticeId > 0L) {
                            deletedIds.add(noticeId)
                            repository.deleteNoticeById(noticeId)
                            appliedCount++
                        }
                    } else if (action == "PASSWORD_RESET") {
                        val loginId = payload.optString("loginId", "").trim()
                        val newPass = payload.optString("newPassword", "")
                        if (loginId.isNotEmpty() && newPass.isNotEmpty()) {
                            repository.resetPassword(loginId, newPass)
                        }
                    } else if (action == "UPSERT") {
                        val nObj = payload.optJSONObject("notice") ?: continue
                        val rawId = nObj.optLong("id", 0L)
                        if (rawId <= 0L || deletedIds.contains(rawId)) continue

                        val title = nObj.optString("title", "").trim()
                        val desc = nObj.optString("description", "").trim()
                        if (title.isEmpty() || desc.isEmpty()) continue

                        val dept = nObj.optString("department", nObj.optString("departmentCode", "ALL"))
                        val category = nObj.optString("category", "General")
                        val priority = nObj.optString("priority", "MEDIUM")
                        val authorId = nObj.optLong("authorId", 1L)
                        val authorName = nObj.optString("authorName", "Admin Principal")
                        val authorRole = nObj.optString("authorRole", UserRole.ADMIN.name)
                        val publishedAt = nObj.optLong("publishedAt", System.currentTimeMillis())
                        val expiresAt = nObj.optLong("expiresAt", 0L)
                        val isPinned = nObj.optBoolean("isPinned", false)
                        val visibility = nObj.optString("visibility", "PUBLIC")

                        val entity = NoticeEntity(
                            id = rawId,
                            title = title,
                            description = desc,
                            departmentCode = dept,
                            category = category,
                            priority = priority,
                            authorId = authorId,
                            authorName = authorName,
                            authorRole = authorRole,
                            publishedAt = publishedAt,
                            expiresAt = expiresAt,
                            status = NoticeStatus.PUBLISHED.name,
                            isPinned = isPinned,
                            visibility = visibility,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.insertNotice(entity)

                        val attArr = nObj.optJSONArray("attachments")
                        if (attArr != null && attArr.length() > 0) {
                            for (i in 0 until attArr.length()) {
                                val aObj = attArr.optJSONObject(i) ?: continue
                                val attId = aObj.optLong("id", rawId * 100 + i + 1)
                                val filename = aObj.optString("filename", "Circular.pdf")
                                val safeStoredName = "${attId}_${filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                                val fileType = aObj.optString("fileType", "PDF")
                                var size = aObj.optLong("size", 0L)
                                val preview = aObj.optString("previewText", "Official GCE Salem Circular")
                                val remoteUrl = aObj.optString("remoteUrl", "")
                                val inlineDataUrl = aObj.optString("dataUrl", "")
                                val uploadedBy = nObj.optString("authorLoginId", "principal")

                                val dir = attachmentsDir
                                val targetFile = if (dir != null) File(dir, safeStoredName) else null
                                if (targetFile != null && (!targetFile.exists() || targetFile.length() < 4000L)) {
                                    var saved = false
                                    val fullBlob = cloudBlobComplete[attId] ?: ""
                                    if (fullBlob.isNotBlank()) {
                                        saved = saveDataUrlToLocalFile(fullBlob, safeStoredName)
                                    }
                                    if (!saved && remoteUrl.isNotBlank()) {
                                        saved = downloadRemoteUrlToLocalFile(remoteUrl, safeStoredName)
                                    }
                                    if (!saved && inlineDataUrl.isNotBlank() && !targetFile.exists()) {
                                        saveDataUrlToLocalFile(inlineDataUrl, safeStoredName)
                                    }
                                }
                                if (targetFile != null && targetFile.exists() && targetFile.length() >= 4000L) {
                                    size = targetFile.length()
                                }

                                repository.insertAttachment(
                                    AttachmentEntity(
                                        id = attId,
                                        noticeId = rawId,
                                        originalFilename = filename,
                                        storedFilename = safeStoredName,
                                        fileType = fileType,
                                        fileSizeBytes = size,
                                        uploadedBy = uploadedBy,
                                        previewSnippet = preview
                                    )
                                )
                            }
                        }
                        appliedCount++
                    }
                } catch (_: Exception) {
                    // Ignore malformed line
                }
            }
        } catch (_: Exception) {
            // Network unavailable or test environment
        }
        appliedCount
    }
}
