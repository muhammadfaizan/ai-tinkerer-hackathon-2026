package io.github.muhammadfaizan.intune

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import io.github.muhammadfaizan.intune.data.AppVersionResponse
import io.github.muhammadfaizan.intune.data.GoalsRepository
import io.github.muhammadfaizan.intune.data.NudgeResponse
import io.github.muhammadfaizan.intune.data.NudgeDatabase
import io.github.muhammadfaizan.intune.data.PendingNudge
import io.github.muhammadfaizan.intune.data.createNudgeApi
import io.github.muhammadfaizan.intune.tracking.EXTRA_MICRO_ACTION
import io.github.muhammadfaizan.intune.tracking.EXTRA_NUDGE_MESSAGE
import io.github.muhammadfaizan.intune.tracking.EXTRA_NUDGE_RECORD_ID
import io.github.muhammadfaizan.intune.ui.GoalAwareApp
import io.github.muhammadfaizan.intune.ui.theme.IntuneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var notificationNudge: PendingNudge? by androidx.compose.runtime.mutableStateOf(null)
    private var availableUpdate: AppVersionResponse? by mutableStateOf(null)
    private val goalsRepository by lazy { GoalsRepository(applicationContext) }
    private val api by lazy { createNudgeApi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationNudge = intent.toNudge()
        enableEdgeToEdge()
        setContent {
            IntuneTheme {
                GoalAwareApp(goalsRepository, api, NudgeDatabase.get(applicationContext).nudgeDao(), rememberCoroutineScope(), notificationNudge) {
                    notificationNudge = null
                }
                availableUpdate?.let { update ->
                    AppUpdateDialog(
                        update = update,
                        required = BuildConfig.VERSION_CODE < update.minVersionCode,
                        onDismiss = { availableUpdate = null },
                    )
                }
            }
        }
        checkForUpdate()
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

    private fun checkForUpdate() = lifecycleScope.launch {
        val now = System.currentTimeMillis()
        if (now - goalsRepository.lastAppVersionCheckAt() < UPDATE_CHECK_INTERVAL_MS) return@launch
        val update = runCatching { api.appVersion() }.getOrNull() ?: return@launch
        goalsRepository.saveLastAppVersionCheckAt(now)
        if (
            BuildConfig.VERSION_CODE < update.latestVersionCode &&
                isSafeDownloadUrl(update.downloadUrl)
        ) {
            availableUpdate = update
        }
    }

    private fun isSafeDownloadUrl(url: String): Boolean = runCatching {
        Uri.parse(url).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
    }.getOrDefault(false)

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1_000L
    }
}

@Composable
private fun AppUpdateDialog(update: AppVersionResponse, required: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val openDownload = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))) }
        if (!required) onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!required) onDismiss() },
        title = { Text(if (required) "Update required" else "Update available") },
        text = { Text(update.notes.ifBlank { "A newer version of In-Tune is ready." }) },
        confirmButton = { Button(onClick = openDownload) { Text("Download update") } },
        dismissButton = if (required) null else {
            { TextButton(onClick = onDismiss) { Text("Not now") } }
        },
    )
}
