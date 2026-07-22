package com.handy.app.corrector

import org.junit.Assert.assertEquals
import org.junit.Test

class WordCorrectorTest {

    @Test
    fun testFilterTranscriptionOutput_removesFillersAndStutters() {
        val inputEs = "ehm este hola probando probando probando sonido"
        val filteredEs = WordCorrector.filterTranscriptionOutput(inputEs, "es")
        assertEquals("hola probando sonido", filteredEs)

        val inputEn = "uh um hello world world world test"
        val filteredEn = WordCorrector.filterTranscriptionOutput(inputEn, "en")
        assertEquals("hello world test", filteredEn)
    }

    @Test
    fun testApplyCustomWords_soundexAndLevenshteinMatching() {
        val customWords = listOf("Parakeet", "ChatGPT", "R&D")
        val corrector = WordCorrector(customWords, threshold = 0.35)

        val text1 = "estamos probando el modelo paraket en android"
        val result1 = corrector.applyCustomWords(text1)
        assertEquals("estamos probando el modelo Parakeet en android", result1)
    }

    @Test
    fun testApplyCustomWords_preservesCapitalizationAndPunctuation() {
        val customWords = listOf("Parakeet")
        val corrector = WordCorrector(customWords, threshold = 0.35)

        val text = "¿PARAKET?"
        val result = corrector.applyCustomWords(text)
        assertEquals("¿PARAKEET?", result)
    }

    @Test
    fun testNormalizeChinesePunctuation() {
        val input = "你好,世界.这是测试?"
        val output = WordCorrector.filterTranscriptionOutput(input, "zh")
        assertEquals("你好，世界。这是测试？", output)
    }
}
