package dev.lfm.mobile.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import dev.lfm.mobile.data.*
import dev.lfm.mobile.inference.GenerationSettings
import dev.lfm.mobile.inference.InferenceMetrics
import dev.lfm.mobile.inference.VoiceModelStore
import dev.lfm.mobile.inference.VoicePart
import dev.lfm.mobile.voice.MicrophoneCapture
import dev.lfm.mobile.voice.OfflineSpeaker
import org.json.JSONObject
import dev.lfm.mobile.updates.UpdateManager
import java.util.UUID
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
    val status: String = "Import the official text GGUF to begin", val streaming: String = "",
    val startedAtMs: Long = 0, val elapsedMs: Long = 0, val metrics: InferenceMetrics? = null,
    val supportsThinking: Boolean = false, val working: Boolean = false,
    val recording: Boolean = false, val voiceBusy: Boolean = false, val speaking: Boolean = false,
    val transcript: String = "", val transcriptId: Long = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, ChatDatabase::class.java, "lfm-chats.db").addMigrations(ChatDatabase.MIGRATION_1_2).build()
    private val dao = db.messages()
    private val models = ModelStore(application)
    private val agent = AgentCoordinator(dao, ToolRegistry(application))
    private val chatPrefs = application.getSharedPreferences("chat-ui", 0)
    private val current = MutableStateFlow(chatPrefs.getString("conversation", DEFAULT_CONVERSATION) ?: DEFAULT_CONVERSATION)
    val conversationId = current.asStateFlow()
    val messages = current.flatMapLatest { dao.observeConversation(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val conversations = dao.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val memories = dao.observeMemories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val prefs = application.getSharedPreferences("generation-settings", 0)
    private val selectedSettings = MutableStateFlow(GenerationSettings(
        prefs.getFloat("temperature", 0.1f), prefs.getInt("maxTokens", 512), prefs.getInt("topK", 50),
        prefs.getFloat("repeatPenalty", 1.1f), prefs.getBoolean("thinking", false)))
    val settings = selectedSettings.asStateFlow()
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    val voiceModels = VoiceModelStore(application)
    private val microphone = MicrophoneCapture(application)
    private var disposed = false
    private var foreground = true
    private var speechJob: Job? = null
    private var voiceReplyPending = false
    private var speakerInstance: OfflineSpeaker? = null
    private fun speaker(): OfflineSpeaker = speakerInstance ?: OfflineSpeaker(getApplication()) { value -> mutable.update { it.copy(speaking = value) } }.also { speakerInstance = it }
    val updates = UpdateManager(application, viewModelScope)
    private var job: Job? = null
    init {
        viewModelScope.launch { dao.createConversation(Conversation(DEFAULT_CONVERSATION, "Earlier conversation")) }
        if (models.model.isFile) load()
    }
    fun updateSettings(value: GenerationSettings) {
        if (state.value.busy) return
        val safe = value.copy(temperature = value.temperature.coerceIn(0f, 2f), maxTokens = value.maxTokens.coerceIn(64, 1536),
            topK = value.topK.coerceIn(1, 100), repeatPenalty = value.repeatPenalty.coerceIn(1f, 1.5f),
            thinking = value.thinking && state.value.supportsThinking)
        selectedSettings.value = safe
        prefs.edit().putFloat("temperature", safe.temperature).putInt("maxTokens", safe.maxTokens)
            .putInt("topK", safe.topK).putFloat("repeatPenalty", safe.repeatPenalty).putBoolean("thinking", safe.thinking).apply()
    }
    fun newConversation() {
        if (state.value.busy) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            voiceReplyPending = false
            dao.createConversation(Conversation(id, "New chat")); current.value = id
            chatPrefs.edit().putString("conversation", id).apply()
            mutable.update { it.copy(streaming = "", metrics = null, elapsedMs = 0, startedAtMs = 0, transcript = "", status = if (it.loaded) "Ready" else it.status) }
        }
    }
    fun selectConversation(id: String) {
        if (state.value.busy) return
        voiceReplyPending = false
        current.value = id
        chatPrefs.edit().putString("conversation", id).apply()
        mutable.update { it.copy(streaming = "", metrics = null, elapsedMs = 0, startedAtMs = 0, transcript = "") }
    }
    fun saveMemory(text: String, id: Long? = null) {
        if (state.value.busy || text.isBlank() || text.length > 1000) return
        viewModelScope.launch {
            if (id != null) dao.editMemory(id, text.trim())
            else if (dao.allMemories().size < 20) dao.saveMemory(MemoryFact(content = text.trim()))
        }
    }
    fun deleteMemory(id: Long) { if (!state.value.busy) viewModelScope.launch { dao.deleteMemory(id) } }
    private fun status(text: String) { mutable.update { it.copy(status = text) } }
    fun load() {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false, status = "Loading native model") }
            try { InferenceRepository.load(models.model.path); val thinking = InferenceRepository.supportsThinking(); mutable.update { it.copy(loaded = true, supportsThinking = thinking, status = "Ready · on-device CPU") } }
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
                val thinking = InferenceRepository.supportsThinking()
                mutable.update { it.copy(loaded = true, supportsThinking = thinking, status = "Ready · verified local model") }
            } catch (e: CancellationException) { status("Import cancelled"); throw e }
            catch (e: Exception) { status(e.message ?: "Import failed") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun send(text: String) {
        if (text.isBlank() || state.value.busy || !state.value.loaded) return
        speechJob?.cancel(); speakerInstance?.stop()
        val readAloud = voiceReplyPending; voiceReplyPending = false
        job = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val conversation = current.value
            val snapshot = settings.value.copy(thinking = settings.value.thinking && state.value.supportsThinking)
            mutable.update { it.copy(busy = true, working = true, streaming = "", status = "Preparing prompt", startedAtMs = started, elapsedMs = 0, metrics = null) }
            try {
                if (dao.inConversation(conversation).isEmpty()) dao.renameConversation(conversation, text.trim().take(60))
                agent.answer(text.trim(), ::status, { value -> mutable.update { it.copy(streaming = value) } },
                    settings = snapshot, conversationId = conversation, memory = dao.allMemories().map { it.content },
                    metrics = { measured -> mutable.update { it.copy(metrics = measured) } })
                if (readAloud && foreground) dao.inConversation(conversation).lastOrNull { it.role == "assistant" }?.let { reply -> speechJob = viewModelScope.launch { status(speaker().speak(reply.content)) } }
            }
            catch (e: CancellationException) { status("Cancelled"); throw e }
            catch (e: Exception) {
                status(e.message ?: "Inference failed")
                dao.insert(ChatMessage(turnId = "error", role = "error", content = e.message ?: "Inference failed", conversationId = conversation))
            } finally { mutable.update { it.copy(busy = false, working = false, streaming = "", elapsedMs = SystemClock.elapsedRealtime() - started) } }
        }
    }
    fun cancel() { job?.cancel(); speechJob?.cancel(); microphone.stop(); speakerInstance?.stop(); InferenceRepository.cancel(); status("Stopping") }
    fun microphoneDenied() { status("Microphone permission denied. Typed chat still works.") }
    fun finishVoiceRecording() { microphone.stop() }
    fun onForeground(value: Boolean) { foreground = value; if (value) updates.checkIfDue(); if (!value) { speechJob?.cancel(); speakerInstance?.stop(); if (state.value.voiceBusy) cancel() } }
    fun importVoice(part: VoicePart, uri: Uri) {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, status = "Verifying ${part.filename}") }
            try {
                InferenceRepository.unloadVoice()
                voiceModels.import(part, uri) { bytes -> status("Importing voice: ${bytes * 100 / part.bytes}% · checking SHA-256") }
                status(if (voiceModels.ready) "Omni pair verified · ready for voice" else "Voice file verified; import the other paired file")
            } catch (e: CancellationException) { status("Voice import cancelled"); throw e }
            catch (e: Exception) { status(e.message ?: "Voice import failed") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun startVoice() {
        if (state.value.busy) return
        if (!voiceModels.ready) { status("Import both pinned Omni files from Models first"); return }
        if (getApplication<Application>().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { microphoneDenied(); return }
        speakerInstance?.stop()
        job = viewModelScope.launch {
            var finalStatus = "Voice cancelled"
            mutable.update { it.copy(busy = true, loaded = false, voiceBusy = true, recording = false, status = "Preparing local Omni voice model") }
            try {
                val rate = InferenceRepository.loadVoice(voiceModels.file(VoicePart.MODEL).path, voiceModels.file(VoicePart.PROJECTOR).path)
                mutable.update { it.copy(recording = true, status = "Listening · tap Stop recording when finished") }
                val audio = microphone.capture(rate) { seconds -> status(String.format(java.util.Locale.US, "Listening %.1f s · tap Stop recording", seconds)) }
                mutable.update { it.copy(recording = false, status = "Transcribing locally with Omni") }
                val result = try { JSONObject(InferenceRepository.transcribe(audio, rate)) } finally { audio.fill(0f) }
                currentCoroutineContext().ensureActive()
                val transcript = result.getString("transcript").trim()
                check(transcript.isNotBlank()) { "No transcript returned" }
                voiceReplyPending = true
                mutable.update { it.copy(transcript = transcript, transcriptId = it.transcriptId + 1) }
                finalStatus = "Transcript ready · review and send. Spoken reply requires an offline Android voice."
            } catch (e: CancellationException) { finalStatus = "Voice cancelled"; throw e }
            catch (e: Exception) { finalStatus = e.message ?: "Voice recognition failed" }
            finally {
                microphone.stop()
                withContext(NonCancellable) {
                    InferenceRepository.unloadVoice()
                    if (!disposed && models.model.isFile) {
                        status("Restoring text model")
                        try { InferenceRepository.load(models.model.path); val supports = InferenceRepository.supportsThinking(); mutable.update { it.copy(loaded = true, supportsThinking = supports) } }
                        catch (e: Exception) { finalStatus += " · Text model reload failed: ${e.message}" }
                    }
                    mutable.update { it.copy(busy = false, voiceBusy = false, recording = false, status = finalStatus) }
                }
            }
        }
    }
    fun clear() { if (!state.value.busy) viewModelScope.launch { dao.clearChat(current.value); status("History cleared") } }
    fun removeModel() {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false) }
            try { InferenceRepository.unload(); check(!models.model.exists() || models.model.delete()); status("Model removed") }
            catch (e: Exception) { status(e.message ?: "Cannot remove model") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { disposed = true; updates.cancel(); microphone.stop(); speakerInstance?.close(); InferenceRepository.cancel(); super.onCleared() }
}
