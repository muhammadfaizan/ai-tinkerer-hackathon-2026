package com.example.intune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeResponse
import com.example.intune.data.NudgeDatabase
import com.example.intune.data.PendingNudge
import com.example.intune.data.createNudgeApi
import com.example.intune.tracking.EXTRA_MICRO_ACTION
import com.example.intune.tracking.EXTRA_NUDGE_MESSAGE
import com.example.intune.tracking.EXTRA_NUDGE_RECORD_ID
import com.example.intune.ui.GoalAwareApp
import com.example.intune.ui.theme.IntuneTheme

class MainActivity : ComponentActivity() {
    private var notificationNudge: PendingNudge? by androidx.compose.runtime.mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationNudge = intent.toNudge()
        enableEdgeToEdge()
        setContent {
            IntuneTheme {
                GoalAwareApp(GoalsRepository(applicationContext), createNudgeApi(), NudgeDatabase.get(applicationContext).nudgeDao(), rememberCoroutineScope(), notificationNudge) {
                    notificationNudge = null
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationNudge = intent.toNudge()
    }

    private fun android.content.Intent.toNudge(): PendingNudge? = getStringExtra(EXTRA_NUDGE_MESSAGE)?.let {
        PendingNudge(
            NudgeResponse("", true, it, getStringExtra(EXTRA_MICRO_ACTION).orEmpty(), false),
            getLongExtra(EXTRA_NUDGE_RECORD_ID, 0),
        )
    }
}
