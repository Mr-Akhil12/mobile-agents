package dev.lfm.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lfm.mobile.data.ChatMessage
import dev.lfm.mobile.inference.InferenceMetrics
import dev.lfm.mobile.inference.ModelStore
import dev.lfm.mobile.inference.VoicePart
import android.Manifest
import android.content.pm.PackageManager
import dev.lfm.mobile.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var chatVm: ChatViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[ChatViewModel::class.java]
        chatVm = vm
        setContent { LocalAgentsTheme { ChatScreen(vm) } }
    }
    override fun onStart() { super.onStart(); if (::chatVm.isInitialized) chatVm.onForeground(true) }
    override fun onStop() { if (::chatVm.isInitialized) chatVm.onForeground(false); super.onStop() }
}

private data class ChatItem(val rows: List<ChatMessage>) {
    val key get() = rows.first().id
    val tool get() = rows.first().role in setOf("tool", "tool_request")
}
private fun chatItems(messages: List<ChatMessage>): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    for (message in messages) {
        val last = items.lastOrNull()
        if (message.role in setOf("tool", "tool_request") && last?.tool == true && last.rows.first().turnId == message.turnId)
            items[items.lastIndex] = last.copy(rows = last.rows + message)
        else items += ChatItem(listOf(message))
    }
    return items
}

@Composable
private fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val foundChats by vm.searchResults.collectAsStateWithLifecycle()
    val chatQuery by vm.searchQuery.collectAsStateWithLifecycle()
    var optionsChat by remember { mutableStateOf<dev.lfm.mobile.data.Conversation?>(null) }
    var exportAll by rememberSaveable { mutableStateOf(false) }
    var exportChatId by rememberSaveable { mutableStateOf<String?>(null) }
    val conversation by vm.conversationId.collectAsStateWithLifecycle()
    val compact = LocalConfiguration.current.screenHeightDp < 480
    var input by rememberSaveable(conversation) { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    var voiceSettingsError by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showUpdates by remember { mutableStateOf(false) }
    val update by vm.updates.state.collectAsStateWithLifecycle()
    var automaticUpdates by remember { mutableStateOf(vm.updates.autoCheck) }
    var installError by remember { mutableStateOf("") }
    LaunchedEffect(update.available?.code, state.busy, state.speaking) { update.available?.let { if (!state.busy && !state.speaking && vm.updates.shouldPrompt(it)) showUpdates = true } }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var elapsed by remember { mutableLongStateOf(0) }
    LaunchedEffect(state.startedAtMs, state.working) {
        if (state.startedAtMs > 0) {
            while (state.working) { elapsed = SystemClock.elapsedRealtime() - state.startedAtMs; delay(100) }
            elapsed = state.elapsedMs
        } else elapsed = 0
    }
    val context = LocalContext.current
    var voiceImport by rememberSaveable { mutableStateOf<VoicePart?>(null) }
    LaunchedEffect(state.transcriptId) { if (state.transcriptId > 0) input = state.transcript }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportHistory(uri, exportAll, exportChatId ?: conversation) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startVoice() else vm.microphoneDenied() }
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> showModels = true; if (uri != null) voiceImport?.let { vm.importVoice(it, uri) }; voiceImport = null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> showModels = true; uri?.let(vm::importModel) }
    val scroll = rememberLazyListState()
    val items = remember(messages) { chatItems(messages) }
    LaunchedEffect(items.size, state.streaming, conversation) {
        if (items.isNotEmpty() || state.streaming.isNotEmpty()) scroll.animateScrollToItem(items.size)
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(290.dp)) {
            Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
                Text("Local Agents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
                TextButton(enabled = !state.busy, onClick = { vm.newConversation(); scope.launch { drawer.close() } }) { Text("New chat") }
                Text("Your chats", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(value = chatQuery, onValueChange = vm::searchChats, label = { Text("Search chats") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search chats input" })
                LazyColumn(Modifier.weight(1f)) {
                    items(if (chatQuery.isBlank()) conversations else foundChats, key = { it.id }) { chat ->
                        Row {
                        TextButton(enabled = !state.busy, modifier = Modifier.weight(1f), onClick = { vm.selectConversation(chat.id); scope.launch { drawer.close() } }) {
                            Text((if (chat.id == conversation) "• " else "") + chat.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(enabled = !state.busy, onClick = { optionsChat = chat; scope.launch { drawer.close() } }, modifier = Modifier.semantics { contentDescription = "Options for " + chat.title }) { Text("•••") }
                        }
                    }
                    if (chatQuery.isNotBlank() && foundChats.isEmpty()) item { Text("No matching chats", modifier = Modifier.padding(12.dp)) }
                }
                TextButton(onClick = { showModels = true; scope.launch { drawer.close() } }) { Text("Models") }
                TextButton(enabled = !state.busy, onClick = { showMemory = true; scope.launch { drawer.close() } }) { Text("Memory") }
                TextButton(enabled = !state.busy, onClick = { showControls = true; scope.launch { drawer.close() } }) { Text("Controls") }
                Text("Private · On-device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }) {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AgentIconButton(R.drawable.ic_menu, "Chats") { scope.launch { drawer.open() } }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    AgentIconButton(R.drawable.ic_new_chat, "New chat", !state.busy) { vm.newConversation() }
                    Box {
                        AgentIconButton(R.drawable.ic_more, "Menu") { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Models") }, onClick = { menu = false; showModels = true })
                            DropdownMenuItem(text = { Text("Controls") }, enabled = !state.busy, onClick = { menu = false; showControls = true })
                            DropdownMenuItem(text = { Text("Memory") }, enabled = !state.busy, onClick = { menu = false; showMemory = true })
                            DropdownMenuItem(text = { Text("Chat options") }, enabled = !state.busy, onClick = { menu = false; optionsChat = conversations.firstOrNull { it.id == conversation } })
                            DropdownMenuItem(text = { Text("App updates") }, onClick = { menu = false; showUpdates = true })
                            DropdownMenuItem(text = { Text("Clear chat") }, enabled = !state.busy, onClick = { menu = false; confirmClear = true })
                        }
                    }
                }
                if (state.busy && !state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
                    if (messages.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_agent_foreground), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(72.dp))
                            Text("How can I help?", style = MaterialTheme.typography.headlineSmall)
                            Text("Your conversations stay on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                            Text(if (state.loaded) "Text model loaded · ready for your message." else if (state.textAvailable) "Text model available · load it from Models." else "Import your text model from Models, then ask a question.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { input = "What time is it on my phone?" }) { Text("What time is it on my phone?") }
                        }
                    }
                    items(items, key = { it.key }) { item ->
                        if (item.tool) ToolActivity(item) else MessageBody(item.rows.first())
                    }
                    item {
                        if (state.streaming.isNotEmpty()) RichAnswer(state.streaming)
                    }
                }
                if (state.speaking) TextButton(onClick = vm::stopSpeaking, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Stop speaking") }
                if (state.contextNotice.isNotEmpty() && !state.working) Text(state.contextNotice.substringBefore(". Older"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 22.dp))
                if (state.voiceTiming.isNotEmpty() && !state.working) VoiceTimingDetails(state.voiceTiming)
                if (state.managementError.isNotEmpty()) Text(state.managementError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 22.dp))
                if (state.working) Text("Working for " + String.format(java.util.Locale.US, "%.1f s", elapsed / 1000.0) + " · " + state.status,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                else if (state.status != "Finished") Text(state.status, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp), maxLines = 2)
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column {
                        TextField(value = input, onValueChange = { if (it.length <= 8000) input = it },
                            enabled = !state.busy, placeholder = { Text("Message Local Agents") },
                            maxLines = if (compact) 2 else 6, modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showControls = true }, enabled = !state.busy) { Text("Controls") }
                            if (!state.busy) AgentIconButton(R.drawable.ic_mic, "Voice input") {
                                if (!vm.voiceModels.ready) showModels = true
                                else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoice()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            Spacer(Modifier.weight(1f))
                            if (state.recording) {
                                AgentIconButton(R.drawable.ic_stop, "Cancel recording") { vm.cancel() }
                                FilledIconButton(onClick = vm::finishVoiceRecording) { Icon(painterResource(R.drawable.ic_send), contentDescription = "Send voice", modifier = Modifier.size(22.dp)) }
                            }
                            else if (state.busy || state.speaking) AgentIconButton(R.drawable.ic_stop, "Cancel") { vm.cancel() }
                            else FilledIconButton(onClick = { if (vm.send(input)) input = "" }, enabled = state.loaded && input.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                Icon(painterResource(R.drawable.ic_send), contentDescription = "Send", modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    optionsChat?.let { chat -> ChatOptions(vm, chat, onDismiss = { optionsChat = null }, onExport = { all ->
        exportAll = all; exportChatId = chat.id; optionsChat = null
        exportPicker.launch(if (all) "Local_Agents_all_data.json" else "Local_Agents_chat.json")
    }) }
    if (showUpdates) AlertDialog(onDismissRequest = { vm.updates.dismiss(); showUpdates = false }, title = { Text("App updates") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Installed: " + vm.updates.currentVersion)
            Text(update.message)
            if (update.busy) LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = automaticUpdates, onCheckedChange = { automaticUpdates = it; vm.updates.setAutoCheck(it) })
                Text("Check daily when opening the app")
            }
            Text("Checks contact this project's public GitHub releases. Chats, memory and models stay on your phone. Updates require the same signing identity and Android's approval.", style = MaterialTheme.typography.bodySmall)
            update.available?.let { release ->
                Text("Version " + release.version, style = MaterialTheme.typography.titleSmall)
                Text(release.notes)
                if (update.downloaded) TextButton(enabled = !state.busy, onClick = { scope.launch {
                    try { context.startActivity(vm.updates.installerIntent()); installError = "If Android asked for install permission, return here and tap Install update again." }
                    catch (e: Exception) { installError = e.message ?: "Android installer unavailable" }
                } }) { Text("Install update") }
                else TextButton(enabled = !update.busy, onClick = vm.updates::download) { Text("Download update") }
            }
            if (installError.isNotEmpty()) Text(installError)
            if (update.busy) TextButton(onClick = vm.updates::cancel) { Text("Cancel download/check") }
        }
    }, confirmButton = { TextButton(enabled = !update.busy, onClick = vm.updates::check) { Text("Check now") } },
        dismissButton = { TextButton(onClick = { vm.updates.dismiss(); showUpdates = false }) { Text("Close") } })
    if (showControls) AgentControls(vm, onDismiss = { showControls = false })
    if (showMemory) MemoryDialog(vm, onDismiss = { showMemory = false })
    if (showModels) AlertDialog(onDismissRequest = { showModels = false }, title = { Text("On-device models") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.importTarget != null) TextButton(onClick = vm::cancel) { Text("Cancel import") }
            Text("Loaded now", style = MaterialTheme.typography.titleSmall)
            Text(if (state.loaded) "LFM3-1.7B · text and tools" else if (state.voiceBusy) "Omni voice · preparing, listening or transcribing" else "No model loaded")
            Text("Available on this phone", style = MaterialTheme.typography.titleSmall)
            Text("Imported files stay in private app storage. Available means imported and verified; loaded means active in memory. Text and Omni swap automatically.", style = MaterialTheme.typography.bodySmall)
            ModelFileCard("Text · LFM3-1.7B", ModelStore.FILENAME, ModelStore.SIZE, state.textAvailable,
                "text", state, { picker.launch(arrayOf("*/*")) }, "Import text model") {
                if (state.textAvailable) {
                    TextButton(enabled = !state.busy && !state.loaded, onClick = vm::load) { Text("Load text model") }
                    TextButton(enabled = !state.busy, onClick = vm::removeModel) { Text("Unload and delete model") }
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelStore.SOURCE))) }) { Text("Official model download") }
            }
            Text("Omni offline voice · two required files", style = MaterialTheme.typography.titleSmall)
            Text(if (state.omniAvailable && state.projectorAvailable) "Both voice files verified. Tap the microphone, stop when finished, then review the transcript before Send." else "Voice unavailable until both files below are imported. Select each file using its own Import button.")
            VoicePart.entries.forEach { part ->
                val isModel = part == VoicePart.MODEL
                ModelFileCard(if (isModel) "Omni model" else "Omni audio projector", part.filename, part.bytes,
                    if (isModel) state.omniAvailable else state.projectorAvailable, part.name, state,
                    { voiceImport = part; voicePicker.launch(arrayOf("*/*")) }, if (isModel) "Import Omni model" else "Import Omni projector") {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(part.source))) }) { Text(if (isModel) "Official Omni download" else "Official projector download") }
                }
            }
            Text("Import copies the selected file and checks its pinned SHA-256. Existing matching Termux files work; no model is bundled. Failed imports preserve existing imported files.", style = MaterialTheme.typography.bodySmall)
            Text("Spoken replies require an installed offline Android TTS voice. Microphone capture stops when the app leaves the foreground.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                catch (e: android.content.ActivityNotFoundException) { voiceSettingsError = "Open Android Settings and select an installed offline text-to-speech voice." }
            }) { Text("Android voice settings") }
            if (voiceSettingsError.isNotEmpty()) Text(voiceSettingsError)
            Text("Liquid AI LFM Open License v1.0 applies, including its commercial revenue threshold.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showLicense = true }) { Text("Model license (offline)") }
        }
    }, confirmButton = { TextButton(onClick = { showModels = false }) { Text("Done") } })
    if (showLicense) {
        val license = remember { context.assets.open("licenses/LFM-Open-License-1.0.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("LFM Open License v1.0") },
            text = { Text(license, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Delete local chat history?") }, text = { Text("This deletes messages and tool evidence from the current chat. Saved memory is managed separately in Memory.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Delete history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } })
}

@Composable
private fun ModelFileCard(title: String, filename: String, bytes: Long, available: Boolean,
    target: String, state: ChatState, onImport: () -> Unit, importLabel: String, actions: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(filename, style = MaterialTheme.typography.bodySmall)
            Text("$bytes bytes", style = MaterialTheme.typography.bodySmall)
            Text(if (available) "Available · verified import" else "Not imported")
            if (state.importTarget == target) {
                LinearProgressIndicator(progress = { state.importProgress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Importing and checking SHA-256 · ${state.importProgress}%", style = MaterialTheme.typography.bodySmall)
            }
            state.importErrors[target]?.let { error -> SelectionContainer { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            TextButton(enabled = !state.busy, onClick = onImport) { Text(if (available) "Replace · $importLabel" else importLabel) }
            actions()
        }
    }
}

@Composable
private fun AgentIconButton(icon: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled) { Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun ToolActivity(item: ChatItem) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
            Text("Android activity · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (expanded) "Hide evidence ▴" else "Show evidence ▾", style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.rows.forEach { row ->
                    Text(if (row.role == "tool") "Tool evidence · Android" else "Tool request", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(if (row.role == "tool_request") row.contextJson ?: row.content else row.content,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun MessageBody(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(22.dp, 22.dp, 4.dp, 22.dp), modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.84f).dp)) {
                SelectionContainer { Text(message.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = if (message.role == "assistant") "Local Agents · model answer" else "Error" }) {
            RichAnswer(message.content)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.width(12.dp))
                message.metricsJson?.let { json -> runCatching { InferenceMetrics.fromJson(json) }.getOrNull()?.let { PerformanceDetails(it, json) } }
            }

        }
    }
}

@Composable
private fun VoiceTimingDetails(json: String) {
    val timing = remember(json) { runCatching { org.json.JSONObject(json) }.getOrNull() } ?: return
    fun seconds(key: String) = String.format(java.util.Locale.US, "%.1f s", timing.optLong(key) / 1000.0)
    Text("Voice · audio ${seconds("audio_ms")} · transcription ${seconds("asr_ms")} · load ${seconds("load_ms")} · restore ${seconds("restore_ms")}",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun PerformanceDetails(metrics: InferenceMetrics, json: String) {
    var details by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
            Text(String.format(java.util.Locale.US, "Worked for %.1f s · ", metrics.elapsedMs / 1000.0) + if (details) "Hide details" else "Details",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (details) {
            val extra = remember(json) { runCatching { org.json.JSONObject(json) }.getOrNull() }
            extra?.optJSONObject("voice")?.let { VoiceTimingDetails(it.toString()) }
            extra?.optString("context")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            fun speed(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.1f tok/s", it) } ?: "Not measured"
            Text("Ingestion ${speed(metrics.ingestionSpeed)} · Generation ${speed(metrics.generationSpeed)}\n" +
                "${metrics.promptTokens} decoded input · ${metrics.reusedPromptTokens} reused · ${metrics.generatedTokens} generated · ${metrics.calls} model call(s)",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small, safe renderer for model text: emphasis, inline code and fenced code.
 * It never executes HTML, scripts, or remote content. */
@Composable
private fun RichAnswer(text: String) {
    val parts = remember(text) { text.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (part.isNotEmpty()) {
                if (index % 2 == 1) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                    SelectionContainer { Text(part.removePrefix("\n"), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp)) }
                } else SelectionContainer { Text(inlineMarkdown(part), fontSize = 16.sp, lineHeight = 25.sp) }
            }
        }
    }
}
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var start = 0
    for (match in regex.findAll(text)) {
        append(text.substring(start, match.range.first))
        val bold = match.groups[1]?.value
        val begin = length
        append(bold ?: match.groups[2]!!.value)
        addStyle(if (bold != null) SpanStyle(fontWeight = FontWeight.SemiBold) else SpanStyle(fontFamily = FontFamily.Monospace), begin, length)
        start = match.range.last + 1
    }
    append(text.substring(start))
}
