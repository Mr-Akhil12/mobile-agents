package dev.lfm.nativebridge

/** All operations except cancel are serialized on the app inference dispatcher. */
object NativeInference {
    init { System.loadLibrary("lfm_jni") }
    fun interface Listener { fun onContent(content: String) }
    external fun load(path: String)
    external fun generate(messagesJson: String, toolsJson: String, listener: Listener): String
    external fun cancel()
    external fun unload()
}
