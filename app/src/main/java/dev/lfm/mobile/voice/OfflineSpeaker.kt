package dev.lfm.mobile.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.util.UUID

class OfflineSpeaker(context: Context, private val speaking: (Boolean) -> Unit) {
    private val initialized = CompletableDeferred<Boolean>()
    private val engine = TextToSpeech(context.applicationContext) { result -> initialized.complete(result == TextToSpeech.SUCCESS) }
    @Volatile private var finalUtterance: String? = null
    init { engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(id: String?) { if (finalUtterance != null) speaking(true) }
        override fun onDone(id: String?) { if (id == finalUtterance) speaking(false) }
        @Deprecated("Android callback") override fun onError(id: String?) { speaking(false) }
    }) }
    suspend fun speak(text: String): String {
        if (withTimeoutOrNull(5000) { initialized.await() } != true) return "No Android TTS engine available; text reply is ready"
        val offline = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }.sortedBy { it.name }.firstOrNull()
            ?: return "Install an offline English voice in Android TTS settings; text reply is ready"
        if (engine.setVoice(offline) != TextToSpeech.SUCCESS) return "Offline voice pack could not load; text reply is ready"
        engine.stop()
        val chunks = text.chunked(TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1))
        val ids = chunks.map { UUID.randomUUID().toString() }; finalUtterance = ids.lastOrNull()
        for (i in chunks.indices) if (engine.speak(chunks[i], TextToSpeech.QUEUE_ADD, Bundle(), ids[i]) != TextToSpeech.SUCCESS) {
            stop(); return "Offline speech failed; text reply is ready"
        }
        return "Speaking with an installed offline Android voice"
    }
    fun stop() { finalUtterance = null; engine.stop(); speaking(false) }
    fun close() { stop(); engine.shutdown() }
}
