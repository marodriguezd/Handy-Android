package com.handy.app.postprocess

import android.content.Context
import android.util.AtomicFile
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

class PromptsRepository(context: Context) {

    private val context = context.applicationContext
    private val file = AtomicFile(File(this.context.filesDir, "prompts.json"))
    private val prefs = this.context.getSharedPreferences("handy_prompts_prefs", Context.MODE_PRIVATE)

    @Synchronized
    fun getPrompts(): List<Prompt> {
        val list = mutableListOf<Prompt>()
        list.add(getBuiltinPrompt())
        list.addAll(loadCustomPrompts())
        return list
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
        if (!file.baseFile.exists()) return list
        try {
            val bytes = file.readFully()
            val jsonArray = JSONArray(String(bytes))
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
        var fos: java.io.FileOutputStream? = null
        try {
            fos = file.startWrite()
            fos.write(jsonArray.toString(2).toByteArray())
            file.finishWrite(fos)
        } catch (e: Exception) {
            if (fos != null) {
                file.failWrite(fos)
            }
            Log.e(TAG, "Failed to write prompts to JSON", e)
        }
    }

    companion object {
        private const val TAG = "PromptsRepository"
        private const val KEY_ACTIVE_PROMPT_ID = "active_prompt_id"
    }
}
