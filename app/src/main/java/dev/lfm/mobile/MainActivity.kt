package dev.lfm.mobile

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lfm.mobile.inference.ModelStore
import dev.lfm.mobile.ui.ChatViewModel
import dev.lfm.mobile.ui.LocalAgentsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[ChatViewModel::class.java]
        setContent {
            LocalAgentsTheme {
                ChatScreen(vm)
            }
        }
    }
}
@Composable
private fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }
    val scroll = rememberLazyListState()
    LaunchedEffect(messages.size, state.streaming) {
        if (messages.isNotEmpty() || state.streaming.isNotEmpty()) scroll.animateScrollToItem(messages.size)
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_agent_foreground), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(32.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 16.dp))
                TextButton(onClick = { showModels = true }) { Text("Models") }
                TextButton(onClick = { confirmClear = true }, enabled = !state.busy) { Text("Clear chat") }
            }
            Text("Local-first · Text prototype", style = MaterialTheme.typography.labelMedium)
            Text(state.status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (messages.isEmpty()) item {
                    Text("Your conversations stay on this phone. Import LFM3-1.7B Q5_K_M from Models, then ask a question.\n\nTry: What time is it on my phone?\n\nVoice and web research are not available in this milestone. Model answers may be inaccurate.")
                }
                items(messages, key = { it.id }) { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = when (message.role) {
                        "user" -> MaterialTheme.colorScheme.secondaryContainer
                        "tool", "tool_request" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    })) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(when (message.role) { "user" -> "You"; "assistant" -> "Local Agents · model answer"; "tool" -> "Tool evidence · Android"; "error" -> "Error"; else -> "Tool request" }, style = MaterialTheme.typography.labelMedium)
                            Text(message.content)
                        }
                    }
                }
                item { if (state.streaming.isNotEmpty()) Text(state.streaming, modifier = Modifier.padding(14.dp)) }
            }
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 8000) input = it }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                label = { Text("Message Local Agents") }, maxLines = 5)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                if (state.busy) OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
                else Button(onClick = { vm.send(input); input = "" }, enabled = state.loaded && input.isNotBlank()) { Text("Send") }
            }
        }
    }
    if (showModels) AlertDialog(onDismissRequest = { showModels = false }, title = { Text("Local text model") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LFM3-1.7B · Q5_K_M\n1,258,663,200 bytes (1.26 GB)\nReserve at least 1.5 GB app storage in addition to the downloaded file.")
            Text("Use the official file. Import copies it into private storage and verifies its pinned SHA-256. No model is bundled with the APK.")
            Text("Liquid AI LFM Open License v1.0 applies, including its commercial revenue threshold. Review the license before use.")
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelStore.SOURCE))) }) { Text("Official model download") }
            TextButton(onClick = { showLicense = true }) { Text("Model license (offline)") }
            TextButton(enabled = !state.busy, onClick = { showModels = false; vm.removeModel() }) { Text("Unload and delete model") }
        }
    }, confirmButton = { TextButton(enabled = !state.busy, onClick = { showModels = false; picker.launch(arrayOf("*/*")) }) { Text("Import GGUF") } }, dismissButton = {
        TextButton(onClick = { showModels = false }) { Text("Close") }
    })
    if (showLicense) {
        val license = remember { context.assets.open("licenses/LFM-Open-License-1.0.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("LFM Open License v1.0") },
            text = { Text(license, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Delete local chat history?") }, text = { Text("This deletes messages and tool evidence from this app.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Delete history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } })
}
