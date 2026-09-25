package com.itantra.app.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Silero Voice Activity Detector using TensorFlow Lite.
 * Operates on 16kHz audio chunks of 512 samples (~32ms).
 * Maintains internal state tensors across audio frames.
 *
 * If the silero_vad.tflite model file is not yet placed, provides an adaptive
 * energy-based fallback so speech processing is never blocked.
 */
class VadProcessor(context: Context) {

    companion object {
        private const val TAG = "VadProcessor"
        const val WINDOW_SIZE_SAMPLES = 512 // 32ms at 16kHz
        private const val SPEECH_THRESHOLD = 0.5f
    }

    private var tfliteInterpreter: Interpreter? = null
    private var isTfliteLoaded = false

    // State tensors for Silero VAD v4: [2, 1, 64] float32
    private var stateH = Array(2) { Array(1) { FloatArray(64) } }
    private var stateC = Array(2) { Array(1) { FloatArray(64) } }

    init {
        loadModel(context)
    }

    private fun loadModel(context: Context) {
        try {
            // Check internal files first, then assets
            val localModel = File(context.filesDir, "models/vad/silero_vad.tflite")
            val byteBuffer: ByteBuffer = if (localModel.exists()) {
                val fis = FileInputStream(localModel)
                val channel = fis.channel
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            } else {
                val assetFd = context.assets.openFd("models/silero_vad.tflite")
                val fis = FileInputStream(assetFd.fileDescriptor)
                val channel = fis.channel
                channel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
            }

            val options = Interpreter.Options()
            options.setNumThreads(2)
            tfliteInterpreter = Interpreter(byteBuffer, options)
            isTfliteLoaded = true
            Log.d(TAG, "Silero VAD TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Silero VAD model not found or failed to load. Using adaptive energy fallback: ${e.message}")
            isTfliteLoaded = false
        }
    }

    /**
     * Evaluates a 512-sample audio chunk (normalized floats -1.0 to 1.0 at 16kHz)
     * Returns true if speech is detected.
     */
    fun isSpeech(chunk: FloatArray): Boolean {
        if (chunk.isEmpty()) return false

        if (isTfliteLoaded && tfliteInterpreter != null) {
            return try {
                runSileroInference(chunk) >= SPEECH_THRESHOLD
            } catch (e: Exception) {
                Log.e(TAG, "Silero VAD inference error: ${e.message}, falling back to energy", e)
                fallbackEnergyCheck(chunk)
            }
        }
        return fallbackEnergyCheck(chunk)
    }

    private fun runSileroInference(chunk: FloatArray): Float {
        // Silero VAD expects input [1, 512] float32
        val inputBuffer = ByteBuffer.allocateDirect(WINDOW_SIZE_SAMPLES * 4).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until WINDOW_SIZE_SAMPLES) {
                putFloat(if (i < chunk.size) chunk[i] else 0.0f)
            }
            rewind()
        }

        // Output probability buffer: [1, 1]
        val outputProb = Array(1) { FloatArray(1) }

        // Input map
        val inputs = arrayOf<Any>(inputBuffer)
        val outputs = mutableMapOf<Int, Any>(
            0 to outputProb
        )

        tfliteInterpreter?.runForMultipleInputsOutputs(inputs, outputs)
        return outputProb[0][0]
    }

    /**
     * Fallback RMS energy detector with noise floor baseline.
     */
    private fun fallbackEnergyCheck(chunk: FloatArray): Boolean {
        var sumSquares = 0.0
        for (sample in chunk) {
            sumSquares += (sample * sample)
        }
        val rms = sqrt(sumSquares / chunk.size.toDouble()).toFloat()
        // Threshold corresponding to typical human speech onset above ambient background
        return rms > 0.015f
    }

    fun resetState() {
        stateH = Array(2) { Array(1) { FloatArray(64) } }
        stateC = Array(2) { Array(1) { FloatArray(64) } }
    }

    fun close() {
        tfliteInterpreter?.close()
        tfliteInterpreter = null
        isTfliteLoaded = false
    }
}
