package dev.lfm.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lfm.mobile.inference.GenerationSettings

@Composable
fun AgentControls(vm: ChatViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val reviewVoice by vm.reviewVoice.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Generation controls") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Applied to the next prompt. Higher limits can increase latency and memory use. Context fits up to 12 recent complete turns and whole saved notes. Native token limits trim oldest context when needed; full history stays saved. Other chats are not automatically searched.")
            Text("Voice", style = MaterialTheme.typography.titleSmall)
            Row {
                Text("Review transcript first", modifier = Modifier.weight(1f))
                Switch(checked = reviewVoice, onCheckedChange = vm::setReviewVoice, enabled = !state.busy)
            }
            Text("Off: Send stops recording, transcribes and submits once. On: Send prepares an editable transcript; press Send again after reviewing. The 20-second recording limit prepares a draft without submitting automatically.", style = MaterialTheme.typography.bodySmall)
            Text("Temperature · %.2f".format(settings.temperature))
            Slider(value = settings.temperature, onValueChange = { vm.updateSettings(settings.copy(temperature = it)) }, valueRange = 0f..2f, enabled = !state.busy)
            Text("Token budget per model call · ${settings.maxTokens}")
            Slider(value = settings.maxTokens.toFloat(), onValueChange = { vm.updateSettings(settings.copy(maxTokens = it.toInt())) }, valueRange = 128f..1024f, steps = 6, enabled = !state.busy)
            Text("Each call budget includes hidden reasoning and tool requests; a prompt may use multiple calls. Incomplete answers are rejected at the limit.", style = MaterialTheme.typography.labelSmall)
            Row {
                Text("Thinking", modifier = Modifier.weight(1f))
                Switch(checked = settings.thinking && state.supportsThinking, onCheckedChange = { vm.updateSettings(settings.copy(thinking = it)) }, enabled = state.loaded && state.supportsThinking && !state.busy)
            }
            Text(if (state.supportsThinking) "Allows extra internal reasoning. It stays hidden and uses the same token budget; tool permissions stay unchanged." else "Thinking control requires a supporting local model.", style = MaterialTheme.typography.labelSmall)
            Text("Top-k · ${settings.topK}")
            Slider(value = settings.topK.toFloat(), onValueChange = { vm.updateSettings(settings.copy(topK = it.toInt())) }, valueRange = 1f..100f, enabled = !state.busy)
            Text("Repetition penalty · %.2f".format(settings.repeatPenalty))
            Slider(value = settings.repeatPenalty, onValueChange = { vm.updateSettings(settings.copy(repeatPenalty = it)) }, valueRange = 1f..1.5f, enabled = !state.busy)
            Text("Speeds use native token counts and elapsed decode times, summed across tool/repair calls. They include hidden model output; they are not network or phone-wide benchmarks.", style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { vm.updateSettings(GenerationSettings()) }, enabled = !state.busy) { Text("Reset defaults") } })
}

@Composable
fun MemoryDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<dev.lfm.mobile.data.MemoryFact?>(null) }
    val state by vm.state.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Saved memory") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Only notes you save here are shared across chats. Stored only on this phone. Clearing a chat does not remove these notes. Up to 20 notes, 1,000 characters each. Whole notes fitting a 3,000-character budget are included; native context limits may fit fewer. The context notice shows how many were used.")
            OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, label = { Text("Search memory") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search memory input" })
            memories.filter { it.content.contains(query, ignoreCase = true) }.forEach { note ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text(note.content)
                    Text("You saved this · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAt)), style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(enabled = !state.busy, onClick = { editing = note.id; draft = note.content }) { Text("Edit") }
                        TextButton(enabled = !state.busy, onClick = { deleting = note }) { Text("Delete") }
                    }
                } }
            }
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 1000) draft = it }, label = { Text("Memory note") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Memory note input" }, maxLines = 5)
            TextButton(enabled = !state.busy && draft.isNotBlank() && (editing != null || memories.size < 20), onClick = { vm.saveMemory(draft, editing); draft = ""; editing = null }) { Text(if (editing == null) "Save memory" else "Update memory") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete saved memory?") }, text = { Text(note.content) },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { vm.deleteMemory(note.id); if (editing == note.id) { editing = null; draft = "" }; deleting = null }) { Text("Delete memory") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}

@Composable
fun ChatHistoryDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val current by vm.conversationId.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Local chats") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            conversations.forEach { chat ->
                TextButton(onClick = { vm.selectConversation(chat.id); onDismiss() }) { Text((if (chat.id == current) "• " else "") + chat.title) }
            }
        }
    }, confirmButton = { TextButton(onClick = { vm.newConversation(); onDismiss() }) { Text("New chat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
