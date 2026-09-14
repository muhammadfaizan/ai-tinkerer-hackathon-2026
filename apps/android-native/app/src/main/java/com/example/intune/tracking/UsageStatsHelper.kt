package com.example.intune.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

data class ForegroundApp(val packageName: String, val label: String, val category: String?, val durationMin: Int)

class UsageStatsHelper(private val context: Context) {
    fun currentApp(now: Long = System.currentTimeMillis()): ForegroundApp? {
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - DAY_MS, now)
        var packageName: String? = null
        var startedAt = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            ) {
                packageName = event.packageName
                startedAt = event.timeStamp
            }
        }
        val name = packageName?.takeUnless { it == context.packageName } ?: return null
        val appInfo = runCatching { context.packageManager.getApplicationInfo(name, 0) }.getOrNull()
        val label = runCatching { appInfo?.let { context.packageManager.getApplicationLabel(it).toString() } }.getOrNull() ?: name
        return ForegroundApp(name, label, appInfo?.categoryName(), ((now - startedAt) / MINUTE_MS).toInt())
    }

    private fun ApplicationInfo.categoryName(): String? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) null else readableCategory(category)

    private companion object {
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * MINUTE_MS
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
