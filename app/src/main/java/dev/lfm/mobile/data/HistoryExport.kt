package dev.lfm.mobile.data

import org.json.JSONObject
import java.io.Writer

/** Portable user-visible data only: no raw context/protocol/reasoning, audio, model or credentials. */
object HistoryExport {
    fun message(row: ChatMessage): JSONObject = JSONObject().put("id", row.id).put("conversationId", row.conversationId)
        .put("turnId", row.turnId).put("role", row.role).put("content", row.content).put("observedAt", row.observedAt)
    fun conversation(row: Conversation): JSONObject = JSONObject().put("id", row.id).put("title", row.title).put("createdAt", row.createdAt)
    fun memory(row: MemoryFact): JSONObject = JSONObject().put("id", row.id).put("content", row.content)
        .put("createdAt", row.createdAt).put("provenance", "User-saved note")
    fun start(writer: Writer, scope: String) { writer.write("{\"format\":\"local-agents-history\",\"version\":1,\"scope\":" + JSONObject.quote(scope) + ",\"exportedAt\":" + System.currentTimeMillis()) }
}
