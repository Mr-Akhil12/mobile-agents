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
import androidx.room.withTransaction
import dev.lfm.mobile.voice.VoiceSubmission
import org.json.JSONArray
import android.provider.DocumentsContract
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
    val transcript: String = "", val transcriptId: Long = 0,
    val textAvailable: Boolean = false, val omniAvailable: Boolean = false, val projectorAvailable: Boolean = false,
    val contextNotice: String = "", val voiceTiming: String = "", val managementError: String = "",
    val importTarget: String? = null, val importProgress: Int = 0, val importErrors: Map<String, String> = emptyMap())

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
    private val searchText = MutableStateFlow("")
    val searchQuery = searchText.asStateFlow()
    val searchResults = searchText.flatMapLatest { dao.searchConversations(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
    private val voiceSubmission = VoiceSubmission()
    private var pendingVoiceTiming: JSONObject? = null
    private val voicePrefs = application.getSharedPreferences("voice-settings", 0)
    private val reviewChoice = MutableStateFlow(voicePrefs.getBoolean("reviewFirst", false))
    val reviewVoice = reviewChoice.asStateFlow()
    fun setReviewVoice(value: Boolean) { if (!state.value.busy) { reviewChoice.value = value; voicePrefs.edit().putBoolean("reviewFirst", value).apply() } }
    fun searchChats(value: String) { searchText.value = value.take(200) }
    fun renameChat(id: String, title: String) { if (!state.value.busy && title.isNotBlank()) manage { dao.renameConversation(id, title.trim().take(100)) } }
    fun deleteChat(id: String) {
        if (state.value.busy) return
        manage {
            val replacement = if (current.value == id) UUID.randomUUID().toString() else null
            db.withTransaction {
                dao.deleteChat(id)
                if (replacement != null) dao.createConversation(Conversation(replacement, "New chat"))
            }
            if (replacement != null) { current.value = replacement; chatPrefs.edit().putString("conversation", replacement).apply(); resetDraft() }
        }
    }
    private fun resetDraft() {
        voiceReplyPending = false; pendingVoiceTiming = null; voiceSubmission.reset(); stopSpeaking()
        mutable.update { it.copy(streaming = "", transcript = "", transcriptId = it.transcriptId + 1, contextNotice = "", voiceTiming = "") }
    }
    fun stopSpeaking() { speechJob?.cancel(); speakerInstance?.stop() }
    fun exportHistory(uri: Uri, all: Boolean, chatId: String = current.value) {
        if (state.value.busy) return
        val selected = chatId
        stopSpeaking()
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, status = "Exporting local history", managementError = "") }
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt").use { stream ->
                        checkNotNull(stream) { "Cannot write the chosen document" }
                        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            db.withTransaction {
                                HistoryExport.start(writer, if (all) "all chats and saved memory" else "current chat")
                                writer.write(",\"conversations\":[")
                                dao.allConversations().filter { all || it.id == selected }.forEachIndexed { i, row -> if (i > 0) writer.write(","); writer.write(HistoryExport.conversation(row).toString()) }
                                writer.write("],\"messages\":[")
                                var offset = 0; var first = true
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val page = dao.exportPage(if (all) null else selected, 200, offset)
                                    if (page.isEmpty()) break
                                    for (row in page) { if (!first) writer.write(","); first = false; writer.write(HistoryExport.message(row).toString()) }
                                    offset += page.size
                                }
                                writer.write("],\"memories\":[")
                                if (all) dao.allMemories().forEachIndexed { i, row -> if (i > 0) writer.write(","); writer.write(HistoryExport.memory(row).toString()) }
                                writer.write("]}")
                            }
                        }
                    }
                }
                status("Export saved to your chosen document")
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } }
                status("Export cancelled; check the chosen location for an incomplete document"); throw e
            } catch (e: Exception) { mutable.update { it.copy(status = "Export failed", managementError = "${e.message}. An incomplete document may remain at the chosen location.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private var speakerInstance: OfflineSpeaker? = null
    private fun speaker(): OfflineSpeaker = speakerInstance ?: OfflineSpeaker(getApplication(), speaking = { value -> mutable.update { it.copy(speaking = value) } }, failure = { message -> status(message) }).also { speakerInstance = it }
    val updates = UpdateManager(application, viewModelScope)
    private var job: Job? = null
    init {
        viewModelScope.launch { if (dao.conversation(current.value) == null) dao.createConversation(Conversation(current.value, "New chat")) }
        refreshModels()
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
        manage {
            val id = UUID.randomUUID().toString()
            resetDraft()
            dao.createConversation(Conversation(id, "New chat")); current.value = id
            chatPrefs.edit().putString("conversation", id).apply()
            mutable.update { it.copy(streaming = "", metrics = null, elapsedMs = 0, startedAtMs = 0, transcript = "", status = if (it.loaded) "Ready" else it.status) }
        }
    }
    fun selectConversation(id: String) {
        if (state.value.busy) return
        resetDraft()
        current.value = id
        chatPrefs.edit().putString("conversation", id).apply()
        mutable.update { it.copy(streaming = "", metrics = null, elapsedMs = 0, startedAtMs = 0, transcript = "") }
    }
    fun saveMemory(text: String, id: Long? = null) {
        if (state.value.busy || text.isBlank() || text.length > 1000) return
        manage {
            if (id != null) dao.editMemory(id, text.trim())
            else if (dao.allMemories().size < 20) dao.saveMemory(MemoryFact(content = text.trim()))
        }
    }
    fun deleteMemory(id: Long) { if (!state.value.busy) manage { dao.deleteMemory(id) } }
    private fun manage(action: suspend () -> Unit) {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, managementError = "") }
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(managementError = e.message ?: "Local data update failed") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
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
    private fun refreshModels() { mutable.update { it.copy(
        textAvailable = models.model.isFile && models.model.length() == ModelStore.SIZE,
        omniAvailable = voiceModels.available(VoicePart.MODEL), projectorAvailable = voiceModels.available(VoicePart.PROJECTOR)) } }
    private fun importProgress(target: String, bytes: Long, total: Long) { mutable.update { it.copy(importTarget = target, importProgress = (bytes * 100 / total).toInt()) } }
    fun importModel(uri: Uri) {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, importTarget = "text", importProgress = 0, importErrors = it.importErrors - "text", status = "Importing text model · see Models") }
            try {
                models.importModel(uri) { bytes -> importProgress("text", bytes, ModelStore.SIZE) }
                mutable.update { it.copy(loaded = false) }
                InferenceRepository.unload()
                status("SHA-256 verified. Loading native model")
                InferenceRepository.load(models.model.path)
                val thinking = InferenceRepository.supportsThinking()
                mutable.update { it.copy(loaded = true, supportsThinking = thinking, status = "Ready · verified local model") }
            } catch (e: CancellationException) { status("Import cancelled"); throw e }
            catch (e: Exception) { mutable.update { it.copy(importErrors = it.importErrors + ("text" to (e.message ?: "Import failed")), status = "Text import failed · see Models") } }
            finally { refreshModels(); mutable.update { it.copy(busy = false, importTarget = null) } }
        }
    }
    fun send(text: String): Boolean {
        if (text.isBlank() || text.length > 8000 || state.value.busy || !state.value.loaded) return false
        speechJob?.cancel(); speakerInstance?.stop()
        val readAloud = voiceReplyPending; voiceReplyPending = false
        val timing = if (readAloud) pendingVoiceTiming else null; pendingVoiceTiming = null
        job = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val conversation = current.value
            val snapshot = settings.value.copy(thinking = settings.value.thinking && state.value.supportsThinking)
            mutable.update { it.copy(busy = true, working = true, streaming = "", status = "Preparing prompt", voiceTiming = timing?.toString() ?: "", startedAtMs = started, elapsedMs = 0, metrics = null) }
            try {
                if (dao.inConversation(conversation).isEmpty() && dao.conversation(conversation)?.title in setOf("New chat", "Earlier conversation")) dao.renameConversation(conversation, text.trim().take(60))
                agent.answer(text.trim(), ::status, { value -> mutable.update { it.copy(streaming = value) } },
                    settings = snapshot, conversationId = conversation, memory = dao.allMemories().map { it.content },
                    context = { notice -> mutable.update { it.copy(contextNotice = notice) } }, voiceTiming = timing,
                    metrics = { measured -> mutable.update { it.copy(metrics = measured) } })
                if (readAloud && foreground) dao.inConversation(conversation).lastOrNull { it.role == "assistant" }?.let { reply -> speechJob = viewModelScope.launch {
                    try { status(speaker().speak(reply.content)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status("Offline speech unavailable; text reply is ready") }
                } }
            }
            catch (e: CancellationException) { status("Cancelled"); throw e }
            catch (e: Exception) {
                status(e.message ?: "Inference failed")
                dao.insert(ChatMessage(turnId = "error", role = "error", content = e.message ?: "Inference failed", conversationId = conversation))
            } finally { mutable.update { it.copy(busy = false, working = false, streaming = "", elapsedMs = SystemClock.elapsedRealtime() - started) } }
        }
        return true
    }
    fun cancel() {
        voiceSubmission.reset()
        val waitingForInference = state.value.busy
        job?.cancel(); speechJob?.cancel(); microphone.stop(); speakerInstance?.stop(); InferenceRepository.cancel()
        status(if (waitingForInference) "Stopping" else "Speech stopped")
    }
    fun microphoneDenied() { status("Microphone permission denied. Typed chat still works.") }
    fun finishVoiceRecording() { if (state.value.recording) { voiceSubmission.request(true, reviewChoice.value); microphone.stop() } }
    fun onForeground(value: Boolean) { foreground = value; if (value) updates.checkIfDue(); if (!value) { speechJob?.cancel(); speakerInstance?.stop(); if (state.value.voiceBusy) cancel() } }
    fun importVoice(part: VoicePart, uri: Uri) {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, importTarget = part.name, importProgress = 0, importErrors = it.importErrors - part.name, status = "Importing voice file · see Models") }
            try {
                InferenceRepository.unloadVoice()
                voiceModels.import(part, uri) { bytes -> importProgress(part.name, bytes, part.bytes) }
                status(if (voiceModels.ready) "Omni pair verified · ready for voice" else "Voice file verified; import the other paired file")
            } catch (e: CancellationException) { status("Voice import cancelled"); throw e }
            catch (e: Exception) { mutable.update { it.copy(importErrors = it.importErrors + (part.name to (e.message ?: "Import failed")), status = "Voice import failed · see Models") } }
            finally { refreshModels(); mutable.update { it.copy(busy = false, importTarget = null) } }
        }
    }
    fun startVoice() {
        if (state.value.busy) return
        if (!voiceModels.ready) { status("Import both pinned Omni files from Models first"); return }
        if (!state.value.textAvailable) { status("Import the text model before sending a voice prompt"); return }
        if (getApplication<Application>().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { microphoneDenied(); return }
        stopSpeaking(); voiceSubmission.reset(); voiceReplyPending = false; pendingVoiceTiming = null
        val conversation = current.value
        job = viewModelScope.launch {
            var finalStatus = "Voice cancelled"
            var transcript: String? = null
            val timings = JSONObject()
            mutable.update { it.copy(busy = true, loaded = false, voiceBusy = true, recording = false, voiceTiming = "", status = "Preparing local Omni voice model") }
            try {
                var started = SystemClock.elapsedRealtime()
                val rate = InferenceRepository.loadVoice(voiceModels.file(VoicePart.MODEL).path, voiceModels.file(VoicePart.PROJECTOR).path)
                timings.put("load_ms", SystemClock.elapsedRealtime() - started)
                mutable.update { it.copy(recording = false, status = "Starting microphone") }
                val audio = microphone.capture(rate) { seconds -> mutable.update {
                    it.copy(recording = true, status = String.format(java.util.Locale.US, "Listening %.1f s · tap Send", seconds))
                } }
                timings.put("audio_ms", audio.size * 1000L / rate)
                mutable.update { it.copy(recording = false, status = "Transcribing locally with Omni") }
                started = SystemClock.elapsedRealtime()
                val result = try { JSONObject(InferenceRepository.transcribe(audio, rate)) } finally { audio.fill(0f) }
                timings.put("asr_ms", SystemClock.elapsedRealtime() - started)
                currentCoroutineContext().ensureActive()
                val recognized = result.getString("transcript").trim()
                transcript = recognized
                check(recognized.isNotBlank()) { "No transcript returned" }
                mutable.update { it.copy(transcript = recognized, transcriptId = it.transcriptId + 1) }
                finalStatus = "Transcript ready · review and send. Spoken reply requires an offline Android voice."
            } catch (e: CancellationException) { voiceSubmission.reset(); finalStatus = "Voice cancelled"; throw e }
            catch (e: Exception) { voiceSubmission.reset(); finalStatus = e.message ?: "Voice recognition failed" }
            finally {
                microphone.stop()
                withContext(NonCancellable) {
                    InferenceRepository.unloadVoice()
                    if (!disposed && models.model.isFile) {
                        status("Restoring text model")
                        val started = SystemClock.elapsedRealtime()
                        try { InferenceRepository.load(models.model.path); val supports = InferenceRepository.supportsThinking(); mutable.update { it.copy(loaded = true, supportsThinking = supports) } }
                        catch (e: Exception) { finalStatus += " · Text model reload failed: ${e.message}" }
                        timings.put("restore_ms", SystemClock.elapsedRealtime() - started)
                    }
                    mutable.update { it.copy(busy = false, voiceBusy = false, recording = false, status = finalStatus, voiceTiming = timings.toString()) }
                }
            }
            currentCoroutineContext().ensureActive()
            transcript?.let { recognized ->
                voiceReplyPending = true; pendingVoiceTiming = timings
                if (voiceSubmission.consume(foreground, current.value == conversation, state.value.loaded) && send(recognized)) {
                    mutable.update { it.copy(transcript = "", transcriptId = it.transcriptId + 1) }
                }
            }
        }
    }
    fun clear() { if (!state.value.busy) manage { dao.clearChat(current.value); resetDraft(); status("History cleared") } }
    fun removeModel() {
        if (state.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, loaded = false) }
            try { InferenceRepository.unload(); check(!models.model.exists() || models.model.delete()); status("Model removed") }
            catch (e: Exception) { status(e.message ?: "Cannot remove model") }
            finally { refreshModels(); mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { disposed = true; updates.cancel(); microphone.stop(); speakerInstance?.close(); InferenceRepository.cancel(); super.onCleared() }
}
