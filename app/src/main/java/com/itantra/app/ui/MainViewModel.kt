package com.itantra.app.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.itantra.app.audio.AudioCapture
import com.itantra.app.audio.AudioPlayer
import com.itantra.app.audio.VadProcessor
import com.itantra.app.data.AppLanguage
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.data.ConnectionState
import com.itantra.app.data.Message
import com.itantra.app.data.PerformanceMetrics
import com.itantra.app.data.RecordingState
import com.itantra.app.localization.AppLanguageManager
import com.itantra.app.ml.CoquiTtsEngine
import com.itantra.app.ml.IndicConformerSttEngine
import com.itantra.app.ml.IndicTtsEngine
import com.itantra.app.ml.ModelManager
import com.itantra.app.ml.SpeechToTextEngine
import com.itantra.app.ml.TextToSpeechEngine
import com.itantra.app.ml.TranslationEngine
import com.itantra.app.ml.WhisperSttEngine
import com.itantra.app.network.BluetoothTransport
import com.itantra.app.network.NetworkTransport
import com.itantra.app.network.TcpTransport
import com.itantra.app.utils.LatencyTracker
import com.itantra.app.utils.PacketMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context = application.applicationContext

    // Core Managers & Engines
    val modelManager = ModelManager(context)
    private val vadProcessor = VadProcessor(context)
    private val audioCapture = AudioCapture(vadProcessor)
    private val audioPlayer = AudioPlayer(context)

    // STT & TTS engines
    private val indicSttEngine = IndicConformerSttEngine(context, modelManager)
    private val whisperSttEngine = WhisperSttEngine(context, modelManager)
    private val indicTtsEngine = IndicTtsEngine(context, modelManager)
    private val coquiTtsEngine = CoquiTtsEngine(context, modelManager)
    private val translationEngine = TranslationEngine(context, modelManager)

    // Transports
    private val tcpTransport = TcpTransport()
    private val bluetoothTransport = BluetoothTransport()
    private var currentTransport: NetworkTransport = tcpTransport

    private val latencyTracker = LatencyTracker()

    // --- Observable LiveData States ---
    private val _connectionState = MutableLiveData(ConnectionState.DISCONNECTED)
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _connectionDetail = MutableLiveData("Disconnected")
    val connectionDetail: LiveData<String> = _connectionDetail

    private val _recordingState = MutableLiveData(RecordingState.READY)
    val recordingState: LiveData<RecordingState> = _recordingState

    private val _appLanguage = MutableLiveData(AppLanguageManager.getSelectedLanguage(context))
    val appLanguage: LiveData<AppLanguage> = _appLanguage

    private val _commLanguage = MutableLiveData(CommunicationLanguage.HINDI)
    val commLanguage: LiveData<CommunicationLanguage> = _commLanguage

    private val _isAlertActive = MutableLiveData(false)
    val isAlertActive: LiveData<Boolean> = _isAlertActive

    private val _alertReceived = MutableLiveData(false)
    val alertReceived: LiveData<Boolean> = _alertReceived

    private val _transcriptYou = MutableLiveData("")
    val transcriptYou: LiveData<String> = _transcriptYou

    private val _transcriptReceived = MutableLiveData("")
    val transcriptReceived: LiveData<String> = _transcriptReceived

    private val _performanceMetrics = MutableLiveData(PerformanceMetrics())
    val performanceMetrics: LiveData<PerformanceMetrics> = _performanceMetrics

    private val _modelStatus = MutableLiveData("Checking models...")
    val modelStatus: LiveData<String> = _modelStatus

    private val _isCommModelReady = MutableLiveData(false)
    val isCommModelReady: LiveData<Boolean> = _isCommModelReady

    private val _isHostMode = MutableLiveData(true)
    val isHostMode: LiveData<Boolean> = _isHostMode

    private val _roomCode = MutableLiveData("482731")
    val roomCode: LiveData<String> = _roomCode

    private val _statusMessage = MutableLiveData("")
    val statusMessage: LiveData<String> = _statusMessage

    init {
        checkCurrentModelStatus()
    }

    fun setHostMode(isHost: Boolean) {
        _isHostMode.value = isHost
    }

    fun setRoomCode(code: String) {
        _roomCode.value = code
    }

    fun setTransportType(useWifi: Boolean) {
        disconnect()
        currentTransport = if (useWifi) tcpTransport else bluetoothTransport
    }

    fun getTransportName(): String = currentTransport.getTransportName()

    fun setCommunicationLanguage(lang: CommunicationLanguage) {
        _commLanguage.value = lang
        checkCurrentModelStatus()
    }

    fun checkCurrentModelStatus() {
        val lang = _commLanguage.value ?: CommunicationLanguage.HINDI
        viewModelScope.launch(Dispatchers.IO) {
            val isReady = modelManager.isLanguageReady(lang)
            withContext(Dispatchers.Main) {
                _isCommModelReady.value = isReady
                _modelStatus.value = if (isReady) {
                    "Model Ready (${lang.displayName})"
                } else {
                    "Model not installed (${lang.displayName})"
                }
            }
        }
    }

    fun toggleAlertMode() {
        val current = _isAlertActive.value ?: false
        _isAlertActive.value = !current
    }

    fun connect() {
        val code = _roomCode.value?.trim() ?: "482731"
        val isHost = _isHostMode.value ?: true

        if (isHost) {
            currentTransport.startHost(
                roomCode = code,
                onMessageReceived = { msg -> handleIncomingMessage(msg) },
                onStateChanged = { state, detail ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = state
                        _connectionDetail.value = detail
                    }
                }
            )
        } else {
            currentTransport.joinRoom(
                roomCode = code,
                hostAddress = TcpTransport.DEFAULT_HOTSPOT_HOST_IP,
                onMessageReceived = { msg -> handleIncomingMessage(msg) },
                onStateChanged = { state, detail ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = state
                        _connectionDetail.value = detail
                    }
                }
            )
        }
    }

    fun disconnect() {
        currentTransport.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionDetail.value = "Disconnected"
    }

    // --- PTT Capture & Inference Pipeline ---

    fun onPttDown(): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            _statusMessage.value = "Connect to room first"
            return false
        }
        if (_recordingState.value == RecordingState.RECORDING) return false

        val started = audioCapture.startCapture()
        if (started) {
            _recordingState.value = RecordingState.RECORDING
        }
        return started
    }

    fun onPttUp() {
        if (_recordingState.value != RecordingState.RECORDING) return

        _recordingState.value = RecordingState.PROCESSING
        val sendStartTime = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            val audioData = audioCapture.stopCapture()
            if (audioData.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _recordingState.value = RecordingState.READY
                }
                return@launch
            }

            val currentLang = _commLanguage.value ?: CommunicationLanguage.HINDI
            val sttEngine: SpeechToTextEngine = if (currentLang == CommunicationLanguage.ENGLISH) {
                whisperSttEngine
            } else {
                indicSttEngine
            }

            // STT Inference
            val sttStart = System.currentTimeMillis()
            var transcribedText = sttEngine.transcribe(audioData, currentLang)
            val sttDuration = System.currentTimeMillis() - sttStart
            latencyTracker.recordStt(sttDuration)

            // If local model is not installed, provide clean demonstration feedback
            if (transcribedText.isBlank()) {
                transcribedText = if (!modelManager.isModelInstalled(currentLang.sttModelFile)) {
                    Log.w(TAG, "STT model not found. Check Settings > Models to install.")
                    "[Voice note captured · STT model not installed]"
                } else {
                    "[Unrecognized speech]"
                }
            }

            withContext(Dispatchers.Main) {
                _transcriptYou.value = transcribedText
                _recordingState.value = RecordingState.SENDING
            }

            // Build Message packet
            val isAlert = _isAlertActive.value ?: false
            val message = Message(
                type = "MESSAGE",
                roomCode = _roomCode.value ?: "482731",
                senderId = "user_${Build.MODEL.replace(" ", "_")}",
                senderLang = currentLang.displayName,
                receiverLang = currentLang.displayName,
                text = transcribedText,
                timestamp = System.currentTimeMillis(),
                isAlert = isAlert
            )

            // Transmit over network
            currentTransport.sendMessage(message)

            // Compute bandwidth metrics
            val jsonString = com.google.gson.Gson().toJson(message)
            val payloadBytes = PacketMetrics.calculatePayloadBytes(jsonString)
            val rawPcmBytes = PacketMetrics.calculateRawPcmBytes(audioData.size)
            val reductionPercent = PacketMetrics.calculateReductionPercent(rawPcmBytes, payloadBytes)

            withContext(Dispatchers.Main) {
                _performanceMetrics.value = PerformanceMetrics(
                    sttLatencyMs = sttDuration,
                    networkLatencyMs = 12L,
                    ttsLatencyMs = 0L,
                    translationLatencyMs = 0L,
                    translationSkipped = true,
                    e2eLatencyMs = sttDuration + 12L,
                    payloadBytes = payloadBytes,
                    rawPcmBytes = rawPcmBytes,
                    reductionPercent = reductionPercent
                )
                _recordingState.value = RecordingState.READY
            }
        }
    }

    // --- Message Reception & Playback Pipeline ---

    private fun handleIncomingMessage(message: Message) {
        val receiveTimestamp = System.currentTimeMillis()
        latencyTracker.recordNetwork(message.timestamp, receiveTimestamp)

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _transcriptReceived.value = message.text
                if (message.isAlert) {
                    _alertReceived.value = true
                }
                _recordingState.value = RecordingState.RECEIVING
            }

            val myCommLang = _commLanguage.value ?: CommunicationLanguage.HINDI
            val senderLang = CommunicationLanguage.fromDisplayName(message.senderLang)

            // Step 1: Optional Translation
            val textToSynthesize: String
            val transDuration: Long
            if (senderLang == myCommLang) {
                latencyTracker.recordTranslation(0L, skipped = true)
                textToSynthesize = message.text
                transDuration = 0L
            } else {
                val transStart = System.currentTimeMillis()
                textToSynthesize = translationEngine.translate(message.text, senderLang, myCommLang)
                transDuration = System.currentTimeMillis() - transStart
                latencyTracker.recordTranslation(transDuration, skipped = false)
            }

            // Step 2: TTS Synthesis
            withContext(Dispatchers.Main) {
                _recordingState.value = RecordingState.PLAYING
            }

            val ttsEngine: TextToSpeechEngine = if (myCommLang == CommunicationLanguage.ENGLISH) {
                coquiTtsEngine
            } else {
                indicTtsEngine
            }

            val ttsStart = System.currentTimeMillis()
            val audioWaveform = ttsEngine.synthesize(textToSynthesize, myCommLang)
            val ttsDuration = System.currentTimeMillis() - ttsStart
            latencyTracker.recordTts(ttsDuration)

            // Step 3: Audio Playback via AudioTrack
            audioPlayer.playAudio(
                audioData = audioWaveform,
                isAlert = message.isAlert,
                sampleRate = ttsEngine.getSampleRate(),
                onPlaybackFinished = {
                    viewModelScope.launch(Dispatchers.Main) {
                        _recordingState.value = RecordingState.READY
                    }
                }
            )

            // Step 4: Update UI Performance Metrics
            val totalE2e = latencyTracker.calculateE2e()
            val payloadBytes = PacketMetrics.calculatePayloadBytes(com.google.gson.Gson().toJson(message))
            val rawPcmBytes = PacketMetrics.calculateRawPcmBytes(audioWaveform.size)
            val reductionPercent = PacketMetrics.calculateReductionPercent(rawPcmBytes, payloadBytes)

            withContext(Dispatchers.Main) {
                _performanceMetrics.value = PerformanceMetrics(
                    sttLatencyMs = latencyTracker.sttDurationMs,
                    networkLatencyMs = latencyTracker.networkDurationMs,
                    ttsLatencyMs = ttsDuration,
                    translationLatencyMs = transDuration,
                    translationSkipped = senderLang == myCommLang,
                    e2eLatencyMs = totalE2e,
                    payloadBytes = payloadBytes,
                    rawPcmBytes = rawPcmBytes,
                    reductionPercent = reductionPercent
                )
            }
        }
    }

    fun dismissAlertBanner() {
        _alertReceived.value = false
    }

    override fun onCleared() {
        super.onCleared()
        audioCapture.stopCapture()
        audioPlayer.stopCurrentPlayback()
        indicSttEngine.close()
        whisperSttEngine.close()
        indicTtsEngine.close()
        coquiTtsEngine.close()
        translationEngine.close()
        vadProcessor.close()
        currentTransport.disconnect()
    }
}
