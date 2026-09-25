package com.itantra.app

import com.google.gson.Gson
import com.itantra.app.data.Message
import com.itantra.app.utils.LatencyTracker
import com.itantra.app.utils.PacketMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketMetricsTest {

    private val gson = Gson()

    @Test
    fun testJsonPacketSerialization() {
        val msg = Message(
            type = "MESSAGE",
            roomCode = "482731",
            senderId = "user_01",
            senderLang = "Hindi",
            receiverLang = "Hindi",
            text = "सभी टीमें सेक्टर चार में इकट्ठा हों",
            timestamp = 1718023400123L,
            isAlert = false
        )

        val json = gson.toJson(msg)
        assertTrue(json.contains("\"type\":\"MESSAGE\""))
        assertTrue(json.contains("\"room_code\":\"482731\""))
        assertTrue(json.contains("\"sender_lang\":\"Hindi\""))
        assertTrue(json.contains("सभी टीमें सेक्टर चार में इकट्ठा हों"))

        val deserialized = gson.fromJson(json, Message::class.java)
        assertEquals(msg.roomCode, deserialized.roomCode)
        assertEquals(msg.text, deserialized.text)
        assertEquals(msg.isAlert, deserialized.isAlert)
    }

    @Test
    fun testBandwidthMetricsCalculation() {
        // 3 seconds of 16kHz 16-bit audio = 3 * 16000 = 48000 samples -> 96,000 bytes
        val sampleCount = 48000
        val rawPcm = PacketMetrics.calculateRawPcmBytes(sampleCount)
        assertEquals(96000, rawPcm)

        val sampleJson = "{\"type\":\"MESSAGE\",\"text\":\"सभी टीमें सेक्टर चार में इकट्ठा हों\"}"
        val payloadBytes = PacketMetrics.calculatePayloadBytes(sampleJson)

        val saved = PacketMetrics.calculateBytesSaved(rawPcm, payloadBytes)
        val reduction = PacketMetrics.calculateReductionPercent(rawPcm, payloadBytes)

        assertTrue(payloadBytes < 200)
        assertTrue(saved > 95000)
        assertTrue(reduction > 99.0f)
    }

    @Test
    fun testLatencyTrackerCalculations() {
        val tracker = LatencyTracker()
        tracker.recordStt(820L)
        tracker.recordNetwork(1000L, 1025L) // 25ms
        tracker.recordTranslation(0L, skipped = true)
        tracker.recordTts(640L)

        val e2e = tracker.calculateE2e()
        assertEquals(820L + 25L + 0L + 640L, e2e)
        assertEquals(1485L, e2e)
    }
}
