# iTantra — System Architecture Document
**Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links**
**SIH Problem Statement ID: 26173**
**Team: DEPSTAR · CHARUSAT**

---

## 1. What We Are Actually Building

iTantra is a symmetric offline voice communication system where human speech is compressed to text by STT, transmitted over any low-bitrate link, and reconstructed as speech by TTS — supporting 10 Indian languages on low-end Android hardware.

The core innovation is the transmission layer:

```
Traditional voice call:
Raw Audio → Network → Raw Audio
Bandwidth required: 64,000 – 256,000 bits/second

iTantra approach:
Voice → STT → Text → Network → TTS → Voice
Bandwidth required: ~300 bits/sentence (1000x reduction)
```

This makes voice communication possible over:
- Local Wi-Fi hotspot
- Bluetooth
- Any low-bitrate radio or serial link

---

## 2. Core Design Decisions (Finalised)

| Decision | Choice | Reason |
|---|---|---|
| STT Engine | AI4Bharat IndicConformer (ONNX INT8) | Best Indian language coverage, open source |
| TTS Engine | AI4Bharat IndicTTS VITS (ONNX INT8) | Most natural Indian language speech |
| VAD | Silero VAD (TFLite, 1.8 MB) | Near-zero CPU idle, triggers STT on pause |
| Translation | IndicTrans2 distilled (ONNX) | All 10 Indian languages, offline |
| Runtime | ONNX Runtime for Android | Open source, CPU-only, API 26+ |
| Networking | Android Hotspot + TCP Socket | Infrastructure-free, Zender/Sharity pattern |
| Fallback Network | Bluetooth RFCOMM | 2-phone walkie-talkie without hotspot |
| App size strategy | On-demand language pack download | APK stays ~40-50 MB |
| Minimum Android | API 26 (Android 8) | Covers low-end devices |

---

## 3. Model Storage Architecture

### Problem
Bundling all 10 language models inside APK = ~1.2 GB.
Play Store rejects APKs over 100 MB.
Low-end phones cannot handle this.

### Solution: On-Demand Language Packs

```
APK (installed from Play Store)
├── App code + UI           ~15 MB
├── ONNX Runtime library    ~25 MB
└── Silero VAD model         ~1.8 MB
Total APK: ~42 MB
```

After install, models download to phone internal storage:

```
/Android/data/com.itantra.app/files/models/
├── vad/
│   └── silero_vad.tflite          1.8 MB  [bundled in APK]
├── hindi/
│   ├── stt_hi.onnx                ~75 MB  [downloaded on demand]
│   └── tts_hi.onnx                ~45 MB  [downloaded on demand]
├── tamil/
│   ├── stt_ta.onnx                ~75 MB  [downloaded on demand]
│   └── tts_ta.onnx                ~45 MB  [downloaded on demand]
└── english/
    ├── stt_en.onnx                ~70 MB  [downloaded on demand]
    └── tts_en.onnx                ~40 MB  [downloaded on demand]
```

**Pre-bundled with install:** Hindi + English (~230 MB on device)
**Each additional language:** ~120 MB downloaded when user selects it
**User downloads only what they need.**

---

## 4. Model Selection — All 10 Languages

### STT Models

| Language | Model | Size (INT8) | Expected WER |
|---|---|---|---|
| Hindi | ai4bharat/indicconformer-hi | ~75 MB | ~8% |
| Tamil | ai4bharat/indicconformer-ta | ~75 MB | ~11% |
| Telugu | ai4bharat/indicconformer-te | ~75 MB | ~12% |
| Kannada | ai4bharat/indicconformer-kn | ~75 MB | ~14% |
| Malayalam | ai4bharat/indicconformer-ml | ~75 MB | ~13% |
| Marathi | ai4bharat/indicconformer-mr | ~75 MB | ~15% |
| Bengali | ai4bharat/indicconformer-bn | ~75 MB | ~14% |
| Gujarati | indicconformer-gu / whisper-small-gu | ~80 MB | ~20-25% |
| Odia | indicconformer-or / whisper-small-or | ~80 MB | ~22-28% |
| English | whisper-small.en quantized | ~70 MB | ~5% |

**Note:** Gujarati and Odia have less training data. WER will be higher. Show per-language metrics honestly in demo — evaluators respect transparency.

### TTS Models

| Language | Model | Size | Quality |
|---|---|---|---|
| Hindi | indicTTS-hi (VITS) | ~45 MB | High |
| Tamil | indicTTS-ta (VITS) | ~45 MB | High |
| Telugu | indicTTS-te (VITS) | ~45 MB | High |
| Kannada | indicTTS-kn (VITS) | ~45 MB | Medium-High |
| Malayalam | indicTTS-ml (VITS) | ~45 MB | Medium-High |
| Marathi | indicTTS-mr (VITS) | ~45 MB | Medium |
| Bengali | indicTTS-bn (VITS) | ~45 MB | Medium-High |
| Gujarati | indicTTS-gu (VITS) | ~48 MB | Medium |
| Odia | indicTTS-or (VITS) | ~48 MB | Medium-Low |
| English | Coqui VITS en | ~40 MB | High |

---

## 5. Network Architecture

### Connection Logic (Zender/Sharity Pattern)

```
Person A
└── Turns on Android Hotspot
└── Opens iTantra → Creates Room → Gets Code: 482731
└── Becomes HOST + TCP Server on port 54321

Person B, C, D
└── Connect to Person A's hotspot (Wi-Fi)
└── Open iTantra → Enter Room Code: 482731
└── App connects to 192.168.43.1:54321
    (Android hotspot host IP is always 192.168.43.1)
└── Sends JOIN packet
└── Host validates → added to room
└── All members can now communicate
```

### Why 192.168.43.1
Android hotspot always assigns this IP to the host device. No mDNS or IP discovery needed. Clients always know where to connect.

### Room JOIN Packet

```json
{
  "type": "JOIN",
  "room_code": "482731",
  "user_id": "dhruv_01",
  "language": "Hindi",
  "device_name": "Redmi Note 10"
}
```

### Message Packet (Text transmission over network)

```json
{
  "type": "MESSAGE",
  "room_code": "482731",
  "sender_id": "dhruv_01",
  "sender_lang": "Hindi",
  "text": "सभी को सतर्क रहना है",
  "timestamp": 1718023400123,
  "is_alert": false
}
```

### Alert Packet

```json
{
  "type": "MESSAGE",
  "room_code": "482731",
  "sender_id": "dhruv_01",
  "sender_lang": "Hindi",
  "text": "तुरंत मदद चाहिए",
  "timestamp": 1718023400123,
  "is_alert": true
}
```

### Network Modes

```
Wi-Fi Hotspot Mode (Primary)
├── One phone creates hotspot
├── Others connect to hotspot
├── TCP socket server on host port 54321
├── Supports multiple phones in one room
└── Latency: 5-20ms (text only)

Bluetooth Mode (Fallback)
├── 2-phone only
├── Bluetooth Classic RFCOMM / SPP Profile
├── No hotspot needed
├── Quick pairing walkie-talkie
└── Latency: 50-80ms
```

---

## 6. Full System Pipeline

### Sender Side (Phone A — Speaking)

```
MICROPHONE
    │
    ▼
AUDIO CAPTURE
Android AudioRecord API
16 kHz · 16-bit · Mono PCM
    │
    ▼
VOICE ACTIVITY DETECTION
Silero VAD · TFLite · 1.8 MB
Runs continuously
Detects speech start and pause/end
CPU usage during idle: < 3%
    │
    ├── No speech detected → keep listening (idle loop)
    │
    └── Speech detected → buffer audio
            │
            ▼
        AUDIO BUFFER
        Ring buffer · 30 second sliding window
        Holds audio from speech onset to silence
            │
            ▼ (on pause/stoppage detected by VAD)
        STT INFERENCE
        IndicConformer / Whisper · ONNX Runtime · INT8
        Runs on phone CPU · Background thread
        Target: < 1.5 seconds for 5 seconds of speech
            │
            ▼
        TEXT OUTPUT
        UTF-8 string · ~50-200 bytes per sentence
            │
            ▼
        BUILD MESSAGE PACKET
        JSON with text, lang, sender_id, is_alert flag
            │
            ▼
        TRANSMIT over TCP Socket / Bluetooth RFCOMM
        Text only · ~200-500 bytes per packet
        Network latency: ~10-30ms on local Wi-Fi
```

### Network Layer (Host Phone — Routing)

```
RECEIVE PACKET from sender
    │
    ▼
VALIDATE room_code
    │
    ▼
BROADCAST to all connected clients in room
(raw packet — host does not translate or process)
    │
    ▼
Each client receives same packet independently
```

### Receiver Side (Phone B — Listening)

```
RECEIVE PACKET
TCP Socket / Bluetooth RFCOMM
    │
    ▼
PARSE JSON
Extract: text, sender_lang, is_alert
    │
    ├── is_alert = true
    │       │
    │       ▼
    │   SET AudioManager.STREAM_ALARM
    │   Maximum volume
    │   Acquire WakeLock (wake screen)
    │   Non-interruptible flag
    │
    └── is_alert = false → normal playback
    │
    ▼
CHECK LANGUAGE
sender_lang == receiver_lang?
    │
    ├── YES → skip translation (zero latency path)
    │
    └── NO
            │
            ▼
        TRANSLATION
        IndicTrans2 distilled · ONNX · ~120 MB
        Runs on receiver device CPU
        Target: < 500ms per sentence
    │
    ▼
TTS INFERENCE
IndicTTS VITS · ONNX Runtime · INT8
Generates audio waveform from text
Target: < 800ms per sentence
    │
    ▼
AUDIO PLAYBACK
Android AudioTrack
Speech plays on speaker
Text shown on screen simultaneously
```

---

## 7. Operating Modes

### Mode 1 — Push to Talk (Walkie-Talkie)

```
User presses and holds PTT button
    │
    ▼
Microphone activates
VAD runs inside PTT window
STT fires on detected pause within hold
    │
    ▼
User releases PTT button
    │
    ▼
Force-send current text segment immediately
    │
    ▼
Receiver TTS plays
    │
    ▼
PTT indicator visible to all room members
Half-duplex: only one sender at a time
Host enforces this
```

### Mode 2 — Phone Mode (Continuous)

```
VAD runs continuously without PTT
Auto-detects speech start and end
STT fires after each sentence pause
Sends text automatically
Full-duplex: both sides active simultaneously
Works like a voice messaging app
Text transcript shown on screen
```

### Mode 3 — Alert Mode

```
Sender taps Alert button before speaking
    │
    ▼
is_alert = true in packet
    │
    ▼ (on receiver)
AudioManager.STREAM_ALARM
Volume set to maximum
WakeLock acquired → screen wakes
Non-interruptible TTS playback
Vibration pattern triggered
Other audio ducked
Cannot be dismissed until playback complete
```

---

## 8. Latency Budget

### Target
End-to-end: sentence spoken on Phone A → same sentence starts playing on Phone B

### Budget Breakdown

```
Stage                           Low-End Device    Mid-Range Device
─────────────────────────────   ──────────────    ────────────────
VAD pause detection             ~50ms             ~30ms
STT inference (5s of speech)    ~1800ms           ~900ms
Text packet transmission        ~20ms             ~10ms
Translation (if needed)         ~600ms            ~300ms
TTS synthesis (10 words)        ~1000ms           ~500ms
Audio buffer + playback start   ~80ms             ~50ms
─────────────────────────────   ──────────────    ────────────────
TOTAL (same language)           ~2.95 seconds     ~1.49 seconds
TOTAL (cross-language)          ~3.55 seconds     ~1.79 seconds
```

### Optimisations

- Stream partial STT results during speech rather than waiting for full pause
- Pre-warm ONNX models at app startup so first inference has no model load delay
- Skip translation entirely when sender and receiver use same language
- Use Whisper Tiny as fast-path fallback for very short utterances

---

## 9. Efficiency Targets

```
Metric                        Target          How
────────────────────────────  ──────────────  ────────────────────────────────
APK install size              ~42 MB          No models bundled except VAD
RAM during idle listening     < 120 MB        Only VAD active when idle
RAM during STT inference      < 350 MB        ONNX INT8 reduces FP32 footprint
CPU during idle               < 3%            Silero VAD at 10ms chunks
CPU during STT (burst)        60-90%          Background thread, UI never blocks
Battery (1 hour active)       < 15% drain     VAD is 95% of runtime
Per language pack size        ~120 MB         Downloaded to internal storage
```

---

## 10. Android App Architecture

```
iTantra Android App
├── Foreground Service
│   └── Keeps microphone and VAD running when app is backgrounded
│
├── Audio Module
│   ├── AudioRecord (microphone capture)
│   ├── Silero VAD (speech detection)
│   ├── STT Engine (IndicConformer via ONNX Runtime)
│   └── AudioTrack (playback)
│
├── ML Module
│   ├── ONNX Runtime session manager
│   ├── Model loader (loads from internal storage)
│   ├── STT inference runner
│   ├── TTS inference runner
│   └── Translation runner (IndicTrans2)
│
├── Network Module
│   ├── TCP Server (host mode — runs on port 54321)
│   ├── TCP Client (guest mode — connects to 192.168.43.1:54321)
│   ├── Bluetooth RFCOMM server/client
│   └── Message router (broadcast to room members)
│
├── Room Module
│   ├── Room code generator
│   ├── Member registry
│   ├── PTT state machine
│   └── Alert handler
│
├── Language Pack Manager
│   ├── Download manager (WorkManager)
│   ├── Model file validator
│   └── Storage manager (delete unused packs)
│
└── UI Layer
    ├── Home screen (create/join room)
    ├── Room screen (PTT button, member list, transcript)
    ├── Language settings
    ├── Alert button
    └── Metrics display (latency, WER stats)
```

---

## 11. Technology Stack

```
Layer               Technology                      License
──────────────────  ──────────────────────────────  ───────────
Language            Kotlin                          Apache 2.0
Android SDK         Android API 26+                 Open
STT Models          AI4Bharat IndicConformer        Apache 2.0
TTS Models          AI4Bharat IndicTTS (VITS)       Open
STT Fallback        OpenAI Whisper Small (quantized) MIT
TTS Fallback        Coqui TTS                       MPL 2.0
VAD                 Silero VAD                      MIT
Translation         AI4Bharat IndicTrans2           MIT
ML Runtime          ONNX Runtime for Android        MIT
VAD Runtime         TensorFlow Lite                 Apache 2.0
Networking          Android TCP Sockets             Open
Bluetooth           Android Bluetooth RFCOMM API    Open
```

All components are open source. No proprietary SDK anywhere in the stack. PS compliant.

---

## 12. PS Compliance Checklist

```
Requirement                                     Status    Note
──────────────────────────────────────────────  ────────  ─────────────────────────────────
10 Indian languages supported                   DONE      All 10 covered with models mapped
Runs on low-power device                        DONE      CPU-only, API 26+, <350MB RAM
Fully offline                                   DONE      Zero internet dependency
Open source only                                DONE      All Apache/MIT/MPL licensed
STT activates on pause/stoppage detection       DONE      Silero VAD handles this
Text streamed instantly over Wi-Fi/Bluetooth    DONE      TCP socket, ~200 bytes per packet
TTS plays received text as speech               DONE      IndicTTS VITS on receiver
Alert messages at max volume non-interruptible  DONE      STREAM_ALARM + WakeLock
PTT walkie-talkie mode                          DONE      PTT state machine
Continuous phone mode when PTT off              DONE      Full-duplex VAD mode
Low bandwidth transmission                      DONE      Text only, 1000x reduction vs audio
```

---

## 13. Known Limitations (Be Honest in Demo)

```
1. Gujarati and Odia WER will be 20-28%
   Reason: less training data available publicly
   Mitigation: show per-language WER table, do not hide it

2. E2E latency on low-end devices is ~3 seconds
   Reason: STT inference is compute heavy on Snapdragon 4xx
   Mitigation: this is acceptable for distress/field communication
   A walkie-talkie already has PTT delay

3. STT errors propagate directly to TTS
   Reason: no error correction layer
   Example: "sector 4" → "sector 9" if STT mishears
   Mitigation: show confidence score to sender, allow retransmit
   Mark as future work

4. Group mode over Bluetooth not supported
   Reason: Bluetooth RFCOMM is point-to-point
   Mitigation: group mode uses Wi-Fi hotspot only
   Bluetooth is 2-phone fallback only
```x