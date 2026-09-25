package com.itantra.app

import com.itantra.app.data.VoiceGender
import com.itantra.app.data.VoiceOption
import com.itantra.app.ml.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAndModelTest {

    @Test
    fun testVoiceOptions() {
        val voices = VoiceOption.ALL_VOICES
        assertEquals(5, voices.size)

        val priya = VoiceOption.fromId("voice_priya")
        assertEquals("Priya", priya.name)
        assertEquals(VoiceGender.FEMALE, priya.gender)
        assertEquals(0, priya.speakerId)

        val aarav = VoiceOption.fromId("voice_aarav")
        assertEquals("Aarav", aarav.name)
        assertEquals(VoiceGender.MALE, aarav.gender)
        assertEquals(1, aarav.speakerId)

        val vikram = VoiceOption.fromId("voice_vikram")
        assertEquals(1, vikram.speakerId)
        assertTrue(vikram.speedRate > 1.0f)
    }

    @Test
    fun testModelDownloadUrlsConfigured() {
        val models = ModelManager.AVAILABLE_MODELS
        assertTrue(models.isNotEmpty())

        for (model in models) {
            assertNotNull("Model ${model.id} must have download URL", model.downloadUrl)
            assertTrue("Download URL should start with http", model.downloadUrl!!.startsWith("http"))
        }
    }
}
