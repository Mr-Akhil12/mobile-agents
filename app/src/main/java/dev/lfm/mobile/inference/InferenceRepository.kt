package dev.lfm.mobile.inference

import dev.lfm.nativebridge.NativeInference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.Executors

/** Process-wide owner: one model, serialized load/generate/unload; cancel is atomic. */
object InferenceRepository {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    suspend fun load(path: String) = withContext(dispatcher) { currentCoroutineContext().ensureActive(); NativeInference.load(path) }
    suspend fun generate(messages: String, tools: String, onContent: (String) -> Unit): String =
        withContext(dispatcher) { currentCoroutineContext().ensureActive(); NativeInference.generate(messages, tools, onContent) }
    suspend fun generateConfigured(messages: String, tools: String, settings: GenerationSettings, onContent: (String) -> Unit): String =
        withContext(dispatcher) { currentCoroutineContext().ensureActive(); NativeInference.generateConfigured(messages, tools, settings.toJson(), onContent) }
    suspend fun supportsThinking(): Boolean = withContext(dispatcher) { NativeInference.supportsThinking() }
    suspend fun unload() = withContext(dispatcher) { NativeInference.unload() }
    fun cancel() = NativeInference.cancel()
}
