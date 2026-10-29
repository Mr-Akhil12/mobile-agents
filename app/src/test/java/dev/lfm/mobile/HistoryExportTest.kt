package dev.lfm.mobile

import dev.lfm.mobile.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class HistoryExportTest {
    @Test fun escapesUserTextWithoutExportingInternalProtocol() {
        val text = "A quote \" and newline\n🐘"
        val row = ChatMessage(turnId="t",role="assistant",content=text,contextJson="private reasoning",metricsJson="private settings")
        val json = HistoryExport.message(row)
        assertEquals(text,JSONObject(json.toString()).getString("content"))
        assertFalse(json.has("contextJson"));assertFalse(json.toString().contains("private reasoning"));assertFalse(json.has("metricsJson"))
    }
    @Test fun formatAndMemoryProvenanceAreExplicit() {
        val writer=StringWriter();HistoryExport.start(writer,"current chat");writer.write(",\"messages\":[],\"conversations\":[],\"memories\":[]}")
        assertEquals(1,JSONObject(writer.toString()).getInt("version"))
        assertEquals("User-saved note",HistoryExport.memory(MemoryFact(content="note")).getString("provenance"))
    }
}
