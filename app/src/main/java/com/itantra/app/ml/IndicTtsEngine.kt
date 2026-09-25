package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.itantra.app.data.CommunicationLanguage
import java.io.FileInputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * AI4Bharat IndicTTS VITS Text-To-Speech Engine running on ONNX Runtime (CPU-first).
 * Synthesizes Unicode text directly into audio waveform at 22050 Hz.
 */
class IndicTtsEngine(
    private val context: Context,
    private val modelManager: ModelManager
) : TextToSpeechEngine {

    companion object {
        private const val TAG = "IndicTtsEngine"
        const val SAMPLE_RATE = 22050
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var loadedLanguage: CommunicationLanguage? = null
    var currentSpeakerId: Int = 0

    @Synchronized
    fun loadModel(language: CommunicationLanguage): Boolean {
        if (session != null && loadedLanguage == language) {
            return true
        }

        close()

        val fileName = language.ttsModelFile
        return try {
            val modelBytes: ByteArray = getModelBytes(fileName) ?: run {
                Log.w(TAG, "TTS model file $fileName not found.")
                return false
            }

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }

            session = env.createSession(modelBytes, options)
            loadedLanguage = language
            Log.d(TAG, "IndicTTS session created for ${language.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load IndicTTS model: ${e.message}", e)
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

    override fun synthesize(text: String, language: CommunicationLanguage): FloatArray {
        if (text.isBlank()) return FloatArray(0)

        if (!loadModel(language)) {
            Log.w(TAG, "TTS Model not loaded for ${language.displayName}")
            return FloatArray(0)
        }

        val currentSession = session ?: return FloatArray(0)
        val startTime = System.currentTimeMillis()

        val allocatedTensors = mutableListOf<OnnxTensor>()
        var results: OrtSession.Result? = null

        return try {
            val tokenIds = tokenize(text)
            val inputMap = mutableMapOf<String, OnnxTensor>()

            val inputNames = currentSession.inputNames

            // Match text input ids tensor
            val textInputName = inputNames.firstOrNull {
                it.contains("text", ignoreCase = true) ||
                        it.contains("input_ids", ignoreCase = true) ||
                        it.contains("input", ignoreCase = true)
            } ?: inputNames.firstOrNull() ?: "input_ids"

            val textTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenIds),
                longArrayOf(1, tokenIds.size.toLong())
            )
            allocatedTensors.add(textTensor)
            inputMap[textInputName] = textTensor

            // Provide length tensor if expected by VITS architecture
            val textLengthsName = inputNames.firstOrNull {
                it.contains("length", ignoreCase = true) || it.contains("text_lengths", ignoreCase = true)
            }
            if (textLengthsName != null) {
                val lengthTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(tokenIds.size.toLong())),
                    longArrayOf(1)
                )
                allocatedTensors.add(lengthTensor)
                inputMap[textLengthsName] = lengthTensor
            }

            // Provide scales tensor if expected by VITS [noise_scale, length_scale, noise_scale_w]
            val scalesName = inputNames.firstOrNull { it.contains("scale", ignoreCase = true) }
            if (scalesName != null) {
                val scalesTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatArrayOf(0.667f, 1.0f, 0.8f)),
                    longArrayOf(3)
                )
                allocatedTensors.add(scalesTensor)
                inputMap[scalesName] = scalesTensor
            }

            // Provide speaker id (sid) if expected
            val sidName = inputNames.firstOrNull { it.equals("sid", ignoreCase = true) }
            if (sidName != null) {
                val sidTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(currentSpeakerId.toLong())),
                    longArrayOf(1)
                )
                allocatedTensors.add(sidTensor)
                inputMap[sidName] = sidTensor
            }

            results = currentSession.run(inputMap)
            val audioOutput = extractAudioFloatArray(results[0].value)

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Synthesized ${audioOutput.size} samples in ${elapsed}ms for text '$text'")
            audioOutput

        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis error: ${e.message}", e)
            FloatArray(0)
        } finally {
            for (tensor in allocatedTensors) {
                try { tensor.close() } catch (ignored: Exception) {}
            }
            try { results?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Extracts float audio samples from various ONNX model output structures:
     * float[][][], float[][], float[], etc.
     */
    private fun extractAudioFloatArray(value: Any?): FloatArray {
        if (value == null) return FloatArray(0)
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                if (value.isNotEmpty() && value[0] is FloatArray) {
                    value[0] as FloatArray
                } else if (value.isNotEmpty() && value[0] is Array<*>) {
                    val sub = value[0] as Array<*>
                    if (sub.isNotEmpty() && sub[0] is FloatArray) {
                        sub[0] as FloatArray
                    } else FloatArray(0)
                } else FloatArray(0)
            }
            else -> FloatArray(0)
        }
    }

    /**
     * Indic character-level tokenizer.
     * Maps Unicode characters to stable token identifiers.
     */
    private fun tokenize(text: String): LongArray {
        val tokens = mutableListOf<Long>()
        // 0 is reserved for padding/blank
        for (char in text) {
            val code = char.code.toLong()
            tokens.add(code)
        }
        return tokens.toLongArray()
    }

    override fun getSampleRate(): Int = SAMPLE_RATE

    override fun isModelLoaded(): Boolean = session != null

    override fun close() {
        try {
            session?.close()
        } catch (ignored: Exception) {}
        session = null
        loadedLanguage = null
    }
}
