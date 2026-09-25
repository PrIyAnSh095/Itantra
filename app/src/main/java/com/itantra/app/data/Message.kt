package com.itantra.app.data

import com.google.gson.annotations.SerializedName

/**
 * Core JSON Message Packet transmitted over TCP Socket or Bluetooth RFCOMM.
 * Conforms to the iTantra specification:
 * {
 *   "type": "MESSAGE",
 *   "room_code": "482731",
 *   "sender_id": "user_01",
 *   "sender_lang": "Hindi",
 *   "receiver_lang": "Hindi",
 *   "text": "सभी को सतर्क रहना है",
 *   "timestamp": 1718023400123,
 *   "is_alert": false
 * }
 */
data class Message(
    @SerializedName("type")
    val type: String = "MESSAGE",

    @SerializedName("room_code")
    val roomCode: String,

    @SerializedName("sender_id")
    val senderId: String,

    @SerializedName("sender_lang")
    val senderLang: String,

    @SerializedName("receiver_lang")
    val receiverLang: String? = null,

    @SerializedName("text")
    val text: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("is_alert")
    val isAlert: Boolean = false
)

/**
 * Handshake packet sent by clients when joining a room.
 */
data class JoinPacket(
    @SerializedName("type")
    val type: String = "JOIN",

    @SerializedName("room_code")
    val roomCode: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("language")
    val language: String,

    @SerializedName("device_name")
    val deviceName: String
)
