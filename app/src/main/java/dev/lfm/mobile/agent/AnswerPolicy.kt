package dev.lfm.mobile.agent

object AnswerPolicy {
    private val planningPrefixes = listOf("we need to answer", "the user is asking", "i should answer", "let me think")
    fun safeForDisplay(text: String): Boolean {
        val s = text.lowercase()
        return listOf("<think", "</think", "[start thinking]", "[end thinking]", "<tool_call", "<function=", "<|im_")
            .none { it in s } && planningPrefixes
            .none { s.trimStart().startsWith(it) }
    }
    fun safeForStreaming(text: String): Boolean {
        val leading = text.lowercase().trimStart()
        // Native callbacks contain the cumulative text. Briefly withhold a partial
        // planning prefix; a normal answer is displayed as soon as it diverges.
        return leading.isNotEmpty() && safeForDisplay(text) && planningPrefixes.none { it.startsWith(leading) }
    }
    fun validate(text: String) {
        require(text.isNotBlank()) { "Model returned no final answer" }
        require(safeForDisplay(text)) { "Model returned planning/protocol text; answer withheld" }
    }
}
