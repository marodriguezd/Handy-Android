package com.handy.android

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostProcessorTest {
    private val context = FileBackedContext()

    @After
    fun cleanUp() {
        context.root.deleteRecursively()
    }

    @Test
    fun defaultsEnableTheLocalPipeline() {
        assertTrue(SettingsManager.postProcessingEnabled(context))
        assertTrue(SettingsManager.autoCapitalizationEnabled(context))
        assertTrue(SettingsManager.punctuationCleanupEnabled(context))
    }

    @Test
    fun explicitRulesAndPlainWordsRespectTokenBoundaries() {
        SettingsManager.setCustomWords(
            context,
            listOf(
                "handy = Handy",
                "c++ = C++",
                "open ai = Open AI",
            ),
        )

        assertEquals(
            "Handy and C++ and Open AI; handymen and open airline",
            PostProcessor.process(context, "handy and c++ and open ai; handymen and open airline"),
        )
    }

    @Test
    fun punctuationCleanupCollapsesWhitespaceAndNormalizesPunctuation() {
        SettingsManager.setAutoCapitalizationEnabled(context, false)

        assertEquals(
            "hello, world! next?",
            PostProcessor.process(context, "  hello  ,   world!  next?  "),
        )
    }

    @Test
    fun autoCapitalizationHandlesSentencesAndIsolatedI() {
        SettingsManager.setPunctuationCleanupEnabled(context, false)

        assertEquals(
            "Hello there. I am ready! You are?",
            PostProcessor.process(context, "hello there. i am ready! you are?"),
        )
    }

    @Test
    fun cleanupCanBeDisabledIndependently() {
        SettingsManager.setPunctuationCleanupEnabled(context, false)
        SettingsManager.setAutoCapitalizationEnabled(context, false)

        assertEquals(
            "Hello  , world",
            PostProcessor.process(context, "Hello  , world"),
        )
    }

    @Test
    fun disablingPostProcessingReturnsTextWithoutTransformations() {
        SettingsManager.setPostProcessingEnabled(context, false)
        SettingsManager.setCustomWords(context, listOf("hello = goodbye"))

        assertEquals("  hello  , i  ", PostProcessor.process(context, "  hello  , i  "))
    }

    private class FileBackedContext : ContextWrapper(null) {
        val root: File = Files.createTempDirectory("handy-post-processor-test").toFile()

        override fun getFilesDir(): File = root

        override fun getApplicationContext(): Context = this
    }
}
