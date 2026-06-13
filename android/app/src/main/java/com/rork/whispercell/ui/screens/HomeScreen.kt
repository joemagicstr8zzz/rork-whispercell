package com.rork.whispercell.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData
import com.rork.whispercell.models.LogEntry
import com.rork.whispercell.models.LogLevel
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.PerformanceUiState
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.services.WhisperCellForegroundService
import com.rork.whispercell.viewmodels.WhisperCellViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
    Performance("Performance", Icons.Filled.Mic),
    Review("Review", Icons.Filled.Article),
    Profiles("Routines", Icons.Filled.Tune),
    Channels("Outputs", Icons.Filled.Route),
    Inject("Inject", Icons.Filled.Hub),
    Logs("Logs", Icons.Filled.Podcasts),
    Settings("Settings", Icons.Filled.Settings),
    Help("Help", Icons.Filled.Help)
}

private data class PermissionUiState(
    val hasRecordAudio: Boolean,
    val hasPostNotifications: Boolean,
    val hasMicrophoneHardware: Boolean,
    val missingLabels: List<String>
) {
    val hasRequiredRuntimePermissions: Boolean = hasRecordAudio && hasPostNotifications
}

private fun buildPermissionUiState(context: Context): PermissionUiState {
    val hasRecordAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val hasMicrophoneHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    val missingLabels = buildList {
        if (!hasRecordAudio) add("Microphone")
        if (!hasPostNotifications) add("Notifications")
    }
    return PermissionUiState(hasRecordAudio, hasPostNotifications, hasMicrophoneHardware, missingLabels)
}

private fun requiredRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun startPerformanceForegroundService(context: Context, stateLabel: String) {
    ContextCompat.startForegroundService(context, WhisperCellForegroundService.intent(context, stateLabel))
}

private fun stopPerformanceForegroundService(context: Context) {
    context.stopService(WhisperCellForegroundService.intent(context, "Stopping"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: WhisperCellViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(MainTab.Performance) }
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(buildPermissionUiState(context)) }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val refreshed = buildPermissionUiState(context)
        permissionStatus = refreshed
        if (pendingStartAfterPermission && refreshed.hasRequiredRuntimePermissions) {
            pendingStartAfterPermission = false
            startPerformanceForegroundService(context, "Waiting for start phrase")
            viewModel.startBackgroundSession()
        } else if (pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            viewModel.reportPermissionBlocked(refreshed.missingLabels.joinToString().ifBlank { "required listening permissions" })
        }
    }
    val requestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) }
    val startBackgroundSession = {
        val refreshed = buildPermissionUiState(context)
        permissionStatus = refreshed
        if (refreshed.hasRequiredRuntimePermissions) {
            startPerformanceForegroundService(context, "Waiting for start phrase")
            viewModel.startBackgroundSession()
        } else {
            pendingStartAfterPermission = true
            viewModel.reportPermissionBlocked(refreshed.missingLabels.joinToString().ifBlank { "required listening permissions" })
            requestPermissions()
        }
    }
    val stopSession = { stopPerformanceForegroundService(context); viewModel.stopSession() }
    val panicStop = { stopPerformanceForegroundService(context); viewModel.panicStop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WhisperCell", fontWeight = FontWeight.Black)
                        Text("Performer-side AI listening hub", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    ListeningBeacon(state.isListeningVisible)
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF071017)) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF05080C), Color(0xFF071821), Color(0xFF05080C))))
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.Performance -> PerformanceScreen(state, permissionStatus, startBackgroundSession, stopSession, panicStop, viewModel::pauseListening, viewModel::resumeListening, requestPermissions, viewModel::clearSession)
                MainTab.Review -> ReviewScreen(state, viewModel)
                MainTab.Profiles -> ProfilesScreen(state, viewModel)
                MainTab.Channels -> ChannelsScreen(state, viewModel)
                MainTab.Inject -> InjectScreen(state, viewModel)
                MainTab.Logs -> LogsScreen(state)
                MainTab.Settings -> SettingsScreen(state, viewModel, permissionStatus, requestPermissions)
                MainTab.Help -> HelpScreen()
            }
        }
    }
}

@Composable
private fun PerformanceScreen(
    state: PerformanceUiState,
    permissionStatus: PermissionUiState,
    onStartBackgroundSession: () -> Unit,
    onStopSession: () -> Unit,
    onPanicStop: () -> Unit,
    onPauseListening: () -> Unit,
    onResumeListening: () -> Unit,
    onRequestPermissions: () -> Unit,
    onClearSession: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SessionHero(state) }
        item { PermissionReadinessCard(permissionStatus, onRequestPermissions) }
        item {
            SectionCard("Hands-free controls", Icons.Filled.RadioButtonChecked) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Start Background Session", Icons.Filled.PlayArrow, Modifier.weight(1f), onStartBackgroundSession)
                    DangerControlButton("Panic Stop", Icons.Filled.Emergency, Modifier.weight(1f), onPanicStop)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Pause", Icons.Filled.Pause, Modifier.weight(1f), onPauseListening)
                    SecondaryControlButton("Resume", Icons.Filled.PlayArrow, Modifier.weight(1f), onResumeListening)
                    SecondaryControlButton("Stop", Icons.Filled.Stop, Modifier.weight(1f), onStopSession)
                }
                Spacer(Modifier.height(10.dp))
                SecondaryControlButton("Clear Session", Icons.Filled.DeleteSweep, Modifier.fillMaxWidth(), onClearSession)
            }
        }
        item {
            SectionCard("Current capture", Icons.Filled.Bolt) {
                InfoRow("Active routine", state.activeProfile.name)
                InfoRow("Start Phrase", primaryStartPhrase(state))
                InfoRow("Stop Phrase", primaryStopPhrase(state))
                InfoRow("Outputs", state.activeChannels.joinToString { it.name.removeSuffix(" Channel") }.ifBlank { "No active outputs" })
                InfoRow("Selection code", state.settings.selectionCode.ifBlank { "Not set" })
                InfoRow("Status", state.injectStatus.label)
                InfoRow("URL", state.lastInjectUrl.ifBlank { "Generated after code/value are ready" })
            }
        }
        item { TranscriptMonitorCard(state) }
        if (state.errorMessage != null) item { AlertCard(state.errorMessage) }
    }
}

@Composable
private fun SessionHero(state: PerformanceUiState) {
    val accent = if (state.isListeningVisible) Color(0xFF39D7C8) else MaterialTheme.colorScheme.primary
    SectionCard("Session status", Icons.Filled.Mic) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SESSION STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.sessionState.label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = accent)
            }
        }
        Text(if (state.isListeningVisible) "Listening is active. Audio saving is OFF by default." else "No active listening. Start intentionally before performance.", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PermissionReadinessCard(status: PermissionUiState, onRequestPermissions: () -> Unit) {
    SectionCard("Permissions", Icons.Filled.RadioButtonChecked) {
        InfoRow("Microphone", if (status.hasRecordAudio) "Granted" else "Not granted")
        InfoRow("Notifications", if (status.hasPostNotifications) "Granted" else "Not granted")
        InfoRow("Microphone hardware", if (status.hasMicrophoneHardware) "Detected" else "Not detected")
        if (!status.hasRequiredRuntimePermissions) {
            Spacer(Modifier.height(10.dp))
            PrimaryControlButton("Grant listening permissions", Icons.Filled.Mic, Modifier.fillMaxWidth(), onRequestPermissions)
        }
    }
}

@Composable
private fun ReviewScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    val clipboard = LocalClipboardManager.current
    val presets = listOf(
        "I want to go to Spain and meet Tom Cruise on June 2nd, 2035.",
        "My song is Bohemian Rhapsody by Queen.",
        "I'm thinking of the Queen of Hearts.",
        "My birthday is March 14.",
        "The serial number is A12345678B."
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Mock Transcript Mode", Icons.Filled.Article) {
                OutlinedTextField(value = state.mockTranscriptInput, onValueChange = viewModel::updateMockTranscript, modifier = Modifier.fillMaxWidth(), label = { Text("Manual transcript input") }, minLines = 3)
                Spacer(Modifier.height(10.dp))
                presets.forEach { preset ->
                    OutlinedButton(onClick = { viewModel.loadPresetTranscript(preset) }, modifier = Modifier.fillMaxWidth()) { Text(preset, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Fake partials", Icons.Filled.Podcasts, Modifier.weight(1f), viewModel::fakePartialPlayback)
                    SecondaryControlButton("Run extraction", Icons.Filled.Bolt, Modifier.weight(1f), viewModel::runExtractionOnly)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Run routine", Icons.Filled.CallSplit, Modifier.weight(1f), viewModel::runSelectedProfile)
                    PrimaryControlButton("Simulate", Icons.Filled.Publish, Modifier.weight(1f), viewModel::simulateInjectPublish)
                }
            }
        }
        item {
            SectionCard("Detected values", Icons.Filled.Bolt) {
                val items = state.extractedData?.detectedItems.orEmpty()
                if (items.isEmpty()) Text("No values detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { DetectedItemRow(it) } }
            }
        }
        item {
            SectionCard("Publish review", Icons.Filled.Publish) {
                InfoRow("Selected output", state.selectedMatch?.channel?.name ?: "No output selected")
                InfoRow("Payload", state.selectedMatch?.payload ?: "Nothing ready")
                InfoRow("URL", state.lastInjectUrl.ifBlank { "Generated after publish/test" })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Publish", Icons.Filled.Publish, Modifier.weight(1f), viewModel::publishSelectedValue)
                    SecondaryControlButton("Copy payload", Icons.Filled.ContentCopy, Modifier.weight(1f)) { clipboard.setText(AnnotatedString(state.selectedMatch?.payload ?: state.lastPublishedValue)) }
                }
            }
        }
        item { EmergencyRevealCard(state.selectedMatch?.payload ?: state.lastPublishedValue) }
    }
}

@Composable
private fun ProfilesScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Active routine", Icons.Filled.Tune) {
                InfoRow("Routine", state.activeProfile.name)
                InfoRow("Start", primaryStartPhrase(state))
                InfoRow("Stop", primaryStopPhrase(state))
                SettingsToggleRow("Review Mode", state.activeProfile.reviewModeEnabled, viewModel::toggleActiveProfileReviewMode)
                SettingsToggleRow("Full Automation", state.activeProfile.fullAutomationEnabled, viewModel::toggleActiveProfileFullAutomation)
                Text("Outputs used by this routine", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.channels.forEach { channel -> SettingsToggleRow(channel.name, channel.id in state.activeProfile.activeChannelIds) { enabled -> viewModel.toggleProfileChannel(channel.id, enabled) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Duplicate", Icons.Filled.ContentCopy, Modifier.weight(1f), viewModel::duplicateActiveProfile)
                    SecondaryControlButton("Delete", Icons.Filled.DeleteSweep, Modifier.weight(1f), viewModel::deleteActiveProfile)
                }
            }
        }
        items(state.profiles, key = { it.id }) { profile -> ProfileCard(profile, profile.id == state.activeProfile.id) { viewModel.activateProfile(profile.id) } }
    }
}

@Composable
private fun ChannelsScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("Output rules", Icons.Filled.Route) { Text("Channels decide which detected value becomes the published value.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(state.channels, key = { it.id }) { channel -> ChannelCard(channel, viewModel) }
    }
}

@Composable
private fun InjectScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Inject setup", Icons.Filled.Hub) {
                SettingsToggleRow("Enable Inject", state.settings.injectEnabled, viewModel::toggleInjectEnabled)
                OutlinedTextField(
                    value = state.settings.selectionCode,
                    onValueChange = viewModel::updateSelectionCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Inject Code") },
                    supportingText = { Text("Enter your Inject code only. Do not paste the full URL. Max 7 letters/numbers.") },
                    singleLine = true
                )
                InfoRow("Status", state.injectStatus.label)
                InfoRow("Generated URL", state.lastInjectUrl.ifBlank { "https://11z.co/_w/{INJECT_CODE}/selection?value={ENCODED_VALUE}" })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Test Inject", Icons.Filled.Bolt, Modifier.weight(1f), viewModel::testInject)
                    SecondaryControlButton("Copy URL", Icons.Filled.ContentCopy, Modifier.weight(1f)) { clipboard.setText(AnnotatedString(state.lastInjectUrl)) }
                }
            }
        }
        item {
            SectionCard("Publish behavior", Icons.Filled.Publish) {
                InfoRow("Send method", "GET with value parameter")
                InfoRow("Timeout", "${state.settings.injectTimeoutSeconds} seconds")
                InfoRow("Retry", if (state.settings.injectRetryOnce) "Retry once on failure" else "No retry")
                InfoRow("Last sent value", state.lastPublishedValue)
                PrimaryControlButton("Publish selected value", Icons.Filled.Publish, Modifier.fillMaxWidth(), viewModel::publishSelectedValue)
            }
        }
        item { EmergencyRevealCard(state.selectedMatch?.payload ?: state.lastPublishedValue) }
    }
}

@Composable
private fun LogsScreen(state: PerformanceUiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("Session log", Icons.Filled.Podcasts) { Text("Current session only by default. No audio is saved unless explicitly enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(state.logs, key = { it.id }) { LogRow(it) }
    }
}

@Composable
private fun SettingsScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel, permissionStatus: PermissionUiState, onRequestPermissions: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PermissionReadinessCard(permissionStatus, onRequestPermissions) }
        item {
            SectionCard("Start and stop phrases", Icons.Filled.Tune) {
                SettingsToggleRow("Start Phrase enabled", state.settings.startPhraseEnabled, viewModel::toggleStartPhraseEnabled)
                OutlinedTextField(value = primaryStartPhrase(state), onValueChange = viewModel::updateGlobalStartPhrase, modifier = Modifier.fillMaxWidth(), label = { Text("Global Start Phrase") }, singleLine = true)
                SettingsToggleRow("Stop Phrase enabled", state.settings.stopPhraseEnabled, viewModel::toggleStopPhraseEnabled)
                OutlinedTextField(value = primaryStopPhrase(state), onValueChange = viewModel::updateGlobalStopPhrase, modifier = Modifier.fillMaxWidth(), label = { Text("Global Stop Phrase") }, singleLine = true)
                SettingsToggleRow("Remove phrases from transcript", state.settings.removeStartAndStopPhrases, viewModel::toggleRemovePhrases)
                InfoRow("Silence behavior", "Ignore silence as stop trigger")
                OutlinedTextField(value = state.settings.maximumCaptureSeconds.toString(), onValueChange = viewModel::updateMaximumCaptureSeconds, modifier = Modifier.fillMaxWidth(), label = { Text("Maximum capture seconds") }, singleLine = true)
            }
        }
        item { SectionCard("Speech providers", Icons.Filled.Mic) { state.speechProviders.forEach { provider -> ProviderRow(provider, provider.id == state.settings.selectedSpeechProviderId) { viewModel.selectSpeechProvider(provider.id) } } } }
        item {
            SectionCard("OpenAI / GPT extraction", Icons.Filled.Bolt) {
                SettingsToggleRow("Enable GPT extraction", state.settings.openAiTranscriptionEnabled, viewModel::toggleOpenAiEnabled)
                OutlinedTextField(value = state.settings.openAiApiKey, onValueChange = viewModel::updateOpenAiApiKey, modifier = Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, singleLine = true)
                OutlinedTextField(value = state.settings.openAiModel, onValueChange = viewModel::updateOpenAiModel, modifier = Modifier.fillMaxWidth(), label = { Text("Model or custom model name") }, singleLine = true)
                SettingsToggleRow("Realtime transcription", state.settings.openAiRealtimeEnabled, viewModel::toggleOpenAiRealtime)
                SettingsToggleRow("Chunk transcription", state.settings.openAiChunkEnabled, viewModel::toggleOpenAiChunk)
                InfoRow("Validation", state.settings.openAiValidationStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Validate", Icons.Filled.Bolt, Modifier.weight(1f), viewModel::validateOpenAiKey)
                    PrimaryControlButton("Run Test", Icons.Filled.Mic, Modifier.weight(1f), viewModel::testOpenAiTranscription)
                }
            }
        }
        item {
            SectionCard("ElevenLabs Speech to Text", Icons.Filled.Article) {
                SettingsToggleRow("Enable ElevenLabs", state.settings.elevenLabsEnabled, viewModel::toggleElevenLabsEnabled)
                OutlinedTextField(value = state.settings.elevenLabsApiKey, onValueChange = viewModel::updateElevenLabsApiKey, modifier = Modifier.fillMaxWidth(), label = { Text("ElevenLabs API Key") }, singleLine = true)
                OutlinedTextField(value = state.settings.elevenLabsModel, onValueChange = viewModel::updateElevenLabsModel, modifier = Modifier.fillMaxWidth(), label = { Text("Model/provider mode") }, singleLine = true)
                InfoRow("Validation", state.settings.elevenLabsValidationStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Validate", Icons.Filled.Bolt, Modifier.weight(1f), viewModel::validateElevenLabsKey)
                    PrimaryControlButton("Run Test", Icons.Filled.Mic, Modifier.weight(1f), viewModel::testElevenLabsTranscription)
                }
            }
        }
        item {
            SectionCard("Privacy and automation", Icons.Filled.Settings) {
                SettingsToggleRow("Review Mode", state.settings.reviewModeEnabled, viewModel::toggleActiveProfileReviewMode)
                SettingsToggleRow("Full Automation", state.settings.fullAutomationEnabled, viewModel::toggleActiveProfileFullAutomation)
                SettingsToggleRow("Audio Save", state.settings.audioSavingEnabled, viewModel::toggleAudioSaving)
                SettingsToggleRow("Keep logs for 24 hours", state.settings.keepLogsFor24Hours, viewModel::toggleKeepLogs24Hours)
                InfoRow("Transcript Save", state.settings.transcriptSavePolicy)
                SettingsToggleRow("Continue listening after publish", state.settings.continueListeningAfterPublish, viewModel::toggleContinueListening)
            }
        }
    }
}

@Composable
private fun HelpScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionCard("Welcome to WhisperCell", Icons.Filled.Help) { Text("WhisperCell listens for a natural start phrase, captures conversation, extracts useful performance information, and publishes the chosen value using your Inject Code.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { SectionCard("Basic setup", Icons.Filled.RadioButtonChecked) { listOf("Choose a routine.", "Set natural start and stop phrases.", "Enter your Inject Code only.", "Start Background Session.", "Perform normally.", "Review or publish the selected value.").forEachIndexed { index, step -> Text("${index + 1}. $step", color = MaterialTheme.colorScheme.onSurface) } } }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xD90B121A)), border = BorderStroke(1.dp, Color(0xFF16323A))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PrimaryControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(icon, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun SecondaryControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(icon, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun DangerControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Icon(icon, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun InfoRow(label: String, value: String) { Column(Modifier.padding(vertical = 5.dp)) { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) } }

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}

@Composable
private fun StatusChip(text: String) { Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } }

@Composable
private fun ListeningBeacon(isActive: Boolean) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (isActive) Color(0xFF39D7C8) else Color(0xFF45545A))); Spacer(Modifier.width(6.dp)); Text(if (isActive) "LISTENING" else "IDLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun DetectedItemRow(item: DetectedItem) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF0F1B24), border = BorderStroke(1.dp, Color(0xFF203B45))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(item.normalizedValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Source: ${item.sourcePhrase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }; Text("${(item.confidence * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }

@Composable
private fun ProfileCard(profile: PerformanceProfile, isActive: Boolean, onActivate: () -> Unit) { SectionCard(profile.name, if (isActive) Icons.Filled.RadioButtonChecked else Icons.Filled.Tune) { InfoRow("Categories", profile.activeCategories.joinToString { it.label }); InfoRow("Mode", if (profile.fullAutomationEnabled) "Full Automation" else "Review Mode"); Button(onClick = onActivate, enabled = !isActive, modifier = Modifier.fillMaxWidth()) { Text(if (isActive) "Active routine" else "Activate routine") } } }

@Composable
private fun ChannelCard(channel: Channel, viewModel: WhisperCellViewModel) { SectionCard(channel.name, Icons.Filled.Route) { SettingsToggleRow("Enabled", channel.enabled) { viewModel.toggleChannelEnabled(channel.id, it) }; InfoRow("Input category", channel.inputCategories.joinToString { it.label }); OutlinedTextField(value = channel.payloadFormat, onValueChange = { viewModel.updateChannelPayloadFormat(channel.id, it) }, modifier = Modifier.fillMaxWidth(), label = { Text("Payload format") }, singleLine = true); SettingsToggleRow("Auto-publish", channel.autoPublish) { viewModel.toggleChannelAutoPublish(channel.id, it) }; InfoRow("Confidence", "${(channel.confidenceThreshold * 100).toInt()}%"); PrimaryControlButton("Test output", Icons.Filled.Bolt, Modifier.fillMaxWidth()) { viewModel.testChannel(channel.id) } } }

@Composable
private fun ProviderRow(provider: SpeechProviderInfo, isSelected: Boolean, onSelect: () -> Unit) { Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(14.dp), color = if (isSelected) Color(0xFF142936) else Color(0xFF0F1B24), border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF203B45))) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(provider.displayName, fontWeight = FontWeight.Bold); Text("Mode: ${provider.mode} • Background: ${if (provider.supportsBackground) "Yes" else "No"} • Partials: ${if (provider.supportsPartialResults) "Yes" else "No"}", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(provider.status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium); OutlinedButton(onClick = onSelect, enabled = !isSelected, modifier = Modifier.fillMaxWidth()) { Text(if (isSelected) "Selected provider" else "Select provider") } } } }

@Composable
private fun LogRow(log: LogEntry) { val color = when (log.level) { LogLevel.Success -> Color(0xFF89F29A); LogLevel.Warning -> Color(0xFFFFB85C); LogLevel.Error -> Color(0xFFFF6B6B); LogLevel.Info -> MaterialTheme.colorScheme.primary }; Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF0B121A), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) { Text(log.timestamp, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(70.dp)); Text(log.message, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)) } } }

@Composable
private fun EmergencyRevealCard(value: String) { SectionCard("Emergency reveal screen", Icons.Filled.ContentCopy) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF111820), border = BorderStroke(1.dp, Color(0xFF2A3B46))) { Column(Modifier.padding(18.dp)) { Text("Quote", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); Text(if (value.isBlank() || value == "Nothing published yet.") "Keep one clear image in mind." else value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) } } } }

@Composable
private fun AlertCard(message: String) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))) { Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } }

@Composable
private fun TranscriptMonitorCard(state: PerformanceUiState) { SectionCard("Transcript monitor", Icons.Filled.Podcasts) { InfoRow("Speech provider", state.providerActivity); InfoRow("Extraction", state.aiActivity); InfoRow("Last transcript", state.lastTranscriptLine); InfoRow("Raw captured", state.rawCapturedTranscript.ifBlank { "Nothing captured yet" }); InfoRow("Cleaned", state.currentTranscript.ifBlank { "Nothing processed yet" }); if (state.transcriptEvents.isNotEmpty()) { Spacer(Modifier.height(8.dp)); state.transcriptEvents.take(5).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) } } } }

private fun primaryStartPhrase(state: PerformanceUiState): String = state.settings.startPhrases.firstOrNull().orEmpty().ifBlank { "Picture this clearly for me" }
private fun primaryStopPhrase(state: PerformanceUiState): String = state.settings.stopPhrases.firstOrNull().orEmpty().ifBlank { "Perfect" }
private fun detectedSummary(data: ExtractedPerformanceData?): String { val items = data?.detectedItems.orEmpty(); return if (items.isEmpty()) "Awaiting transcript" else items.take(4).joinToString(" • ") { "${it.category.label}: ${it.normalizedValue}" } }
