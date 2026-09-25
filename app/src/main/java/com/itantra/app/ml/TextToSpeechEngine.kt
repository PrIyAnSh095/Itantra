package com.itantra.app.ml

import com.itantra.app.data.CommunicationLanguage

/**
 * Common abstraction for Text-To-Speech synthesis engines.
 */
interface TextToSpeechEngine {
    /**
     * Synthesizes text into a float waveform (-1.0 to 1.0) ready for AudioTrack playback.
     */
    fun synthesize(text: String, language: CommunicationLanguage): FloatArray

    /**
     * The output audio sample rate in Hz (e.g. 22050 Hz for IndicTTS VITS).
     */
    fun getSampleRate(): Int

    /**
     * Returns true if model session is initialized and ready.
     */
    fun isModelLoaded(): Boolean

    /**
     * Releases ONNX runtime sessions and memory.
     */
    fun close()
}
