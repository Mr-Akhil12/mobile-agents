package dev.lfm.mobile.inference

import org.json.JSONObject

/** Limits are also enforced in native code. Token budget includes hidden reasoning. */
data class GenerationSettings(val temperature: Float = 0.1f, val maxTokens: Int = 512,
    val topK: Int = 50, val repeatPenalty: Float = 1.1f, val thinking: Boolean = false) {
    fun toJson(): String = JSONObject().put("temperature", temperature.toDouble()).put("maxTokens", maxTokens)
        .put("topK", topK).put("repeatPenalty", repeatPenalty.toDouble()).put("thinking", thinking).toString()
}

data class InferenceMetrics(val promptTokens: Long = 0, val generatedTokens: Long = 0,
    val promptMs: Double = 0.0, val generationMs: Double = 0.0, val elapsedMs: Long = 0, val calls: Int = 0,
    val totalPromptTokens: Long = 0, val reusedPromptTokens: Long = 0) {
    val ingestionSpeed: Double? get() = if (promptMs > 0) promptTokens * 1000.0 / promptMs else null
    val generationSpeed: Double? get() = if (generationMs > 0) generatedTokens * 1000.0 / generationMs else null
    fun add(j: JSONObject) = copy(promptTokens = promptTokens + j.getLong("prompt_tokens"),
        generatedTokens = generatedTokens + j.getLong("generated_tokens"), promptMs = promptMs + j.getDouble("prompt_ms"),
        generationMs = generationMs + j.getDouble("generation_ms"), calls = calls + 1,
        totalPromptTokens = totalPromptTokens + j.optLong("prompt_total_tokens", j.getLong("prompt_tokens")),
        reusedPromptTokens = reusedPromptTokens + j.optLong("reused_prompt_tokens"))
    fun toJson(): String = JSONObject().put("prompt_tokens", promptTokens).put("generated_tokens", generatedTokens)
        .put("prompt_ms", promptMs).put("generation_ms", generationMs).put("elapsed_ms", elapsedMs).put("calls", calls).put("prompt_total_tokens", totalPromptTokens).put("reused_prompt_tokens", reusedPromptTokens).toString()
    companion object {
        fun fromJson(text: String): InferenceMetrics {
            val j = JSONObject(text)
            return InferenceMetrics(j.getLong("prompt_tokens"), j.getLong("generated_tokens"), j.getDouble("prompt_ms"),
                j.getDouble("generation_ms"), j.optLong("elapsed_ms"), j.optInt("calls"), j.optLong("prompt_total_tokens", j.getLong("prompt_tokens")), j.optLong("reused_prompt_tokens"))
        }
    }
}
