package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.itantra.app.data.CommunicationLanguage
import java.io.File
import java.io.FileInputStream
import java.nio.FloatBuffer

/**
 * AI4Bharat IndicConformer Speech-To-Text Engine running on ONNX Runtime (CPU-first).
 * Operates on 16kHz mono PCM float audio (-1.0 to 1.0).
 */
class IndicConformerSttEngine(
    private val context: Context,
    private val modelManager: ModelManager
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "IndicConformerStt"

        // Default Devanagari character mapping for IndicConformer CTC decoder
        private val HINDI_VOCAB: Map<Int, Char> = mapOf(
            1 to ' ', 2 to 'अ', 3 to 'आ', 4 to 'इ', 5 to 'ई', 6 to 'उ', 7 to 'ऊ', 8 to 'ऋ',
            9 to 'ए', 10 to 'ऐ', 11 to 'ओ', 12 to 'औ', 13 to 'क', 14 to 'ख', 15 to 'ग',
            16 to 'घ', 17 to 'ङ', 18 to 'च', 19 to 'छ', 20 to 'ज', 21 to 'झ', 22 to 'ञ',
            23 to 'ट', 24 to 'ठ', 25 to 'ड', 26 to 'ढ', 27 to 'ण', 28 to 'त', 29 to 'थ',
            30 to 'द', 31 to 'ध', 32 to 'न', 33 to 'प', 34 to 'फ', 35 to 'ब', 36 to 'भ',
            37 to 'म', 38 to 'य', 39 to 'र', 40 to 'ल', 41 to 'व', 42 to 'श', 43 to 'ष',
            44 to 'स', 45 to 'ह', 46 to 'ा', 47 to 'ि', 48 to 'ी', 49 to 'ु', 50 to 'ू',
            51 to 'ृ', 52 to 'े', 53 to 'ै', 54 to 'ो', 55 to 'ौ', 56 to '्', 57 to 'ं',
            58 to 'ः', 59 to 'ँ', 60 to '़'
        )
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var loadedLanguage: CommunicationLanguage? = null

    /**
     * Lazily loads or switches model for the specified language.
     */
    @Synchronized
    fun loadModel(language: CommunicationLanguage): Boolean {
        if (session != null && loadedLanguage == language) {
            return true
        }

        close()

        val fileName = language.sttModelFile
        return try {
            val modelBytes: ByteArray = getModelBytes(fileName) ?: run {
                Log.w(TAG, "STT model file $fileName not found on device or in assets.")
                return false
            }

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }

            session = env.createSession(modelBytes, options)
            loadedLanguage = language
            Log.d(TAG, "IndicConformer STT session created for ${language.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize IndicConformer ONNX session: ${e.message}", e)
            session = null
            loadedLanguage = null
            false
        }
    }

    private fun getModelBytes(fileName: String): ByteArray? {
        val file = modelManager.getModelFile(fileName)
        if (file != null && file.exists()) {
            return FileInputStream(file).use { it.readBytes() }
        }
        return try {
            context.assets.open("models/$fileName").use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun transcribe(audioData: FloatArray, language: CommunicationLanguage): String {
        if (audioData.isEmpty()) return ""

        if (!loadModel(language)) {
            Log.w(TAG, "STT Model not available for ${language.displayName}")
            return ""
        }

        val currentSession = session ?: return ""

        val startTime = System.currentTimeMillis()
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            // Find input tensor name dynamically from session metadata
            val inputName = currentSession.inputNames.firstOrNull() ?: "input"

            val shape = longArrayOf(1, audioData.size.toLong())
            inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioData),
                shape
            )

            val inputs = mapOf(inputName to inputTensor)
            results = currentSession.run(inputs)

            val outputValue = results[0].value
            val decodedText = when (outputValue) {
                is String -> outputValue
                is Array<*> -> decodeCtcOutput(outputValue)
                else -> ""
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "STT Transcribed in ${elapsed}ms: '$decodedText'")
            decodedText

        } catch (e: Exception) {
            Log.e(TAG, "STT inference error: ${e.message}", e)
            ""
        } finally {
            try {
                inputTensor?.close()
                results?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * CTC Greedy Decoder: finds argmax at each frame and suppresses consecutive duplicates
     * and blank token (index 0).
     */
    private fun decodeCtcOutput(outputArray: Array<*>): String {
        val sb = StringBuilder()
        var prevToken = -1

        try {
            // Typical shape: [1, time_steps, vocab_size] -> Array<Array<FloatArray>>
            @Suppress("UNCHECKED_CAST")
            val frames = (outputArray[0] as? Array<FloatArray>) ?: return ""

            for (frame in frames) {
                var maxIdx = 0
                var maxVal = frame[0]
                for (i in 1 until frame.size) {
                    if (frame[i] > maxVal) {
                        maxVal = frame[i]
                        maxIdx = i
                    }
                }

                if (maxIdx != 0 && maxIdx != prevToken) {
                    val ch = HINDI_VOCAB[maxIdx]
                    if (ch != null) {
                        sb.append(ch)
                    }
                }
                prevToken = maxIdx
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error decoding CTC output: ${e.message}")
        }
        return sb.toString().trim()
    }

    override fun isModelLoaded(): Boolean = session != null

    override fun close() {
        try {
            session?.close()
        } catch (ignored: Exception) {}
        session = null
        loadedLanguage = null
    }
}
