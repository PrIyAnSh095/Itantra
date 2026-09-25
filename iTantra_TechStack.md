# iTantra — Technology Stack
**SIH 26173 **

---

## 1. Android Application Layer

### Language & Build
| Tool | Version | Purpose |
|---|---|---|
| Kotlin | 1.9+ | Primary app language |
| Android SDK | API 26+ (Android 8.0) | Minimum device support |
| Gradle | 8.x | Build system |
| Android Studio | Hedgehog+ | IDE |

### Core Android APIs Used
| API | Purpose |
|---|---|
| AudioRecord | Raw microphone PCM capture at 16kHz |
| AudioTrack | Low-latency audio playback of TTS output |
| AudioManager.STREAM_ALARM | Max volume non-interruptible alert playback |
| WakeLock | Wake screen on alert message receive |
| WifiManager / WifiP2pManager | Hotspot detection and Wi-Fi Direct |
| BluetoothAdapter / BluetoothSocket | Bluetooth RFCOMM connection |
| Foreground Service | Keep mic and VAD alive when app backgrounded |
| WorkManager | Background language pack downloads |

---

## 2. ML Runtime Layer

### Primary Runtime
| Tool | Purpose | License |
|---|---|---|
| ONNX Runtime for Android | Runs STT, TTS, Translation models on CPU | MIT |
| TensorFlow Lite (TFLite) | Runs Silero VAD model | Apache 2.0 |

### Why ONNX Runtime
- Supports INT8 quantized models (smaller, faster)
- CPU-only execution — no GPU dependency
- Works on Android API 26+
- Supports IndicConformer and VITS architectures
- Single runtime for STT, TTS, and Translation

### Quantization
All models converted to INT8 before deployment:
```
Original FP32 model (~300 MB)
        ↓
INT8 Quantization (ONNX tools)
        ↓
Quantized model (~75-80 MB)
50-70% size reduction
2x faster inference on CPU
Minimal accuracy loss (<1% WER increase)
```

---

## 3. Speech-to-Text (STT)

| Component | Technology | Source |
|---|---|---|
| Primary STT (8 Indian languages) | AI4Bharat IndicConformer | huggingface.co/ai4bharat |
| Fallback STT (Gujarati, Odia, English) | OpenAI Whisper Small (quantized) | openai/whisper |
| Model format | ONNX INT8 | Converted from PyTorch checkpoint |
| Inference runtime | ONNX Runtime Android | onnxruntime.ai |

### IndicConformer Details
- Architecture: Conformer encoder + CTC decoder
- Training data: Shrutilipi, IndicSUPERB, CommonVoice India
- Languages: Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali + more
- Input: 16kHz mono PCM audio
- Output: Unicode text in respective script

### Whisper Small Details
- Architecture: Encoder-Decoder Transformer
- Used for: English, Gujarati, Odia (where IndicConformer data is weak)
- Quantized to INT8 via ONNX export
- Input: 16kHz mono, mel spectrogram
- Output: Unicode text

---

## 4. Voice Activity Detection (VAD)

| Component | Technology | Size | License |
|---|---|---|---|
| VAD Engine | Silero VAD | 1.8 MB | MIT |
| Runtime | TensorFlow Lite | included in app | Apache 2.0 |

### Why Silero VAD
- Runs at 10ms audio chunks — very low latency
- CPU usage during idle: < 3% (critical for efficiency score)
- Detects speech onset and pause/end accurately
- Works across all Indian language phonetics
- 1.8 MB — small enough to bundle inside APK

### VAD Logic
```
Continuous audio stream from microphone
        ↓
Silero VAD processes 10ms chunks
        ↓
Speech detected → start buffering audio
        ↓
Silence/pause detected (> 500ms threshold)
        ↓
Send buffered audio chunk to STT
```

---

## 5. Text-to-Speech (TTS)

| Component | Technology | Source |
|---|---|---|
| Primary TTS (10 Indian languages) | AI4Bharat IndicTTS (VITS) | ai4bharat.org |
| English TTS | Coqui TTS VITS | coqui.ai |
| Model format | ONNX INT8 | Converted from PyTorch |
| Inference runtime | ONNX Runtime Android | onnxruntime.ai |

### VITS Architecture
- End-to-end model: text → waveform directly
- No separate vocoder needed
- More natural than FastSpeech2 + HiFi-GAN pipeline
- Single model file per language
- Input: Unicode text string
- Output: Raw PCM audio waveform at 22050 Hz

---

## 6. Translation (Optional Layer)

| Component | Technology | Size | License |
|---|---|---|---|
| Translation Engine | AI4Bharat IndicTrans2 distilled | ~120 MB | MIT |
| Runtime | ONNX Runtime Android | — | MIT |
| Languages | All 10 Indian languages + English | — | — |

### When Translation Runs
```
sender_language == receiver_language → SKIP (zero latency)
sender_language != receiver_language → RUN IndicTrans2
```
Translation only loads into memory when a cross-language session is active. It is not loaded during same-language communication.

---

## 7. Networking Layer

### Wi-Fi Mode (Primary)
| Component | Technology | Purpose |
|---|---|---|
| Transport | TCP Socket (Java/Kotlin) | Reliable ordered delivery |
| Server | ServerSocket on port 54321 | Host phone runs this |
| Client | Socket to 192.168.43.1:54321 | Guest phones connect here |
| Host IP | 192.168.43.1 (Android hotspot default) | No discovery needed |
| Payload | JSON text packets | ~200-500 bytes per message |

### Bluetooth Mode (Fallback)
| Component | Technology | Purpose |
|---|---|---|
| Profile | Bluetooth Classic RFCOMM | Serial data over Bluetooth |
| Protocol | SPP (Serial Port Profile) | Standard BT serial |
| Topology | Point-to-point only | 2-phone walkie-talkie |
| Use case | No hotspot available | Field / quick deployment |

### Packet Format
```json
{
  "type": "MESSAGE",
  "room_code": "482731",
  "sender_id": "user_01",
  "sender_lang": "Hindi",
  "text": "सभी को सतर्क रहना है",
  "timestamp": 1718023400123,
  "is_alert": false
}
```

---

## 8. Model Delivery & Storage

| Component | Technology | Purpose |
|---|---|---|
| Download manager | Android WorkManager | Background model downloads |
| Storage location | Internal app storage | /Android/data/com.itantra.app/files/ |
| File validation | SHA256 checksum | Verify model integrity after download |
| Model loader | ONNX Runtime SessionOptions | Lazy load on first use |

### Storage Budget Per Language
```
STT model (INT8 ONNX)    ~75 MB
TTS model (INT8 ONNX)    ~45 MB
Total per language       ~120 MB

Pre-installed (Hindi + English)   ~230 MB
APK base size                      ~42 MB
Total fresh install footprint     ~272 MB
```

---

## 9. Full Stack Summary

```
┌─────────────────────────────────────────────────┐
│                   UI LAYER                       │
│         Kotlin · Android SDK · Jetpack           │
├─────────────────────────────────────────────────┤
│               APPLICATION LAYER                  │
│   Room Manager · PTT State Machine · Alert Handler│
│        Language Pack Manager · WorkManager        │
├──────────────────┬──────────────────────────────┤
│   AUDIO LAYER    │      NETWORK LAYER            │
│  AudioRecord     │  TCP Socket (Wi-Fi)           │
│  AudioTrack      │  Bluetooth RFCOMM             │
│  AudioManager    │  JSON Packet Protocol         │
├──────────────────┴──────────────────────────────┤
│                  ML LAYER                         │
│  Silero VAD (TFLite)  ·  ONNX Runtime            │
│  IndicConformer (STT) ·  IndicTTS VITS (TTS)     │
│  Whisper Small        ·  IndicTrans2              │
├─────────────────────────────────────────────────┤
│               STORAGE LAYER                       │
│     Internal App Storage · ONNX model files       │
│         SHA256 validation · Lazy loading          │
└─────────────────────────────────────────────────┘
```

---

## 10. What You Need to Install / Set Up

### Development Machine
```
Android Studio (latest stable)
JDK 17
Android SDK Platform API 26, 33, 34
Python 3.10+ (for model conversion scripts)
pip install onnx onnxruntime optimum transformers torch
```

### Model Conversion (Run Once)
```
1. Download IndicConformer checkpoints from AI4Bharat HuggingFace
2. Export to ONNX using optimum or torch.onnx.export
3. Quantize to INT8 using onnxruntime.quantization
4. Validate output quality (run WER test)
5. Place .onnx files in model server or ship with app
```

### Android Dependencies (build.gradle)
```
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("com.google.code.gson:gson:2.10.1")
```

---

*iTantra Tech Stack v1.0 · *
