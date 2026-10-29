package dev.lfm.mobile

import dev.lfm.mobile.voice.VoiceSubmission
import org.junit.Assert.*
import org.junit.Test

class VoiceSubmissionTest {
    @Test fun explicitSendSubmitsOnlyOnce() {
        val gate = VoiceSubmission(); gate.request(true, false)
        assertTrue(gate.consume(true, true, true)); assertFalse(gate.consume(true, true, true))
    }
    @Test fun reviewAndCaptureTimeoutNeverAutoSubmit() {
        val gate = VoiceSubmission(); assertFalse(gate.consume(true, true, true))
        gate.request(true, true); assertFalse(gate.consume(true, true, true))
        gate.request(false, false); assertFalse(gate.consume(true, true, true))
    }
    @Test fun cancelAndLifecyclePreventLateSubmission() {
        val gate = VoiceSubmission(); gate.request(true, false); gate.reset(); assertFalse(gate.consume(true, true, true))
        for (conditions in listOf(Triple(false,true,true), Triple(true,false,true), Triple(true,true,false))) {
            gate.request(true, false); assertFalse(gate.consume(conditions.first, conditions.second, conditions.third))
            assertFalse(gate.consume(true, true, true))
        }
    }
}
