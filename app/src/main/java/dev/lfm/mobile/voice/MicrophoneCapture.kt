package dev.lfm.mobile.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Foreground, user-started capture. Audio stays in bounded RAM and is never logged. */
class MicrophoneCapture(private val context: Context) {
    private val wanted = AtomicBoolean(false)
    private val recorder = AtomicReference<AudioRecord?>()
    val active get() = recorder.get() != null
    fun stop() { wanted.set(false); recorder.get()?.let { runCatching { it.stop() } } }
    @SuppressLint("MissingPermission")
    suspend fun capture(rate: Int, progress: (Float) -> Unit = {}): FloatArray = withContext(Dispatchers.IO) {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission denied" }
        require(rate in 8000..48000)
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "This microphone does not support the model's sample rate" }
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096))
        check(audio.state == AudioRecord.STATE_INITIALIZED) { audio.release(); "Microphone could not initialize" }
        check(recorder.compareAndSet(null, audio)) { audio.release(); "Microphone already in use" }
        val samples = FloatArray(rate * 20)
        val block = ShortArray(1024)
        var count = 0; var reported = -1
        wanted.set(true)
        try {
            audio.startRecording()
            while (wanted.get() && count < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = audio.read(block, 0, minOf(block.size, samples.size - count))
                if (n < 0) { check(!wanted.get()) { "Microphone read failed" }; break }
                for (i in 0 until n) samples[count++] = block[i] / 32768f
                val tick = count * 10 / rate
                if (tick != reported) { reported = tick; progress(count.toFloat() / rate) }
            }
            currentCoroutineContext().ensureActive()
            samples.copyOf(count)
        } finally { wanted.set(false); runCatching { audio.stop() }; audio.release(); recorder.set(null); samples.fill(0f) }
    }
}
