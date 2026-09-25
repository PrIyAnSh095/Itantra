package com.itantra.app.data

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class RecordingState {
    READY,
    RECORDING,
    PROCESSING,
    SENDING,
    RECEIVING,
    PLAYING
}
