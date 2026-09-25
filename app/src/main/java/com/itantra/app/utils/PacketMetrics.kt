package com.itantra.app.utils

object PacketMetrics {

    /**
     * Calculates the raw PCM byte size of 16-bit Mono audio.
     * Each 16-bit sample is 2 bytes.
     */
    fun calculateRawPcmBytes(sampleCount: Int): Int {
        return sampleCount * 2
    }

    /**
     * Calculates the actual byte size of the UTF-8 encoded JSON payload.
     */
    fun calculatePayloadBytes(jsonString: String): Int {
        return jsonString.toByteArray(Charsets.UTF_8).size
    }

    /**
     * Calculates the bytes saved by sending text instead of raw PCM audio.
     */
    fun calculateBytesSaved(rawPcmBytes: Int, payloadBytes: Int): Int {
        return (rawPcmBytes - payloadBytes).coerceAtLeast(0)
    }

    /**
     * Calculates the bandwidth reduction percentage.
     * e.g., (1 - payloadBytes / rawPcmBytes) * 100
     */
    fun calculateReductionPercent(rawPcmBytes: Int, payloadBytes: Int): Float {
        if (rawPcmBytes <= 0 || payloadBytes <= 0) return 0.0f
        val reduction = (1.0f - (payloadBytes.toFloat() / rawPcmBytes.toFloat())) * 100.0f
        return reduction.coerceIn(0.0f, 99.99f)
    }
}
