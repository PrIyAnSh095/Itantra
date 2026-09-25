package com.itantra.app.data

data class ModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val expectedSizeMb: Int,
    val isInstalled: Boolean,
    val isBundled: Boolean,
    val languageCode: String,
    val type: ModelType,
    val downloadUrl: String? = null,
    val downloadState: ModelDownloadState = ModelDownloadState.IDLE,
    val downloadProgress: Int = 0
)

enum class ModelType {
    VAD,
    STT,
    TTS,
    TRANSLATION
}

enum class ModelDownloadState {
    IDLE,
    DOWNLOADING,
    INSTALLED,
    ERROR
}

data class PerformanceMetrics(
    val sttLatencyMs: Long = 0L,
    val networkLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L,
    val translationLatencyMs: Long = 0L,
    val translationSkipped: Boolean = true,
    val e2eLatencyMs: Long = 0L,
    val payloadBytes: Int = 0,
    val rawPcmBytes: Int = 0,
    val reductionPercent: Float = 0.0f
)
