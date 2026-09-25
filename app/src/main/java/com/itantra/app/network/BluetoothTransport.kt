package com.itantra.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.gson.Gson
import com.itantra.app.data.ConnectionState
import com.itantra.app.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

/**
 * Fallback point-to-point networking transport using Bluetooth Classic RFCOMM / SPP.
 * Uses standard Serial Port Profile UUID: 00001101-0000-1000-8000-00805F9B34FB
 */
class BluetoothTransport : NetworkTransport {

    companion object {
        private const val TAG = "BluetoothTransport"
        private const val SERVICE_NAME = "iTantraVoice"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var socketWriter: PrintWriter? = null
    private var workerJob: Job? = null

    private var isConnectedFlag = false
    private var activeRoomCode: String = ""

    @SuppressLint("MissingPermission")
    override fun startHost(
        roomCode: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    ) {
        disconnect()
        activeRoomCode = roomCode

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onStateChanged(ConnectionState.ERROR, "Bluetooth is disabled or unavailable")
            return
        }

        workerJob = scope.launch {
            try {
                onStateChanged(ConnectionState.CONNECTING, "Listening for Bluetooth connection...")
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                val socket = serverSocket!!.accept()
                serverSocket?.close()
                serverSocket = null

                activeSocket = socket
                socketWriter = PrintWriter(socket.getOutputStream(), true)
                isConnectedFlag = true
                onStateChanged(ConnectionState.CONNECTED, socket.remoteDevice.name ?: "Peer Device")

                listenForIncoming(socket, onMessageReceived)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth host error: ${e.message}", e)
                isConnectedFlag = false
                onStateChanged(ConnectionState.ERROR, e.message ?: "Bluetooth host error")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun joinRoom(
        roomCode: String,
        hostAddress: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    ) {
        disconnect()
        activeRoomCode = roomCode

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onStateChanged(ConnectionState.ERROR, "Bluetooth is disabled or unavailable")
            return
        }

        workerJob = scope.launch {
            try {
                onStateChanged(ConnectionState.CONNECTING, "Connecting via Bluetooth SPP...")

                // Find paired device matching address or first paired device
                val bonded = bluetoothAdapter.bondedDevices
                val targetDevice = if (hostAddress.isNotBlank()) {
                    bonded.firstOrNull { it.address.equals(hostAddress, ignoreCase = true) || it.name.equals(hostAddress, ignoreCase = true) }
                } else {
                    bonded.firstOrNull()
                }

                if (targetDevice == null) {
                    onStateChanged(ConnectionState.ERROR, "No paired Bluetooth device found. Pair phones first.")
                    return@launch
                }

                val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter.cancelDiscovery()
                socket.connect()

                activeSocket = socket
                socketWriter = PrintWriter(socket.getOutputStream(), true)
                isConnectedFlag = true
                onStateChanged(ConnectionState.CONNECTED, targetDevice.name ?: targetDevice.address)

                listenForIncoming(socket, onMessageReceived)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth client connection error: ${e.message}", e)
                isConnectedFlag = false
                onStateChanged(ConnectionState.ERROR, e.message ?: "Bluetooth connection failed")
            }
        }
    }

    private fun listenForIncoming(socket: BluetoothSocket, onMessageReceived: (Message) -> Unit) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (scope.isActive) {
            val raw = reader.readLine() ?: break
            try {
                val message = gson.fromJson(raw, Message::class.java)
                if (message.type == "MESSAGE") {
                    onMessageReceived(message)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bluetooth message parse error: ${e.message}")
            }
        }
    }

    override fun sendMessage(message: Message) {
        val json = gson.toJson(message)
        scope.launch {
            try {
                socketWriter?.println(json)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth write error: ${e.message}", e)
            }
        }
    }

    override fun disconnect() {
        isConnectedFlag = false
        activeRoomCode = ""
        try {
            workerJob?.cancel()
            socketWriter?.close()
            activeSocket?.close()
            serverSocket?.close()
        } catch (ignored: Exception) {}
        activeSocket = null
        socketWriter = null
        serverSocket = null
    }

    override fun isConnected(): Boolean = isConnectedFlag

    override fun getTransportName(): String = "Bluetooth Classic"
}
