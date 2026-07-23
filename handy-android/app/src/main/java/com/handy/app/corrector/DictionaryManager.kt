package com.handy.app.corrector

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.handy.app.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.UUID

/**
 * Manages persistent user dictionaries and custom word list overrides in files/dictionaries.json.
 */
class DictionaryManager(context: Context) {

    companion object {
        private const val TAG = "DictionaryManager"
        private const val FILE_NAME = "dictionaries.json"
        private const val KEY_HOTWORDS = "custom_hotwords"
        private const val PREFS_NAME = "transcribe_settings"
    }

    private val appContext = context.applicationContext
    private val dictionariesFile = File(appContext.filesDir, FILE_NAME)
    private var dictionaries: MutableList<Dictionary> = mutableListOf()
    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) {
            loaded = true
            dictionaries = load()
            migrateFromPreferences()
        }
    }

    private fun load(): MutableList<Dictionary> {
        val list = mutableListOf<Dictionary>()
        if (!dictionariesFile.exists()) return list

        try {
            FileInputStream(dictionariesFile).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    val root = JSONObject(sb.toString())
                    val arr = root.optJSONArray("dictionaries")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            list.add(Dictionary.fromJson(arr.getJSONObject(i)))
                        }
                    }
                    val override = root.optJSONObject("default_override")
                    if (override != null) {
                        val name = override.optString(
                            "name",
                            appContext.getString(R.string.name_default_dictionary)
                        )
                        val enabled = override.optBoolean("enabled", true)
                        val words = mutableListOf<String>()
                        val wordsArr = override.optJSONArray("words")
                        if (wordsArr != null) {
                            for (i in 0 until wordsArr.length()) {
                                val w = wordsArr.optString(i, null)
                                if (!w.isNullOrEmpty()) words.add(w)
                            }
                        }
                        if (name.isNotEmpty()) {
                            list.add(Dictionary(Dictionary.DEFAULT_ID, name, words, enabled))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionaries", e)
        }
        return list
    }

    private fun save() {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            var overrideJson: JSONObject? = null

            for (d in dictionaries) {
                if (d.isDefault()) {
                    val o = d.toJson()
                    o.remove("id")
                    overrideJson = o
                    continue
                }
                arr.put(d.toJson())
            }
            root.put("dictionaries", arr)
            if (overrideJson != null) {
                root.put("default_override", overrideJson)
            }
            root.put("schema_version", 2)

            FileOutputStream(dictionariesFile).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write(root.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save dictionaries", e)
        }
    }

    private fun migrateFromPreferences() {
        val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldWords = prefs.getStringSet(KEY_HOTWORDS, null)
        if (!oldWords.isNullOrEmpty()) {
            val defaultDict = Dictionary(
                Dictionary.DEFAULT_ID,
                appContext.getString(R.string.name_default_dictionary),
                oldWords.toMutableList(),
                true
            )
            dictionaries.add(defaultDict)
            save()
            prefs.edit().remove(KEY_HOTWORDS).apply()
        }
    }

    fun getAll(): List<Dictionary> {
        ensureLoaded()
        return ArrayList(dictionaries)
    }

    fun getDefault(): Dictionary {
        ensureLoaded()
        for (d in dictionaries) {
            if (d.isDefault()) return d
        }
        return Dictionary(
            Dictionary.DEFAULT_ID,
            appContext.getString(R.string.name_default_dictionary),
            mutableListOf(),
            true
        )
    }

    fun isDefaultOverridden(): Boolean {
        ensureLoaded()
        return dictionaries.any { it.isDefault() }
    }

    fun getActiveWordsList(): List<String> {
        ensureLoaded()
        val ordered = mutableListOf<Dictionary>()
        var def: Dictionary? = null
        for (d in dictionaries) {
            if (d.isDefault()) def = d else ordered.add(d)
        }
        if (def == null) def = getDefault()
        ordered.add(def)

        val allWords = mutableListOf<String>()
        for (d in ordered) {
            if (d.isEnabled) {
                for (word in d.words) {
                    val eqIdx = word.indexOf('=')
                    if (eqIdx >= 0) {
                        allWords.add(word.substring(eqIdx + 1))
                    } else {
                        allWords.add(word)
                    }
                }
            }
        }
        return allWords
    }

    fun getById(id: String): Dictionary? {
        ensureLoaded()
        if (Dictionary.DEFAULT_ID == id) return getDefault()
        return dictionaries.firstOrNull { it.id == id }
    }

    fun addDictionary(dictionary: Dictionary) {
        ensureLoaded()
        if (Dictionary.DEFAULT_ID == dictionary.id) {
            dictionary.id = UUID.randomUUID().toString()
        }
        dictionaries.add(dictionary)
        save()
    }

    fun updateDictionary(updated: Dictionary) {
        ensureLoaded()
        if (updated.isDefault()) {
            val idx = dictionaries.indexOfFirst { it.isDefault() }
            if (idx >= 0) {
                dictionaries[idx] = updated
            } else {
                dictionaries.add(updated)
            }
            save()
            return
        }
        val idx = dictionaries.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            dictionaries[idx] = updated
            save()
        }
    }

    fun deleteDictionary(id: String) {
        ensureLoaded()
        if (Dictionary.DEFAULT_ID == id) {
            val removed = dictionaries.removeAll { it.isDefault() }
            if (removed) save()
            return
        }
        dictionaries.removeAll { it.id == id }
        save()
    }

    fun addWord(dictId: String, word: String) {
        ensureLoaded()
        if (Dictionary.DEFAULT_ID == dictId) {
            val def = getDefault()
            if (!def.words.contains(word)) {
                def.words.add(word)
                updateDictionary(def)
            }
            return
        }
        val d = getById(dictId)
        if (d != null && !d.words.contains(word)) {
            d.words.add(word)
            save()
        }
    }

    fun removeWord(dictId: String, word: String) {
        ensureLoaded()
        if (Dictionary.DEFAULT_ID == dictId) {
            val def = getDefault()
            if (def.words.contains(word)) {
                def.words.remove(word)
                updateDictionary(def)
            }
            return
        }
        val d = getById(dictId)
        if (d != null) {
            d.words.remove(word)
            save()
        }
    }
}
