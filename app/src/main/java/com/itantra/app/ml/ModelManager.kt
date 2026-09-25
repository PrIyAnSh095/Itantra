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
import com.itantra.app.data.ModelDownloadState
import com.itantra.app.data.ModelInfo
import com.itantra.app.data.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages on-device model discovery, storage paths, validation,
 * live downloading with progress tracking, and storage management.
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
                type = ModelType.VAD,
                downloadUrl = "https://github.com/snakers4/silero-vad/raw/master/files/silero_vad.tflite"
            ),
            ModelInfo(
                id = "stt_hi",
                name = "Hindi STT (IndicConformer)",
                fileName = "stt_hi.onnx",
                expectedSizeMb = 75,
                isInstalled = false,
                isBundled = false,
                languageCode = "hi",
                type = ModelType.STT,
                downloadUrl = "https://huggingface.co/ai4bharat/indicconformer-hi-onnx/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "tts_hi",
                name = "Hindi TTS (IndicTTS VITS)",
                fileName = "tts_hi.onnx",
                expectedSizeMb = 45,
                isInstalled = false,
                isBundled = false,
                languageCode = "hi",
                type = ModelType.TTS,
                downloadUrl = "https://huggingface.co/ai4bharat/indic-tts-hi-onnx/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "stt_en",
                name = "English STT (Whisper Small)",
                fileName = "stt_en.onnx",
                expectedSizeMb = 70,
                isInstalled = false,
                isBundled = false,
                languageCode = "en",
                type = ModelType.STT,
                downloadUrl = "https://huggingface.co/openai/whisper-small-onnx/resolve/main/encoder_model.onnx"
            ),
            ModelInfo(
                id = "tts_en",
                name = "English TTS (Coqui VITS)",
                fileName = "tts_en.onnx",
                expectedSizeMb = 40,
                isInstalled = false,
                isBundled = false,
                languageCode = "en",
                type = ModelType.TTS,
                downloadUrl = "https://huggingface.co/coqui/vits-en-onnx/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "stt_gu",
                name = "Gujarati STT (IndicConformer)",
                fileName = "stt_gu.onnx",
                expectedSizeMb = 80,
                isInstalled = false,
                isBundled = false,
                languageCode = "gu",
                type = ModelType.STT,
                downloadUrl = "https://huggingface.co/ai4bharat/indicconformer-gu-onnx/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "tts_gu",
                name = "Gujarati TTS (IndicTTS VITS)",
                fileName = "tts_gu.onnx",
                expectedSizeMb = 48,
                isInstalled = false,
                isBundled = false,
                languageCode = "gu",
                type = ModelType.TTS,
                downloadUrl = "https://huggingface.co/ai4bharat/indic-tts-gu-onnx/resolve/main/model.onnx"
            ),
            ModelInfo(
                id = "translation_indic",
                name = "Translation (IndicTrans2 Distilled)",
                fileName = "indictrans2.onnx",
                expectedSizeMb = 120,
                isInstalled = false,
                isBundled = false,
                languageCode = "all",
                type = ModelType.TRANSLATION,
                downloadUrl = "https://huggingface.co/ai4bharat/indictrans2-indic-indic-onnx/resolve/main/model.onnx"
            )
        )
    }

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, MODELS_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    // In-memory download tracking
    private val activeDownloads = ConcurrentHashMap<String, Int>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

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
     * Returns the list of all models with their current on-disk installation status
     * and active download progress if applicable.
     */
    fun getAllModelsStatus(): List<ModelInfo> {
        return AVAILABLE_MODELS.map { model ->
            val installed = isModelInstalled(model.fileName)
            val file = File(modelsDir, model.fileName)
            val sizeMb = if (file.exists()) (file.length() / (1024 * 1024)).toInt() else model.expectedSizeMb

            val progress = activeDownloads[model.fileName]
            val state = when {
                progress != null && progress in 0..99 -> ModelDownloadState.DOWNLOADING
                installed -> ModelDownloadState.INSTALLED
                else -> ModelDownloadState.IDLE
            }

            model.copy(
                isInstalled = installed,
                expectedSizeMb = if (installed && file.exists()) sizeMb else model.expectedSizeMb,
                downloadState = state,
                downloadProgress = progress ?: if (installed) 100 else 0
            )
        }
    }

    /**
     * Downloads a model file directly over HTTP/HTTPS with live percentage callback.
     */
    suspend fun downloadModel(
        model: ModelInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        val downloadUrl = model.downloadUrl ?: run {
            onResult(false, "No download URL available for ${model.name}")
            return
        }

        withContext(Dispatchers.IO) {
            val targetDir = modelsDir
            val targetFile = File(targetDir, model.fileName)
            val tempFile = File(targetDir, "${model.fileName}.tmp")

            var connection: HttpURLConnection? = null
            try {
                activeDownloads[model.fileName] = 0
                val url = URL(downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 45000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0")
                connection.setRequestProperty("Accept", "*/*")
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    activeDownloads.remove(model.fileName)
                    val errorDetail = if (connection.responseCode == 401) {
                        "401 Unauthorized: The remote server requires authentication. You can use 'Instant Demo Setup' or enter a custom model link."
                    } else {
                        "HTTP ${connection.responseCode}: ${connection.responseMessage}"
                    }
                    withContext(Dispatchers.Main) {
                        onResult(false, errorDetail)
                    }
                    return@withContext
                }

                val contentLength = connection.contentLength.toLong()
                val totalBytes = if (contentLength > 0) contentLength else (model.expectedSizeMb * 1024L * 1024L)

                var totalRead = 0L
                val buffer = ByteArray(32768)

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val percent = if (totalBytes > 0) {
                                ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 99)
                            } else 50

                            activeDownloads[model.fileName] = percent
                            withContext(Dispatchers.Main) {
                                onProgress(percent, totalRead, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (renamed) {
                        activeDownloads.remove(model.fileName)
                        withContext(Dispatchers.Main) {
                            onProgress(100, targetFile.length(), targetFile.length())
                            onResult(true, null)
                        }
                    } else {
                        activeDownloads.remove(model.fileName)
                        withContext(Dispatchers.Main) {
                            onResult(false, "Failed to rename temporary file.")
                        }
                    }
                } else {
                    activeDownloads.remove(model.fileName)
                    withContext(Dispatchers.Main) {
                        onResult(false, "Downloaded file is empty.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for ${model.fileName}: ${e.message}", e)
                activeDownloads.remove(model.fileName)
                if (tempFile.exists()) tempFile.delete()
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Download connection error")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Deletes an installed model from local storage.
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Creates a lightweight demo/test model file on device for offline testing and demonstration.
     */
    fun createDemoModel(fileName: String): Boolean {
        return try {
            val targetDir = modelsDir
            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile).use { output ->
                val header = "iTantra_Demo_Model_${fileName}_ONNX".toByteArray()
                output.write(header)
                val dummy = ByteArray(2048)
                for (i in 0 until 50) {
                    output.write(dummy)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating demo model $fileName: ${e.message}", e)
            false
        }
    }

    fun createDemoModelsForLanguage(language: CommunicationLanguage): Boolean {
        val sttOk = createDemoModel(language.sttModelFile)
        val ttsOk = createDemoModel(language.ttsModelFile)
        return sttOk && ttsOk
    }

    /**
     * Imports a model file from an InputStream (e.g. from Storage Access Framework).
     */
    fun importModel(inputStream: InputStream, targetFileName: String): Boolean {
        return try {
            val targetFile = File(modelsDir, targetFileName)
            val tempFile = File(modelsDir, "$targetFileName.tmp")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing model $targetFileName: ${e.message}", e)
            false
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
                connection.connectTimeout = 20000
                connection.readTimeout = 45000
                connection.connect()

                if (connection.responseCode !in 200..299) {
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
