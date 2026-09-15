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
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.data.SessionAppPayload
import com.example.intune.data.SessionPayload
import com.example.intune.data.createNudgeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (isStopped) {
            Log.d(TAG, "Worker was already stopped; skipping work")
            return Result.failure()
        }
        val usageAccessGranted = UsageAccess.isGranted(applicationContext)
        Log.d(TAG, "Usage access granted: $usageAccessGranted")
        if (!usageAccessGranted) {
            Log.d(TAG, "Skipping /nudge: usage access is not granted")
            return Result.success()
        }
        val repository = GoalsRepository(applicationContext)
        val goals = repository.goals.first()
        val session = UsageStatsHelper(applicationContext).sessionSummary()
        if (goals.isEmpty()) {
            Log.d(TAG, "Skipping /nudge: no goals are stored")
            return Result.success()
        }
        if (session.apps.isEmpty()) {
            Log.d(TAG, "Skipping /nudge: no meaningful app usage in the session")
            return Result.success()
        }
        // A 30-minute rolling window cannot contain more than 30 minutes of foreground use.
        if (session.totalDurationMin < SESSION_THRESHOLD_MIN) {
            Log.d(TAG, "Skipping /nudge: session total ${session.totalDurationMin}m is under ${SESSION_THRESHOLD_MIN}m")
            return Result.success()
        }
        val now = System.currentTimeMillis()
        if (now - repository.lastNotifiedAt() < COOLDOWN_MS) {
            Log.d(TAG, "Skipping /nudge: session cooldown is active")
            return Result.success()
        }

        val request = NudgeRequest(
            goals = goals,
            session = SessionPayload(session.apps.map { SessionAppPayload(it.label, it.durationMin) }, session.totalDurationMin),
        )
        Log.d(TAG, "Calling /nudge with request: $request")
        val response = try {
            createNudgeApi().nudge(request)
        } catch (error: CancellationException) {
            Log.d(TAG, "Network call cancelled because WorkManager stopped the worker")
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Calling /nudge failed; retrying", error)
            return Result.retry()
        }
        if (isStopped) {
            Log.d(TAG, "Worker was stopped after /nudge returned")
            return Result.failure()
        }
        Log.d(TAG, "Received /nudge response: $response")
        repository.saveLastNotifiedAt(now)
        if (response.shouldNotify) {
            Log.d(TAG, "Response requires notification")
            notify(response)
        } else {
            Log.d(TAG, "Response does not require notification")
        }
        return Result.success()
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
        const val SESSION_THRESHOLD_MIN = 30
        const val COOLDOWN_MS = 30 * 60_000L
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
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<NudgeWorker>().build(),
    )
}
