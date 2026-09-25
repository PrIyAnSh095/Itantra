package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.itantra.app.data.CommunicationLanguage
import java.io.FileInputStream
import java.nio.FloatBuffer

/**
 * Fallback Speech-To-Text Engine using OpenAI Whisper Small (quantized INT8 ONNX).
 * Used for English and as fallback where IndicConformer data is sparse.
 */
class WhisperSttEngine(
    private val context: Context,
    private val modelManager: ModelManager
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "WhisperSttEngine"
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Synchronized
    fun loadModel(language: CommunicationLanguage): Boolean {
        if (session != null) return true

        val fileName = language.sttModelFile
        return try {
            val file = modelManager.getModelFile(fileName)
            val modelBytes = if (file != null && file.exists()) {
                FileInputStream(file).use { it.readBytes() }
            } else {
                try {
                    context.assets.open("models/$fileName").use { it.readBytes() }
                } catch (e: Exception) {
                    null
                }
            } ?: return false

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = env.createSession(modelBytes, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Whisper ONNX session initialization failed: ${e.message}")
            false
        }
    }

    override fun transcribe(audioData: FloatArray, language: CommunicationLanguage): String {
        if (audioData.isEmpty()) return ""
        if (!loadModel(language)) return ""

        val currentSession = session ?: return ""

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            val inputName = currentSession.inputNames.firstOrNull() ?: "input"
            val shape = longArrayOf(1, audioData.size.toLong())
            inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(audioData), shape)

            results = currentSession.run(mapOf(inputName to inputTensor))
            val output = results[0].value
            if (output is String) output else output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Whisper transcription error: ${e.message}")
            ""
        } finally {
            try {
                inputTensor?.close()
                results?.close()
            } catch (ignored: Exception) {}
        }
    }

    override fun isModelLoaded(): Boolean = session != null

    override fun close() {
        try {
            session?.close()
        } catch (ignored: Exception) {}
        session = null
    }
}
