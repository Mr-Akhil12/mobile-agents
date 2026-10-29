package dev.lfm.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lfm.mobile.inference.GenerationSettings

@Composable
fun AgentControls(vm: ChatViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Generation controls") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Applied to the next prompt. Higher limits can increase latency and memory use. Context uses four recent completed turns in this chat plus saved memory; older chats are not automatically searched.")
            Text("Temperature · %.2f".format(settings.temperature))
            Slider(value = settings.temperature, onValueChange = { vm.updateSettings(settings.copy(temperature = it)) }, valueRange = 0f..2f, enabled = !state.busy)
            Text("Total token budget · ${settings.maxTokens}")
            Slider(value = settings.maxTokens.toFloat(), onValueChange = { vm.updateSettings(settings.copy(maxTokens = it.toInt())) }, valueRange = 128f..1024f, steps = 6, enabled = !state.busy)
            Text("Budget includes hidden reasoning and tool requests. Incomplete answers are rejected at the limit.", style = MaterialTheme.typography.labelSmall)
            Row {
                Text("Thinking", modifier = Modifier.weight(1f))
                Switch(checked = settings.thinking && state.supportsThinking, onCheckedChange = { vm.updateSettings(settings.copy(thinking = it)) }, enabled = state.loaded && state.supportsThinking && !state.busy)
            }
            Text(if (state.supportsThinking) "Uses the loaded model's thinking template. Reasoning stays hidden; tool policy is unchanged." else "Thinking control is unavailable until a supporting model template is loaded.", style = MaterialTheme.typography.labelSmall)
            Text("Top-k · ${settings.topK}")
            Slider(value = settings.topK.toFloat(), onValueChange = { vm.updateSettings(settings.copy(topK = it.toInt())) }, valueRange = 1f..100f, enabled = !state.busy)
            Text("Repetition penalty · %.2f".format(settings.repeatPenalty))
            Slider(value = settings.repeatPenalty, onValueChange = { vm.updateSettings(settings.copy(repeatPenalty = it)) }, valueRange = 1f..1.5f, enabled = !state.busy)
            Text("Speeds use native token counts and elapsed decode times, summed across tool/repair calls. They include hidden model output; they are not network or phone-wide benchmarks.", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { vm.updateSettings(GenerationSettings()) }, enabled = !state.busy) { Text("Reset defaults") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun MemoryDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Saved memory") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Only notes you save here are shared across chats. Stored in SQLite on this phone. Clearing a chat does not remove these notes. Up to 20 notes, 1,000 characters each; up to 6,000 characters are included per prompt.")
            memories.forEach { note ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text(note.content)
                    Row {
                        TextButton(onClick = { editing = note.id; draft = note.content }) { Text("Edit") }
                        TextButton(onClick = { vm.deleteMemory(note.id); if (editing == note.id) { editing = null; draft = "" } }) { Text("Delete") }
                    }
                } }
            }
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 1000) draft = it }, label = { Text("Memory note") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            TextButton(enabled = draft.isNotBlank() && (editing != null || memories.size < 20), onClick = { vm.saveMemory(draft, editing); draft = ""; editing = null }) { Text(if (editing == null) "Save memory" else "Update memory") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
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
