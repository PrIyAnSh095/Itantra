package com.itantra.app.network

import com.itantra.app.data.ConnectionState
import com.itantra.app.data.Message

/**
 * Common interface for all iTantra transport mechanisms (TCP Socket & Bluetooth RFCOMM).
 */
interface NetworkTransport {

    fun startHost(
        roomCode: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    )

    fun joinRoom(
        roomCode: String,
        hostAddress: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    )

    fun sendMessage(message: Message)

    fun disconnect()

    fun isConnected(): Boolean

    fun getTransportName(): String
}
