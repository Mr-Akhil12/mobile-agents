package dev.lfm.nativebridge

object NativeOmni {
    init { System.loadLibrary("lfm_jni") }
    external fun load(model: String, projector: String)
    external fun sampleRate(): Int
    external fun transcribe(samples: FloatArray, sampleRate: Int): String
    external fun cancel()
    external fun unload()
}
