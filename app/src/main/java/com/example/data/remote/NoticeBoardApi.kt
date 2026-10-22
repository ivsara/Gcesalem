package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** DTOs and HTTP contract shared with the FastAPI web backend. */
data class LoginPayload(val login_id: String, val password: String)
data class ApiUser(val login_id: String, val full_name: String, val role: String, val department: String?)
data class LoginResult(val access_token: String, val token_type: String, val user: ApiUser)
data class ApiAttachment(val id: Int, val filename: String, val size: Long, val content_type: String? = null, val url: String)
data class ApiNotice(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val priority: String,
    val department_id: Int?,
    val department: String,
    val department_name: String,
    val author: String,
    val published_at: String?,
    val expires_at: String?,
    val is_pinned: Boolean,
    val visibility: String,
    val attachments: List<ApiAttachment> = emptyList()
)
data class NoticeListResult(val notices: List<ApiNotice>, val server_time: String)
data class CreateNoticePayload(
    val title: String,
    val description: String,
    val department: String = "ALL",
    val category: String = "General",
    val priority: String = "MEDIUM",
    val visibility: String = "PUBLIC",
    val expires_at: String? = null,
    val is_pinned: Boolean = false
)
data class UploadResult(val uploaded: List<ApiAttachment>)

interface NoticeBoardApi {
    @POST("api/auth/login") suspend fun login(@Body payload: LoginPayload): LoginResult
    @GET("api/auth/me") suspend fun me(): ApiUser
    @GET("api/departments") suspend fun departments(): List<Map<String, Any>>
    @GET("api/notices") suspend fun notices(
        @Query("department") department: String = "ALL",
        @Query("category") category: String = "ALL",
        @Query("q") query: String = ""
    ): NoticeListResult
    @POST("api/notices") suspend fun createNotice(@Body payload: CreateNoticePayload): ApiNotice
    @PUT("api/notices/{noticeId}") suspend fun updateNotice(@Path("noticeId") noticeId: Int, @Body payload: Map<String, Any?>): ApiNotice
    @DELETE("api/notices/{noticeId}") suspend fun deleteNotice(@Path("noticeId") noticeId: Int): Map<String, Boolean>
    @Multipart @POST("api/notices/{noticeId}/attachments") suspend fun uploadAttachments(
        @Path("noticeId") noticeId: Int,
        @Part files: List<MultipartBody.Part>
    ): UploadResult
}

/** Set [token] after login. The default URL targets the Android emulator; override API_BASE_URL for a device/server. */
object NoticeBoardApiClient {
    @Volatile var token: String? = null

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(request)
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
    private val retrofit: Retrofit by lazy {
        val baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(MoshiConverterFactory.create(moshi)).build()
    }
    val service: NoticeBoardApi by lazy { retrofit.create(NoticeBoardApi::class.java) }
}
