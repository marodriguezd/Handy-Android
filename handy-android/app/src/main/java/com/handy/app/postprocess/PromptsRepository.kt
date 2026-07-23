package com.handy.app.postprocess

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Prompt(
    val id: String,
    val name: String,
    val body: String
) {
    companion object {
        const val BUILTIN_ID = "__builtin__"
        const val DEFAULT_BODY = "Corrige la puntuación, ortografía y formato del siguiente texto transcrito. Mantén el significado original intacto.\n\nTranscripción:\n\${output}"
    }
}

class PromptsRepository(
    private val file: File,
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "prompts.json"),
        context.applicationContext.getSharedPreferences("handy_prompts_prefs", Context.MODE_PRIVATE),
    )

    @Synchronized
    fun getPrompts(): List<Prompt> {
        val list = mutableListOf<Prompt>()
        list.add(getBuiltinPrompt())
        list.addAll(loadCustomPrompts())
        return list
    }

    /**
     * Export all custom prompts (without the built-in default) as a JSON array.
     * Caller is responsible for choosing the destination (clipboard, file, etc.).
     */
    @Synchronized
    fun exportToJson(): String {
        val custom = loadCustomPrompts()
        val jsonArray = JSONArray()
        for (p in custom) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("body", p.body)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    /**
     * Import custom prompts from a JSON array string. Each object must contain
     * at least `name` and `body`; missing `id` is auto-generated. Existing
     * custom prompts are replaced by the imported list.
     * @return true if at least one prompt was imported successfully.
     */
    @Synchronized
    fun importFromJson(json: String): Boolean {
        val imported = mutableListOf<Prompt>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                val body = obj.optString("body", "").trim()
                if (name.isEmpty() || body.isEmpty()) continue
                val id = obj.optString("id", "").ifEmpty { java.util.UUID.randomUUID().toString() }
                imported.add(Prompt(id, name, body))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse prompt import JSON", e)
            return false
        }
        writeCustomPrompts(imported)
        return imported.isNotEmpty()
    }

    @Synchronized
    fun getActivePrompt(): Prompt {
        val activeId = prefs.getString(KEY_ACTIVE_PROMPT_ID, Prompt.BUILTIN_ID) ?: Prompt.BUILTIN_ID
        return getPrompts().find { it.id == activeId } ?: getBuiltinPrompt()
    }

    @Synchronized
    fun setActivePromptId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROMPT_ID, id).apply()
    }

    @Synchronized
    fun savePrompt(prompt: Prompt) {
        val custom = loadCustomPrompts().toMutableList()
        val index = custom.indexOfFirst { it.id == prompt.id }
        if (index >= 0) {
            custom[index] = prompt
        } else {
            custom.add(prompt)
        }
        writeCustomPrompts(custom)
    }

    @Synchronized
    fun deletePrompt(id: String) {
        if (id == Prompt.BUILTIN_ID) return
        val custom = loadCustomPrompts().filter { it.id != id }
        writeCustomPrompts(custom)
        if (prefs.getString(KEY_ACTIVE_PROMPT_ID, "") == id) {
            setActivePromptId(Prompt.BUILTIN_ID)
        }
    }

    private fun getBuiltinPrompt(): Prompt {
        return Prompt(
            id = Prompt.BUILTIN_ID,
            name = "Predeterminado (Corrección General)",
            body = Prompt.DEFAULT_BODY
        )
    }

    private fun loadCustomPrompts(): List<Prompt> {
        val list = mutableListOf<Prompt>()
        if (!file.exists()) return list
        try {
            val text = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(text)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Prompt(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        body = obj.getString("body")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load prompts from JSON", e)
        }
        return list
    }

    private fun writeCustomPrompts(prompts: List<Prompt>) {
        val jsonArray = JSONArray()
        for (p in prompts) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("body", p.body)
            }
            jsonArray.put(obj)
        }
        try {
            file.parentFile?.mkdirs()
            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write prompts to JSON", e)
        }
    }

    companion object {
        private const val TAG = "PromptsRepository"
        private const val KEY_ACTIVE_PROMPT_ID = "active_prompt_id"
    }
}
