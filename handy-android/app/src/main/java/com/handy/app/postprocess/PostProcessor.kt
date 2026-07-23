package com.handy.app.postprocess

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PostProcessor(
    private val apiUrl: String,
    private val apiKey: String? = null,
    private val modelName: String = "gpt-3.5-turbo"
) {

    suspend fun process(
        rawText: String,
        promptTemplate: String,
        hotwordHints: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var completionUrl = apiUrl.trim()
            if (!completionUrl.endsWith("/chat/completions")) {
                completionUrl = if (completionUrl.endsWith("/")) {
                    "${completionUrl}chat/completions"
                } else {
                    "${completionUrl}/chat/completions"
                }
            }

            val hintsHeader = if (hotwordHints.isNotEmpty()) {
                val sb = StringBuilder("Custom dictionary / hotwords reference:\n")
                for (h in hotwordHints) {
                    if (h.isNotBlank()) sb.append("- ").append(h.trim()).append("\n")
                }
                sb.append("\n").toString()
            } else ""

            var systemPrompt = promptTemplate
            val userContent = if (systemPrompt.contains("\${output}")) {
                systemPrompt = systemPrompt.replace("\${output}", "").trim()
                if (hintsHeader.isNotBlank()) {
                    "$hintsHeader\n$rawText"
                } else {
                    rawText
                }
            } else {
                if (hintsHeader.isNotBlank()) {
                    "$hintsHeader\nTranscript:\n$rawText"
                } else {
                    "Transcript:\n$rawText"
                }
            }
            if (systemPrompt.contains("\${hints}")) {
                systemPrompt = systemPrompt.replace("\${hints}", hintsHeader).trim()
            }

            val requestJson = JSONObject().apply {
                put("model", modelName)
                val messages = JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    })
                }
                put("messages", messages)
            }

            val url = URL(completionUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (!apiKey.isNull_or_Empty()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            connection.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "API HTTP error $responseCode: $errorText")
                return@withContext Result.failure(Exception("HTTP $responseCode: $errorText"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseText)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() > 0) {
                val content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(content.trim())
            } else {
                Result.failure(Exception("Empty choices in LLM response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute LLM post-processing", e)
            Result.failure(e)
        }
    }

    private fun String?.isNull_or_Empty(): Boolean = this == null || this.trim().isEmpty()

    companion object {
        private const val TAG = "PostProcessor"
    }
}
