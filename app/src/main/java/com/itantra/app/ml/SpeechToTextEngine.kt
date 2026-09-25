package com.itantra.app.ml

import com.itantra.app.data.CommunicationLanguage

/**
 * Common abstraction for Speech-To-Text engines.
 */
interface SpeechToTextEngine {
    /**
     * Transcribes 16kHz normalized PCM audio floats (-1.0 to 1.0) into unicode text.
     */
    fun transcribe(audioData: FloatArray, language: CommunicationLanguage): String

    /**
     * Returns true if the model session is initialized and ready for inference.
     */
    fun isModelLoaded(): Boolean

    /**
     * Releases ONNX runtime sessions and memory.
     */
    fun close()
}
