package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.AttachmentEntity
import com.example.data.model.NoticeEntity
import com.example.data.model.NoticeStatus
import com.example.data.model.UserRole
import com.example.data.repository.NoticeBoardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

object CloudNoticeSync {
    private const val SYNC_TOPIC = "gce_salem_notice_sync_v5"
    private const val BASE_URL = "https://ntfy.sh/$SYNC_TOPIC"
    private const val LEGACY_SYNC_TOPIC = "gce_salem_notice_board_sync_v4"
    private const val LEGACY_BASE_URL = "https://ntfy.sh/$LEGACY_SYNC_TOPIC"
    private const val FILES_TOPIC = "gce_salem_notice_files_v4"
    private const val FILES_URL = "https://ntfy.sh/$FILES_TOPIC"
    private const val BLOBS_TOPIC = "gce_salem_notice_blobs_v4"
    private const val BLOBS_URL = "https://ntfy.sh/$BLOBS_TOPIC"

    private var attachmentsDir: File? = null
    private val cloudBlobChunks = mutableMapOf<Long, MutableMap<Int, String>>()
    private val cloudBlobTotals = mutableMapOf<Long, Int>()
    private val cloudBlobComplete = mutableMapOf<Long, String>()
    private val knownRemoteUrls = mutableMapOf<Long, String>()
    private val processedBlobEnvIds = mutableSetOf<String>()
    private val processedAttachmentEnvPayloads = mutableMapOf<String, String>()
    private val syncedCloudNoticeIds = mutableSetOf<Long>()
    @Volatile
    private var legacyV4Checked = false

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        attachmentsDir = dir
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
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
            ext == "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ext == "doc" -> "application/msword"
            ext == "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ext == "xls" -> "application/vnd.ms-excel"
            ext == "csv" -> "text/csv"
            ext == "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun compressImageBytes(rawBytes: ByteArray, maxDim: Int, quality: Int): ByteArray? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val largest = max(w, h)
            val scaled = if (largest > maxDim) {
                val ratio = maxDim.toFloat() / largest.toFloat()
                Bitmap.createScaledBitmap(
                    bmp,
                    (w * ratio).toInt().coerceAtLeast(1),
                    (h * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bmp
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCompactInlineDataUrl(att: AttachmentEntity): String {
        try {
            val dir = attachmentsDir ?: return ""
            val localFile = File(dir, att.storedFilename).takeIf { it.exists() }
                ?: File(dir, att.originalFilename).takeIf { it.exists() }
                ?: return ""
            val bytes = localFile.readBytes()
            if (bytes.isEmpty()) return ""
            val mime = mimeForType(att.originalFilename, att.fileType)
            val isImage = att.fileType.equals("IMAGE", ignoreCase = true) || mime.startsWith("image/")
            if (isImage) {
                for ((dim, q) in listOf(108 to 40, 84 to 34, 64 to 28)) {
                    val thumbBytes = compressImageBytes(bytes, maxDim = dim, quality = q)
                    if (thumbBytes != null && thumbBytes.size <= 1450) {
                        return "data:image/jpeg;base64," + Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun uploadAttachmentBinary(att: AttachmentEntity, attId: Long): String {
        var remoteUrl = knownRemoteUrls[attId] ?: ""
        try {
            val dir = attachmentsDir ?: return remoteUrl
            val localFile = File(dir, att.storedFilename).takeIf { it.exists() }
                ?: File(dir, att.originalFilename).takeIf { it.exists() }
                ?: return remoteUrl

            val bytes = localFile.readBytes()
            if (bytes.isEmpty()) return remoteUrl

            val safeName = att.originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment.bin" }
            val mime = mimeForType(att.originalFilename, att.fileType)
            val isImage = att.fileType.equals("IMAGE", ignoreCase = true) || mime.startsWith("image/")

            // Optimize large camera photos to crisp 1600px JPEG while keeping documents untouched up to 5 MB
            var uploadBytes = bytes
            var uploadMime = mime
            if (isImage && bytes.size > 220 * 1024) {
                var compressed = compressImageBytes(bytes, maxDim = 1600, quality = 82)
                if (compressed != null && compressed.size > 520 * 1024) {
                    compressed = compressImageBytes(bytes, maxDim = 1280, quality = 76)
                }
                if (compressed != null && compressed.isNotEmpty()) {
                    uploadBytes = compressed
                    uploadMime = "image/jpeg"
                }
            }

            // 1. Primary upload: POST JSON FILE_FULL envelope to BLOBS_URL (works across both Web and Android)
            if (uploadBytes.size <= 5 * 1024 * 1024) {
                try {
                    val b64 = Base64.encodeToString(uploadBytes, Base64.NO_WRAP)
                    val dataUrl = "data:$uploadMime;base64,$b64"
                    cloudBlobComplete[attId] = dataUrl
                    val fullJson = JSONObject().apply {
                        put("action", "FILE_FULL")
                        put("attId", attId)
                        put("filename", att.originalFilename)
                        put("fileType", att.fileType)
                        put("size", att.fileSizeBytes)
                        put("dataUrl", dataUrl)
                    }
                    val postReq = Request.Builder()
                        .url(BLOBS_URL)
                        .post(fullJson.toString().toRequestBody("text/plain; charset=utf-8".toMediaType()))
                        .build()
                    client.newCall(postReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            if (bodyStr.isNotBlank()) {
                                val json = JSONObject(bodyStr)
                                val url = json.optJSONObject("attachment")?.optString("url", "") ?: ""
                                if (url.isNotBlank()) {
                                    remoteUrl = url
                                    knownRemoteUrls[attId] = url
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Secondary direct binary PUT to FILES_URL if remoteUrl is still empty
            if (remoteUrl.isBlank()) {
                try {
                    val putReq = Request.Builder()
                        .url(FILES_URL)
                        .header("Filename", safeName)
                        .put(uploadBytes.toRequestBody(uploadMime.toMediaType()))
                        .build()
                    client.newCall(putReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            if (bodyStr.isNotBlank()) {
                                val json = JSONObject(bodyStr)
                                val url = json.optJSONObject("attachment")?.optString("url", "") ?: ""
                                if (url.isNotBlank()) {
                                    remoteUrl = url
                                    knownRemoteUrls[attId] = url
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Chunked inline fallback for attachments <= 180 KB if ntfy attachment URL wasn't returned
            if (remoteUrl.isBlank() && uploadBytes.size <= 180 * 1024) {
                try {
                    val dataUrl = cloudBlobComplete[attId]
                        ?: ("data:$uploadMime;base64," + Base64.encodeToString(uploadBytes, Base64.NO_WRAP))
                    val chunkSize = 2800
                    val total = (dataUrl.length + chunkSize - 1) / chunkSize
                    for (idx in 0 until total) {
                        val start = idx * chunkSize
                        val end = minOf(dataUrl.length, (idx + 1) * chunkSize)
                        val chunkJson = JSONObject().apply {
                            put("action", "FILE_CHUNK")
                            put("attId", attId)
                            put("filename", att.originalFilename)
                            put("idx", idx)
                            put("total", total)
                            put("chunk", dataUrl.substring(start, end))
                        }
                        val req = Request.Builder()
                            .url(BLOBS_URL)
                            .post(chunkJson.toString().toRequestBody("text/plain; charset=utf-8".toMediaType()))
                            .build()
                        client.newCall(req).execute().close()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return remoteUrl
    }

    private fun saveDataUrlToLocalFile(dataUrl: String, targetFilename: String): Boolean {
        try {
            val dir = attachmentsDir ?: return false
            if (!dir.exists()) dir.mkdirs()
            val trimmed = dataUrl.trim()
            if (trimmed.startsWith("data:") && trimmed.contains(";base64,")) {
                val b64 = trimmed.substringAfter(";base64,", "").replace(Regex("\\s+"), "")
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
                        val prefix = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8).trimStart()
                        if (prefix.startsWith("{")) {
                            val jsonStr = String(bytes, Charsets.UTF_8)
                            val obj = runCatching { JSONObject(jsonStr) }.getOrNull()
                            val embeddedDataUrl = obj?.optString("dataUrl", "") ?: ""
                            if (embeddedDataUrl.startsWith("data:") && embeddedDataUrl.contains(";base64,")) {
                                return saveDataUrlToLocalFile(embeddedDataUrl, targetFilename)
                            }
                        } else if (prefix.startsWith("data:") && prefix.contains(";base64,")) {
                            return saveDataUrlToLocalFile(String(bytes, Charsets.UTF_8), targetFilename)
                        }
                        File(dir, targetFilename).writeBytes(bytes)
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    suspend fun ensureAttachmentHydrated(att: AttachmentEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = attachmentsDir ?: return@withContext false
            if (!dir.exists()) dir.mkdirs()
            val targetName = att.storedFilename.ifBlank { att.originalFilename }
            val targetFile = File(dir, targetName)
            if (targetFile.exists() && targetFile.length() > 4096L) {
                return@withContext true
            }
            val memBlob = cloudBlobComplete[att.id] ?: ""
            if (memBlob.isNotBlank() && saveDataUrlToLocalFile(memBlob, targetName)) {
                return@withContext true
            }
            val remoteUrl = knownRemoteUrls[att.id] ?: ""
            if (remoteUrl.isNotBlank() && downloadRemoteUrlToLocalFile(remoteUrl, targetName)) {
                return@withContext true
            }
            pullCloudBlobs()
            val afterBlob = cloudBlobComplete[att.id] ?: ""
            if (afterBlob.isNotBlank() && saveDataUrlToLocalFile(afterBlob, targetName)) {
                return@withContext true
            }
            val afterRemoteUrl = knownRemoteUrls[att.id] ?: ""
            if (afterRemoteUrl.isNotBlank() && downloadRemoteUrlToLocalFile(afterRemoteUrl, targetName)) {
                return@withContext true
            }
            targetFile.exists() && targetFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun pullCloudBlobs() {
        try {
            val req = Request.Builder()
                .url("$BLOBS_URL/json?poll=1&since=24h&_t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
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
                    val envId = env.optString("id", "")
                    if (envId.isNotBlank() && processedBlobEnvIds.contains(envId)) continue

                    var msg = env.optString("message", "")
                    val attObj = env.optJSONObject("attachment")
                    val envAttUrl = attObj?.optString("url", "") ?: ""
                    if (envAttUrl.isNotBlank()) {
                        try {
                            val attReq = Request.Builder().url(envAttUrl).get().build()
                            client.newCall(attReq).execute().use { r ->
                                if (r.isSuccessful) {
                                    msg = r.body?.string() ?: msg
                                    if (envId.isNotBlank()) processedBlobEnvIds.add(envId)
                                }
                            }
                        } catch (_: Exception) {}
                    } else if (envId.isNotBlank()) {
                        processedBlobEnvIds.add(envId)
                    }
                    if (msg.isBlank()) continue
                    val payload = JSONObject(msg)
                    val action = payload.optString("action")
                    if (action == "FILE_FULL") {
                        val attId = payload.optLong("attId", 0L)
                        val dataUrl = payload.optString("dataUrl", "")
                        if (attId > 0L && dataUrl.startsWith("data:")) {
                            cloudBlobComplete[attId] = dataUrl
                            if (envAttUrl.isNotBlank()) {
                                knownRemoteUrls[attId] = envAttUrl
                            }
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

    private fun postNoticeEventJson(payload: JSONObject): Boolean {
        var jsonStr = payload.toString()
        // Guarantee payload is strictly under 3700 bytes so ntfy.sh stores it inline in env.message
        if (jsonStr.toByteArray(Charsets.UTF_8).size > 3700) {
            val noticeObj = payload.optJSONObject("notice")
            if (noticeObj != null) {
                val desc = noticeObj.optString("description", "")
                if (desc.length > 1400) {
                    noticeObj.put("description", desc.take(1400))
                }
            }
            jsonStr = payload.toString()
            if (jsonStr.toByteArray(Charsets.UTF_8).size > 3700) {
                val attArr = noticeObj?.optJSONArray("attachments")
                if (attArr != null) {
                    for (i in 0 until attArr.length()) {
                        attArr.optJSONObject(i)?.remove("dataUrl")
                    }
                }
                jsonStr = payload.toString()
            }
        }

        val mediaType = "text/plain; charset=utf-8".toMediaType()
        for (attempt in 0..1) {
            try {
                val req = Request.Builder()
                    .url(BASE_URL)
                    .header("Cache-Control", "no-cache")
                    .post(jsonStr.toRequestBody(mediaType))
                    .build()
                val ok = client.newCall(req).execute().use { it.isSuccessful }
                if (ok) return true
                if (attempt == 0) Thread.sleep(450)
            } catch (_: Exception) {
                if (attempt == 0) runCatching { Thread.sleep(450) }
            }
        }
        return false
    }

    private fun buildUpsertPayload(
        notice: NoticeEntity,
        authorLoginId: String,
        attachments: List<AttachmentEntity>,
        remoteUrlsByAttId: Map<Long, String>,
        inlineDataUrlsByAttId: Map<Long, String>
    ): JSONObject {
        val attArray = JSONArray()
        attachments.forEachIndexed { idx, att ->
            val attId = if (att.id > 0) att.id else (notice.id + idx + 1L)
            val remoteUrl = remoteUrlsByAttId[attId] ?: knownRemoteUrls[attId] ?: ""
            val inlineDataUrl = inlineDataUrlsByAttId[attId] ?: ""
            val attJson = JSONObject().apply {
                put("id", attId)
                put("filename", att.originalFilename)
                put("fileType", att.fileType)
                put("size", att.fileSizeBytes)
                put("remoteUrl", remoteUrl)
                put("isAuthentic", true)
                put("previewText", att.previewSnippet.take(320))
                if (inlineDataUrl.isNotBlank() && inlineDataUrl.length <= 2400) {
                    put("dataUrl", inlineDataUrl)
                }
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

        return JSONObject().apply {
            put("action", "UPSERT")
            put("notice", noticeJson)
            put("ts", System.currentTimeMillis())
        }
    }

    suspend fun pushUpsert(
        notice: NoticeEntity,
        authorLoginId: String,
        attachments: List<AttachmentEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inlineMap = mutableMapOf<Long, String>()
            val remoteMap = mutableMapOf<Long, String>()

            attachments.forEachIndexed { idx, att ->
                val attId = if (att.id > 0) att.id else (notice.id + idx + 1L)
                val inlineUrl = buildCompactInlineDataUrl(att)
                if (inlineUrl.isNotBlank()) {
                    inlineMap[attId] = inlineUrl
                }
                val remoteUrl = uploadAttachmentBinary(att, attId)
                if (remoteUrl.isNotBlank()) {
                    remoteMap[attId] = remoteUrl
                    knownRemoteUrls[attId] = remoteUrl
                }
            }

            val payload = buildUpsertPayload(
                notice = notice,
                authorLoginId = authorLoginId,
                attachments = attachments,
                remoteUrlsByAttId = remoteMap,
                inlineDataUrlsByAttId = inlineMap
            )
            val publishedOk = postNoticeEventJson(payload)
            if (publishedOk) {
                syncedCloudNoticeIds.add(notice.id)
            }
            publishedOk
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
            postNoticeEventJson(payload)
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
            postNoticeEventJson(payload)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun processSyncLines(
        lines: List<String>,
        allowRemoteAttachmentFetch: Boolean,
        recordInV5Set: Boolean,
        repository: NoticeBoardRepository
    ): Pair<Int, Boolean> {
        var appliedCount = 0
        var needsBlobPull = false
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        // Determine max envelope time in this batch for clock-skew normalization
        var maxEnvTimeMs = 0L
        for (line in lines) {
            runCatching {
                val env = JSONObject(line)
                val tSec = env.optLong("time", 0L)
                if (tSec * 1000L > maxEnvTimeMs) {
                    maxEnvTimeMs = tSec * 1000L
                }
            }
        }

        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val envelope = JSONObject(line)
                if (envelope.optString("event") != "message") continue
                val envId = envelope.optString("id", "")
                val envTimeMs = envelope.optLong("time", 0L) * 1000L
                var msgStr = envelope.optString("message", "")
                val attObj = envelope.optJSONObject("attachment")

                if (attObj != null && attObj.optString("url").isNotBlank()) {
                    if (envId.isNotBlank() && processedAttachmentEnvPayloads.containsKey(envId)) {
                        msgStr = processedAttachmentEnvPayloads[envId] ?: ""
                    } else if (allowRemoteAttachmentFetch) {
                        try {
                            val attReq = Request.Builder().url(attObj.optString("url")).get().build()
                            client.newCall(attReq).execute().use { r ->
                                if (r.isSuccessful) {
                                    msgStr = r.body?.string() ?: msgStr
                                    if (envId.isNotBlank() && msgStr.isNotBlank()) {
                                        processedAttachmentEnvPayloads[envId] = msgStr
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                if (msgStr.isBlank() || !msgStr.trimStart().startsWith("{")) continue

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
                    if (recordInV5Set) {
                        syncedCloudNoticeIds.add(rawId)
                    }

                    val title = nObj.optString("title", "").trim()
                    val desc = nObj.optString("description", "").trim()
                    if (title.isEmpty() || desc.isEmpty()) continue

                    val dept = nObj.optString("department", nObj.optString("departmentCode", "ALL"))
                    val category = nObj.optString("category", "General")
                    val priority = nObj.optString("priority", "MEDIUM")
                    val authorId = nObj.optLong("authorId", 1L)
                    val authorName = nObj.optString("authorName", "Admin Principal")
                    val authorRole = nObj.optString("authorRole", UserRole.ADMIN.name)
                    val rawPublishedAt = nObj.optLong("publishedAt", now)
                    val rawExpiresAt = nObj.optLong("expiresAt", 0L)
                    val isPinned = nObj.optBoolean("isPinned", false)
                    val visibility = nObj.optString("visibility", "PUBLIC")

                    // Normalize publishedAt & expiresAt across devices with skewed system clocks
                    val relativeAgeMs = if (maxEnvTimeMs > 0L && envTimeMs > 0L) {
                        (maxEnvTimeMs - envTimeMs).coerceAtLeast(0L)
                    } else 0L
                    val isClockSkewed = kotlin.math.abs(now - rawPublishedAt) > 12L * 3600L * 1000L
                    val normalizedPublishedAt = if (isClockSkewed) {
                        now - relativeAgeMs
                    } else {
                        rawPublishedAt
                    }
                    val normalizedExpiresAt = if (rawExpiresAt <= 0L) {
                        0L
                    } else if (rawExpiresAt >= rawPublishedAt) {
                        val durationMs = (rawExpiresAt - rawPublishedAt).coerceAtLeast(dayMs)
                        maxOf(rawExpiresAt, normalizedPublishedAt + durationMs, now + dayMs)
                    } else {
                        rawExpiresAt
                    }

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
                        publishedAt = normalizedPublishedAt,
                        expiresAt = normalizedExpiresAt,
                        status = NoticeStatus.PUBLISHED.name,
                        isPinned = isPinned,
                        visibility = visibility,
                        updatedAt = now
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
                            if (remoteUrl.isNotBlank()) {
                                knownRemoteUrls[attId] = remoteUrl
                            }
                            val inlineDataUrl = aObj.optString("dataUrl", "")
                            val uploadedBy = nObj.optString("authorLoginId", "principal")

                            val dir = attachmentsDir
                            val targetFile = if (dir != null) File(dir, safeStoredName) else null
                            if (targetFile != null) {
                                var saved = false
                                val fullBlob = cloudBlobComplete[attId] ?: ""
                                if (fullBlob.isNotBlank()) {
                                    saved = saveDataUrlToLocalFile(fullBlob, safeStoredName)
                                }
                                if (!saved && remoteUrl.isNotBlank() && (!targetFile.exists() || targetFile.length() < 4096L)) {
                                    saved = downloadRemoteUrlToLocalFile(remoteUrl, safeStoredName)
                                }
                                if (!saved && inlineDataUrl.isNotBlank() && (!targetFile.exists() || targetFile.length() == 0L)) {
                                    saved = saveDataUrlToLocalFile(inlineDataUrl, safeStoredName)
                                }
                                if (!saved && (!targetFile.exists() || targetFile.length() < 4096L)) {
                                    needsBlobPull = true
                                }
                            }
                            if (size <= 0L && targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
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
        return Pair(appliedCount, needsBlobPull)
    }

    suspend fun pullAndApplyRemoteNotices(repository: NoticeBoardRepository): Int = withContext(Dispatchers.IO) {
        var appliedCount = 0
        try {
            // 1. Fetch primary v5 sync feed first so notices appear immediately without waiting for blobs
            val req = Request.Builder()
                .url("$BASE_URL/json?poll=1&since=24h&_t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
                .get()
                .build()

            val responseText = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) "" else (resp.body?.string() ?: "")
            }

            var needsBlobPull = false
            if (responseText.isNotBlank()) {
                val lines = responseText.lines().filter { it.isNotBlank() }
                val (count, blobNeeded) = processSyncLines(
                    lines = lines,
                    allowRemoteAttachmentFetch = true,
                    recordInV5Set = true,
                    repository = repository
                )
                appliedCount += count
                if (blobNeeded) needsBlobPull = true
            }

            // 2. One-time fallback check of legacy v4 inline messages so recently created v4 notices are also imported
            if (!legacyV4Checked) {
                legacyV4Checked = true
                runCatching {
                    val v4Req = Request.Builder()
                        .url("$LEGACY_BASE_URL/json?poll=1&since=24h&_t=${System.currentTimeMillis()}")
                        .header("Cache-Control", "no-cache, no-store")
                        .get()
                        .build()
                    val v4Text = client.newCall(v4Req).execute().use { resp ->
                        if (!resp.isSuccessful) "" else (resp.body?.string() ?: "")
                    }
                    if (v4Text.isNotBlank()) {
                        val (v4Count, _) = processSyncLines(
                            lines = v4Text.lines().filter { it.isNotBlank() },
                            allowRemoteAttachmentFetch = false,
                            recordInV5Set = false,
                            repository = repository
                        )
                        appliedCount += v4Count
                    }
                }
            }

            // 3. Only pull cloud blobs if an attachment actually needs blob hydration
            if (needsBlobPull) {
                pullCloudBlobs()
            }
        } catch (_: Exception) {
            // Network unavailable or test environment
        }
        appliedCount
    }

    suspend fun syncLocalNoticesToCloud(
        notices: List<NoticeEntity>,
        repository: NoticeBoardRepository
    ) = withContext(Dispatchers.IO) {
        try {
            // Push any user-created notices (id > 1000) not yet confirmed in v5 cloud feed
            val unsynced = notices.filter {
                it.id > 1000L && !deletedIds.contains(it.id) && !syncedCloudNoticeIds.contains(it.id)
            }
            for (notice in unsynced.take(5)) {
                val atts: List<AttachmentEntity> = try {
                    repository.getAttachmentsForNotice(notice.id).firstOrNull() ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                pushUpsert(
                    notice = notice,
                    authorLoginId = "principal",
                    attachments = atts
                )
            }
        } catch (_: Exception) {}
    }
}
