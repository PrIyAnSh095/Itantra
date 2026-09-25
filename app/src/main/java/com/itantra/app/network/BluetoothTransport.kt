package com.itantra.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
 * Point-to-point networking transport using Bluetooth Classic RFCOMM / SPP.
 * Uses standard Serial Port Profile UUID: 00001101-0000-1000-8000-00805F9B34FB
 */
class BluetoothTransport : NetworkTransport {

    companion object {
        private const val TAG = "BluetoothTransport"
        private const val SERVICE_NAME = "iTantraVoice"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
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

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getLocalDeviceName(): String = bluetoothAdapter?.name ?: "Android Device"

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                bluetoothAdapter.bondedDevices.toList()
            } catch (e: Exception) {
                Log.w(TAG, "Error getting bonded devices: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun startHost(
        roomCode: String,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: (ConnectionState, String) -> Unit
    ) {
        disconnect()
        activeRoomCode = roomCode

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onStateChanged(ConnectionState.ERROR, "Bluetooth is disabled. Please turn on Bluetooth.")
            return
        }

        workerJob = scope.launch {
            try {
                val myName = bluetoothAdapter.name ?: "Host"
                onStateChanged(ConnectionState.CONNECTING, "Bluetooth Host listening as '$myName'...")
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                val socket = serverSocket!!.accept()
                serverSocket?.close()
                serverSocket = null

                activeSocket = socket
                socketWriter = PrintWriter(socket.getOutputStream(), true)
                isConnectedFlag = true

                val peerName = try { socket.remoteDevice.name ?: socket.remoteDevice.address } catch (e: Exception) { "Peer" }
                onStateChanged(ConnectionState.CONNECTED, "Connected to $peerName (Bluetooth P2P)")

                listenForIncoming(socket, onMessageReceived, onStateChanged)
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
            onStateChanged(ConnectionState.ERROR, "Bluetooth is disabled. Please turn on Bluetooth.")
            return
        }

        workerJob = scope.launch {
            try {
                onStateChanged(ConnectionState.CONNECTING, "Connecting via Bluetooth SPP...")

                val bonded = try { bluetoothAdapter.bondedDevices.toList() } catch (e: Exception) { emptyList() }
                if (bonded.isEmpty()) {
                    onStateChanged(ConnectionState.ERROR, "No paired Bluetooth devices found. Please pair phones first in Bluetooth settings.")
                    return@launch
                }

                // If hostAddress is a valid MAC address or device name, match it
                val targetDevice: BluetoothDevice? = if (hostAddress.isNotBlank() && !hostAddress.startsWith("192.")) {
                    bonded.firstOrNull {
                        it.address.equals(hostAddress, ignoreCase = true) ||
                                (it.name != null && it.name.equals(hostAddress, ignoreCase = true))
                    } ?: bonded.firstOrNull()
                } else {
                    bonded.firstOrNull()
                }

                if (targetDevice == null) {
                    onStateChanged(ConnectionState.ERROR, "Target Bluetooth peer device not found.")
                    return@launch
                }

                val deviceName = try { targetDevice.name ?: targetDevice.address } catch (e: Exception) { targetDevice.address }
                onStateChanged(ConnectionState.CONNECTING, "Connecting to $deviceName...")

                val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    bluetoothAdapter.cancelDiscovery()
                } catch (ignored: Exception) {}

                socket.connect()

                activeSocket = socket
                socketWriter = PrintWriter(socket.getOutputStream(), true)
                isConnectedFlag = true
                onStateChanged(ConnectionState.CONNECTED, "Connected to $deviceName (Bluetooth P2P)")

                listenForIncoming(socket, onMessageReceived, onStateChanged)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth client connection error: ${e.message}", e)
                isConnectedFlag = false
                onStateChanged(ConnectionState.ERROR, "Failed to connect to Bluetooth peer. Ensure peer is in HOST mode.")
            }
        }
    }

    private fun listenForIncoming(
        socket: BluetoothSocket,
        onMessageReceived: (Message) -> Unit,
        onStateChanged: ((ConnectionState, String) -> Unit)? = null
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (scope.isActive && isConnectedFlag) {
                val raw = reader.readLine() ?: break
                try {
                    val message = gson.fromJson(raw, Message::class.java)
                    if (message != null && message.type == "MESSAGE") {
                        onMessageReceived(message)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bluetooth message parse error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth stream ended: ${e.message}")
        } finally {
            if (isConnectedFlag) {
                isConnectedFlag = false
                onStateChanged?.invoke(ConnectionState.DISCONNECTED, "Bluetooth connection closed")
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

    override fun getTransportName(): String = "Bluetooth Classic (RFCOMM P2P)"
}
