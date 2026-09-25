package com.itantra.app.utils

class LatencyTracker {

    var sttDurationMs: Long = 0L
        private set

    var networkDurationMs: Long = 0L
        private set

    var ttsDurationMs: Long = 0L
        private set

    var translationDurationMs: Long = 0L
        private set

    var translationSkipped: Boolean = true
        private set

    fun recordStt(durationMs: Long) {
        this.sttDurationMs = durationMs
    }

    fun recordNetwork(sendTimestamp: Long, receiveTimestamp: Long = System.currentTimeMillis()) {
        val diff = receiveTimestamp - sendTimestamp
        this.networkDurationMs = if (diff >= 0) diff else 10L
    }

    fun recordTts(durationMs: Long) {
        this.ttsDurationMs = durationMs
    }

    fun recordTranslation(durationMs: Long, skipped: Boolean = false) {
        this.translationDurationMs = durationMs
        this.translationSkipped = skipped
    }

    fun calculateE2e(): Long {
        val trans = if (translationSkipped) 0L else translationDurationMs
        return sttDurationMs + networkDurationMs + trans + ttsDurationMs
    }

    fun reset() {
        sttDurationMs = 0L
        networkDurationMs = 0L
        ttsDurationMs = 0L
        translationDurationMs = 0L
        translationSkipped = true
    }
}
