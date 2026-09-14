package com.example.intune.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.intune.MainActivity
import com.example.intune.R
import com.example.intune.data.ActivityPayload
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.data.createNudgeApi
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val usageAccessGranted = UsageAccess.isGranted(applicationContext)
        Log.d(TAG, "Usage access granted: $usageAccessGranted")
        if (!usageAccessGranted) {
            Log.d(TAG, "Skipping /nudge: usage access is not granted")
            return Result.success()
        }
        val repository = GoalsRepository(applicationContext)
        val goals = repository.goals.first()
        val app = UsageStatsHelper(applicationContext).currentApp()
        if (app == null) {
            Log.d(TAG, "Foreground app: none detected; skipping /nudge")
            return Result.success()
        }
        Log.d(TAG, "Foreground app: package=${app.packageName}, label=${app.label}, category=${app.category ?: "none"}")
        Log.d(TAG, "Foreground duration: ${app.durationMin} minutes")
        val lastChecked = repository.lastCheckedPackage()
        val isSameApp = app.packageName == lastChecked
        Log.d(TAG, "Last checked app: ${lastChecked ?: "none"}; same app: $isSameApp")
        val meetsDurationThreshold = app.durationMin > 2
        Log.d(TAG, "Two-minute threshold met: $meetsDurationThreshold")
        if (goals.isEmpty()) {
            Log.d(TAG, "Skipping /nudge: no goals are stored")
            return Result.success()
        }
        if (!meetsDurationThreshold) {
            Log.d(TAG, "Skipping /nudge: foreground duration is not over 2 minutes")
            return Result.success()
        }
        if (isSameApp) {
            Log.d(TAG, "Skipping /nudge: foreground app matches the last checked app")
            return Result.success()
        }

        val request = NudgeRequest(goals, ActivityPayload(app.label, app.durationMin, timeOfDay(), app.category))
        Log.d(TAG, "Calling /nudge with request: $request")
        val response = runCatching { createNudgeApi().nudge(request) }.getOrElse {
            Log.e(TAG, "Calling /nudge failed; retrying", it)
            return Result.retry()
        }
        Log.d(TAG, "Received /nudge response: $response")
        repository.saveLastCheckedPackage(app.packageName)
        if (response.shouldNotify) {
            Log.d(TAG, "Response requires notification")
            notify(response)
        } else {
            Log.d(TAG, "Response does not require notification")
        }
        return Result.success()
    }

    private fun timeOfDay(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }

    private fun notify(nudge: NudgeResponse) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nudges", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_NUDGE_MESSAGE, nudge.message)
            .putExtra(EXTRA_MICRO_ACTION, nudge.microAction)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("A quick nudge")
            .setContentText(nudge.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build())
    }

    private companion object {
        const val TAG = "NudgeWorker"
        const val CHANNEL_ID = "nudges"
        const val NOTIFICATION_ID = 1
    }
}

const val EXTRA_NUDGE_MESSAGE = "nudge_message"
const val EXTRA_MICRO_ACTION = "micro_action"

fun scheduleNudgeWork(context: Context) {
    val request = PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("nudge-check", ExistingPeriodicWorkPolicy.KEEP, request)
}

fun triggerNudgeCheckNow(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "nudge-check-debug",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<NudgeWorker>().build(),
    )
}
