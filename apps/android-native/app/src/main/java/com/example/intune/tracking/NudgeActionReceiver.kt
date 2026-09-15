package com.example.intune.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.intune.data.ActionTaken
import com.example.intune.data.NudgeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NudgeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actionTaken = when (intent.action) {
            ACTION_ACCEPT -> ActionTaken.ACCEPTED
            ACTION_DISMISS -> ActionTaken.DISMISSED
            ACTION_ALREADY_ALIGNED -> ActionTaken.ALREADY_ALIGNED
            else -> return
        }
        val recordId = intent.getLongExtra(EXTRA_NUDGE_RECORD_ID, 0)
        if (recordId <= 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updated = NudgeDatabase.get(context).nudgeDao().updateActionTaken(recordId, actionTaken)
                NotificationManagerCompat.from(context).cancel(NUDGE_NOTIFICATION_ID)
                val message = if (updated == 0) "Response already saved" else if (actionTaken == ActionTaken.ACCEPTED) "Nice work! +10 points" else "Response saved"
                withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show() }
            } catch (error: Exception) {
                Log.e("NudgeActionReceiver", "Could not save notification response", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
