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
import dev.lfm.mobile.data.Conversation

@Composable
fun ChatOptions(vm: ChatViewModel, chat: Conversation, onDismiss: () -> Unit, onExport: (Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var title by rememberSaveable(chat.id) { mutableStateOf(chat.title) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmExportAll by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Chat options") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Chat title") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Chat title input" }, enabled = !state.busy)
            TextButton(enabled = !state.busy && title.isNotBlank(), onClick = { vm.renameChat(chat.id, title); onDismiss() }) { Text("Save title") }
            Text("Exports are readable JSON documents saved only to the location you choose. They may contain private conversation and tool details. Saved memory is included only in Export all data.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !state.busy, onClick = { onExport(false) }) { Text("Export this chat") }
            TextButton(enabled = !state.busy, onClick = { confirmExportAll = true }) { Text("Export all data") }
            TextButton(enabled = !state.busy, onClick = { confirmDelete = true }) { Text("Delete this chat") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete chat?") },
        text = { Text("Delete “${chat.title}” and its messages/tool activity from this phone? Saved memory and other chats stay available. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { vm.deleteChat(chat.id); confirmDelete = false; onDismiss() }) { Text("Delete chat") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep chat") } })
    if (confirmExportAll) AlertDialog(onDismissRequest = { confirmExportAll = false }, title = { Text("Export all local data?") },
        text = { Text("This readable JSON contains all saved chats, visible tool evidence and saved memory. Choose a private location. It excludes models, audio and credentials.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { confirmExportAll = false; onExport(true) }) { Text("Choose export location") } },
        dismissButton = { TextButton(onClick = { confirmExportAll = false }) { Text("Cancel") } })
}
