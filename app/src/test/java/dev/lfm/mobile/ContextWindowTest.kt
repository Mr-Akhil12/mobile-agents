package dev.lfm.mobile

import dev.lfm.mobile.agent.ContextWindow
import dev.lfm.mobile.data.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ContextWindowTest {
    private fun row(turn: String, role: String, text: String = "{}") = ChatMessage(turnId = turn, role = role, content = role, contextJson = text)
    @Test fun excludesCancelledTurnsAndPreservesWholeToolPairs() {
        val rows = listOf(row("old","user"), row("old","tool_request"), row("old","tool"), row("old","assistant"), row("cancelled","user"))
        assertEquals(listOf(rows.take(4)), ContextWindow.turns(rows))
        assertTrue(ContextWindow.turns(rows, 7).isEmpty())
    }
    @Test fun selectsTwelveMostRecentWithoutChangingHistory() {
        val rows = (1..20).flatMap { listOf(row("$it","user"),row("$it","assistant")) }
        val selected = ContextWindow.turns(rows)
        assertEquals(12,selected.size);assertEquals("9", selected.first().first().turnId);assertEquals(40,rows.size)
    }
    @Test fun notesAreWholeAndBounded() {
        assertEquals(listOf("one", "two"),ContextWindow.notes(listOf("one", "oversized", "two"),8))
    }
}
