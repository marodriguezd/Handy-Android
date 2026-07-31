package com.handy.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelDownloader(private val context: Context) {
    data class Model(
        val id: String,
        val displayName: String,
        val fileName: String,
        val downloadUrl: String,
        val catalogId: String,
        val description: String,
        val parameters: String,
        val languageCount: Int,
        val downloadSizeBytes: Long,
        /** Published SHA-256 from the desktop catalog; null means not authenticated. */
        val expectedSha256: String? = null,
    ) {
        val localFile: File
            get() = File(requireNotNull(directory), fileName)

        private var directory: File? = null

        internal fun inDirectory(directory: File): Model = apply { this.directory = directory }
    }

    private val directory: File = File(context.filesDir, "models").apply { mkdirs() }

    companion object {
        // Serialize downloads across Activity instances in the same app process.
        private val downloadMutex = Mutex()
    }

    /**
     * Downloadable entries are limited to formats the current Android JNI
     * backend can load. The storefront itself is broader; see [ModelCatalog].
     */
    val availableModels: List<Model>
        get() = ModelCatalog.downloadableModels.mapNotNull { entry ->
            val download = entry.androidDownload ?: return@mapNotNull null
            Model(
                id = download.id,
                displayName = entry.name,
                fileName = download.fileName,
                downloadUrl = download.url,
                catalogId = entry.id,
                description = entry.description,
                parameters = entry.parameters,
                languageCount = entry.languageCount,
                downloadSizeBytes = entry.downloadSizeBytes,
                expectedSha256 = download.sha256,
            ).inDirectory(directory)
        }

    fun installedModels(): List<File> = directory.listFiles()
        ?.filter { TranscriptionEngine.isSupportedModel(it) }
        ?.sortedBy { it.name }
        .orEmpty()

    fun expectedSha256(file: File): String? = availableModels
        .firstOrNull { it.fileName == file.name }
        ?.expectedSha256

    /** Downloads, hashes, and loads a model before replacing the final file. */
    suspend fun download(
        model: Model,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        engineFactory: () -> IWhisperEngine = { WhisperLib() },
    ): File = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            check(model.downloadUrl.startsWith("https://", ignoreCase = true)) {
                "Model download URL must use HTTPS"
            }
            val target = model.localFile
            val partial = File(target.path + ".part")
            partial.delete()
            val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("Model download failed with HTTP ${connection.responseCode}")
                }
                check(connection.url.protocol.equals("https", ignoreCase = true)) {
                    "Model download redirected to an insecure URL"
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(bytes)
                            if (read < 0) break
                            output.write(bytes, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
                check(partial.isFile && partial.length() > 0L) { "Downloaded model is empty" }
                val validation = ModelValidator.validate(partial, model.expectedSha256, engineFactory)
                if (SettingsManager.activeModelName(context) == target.name) {
                    // Fail closed while replacing an active model; it must be explicitly reactivated.
                    SettingsManager.clearActiveModel(context)
                }
                replaceFile(partial, target)
                ModelValidator.writeDigestFile(target, validation)
                target
            } finally {
                connection.disconnect()
                if (partial.exists()) partial.delete()
            }
        }
    }

    /** Revalidates a local model and makes it active only after native Whisper accepts it. */
    suspend fun validateAndActivate(
        file: File,
        engineFactory: () -> IWhisperEngine = { WhisperLib() },
    ): ModelValidationResult = withContext(Dispatchers.IO) {
        require(installedModels().any { it.name == file.name }) { "Model is not installed: ${file.name}" }
        val result = ModelValidator.validateAndRecord(file, expectedSha256(file), engineFactory)
        SettingsManager.setActiveModel(context, file.name, result)
        result
    }

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            target.delete()
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        check(target.isFile && target.length() > 0L) { "Could not finalize downloaded model" }
    }
}
