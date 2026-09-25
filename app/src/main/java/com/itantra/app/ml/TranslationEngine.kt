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
 * AI4Bharat IndicTrans2 Distilled Machine Translation Engine (ONNX).
 *
 * Crucial architecture optimization:
 * - sender_lang == receiver_lang -> SKIP entirely (zero latency)
 * - sender_lang != receiver_lang -> Run IndicTrans2
 *
 * Lazy-loaded: model is never loaded into RAM unless a cross-language message arrives.
 */
class TranslationEngine(
    private val context: Context,
    private val modelManager: ModelManager
) {

    companion object {
        private const val TAG = "TranslationEngine"
        private const val MODEL_FILE = "indictrans2.onnx"
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var isModelLoading = false

    @Synchronized
    fun loadModel(): Boolean {
        if (session != null) return true
        if (isModelLoading) return false

        isModelLoading = true
        return try {
            val file = modelManager.getModelFile(MODEL_FILE)
            val modelBytes = if (file != null && file.exists()) {
                FileInputStream(file).use { it.readBytes() }
            } else {
                try {
                    context.assets.open("models/$MODEL_FILE").use { it.readBytes() }
                } catch (e: Exception) {
                    null
                }
            }

            if (modelBytes == null) {
                Log.d(TAG, "IndicTrans2 model not installed. Skipping or passing through.")
                isModelLoading = false
                return false
            }

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = env.createSession(modelBytes, options)
            isModelLoading = false
            Log.d(TAG, "IndicTrans2 translation session loaded into memory.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load IndicTrans2 session: ${e.message}")
            isModelLoading = false
            false
        }
    }

    /**
     * Translates text from source language to target language.
     * If source and target languages match, returns text immediately without translation.
     */
    fun translate(
        text: String,
        sourceLang: CommunicationLanguage,
        targetLang: CommunicationLanguage
    ): String {
        if (text.isBlank()) return text

        // Zero-latency path: same language
        if (sourceLang == targetLang) {
            return text
        }

        // Cross-language path
        if (!loadModel()) {
            Log.w(TAG, "Translation model unavailable. Returning original text.")
            return text
        }

        val currentSession = session ?: return text
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            val tokens = text.map { it.code.toLong() }.toLongArray()
            val inputName = currentSession.inputNames.firstOrNull() ?: "input_ids"
            inputTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokens),
                longArrayOf(1, tokens.size.toLong())
            )

            results = currentSession.run(mapOf(inputName to inputTensor))
            val output = results[0].value
            if (output is String) output else text
        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)
            text
        } finally {
            try {
                inputTensor?.close()
                results?.close()
            } catch (ignored: Exception) {}
        }
    }

    fun isModelLoaded(): Boolean = session != null

    fun close() {
        try {
            session?.close()
        } catch (ignored: Exception) {}
        session = null
        isModelLoading = false
    }
}
