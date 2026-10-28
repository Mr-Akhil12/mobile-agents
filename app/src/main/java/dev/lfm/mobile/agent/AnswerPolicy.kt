package dev.lfm.mobile.agent

object AnswerPolicy {
    fun safeForDisplay(text: String): Boolean {
        val s = text.lowercase()
        return listOf("<think", "</think", "[start thinking]", "[end thinking]", "<tool_call", "<function=", "<|im_")
            .none { it in s } && listOf("we need to answer", "the user is asking", "i should answer", "let me think")
            .none { s.trimStart().startsWith(it) }
    }
    fun validate(text: String) {
        require(text.isNotBlank()) { "Model returned no final answer" }
        require(safeForDisplay(text)) { "Model returned planning/protocol text; answer withheld" }
    }
}
