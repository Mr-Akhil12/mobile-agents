package dev.lfm.mobile.agent

import dev.lfm.mobile.data.ChatMessage

/** Keep whole completed turns; disk history is never truncated or summarized as invented facts. */
object ContextWindow {
    fun turns(rows: List<ChatMessage>, maxChars: Int = 9000): List<List<ChatMessage>> {
        val completed = rows.filter { it.role == "assistant" }.map { it.turnId }.toSet()
        val candidates = rows.filter { it.turnId in completed && it.contextJson != null }.groupBy { it.turnId }.values.toList().takeLast(12)
        val selected = mutableListOf<List<ChatMessage>>()
        var used = 0
        for (turn in candidates.asReversed()) {
            val cost = turn.sumOf { it.contextJson!!.length() }
            if (used + cost > maxChars) break
            selected.add(0, turn); used += cost
        }
        return selected
    }
    fun notes(notes: List<String>, maxChars: Int = 3000): List<String> {
        val selected = mutableListOf<String>(); var used = 0
        for (note in notes.take(20)) {
            if (used + note.length() + 1 > maxChars) continue
            selected += note; used += note.length() + 1
        }
        return selected
    }
}
