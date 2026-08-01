package com.handy.android

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPostProcessorTest {
    private val context = FileBackedContext()

    @After
    fun cleanUp() {
        context.root.deleteRecursively()
    }

    @Test
    fun llmSettingsHaveSafeDefaultsAndPersist() {
        assertFalse(SettingsManager.llmEnabled(context))
        assertEquals("https://api.openai.com/v1/chat/completions", SettingsManager.llmEndpoint(context))
        assertEquals("gpt-4o-mini", SettingsManager.llmModel(context))

        SettingsManager.setLlmEnabled(context, true)
        SettingsManager.setLlmEndpoint(context, "http://localhost:11434/v1/chat/completions")
        SettingsManager.setLlmApiKey(context, "secret")
        SettingsManager.setLlmModel(context, "llama3")
        SettingsManager.setLlmSystemPrompt(context, "Return only text")

        assertTrue(SettingsManager.llmEnabled(context))
        assertEquals("http://localhost:11434/v1/chat/completions", SettingsManager.llmEndpoint(context))
        assertEquals("secret", SettingsManager.llmApiKey(context))
        assertEquals("llama3", SettingsManager.llmModel(context))
        assertEquals("Return only text", SettingsManager.llmSystemPrompt(context))
    }

    @Test
    fun disabledLlmUsesLocalProcessorWithoutNetwork() = runBlocking {
        SettingsManager.setCustomWords(context, listOf("hello = Hello"))
        assertEquals("Hello", LlmPostProcessor.process(context, "hello"))
    }

    private class FileBackedContext : ContextWrapper(null) {
        val root: File = Files.createTempDirectory("handy-llm-test").toFile()
        override fun getFilesDir(): File = root
        override fun getApplicationContext(): Context = this
    }
}
