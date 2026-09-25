package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 22050 // IndicTTS VITS standard output rate
    }

    private var activeAudioTrack: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Plays synthesized speech audio samples (-1.0 to 1.0 floats)
     */
    fun playAudio(
        audioData: FloatArray,
        isAlert: Boolean = false,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        onPlaybackFinished: (() -> Unit)? = null
    ) {
        if (audioData.isEmpty()) {
            onPlaybackFinished?.invoke()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isAlert) {
                    acquireScreenWakeLock()
                    boostAlertVolume()
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(
                        if (isAlert) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )

                val bufferSize = (audioData.size * 4).coerceAtLeast(minBufferSize)

                stopCurrentPlayback()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeAudioTrack = track

                track.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
                track.play()

                // Calculate duration in ms
                val durationMs = (audioData.size.toLong() * 1000L / sampleRate) + 200L
                Thread.sleep(durationMs)

                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioTrack cleanup exception: ${e.message}")
                } finally {
                    activeAudioTrack = null
                    releaseScreenWakeLock()
                    onPlaybackFinished?.invoke()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during audio playback: ${e.message}", e)
                releaseScreenWakeLock()
                onPlaybackFinished?.invoke()
            }
        }
    }

    private fun boostAlertVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to boost alert volume: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "iTantra:AlertWakeLock"
            ).apply {
                acquire(30_000L) // 30 sec max
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseScreenWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release exception: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    fun stopCurrentPlayback() {
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (e: Exception) {
            // Ignored
        } finally {
            activeAudioTrack = null
            releaseScreenWakeLock()
        }
    }
}
