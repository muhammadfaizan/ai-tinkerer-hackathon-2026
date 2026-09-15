package com.example.intune.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.intune.BuildConfig
import com.example.intune.MainActivity
import com.example.intune.R
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.data.NudgeDatabase
import com.example.intune.data.NudgeRecord
import com.example.intune.data.NudgeSource
import com.example.intune.data.SessionAppPayload
import com.example.intune.data.SessionPayload
import com.example.intune.data.RoutineContextPayload
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
        val thresholdMin = if (BuildConfig.DEBUG) TESTING_THRESHOLD_MIN else PRODUCTION_THRESHOLD_MIN
        Log.d(TAG, "Session threshold: ${thresholdMin}m${if (BuildConfig.DEBUG) " (temporary debug value)" else ""}")
        // A 30-minute rolling window cannot contain more than 30 minutes of foreground use.
        if (session.totalDurationMin < thresholdMin) {
            Log.d(TAG, "Skipping /nudge: session total ${session.totalDurationMin}m is under ${thresholdMin}m")
            return Result.success()
        }
        val now = System.currentTimeMillis()
        if (now - repository.lastNotifiedAt() < COOLDOWN_MS) {
            Log.d(TAG, "Skipping /nudge: session cooldown is active")
            return Result.success()
        }

        val routineContext = NudgeDatabase.get(applicationContext).routineDao().labeled().mapNotNull { profile ->
            profile.label?.let { RoutineContextPayload(it, profile.dayPattern, profile.approxStartHour, profile.approxEndHour) }
        }.takeIf { it.isNotEmpty() }
        val request = NudgeRequest(
            goals = goals,
            session = SessionPayload(session.apps.map { SessionAppPayload(it.label, it.durationMin) }, session.totalDurationMin),
            routineContext = routineContext,
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
            val recordId = NudgeDatabase.get(applicationContext).nudgeDao().insert(NudgeRecord(
                timestamp = now,
                source = NudgeSource.SESSION,
                appSummary = session.apps.joinToString { it.label },
                message = response.message,
                microAction = response.microAction,
                goalsSnapshot = goals.joinToString(),
            ))
            notify(response, recordId)
        } else {
            Log.d(TAG, "Response does not require notification")
        }
        return Result.success()
    }

    private fun notify(nudge: NudgeResponse, recordId: Long) {
        Log.d(TAG, "Preparing to build and show notification")
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "POST_NOTIFICATIONS granted: $notificationsGranted")
        if (!notificationsGranted) {
            Log.w(TAG, "Notification not shown: POST_NOTIFICATIONS is not granted")
            return
        }
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        Log.d(TAG, "App notifications enabled: ${notificationManagerCompat.areNotificationsEnabled()}")
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            Log.w(TAG, "Notification not shown: notifications are disabled for the app")
            return
        }
        val powerManager = applicationContext.getSystemService(PowerManager::class.java)
        Log.d(
            TAG,
            "Power state: deviceIdle=${powerManager.isDeviceIdleMode}, ignoringBatteryOptimizations=" +
                powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName),
        )
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                },
            )
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            Log.d(TAG, "Nudges channel: exists=${channel != null}, importance=${channelImportance(channel?.importance)}, sound=${channel?.sound}, vibration=${channel?.shouldVibrate()}")
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_NUDGE_MESSAGE, nudge.message)
            .putExtra(EXTRA_MICRO_ACTION, nudge.microAction)
            .putExtra(EXTRA_NUDGE_RECORD_ID, recordId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("A quick nudge")
            .setContentText(nudge.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Accept", actionPendingIntent(ACTION_ACCEPT, recordId, 1))
            .addAction(0, "Dismiss", actionPendingIntent(ACTION_DISMISS, recordId, 2))
            .addAction(0, "Actually, I'm working", actionPendingIntent(ACTION_ALREADY_ALIGNED, recordId, 3))
            .build()
        try {
            notificationManagerCompat.notify(NUDGE_NOTIFICATION_ID, notification)
            Log.d(TAG, "NotificationManagerCompat.notify($NUDGE_NOTIFICATION_ID) completed")
        } catch (error: Exception) {
            Log.e(TAG, "NotificationManagerCompat.notify($NUDGE_NOTIFICATION_ID) failed", error)
        }
    }

    private fun actionPendingIntent(action: String, recordId: Long, actionIndex: Int): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            recordId.hashCode() * 10 + actionIndex,
            Intent(applicationContext, NudgeActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("intune://nudge/$recordId/$action"))
                .putExtra(EXTRA_NUDGE_RECORD_ID, recordId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG = "NudgeWorker"
        const val PRODUCTION_THRESHOLD_MIN = 30
        const val PRODUCTION_COOLDOWN_MIN = 30
        // TEMPORARY TESTING VALUE: debug builds only; remove before real-world testing.
        const val TESTING_THRESHOLD_MIN = 5
        const val COOLDOWN_MS = PRODUCTION_COOLDOWN_MIN * 60_000L
        const val CHANNEL_ID = "nudges_v2"
    }

    private fun channelImportance(importance: Int?): String = when (importance) {
        NotificationManager.IMPORTANCE_NONE -> "NONE"
        NotificationManager.IMPORTANCE_MIN -> "MIN"
        NotificationManager.IMPORTANCE_LOW -> "LOW"
        NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
        NotificationManager.IMPORTANCE_HIGH -> "HIGH"
        else -> importance?.toString() ?: "missing"
    }
}

const val EXTRA_NUDGE_MESSAGE = "nudge_message"
const val EXTRA_MICRO_ACTION = "micro_action"
const val EXTRA_NUDGE_RECORD_ID = "nudge_record_id"
const val ACTION_ACCEPT = "com.example.intune.action.ACCEPT"
const val ACTION_DISMISS = "com.example.intune.action.DISMISS"
const val ACTION_ALREADY_ALIGNED = "com.example.intune.action.ALREADY_ALIGNED"
const val NUDGE_NOTIFICATION_ID = 1

fun scheduleNudgeWork(context: Context) {
    val request = PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("nudge-check", ExistingPeriodicWorkPolicy.KEEP, request)
}

fun triggerNudgeCheckNow(context: Context) {
    val workManager = WorkManager.getInstance(context)
    Log.d("NudgeWorker", "Debug check requested; replacing any previous debug request")
    val operation = workManager.enqueueUniqueWork(
        DEBUG_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<NudgeWorker>().build(),
    )
    operation.result.addListener({
        try {
            operation.result.get()
            val workInfos = workManager.getWorkInfosForUniqueWork(DEBUG_WORK_NAME)
            workInfos.addListener({
                try {
                    val states = workInfos.get().joinToString { it.state.name }
                    Log.d("NudgeWorker", "Debug check enqueued; current state(s): $states")
                } catch (error: Exception) {
                    Log.e("NudgeWorker", "Could not read debug check state", error)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (error: Exception) {
            Log.e("NudgeWorker", "Debug check enqueue failed", error)
        }
    }, ContextCompat.getMainExecutor(context))
}

private const val DEBUG_WORK_NAME = "nudge-check-debug"
