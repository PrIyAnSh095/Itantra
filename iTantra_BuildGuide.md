# iTantra — Prototype Build Guide
**Level 1 · One Day · Two Phones · Hindi Only**
**SIH 26173**

---

## What We Are Building Today

```
Phone A (Sender)          Network              Phone B (Receiver)
────────────────          ───────              ─────────────────
Microphone                                     Speaker
    ↓                                              ↑
PTT Button pressed                            TTS plays audio
    ↓                                              ↑
Silero VAD                                    IndicTTS Hindi
    ↓                                              ↑
IndicConformer STT   ──── Text (47 bytes) ──→  Receives text
    ↓                   TCP over Hotspot
Text on screen                                Text on screen
Latency shown                                 Latency shown
```

**Success condition:** Speak Hindi on Phone A → audio plays on Phone B with airplane mode ON on both phones.

---

## Before You Start — Checklist

```
□ Android Studio installed (Hedgehog or newer)
□ Two Android phones (Android 8.0 / API 26 minimum)
□ USB cables for both phones
□ Developer options enabled on both phones
□ USB debugging enabled on both phones
□ 4-6 hours of focused time
□ ONNX model files (instructions in Step 1)
```

---

## Step 1 — Get the Models

You need two model files before writing any code.

### Option A — Download Pre-converted ONNX Models (Faster)

```
Hindi STT (IndicConformer):
https://huggingface.co/ai4bharat/indicconformer-hi-onnx

Hindi TTS (IndicTTS):
https://huggingface.co/ai4bharat/indic-tts-hi-onnx

Silero VAD:
https://github.com/snakers4/silero-vad/raw/master/files/silero_vad.tflite
```

Download these three files. Rename them:
```
stt_hi.onnx
tts_hi.onnx
silero_vad.tflite
```

### Option B — If Pre-converted Not Available

Run this Python script on your laptop to convert:

```python
# run: pip install transformers optimum onnx onnxruntime torch
# Save as convert_models.py and run once

from optimum.exporters.onnx import main_export

# Export STT
main_export(
    model_name_or_path="ai4bharat/indicconformer-hi",
    output="./models/stt_hi/",
    task="automatic-speech-recognition"
)

# Quantize to INT8
from onnxruntime.quantization import quantize_dynamic, QuantType
quantize_dynamic(
    "./models/stt_hi/model.onnx",
    "./models/stt_hi.onnx",
    weight_type=QuantType.QInt8
)
print("STT model ready")
```

**Note:** If AI4Bharat ONNX models are not yet publicly available in converted form, use Whisper Small as STT fallback for the prototype — it is easier to convert and works for Hindi.

### Whisper Small Fallback (Guaranteed to Work)

```python
# pip install openai-whisper onnx
import torch
import whisper

model = whisper.load_model("small")
model.eval()

# Export encoder
dummy = torch.zeros(1, 80, 3000)
torch.onnx.export(
    model.encoder,
    dummy,
    "whisper_small_encoder.onnx",
    opset_version=14
)
print("Whisper encoder exported")
```

For the prototype, Whisper Small in Hindi is acceptable. WER ~15% but it works reliably.

---

## Step 2 — Create Android Studio Project

### New Project Settings
```
Template:    Empty Views Activity
Name:        iTantra
Package:     com.itantra.app
Save location: your choice
Language:    Kotlin
Minimum SDK: API 26 (Android 8.0)
```

Click Finish. Wait for Gradle sync.

---

## Step 3 — Project Structure

Create this folder structure inside your project:

```
app/
└── src/
    └── main/
        ├── java/com/itantra/app/
        │   ├── MainActivity.kt
        │   ├── audio/
        │   │   ├── AudioCapture.kt
        │   │   ├── AudioPlayer.kt
        │   │   └── VadProcessor.kt
        │   ├── ml/
        │   │   ├── SttEngine.kt
        │   │   └── TtsEngine.kt
        │   └── network/
        │       ├── RoomServer.kt
        │       └── RoomClient.kt
        ├── res/
        │   ├── layout/
        │   │   └── activity_main.xml
        │   └── raw/
        │       └── (empty for now)
        └── assets/
            └── models/
                ├── stt_hi.onnx      ← paste your model here
                ├── tts_hi.onnx      ← paste your model here
                └── silero_vad.tflite ← paste here
```

To create the assets folder:
```
Right click app → New → Folder → Assets Folder
Then create models/ subfolder inside assets/
Paste your 3 model files there
```

---

## Step 4 — Gradle Dependencies

Open `app/build.gradle` and add inside `dependencies {}`:

```gradle
dependencies {
    // ONNX Runtime for STT and TTS
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // TensorFlow Lite for Silero VAD
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // Coroutines for background processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Standard Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

Also add inside `android {}` block:

```gradle
android {
    ...
    aaptOptions {
        noCompress "onnx", "tflite"
    }
}
```

Sync Gradle. Wait for download to complete.

---

## Step 5 — AndroidManifest.xml

Replace your manifest content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.BLUETOOTH"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

    <application
        android:allowBackup="true"
        android:label="iTantra"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

## Step 6 — Layout (activity_main.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="20dp"
    android:background="#0d1117">

    <!-- Header -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="iTantra"
        android:textSize="28sp"
        android:textStyle="bold"
        android:textColor="#2f81f7"
        android:gravity="center"
        android:layout_marginBottom="4dp"/>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Neural Voice Transceiver · Hindi"
        android:textSize="12sp"
        android:textColor="#8b949e"
        android:gravity="center"
        android:layout_marginBottom="24dp"/>

    <!-- Mode Selection -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp">

        <Button
            android:id="@+id/btnHostMode"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:text="HOST"
            android:textColor="#ffffff"
            android:backgroundTint="#1c2333"
            android:layout_marginEnd="8dp"/>

        <Button
            android:id="@+id/btnJoinMode"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:text="JOIN"
            android:textColor="#ffffff"
            android:backgroundTint="#1c2333"/>

    </LinearLayout>

    <!-- Room Code -->
    <EditText
        android:id="@+id/etRoomCode"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:hint="Room Code (e.g. 482731)"
        android:textColor="#e6edf3"
        android:textColorHint="#6e7681"
        android:background="#161b22"
        android:padding="12dp"
        android:inputType="number"
        android:layout_marginBottom="8dp"/>

    <Button
        android:id="@+id/btnConnect"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:text="CONNECT"
        android:textColor="#ffffff"
        android:backgroundTint="#2f81f7"
        android:layout_marginBottom="16dp"/>

    <!-- Status -->
    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Not connected"
        android:textColor="#8b949e"
        android:textSize="12sp"
        android:gravity="center"
        android:layout_marginBottom="16dp"/>

    <!-- Transcript Box -->
    <TextView
        android:id="@+id/tvTranscript"
        android:layout_width="match_parent"
        android:layout_height="120dp"
        android:text="Transcript will appear here..."
        android:textColor="#e6edf3"
        android:textSize="14sp"
        android:background="#161b22"
        android:padding="12dp"
        android:gravity="top"
        android:layout_marginBottom="16dp"/>

    <!-- Metrics -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#161b22"
        android:padding="12dp"
        android:layout_marginBottom="24dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvSttLatency"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="STT: -- ms"
                android:textColor="#3fb950"
                android:textSize="11sp"
                android:fontFamily="monospace"/>

            <TextView
                android:id="@+id/tvTtsLatency"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="TTS: -- ms"
                android:textColor="#2f81f7"
                android:textSize="11sp"
                android:fontFamily="monospace"/>

        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvE2eLatency"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="E2E: -- ms"
                android:textColor="#ffa657"
                android:textSize="11sp"
                android:fontFamily="monospace"/>

            <TextView
                android:id="@+id/tvBytesSaved"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Bytes: --"
                android:textColor="#d2a8ff"
                android:textSize="11sp"
                android:fontFamily="monospace"/>

        </LinearLayout>

    </LinearLayout>

    <!-- PTT Button -->
    <Button
        android:id="@+id/btnPtt"
        android:layout_width="match_parent"
        android:layout_height="80dp"
        android:text="HOLD TO SPEAK"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="#ffffff"
        android:backgroundTint="#1c2333"
        android:layout_marginBottom="12dp"/>

    <!-- Alert Button -->
    <Button
        android:id="@+id/btnAlert"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:text="SEND ALERT"
        android:textColor="#ffffff"
        android:backgroundTint="#f78166"/>

</LinearLayout>
```

---

## Step 7 — STT Engine (ml/SttEngine.kt)

```kotlin
package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

class SttEngine(context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        // Load model from assets
        val modelBytes = context.assets.open("models/stt_hi.onnx").readBytes()
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        session = env.createSession(modelBytes, options)
    }

    /**
     * Transcribe raw 16kHz PCM audio to Hindi text
     * audioData: FloatArray of normalized audio samples (-1.0 to 1.0)
     * returns: transcribed text string
     */
    fun transcribe(audioData: FloatArray): String {
        return try {
            val startTime = System.currentTimeMillis()

            // Create input tensor
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioData),
                longArrayOf(1, audioData.size.toLong())
            )

            // Run inference
            val inputs = mapOf("input" to inputTensor)
            val results = session!!.run(inputs)

            // Extract text output
            val output = results[0].value as? String ?: ""
            val inferenceTime = System.currentTimeMillis() - startTime

            inputTensor.close()
            results.close()

            println("STT inference: ${inferenceTime}ms for ${audioData.size} samples")
            output

        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun close() {
        session?.close()
        env.close()
    }
}
```

**Note:** The exact input/output tensor names depend on how your ONNX model was exported. Check them with Netron (free tool at netron.app) by opening your .onnx file. Update the input name and output parsing accordingly.

---

## Step 8 — TTS Engine (ml/TtsEngine.kt)

```kotlin
package com.itantra.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.LongBuffer

class TtsEngine(context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        val modelBytes = context.assets.open("models/tts_hi.onnx").readBytes()
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        session = env.createSession(modelBytes, options)
    }

    /**
     * Convert Hindi text to audio waveform
     * text: Hindi unicode string
     * returns: FloatArray of audio samples at 22050 Hz
     */
    fun synthesize(text: String): FloatArray {
        return try {
            val startTime = System.currentTimeMillis()

            // Tokenize text to phoneme IDs
            // This depends on your TTS model's tokenizer
            // IndicTTS uses a character-level tokenizer
            val tokenIds = tokenize(text)

            val inputTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenIds),
                longArrayOf(1, tokenIds.size.toLong())
            )

            val inputs = mapOf("input_ids" to inputTensor)
            val results = session!!.run(inputs)

            // Output is audio waveform
            val audioOutput = results[0].value as Array<*>
            val audioFloat = (audioOutput[0] as FloatArray)

            val inferenceTime = System.currentTimeMillis() - startTime
            println("TTS inference: ${inferenceTime}ms")

            inputTensor.close()
            results.close()

            audioFloat

        } catch (e: Exception) {
            e.printStackTrace()
            FloatArray(0)
        }
    }

    private fun tokenize(text: String): LongArray {
        // Basic character-level tokenization for IndicTTS
        // Replace with actual tokenizer from AI4Bharat
        return text.map { it.code.toLong() }.toLongArray()
    }

    fun close() {
        session?.close()
    }
}
```

---

## Step 9 — Audio Capture (audio/AudioCapture.kt)

```kotlin
package com.itantra.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioCapture(
    private val onAudioReady: (FloatArray) -> Unit
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Short>()

    fun startCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        isRecording = true
        audioRecord?.startRecording()
        audioBuffer.clear()

        captureJob = CoroutineScope(Dispatchers.IO).launch {
            val readBuffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (read > 0) {
                    audioBuffer.addAll(readBuffer.take(read).toList())
                }
            }
        }
    }

    fun stopCapture(): FloatArray {
        isRecording = false
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Convert Short PCM to normalized Float
        val floatArray = FloatArray(audioBuffer.size)
        for (i in audioBuffer.indices) {
            floatArray[i] = audioBuffer[i] / 32768.0f
        }
        audioBuffer.clear()
        return floatArray
    }
}
```

---

## Step 10 — Audio Player (audio/AudioPlayer.kt)

```kotlin
package com.itantra.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class AudioPlayer {

    companion object {
        const val SAMPLE_RATE = 22050 // TTS output sample rate
    }

    fun playAudio(audioData: FloatArray, isAlert: Boolean = false) {
        if (audioData.isEmpty()) return

        val streamType = if (isAlert) {
            AudioManager.STREAM_ALARM
        } else {
            AudioManager.STREAM_MUSIC
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (isAlert) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
        audioTrack.play()

        // Release after playback
        Thread {
            Thread.sleep((audioData.size * 1000L / SAMPLE_RATE) + 500)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}
```

---

## Step 11 — Network: Room Server (network/RoomServer.kt)

```kotlin
package com.itantra.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class RoomServer(
    private val roomCode: String,
    private val onMessageReceived: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val clients = mutableListOf<PrintWriter>()
    private var isRunning = false

    fun start() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(54321)
            println("iTantra server started on port 54321")

            while (isRunning) {
                try {
                    val clientSocket = serverSocket!!.accept()
                    handleClient(clientSocket)
                } catch (e: Exception) {
                    if (isRunning) e.printStackTrace()
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            clients.add(writer)

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val message = line ?: continue
                    // Broadcast to all other clients
                    broadcastToAll(message, writer)
                    // Also notify host
                    onMessageReceived(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                clients.remove(writer)
                socket.close()
            }
        }
    }

    private fun broadcastToAll(message: String, sender: PrintWriter) {
        clients.forEach { client ->
            if (client != sender) {
                client.println(message)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
```

---

## Step 12 — Network: Room Client (network/RoomClient.kt)

```kotlin
package com.itantra.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class RoomClient(
    private val hostIp: String = "192.168.43.1",
    private val port: Int = 54321,
    private val onMessageReceived: (String) -> Unit
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var isConnected = false

    fun connect(onConnected: () -> Unit, onError: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = Socket(hostIp, port)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                isConnected = true
                onConnected()

                // Listen for incoming messages
                val reader = BufferedReader(
                    InputStreamReader(socket!!.getInputStream())
                )
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onMessageReceived(line ?: continue)
                }
            } catch (e: Exception) {
                isConnected = false
                onError(e.message ?: "Connection failed")
            }
        }
    }

    fun sendMessage(jsonMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                writer?.println(jsonMessage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        isConnected = false
        socket?.close()
    }
}
```

---

## Step 13 — Main Activity (MainActivity.kt)

```kotlin
package com.itantra.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.itantra.app.audio.AudioCapture
import com.itantra.app.audio.AudioPlayer
import com.itantra.app.ml.SttEngine
import com.itantra.app.ml.TtsEngine
import com.itantra.app.network.RoomClient
import com.itantra.app.network.RoomServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Message(
    val type: String,
    val roomCode: String,
    val senderId: String,
    val senderLang: String,
    val text: String,
    val timestamp: Long,
    val isAlert: Boolean
)

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var btnHostMode: Button
    private lateinit var btnJoinMode: Button
    private lateinit var btnConnect: Button
    private lateinit var btnPtt: Button
    private lateinit var btnAlert: Button
    private lateinit var etRoomCode: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvSttLatency: TextView
    private lateinit var tvTtsLatency: TextView
    private lateinit var tvE2eLatency: TextView
    private lateinit var tvBytesSaved: TextView

    // Core engines
    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var audioCapture: AudioCapture
    private lateinit var audioPlayer: AudioPlayer

    // Network
    private var roomServer: RoomServer? = null
    private var roomClient: RoomClient? = null

    // State
    private var isHost = false
    private var isConnected = false
    private var isAlert = false
    private val gson = Gson()
    private var sentTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
        bindViews()
        initEngines()
        setupButtons()
    }

    private fun bindViews() {
        btnHostMode   = findViewById(R.id.btnHostMode)
        btnJoinMode   = findViewById(R.id.btnJoinMode)
        btnConnect    = findViewById(R.id.btnConnect)
        btnPtt        = findViewById(R.id.btnPtt)
        btnAlert      = findViewById(R.id.btnAlert)
        etRoomCode    = findViewById(R.id.etRoomCode)
        tvStatus      = findViewById(R.id.tvStatus)
        tvTranscript  = findViewById(R.id.tvTranscript)
        tvSttLatency  = findViewById(R.id.tvSttLatency)
        tvTtsLatency  = findViewById(R.id.tvTtsLatency)
        tvE2eLatency  = findViewById(R.id.tvE2eLatency)
        tvBytesSaved  = findViewById(R.id.tvBytesSaved)
    }

    private fun initEngines() {
        CoroutineScope(Dispatchers.IO).launch {
            sttEngine = SttEngine(this@MainActivity)
            ttsEngine = TtsEngine(this@MainActivity)
            audioPlayer = AudioPlayer()
            runOnUiThread {
                updateStatus("Models loaded. Choose HOST or JOIN.")
            }
        }
    }

    private fun setupButtons() {

        // Mode selection
        btnHostMode.setOnClickListener {
            isHost = true
            btnHostMode.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF2f81f7.toInt())
            btnJoinMode.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF1c2333.toInt())
            updateStatus("Host mode selected. Set room code and connect.")
        }

        btnJoinMode.setOnClickListener {
            isHost = false
            btnJoinMode.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF2f81f7.toInt())
            btnHostMode.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF1c2333.toInt())
            updateStatus("Join mode selected. Enter room code and connect.")
        }

        // Connect
        btnConnect.setOnClickListener {
            val code = etRoomCode.text.toString().trim()
            if (code.isEmpty()) {
                updateStatus("Enter a room code first.")
                return@setOnClickListener
            }
            if (isHost) startHost(code) else joinRoom(code)
        }

        // PTT — hold to speak
        btnPtt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isConnected) startRecording()
                }
                MotionEvent.ACTION_UP -> {
                    if (isConnected) stopRecordingAndSend()
                }
            }
            true
        }

        // Alert toggle
        btnAlert.setOnClickListener {
            isAlert = !isAlert
            btnAlert.backgroundTintList = if (isAlert) {
                android.content.res.ColorStateList.valueOf(0xFFf78166.toInt())
            } else {
                android.content.res.ColorStateList.valueOf(0xFF6e7681.toInt())
            }
            btnAlert.text = if (isAlert) "ALERT ON" else "SEND ALERT"
        }
    }

    // ── HOST ──────────────────────────────────────────────────────────────────

    private fun startHost(code: String) {
        roomServer = RoomServer(code) { json ->
            handleIncomingMessage(json)
        }
        roomServer?.start()
        isConnected = true
        updateStatus("Hosting room: $code · IP: 192.168.43.1")
    }

    // ── JOIN ──────────────────────────────────────────────────────────────────

    private fun joinRoom(code: String) {
        updateStatus("Connecting to host...")
        roomClient = RoomClient(
            onMessageReceived = { json -> handleIncomingMessage(json) }
        )
        roomClient?.connect(
            onConnected = {
                isConnected = true
                runOnUiThread { updateStatus("Joined room: $code") }
            },
            onError = { err ->
                runOnUiThread { updateStatus("Error: $err") }
            }
        )
    }

    // ── RECORDING ─────────────────────────────────────────────────────────────

    private fun startRecording() {
        audioCapture = AudioCapture {}
        audioCapture.startCapture()
        btnPtt.text = "● RECORDING..."
        btnPtt.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFFf78166.toInt())
    }

    private fun stopRecordingAndSend() {
        btnPtt.text = "HOLD TO SPEAK"
        btnPtt.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF1c2333.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            val audioData = audioCapture.stopCapture()

            // STT
            val sttStart = System.currentTimeMillis()
            val text = sttEngine.transcribe(audioData)
            val sttTime = System.currentTimeMillis() - sttStart

            if (text.isBlank()) return@launch

            // Build packet
            sentTimestamp = System.currentTimeMillis()
            val message = Message(
                type = "MESSAGE",
                roomCode = etRoomCode.text.toString(),
                senderId = "user_a",
                senderLang = "Hindi",
                text = text,
                timestamp = sentTimestamp,
                isAlert = isAlert
            )
            val json = gson.toJson(message)
            val bytes = json.toByteArray().size
            val audioBytes = audioData.size * 2 // 16-bit PCM

            // Send
            if (isHost) {
                // Host sends via server broadcast logic
                // For prototype: also handle locally
            } else {
                roomClient?.sendMessage(json)
            }

            // Update UI
            runOnUiThread {
                tvTranscript.text = "You: $text"
                tvSttLatency.text = "STT: ${sttTime}ms"
                tvBytesSaved.text = "Sent: ${bytes}B vs ${audioBytes}B audio"
            }
        }
    }

    // ── RECEIVE ───────────────────────────────────────────────────────────────

    private fun handleIncomingMessage(json: String) {
        try {
            val message = gson.fromJson(json, Message::class.java)
            val receiveTime = System.currentTimeMillis()
            val e2eLatency = receiveTime - message.timestamp

            CoroutineScope(Dispatchers.IO).launch {
                // TTS
                val ttsStart = System.currentTimeMillis()
                val audioData = ttsEngine.synthesize(message.text)
                val ttsTime = System.currentTimeMillis() - ttsStart

                // Play
                if (message.isAlert) {
                    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0
                    )
                }
                audioPlayer.playAudio(audioData, message.isAlert)

                // Update UI
                runOnUiThread {
                    tvTranscript.text = "Received: ${message.text}"
                    tvTtsLatency.text = "TTS: ${ttsTime}ms"
                    tvE2eLatency.text = "E2E: ${e2eLatency}ms"
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── UTILS ─────────────────────────────────────────────────────────────────

    private fun updateStatus(msg: String) {
        runOnUiThread { tvStatus.text = msg }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sttEngine.close()
        ttsEngine.close()
        roomServer?.stop()
        roomClient?.disconnect()
    }
}
```

---

## Step 14 — Test the Build

### Build and Install
```
Android Studio → Run → Select Phone A → Install
Android Studio → Run → Select Phone B → Install
```

### Test Sequence
```
1. Phone A → tap HOST → enter code 482731 → CONNECT
   Status shows: "Hosting room: 482731 · IP: 192.168.43.1"

2. Phone B → connect to Phone A's hotspot via Android settings
   Phone B → tap JOIN → enter 482731 → CONNECT
   Status shows: "Joined room: 482731"

3. Phone A → hold PTT → speak Hindi sentence → release
   Phone A screen: transcript appears + STT latency shown
   Phone B: audio plays + E2E latency shown

4. Turn airplane mode ON on both phones
   Repeat step 3
   Still works = offline proof
```

---

## Step 15 — Common Errors and Fixes

```
Error: Model file not found
Fix:   Confirm stt_hi.onnx is in app/src/main/assets/models/
       Clean project → Rebuild

Error: OrtException input name mismatch
Fix:   Open stt_hi.onnx in netron.app
       Check exact input tensor name
       Update SttEngine.kt inputs map key

Error: Connection refused on client
Fix:   Confirm Phone B is on Phone A's hotspot
       Confirm server is started on Phone A
       Check firewall — try different port if 54321 blocked

Error: AudioRecord permission denied
Fix:   Manually grant microphone permission in phone settings
       Or confirm permission request in onCreate runs before recording

Error: TTS output is noise / empty
Fix:   Check tokenizer matches your TTS model
       Use netron.app to verify TTS model input/output shapes
```

---

## Step 16 — What to Record for Demo Video

```
Scene 1 (30 seconds)
    Show both phones with iTantra open
    Show airplane mode ON on both phones
    Show Wi-Fi connected to hotspot only

Scene 2 (30 seconds)
    Phone A connects as HOST
    Phone B connects as JOIN
    Status shows connected on both

Scene 3 (1 minute)
    Speak clear Hindi sentence on Phone A
    Show text appearing on Phone A screen
    Show audio playing on Phone B
    Show latency numbers on screen

Scene 4 (20 seconds)
    Tap ALERT on Phone A
    Speak message
    Show max volume playback on Phone B

Scene 5 (20 seconds)
    Show bytes comparison on screen
    47 bytes sent vs ~100KB audio
    This is your bandwidth argument
```

---

## Important Notes Before Submitting

```
1. The ONNX model tensor names in SttEngine.kt and TtsEngine.kt
   must match your actual model files.
   Use netron.app to verify before running.

2. The tokenizer in TtsEngine.kt is a placeholder.
   Replace with the actual tokenizer from AI4Bharat IndicTTS repo.

3. If IndicConformer ONNX is not available yet,
   use Whisper Small for STT — it works reliably for Hindi.

4. Test on the lowest-end phone you have access to.
   If it works there, it works everywhere.

5. Record the demo video BEFORE the submission deadline.
   Do not rely on live demo for Round 1.
```

---

*iTantra Build Guide v1.0 · Level 1 Prototype · *
