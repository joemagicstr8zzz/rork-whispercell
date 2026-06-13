package com.rork.whispercell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.rork.whispercell.models.SessionState
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.viewmodels.WhisperCellViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
    Performance("Performance", Icons.Filled.Mic),
    Review("Review", Icons.Filled.Article),
    Profiles("Profiles", Icons.Filled.Tune),
    Channels("Channels", Icons.Filled.Route),
    Inject("Inject", Icons.Filled.Hub),
    Logs("Logs", Icons.Filled.Podcasts),
    Settings("Settings", Icons.Filled.Settings),
    Help("Help", Icons.Filled.Help)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: WhisperCellViewModel = viewModel()
) {
    val state: PerformanceUiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab: MainTab by remember { mutableStateOf(MainTab.Performance) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WhisperCell", fontWeight = FontWeight.Black)
                        Text(
                            text = "Performer-side AI listening hub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ListeningBeacon(isActive = state.isListeningVisible)
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF05080C), Color(0xFF071821), Color(0xFF05080C))
                    )
                )
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.Performance -> PerformanceScreen(state, viewModel)
                MainTab.Review -> ReviewScreen(state, viewModel)
                MainTab.Profiles -> ProfilesScreen(state, viewModel)
                MainTab.Channels -> ChannelsScreen(state)
                MainTab.Inject -> InjectScreen(state, viewModel)
                MainTab.Logs -> LogsScreen(state)
                MainTab.Settings -> SettingsScreen(state)
                MainTab.Help -> HelpScreen()
            }
        }
    }
}

@Composable
private fun PerformanceScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SessionHero(state = state)
        }
        item {
            SectionCard(title = "Hands-free controls", icon = Icons.Filled.RadioButtonChecked) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton(
                        text = "Start Background Session",
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::startBackgroundSession
                    )
                    DangerControlButton(
                        text = "Panic Stop",
                        icon = Icons.Filled.Emergency,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::panicStop
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Pause", Icons.Filled.Pause, Modifier.weight(1f), viewModel::pauseListening)
                    SecondaryControlButton("Resume", Icons.Filled.PlayArrow, Modifier.weight(1f), viewModel::resumeListening)
                    SecondaryControlButton("Stop", Icons.Filled.Stop, Modifier.weight(1f), viewModel::stopSession)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = viewModel::clearSession, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Session")
                }
            }
        }
        item {
            SectionCard(title = "Current capture", icon = Icons.Filled.Bolt) {
                InfoRow("Active profile", state.activeProfile.name)
                InfoRow("Start Phrase", state.activeProfile.startPhrase)
                InfoRow("Stop Phrase", state.activeProfile.stopPhrase)
                InfoRow("Active channels", state.activeChannels.joinToString { it.name })
                InfoRow("Inject status", state.injectStatus.label)
                InfoRow("Notification", state.notificationState)
            }
        }
        item {
            SectionCard(title = "Live intelligence", icon = Icons.Filled.Article) {
                InfoRow("Last transcript line", state.lastTranscriptLine)
                InfoRow("Last detected values", detectedSummary(state.extractedData))
                InfoRow("Selected payload", state.selectedMatch?.payload ?: "Awaiting extraction")
                InfoRow("Last published value", state.lastPublishedValue)
            }
        }
        if (state.errorMessage != null) {
            item { AlertCard(message = state.errorMessage) }
        }
    }
}

@Composable
private fun SessionHero(state: PerformanceUiState) {
    val accent: Color = when (state.sessionState) {
        SessionState.Capturing -> Color(0xFF39D7C8)
        SessionState.Published -> Color(0xFF89F29A)
        SessionState.Error, SessionState.PanicStopped -> Color(0xFFFF6B6B)
        SessionState.Paused, SessionState.ThinkingPause -> Color(0xFFFFB85C)
        else -> Color(0xFF7DA2FF)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE60A1017))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SESSION STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = state.sessionState.label,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
            }
            Text(
                text = if (state.isListeningVisible) "Listening is visibly active. Audio saving is OFF by default." else "No active listening. Start a session intentionally before performance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Review Mode ON")
                StatusChip(if (state.activeProfile.fullAutomationEnabled) "Full Automation ON" else "Full Automation OFF")
                StatusChip("Silence ignored")
                StatusChip("Mock STT ready")
            }
        }
    }
}

@Composable
private fun ReviewScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    val presets: List<String> = listOf(
        "I want to go to Spain and meet Tom Cruise on June 2nd, 2035.",
        "My song is Bohemian Rhapsody by Queen.",
        "I'm thinking of the Queen of Hearts.",
        "My birthday is March 14.",
        "The serial number is A12345678B."
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Mock Transcript Mode", icon = Icons.Filled.Article) {
                Text(
                    "Preview-safe testing without microphone access. Paste a line, play fake partials, extract, match the selected profile, and simulate Inject publishing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.mockTranscriptInput,
                    onValueChange = viewModel::updateMockTranscript,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Manual transcript input") },
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        AssistChip(onClick = { viewModel.loadPresetTranscript(preset) }, label = { Text(preset.take(32) + if (preset.length > 32) "…" else "") })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryControlButton("Fake partials", Icons.Filled.Podcasts, Modifier.weight(1f), viewModel::fakePartialPlayback)
                    SecondaryControlButton("Run extraction", Icons.Filled.Bolt, Modifier.weight(1f), viewModel::runExtractionOnly)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryControlButton("Run profile", Icons.Filled.CallSplit, Modifier.weight(1f), viewModel::runSelectedProfile)
                    PrimaryControlButton("Simulate Inject", Icons.Filled.Publish, Modifier.weight(1f), viewModel::simulateInjectPublish)
                }
            }
        }
        item {
            SectionCard(title = "Detected values", icon = Icons.Filled.Bolt) {
                val items: List<DetectedItem> = state.extractedData?.detectedItems.orEmpty()
                if (items.isEmpty()) {
                    Text("No values detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { item -> DetectedItemRow(item) }
                    }
                }
            }
        }
        item {
            SectionCard(title = "Publish review", icon = Icons.Filled.Publish) {
                InfoRow("Selected channel", state.selectedMatch?.channel?.name ?: "No channel selected")
                InfoRow("Payload", state.selectedMatch?.payload ?: "Nothing ready")
                InfoRow("Generated Inject URL", state.lastInjectUrl.ifBlank { "Generated after publish/test" })
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryControlButton("Publish selected value to Inject", Icons.Filled.Publish, Modifier.fillMaxWidth(), viewModel::publishSelectedValue)
            }
        }
        item {
            EmergencyRevealCard(value = state.selectedMatch?.payload ?: state.lastPublishedValue)
        }
    }
}

@Composable
private fun ProfilesScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "Active Performance Profile", icon = Icons.Filled.Tune) {
                InfoRow("Profile", state.activeProfile.name)
                InfoRow("Start Phrase", state.activeProfile.startPhrase)
                InfoRow("Stop Phrase", state.activeProfile.stopPhrase)
                InfoRow("Speech engine", state.activeProfile.speechProviderId)
                InfoRow("Automation", if (state.activeProfile.fullAutomationEnabled) "Full Automation" else "Review Mode")
                InfoRow("Inject code", state.activeProfile.injectCode)
            }
        }
        items(state.profiles, key = { it.id }) { profile ->
            ProfileCard(profile = profile, isActive = profile.id == state.activeProfile.id, onActivate = { viewModel.activateProfile(profile.id) })
        }
    }
}

@Composable
private fun ChannelsScreen(state: PerformanceUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "Channel matching", icon = Icons.Filled.Route) {
                Text("Channels publish to Inject only. They decide what matters, how it is formatted, and whether automation may send it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.channels, key = { it.id }) { channel -> ChannelCard(channel) }
    }
}

@Composable
private fun InjectScreen(state: PerformanceUiState, viewModel: WhisperCellViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Inject setup", icon = Icons.Filled.Hub) {
                InfoRow("Status", state.injectStatus.label)
                InfoRow("Default Inject Code", state.settings.defaultInjectCode)
                InfoRow("Instruction", "Enter your Inject code only. Do not paste the full URL.")
                InfoRow("Generated URL", state.lastInjectUrl.ifBlank { "https://11z.co/_w/{INJECT_CODE}/selection?value={ENCODED_VALUE}" })
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryControlButton("Test Inject", Icons.Filled.Bolt, Modifier.fillMaxWidth(), viewModel::testInject)
            }
        }
        item {
            SectionCard(title = "Publish behavior", icon = Icons.Filled.Publish) {
                InfoRow("Send method", "GET with value parameter")
                InfoRow("Timeout", "${state.settings.injectTimeoutSeconds} seconds")
                InfoRow("Retry", if (state.settings.injectRetryOnce) "Retry once on failure" else "No retry")
                InfoRow("Last sent value", state.lastPublishedValue)
                InfoRow("Channel codes", "Default code or custom per channel")
            }
        }
        item {
            SectionCard(title = "Standalone utilities", icon = Icons.Filled.ContentCopy) {
                InfoRow("Copy detected value", state.selectedMatch?.payload ?: "No value selected")
                InfoRow("Large performer peek", state.selectedMatch?.payload ?: state.lastPublishedValue)
                InfoRow("Emergency reveal", "Quote, note, search result, weather-style line, focus word, calendar-style line")
            }
        }
    }
}

@Composable
private fun LogsScreen(state: PerformanceUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(title = "Session log", icon = Icons.Filled.Podcasts) {
                Text("Current session only by default. No audio is saved unless explicitly enabled later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.logs, key = { it.id }) { log -> LogRow(log) }
    }
}

@Composable
private fun SettingsScreen(state: PerformanceUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Start and stop phrases", icon = Icons.Filled.Tune) {
                SettingsToggleRow("Start Phrase enabled", state.settings.startPhraseEnabled)
                InfoRow("Start Phrase text", state.settings.startPhrases.joinToString())
                SettingsToggleRow("Stop Phrase enabled", state.settings.stopPhraseEnabled)
                InfoRow("Stop Phrase text", state.settings.stopPhrases.joinToString())
                SettingsToggleRow("Remove phrases from transcript", state.settings.removeStartAndStopPhrases)
                InfoRow("Silence behavior", "Ignore silence as stop trigger")
                InfoRow("Maximum capture", "${state.settings.maximumCaptureSeconds} seconds")
            }
        }
        item {
            SectionCard(title = "Speech providers", icon = Icons.Filled.Mic) {
                state.speechProviders.forEach { provider -> ProviderRow(provider) }
            }
        }
        item {
            SectionCard(title = "OpenAI transcription", icon = Icons.Filled.Bolt) {
                SettingsToggleRow("Enable OpenAI Transcription", state.settings.openAiTranscriptionEnabled)
                InfoRow("Model", state.settings.openAiModel)
                SettingsToggleRow("Realtime transcription toggle", state.settings.openAiRealtimeEnabled)
                SettingsToggleRow("Chunk transcription toggle", state.settings.openAiChunkEnabled)
                InfoRow("Custom model", "Supported by settings architecture")
                InfoRow("Validate Key", "UI boundary ready; do not hardcode secrets")
            }
        }
        item {
            SectionCard(title = "ElevenLabs Speech to Text", icon = Icons.Filled.Article) {
                SettingsToggleRow("Enable ElevenLabs Speech to Text", state.settings.elevenLabsEnabled)
                InfoRow("Model/provider mode", state.settings.elevenLabsModel)
                InfoRow("Validate Key", "UI boundary ready; optional provider")
            }
        }
        item {
            SectionCard(title = "Privacy and automation", icon = Icons.Filled.Settings) {
                SettingsToggleRow("Review Mode", state.settings.reviewModeEnabled)
                SettingsToggleRow("Full Automation", state.settings.fullAutomationEnabled)
                SettingsToggleRow("Audio Save", state.settings.audioSavingEnabled)
                InfoRow("Transcript Save", state.settings.transcriptSavePolicy)
                SettingsToggleRow("Continue listening after publish", state.settings.continueListeningAfterPublish)
            }
        }
    }
}

@Composable
private fun HelpScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Welcome to WhisperCell", icon = Icons.Filled.Help) {
                Text(
                    "WhisperCell is a private performer-side listening hub. It listens for a natural start phrase, captures conversation, extracts useful performance information, and publishes the chosen value to Inject.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard(title = "Basic hands-free setup", icon = Icons.Filled.RadioButtonChecked) {
                val steps: List<String> = listOf(
                    "Choose a Performance Profile.",
                    "Set your start phrase.",
                    "Set your stop phrase.",
                    "Choose which channels are active.",
                    "Confirm your Inject code.",
                    "Start Background Session.",
                    "Perform normally.",
                    "Say your start phrase when you want WhisperCell to begin active capture.",
                    "Let the spectator speak naturally.",
                    "Say your stop phrase when you want WhisperCell to finish and publish."
                )
                steps.forEachIndexed { index, step -> Text("${index + 1}. $step", color = MaterialTheme.colorScheme.onSurface) }
            }
        }
        item {
            SectionCard(title = "Example", icon = Icons.Filled.Bolt) {
                InfoRow("Start phrase", "Picture this clearly for me")
                InfoRow("Stop phrase", "Perfect")
                InfoRow("Spectator says", "I want to go to Spain and meet Tom Cruise on June 2nd, 2035.")
                InfoRow("Extracted", "Spain • Tom Cruise • June 2, 2035")
                InfoRow("Review Mode", "Approve detected values manually.")
                InfoRow("Full Automation Mode", "Publish automatically only after testing your routine.")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD90B121A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF16323A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PrimaryControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SecondaryControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DangerControlButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ListeningBeacon(isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF39D7C8) else Color(0xFF45545A))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isActive) "LISTENING" else "IDLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetectedItemRow(item: DetectedItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1B24),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF203B45))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(item.normalizedValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Source: ${item.sourcePhrase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${(item.confidence * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileCard(profile: PerformanceProfile, isActive: Boolean, onActivate: () -> Unit) {
    SectionCard(title = profile.name, icon = if (isActive) Icons.Filled.RadioButtonChecked else Icons.Filled.Tune) {
        InfoRow("Start", profile.startPhrase)
        InfoRow("Stop", profile.stopPhrase)
        InfoRow("Categories", profile.activeCategories.joinToString { it.label })
        InfoRow("Mode", if (profile.fullAutomationEnabled) "Full Automation" else "Review Mode")
        Button(onClick = onActivate, enabled = !isActive, modifier = Modifier.fillMaxWidth()) {
            Text(if (isActive) "Active Profile" else "Activate Profile")
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel) {
    SectionCard(title = channel.name, icon = Icons.Filled.Route) {
        SettingsToggleRow("Enabled", channel.enabled)
        InfoRow("Input category", channel.inputCategories.joinToString { it.label })
        InfoRow("Extraction priority", channel.priority.joinToString { it.label })
        InfoRow("Payload format", channel.payloadFormat)
        InfoRow("Inject code", if (channel.useDefaultInjectCode) "Default Inject code" else channel.customInjectCode.orEmpty())
        InfoRow("Auto-publish", if (channel.autoPublish) "Enabled" else "Off by default")
        InfoRow("Confidence threshold", "${(channel.confidenceThreshold * 100).toInt()}%")
        InfoRow("Cooldown", "${channel.cooldownSeconds} seconds")
    }
}

@Composable
private fun ProviderRow(provider: SpeechProviderInfo) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F1B24)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(provider.displayName, fontWeight = FontWeight.Bold)
            Text("Mode: ${provider.mode} • Background: ${if (provider.supportsBackground) "Yes" else "No"} • Partials: ${if (provider.supportsPartialResults) "Yes" else "No"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(provider.status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    val color: Color = when (log.level) {
        LogLevel.Success -> Color(0xFF89F29A)
        LogLevel.Warning -> Color(0xFFFFB85C)
        LogLevel.Error -> Color(0xFFFF6B6B)
        LogLevel.Info -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0B121A),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(log.timestamp, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(70.dp))
            Text(log.message, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmergencyRevealCard(value: String) {
    SectionCard(title = "Emergency reveal screen", icon = Icons.Filled.ContentCopy) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF111820),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3B46))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Quote", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (value.isBlank() || value == "Nothing published yet.") "Keep one clear image in mind." else value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AlertCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

private fun detectedSummary(data: ExtractedPerformanceData?): String {
    val items: List<DetectedItem> = data?.detectedItems.orEmpty()
    return if (items.isEmpty()) "Awaiting transcript" else items.take(4).joinToString(" • ") { "${it.category.label}: ${it.normalizedValue}" }
}
