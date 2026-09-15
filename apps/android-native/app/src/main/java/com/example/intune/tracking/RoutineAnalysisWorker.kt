package com.example.intune.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.intune.BuildConfig
import com.example.intune.data.NudgeDatabase
import com.example.intune.data.RoutineActivityType
import com.example.intune.data.RoutineProfile
import com.example.intune.data.TransitionType
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RoutineAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val records = NudgeDatabase.get(applicationContext).routineDao().transitionsSince(now - LOOKBACK_MS)
        val starts = mutableMapOf<RoutineActivityType, Long>()
        val occurrences = mutableMapOf<Pair<RoutineActivityType, Int>, MutableSet<String>>()
        val endHours = mutableMapOf<Pair<RoutineActivityType, Int>, MutableList<Int>>()
        records.forEach { record ->
            if (record.transitionType == TransitionType.ENTER) starts[record.activityType] = record.timestamp
            else {
                val start = starts.remove(record.activityType) ?: return@forEach
                if (record.activityType !in ROUTINE_ACTIVITIES) return@forEach
                val calendar = Calendar.getInstance().apply { timeInMillis = start }
                if (calendar.get(Calendar.DAY_OF_WEEK) !in Calendar.MONDAY..Calendar.FRIDAY) return@forEach
                val key = record.activityType to calendar.get(Calendar.HOUR_OF_DAY)
                val day = "%04d-%02d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
                occurrences.getOrPut(key) { mutableSetOf() }.add(day)
                endHours.getOrPut(key) { mutableListOf() }.add(Calendar.getInstance().apply { timeInMillis = record.timestamp }.get(Calendar.HOUR_OF_DAY))
            }
        }
        val dao = NudgeDatabase.get(applicationContext).routineDao()
        val requiredDays = if (BuildConfig.DEBUG) TESTING_REQUIRED_DAYS else PRODUCTION_REQUIRED_DAYS
        occurrences.filterValues { it.size >= requiredDays }.forEach { (key, days) ->
            val (activityType, startHour) = key
            if (dao.matchingCount(activityType, WEEKDAY, startHour) == 0) {
                val ends = endHours[key].orEmpty()
                dao.insertProfile(RoutineProfile(activityType = activityType, dayPattern = WEEKDAY, approxStartHour = startHour, approxEndHour = ends.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: startHour))
            }
        }
        return Result.success()
    }

    private companion object {
        const val WEEKDAY = "weekday"
        const val LOOKBACK_MS = 14 * 24 * 60 * 60_000L
        const val PRODUCTION_REQUIRED_DAYS = 4
        // TEMPORARY TESTING VALUE: debug builds need only two weekday sessions; restore production behavior for release.
        const val TESTING_REQUIRED_DAYS = 2
        val ROUTINE_ACTIVITIES = setOf(RoutineActivityType.WALKING, RoutineActivityType.IN_VEHICLE)
    }
}

fun scheduleRoutineAnalysis(context: Context) {
    val request = PeriodicWorkRequestBuilder<RoutineAnalysisWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("routine-analysis", ExistingPeriodicWorkPolicy.KEEP, request)
}

fun triggerRoutineAnalysisNow(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "routine-analysis-debug",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<RoutineAnalysisWorker>().build(),
    )
}
