package com.itantra.app.ml

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.data.ModelInfo
import com.itantra.app.data.ModelType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages on-device model discovery, storage paths, validation,
 * and background download via Android WorkManager.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        const val MODELS_DIR_NAME = "models"

        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "vad_silero",
                name = "Silero VAD",
                fileName = "silero_vad.tflite",
                expectedSizeMb = 2,
                isInstalled = false,
                isBundled = true,
                languageCode = "all",
                type = ModelType.VAD
            ),
            ModelInfo(
                id = "stt_hi",
                name = "Hindi STT (IndicConformer)",
                fileName = "stt_hi.onnx",
                expectedSizeMb = 75,
                isInstalled = false,
                isBundled = false,
                languageCode = "hi",
                type = ModelType.STT
            ),
            ModelInfo(
                id = "tts_hi",
                name = "Hindi TTS (IndicTTS VITS)",
                fileName = "tts_hi.onnx",
                expectedSizeMb = 45,
                isInstalled = false,
                isBundled = false,
                languageCode = "hi",
                type = ModelType.TTS
            ),
            ModelInfo(
                id = "stt_en",
                name = "English STT (Whisper Small)",
                fileName = "stt_en.onnx",
                expectedSizeMb = 70,
                isInstalled = false,
                isBundled = false,
                languageCode = "en",
                type = ModelType.STT
            ),
            ModelInfo(
                id = "tts_en",
                name = "English TTS (Coqui VITS)",
                fileName = "tts_en.onnx",
                expectedSizeMb = 40,
                isInstalled = false,
                isBundled = false,
                languageCode = "en",
                type = ModelType.TTS
            ),
            ModelInfo(
                id = "stt_gu",
                name = "Gujarati STT (IndicConformer)",
                fileName = "stt_gu.onnx",
                expectedSizeMb = 80,
                isInstalled = false,
                isBundled = false,
                languageCode = "gu",
                type = ModelType.STT
            ),
            ModelInfo(
                id = "tts_gu",
                name = "Gujarati TTS (IndicTTS VITS)",
                fileName = "tts_gu.onnx",
                expectedSizeMb = 48,
                isInstalled = false,
                isBundled = false,
                languageCode = "gu",
                type = ModelType.TTS
            ),
            ModelInfo(
                id = "translation_indic",
                name = "Translation (IndicTrans2 Distilled)",
                fileName = "indictrans2.onnx",
                expectedSizeMb = 120,
                isInstalled = false,
                isBundled = false,
                languageCode = "all",
                type = ModelType.TRANSLATION
            )
        )
    }

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, MODELS_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getModelsDirectoryPath(): String = modelsDir.absolutePath

    /**
     * Resolves the model file either from internal storage or assets
     */
    fun getModelFile(fileName: String): File? {
        val file = File(modelsDir, fileName)
        if (file.exists() && file.length() > 0) {
            return file
        }
        return null
    }

    /**
     * Checks whether a model file exists either in internal storage or assets.
     */
    fun isModelInstalled(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        if (file.exists() && file.length() > 0) return true

        // Check assets
        return try {
            context.assets.open("models/$fileName").use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if both STT and TTS models are available for a given communication language.
     */
    fun isLanguageReady(language: CommunicationLanguage): Boolean {
        val sttReady = isModelInstalled(language.sttModelFile)
        val ttsReady = isModelInstalled(language.ttsModelFile)
        return sttReady && ttsReady
    }

    /**
     * Returns the list of all models with their current on-disk installation status.
     */
    fun getAllModelsStatus(): List<ModelInfo> {
        return AVAILABLE_MODELS.map { model ->
            val installed = isModelInstalled(model.fileName)
            val file = File(modelsDir, model.fileName)
            val sizeMb = if (file.exists()) (file.length() / (1024 * 1024)).toInt() else model.expectedSizeMb
            model.copy(
                isInstalled = installed,
                expectedSizeMb = if (installed && file.exists()) sizeMb else model.expectedSizeMb
            )
        }
    }

    /**
     * Verifies SHA256 checksum of an installed model file.
     */
    fun verifyChecksum(fileName: String, expectedSha256: String): Boolean {
        val file = getModelFile(fileName) ?: return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            val hashBytes = digest.digest()
            val computedHash = hashBytes.joinToString("") { "%02x".format(it) }
            computedHash.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256 for $fileName", e)
            false
        }
    }

    /**
     * Enqueues background model download via WorkManager.
     */
    fun enqueueDownload(fileName: String, downloadUrl: String) {
        val data = Data.Builder()
            .putString("file_name", fileName)
            .putString("download_url", downloadUrl)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .addTag("model_download_$fileName")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Worker for downloading models in the background.
     */
    class ModelDownloadWorker(
        appContext: Context,
        workerParams: WorkerParameters
    ) : Worker(appContext, workerParams) {

        override fun doWork(): Result {
            val fileName = inputData.getString("file_name") ?: return Result.failure()
            val downloadUrl = inputData.getString("download_url") ?: return Result.failure()

            val targetDir = File(applicationContext.filesDir, MODELS_DIR_NAME)
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            val tempFile = File(targetDir, "$fileName.tmp")

            return try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return Result.retry()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempFile.renameTo(targetFile)) {
                    Result.success()
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download worker failed: ${e.message}", e)
                Result.retry()
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }
}
