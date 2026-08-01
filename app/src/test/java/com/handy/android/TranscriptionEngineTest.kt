package com.handy.android

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionEngineTest {
    private val temporaryDirectories = mutableListOf<File>()

    @After
    fun cleanUp() {
        temporaryDirectories.asReversed().forEach { directory ->
            directory.walkBottomUp().forEach(File::delete)
        }
    }

    @Test
    fun isSupportedModelReturnsTrueOnlyForBinFiles() {
        val binFile = temporaryFile("model.bin", "data")
        val txtFile = temporaryFile("model.txt", "data")
        val noExtFile = temporaryFile("model", "data")

        assertTrue(TranscriptionEngine.isSupportedModel(binFile))
        assertFalse(TranscriptionEngine.isSupportedModel(txtFile))
        assertFalse(TranscriptionEngine.isSupportedModel(noExtFile))
    }

    @Test
    fun isValidatedModelRequiresRecordedDigest() {
        val binFile = temporaryFile("model.bin", "valid model content")
        assertFalse(TranscriptionEngine.isValidatedModel(binFile))

        val result = ModelValidationResult(ModelValidator.sha256(binFile), binFile.length())
        ModelValidator.writeDigestFile(binFile, result)
        assertTrue(TranscriptionEngine.isValidatedModel(binFile))
    }

    @Test
    fun cacheReusesOneEngineForTheSameModel() {
        val cache = WhisperEngineCache()
        val engines = mutableListOf<FakeWhisperEngine>()

        val first = cache.get("first.bin") { FakeWhisperEngine().also(engines::add) }
        val second = cache.get("first.bin") { FakeWhisperEngine().also(engines::add) }

        assertSame(first, second)
        assertEquals(1, engines.size)
        assertEquals(1, engines.single().initCount)
        assertEquals("first.bin", cache.cachedModelPath())
    }

    @Test
    fun cacheClosesOldEngineWhenTheActiveModelChanges() {
        val cache = WhisperEngineCache()
        val engines = mutableListOf<FakeWhisperEngine>()

        cache.get("first.bin") { FakeWhisperEngine().also(engines::add) }
        val second = cache.get("second.bin") { FakeWhisperEngine().also(engines::add) }

        assertEquals(2, engines.size)
        assertEquals(1, engines[0].cancelCount)
        assertEquals(1, engines[0].closeCount)
        assertSame(engines[1], second)
        assertEquals("second.bin", cache.cachedModelPath())
    }

    @Test
    fun clearingCacheCancelsAndClosesTheNativeEngine() {
        val cache = WhisperEngineCache()
        val engine = FakeWhisperEngine()
        cache.get("model.bin") { engine }

        cache.clear()

        assertEquals(1, engine.cancelCount)
        assertEquals(1, engine.closeCount)
        assertEquals(null, cache.cachedModelPath())
    }

    @Test
    fun cacheClearModelsMemoryPressureEviction() {
        val cache = WhisperEngineCache()
        val engine = FakeWhisperEngine()
        cache.get("model.bin") { engine }

        cache.clear()

        assertEquals(1, engine.cancelCount)
        assertEquals(1, engine.closeCount)
        assertEquals(null, cache.cachedModelPath())
    }

    @Test
    fun transcribeReturnsPostProcessedText() = runBlocking {
        val context = FileBackedContext()
        val modelsDirectory = File(context.filesDir, "models")
        try {
            val model = File(modelsDirectory, "model.bin").apply {
                parentFile?.mkdirs()
                writeText("validated model")
            }
            val validation = ModelValidationResult(ModelValidator.sha256(model), model.length())
            ModelValidator.writeDigestFile(model, validation)
            SettingsManager.setActiveModel(context, model.name, validation)
            SettingsManager.setCustomWords(context, listOf("hello = Hello"))

            val result = TranscriptionEngine.transcribe(
                context = context,
                samples = floatArrayOf(0f),
                engineFactory = { FakeWhisperEngine(result = "hello i") },
            )

            assertEquals("Hello I", result)
        } finally {
            TranscriptionEngine.evictForMemoryForTests()
            modelsDirectory.deleteRecursively()
            context.root.deleteRecursively()
        }
    }

    private fun temporaryFile(name: String, content: String): File {
        val directory = Files.createTempDirectory("handy-transcription-test").toFile()
        temporaryDirectories += directory
        return File(directory, name).apply { writeText(content) }
    }

    private class FileBackedContext : ContextWrapper(null) {
        val root: File = Files.createTempDirectory("handy-transcription-context-test").toFile()

        override fun getFilesDir(): File = root

        override fun getApplicationContext(): Context = this

        override fun registerComponentCallbacks(callback: ComponentCallbacks) = Unit

        override fun unregisterComponentCallbacks(callback: ComponentCallbacks) = Unit
    }

    private class FakeWhisperEngine(private val result: String = "fake result") : IWhisperEngine {
        var initCount = 0
        var cancelCount = 0
        var closeCount = 0

        override fun init(modelPath: String): Boolean {
            initCount += 1
            return true
        }

        override fun transcribe(
            audioData: FloatArray,
            numThreads: Int,
            translate: Boolean,
            language: String,
        ): String = result

        override fun cancelTranscribe() {
            cancelCount += 1
        }

        override fun close() {
            closeCount += 1
        }
    }
}
