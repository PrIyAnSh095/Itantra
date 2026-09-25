package com.itantra.app.data

enum class VoiceGender {
    FEMALE,
    MALE
}

data class VoiceOption(
    val id: String,
    val name: String,
    val gender: VoiceGender,
    val description: String,
    val speakerId: Int = 0,
    val pitch: Float = 1.0f,
    val speedRate: Float = 1.0f
) {
    val displayLabel: String
        get() = "$name (${if (gender == VoiceGender.FEMALE) "Female" else "Male"})"

    companion object {
        val PRIYA = VoiceOption(
            id = "voice_priya",
            name = "Priya",
            gender = VoiceGender.FEMALE,
            description = "Natural, warm & clear tone (Standard)",
            speakerId = 0,
            pitch = 1.05f,
            speedRate = 1.0f
        )

        val AARAV = VoiceOption(
            id = "voice_aarav",
            name = "Aarav",
            gender = VoiceGender.MALE,
            description = "Deep, resonant & authoritative",
            speakerId = 1,
            pitch = 0.85f,
            speedRate = 0.98f
        )

        val ANANYA = VoiceOption(
            id = "voice_ananya",
            name = "Ananya",
            gender = VoiceGender.FEMALE,
            description = "Crisp, bright & articulate",
            speakerId = 0,
            pitch = 1.20f,
            speedRate = 1.05f
        )

        val VIKRAM = VoiceOption(
            id = "voice_vikram",
            name = "Vikram",
            gender = VoiceGender.MALE,
            description = "Radio / Tactical dispatch persona",
            speakerId = 1,
            pitch = 0.90f,
            speedRate = 1.15f
        )

        val KAVITA = VoiceOption(
            id = "voice_kavita",
            name = "Kavita",
            gender = VoiceGender.FEMALE,
            description = "Soft, calm & conversational",
            speakerId = 0,
            pitch = 0.95f,
            speedRate = 0.92f
        )

        val ALL_VOICES = listOf(PRIYA, AARAV, ANANYA, VIKRAM, KAVITA)

        fun fromId(id: String?): VoiceOption {
            return ALL_VOICES.firstOrNull { it.id == id } ?: PRIYA
        }
    }
}
