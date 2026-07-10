package com.example.cameramusicapp.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.util.Log
import kotlin.math.abs

/**
 * Foydalanuvchi tanlagan audio/qo'shiqni ijro etadi va shu bilan bir
 * vaqtda Visualizer API yordamida real vaqtda ovoz to'lqini
 * (waveform) ma'lumotlarini chiqaradi.
 */
class MusicVisualizerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null

    /**
     * Musiqani boshlaydi va har bir waveform freymida onAmplitude
     * chaqiriladi (0f..1f normallashtirilgan qiymat bilan).
     */
    fun play(uri: Uri, onAmplitude: (Float) -> Unit, onCompletion: () -> Unit) {
        stop()

        val player = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            isLooping = false
            setOnCompletionListener { onCompletion() }
        }
        mediaPlayer = player
        player.start()

        try {
            val captureSize = Visualizer.getCaptureSizeRange()[1] // maksimal aniqlik
            val viz = Visualizer(player.audioSessionId)
            viz.captureSize = captureSize
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform ?: return
                        // Bayt qiymatlarini 0..1 oralig'idagi amplitudaga aylantiramiz
                        var maxDelta = 0
                        for (b in waveform) {
                            val delta = abs(b.toInt() - 128)
                            if (delta > maxDelta) maxDelta = delta
                        }
                        onAmplitude((maxDelta / 128f).coerceIn(0f, 1f))
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Ishlatilmaydi - waveform (vaqt domenidagi) ma'lumot yetarli
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            viz.enabled = true
            visualizer = viz
        } catch (e: Exception) {
            Log.e("MusicVisualizer", "Visualizer ishga tushmadi (ba'zi qurilmalarda cheklangan)", e)
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
