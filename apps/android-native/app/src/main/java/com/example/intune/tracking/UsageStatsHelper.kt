package com.example.intune.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ForegroundApp(val packageName: String, val label: String, val category: String?, val durationMin: Int)

class UsageStatsHelper(private val context: Context) {
    fun currentApp(now: Long = System.currentTimeMillis()): ForegroundApp? {
        val beginTime = now - DAY_MS
        Log.d(TAG, "queryEvents window: ${formatTime(beginTime)} to ${formatTime(now)} (last 24 hours)")
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(beginTime, now)
        var packageName: String? = null
        var startedAt = 0L
        var eventCount = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            Log.d(TAG, "Event #$eventCount: ${eventTypeName(event.eventType)} (${event.eventType}), package=${event.packageName ?: "none"}, at=${formatTime(event.timeStamp)}")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                packageName = event.packageName
                startedAt = event.timeStamp
            }
        }
        if (eventCount == 0) Log.d(TAG, "queryEvents returned zero events")
        if (packageName == null) Log.d(TAG, "No MOVE_TO_FOREGROUND or ACTIVITY_RESUMED event was found")
        if (packageName == context.packageName) Log.d(TAG, "Most recent foreground event belongs to this app; ignoring it")
        val name = packageName?.takeUnless { it == context.packageName } ?: return null
        val appInfo = runCatching { context.packageManager.getApplicationInfo(name, 0) }.getOrNull()
        val label = runCatching { appInfo?.let { context.packageManager.getApplicationLabel(it).toString() } }.getOrNull() ?: name
        return ForegroundApp(name, label, appInfo?.categoryName(), ((now - startedAt) / MINUTE_MS).toInt())
    }

    private fun ApplicationInfo.categoryName(): String? = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) null else readableCategory(category)

    private companion object {
        const val TAG = "UsageStatsHelper"
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * MINUTE_MS
    }
}

private fun formatTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))

private fun eventTypeName(type: Int): String = when (type) {
    UsageEvents.Event.MOVE_TO_FOREGROUND -> "MOVE_TO_FOREGROUND/ACTIVITY_RESUMED"
    UsageEvents.Event.MOVE_TO_BACKGROUND -> "MOVE_TO_BACKGROUND/ACTIVITY_PAUSED"
    else -> "OTHER"
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
