iTantra Neural Voice Transceiver — Local AI Models Directory
============================================================

Place pre-converted INT8 ONNX and TFLite model files in this directory (or in the app's internal storage /Android/data/com.itantra.app/files/models/):

1. Silero VAD (TFLite, 1.8 MB):
   - silero_vad.tflite
   - Source: https://github.com/snakers4/silero-vad/raw/master/files/silero_vad.tflite

2. Hindi Speech-to-Text (AI4Bharat IndicConformer INT8 ONNX, ~75 MB):
   - stt_hi.onnx
   - Source: huggingface.co/ai4bharat/indicconformer-hi-onnx

3. Hindi Text-to-Speech (AI4Bharat IndicTTS VITS INT8 ONNX, ~45 MB):
   - tts_hi.onnx
   - Source: huggingface.co/ai4bharat/indic-tts-hi-onnx

4. English Speech-to-Text (OpenAI Whisper Small INT8 ONNX, ~70 MB):
   - stt_en.onnx
   - Source: openai/whisper

5. English Text-to-Speech (Coqui VITS INT8 ONNX, ~40 MB):
   - tts_en.onnx
   - Source: coqui.ai

6. Gujarati Models:
   - stt_gu.onnx (~80 MB)
   - tts_gu.onnx (~48 MB)

7. Translation (AI4Bharat IndicTrans2 Distilled INT8 ONNX, ~120 MB):
   - indictrans2.onnx
   - Source: huggingface.co/ai4bharat/indictrans2-distilled-onnx

Note: The app automatically detects model presence. If a model file is not present, the app gracefully reports "Model not installed" in the UI without crashing.
