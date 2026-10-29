package dev.lfm.nativebridge

/** All operations except cancel are serialized on the app inference dispatcher. */
object NativeInference {
    init { System.loadLibrary("lfm_jni") }
    fun interface Listener { fun onContent(content: String) }
    external fun load(path: String)
    fun generate(messagesJson: String, toolsJson: String, listener: Listener): String =
        generateConfigured(messagesJson, toolsJson, "{}", listener)
    external fun generateConfigured(messagesJson: String, toolsJson: String, settingsJson: String, listener: Listener): String
    external fun supportsThinking(): Boolean
    external fun cancel()
    external fun unload()
}
