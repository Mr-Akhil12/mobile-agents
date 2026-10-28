package dev.lfm.mobile

import dev.lfm.mobile.agent.AnswerPolicy
import dev.lfm.mobile.tools.ToolPolicy
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun readOnlyToolsAcceptEmptyArguments() {
        ToolPolicy.validate("get_local_time", "{}")
        ToolPolicy.validate("get_battery_status", "{}")
    }
    @Test fun unknownToolCannotExecute() {
        assertThrows(IllegalArgumentException::class.java) { ToolPolicy.validate("run_shell_command", "{}") }
    }
    @Test fun extraArgumentsCannotExecute() {
        assertThrows(IllegalArgumentException::class.java) { ToolPolicy.validate("get_local_time", "{\"timezone\":\"invented\"}") }
    }
    @Test fun malformedArgumentsCannotExecute() {
        assertThrows(Exception::class.java) { ToolPolicy.validate("get_local_time", "[]") }
    }
    @Test fun planningAndProtocolTextWithheld() {
        listOf("<think>secret</think>", "[Start thinking]test", "<tool_call>call", "We need to answer this").forEach {
            assertFalse(AnswerPolicy.safeForDisplay(it))
        }
    }
    @Test fun ordinaryAnswerRetained() { AnswerPolicy.validate("The device reports 16:00 SAST.") }
    @Test fun emptyAnswerRejected() {
        assertThrows(IllegalArgumentException::class.java) { AnswerPolicy.validate(" ") }
    }
}
