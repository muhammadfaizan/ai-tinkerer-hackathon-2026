package com.example.intune.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.intune.data.ActivityTransitionRecord
import com.example.intune.data.NudgeDatabase
import com.example.intune.data.RoutineActivityType
import com.example.intune.data.TransitionType
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ActivityTransitions {
    fun isGranted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun register(context: Context) {
        if (!isGranted(context)) return
        val transitions = listOf(DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE)
            .flatMap { type -> listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { transition ->
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(transition).build()
            } }
        val request = ActivityTransitionRequest(transitions)
        ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent(context))
            .addOnSuccessListener { Log.d(TAG, "Activity transition updates registered") }
            .addOnFailureListener { Log.e(TAG, "Could not register activity transitions", it) }
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        501,
        Intent(context, ActivityTransitionReceiver::class.java).setAction(ACTION_ACTIVITY_TRANSITION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_ACTIVITY_TRANSITION = "com.example.intune.action.ACTIVITY_TRANSITION"
    private const val TAG = "ActivityTransitions"
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val pendingResult = goAsync()
        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = NudgeDatabase.get(context).routineDao()
                val nowWallTime = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtimeNanos()
                events.mapNotNull { event -> event.activityType.toRoutineActivity()?.let { activityType ->
                    ActivityTransitionRecord(activityType = activityType, transitionType = if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) TransitionType.ENTER else TransitionType.EXIT, timestamp = nowWallTime - (nowElapsed - event.elapsedRealTimeNanos) / 1_000_000L)
                } }.forEach { dao.insertTransition(it) }
                Log.d("ActivityTransitionReceiver", "Stored ${events.size} activity transitions")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun Int.toRoutineActivity(): RoutineActivityType? = when (this) {
    DetectedActivity.STILL -> RoutineActivityType.STILL
    DetectedActivity.WALKING -> RoutineActivityType.WALKING
    DetectedActivity.IN_VEHICLE -> RoutineActivityType.IN_VEHICLE
    DetectedActivity.ON_BICYCLE -> RoutineActivityType.ON_BICYCLE
    else -> null
}
