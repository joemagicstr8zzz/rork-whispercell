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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.LogEntry
import com.rork.whispercell.models.LogLevel
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.PerformanceUiState
import com.rork.whispercell.services.WhisperCellForegroundService
import com.rork.whispercell.viewmodels.WhisperCellViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
    Performance("Performance", Icons.Filled.Mic),
    Routine("Routine", Icons.Filled.Tune),
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
            startPerformanceForegroundService(context, "Recording")
            viewModel.startBackgroundSession()
        } else if (pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            viewModel.reportPermissionBlocked(refreshed.missingLabels.joinToString().ifBlank { "required listening permissions" })
        }
    }
    val requestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) }
    val startCapture = {
        val refreshed = buildPermissionUiState(context)
        permissionStatus = refreshed
        if (refreshed.hasRequiredRuntimePermissions) {
            startPerformanceForegroundService(context, "Recording")
            viewModel.startBackgroundSession()
        } else {
            pendingStartAfterPermission = true
            viewModel.reportPermissionBlocked(refreshed.missingLabels.joinToString().ifBlank { "required listening permissions" })
            requestPermissions()
        }
    }
    val stopCapture = { stopPerformanceForegroundService(context); viewModel.stopSession() }
    val panicStop = { stopPerformanceForegroundService(context); viewModel.panicStop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WhisperCell", fontWeight = FontWeight.Black)
                        Text("Recorder → transcript → brain → Inject", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    RecordingBeacon(state.isListeningVisible)
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
                MainTab.Performance -> PerformanceScreen(state, permissionStatus, startCapture, stopCapture, panicStop, requestPermissions, viewModel::clearSession)
                MainTab.Routine -> RoutineSetupScreen(state, viewModel)
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
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onPanicStop: () -> Unit,
    onRequestPermissions: () -> Unit,
    onClearSession: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SessionHero(state) }
        item { PermissionReadinessCard(permissionStatus, onRequestPermissions) }
        item {
            SectionCard("Recorder controls", Icons.Filled.RadioButtonChecked) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Start Recording", Icons.Filled.PlayArrow, Modifier.weight(1f), onStartCapture)
                    SecondaryControlButton("Stop", Icons.Filled.Stop, Modifier.weight(1f), onStopCapture)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DangerControlButton("Panic Stop", Icons.Filled.Emergency, Modifier.weight(1f), onPanicStop)
                    SecondaryControlButton("Clear", Icons.Filled.DeleteSweep, Modifier.weight(1f), onClearSession)
                }
            }
        }
        item {
            SectionCard("Live capture", Icons.Filled.Podcasts) {
                InfoRow("Provider", state.providerActivity)
                InfoRow("Last heard", state.lastTranscriptLine)
                InfoRow("Transcript", state.rawCapturedTranscript.ifBlank { "Recording has not produced transcript yet" })
                InfoRow("Analysis", state.aiActivity)
                if (state.transcriptEvents.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.transcriptEvents.take(5).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item {
            SectionCard("Brain and Inject", Icons.Filled.Bolt) {
                val brain = state.transcriptBrain
                val payload = brain?.primaryPayload?.takeIf { it.isNotBlank() } ?: state.selectedMatch?.payload ?: "Nothing ready"
                InfoRow("Payload", payload)
                InfoRow("Confidence", brain?.let { "${(it.confidence * 100).toInt()}%" } ?: "Waiting")
                InfoRow("Decision", brain?.let { if (it.shouldPublish) "Auto-publish ready" else "Waiting for stronger payload" } ?: "Waiting for transcript")
                InfoRow("Inject", if (state.settings.injectEnabled) "Automatic output ON" else "Disabled")
                InfoRow("Last sent", state.lastPublishedValue)
                if (brain?.warnings?.isNotEmpty() == true) InfoRow("Warnings", brain.warnings.joinToString(" • "))
            }
        }
        if (state.extractedData?.detectedItems?.isNotEmpty() == true) {
            item {
                SectionCard("Detected values", Icons.Filled.Article) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { state.extractedData.detectedItems.forEach { DetectedItemRow(it) } }
                }
            }
        }
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
        Text(if (state.isListeningVisible) "Recording is active. Stop manually or use a configured stop phrase." else "No active capture. Tap Start Recording before performance.", color = MaterialTheme.colorScheme.onSurface)
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
            PrimaryControlButton("Grant permissions", Icons.Filled.Mic, Modifier.fillMaxWidth(), onRequestPermissions)
        }
    }
}

@Composable
private fun RoutineSetupScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Routine", Icons.Filled.Tune) {
                InfoRow("Active", state.activeProfile.name)
                InfoRow("Output", "Automatic to Inject when a payload is ready")
            }
        }
        items(state.profiles, key = { it.id }) { profile -> ProfileCard(profile, profile.id == state.activeProfile.id) { viewModel.activateProfile(profile.id) } }
        item {
            SectionCard("Allowed outputs", Icons.Filled.Route) {
                Text("These are the value types the active routine may publish. Formatting is automatic.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.channels, key = { it.id }) { channel -> SimpleChannelCard(channel, channel.id in state.activeProfile.activeChannelIds, viewModel) }
    }
}

@Composable
private fun InjectScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Inject", Icons.Filled.Hub) {
                SettingsToggleRow("Automatic Inject output", state.settings.injectEnabled, viewModel::toggleInjectEnabled)
                OutlinedTextField(
                    value = state.settings.selectionCode,
                    onValueChange = viewModel::updateSelectionCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Inject Code") },
                    supportingText = { Text("Enter code only. Do not paste the full URL. Max 7 letters/numbers.") },
                    singleLine = true
                )
                InfoRow("Endpoint", state.lastInjectUrl.ifBlank { "https://11z.co/_w/{INJECT_CODE}/selection" })
                InfoRow("Last status", state.injectStatus.label)
                InfoRow("Last value", state.lastPublishedValue)
                Text("No manual publish button. WhisperCell sends one value automatically after transcription and analysis.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LogsScreen(state: PerformanceUiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("Session log", Icons.Filled.Podcasts) { Text("Current session only unless save options are enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(state.logs, key = { it.id }) { LogRow(it) }
    }
}

@Composable
private fun SettingsScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel, permissionStatus: PermissionUiState, onRequestPermissions: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PermissionReadinessCard(permissionStatus, onRequestPermissions) }
        item {
            SectionCard("Capture", Icons.Filled.Mic) {
                Text("Native Recorder is always the microphone engine. OpenAI and ElevenLabs are transcription/analysis services, not microphone choices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsToggleRow("Use Start Phrase", state.settings.startPhraseEnabled, viewModel::toggleStartPhraseEnabled)
                OutlinedTextField(value = primaryStartPhrase(state), onValueChange = viewModel::updateGlobalStartPhrase, modifier = Modifier.fillMaxWidth(), label = { Text("Start Phrase") }, singleLine = true)
                SettingsToggleRow("Use Stop Phrase", state.settings.stopPhraseEnabled, viewModel::toggleStopPhraseEnabled)
                OutlinedTextField(value = primaryStopPhrase(state), onValueChange = viewModel::updateGlobalStopPhrase, modifier = Modifier.fillMaxWidth(), label = { Text("Stop Phrase") }, singleLine = true)
                OutlinedTextField(value = state.settings.maximumCaptureSeconds.toString(), onValueChange = viewModel::updateMaximumCaptureSeconds, modifier = Modifier.fillMaxWidth(), label = { Text("Max recording seconds") }, singleLine = true)
            }
        }
        item {
            SectionCard("Save options", Icons.Filled.Article) {
                SettingsToggleRow("Save audio after session", state.settings.audioSavingEnabled, viewModel::toggleAudioSaving)
                SettingsToggleRow("Keep logs for 24 hours", state.settings.keepLogsFor24Hours, viewModel::toggleKeepLogs24Hours)
                InfoRow("Transcript retention", state.settings.transcriptSavePolicy)
            }
        }
        item {
            SectionCard("OpenAI", Icons.Filled.Bolt) {
                SettingsToggleRow("Use OpenAI for transcription/analysis", state.settings.openAiTranscriptionEnabled, viewModel::toggleOpenAiEnabled)
                OutlinedTextField(value = state.settings.openAiApiKey, onValueChange = viewModel::updateOpenAiApiKey, modifier = Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, singleLine = true)
                OutlinedTextField(value = state.settings.openAiModel, onValueChange = viewModel::updateOpenAiModel, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
            }
        }
        item {
            SectionCard("ElevenLabs", Icons.Filled.Article) {
                SettingsToggleRow("Use ElevenLabs for transcription", state.settings.elevenLabsEnabled, viewModel::toggleElevenLabsEnabled)
                OutlinedTextField(value = state.settings.elevenLabsApiKey, onValueChange = viewModel::updateElevenLabsApiKey, modifier = Modifier.fillMaxWidth(), label = { Text("ElevenLabs API Key") }, singleLine = true)
                OutlinedTextField(value = state.settings.elevenLabsModel, onValueChange = viewModel::updateElevenLabsModel, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
            }
        }
    }
}

@Composable
private fun HelpScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionCard("Welcome to WhisperCell", Icons.Filled.Help) { Text("WhisperCell records a performance, transcribes it, lets Transcript Brain choose the best payload, and publishes once to Inject automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { SectionCard("Basic flow", Icons.Filled.RadioButtonChecked) { listOf("Enter your Inject Code.", "Tap Start Recording.", "Talk naturally.", "Stop manually or use a Stop Phrase.", "WhisperCell transcribes and analyzes the recording.", "The best payload is sent to Inject automatically.").forEachIndexed { index, step -> Text("${index + 1}. $step", color = MaterialTheme.colorScheme.onSurface) } } }
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
    Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors()) { Icon(icon, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
private fun RecordingBeacon(isActive: Boolean) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (isActive) Color(0xFF39D7C8) else Color(0xFF45545A))); Spacer(Modifier.width(6.dp)); Text(if (isActive) "RECORDING" else "IDLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun DetectedItemRow(item: DetectedItem) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF0F1B24), border = BorderStroke(1.dp, Color(0xFF203B45))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(item.normalizedValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Source: ${item.sourcePhrase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }; Text("${(item.confidence * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }

@Composable
private fun ProfileCard(profile: PerformanceProfile, isActive: Boolean, onActivate: () -> Unit) { SectionCard(profile.name, if (isActive) Icons.Filled.RadioButtonChecked else Icons.Filled.Tune) { InfoRow("Categories", profile.activeCategories.joinToString { it.label }); InfoRow("Mode", if (profile.fullAutomationEnabled) "Auto-publish" else "Auto-publish via global setting"); Button(onClick = onActivate, enabled = !isActive, modifier = Modifier.fillMaxWidth()) { Text(if (isActive) "Active routine" else "Activate") } } }

@Composable
private fun SimpleChannelCard(channel: Channel, active: Boolean, viewModel: WhisperCellViewModel) { SectionCard(channel.name.removeSuffix(" Channel"), Icons.Filled.Route) { InfoRow("Detects", channel.inputCategories.joinToString { it.label }); SettingsToggleRow("Allowed", active) { viewModel.toggleProfileChannel(channel.id, it) } } }

@Composable
private fun LogRow(log: LogEntry) { val color = when (log.level) { LogLevel.Success -> Color(0xFF89F29A); LogLevel.Warning -> Color(0xFFFFB85C); LogLevel.Error -> Color(0xFFFF6B6B); LogLevel.Info -> MaterialTheme.colorScheme.primary }; Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF0B121A), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) { Text(log.timestamp, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(70.dp)); Text(log.message, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)) } } }

@Composable
private fun AlertCard(message: String) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))) { Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } }

private fun primaryStartPhrase(state: PerformanceUiState): String = state.settings.startPhrases.firstOrNull().orEmpty().ifBlank { "open" }
private fun primaryStopPhrase(state: PerformanceUiState): String = state.settings.stopPhrases.firstOrNull().orEmpty().ifBlank { "perfect" }
