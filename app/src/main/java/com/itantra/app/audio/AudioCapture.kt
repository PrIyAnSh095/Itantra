package com.itantra.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles raw microphone PCM capture at 16kHz, 16-bit Mono.
 * Offloads read operations to a background coroutine thread.
 */
class AudioCapture(
    private val vadProcessor: VadProcessor? = null,
    private val onSpeechDetected: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val audioBuffer = ArrayList<Short>(SAMPLE_RATE * 10) // 10s initial capacity

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (isRecording.get()) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = minBufferSize.coerceAtLeast(VadProcessor.WINDOW_SIZE_SAMPLES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            synchronized(audioBuffer) {
                audioBuffer.clear()
            }
            vadProcessor?.resetState()

            audioRecord?.startRecording()
            isRecording.set(true)

            captureJob = CoroutineScope(Dispatchers.IO).launch {
                val readChunk = ShortArray(VadProcessor.WINDOW_SIZE_SAMPLES)
                val floatChunk = FloatArray(VadProcessor.WINDOW_SIZE_SAMPLES)

                while (isActive && isRecording.get()) {
                    val read = audioRecord?.read(readChunk, 0, readChunk.size) ?: 0
                    if (read > 0) {
                        synchronized(audioBuffer) {
                            for (i in 0 until read) {
                                audioBuffer.add(readChunk[i])
                            }
                        }

                        // Real-time VAD evaluation
                        if (vadProcessor != null && onSpeechDetected != null) {
                            for (i in 0 until read) {
                                floatChunk[i] = readChunk[i] / 32768.0f
                            }
                            for (i in read until floatChunk.size) {
                                floatChunk[i] = 0.0f
                            }
                            val speech = vadProcessor.isSpeech(floatChunk)
                            onSpeechDetected.invoke(speech)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio capture: ${e.message}", e)
            stopCapture()
            return false
        }
    }

    /**
     * Stops microphone capture and returns normalized FloatArray (-1.0 to 1.0)
     */
    fun stopCapture(): FloatArray {
        isRecording.set(false)
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord?.release()
            audioRecord = null
        }

        val result: FloatArray
        synchronized(audioBuffer) {
            result = FloatArray(audioBuffer.size)
            for (i in audioBuffer.indices) {
                result[i] = audioBuffer[i] / 32768.0f
            }
            audioBuffer.clear()
        }
        return result
    }

    fun isCapturing(): Boolean = isRecording.get()
}
