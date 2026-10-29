package dev.lfm.mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.lfm.mobile.agent.AgentCoordinator
import dev.lfm.mobile.data.ChatDatabase
import dev.lfm.mobile.data.ChatMessage
import dev.lfm.mobile.inference.InferenceRepository
import dev.lfm.mobile.inference.ModelStore
import dev.lfm.mobile.tools.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ChatState(val busy: Boolean = false, val loaded: Boolean = false,
    val status: String = "Import the official text GGUF to begin", val streaming: String = "")

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, ChatDatabase::class.java, "lfm-chats.db").build()
    private val dao = db.messages()
    private val models = ModelStore(application)
    private val agent = AgentCoordinator(dao, ToolRegistry(application))
    val messages = dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { if (models.model.isFile) load() }
    private fun status(text: String) { mutable.update { it.copy(status = text) } }
    fun load() {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false, status = "Loading native model") }
            try { InferenceRepository.load(models.model.path); mutable.update { it.copy(loaded = true, status = "Ready · on-device CPU") } }
            catch (e: CancellationException) { status("Load cancelled"); throw e }
            catch (e: Exception) { status(e.message ?: "Model load failed") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun importModel(uri: Uri) {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false, status = "Importing and verifying model") }
            try {
                InferenceRepository.unload()
                models.importModel(uri) { bytes -> status("Importing: ${bytes * 100 / ModelStore.SIZE}% · checking SHA-256") }
                status("SHA-256 verified. Loading native model")
                InferenceRepository.load(models.model.path)
                mutable.update { it.copy(loaded = true, status = "Ready · verified local model") }
            } catch (e: CancellationException) { status("Import cancelled"); throw e }
            catch (e: Exception) { status(e.message ?: "Import failed") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun send(text: String) {
        if (text.isBlank() || state.value.busy || !state.value.loaded) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, streaming = "") }
            try { agent.answer(text.trim(), ::status) { value -> mutable.update { it.copy(streaming = value) } } }
            catch (e: CancellationException) { status("Cancelled"); throw e }
            catch (e: Exception) {
                status(e.message ?: "Inference failed")
                dao.insert(ChatMessage(turnId = "error", role = "error", content = e.message ?: "Inference failed"))
            } finally { mutable.update { it.copy(busy = false, streaming = "") } }
        }
    }
    fun cancel() { job?.cancel(); InferenceRepository.cancel(); status("Stopping") }
    fun clear() { if (!state.value.busy) viewModelScope.launch { dao.clear(); status("History cleared") } }
    fun removeModel() {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false) }
            try { InferenceRepository.unload(); check(!models.model.exists() || models.model.delete()); status("Model removed") }
            catch (e: Exception) { status(e.message ?: "Cannot remove model") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { InferenceRepository.cancel(); super.onCleared() }
}
