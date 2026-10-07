package com.example.intune.data

import com.example.intune.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

private val baseUrl = "${BuildConfig.BASE_URL.trimEnd('/')}/"

data class ActivityPayload(
    val app: String,
    val durationMin: Int,
    val timeOfDay: String,
    val category: String? = null,
)
data class SessionAppPayload(val app: String, val durationMin: Int)
data class SessionPayload(val apps: List<SessionAppPayload>, val totalDurationMin: Int, val windowMin: Int = 30)
data class RoutineContextPayload(val label: String, val dayPattern: String, val approxStartHour: Int, val approxEndHour: Int)
data class NudgeRequest(
    val goals: List<String>,
    val activity: ActivityPayload? = null,
    val session: SessionPayload? = null,
    val routineContext: List<RoutineContextPayload>? = null,
)
data class NudgeResponse(
    val classification: String,
    val shouldNotify: Boolean,
    val message: String,
    val microAction: String,
    val groundingUsed: Boolean,
    val error: Boolean = false,
)
data class ParseGoalsRequest(val transcript: String, val existingGoals: List<String>)
data class ParseGoalsResponse(val goals: List<String>)
data class AppVersionResponse(
    val latestVersionCode: Int,
    val minVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val notes: String,
)

interface NudgeApi {
    @POST("nudge")
    suspend fun nudge(@Body request: NudgeRequest): NudgeResponse

    @POST("parse-goals")
    suspend fun parseGoals(@Body request: ParseGoalsRequest): ParseGoalsResponse

    @GET("app-version")
    suspend fun appVersion(): AppVersionResponse
}

fun createNudgeApi(): NudgeApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    if (BuildConfig.APP_API_KEY.isNotBlank()) header("X-App-Key", BuildConfig.APP_API_KEY)
                }.build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build(),
    )
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(NudgeApi::class.java)
