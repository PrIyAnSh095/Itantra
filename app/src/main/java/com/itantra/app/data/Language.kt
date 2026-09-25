package com.itantra.app.data

/**
 * Supported App UI Languages.
 * Controls every visible string in the interface.
 */
enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

/**
 * Supported Communication Languages for Speech-to-Text and Text-to-Speech.
 * Separate from the UI language.
 */
enum class CommunicationLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val sttModelFile: String,
    val ttsModelFile: String
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        sttModelFile = "stt_en.onnx",
        ttsModelFile = "tts_en.onnx"
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeName = "हिन्दी",
        sttModelFile = "stt_hi.onnx",
        ttsModelFile = "tts_hi.onnx"
    ),
    GUJARATI(
        code = "gu",
        displayName = "Gujarati",
        nativeName = "ગુજરાતી",
        sttModelFile = "stt_gu.onnx",
        ttsModelFile = "tts_gu.onnx"
    );

    companion object {
        fun fromCode(code: String): CommunicationLanguage {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }

        fun fromDisplayName(name: String): CommunicationLanguage {
            return values().firstOrNull {
                it.displayName.equals(name, ignoreCase = true) ||
                        it.nativeName.equals(name, ignoreCase = true) ||
                        it.code.equals(name, ignoreCase = true)
            } ?: HINDI
        }
    }
}
