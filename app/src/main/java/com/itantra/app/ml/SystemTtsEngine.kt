package com.itantra.app.ml

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.data.VoiceOption
import java.util.Locale

/**
 * System TextToSpeech engine wrapping Android's built-in android.speech.tts.TextToSpeech.
 * Provides instant multi-voice playback and serves as a seamless fallback
 * when ONNX models are not yet downloaded on the device.
 */
class SystemTtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SystemTtsEngine"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingActions = mutableListOf<() -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d(TAG, "System TextToSpeech initialized successfully")
            synchronized(pendingActions) {
                pendingActions.forEach { it.invoke() }
                pendingActions.clear()
            }
        } else {
            Log.e(TAG, "Failed to initialize System TextToSpeech: status=$status")
            isInitialized = false
        }
    }

    /**
     * Speaks the given text using the selected VoiceOption (pitch, rate, gender)
     * and the appropriate communication language locale.
     */
    fun speak(
        text: String,
        language: CommunicationLanguage,
        voiceOption: VoiceOption,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized) {
            synchronized(pendingActions) {
                pendingActions.add {
                    speak(text, language, voiceOption, onStart, onDone)
                }
            }
            return
        }

        val ttsEngine = tts ?: run {
            onDone?.invoke()
            return
        }

        val targetLocale = when (language) {
            CommunicationLanguage.HINDI -> Locale("hi", "IN")
            CommunicationLanguage.GUJARATI -> Locale("gu", "IN")
            CommunicationLanguage.ENGLISH -> Locale("en", "US")
        }

        val langResult = ttsEngine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $targetLocale not fully supported, falling back to default locale")
            ttsEngine.setLanguage(Locale.getDefault())
        }

        // Apply pitch and rate from the chosen VoiceOption
        ttsEngine.setPitch(voiceOption.pitch)
        ttsEngine.setSpeechRate(voiceOption.speedRate)

        // Try to match voice by gender if supported by device engine
        try {
            val availableVoices = ttsEngine.voices
            if (!availableVoices.isNullOrEmpty()) {
                val matchedVoice = availableVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    val matchesLang = voice.locale.language.equals(targetLocale.language, ignoreCase = true)
                    val matchesGender = if (voiceOption.gender == com.itantra.app.data.VoiceGender.FEMALE) {
                        name.contains("female") || name.contains("fem") || name.contains("f0")
                    } else {
                        name.contains("male") || name.contains("masc") || name.contains("m0")
                    }
                    matchesLang && matchesGender
                } ?: availableVoices.firstOrNull { it.locale.language.equals(targetLocale.language, ignoreCase = true) }

                if (matchedVoice != null) {
                    ttsEngine.voice = matchedVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection error: ${e.message}")
        }

        val utteranceId = "utantra_tts_${System.currentTimeMillis()}"
        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) onStart?.invoke()
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) onDone?.invoke()
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) onDone?.invoke()
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (ignored: Exception) {}
    }

    fun close() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (ignored: Exception) {}
        tts = null
        isInitialized = false
    }
}
