package com.joemagicstr8zzz.screenvault.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.joemagicstr8zzz.screenvault.data.AdvancedImageAnalyzer
import com.joemagicstr8zzz.screenvault.data.MediaScanner
import com.joemagicstr8zzz.screenvault.data.SampleData
import com.joemagicstr8zzz.screenvault.data.ScreenVaultRepository
import com.joemagicstr8zzz.screenvault.data.ScreenshotAnalyzer
import com.joemagicstr8zzz.screenvault.model.Priority
import com.joemagicstr8zzz.screenvault.model.ScreenVaultSettings
import com.joemagicstr8zzz.screenvault.model.ScreenshotCategory
import com.joemagicstr8zzz.screenvault.model.ScreenshotItem
import com.joemagicstr8zzz.screenvault.model.ScreenshotStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF2563EB)
private val Bg = Color(0xFFF7F9FC)
private val Surface = Color.White
private val Border = Color(0xFFD8E0EA)
private val Muted = Color(0xFF667085)
private val TextColor = Color(0xFF111827)
private val Green = Color(0xFF16A34A)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFDC2626)

enum class ScreenVaultTab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Outlined.Inbox),
    Action("Action", Icons.Outlined.Alarm),
    Vault("Vault", Icons.Outlined.Archive),
    Reports("Reports", Icons.Outlined.BarChart),
    Settings("Settings", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenVaultApp(repository: ScreenVaultRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(repository.loadItems()) }
    var settings by remember { mutableStateOf(repository.loadSettings()) }
    var tab by remember { mutableStateOf(ScreenVaultTab.Inbox) }
    var reviewItem by remember { mutableStateOf<ScreenshotItem?>(null) }
    var detailItem by remember { mutableStateOf<ScreenshotItem?>(null) }
    var manualOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun saveItems(next: List<ScreenshotItem>) { items = next; repository.saveItems(next) }
    fun saveSettings(next: ScreenVaultSettings) { settings = next; repository.saveSettings(next) }
    fun useOpenAi(): Boolean = settings.cloudAiProcessing && settings.openAiApiKey.isNotBlank()

    fun upsert(item: ScreenshotItem) {
        saveItems(if (items.any { it.id == item.id }) items.map { if (it.id == item.id) item else it } else listOf(item) + items)
    }

    fun patch(id: String, update: ScreenshotItem.() -> ScreenshotItem) {
        val next = items.map { if (it.id == id) it.update() else it }
        saveItems(next)
        detailItem = next.firstOrNull { it.id == id } ?: detailItem
    }

    fun runScan() {
        scope.launch {
            message = if (useOpenAi()) "Scanning screenshots with OCR, barcode detection, and OpenAI vision..." else "Scanning screenshots with on-device OCR and barcode detection..."
            val found = runCatching {
                MediaScanner.scanRecentScreenshots(
                    context = context,
                    knownUris = items.map { it.imageUri }.toSet(),
                    openAiApiKey = settings.openAiApiKey,
                    openAiModel = settings.openAiModel,
                    useOpenAi = useOpenAi()
                )
            }.getOrElse { error ->
                message = "Scan failed: ${error.message ?: "unknown error"}"
                emptyList()
            }
            if (found.isNotEmpty()) saveItems(found + items)
            if (found.isNotEmpty()) message = "Analyzed ${found.size} screenshot${if (found.size == 1) "" else "s"}."
            if (found.isEmpty() && message?.startsWith("Scan failed") != true) message = "No new screenshots found. Manual import and paste still work."
            saveSettings(settings.copy(lastScanAt = System.currentTimeMillis()))
        }
    }

    val imagePermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runScan() else message = "Photo/media permission was denied. You can still import one image or paste text."
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                message = if (useOpenAi()) "Analyzing image with OCR, barcode detection, and OpenAI vision..." else "Analyzing image with on-device OCR and barcode detection..."
                reviewItem = runCatching {
                    AdvancedImageAnalyzer.analyzeImage(
                        context = context,
                        imageUri = it.toString(),
                        sourceType = "photo",
                        openAiApiKey = settings.openAiApiKey,
                        openAiModel = settings.openAiModel,
                        useOpenAi = useOpenAi()
                    )
                }.getOrElse { error ->
                    message = "Image analysis failed: ${error.message ?: "unknown error"}"
                    ScreenshotAnalyzer.analyze(imageUri = it.toString(), sourceType = "photo", visibleText = "")
                }
            }
        }
    }

    if (!settings.onboardingComplete) {
        OnboardingScreen { saveSettings(settings.copy(onboardingComplete = true)) }
        return
    }

    reviewItem?.let { item ->
        ReviewScreen(
            item = item,
            onBack = { reviewItem = null },
            onSave = { saved ->
                upsert(saved)
                reviewItem = null
                tab = if (saved.status == ScreenshotStatus.Action) ScreenVaultTab.Action else ScreenVaultTab.Vault
            }
        )
        return
    }

    detailItem?.let { item ->
        DetailScreen(
            item = items.firstOrNull { it.id == item.id } ?: item,
            showRawExtractedText = settings.showRawExtractedText,
            onBack = { detailItem = null },
            onEdit = { reviewItem = it },
            onPatch = { changed -> upsert(changed) },
            onDelete = { id -> saveItems(items.filterNot { it.id == id }); detailItem = null }
        )
        return
    }

    if (manualOpen) {
        ManualPasteDialog(
            onDismiss = { manualOpen = false },
            onAnalyze = { text ->
                manualOpen = false
                reviewItem = ScreenshotAnalyzer.analyze(
                    imageUri = "manual://${System.currentTimeMillis()}",
                    sourceType = "manual",
                    visibleText = text
                )
            }
        )
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                ScreenVaultTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                ScreenVaultTab.Inbox -> InboxScreen(
                    items = items.filter { it.status == ScreenshotStatus.Inbox },
                    message = message,
                    openAiEnabled = useOpenAi(),
                    onScan = {
                        if (ContextCompat.checkSelfPermission(context, imagePermission) == PackageManager.PERMISSION_GRANTED) runScan()
                        else permissionLauncher.launch(imagePermission)
                    },
                    onImport = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPaste = { manualOpen = true },
                    onSamples = { saveItems(SampleData.create() + items.filterNot { it.id.startsWith("sample_") }) },
                    onReview = { reviewItem = it },
                    onSave = { upsert(it.copy(status = if (it.status == ScreenshotStatus.Action) ScreenshotStatus.Action else ScreenshotStatus.Saved)) },
                    onIgnore = { id -> patch(id) { copy(status = ScreenshotStatus.Ignored, updatedAt = System.currentTimeMillis()) } }
                )
                ScreenVaultTab.Action -> ActionScreen(
                    items = items.filter { it.status == ScreenshotStatus.Action || it.status == ScreenshotStatus.Snoozed },
                    onOpen = { detailItem = it },
                    onDone = { id -> patch(id) { copy(status = ScreenshotStatus.Done, updatedAt = System.currentTimeMillis()) } },
                    onSnooze = { id -> patch(id) { copy(status = ScreenshotStatus.Snoozed, reminderAt = System.currentTimeMillis() + 86400000L, updatedAt = System.currentTimeMillis()) } }
                )
                ScreenVaultTab.Vault -> VaultScreen(items = items.filterNot { it.status == ScreenshotStatus.Inbox || it.status == ScreenshotStatus.Ignored }, onOpen = { detailItem = it })
                ScreenVaultTab.Reports -> ReportsScreen(items = items)
                ScreenVaultTab.Settings -> SettingsScreen(
                    settings = settings,
                    itemCount = items.size,
                    onSettings = ::saveSettings,
                    onSamples = { saveItems(SampleData.create() + items.filterNot { it.id.startsWith("sample_") }) },
                    onClear = { repository.clear(); items = emptyList(); settings = ScreenVaultSettings() }
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Bg).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = Blue, modifier = Modifier.size(86.dp))
        Spacer(Modifier.height(24.dp))
        Text("ScreenVault", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = TextColor)
        Text("Smart Screenshot Organizer", color = Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Text("Your screenshots are a memory bank.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = TextColor)
        Spacer(Modifier.height(12.dp))
        Text("ScreenVault uses advanced on-device OCR, barcode detection, and optional AI vision to organize screenshots you choose to scan.", color = Muted)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Start Organizing") }
    }
}

@Composable
private fun Page(title: String, subtitle: String, content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("ScreenVault", color = Blue, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("Smart Screenshot Organizer", color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(16.dp))
            Text(title, color = TextColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item { content() }
        item { Spacer(Modifier.height(84.dp)) }
    }
}

@Composable
private fun InboxScreen(
    items: List<ScreenshotItem>,
    message: String?,
    openAiEnabled: Boolean,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onPaste: () -> Unit,
    onSamples: () -> Unit,
    onReview: (ScreenshotItem) -> Unit,
    onSave: (ScreenshotItem) -> Unit,
    onIgnore: (String) -> Unit
) {
    Page("Screenshot Inbox", "New screenshots and images waiting to be understood.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox(if (openAiEnabled) "Advanced mode: multilingual OCR + QR/barcode detection + OpenAI vision." else "Local mode: bundled multilingual OCR + QR/barcode detection. Enable OpenAI Vision in Settings for cloud AI sorting.")
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("Scan New Screenshots") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import Image") }
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) { Text("Paste Text") }
            }
            OutlinedButton(onClick = onSamples, modifier = Modifier.fillMaxWidth()) { Text("Use Sample Data") }
            message?.let { InfoBox(it) }
            SectionTitle("Pending (${items.size})")
            if (items.isEmpty()) EmptyBox("No new screenshots", "Import images or scan your screenshot folder to start organizing.")
            items.forEach { item -> ItemCard(item, "Review", { onReview(item) }, "Save", { onSave(item) }, "Ignore", { onIgnore(item.id) }) }
        }
    }
}

@Composable
private fun ActionScreen(items: List<ScreenshotItem>, onOpen: (ScreenshotItem) -> Unit, onDone: (String) -> Unit, onSnooze: (String) -> Unit) {
    Page("Action Queue", "Screenshots with deadlines, reminders, returns, or follow-ups.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatRow(listOf("Open" to items.size.toString(), "High" to items.count { it.priority == Priority.High || it.priority == Priority.Urgent }.toString()))
            if (items.isEmpty()) EmptyBox("Nothing needs action", "Deadlines, returns, appointments, and follow-ups show here.")
            items.forEach { item -> ItemCard(item, "Open", { onOpen(item) }, "Snooze", { onSnooze(item.id) }, "Done", { onDone(item.id) }) }
        }
    }
}

@Composable
private fun VaultScreen(items: List<ScreenshotItem>, onOpen: (ScreenshotItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ScreenshotCategory?>(null) }
    val filtered = items.filter { item ->
        val haystack = listOf(item.title, item.summary, item.extractedText, item.sourceSite.orEmpty(), item.tags.joinToString(" ")).joinToString(" ").lowercase()
        (category == null || item.category == category) && (query.isBlank() || haystack.contains(query.lowercase()))
    }
    Page("Vault", "Everything you saved, organized and searchable.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Search screenshots, receipts, orders...") }, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
                items(ScreenshotCategory.entries) { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) }) }
            }
            SectionTitle("${filtered.size} items")
            if (filtered.isEmpty()) EmptyBox("No matching records", "Try another search or clear your filters.")
            filtered.forEach { ItemCard(it, "Open", { onOpen(it) }) }
        }
    }
}

@Composable
private fun ReportsScreen(items: List<ScreenshotItem>) {
    val context = LocalContext.current
    val receipts = items.filter { it.isReceipt }
    val tax = items.filter { it.isTaxRecord || it.taxCategory != null }
    val orders = items.filter { it.category in listOf(ScreenshotCategory.Order, ScreenshotCategory.Return, ScreenshotCategory.Tracking) }
    val links = items.filter { it.detectedLinks.isNotEmpty() || it.detectedCodes.isNotEmpty() }
    Page("Reports", "Receipts, records, tax items, and saved proof.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatRow(listOf("Receipts" to receipts.size.toString(), "Tax" to tax.size.toString(), "Orders" to orders.size.toString(), "Links" to links.size.toString()))
            val total = receipts.sumOf { it.detectedAmounts.firstOrNull()?.amount ?: 0.0 }
            AppCard { Text("Detected receipt total", fontWeight = FontWeight.Bold); Text("¥${total.toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("Review before using for taxes or reimbursement.", color = Muted) }
            OutlinedButton(onClick = { shareText(context, "ScreenVault Receipts CSV", buildCsv(receipts)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Receipt, null); Spacer(Modifier.width(8.dp)); Text("Export Receipts CSV") }
            OutlinedButton(onClick = { shareText(context, "ScreenVault Tax JSON", Json.encodeToString(tax)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export Tax JSON") }
            OutlinedButton(onClick = { shareText(context, "ScreenVault Full JSON", Json.encodeToString(items)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Export Full Vault JSON") }
        }
    }
}

@Composable
private fun SettingsScreen(settings: ScreenVaultSettings, itemCount: Int, onSettings: (ScreenVaultSettings) -> Unit, onSamples: () -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }, title = { Text("Clear local data?") }, text = { Text("This removes ScreenVault records only. It does not delete original photos.") })
    Page("Settings", "Control your data, privacy, and scanning.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleRow("Local-only processing", "Keep analysis on this device by default. Turn this off if you use cloud AI.", settings.localOnlyProcessing) { onSettings(settings.copy(localOnlyProcessing = it, cloudAiProcessing = if (it) false else settings.cloudAiProcessing)) }
            ToggleRow("OpenAI Vision enrichment", "Optional cloud AI for smarter image understanding. Requires your API key.", settings.cloudAiProcessing) { onSettings(settings.copy(cloudAiProcessing = it, localOnlyProcessing = if (it) false else settings.localOnlyProcessing)) }
            EditableField("OpenAI API key", settings.openAiApiKey, minLines = 1) { onSettings(settings.copy(openAiApiKey = it.trim())) }
            EditableField("OpenAI model", settings.openAiModel, minLines = 1) { onSettings(settings.copy(openAiModel = it.ifBlank { "gpt-5.5" })) }
            ToggleRow("Hide sensitive screenshots", "Blur sensitive items in lists later.", settings.hideSensitiveScreenshots) { onSettings(settings.copy(hideSensitiveScreenshots = it)) }
            ToggleRow("Show raw extracted text", "Off by default. Turn on only when you want to inspect messy OCR output.", settings.showRawExtractedText) { onSettings(settings.copy(showRawExtractedText = it)) }
            ToggleRow("Scan on app open", "Future native background feature. Manual scan works now.", settings.scanOnOpen) { onSettings(settings.copy(scanOnOpen = it)) }
            InfoBox("$itemCount local records. Last scan: ${settings.lastScanAt?.let { shortDate(it) } ?: "Never"}.")
            OutlinedButton(onClick = onSamples, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Add Sample Records") }
            OutlinedButton(onClick = { onSettings(settings.copy(onboardingComplete = false)) }, modifier = Modifier.fillMaxWidth()) { Text("Reset Onboarding") }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Clear Local ScreenVault Data") }
        }
    }
}

@Composable
private fun ReviewScreen(item: ScreenshotItem, onBack: () -> Unit, onSave: (ScreenshotItem) -> Unit) {
    var draft by remember { mutableStateOf(item) }
    var text by remember { mutableStateOf(item.extractedText) }
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
            Text("Review Screenshot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            if (text.isBlank()) InfoBox("No useful text was extracted. You can add context here, or enable OpenAI Vision in Settings and re-import the image.")
            if (draft.isSensitive) InfoBox("This may contain sensitive information. Review before saving or exporting.")
            EditableField("Title", draft.title) { draft = draft.copy(title = it) }
            EditableField("Summary", draft.summary) { draft = draft.copy(summary = it) }
            SectionTitle("Category")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ScreenshotCategory.entries) { cat -> FilterChip(selected = draft.category == cat, onClick = { draft = draft.copy(category = cat) }, label = { Text(cat.label) }) } }
            SectionTitle("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Priority.entries.forEach { p -> FilterChip(selected = draft.priority == p, onClick = { draft = draft.copy(priority = p) }, label = { Text(p.label) }) } }
            EditableField("Analysis text / context", text, minLines = 6) { text = it }
            OutlinedButton(onClick = { draft = ScreenshotAnalyzer.reanalyze(draft, text) }, modifier = Modifier.fillMaxWidth()) { Text("Analyze Text Again") }
            EditableField("Suggested action", draft.suggestedAction.orEmpty()) { draft = draft.copy(suggestedAction = it) }
            EditableField("Due date / reminder text", draft.dueDateText.orEmpty()) { draft = draft.copy(dueDateText = it.ifBlank { null }) }
            ToggleRow("Receipt", "Include in receipt reports.", draft.isReceipt) { draft = draft.copy(isReceipt = it) }
            ToggleRow("Tax / record", "Flag for report review.", draft.isTaxRecord) { draft = draft.copy(isTaxRecord = it) }
            Button(onClick = { onSave(draft.copy(extractedText = ScreenshotAnalyzer.cleanOcrText(text), status = if (draft.status == ScreenshotStatus.Action) ScreenshotStatus.Action else ScreenshotStatus.Saved, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Text("Save to Vault") }
            OutlinedButton(onClick = { onSave(draft.copy(extractedText = ScreenshotAnalyzer.cleanOcrText(text), status = ScreenshotStatus.Action, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Text("Add to Action Queue") }
            OutlinedButton(onClick = { onSave(draft.copy(extractedText = ScreenshotAnalyzer.cleanOcrText(text), status = ScreenshotStatus.Ignored, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Text("Ignore") }
        }
    }
}

@Composable
private fun DetailScreen(item: ScreenshotItem, showRawExtractedText: Boolean, onBack: () -> Unit, onEdit: (ScreenshotItem) -> Unit, onPatch: (ScreenshotItem) -> Unit, onDelete: (String) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(item.id) }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }, title = { Text("Delete record?") }, text = { Text("This deletes only the ScreenVault record. It does not delete the original image.") })
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }; Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); IconButton(onClick = { onEdit(item) }) { Icon(Icons.Outlined.Edit, null) } }
            ItemImage(item)
            AppCard {
                BadgeRow(item)
                DataRow("Summary", item.summary)
                if (!item.suggestedAction.isNullOrBlank()) DataRow("Recommended action", item.suggestedAction)
                DataRow("Due / reminder", item.dueDateText ?: item.reminderAt?.let { shortDate(it) } ?: "None")
                DataRow("Source", item.sourceSite ?: item.sourceApp ?: item.sourceType)
                DataRow("Confidence", item.confidence.label)
            }
            ImportantDetailsCard(item)
            if (showRawExtractedText) {
                AppCard {
                    Text("Raw extracted text", fontWeight = FontWeight.Bold, color = TextColor)
                    Text(item.extractedText.ifBlank { "No text stored." }, color = Muted)
                }
            }
            if (item.userNotes.isNotBlank()) AppCard { DataRow("Analysis notes", item.userNotes) }
            Button(onClick = { onPatch(item.copy(status = ScreenshotStatus.Done, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Mark Complete") }
            OutlinedButton(onClick = { onPatch(item.copy(status = ScreenshotStatus.Snoozed, reminderAt = System.currentTimeMillis() + 86400000L, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Text("Snooze Tomorrow") }
            OutlinedButton(onClick = { onPatch(item.copy(status = ScreenshotStatus.Archived, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth()) { Text("Archive") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete from ScreenVault") }
        }
    }
}

@Composable
private fun ImportantDetailsCard(item: ScreenshotItem) {
    val amountText = item.detectedAmounts.joinToString { it.rawText }
    val dateText = item.detectedDates.joinToString { if (it.dateType != "unknown") "${it.rawText} (${it.dateType.replace('_', ' ')})" else it.rawText }
    val linkText = item.detectedLinks.joinToString("\n") { it.url }
    val codeText = item.detectedCodes.joinToString("\n") { "${it.type}: ${it.value}" }
    val order = item.detectedOrderInfo
    AppCard {
        Text("What matters", fontWeight = FontWeight.Black, color = TextColor)
        if (amountText.isNotBlank()) DataRow("Money", amountText)
        if (dateText.isNotBlank()) DataRow("Dates", dateText)
        if (!order?.vendor.isNullOrBlank()) DataRow("Vendor", order?.vendor.orEmpty())
        if (!order?.orderNumber.isNullOrBlank()) DataRow("Order number", order?.orderNumber.orEmpty())
        if (!order?.trackingNumber.isNullOrBlank()) DataRow("Tracking", order?.trackingNumber.orEmpty())
        if (linkText.isNotBlank()) DataRow("Links", linkText)
        if (codeText.isNotBlank()) DataRow("Codes", codeText)
        if (item.tags.isNotEmpty()) DataRow("Tags", item.tags.joinToString(", "))
        if (amountText.isBlank() && dateText.isBlank() && linkText.isBlank() && codeText.isBlank() && order?.vendor.isNullOrBlank()) Text("No strong details extracted yet. Add context from Review if this screenshot matters.", color = Muted)
    }
}

@Composable
private fun ManualPasteDialog(onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Screenshot Text") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 6, label = { Text("Visible text or context") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onAnalyze(text) }) { Text("Analyze") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ItemCard(item: ScreenshotItem, a: String, onA: () -> Unit, b: String? = null, onB: (() -> Unit)? = null, c: String? = null, onC: (() -> Unit)? = null) {
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemIcon(item.category)
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Black, color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BadgeRow(item)
                Text(item.summary, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.suggestedAction?.let { Text("Next: $it", color = Blue, fontWeight = FontWeight.SemiBold, maxLines = 2) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onA, modifier = Modifier.weight(1f)) { Text(a) }
            if (b != null && onB != null) OutlinedButton(onClick = onB, modifier = Modifier.weight(1f)) { Text(b) }
            if (c != null && onC != null) OutlinedButton(onClick = onC, modifier = Modifier.weight(1f)) { Text(c) }
        }
    }
}

@Composable
private fun AppCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
private fun ItemImage(item: ScreenshotItem) {
    if (item.imageUri.startsWith("content://") || item.imageUri.startsWith("file://")) {
        AsyncImage(model = item.imageUri, contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEFF4FB)))
    } else {
        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEFF4FB)), contentAlignment = Alignment.Center) { ItemIcon(item.category, 54.dp) }
    }
}

@Composable
private fun ItemIcon(category: ScreenshotCategory, size: androidx.compose.ui.unit.Dp = 58.dp) {
    val icon = when (category) { ScreenshotCategory.Receipt -> Icons.Outlined.Receipt; ScreenshotCategory.QrBarcode -> Icons.Outlined.QrCode; ScreenshotCategory.DocumentForm -> Icons.Outlined.Description; else -> Icons.Outlined.Image }
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color(0xFFDBEAFE)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = category.label, tint = Blue) }
}

@Composable
private fun BadgeRow(item: ScreenshotItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Badge(item.category.label, Blue, Color(0xFFDBEAFE))
        val priColor = when (item.priority) { Priority.Low -> Green; Priority.Medium -> Amber; else -> Red }
        Badge(item.priority.label, priColor, priColor.copy(alpha = 0.12f))
        if (item.isReceipt) Badge("Receipt", Green, Color(0xFFDCFCE7))
        if (item.isTaxRecord) Badge("Tax", Amber, Color(0xFFFEF3C7))
    }
}

@Composable
private fun Badge(text: String, fg: Color, bg: Color) { Text(text, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)) }
@Composable
private fun InfoBox(text: String) { AppCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Info, null, tint = Blue); Text(text, color = Blue, fontWeight = FontWeight.SemiBold) } } }
@Composable
private fun EmptyBox(title: String, text: String) { AppCard { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Inbox, null, tint = Blue, modifier = Modifier.size(44.dp)); Text(title, fontWeight = FontWeight.Bold, color = TextColor); Text(text, color = Muted) } } }
@Composable
private fun SectionTitle(text: String) { Text(text, fontWeight = FontWeight.Black, color = TextColor, style = MaterialTheme.typography.titleMedium) }
@Composable
private fun EditableField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) { OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, minLines = minLines, modifier = Modifier.fillMaxWidth()) }
@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) { AppCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted) }; Switch(checked = value, onCheckedChange = onChange) } } }
@Composable
private fun DataRow(label: String, value: String) { Column { Divider(color = Border); Text(label, color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall); Text(value, color = TextColor) } }
@Composable
private fun StatRow(values: List<Pair<String, String>>) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { values.forEach { (label, value) -> Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall); Text(label, color = Muted) } } } } }

private fun buildCsv(items: List<ScreenshotItem>): String {
    val rows = items.map { item -> listOf(item.title, item.category.label, item.sourceSite.orEmpty(), item.detectedAmounts.joinToString("; ") { it.rawText }, item.dueDateText.orEmpty(), item.isTaxRecord.toString()).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
    return "title,category,vendor,amounts,dueDate,isTaxRecord\n" + rows.joinToString("\n")
}

private fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, title))
}

private fun shortDate(time: Long): String = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(time))
