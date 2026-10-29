package dev.lfm.mobile

import dev.lfm.mobile.inference.InferenceMetrics
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InferenceMetricsTest {
    @Test fun ratesUseTotalTokensAndTimeAcrossToolCalls() {
        val first = JSONObject().put("prompt_tokens", 100).put("generated_tokens", 10).put("prompt_ms", 1000).put("generation_ms", 1000)
        val second = JSONObject().put("prompt_tokens", 600).put("generated_tokens", 80).put("prompt_ms", 3000).put("generation_ms", 4000)
        val total = InferenceMetrics().add(first).add(second).copy(elapsedMs = 10000)
        assertEquals(175.0, total.ingestionSpeed!!, 0.001)
        assertEquals(18.0, total.generationSpeed!!, 0.001)
        assertEquals(2, total.calls)
        assertEquals(total, InferenceMetrics.fromJson(total.toJson()))
    }
    @Test fun unmeasuredSpeedsAreAbsentInsteadOfInvented() {
        assertNull(InferenceMetrics().ingestionSpeed)
        assertNull(InferenceMetrics().generationSpeed)
    }
}
