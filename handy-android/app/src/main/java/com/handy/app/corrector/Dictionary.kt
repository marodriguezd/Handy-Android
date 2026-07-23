package com.handy.app.corrector

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data model for a custom word dictionary.
 */
data class Dictionary(
    var id: String = UUID.randomUUID().toString(),
    var name: String,
    var words: MutableList<String> = mutableListOf(),
    var isEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun fromJson(json: JSONObject): Dictionary {
            var rawId = json.optString("id", "")
            if (DEFAULT_ID == rawId || rawId.isBlank()) {
                rawId = UUID.randomUUID().toString()
            }
            val name = json.optString("name", "Custom Dictionary")
            val enabled = json.optBoolean("enabled", true)
            val wordList = mutableListOf<String>()
            val arr = json.optJSONArray("words")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val w = arr.optString(i, null)
                    if (!w.isNullOrBlank()) wordList.add(w)
                }
            }
            return Dictionary(rawId, name, wordList, enabled)
        }
    }

    fun isDefault(): Boolean = id == DEFAULT_ID

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("id", id)
        root.put("name", name)
        root.put("enabled", isEnabled)
        val arr = JSONArray()
        for (w in words) {
            arr.put(w)
        }
        root.put("words", arr)
        return root
    }
}
