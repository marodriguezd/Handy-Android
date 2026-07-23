package com.handy.app.postprocess

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Unit tests for [PostProcessor].
 *
 * Uses `com.sun.net.httpserver.HttpServer` (JDK built-in) to verify
 * the generated request body and URL completion behavior without
 * adding external mocking dependencies.
 */
class PostProcessorTest {

    private var server: HttpServer? = null
    private var baseUrl: String = ""
    private var lastPath: String? = null
    private var lastBody: String? = null
    private var responseCode: Int = 200
    private var responseBody: String = """{"choices":[{"message":{"content":" corrected"}}]}"""

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                lastPath = exchange.requestURI.path
                lastBody = exchange.requestBody.bufferedReader().use { it.readText() }
                val bytes = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(responseCode, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        baseUrl = "http://127.0.0.1:${server?.address?.port}"
    }

    @After
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    @Test
    fun `process completes URL and sends correct payload`(): Unit = runBlocking {
        val processor = PostProcessor(
            apiUrl = baseUrl,
            apiKey = "sk-test",
            modelName = "gpt-test",
        )

        val result = processor.process(
            rawText = "hello world",
            promptTemplate = "Fix punctuation: \${output}",
            hotwordHints = listOf("Parakeet"),
        )

        assertTrue(result.isSuccess)
        assertEquals("corrected", result.getOrThrow().trim())
        assertEquals("/$", lastPath)

        val body = JSONObject(lastBody)
        assertEquals("gpt-test", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Fix punctuation:", messages.getJSONObject(0).getString("content").trim())
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val userContent = messages.getJSONObject(1).getString("content")
        assertTrue(userContent.contains("Parakeet"))
        assertTrue(userContent.contains("hello world"))
    }

    @Test
    fun `process omits hotword header when hints are empty`(): Unit = runBlocking {
        val processor = PostProcessor(apiUrl = baseUrl, apiKey = null, modelName = "gpt-test")

        processor.process(
            rawText = "just text",
            promptTemplate = "Correct: \${output}",
            hotwordHints = emptyList(),
        )

        val body = JSONObject(lastBody)
        val messages = body.getJSONArray("messages")
        val userContent = messages.getJSONObject(1).getString("content")
        assertEquals("Transcript:\njust text", userContent)
    }

    @Test
    fun `process returns failure on non-200 response`(): Unit = runBlocking {
        responseCode = 401
        responseBody = """{"error":"unauthorized"}"""

        val processor = PostProcessor(apiUrl = baseUrl, apiKey = "bad", modelName = "gpt-test")
        val result = processor.process("text", "prompt")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
