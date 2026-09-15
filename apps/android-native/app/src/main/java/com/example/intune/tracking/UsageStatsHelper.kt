package com.example.intune.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

data class SessionApp(val packageName: String, val label: String, val durationMin: Int)
data class UsageSession(val apps: List<SessionApp>, val totalDurationMin: Int)

class UsageStatsHelper(private val context: Context) {
    fun sessionSummary(now: Long = System.currentTimeMillis()): UsageSession {
        val beginTime = now - WINDOW_MS
        val launcherPackage = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val durations = linkedMapOf<String, Long>()
        var activePackage: String? = null
        var activeStartedAt = 0L
        fun closeActive(at: Long) {
            val packageName = activePackage ?: return
            durations[packageName] = (durations[packageName] ?: 0L) + (at - activeStartedAt).coerceAtLeast(0)
            activePackage = null
        }

        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(beginTime, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    closeActive(event.timeStamp)
                    val packageName = event.packageName
                    if (packageName != null && packageName != context.packageName && packageName != launcherPackage) {
                        activePackage = packageName
                        activeStartedAt = event.timeStamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == activePackage) closeActive(event.timeStamp)
                }
            }
        }
        closeActive(now)

        val apps = durations.mapNotNull { (packageName, durationMs) ->
            val durationMin = (durationMs / MINUTE_MS).toInt()
            if (durationMin == 0) null else SessionApp(packageName, appLabel(packageName), durationMin)
        }
        val totalDurationMin = (durations.values.sum() / MINUTE_MS).toInt()
        Log.d(TAG, "Session: ${apps.joinToString { "${it.label} ${it.durationMin}m" }}, total=${totalDurationMin}m")
        return UsageSession(apps, totalDurationMin)
    }

    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).let { context.packageManager.getApplicationLabel(it).toString() }
    }.getOrDefault(packageName)

    private companion object {
        const val TAG = "UsageStatsHelper"
        const val MINUTE_MS = 60_000L
        const val WINDOW_MS = 30 * MINUTE_MS
    }
}

internal fun readableCategory(category: Int): String? = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Game"
    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Image"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_UNDEFINED -> "Undefined"
    else -> null
}
