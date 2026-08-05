package com.handy.android

import android.content.Context
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional network post-processing; local rules remain the guaranteed fallback. */
object LlmPostProcessor {
    /**
     * Refines [text] with the configured OpenAI-compatible endpoint. The
     * in-flight coroutine is registered in [LlmCallRegistry] under [owner] so a
     * surface can cancel only its own request (see [cancelForOwner]).
     *
     * @param owner identity of the calling surface (Activity/Service). `null`
     *   means ownerless: globally cancellable via [cancelAll] but never by an
     *   owner-scoped cancel.
     */
    suspend fun process(context: Context, text: String, owner: Any? = null): String {
        val fallback = PostProcessor.process(context, text)
        if (text.isBlank() || !SettingsManager.llmEnabled(context)) return fallback
        if (!isEndpointAllowed(SettingsManager.llmEndpoint(context))) return fallback

        val deferred = coroutineScope {
            async(Dispatchers.IO) {
                val self = coroutineContext[Job] ?: error("no job in scope")
                LlmCallRegistry.register(self, owner)
                try {
                    runCatching { request(context, text) }
                        .getOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: fallback
                } finally {
                    LlmCallRegistry.unregister(self)
                }
            }
        }
        return withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: fallback
    }

    /** Cancels only the in-flight LLM calls owned by [owner] (by identity). */
    fun cancelForOwner(owner: Any?) = LlmCallRegistry.cancelAllForOwner(owner)

    /** Cancels every in-flight LLM call (global shutdown: feature off, IME death). */
    fun cancelAll() = LlmCallRegistry.cancelAll()

    internal fun isEndpointAllowed(endpoint: String): Boolean = runCatching {
        val uri = URI(endpoint)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true) && host in LOCAL_HOSTS
    }.getOrDefault(false)

    private suspend fun request(context: Context, text: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(SettingsManager.llmEndpoint(context)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS.toInt()
            readTimeout = NETWORK_TIMEOUT_MS.toInt()
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            SettingsManager.llmApiKey(context).takeIf(String::isNotBlank)?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
        try {
            val body = JSONObject()
                .put("model", SettingsManager.llmModel(context))
                .put(
                    "messages",
                    org.json.JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SettingsManager.llmSystemPrompt(context)))
                        .put(JSONObject().put("role", "user").put("content", text)),
                )
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@withContext ""
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MS = 5_000L
    private const val NETWORK_TIMEOUT_MS = 4_500L
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
}
