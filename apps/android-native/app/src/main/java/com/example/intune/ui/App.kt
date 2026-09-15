package com.example.intune.ui

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.intune.BuildConfig
import com.example.intune.data.ActionTaken
import com.example.intune.data.ActivityPayload
import com.example.intune.data.GoalsRepository
import com.example.intune.data.NudgeDao
import com.example.intune.data.NudgeRecord
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.data.NudgeSource
import com.example.intune.data.PendingNudge
import com.example.intune.data.NudgeApi
import com.example.intune.tracking.UsageAccess
import com.example.intune.tracking.scheduleNudgeWork
import com.example.intune.tracking.triggerNudgeCheckNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val ONBOARDING = "onboarding"
private const val PERMISSION = "permission"
private const val HOME = "home"
private const val PROGRESS = "progress"
private val screenPadding = 16.dp

@Composable
fun GoalAwareApp(
    repository: GoalsRepository,
    api: NudgeApi,
    dao: NudgeDao,
    scope: CoroutineScope,
    notificationNudge: PendingNudge?,
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
    LaunchedEffect(usageAccessGranted) {
        if (usageAccessGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    goals?.let { savedGoals ->
        LaunchedEffect(savedGoals, usageAccessGranted) {
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
            composable(PERMISSION) { PermissionScreen { UsageAccess.openSettings(context) } }
            composable(HOME) {
                HomeScreen(savedGoals, api, dao, scope, notificationNudge, onNotificationNudgeShown) {
                    navController.navigate(PROGRESS) { launchSingleTop = true }
                }
            }
            composable(PROGRESS) {
                ProgressScreen(dao) { navController.navigate(HOME) { launchSingleTop = true } }
            }
        }
        LaunchedEffect(usageAccessGranted, savedGoals.isNotEmpty()) {
            val route = navController.currentDestination?.route
            if (savedGoals.isNotEmpty() && usageAccessGranted && route == PERMISSION) {
                navController.navigate(HOME) { popUpTo(PERMISSION) { inclusive = true } }
            } else if (savedGoals.isNotEmpty() && !usageAccessGranted && (route == HOME || route == PROGRESS)) {
                navController.navigate(PERMISSION)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachScaffold(title: String, selectedRoute: String? = null, onHome: (() -> Unit)? = null, onProgress: (() -> Unit)? = null, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        bottomBar = {
            if (selectedRoute != null) NavigationBar {
                NavigationBarItem(selected = selectedRoute == HOME, onClick = { onHome?.invoke() }, icon = { Text("⌂") }, label = { Text("Home") })
                NavigationBarItem(selected = selectedRoute == PROGRESS, onClick = { onProgress?.invoke() }, icon = { Text("★") }, label = { Text("Progress") })
            }
        },
        content = content,
    )
}

@Composable
fun PermissionScreen(onOpenSettings: () -> Unit) = CoachScaffold("Usage access") { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
        Text("Enable thoughtful nudges", style = MaterialTheme.typography.headlineSmall)
        Text("To give you real nudges, this app needs to see which apps you're using — nothing leaves your device.")
        Text("Android requires you to enable this manually in Settings.")
        Button(onClick = onOpenSettings) { Text("Open usage access settings") }
    }
}

@Composable
fun OnboardingScreen(onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val goals = remember { mutableStateListOf<String>() }
    CoachScaffold("Welcome") { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            Text("What would you like to improve?", style = MaterialTheme.typography.headlineSmall)
            Text("Add up to three goals in plain language.")
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("A goal") })
            Button(onClick = { goals += text.trim(); text = "" }, enabled = text.isNotBlank() && goals.size < 3) { Text("Add goal") }
            goals.forEach { Text("• $it") }
            Button(onClick = { onSave(goals) }, enabled = goals.isNotEmpty()) { Text("Save goals") }
        }
    }
}

private data class DemoScenario(val label: String, val activity: ActivityPayload)
private data class ShownNudge(val nudge: NudgeResponse, val recordId: Long)

@Composable
fun HomeScreen(
    goals: List<String>,
    api: NudgeApi,
    dao: NudgeDao,
    scope: CoroutineScope,
    notificationNudge: PendingNudge? = null,
    onNotificationNudgeShown: () -> Unit = {},
    onProgress: () -> Unit,
) {
    val context = LocalContext.current
    val scenarios = listOf(
        DemoScenario("Doomscroll at night", ActivityPayload("Instagram", 25, "night")),
        DemoScenario("Content research scroll", ActivityPayload("Instagram", 20, "afternoon")),
        DemoScenario("Cab booking morning", ActivityPayload("Uber", 8, "morning")),
    )
    var shownNudge by remember { mutableStateOf<ShownNudge?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notificationNudge) {
        notificationNudge?.let { pending ->
            val recordId = pending.recordId.takeIf { it > 0 } ?: dao.insert(NudgeRecord(
                timestamp = System.currentTimeMillis(), source = NudgeSource.SESSION, appSummary = "Background session",
                message = pending.nudge.message, microAction = pending.nudge.microAction, goalsSnapshot = goals.joinToString(),
            ))
            shownNudge = ShownNudge(pending.nudge, recordId)
            onNotificationNudgeShown()
        }
    }
    CoachScaffold("Goal Coach", HOME, onHome = {}, onProgress = onProgress) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            item { Text("Your goals", style = MaterialTheme.typography.titleLarge) }
            items(goals) { Text("• $it") }
            item { Text("Demo Mode", style = MaterialTheme.typography.titleLarge) }
            item { Text("Background checks run about every 15 minutes; Android does not guarantee an exact time.") }
            if (BuildConfig.DEBUG) item { Button(onClick = { triggerNudgeCheckNow(context) }, Modifier.fillMaxWidth()) { Text("Trigger check now") } }
            items(scenarios) { scenario ->
                Button(onClick = {
                    status = "Checking…"
                    scope.launch {
                        runCatching { api.nudge(NudgeRequest(goals, scenario.activity)) }
                            .onSuccess { response ->
                                if (response.shouldNotify) {
                                    val recordId = dao.insert(NudgeRecord(
                                        timestamp = System.currentTimeMillis(), source = NudgeSource.SINGLE_APP, appSummary = scenario.activity.app,
                                        message = response.message, microAction = response.microAction, goalsSnapshot = goals.joinToString(),
                                    ))
                                    shownNudge = ShownNudge(response, recordId)
                                    status = null
                                } else status = "No nudge needed for this activity."
                            }
                            .onFailure { status = "Could not reach the nudge server: ${it.message}" }
                    }
                }, Modifier.fillMaxWidth()) { Text(scenario.label) }
            }
            status?.let { item { Text(it) } }
            shownNudge?.let { shown -> item { NudgeCard(shown.nudge) { action ->
                scope.launch { dao.updateActionTaken(shown.recordId, action) }
                shownNudge = null
            } } }
        }
    }
}

@Composable
fun NudgeCard(nudge: NudgeResponse, onAction: (ActionTaken) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = CardDefaults.elevatedCardElevation()) {
        Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            Text(nudge.message, style = MaterialTheme.typography.titleMedium)
            Text(nudge.microAction)
            Button(onClick = { Log.d("Nudge", "accepted"); onAction(ActionTaken.ACCEPTED) }) { Text("Accept") }
            TextButton(onClick = { Log.d("Nudge", "dismissed"); onAction(ActionTaken.DISMISSED) }) { Text("Dismiss") }
            TextButton(onClick = { Log.d("Nudge", "working"); onAction(ActionTaken.ALREADY_ALIGNED) }) { Text("Actually, I'm working") }
        }
    }
}

@Composable
fun ProgressScreen(dao: NudgeDao, onHome: () -> Unit) {
    val records by dao.getAllRecords().collectAsState(emptyList())
    val points by dao.getTotalPoints().collectAsState(0)
    val streak by dao.getCurrentStreak().collectAsState(0)
    val accepted by dao.getAcceptedCount().collectAsState(0)
    val total by dao.getTotalCount().collectAsState(0)
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    CoachScaffold("Progress", PROGRESS, onHome = onHome, onProgress = {}) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔥 $streak day streak", style = MaterialTheme.typography.headlineSmall)
                        Text("$points points · Level ${points / 100 + 1}")
                        Text("$accepted accepted out of $total nudges")
                    }
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleLarge) }
            items(records, key = { it.id }) { record ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatter.format(Date(record.timestamp)), style = MaterialTheme.typography.labelMedium)
                        Text(record.appSummary, style = MaterialTheme.typography.titleMedium)
                        Text(record.message)
                        Text(actionLabel(record.actionTaken), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun actionLabel(action: ActionTaken): String = when (action) {
    ActionTaken.ACCEPTED -> "Accepted · +10 points"
    ActionTaken.DISMISSED -> "Dismissed"
    ActionTaken.ALREADY_ALIGNED -> "Already aligned"
    ActionTaken.NONE -> "No response"
}
