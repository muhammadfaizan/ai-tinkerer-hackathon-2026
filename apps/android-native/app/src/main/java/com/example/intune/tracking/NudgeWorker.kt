package com.example.intune.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
        if (!UsageAccess.isGranted(applicationContext)) return Result.success()
        val repository = GoalsRepository(applicationContext)
        val goals = repository.goals.first()
        val app = UsageStatsHelper(applicationContext).currentApp() ?: return Result.success()
        if (goals.isEmpty() || app.durationMin <= 2 || app.packageName == repository.lastCheckedPackage()) return Result.success()

        val response = runCatching {
            createNudgeApi().nudge(NudgeRequest(goals, ActivityPayload(app.label, app.durationMin, timeOfDay(), app.category)))
        }.getOrElse { return Result.retry() }
        repository.saveLastCheckedPackage(app.packageName)
        if (response.shouldNotify) notify(response)
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
