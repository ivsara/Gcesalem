package com.example.data.remote

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
import java.util.concurrent.TimeUnit

object CloudNoticeSync {
    private const val SYNC_TOPIC = "gce_salem_notice_board_sync_v4"
    private const val BASE_URL = "https://ntfy.sh/$SYNC_TOPIC"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val deletedIds = mutableSetOf<Long>()

    suspend fun pushUpsert(
        notice: NoticeEntity,
        authorLoginId: String,
        attachments: List<AttachmentEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val attArray = JSONArray()
            attachments.forEach { att ->
                val attJson = JSONObject().apply {
                    put("id", if (att.id > 0) att.id else System.currentTimeMillis())
                    put("filename", att.originalFilename)
                    put("fileType", att.fileType)
                    put("size", att.fileSizeBytes)
                    put("previewText", att.previewSnippet)
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

    suspend fun pullAndApplyRemoteNotices(repository: NoticeBoardRepository): Int = withContext(Dispatchers.IO) {
        var appliedCount = 0
        try {
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
                    val msgStr = envelope.optString("message", "")
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
                                val fileType = aObj.optString("fileType", "PDF")
                                val size = aObj.optLong("size", 102400L)
                                val preview = aObj.optString("previewText", "Official GCE Salem Circular")
                                val uploadedBy = nObj.optString("authorLoginId", "principal")

                                repository.insertAttachment(
                                    AttachmentEntity(
                                        id = attId,
                                        noticeId = rawId,
                                        originalFilename = filename,
                                        storedFilename = filename,
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
