package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.itantra.app.data.CommunicationLanguage
import java.io.FileInputStream
import java.nio.LongBuffer

/**
 * Coqui VITS English Text-To-Speech Engine running on ONNX Runtime.
 */
class CoquiTtsEngine(
    private val context: Context,
    private val modelManager: ModelManager
) : TextToSpeechEngine {

    companion object {
        private const val TAG = "CoquiTtsEngine"
        const val SAMPLE_RATE = 22050
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Synchronized
    fun loadModel(language: CommunicationLanguage): Boolean {
        if (session != null) return true

        val fileName = language.ttsModelFile
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
            Log.e(TAG, "Coqui VITS model loading failed: ${e.message}")
            false
        }
    }

    override fun synthesize(text: String, language: CommunicationLanguage): FloatArray {
        if (text.isBlank()) return FloatArray(0)
        if (!loadModel(language)) return FloatArray(0)

        val currentSession = session ?: return FloatArray(0)
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            val tokenIds = text.map { it.code.toLong() }.toLongArray()
            val inputName = currentSession.inputNames.firstOrNull() ?: "input_ids"
            inputTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenIds),
                longArrayOf(1, tokenIds.size.toLong())
            )

            results = currentSession.run(mapOf(inputName to inputTensor))
            val audioOutput = results[0].value
            if (audioOutput is Array<*> && audioOutput.isNotEmpty() && audioOutput[0] is FloatArray) {
                audioOutput[0] as FloatArray
            } else {
                FloatArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Coqui TTS error: ${e.message}")
            FloatArray(0)
        } finally {
            try {
                inputTensor?.close()
                results?.close()
            } catch (ignored: Exception) {}
        }
    }

    override fun getSampleRate(): Int = SAMPLE_RATE

    override fun isModelLoaded(): Boolean = session != null

    override fun close() {
        try {
            session?.close()
        } catch (ignored: Exception) {}
        session = null
    }
}
