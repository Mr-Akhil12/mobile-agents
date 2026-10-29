package dev.lfm.mobile.voice

/** One-shot Send intent. Timeout/end-of-capture alone never authorizes auto-submission. */
class VoiceSubmission {
    private var requested = false
    fun reset() { requested = false }
    fun request(recording: Boolean, reviewFirst: Boolean) { if (recording) requested = !reviewFirst }
    fun consume(foreground: Boolean, sameConversation: Boolean, loaded: Boolean): Boolean {
        val submit = requested && foreground && sameConversation && loaded
        reset(); return submit
    }
}
