package com.example.intune.ui

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.intune.data.ActivityPayload
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeApi
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.BuildConfig
import com.example.intune.tracking.UsageAccess
import com.example.intune.tracking.scheduleNudgeWork
import com.example.intune.tracking.triggerNudgeCheckNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ONBOARDING = "onboarding"
private const val PERMISSION = "permission"
private const val HOME = "home"

@Composable
fun GoalAwareApp(
    repository: GoalsRepository,
    api: NudgeApi,
    scope: CoroutineScope,
    notificationNudge: NudgeResponse?,
    onNotificationNudgeShown: () -> Unit,
) {
    val context = LocalContext.current
    val goals by repository.goals.collectAsState(initial = null)
    val navController = rememberNavController()
    var usageAccessGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) usageAccessGranted = UsageAccess.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.LaunchedEffect(usageAccessGranted) {
        if (usageAccessGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    goals?.let { savedGoals ->
        androidx.compose.runtime.LaunchedEffect(savedGoals, usageAccessGranted) {
            if (savedGoals.isNotEmpty() && usageAccessGranted) scheduleNudgeWork(context)
        }
        NavHost(navController, startDestination = when {
            savedGoals.isEmpty() -> ONBOARDING
            usageAccessGranted -> HOME
            else -> PERMISSION
        }) {
            composable(ONBOARDING) {
                OnboardingScreen { enteredGoals ->
                    scope.launch {
                        repository.save(enteredGoals)
                        navController.navigate(PERMISSION) { popUpTo(ONBOARDING) { inclusive = true } }
                    }
                }
            }
            composable(PERMISSION) {
                PermissionScreen { UsageAccess.openSettings(context) }
            }
            composable(HOME) { HomeScreen(savedGoals, api, scope, notificationNudge, onNotificationNudgeShown) }
        }
        androidx.compose.runtime.LaunchedEffect(usageAccessGranted, savedGoals.isNotEmpty()) {
            val route = navController.currentDestination?.route
            if (savedGoals.isNotEmpty() && usageAccessGranted && route == PERMISSION) {
                navController.navigate(HOME) { popUpTo(PERMISSION) { inclusive = true } }
            } else if (savedGoals.isNotEmpty() && !usageAccessGranted && route == HOME) {
                navController.navigate(PERMISSION)
            }
        }
    }
}

@Composable
fun PermissionScreen(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enable usage access")
        Text("To give you real nudges, this app needs to see which apps you're using — nothing leaves your device.")
        Text("Android requires you to enable this manually in Settings.")
        Button(onClick = onOpenSettings) { Text("Open usage access settings") }
    }
}

@Composable
fun OnboardingScreen(onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val goals = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What would you like to improve?")
        Text("Add up to three goals in plain language.")
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("A goal") })
        Button(onClick = { goals += text.trim(); text = "" }, enabled = text.isNotBlank() && goals.size < 3) { Text("Add goal") }
        goals.forEach { Text("• $it") }
        Button(onClick = { onSave(goals) }, enabled = goals.isNotEmpty()) { Text("Save goals") }
    }
}

private data class DemoScenario(val label: String, val activity: ActivityPayload)

@Composable
fun HomeScreen(
    goals: List<String>,
    api: NudgeApi,
    scope: CoroutineScope,
    notificationNudge: NudgeResponse? = null,
    onNotificationNudgeShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val scenarios = listOf(
        DemoScenario("Doomscroll at night", ActivityPayload("Instagram", 25, "night")),
        DemoScenario("Content research scroll", ActivityPayload("Instagram", 20, "afternoon")),
        DemoScenario("Cab booking morning", ActivityPayload("Uber", 8, "morning")),
    )
    var nudge by remember { mutableStateOf<NudgeResponse?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(notificationNudge) {
        notificationNudge?.let { nudge = it; onNotificationNudgeShown() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Your goals") }
        items(goals.size) { Text("• ${goals[it]}") }
        item { Text("Demo Mode") }
        item { Text("Background checks run about every 15 minutes; Android does not guarantee an exact time.") }
        if (BuildConfig.DEBUG) item {
            Button(onClick = { triggerNudgeCheckNow(context) }) {
                Text("Trigger check now")
            }
        }
        items(scenarios.size) { index ->
            val scenario = scenarios[index]
            Button(onClick = {
                status = "Checking…"
                scope.launch {
                    runCatching { api.nudge(NudgeRequest(goals, scenario.activity)) }
                        .onSuccess { response ->
                            nudge = response.takeIf { it.shouldNotify }
                            status = if (response.shouldNotify) null else "No nudge needed for this activity."
                        }
                        .onFailure { status = "Could not reach the nudge server: ${it.message}" }
                }
            }, Modifier.fillMaxWidth()) { Text(scenario.label) }
        }
        status?.let { item { Text(it) } }
        nudge?.let { response -> item { NudgeCard(response) { nudge = null } } }
    }
}

@Composable
fun NudgeCard(nudge: NudgeResponse, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(nudge.message)
            Text(nudge.microAction)
            Button(onClick = { Log.d("Nudge", "accepted"); onDismiss() }) { Text("Accept") }
            TextButton(onClick = { Log.d("Nudge", "dismissed"); onDismiss() }) { Text("Dismiss") }
            TextButton(onClick = { Log.d("Nudge", "working"); onDismiss() }) { Text("Actually, I'm working") }
        }
    }
}
