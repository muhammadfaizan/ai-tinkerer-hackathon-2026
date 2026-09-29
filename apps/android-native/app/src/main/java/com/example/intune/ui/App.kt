package com.example.intune.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.intune.BuildConfig
import com.example.intune.ui.theme.LavenderBadge
import com.example.intune.data.ActionTaken
import com.example.intune.data.ActivityPayload
import com.example.intune.data.GoalsRepository
import com.example.intune.data.MAX_GOALS
import com.example.intune.data.NudgeDao
import com.example.intune.data.NudgeDatabase
import com.example.intune.data.NudgeRecord
import com.example.intune.data.NudgeRequest
import com.example.intune.data.NudgeResponse
import com.example.intune.data.NudgeSource
import com.example.intune.data.PendingNudge
import com.example.intune.data.NudgeApi
import com.example.intune.data.ParseGoalsRequest
import com.example.intune.data.RoutineDao
import com.example.intune.data.RoutineProfile
import com.example.intune.data.RoutineStatus
import com.example.intune.tracking.UsageAccess
import com.example.intune.tracking.ActivityTransitions
import com.example.intune.tracking.scheduleNudgeWork
import com.example.intune.tracking.scheduleRoutineAnalysis
import com.example.intune.tracking.triggerRoutineAnalysisNow
import com.example.intune.tracking.triggerNudgeCheckNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Calendar

private const val ONBOARDING = "onboarding"
private const val PERMISSION = "permission"
private const val HOME = "home"
private const val LOGS = "logs"
private const val PROGRESS = "progress"
private const val PROFILE = "profile"
private const val ROUTINE_PERMISSION = "routine-permission"
private val screenPadding = 16.dp
private val largeSpacing = 24.dp
private enum class VoiceState { LISTENING, PROCESSING }

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
    val soundEnabled by repository.soundEnabled.collectAsState(initial = true)
    val routinePermissionPrompted by repository.routinePermissionPrompted.collectAsState(initial = false)
    val navController = rememberNavController()
    var usageAccessGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    var activityRecognitionGranted by remember { mutableStateOf(ActivityTransitions.isGranted(context)) }
    val routineDao = remember(context) { NudgeDatabase.get(context).routineDao() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageAccessGranted = UsageAccess.isGranted(context)
                activityRecognitionGranted = ActivityTransitions.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(usageAccessGranted) {
        if (usageAccessGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(activityRecognitionGranted) {
        if (activityRecognitionGranted) {
            ActivityTransitions.register(context)
            scheduleRoutineAnalysis(context)
        }
    }
    CoachBackground {
    goals?.let { savedGoals ->
        LaunchedEffect(savedGoals, usageAccessGranted) {
            if (savedGoals.isNotEmpty() && usageAccessGranted) scheduleNudgeWork(context)
        }
        NavHost(navController, startDestination = when {
            savedGoals.isEmpty() -> ONBOARDING
            !usageAccessGranted -> PERMISSION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !activityRecognitionGranted && !routinePermissionPrompted -> ROUTINE_PERMISSION
            else -> HOME
        }) {
            composable(ONBOARDING) {
                OnboardingScreen(api, scope) { enteredGoals ->
                    scope.launch {
                        repository.save(enteredGoals)
                        navController.navigate(PERMISSION) { popUpTo(ONBOARDING) { inclusive = true } }
                    }
                }
            }
            composable(PERMISSION) { PermissionScreen { UsageAccess.openSettings(context) } }
            composable(ROUTINE_PERMISSION) {
                ActivityRecognitionPermissionScreen(
                    onPermissionResult = { granted ->
                        activityRecognitionGranted = granted
                        scope.launch { repository.markRoutinePermissionPrompted() }
                    },
                    onSkip = { scope.launch { repository.markRoutinePermissionPrompted() } },
                )
            }
            composable(HOME) {
                HomeScreen(
                    goals = savedGoals, dao = dao, routineDao = routineDao, scope = scope, soundEnabled = soundEnabled,
                    notificationNudge = notificationNudge, onNotificationNudgeShown = onNotificationNudgeShown,
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                )
            }
            composable(PROGRESS) {
                ProgressScreen(dao, soundEnabled) { route -> navController.navigate(route) { launchSingleTop = true } }
            }
            composable(LOGS) {
                LogsScreen(dao, soundEnabled) { route -> navController.navigate(route) { launchSingleTop = true } }
            }
            composable(PROFILE) {
                ProfileScreen(
                    goals = savedGoals,
                    api = api,
                    dao = dao,
                    routineDao = routineDao,
                    soundEnabled = soundEnabled,
                    onSoundChange = { enabled -> scope.launch { repository.saveSoundEnabled(enabled) } },
                    onGoalsChange = { updatedGoals -> scope.launch { repository.save(updatedGoals) } },
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    scope = scope,
                )
            }
        }
        LaunchedEffect(usageAccessGranted, activityRecognitionGranted, routinePermissionPrompted, savedGoals.isNotEmpty()) {
            val route = navController.currentDestination?.route
            val target = when {
                savedGoals.isEmpty() -> ONBOARDING
                !usageAccessGranted -> PERMISSION
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !activityRecognitionGranted && !routinePermissionPrompted -> ROUTINE_PERMISSION
                else -> HOME
            }
            if (route != target && (route == PERMISSION || route == ROUTINE_PERMISSION || route == HOME || route == LOGS || route == PROGRESS || route == PROFILE)) {
                navController.navigate(target) { launchSingleTop = true }
            }
        }
    } ?: LoadingScreen()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CoachScaffold(
    title: String,
    selectedRoute: String? = null,
    onNavigate: (String) -> Unit = {},
    soundEnabled: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sounds = rememberCoachSounds()
    val screenText = MaterialTheme.colorScheme.onBackground
    CompositionLocalProvider(LocalContentColor provides screenText) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (title == "In-Tune") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IntuneLogoMark(Modifier.size(28.dp))
                                Text(title)
                            }
                        } else Text(title)
                    },
                    actions = {
                        if (selectedRoute != null && selectedRoute != PROFILE) {
                            IconButton(
                                onClick = { onNavigate(PROFILE) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        titleContentColor = screenText,
                    ),
                )
            },
            bottomBar = {
                if (selectedRoute != null) NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    val homeSelected = selectedRoute == HOME
                    NavigationBarItem(
                        selected = homeSelected,
                        onClick = { if (!homeSelected) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds.navigate(soundEnabled); onNavigate(HOME) } },
                        icon = { Icon(if (homeSelected) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    val logsSelected = selectedRoute == LOGS
                    NavigationBarItem(
                        selected = logsSelected,
                        onClick = { if (!logsSelected) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds.navigate(soundEnabled); onNavigate(LOGS) } },
                        icon = { Icon(if (logsSelected) Icons.Filled.FormatListBulleted else Icons.Outlined.FormatListBulleted, contentDescription = null) },
                        label = { Text("Logs") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    val progressSelected = selectedRoute == PROGRESS
                    NavigationBarItem(
                        selected = progressSelected,
                        onClick = { if (!progressSelected) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds.navigate(soundEnabled); onNavigate(PROGRESS) } },
                        icon = { Icon(if (progressSelected) Icons.Filled.BarChart else Icons.Outlined.BarChart, contentDescription = null) },
                        label = { Text("Progress") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
            content = content,
        )
    }
}

@Composable
fun PermissionScreen(onOpenSettings: () -> Unit) = CoachScaffold("Usage access") { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
        Text("Enable thoughtful check-ins", style = MaterialTheme.typography.headlineSmall)
        Text("To give you helpful check-ins, this app needs to see which apps you're using — nothing leaves your device.")
        Text("Android requires you to enable this manually in Settings.")
        Button(onClick = onOpenSettings) { Text("Open usage access settings") }
    }
}

@Composable
private fun ActivityRecognitionPermissionScreen(onPermissionResult: (Boolean) -> Unit, onSkip: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermissionResult)
    CoachScaffold("Routine detection") { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            Text("Notice routines without tracking location", style = MaterialTheme.typography.headlineSmall)
            Text("This only detects whether you're walking, in a vehicle, cycling, or still. It never collects your location, destination, coordinates, or maps.")
            Button(onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow activity detection") }
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            ) { Text("Not now") }
        }
    }
}

@Composable
fun OnboardingScreen(api: NudgeApi, scope: CoroutineScope, onSave: (List<String>) -> Unit) = GoalEditor("Welcome", emptyList(), api, scope, true, onSave)

@Composable
private fun GoalEditor(
    title: String,
    initialGoals: List<String>,
    api: NudgeApi,
    scope: CoroutineScope,
    showTrust: Boolean,
    onSave: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf("") }
    val goals = remember(initialGoals) { mutableStateListOf<String>().also { it.addAll(initialGoals) } }
    var voiceState by remember { mutableStateOf<VoiceState?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<String>?>(null) }
    val voicePulse by rememberInfiniteTransition(label = "voicePulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "voicePulseAlpha",
    )
    val voice = remember(context) {
        GoalVoiceInput(context,
            onTranscript = { transcript ->
                voiceState = VoiceState.PROCESSING
                status = null
                scope.launch {
                    runCatching { api.parseGoals(ParseGoalsRequest(transcript, goals.toList())) }
                        .onSuccess { response ->
                            preview = mergeGoals(goals.toList(), response.goals)
                            voiceState = null
                            status = if (preview.isNullOrEmpty()) "I couldn't find clear goals. Try again or type them manually." else null
                        }
                        .onFailure {
                            voiceState = null
                            voiceError = "Couldn't process that — try again?"
                        }
                }
            },
            onError = { message -> voiceState = null; voiceError = message },
            onListening = { voiceState = VoiceState.LISTENING },
            onProcessing = { voiceState = VoiceState.PROCESSING },
        )
    }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { voiceError = null; preview = null; voice.start() }
        else status = "Microphone permission was denied. You can always type your goals manually."
    }
    CoachScaffold(title) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            Text(if (initialGoals.isEmpty()) "What would you like to improve?" else "Keep your goals current", style = MaterialTheme.typography.headlineSmall)
            Text("Add up to $MAX_GOALS goals. Voice suggestions are always shown for confirmation first.")
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("A goal") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                IconButton(modifier = Modifier.align(Alignment.CenterEnd), enabled = voiceState != VoiceState.PROCESSING, onClick = {
                    if (voiceState == VoiceState.LISTENING) voice.stop()
                    else if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) status = "Voice input isn't available on this device. Please type your goal."
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        voiceError = null; preview = null; voice.start()
                    } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(if (voiceState == VoiceState.LISTENING) Icons.Outlined.Close else Icons.Outlined.Mic, contentDescription = if (voiceState == VoiceState.LISTENING) "Stop recording" else "Speak a goal") }
            }
            voiceState?.let { state -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(24.dp).alpha(if (state == VoiceState.LISTENING) voicePulse else 1f))
                Text(if (state == VoiceState.LISTENING) "Listening..." else "Processing...")
                if (state == VoiceState.LISTENING) TextButton(
                    onClick = { voice.stop() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                ) { Text("Stop") }
            } }
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (goals.size >= MAX_GOALS) status = "Goal limit reached. You can keep up to $MAX_GOALS goals."
                else { goals += text.trim(); text = "" }
            }, enabled = text.isNotBlank()) { Text("Add goal") }
            goals.forEachIndexed { index, goal -> GoalChip(goal, index, onRemove = { goals.remove(goal) }) }
            preview?.let { heardGoals ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Here's what I heard. Looks right?", style = MaterialTheme.typography.titleMedium)
                        heardGoals.forEachIndexed { index, goal -> GoalChip(goal, index) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSave(heardGoals) }) { Text("Confirm") }
                            TextButton(onClick = { preview = null; status = "Tap the microphone and try again." }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Try again") }
                            TextButton(onClick = { goals.clear(); goals.addAll(heardGoals); preview = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Edit manually") }
                        }
                    }
                }
            }
            voiceError?.let { error -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        voiceError = null; preview = null; voice.start()
                    } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)) { Text("Retry") }
            } }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onBackground) }
            if (showTrust) TrustBadge()
            Button(onClick = { onSave(goals.toList()) }, enabled = goals.isNotEmpty()) { Text(if (initialGoals.isEmpty()) "Save goals" else "Save changes") }
        }
    }
}

private data class DemoScenario(val label: String, val activity: ActivityPayload)
private data class ShownNudge(val nudge: NudgeResponse, val recordId: Long)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HomeScreen(
    goals: List<String>,
    dao: NudgeDao,
    routineDao: RoutineDao,
    scope: CoroutineScope,
    soundEnabled: Boolean,
    notificationNudge: PendingNudge? = null,
    onNotificationNudgeShown: () -> Unit = {},
    onNavigate: (String) -> Unit,
) {
    var shownNudge by remember { mutableStateOf<ShownNudge?>(null) }
    var pendingRoutine by remember { mutableStateOf<RoutineProfile?>(null) }
    val latestToday by dao.latestRecordSince(remember { startOfToday() }).collectAsState(initial = null)
    LaunchedEffect(Unit) { pendingRoutine = routineDao.nextPending() }
    LaunchedEffect(notificationNudge) {
        notificationNudge?.let { pending ->
            val alreadyActioned = pending.recordId > 0 && dao.getActionTaken(pending.recordId)?.let { it != ActionTaken.NONE } == true
            if (!alreadyActioned) {
                val recordId = pending.recordId.takeIf { it > 0 } ?: dao.insert(NudgeRecord(
                    timestamp = System.currentTimeMillis(), source = NudgeSource.SESSION, appSummary = "Background session",
                    message = pending.nudge.message, microAction = pending.nudge.microAction, goalsSnapshot = goals.joinToString(),
                ))
                shownNudge = ShownNudge(pending.nudge, recordId)
            }
            onNotificationNudgeShown()
        }
    }
    val currentNudge = shownNudge ?: latestToday?.takeIf { it.actionTaken == ActionTaken.NONE }?.let { record ->
        ShownNudge(NudgeResponse("", true, record.message, record.microAction, false), record.id)
    }
    CoachScaffold("In-Tune", HOME, onNavigate = onNavigate, soundEnabled = soundEnabled) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(timeGreeting(), style = MaterialTheme.typography.headlineMedium)
                        Text("A small check-in for the goals you care about.")
                    }
                }
            }
            item { Text("Your goals", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    goals.forEachIndexed { index, goal -> GoalChip(goal, index, onClick = { onNavigate(PROFILE) }) }
                }
            }
            item { Text("Right now", style = MaterialTheme.typography.titleLarge) }
            item {
                when {
                    currentNudge != null -> NudgeCard(currentNudge.nudge, soundEnabled) { action ->
                        scope.launch { dao.updateActionTaken(currentNudge.recordId, action) }
                        shownNudge = null
                    }
                    latestToday != null -> TodayNudgeSummary(latestToday!!)
                    else -> QuietRightNowState()
                }
            }
        }
        pendingRoutine?.let { routine -> RoutineReviewDialog(routine) { status, label ->
            scope.launch {
                routineDao.updateProfile(routine.id, status, label)
                pendingRoutine = routineDao.nextPending()
            }
        } }
    }
}

private fun timeGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun TodayNudgeSummary(record: NudgeRecord) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(largeSpacing), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today’s check-in", style = MaterialTheme.typography.titleMedium)
            Text(record.message, style = MaterialTheme.typography.bodyLarge)
            Text(actionLabel(record.actionTaken), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuietRightNowState() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(largeSpacing), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nothing needs your attention right now", style = MaterialTheme.typography.titleMedium)
            Text("Keep moving gently toward your goals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoutineReviewDialog(profile: RoutineProfile, onAnswer: (RoutineStatus, String?) -> Unit) {
    val activity = when (profile.activityType.name) {
        "IN_VEHICLE" -> "in a vehicle"
        "WALKING" -> "walking"
        else -> profile.activityType.name.lowercase()
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("A possible routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Looks like you're often $activity around ${profile.approxStartHour}:00–${profile.approxEndHour}:00 on weekdays — what's this usually for?")
                listOf("Commute to work", "School drop-off", "Exercise/walk", "Other").forEach { label ->
                    AssistChip(
                        onClick = { onAnswer(RoutineStatus.LABELED, label) },
                        label = { Text(label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            labelColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    )
                }
                TextButton(onClick = { onAnswer(RoutineStatus.DISMISSED, null) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Not a real pattern") }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun NudgeCard(nudge: NudgeResponse, soundEnabled: Boolean, onAction: (ActionTaken) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val sounds = rememberCoachSounds()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    var celebrating by remember { mutableStateOf(false) }
    val bonus by animateIntAsState(if (celebrating) 10 else 0, tween(550), label = "points")
    AnimatedVisibility(visible, exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.92f, animationSpec = tween(220))) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("From In-Tune", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(nudge.message, style = MaterialTheme.typography.headlineLarge)
                Text(nudge.microAction, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (celebrating) {
                    CelebrationBurst(Modifier.fillMaxWidth().size(128.dp))
                    Text("+$bonus points", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                Button(
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sounds.accept(soundEnabled)
                        celebrating = true
                        scope.launch {
                            delay(700)
                            visible = false
                            delay(220)
                            onAction(ActionTaken.ACCEPTED)
                        }
                    },
                ) {
                    Text("Accept")
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds.dismiss(soundEnabled); visible = false
                        scope.launch { delay(180); onAction(ActionTaken.DISMISSED) }
                    }) { Text("Dismiss") }
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sounds.dismiss(soundEnabled); visible = false
                        scope.launch { delay(180); onAction(ActionTaken.ALREADY_ALIGNED) }
                    }) { Text("Actually, I’m working") }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(dao: NudgeDao, soundEnabled: Boolean, onNavigate: (String) -> Unit) {
    val points by dao.getTotalPoints().collectAsState(0)
    val streak by dao.getCurrentStreak().collectAsState(0)
    CoachScaffold("Progress", PROGRESS, onNavigate = onNavigate, soundEnabled = soundEnabled) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(largeSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("$streak", style = MaterialTheme.typography.displayLarge)
                        Text("day streak", style = MaterialTheme.typography.titleMedium)
                        GrowthMark(points / 100 + 1, Modifier.size(80.dp))
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Points", style = MaterialTheme.typography.titleMedium)
                        Text("$points", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Level", style = MaterialTheme.typography.titleMedium)
                        Text("${points / 100 + 1}", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsScreen(dao: NudgeDao, soundEnabled: Boolean, onNavigate: (String) -> Unit) {
    val records by dao.getAllRecords().collectAsState(emptyList())
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    CoachScaffold("Logs", LOGS, onNavigate = onNavigate, soundEnabled = soundEnabled) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (records.isEmpty()) {
                item { EmptyLogsState() }
            }
            items(records, key = { it.id }) { record ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(screenPadding), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.appSummary, style = MaterialTheme.typography.titleMedium)
                            Text("${formatter.format(Date(record.timestamp))} · ${actionLabel(record.actionTaken)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.size(12.dp).background(statusColor(record.actionTaken), CircleShape))
                    }
                }
            }
        }
    }
}

private fun mergeGoals(currentGoals: List<String>, suggestedGoals: List<String>): List<String> {
    val merged = currentGoals.map(String::trim).filter(String::isNotBlank).toMutableList()
    suggestedGoals.map(String::trim).filter(String::isNotBlank).forEach { suggestion ->
        if (merged.none { it.equals(suggestion, ignoreCase = true) }) merged += suggestion
    }
    return merged.take(MAX_GOALS)
}

@Composable
private fun ProfileGoals(
    goals: List<String>,
    api: NudgeApi,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onGoalsChange: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var editingGoal by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf<VoiceState?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<String>?>(null) }
    val voice = remember(context) {
        GoalVoiceInput(
            context,
            onTranscript = { transcript ->
                voiceState = VoiceState.PROCESSING
                scope.launch {
                    runCatching { api.parseGoals(ParseGoalsRequest(transcript, goals)) }
                        .onSuccess { response ->
                            preview = mergeGoals(goals, response.goals)
                            status = if (goals.size >= MAX_GOALS) "Goal limit reached. Remove or rename a goal before adding another." else null
                            voiceState = null
                        }
                        .onFailure { voiceState = null; voiceError = "Couldn't process that — try again?" }
                }
            },
            onError = { message -> voiceState = null; voiceError = message },
            onListening = { voiceState = VoiceState.LISTENING },
            onProcessing = { voiceState = VoiceState.PROCESSING },
        )
    }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { voiceError = null; preview = null; voice.start() }
        else status = "Microphone permission was denied. You can always type your goals manually."
    }
    fun startOrStopVoice() {
        if (voiceState == VoiceState.LISTENING) voice.stop()
        else if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) status = "Voice input isn't available on this device. Please type your goal."
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceError = null
            preview = null
            voice.start()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Goals", style = MaterialTheme.typography.titleLarge)
            if (goals.isEmpty()) Text("Add a goal to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            goals.forEachIndexed { index, goal ->
                if (editingGoal == goal) {
                    OutlinedTextField(renameText, { renameText = it }, Modifier.fillMaxWidth(), label = { Text("Goal") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val renamed = renameText.trim()
                            if (renamed.isBlank()) status = "A goal cannot be empty."
                            else if (goals.any { it != goal && it.equals(renamed, ignoreCase = true) }) status = "That goal is already on your list."
                            else {
                                onGoalsChange(goals.map { if (it == goal) renamed else it })
                                editingGoal = null
                            }
                        }) { Text("Save") }
                        TextButton(onClick = { editingGoal = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GoalChip(goal, index, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingGoal = goal; renameText = goal }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename $goal")
                        }
                        IconButton(onClick = {
                            val previousGoals = goals
                            onGoalsChange(goals.filter { it != goal })
                            scope.launch {
                                if (snackbarHostState.showSnackbar("Goal removed", "Undo") == SnackbarResult.ActionPerformed)
                                    onGoalsChange(previousGoals)
                            }
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Remove $goal") }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Add a goal") },
                )
                IconButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enabled = voiceState != VoiceState.PROCESSING,
                    onClick = ::startOrStopVoice,
                ) {
                    Icon(if (voiceState == VoiceState.LISTENING) Icons.Outlined.Close else Icons.Outlined.Mic, contentDescription = if (voiceState == VoiceState.LISTENING) "Stop recording" else "Speak a goal")
                }
            }
            Button(onClick = {
                val goal = text.trim()
                if (goals.size >= MAX_GOALS) status = "Goal limit reached. You can keep up to $MAX_GOALS goals."
                else if (goals.any { it.equals(goal, ignoreCase = true) }) status = "That goal is already on your list."
                else { onGoalsChange(goals + goal); text = "" }
            }, enabled = text.isNotBlank()) { Text("Add goal") }
            voiceState?.let { Text(if (it == VoiceState.LISTENING) "Listening... Tap the microphone to stop." else "Processing...") }
            preview?.let { heardGoals ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Here's what I heard. Looks right?", style = MaterialTheme.typography.titleMedium)
                        heardGoals.forEachIndexed { index, goal -> GoalChip(goal, index) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onGoalsChange(heardGoals); preview = null }) { Text("Confirm") }
                            TextButton(onClick = { preview = null; startOrStopVoice() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Try again") }
                            TextButton(onClick = { text = heardGoals.lastOrNull().orEmpty(); preview = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Edit manually") }
                        }
                    }
                }
            }
            voiceError?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = ::startOrStopVoice, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Retry") }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProfileScreen(
    goals: List<String>,
    api: NudgeApi,
    dao: NudgeDao,
    routineDao: RoutineDao,
    soundEnabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onGoalsChange: (List<String>) -> Unit,
    onNavigate: (String) -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val routines by routineDao.labeledProfiles().collectAsState(emptyList())
    var editingRoutineId by remember { mutableStateOf<Long?>(null) }
    var routineLabel by remember { mutableStateOf("") }
    var showTrustDialog by remember { mutableStateOf(false) }
    var developerOptionsEnabled by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }
    var lastVersionTapAt by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    CoachScaffold("Profile", PROFILE, onNavigate = onNavigate, soundEnabled = soundEnabled) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(screenPadding), verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            item { ProfileGoals(goals, api, scope, snackbarHostState, onGoalsChange) }
            item { Text("Routines", style = MaterialTheme.typography.titleLarge) }
            if (routines.isEmpty()) item { Text("No labeled routines yet.") }
            items(routines, key = { it.id }) { routine ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${routine.dayPattern.replaceFirstChar { it.uppercase() }} · ${routine.approxStartHour}:00–${routine.approxEndHour}:00", style = MaterialTheme.typography.labelMedium)
                        if (editingRoutineId == routine.id) {
                            OutlinedTextField(routineLabel, { routineLabel = it }, Modifier.fillMaxWidth(), label = { Text("Routine label") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch { routineDao.updateProfile(routine.id, RoutineStatus.LABELED, routineLabel.trim()) }
                                    editingRoutineId = null
                                }) { Text("Save") }
                                TextButton(onClick = { editingRoutineId = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(routine.label.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { editingRoutineId = routine.id; routineLabel = routine.label.orEmpty() }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit routine") }
                                    IconButton(onClick = { scope.launch { routineDao.deleteProfile(routine.id) } }) { Icon(Icons.Outlined.Close, contentDescription = "Delete routine") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { onSoundChange(!soundEnabled) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(if (soundEnabled) "Sound on" else "Sound off") }
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Manage notifications") }
                        TextButton(onClick = { showTrustDialog = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("🔒 Your data stays on this device") }
                    }
                }
            }
            if (BuildConfig.DEBUG && developerOptionsEnabled) {
                item { DeveloperTools(goals, api, dao, scope) }
            }
            item {
                Text(
                    "In-Tune · Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(enabled = BuildConfig.DEBUG) {
                        val now = SystemClock.elapsedRealtime()
                        versionTapCount = if (now - lastVersionTapAt > 2_000) 1 else versionTapCount + 1
                        lastVersionTapAt = now
                        val tapsRemaining = 7 - versionTapCount
                        if (tapsRemaining > 0) {
                            Toast.makeText(context, "$tapsRemaining more taps to enable developer options", Toast.LENGTH_SHORT).show()
                        } else {
                            developerOptionsEnabled = true
                            Toast.makeText(context, "Developer options enabled", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(screenPadding))
        }
    }
    if (showTrustDialog) AlertDialog(onDismissRequest = { showTrustDialog = false }, confirmButton = { TextButton(onClick = { showTrustDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Got it") } }, title = { Text("Your privacy") }, text = { Text("Nothing here is ever sent to a server. All your history and progress stays only on your phone.") })
}

@Composable
private fun DeveloperTools(
    goals: List<String>,
    api: NudgeApi,
    dao: NudgeDao,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val scenarios = remember {
        listOf(
            DemoScenario("Doomscroll at night", ActivityPayload("Instagram", 25, "night")),
            DemoScenario("Content research scroll", ActivityPayload("Instagram", 20, "afternoon")),
            DemoScenario("Cab booking morning", ActivityPayload("Uber", 8, "morning")),
        )
    }
    var status by remember { mutableStateOf<String?>(null) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Developer tools", style = MaterialTheme.typography.titleLarge)
            Text("Debug-only test controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    Log.d("NudgeWorker", "Trigger check now button tapped")
                    triggerNudgeCheckNow(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Trigger check now") }
            TextButton(
                onClick = {
                    scope.launch {
                        GoalsRepository(context).clearLastNotifiedAt()
                        Log.d("NudgeWorker", "Debug cooldown reset")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("Reset cooldown") }
            TextButton(
                onClick = { triggerRoutineAnalysisNow(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("Analyze routines now") }
            scenarios.forEach { scenario ->
                Button(
                    onClick = {
                        status = "Checking…"
                        scope.launch {
                            runCatching { api.nudge(NudgeRequest(goals, scenario.activity)) }
                                .onSuccess { response ->
                                    if (response.shouldNotify) {
                                        dao.insert(
                                            NudgeRecord(
                                                timestamp = System.currentTimeMillis(),
                                                source = NudgeSource.SINGLE_APP,
                                                appSummary = scenario.activity.app,
                                                message = response.message,
                                                microAction = response.microAction,
                                                goalsSnapshot = goals.joinToString(),
                                            ),
                                        )
                                        status = "Check-in ready on Home."
                                    } else status = "No check-in needed for this activity."
                                }
                                .onFailure { status = "Could not reach the check-in server: ${it.message}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(scenario.label) }
            }
            status?.let { Text(it) }
        }
    }
}

@Composable
private fun EmptyLogsState() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(largeSpacing), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GrowthMark(1, Modifier.size(80.dp))
            Text("Your thoughtful choices will appear here.", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun statusColor(action: ActionTaken) = when (action) {
    ActionTaken.ACCEPTED -> MaterialTheme.colorScheme.tertiary
    ActionTaken.DISMISSED -> MaterialTheme.colorScheme.onSurfaceVariant
    ActionTaken.ALREADY_ALIGNED -> MaterialTheme.colorScheme.secondary
    ActionTaken.NONE -> MaterialTheme.colorScheme.primary
}

private fun actionLabel(action: ActionTaken): String = when (action) {
    ActionTaken.ACCEPTED -> "Accepted · +10 points"
    ActionTaken.DISMISSED -> "Dismissed"
    ActionTaken.ALREADY_ALIGNED -> "Already aligned"
    ActionTaken.NONE -> "No response"
}

@Composable
private fun GoalChip(
    goal: String,
    index: Int,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val lavender = index % 2 == 0
    val fill = if (lavender) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val badge = if (lavender) LavenderBadge else MaterialTheme.colorScheme.secondary
    Card(
        modifier = modifier.widthIn(min = 164.dp, max = 220.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = fill),
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(goalIcon(goal), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(goal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            onRemove?.let { remove ->
                IconButton(onClick = remove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove $goal", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private fun goalIcon(goal: String) = when {
    goal.contains("read", true) || goal.contains("book", true) -> Icons.Filled.MenuBook
    goal.contains("walk", true) || goal.contains("run", true) || goal.contains("exercise", true) -> Icons.Filled.DirectionsRun
    goal.contains("food", true) || goal.contains("eat", true) || goal.contains("healthy", true) -> Icons.Filled.Restaurant
    goal.contains("money", true) || goal.contains("save", true) -> Icons.Filled.Savings
    goal.contains("kid", true) || goal.contains("family", true) -> Icons.Filled.Groups
    else -> Icons.Filled.Stars
}

@Composable
private fun TrustBadge() {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text("🔒 Your data stays on this device", Modifier.padding(screenPadding), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(screenPadding)) {
            GrowthMark(1, Modifier.size(112.dp))
            Text("Preparing your coach", style = MaterialTheme.typography.titleMedium)
        }
    }
}
