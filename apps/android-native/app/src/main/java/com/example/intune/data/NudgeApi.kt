package com.example.intune.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

const val BASE_URL = "https://nudge-backend-olive.vercel.app/"

data class ActivityPayload(
    val app: String,
    val durationMin: Int,
    val timeOfDay: String,
    val category: String? = null,
)
data class NudgeRequest(val goals: List<String>, val activity: ActivityPayload)
data class NudgeResponse(
    val classification: String,
    val shouldNotify: Boolean,
    val message: String,
    val microAction: String,
    val groundingUsed: Boolean,
)

interface NudgeApi {
    @POST("nudge")
    suspend fun nudge(@Body request: NudgeRequest): NudgeResponse
}

fun createNudgeApi(): NudgeApi = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }).build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(NudgeApi::class.java)
