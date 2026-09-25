package com.itantra.app.network

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.itantra.app.data.ConnectionState
import com.itantra.app.data.JoinPacket
import com.itantra.app.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Primary networking transport using Android Wi-Fi Hotspot and raw TCP Sockets.
 * Port: 54321
 * Host Default IP: 192.168.43.1
 */
class TcpTransport : NetworkTransport {

    companion object {
        private const val TAG = "TcpTransport"
        const val PORT = 54321
        const val DEFAULT_HOTSPOT_HOST_IP = "192.168.43.1"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Server state
    private var serverSocket: ServerSocket? = null
    private val connectedClients = CopyOnWriteArrayList<ClientHandler>()
    private var serverJob: Job? = null

    // Client state
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null
    private var clientJob: Job? = null

    private var isHosting = false
    private var isConnectedFlag = false
    private var activeRoomCode: String = ""

    override fun startHost(
        roomCode: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    ) {
        disconnect()
        isHosting = true
        activeRoomCode = roomCode

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                isConnectedFlag = true
                val hostIp = resolveLocalIpAddress()
                Log.d(TAG, "Server started on $hostIp:$PORT for room $roomCode")
                onStateChanged(ConnectionState.CONNECTED, hostIp)

                while (isActive && !serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    val handler = ClientHandler(socket, roomCode, onMessageReceived) { disconnectedHandler ->
                        connectedClients.remove(disconnectedHandler)
                    }
                    connectedClients.add(handler)
                    handler.start()
                }
            } catch (e: Exception) {
                if (isHosting) {
                    Log.e(TAG, "Server socket error: ${e.message}", e)
                    isConnectedFlag = false
                    onStateChanged(ConnectionState.ERROR, e.message ?: "Server error")
                }
            }
        }
    }

    override fun joinRoom(
        roomCode: String,
        hostAddress: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    ) {
        disconnect()
        isHosting = false
        activeRoomCode = roomCode
        val targetIp = if (hostAddress.isBlank()) DEFAULT_HOTSPOT_HOST_IP else hostAddress.trim()

        clientJob = scope.launch {
            try {
                onStateChanged(ConnectionState.CONNECTING, targetIp)
                val socket = Socket(targetIp, PORT)
                clientSocket = socket
                clientWriter = PrintWriter(socket.getOutputStream(), true)
                isConnectedFlag = true
                onStateChanged(ConnectionState.CONNECTED, targetIp)

                // Send initial handshake JOIN packet
                val joinPacket = JoinPacket(
                    type = "JOIN",
                    roomCode = roomCode,
                    userId = "user_${Build.MODEL.replace(" ", "_")}",
                    language = "Hindi",
                    deviceName = Build.MODEL
                )
                clientWriter?.println(gson.toJson(joinPacket))

                // Listen for incoming messages
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val raw = reader.readLine() ?: break
                    try {
                        val message = gson.fromJson(raw, Message::class.java)
                        if (message.roomCode.isBlank() || message.roomCode == activeRoomCode) {
                            onMessageReceived(message)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Ignoring non-message packet: $raw")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client socket error: ${e.message}", e)
                isConnectedFlag = false
                onStateChanged(ConnectionState.ERROR, e.message ?: "Connection failed")
            } finally {
                isConnectedFlag = false
            }
        }
    }

    override fun sendMessage(message: Message) {
        val json = gson.toJson(message)
        scope.launch {
            try {
                if (isHosting) {
                    // Host broadcasts to all connected guests
                    connectedClients.forEach { client ->
                        client.send(json)
                    }
                } else {
                    // Guest sends to host
                    clientWriter?.println(json)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending packet: ${e.message}", e)
            }
        }
    }

    override fun disconnect() {
        isConnectedFlag = false
        isHosting = false
        activeRoomCode = ""

        try {
            serverJob?.cancel()
            serverSocket?.close()
            connectedClients.forEach { it.close() }
            connectedClients.clear()
        } catch (ignored: Exception) {}
        serverSocket = null

        try {
            clientJob?.cancel()
            clientWriter?.close()
            clientSocket?.close()
        } catch (ignored: Exception) {}
        clientSocket = null
        clientWriter = null
    }

    override fun isConnected(): Boolean = isConnectedFlag

    override fun getTransportName(): String = "Wi-Fi Hotspot"

    fun getConnectedClientsCount(): Int = connectedClients.size

    private fun resolveLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                // Hotspot interface is typically wlan1, ap0, or wlan0
                if (iface.isUp && !iface.isLoopback) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10."))) {
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return DEFAULT_HOTSPOT_HOST_IP
    }

    /**
     * Inner handler for each connected client on the server.
     */
    private inner class ClientHandler(
        private val socket: Socket,
        private val roomCode: String,
        private val onMessageReceived: (Message) -> Unit,
        private val onDisconnected: (ClientHandler) -> Unit
    ) {
        private var writer: PrintWriter? = null
        private var job: Job? = null

        fun start() {
            job = scope.launch {
                try {
                    writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    while (isActive) {
                        val raw = reader.readLine() ?: break
                        try {
                            val msg = gson.fromJson(raw, Message::class.java)
                            if (msg.type == "MESSAGE") {
                                // Relay to other clients
                                connectedClients.forEach { other ->
                                    if (other != this@ClientHandler) {
                                        other.send(raw)
                                    }
                                }
                                // Notify host
                                onMessageReceived(msg)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Server received non-message: $raw")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Client disconnected: ${e.message}")
                } finally {
                    close()
                    onDisconnected(this@ClientHandler)
                }
            }
        }

        fun send(rawJson: String) {
            try {
                writer?.println(rawJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to client: ${e.message}")
            }
        }

        fun close() {
            try {
                job?.cancel()
                writer?.close()
                socket.close()
            } catch (ignored: Exception) {}
        }
    }
}
