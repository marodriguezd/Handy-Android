package com.handy.app.postprocess

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests for [PostProcessor].
 *
 * Uses a tiny [ServerSocket]-based HTTP server so the tests run without
 * relying on `com.sun.net.httpserver.HttpServer`, which is not available on
 * the Android unit-test classpath. The server runs on a free local port and
 * is torn down after each test.
 */
class PostProcessorTest {

    private var server: TestHttpServer? = null
    private var baseUrl: String = ""

    @Before
    fun setUp() {
        server = TestHttpServer().apply { start() }
        baseUrl = "http://127.0.0.1:${server!!.port}"
    }

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    @Test
    fun `process completes URL and sends correct payload`(): Unit = runBlocking {
        server?.responseBody = """{"choices":[{"message":{"content":" corrected"}}]}"""

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
        assertEquals("/", server?.lastPath)

        val body = JSONObject(server?.lastBody)
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
        server?.responseBody = """{"choices":[{"message":{"content":"ok"}}]}"""

        val processor = PostProcessor(apiUrl = baseUrl, apiKey = null, modelName = "gpt-test")

        processor.process(
            rawText = "just text",
            promptTemplate = "Correct: \${output}",
            hotwordHints = emptyList(),
        )

        val body = JSONObject(server?.lastBody)
        val messages = body.getJSONArray("messages")
        val userContent = messages.getJSONObject(1).getString("content")
        assertEquals("Transcript:\njust text", userContent)
    }

    @Test
    fun `process returns failure on non-200 response`(): Unit = runBlocking {
        server?.responseCode = 401
        server?.responseBody = """{"error":"unauthorized"}"""

        val processor = PostProcessor(apiUrl = baseUrl, apiKey = "bad", modelName = "gpt-test")
        val result = processor.process("text", "prompt")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    /**
     * Minimal single-threaded HTTP server backed by [ServerSocket].
     * Accepts one connection at a time, records the request path/body,
     * and replies with the configured status code and JSON body.
     */
    private class TestHttpServer {
        private var serverSocket: ServerSocket? = null
        private var thread: Thread? = null
        private val running = AtomicBoolean(false)

        var responseCode = 200
        var responseBody = ""
        var lastPath: String? = null
        var lastBody: String? = null

        val port: Int get() = serverSocket?.localPort ?: throw IllegalStateException("Server not started")

        fun start() {
            serverSocket = ServerSocket(0).apply { reuseAddress = true }
            running.set(true)
            thread = Thread({ serveLoop() }, "TestHttpServer").apply { start() }
        }

        fun stop() {
            running.set(false)
            serverSocket?.close()
            thread?.join(1000L)
        }

        private fun serveLoop() {
            while (running.get()) {
                var socket: Socket? = null
                try {
                    socket = serverSocket?.accept() ?: return
                    handle(socket)
                } catch (_: Exception) {
                    // Expected when serverSocket is closed during stop().
                } finally {
                    socket?.close()
                }
            }
        }

        private fun handle(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), false, StandardCharsets.UTF_8)

            val requestLine = reader.readLine() ?: return
            lastPath = requestLine.split(" ").getOrNull(1)

            // Read headers until blank line.
            var contentLength = 0
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line == null || line.isEmpty()) break
                val header = line.split(":", limit = 2)
                if (header.size == 2 && header[0].trim().equals("Content-Length", ignoreCase = true)) {
                    contentLength = header[1].trim().toIntOrNull() ?: 0
                }
            }

            lastBody = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(chars, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                String(chars, 0, read)
            } else {
                ""
            }

            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            writer.print("HTTP/1.1 $responseCode OK\r\n")
            writer.print("Content-Type: application/json\r\n")
            writer.print("Content-Length: ${bytes.size}\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }
}
