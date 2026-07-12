package com.handy.app.model

import org.json.JSONArray

data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val language: String,
    val quant: String,
    val license: String?,
    val description: String?,
    val isDownloaded: Boolean,
    val isActive: Boolean,
    val recommended: Boolean = false,
) {
    companion object {
        fun fromJsonArray(json: String): List<ModelInfo> {
            if (json.isBlank()) return emptyList()
            val result = mutableListOf<ModelInfo>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    ModelInfo(
                        id = obj.getString("id"),
                        displayName = obj.getString("display_name"),
                        sizeBytes = obj.getLong("size_bytes"),
                        language = obj.getString("language"),
                        quant = obj.optString("quant", ""),
                        license = obj.optString("license", null),
                        description = obj.optString("description", null),
                        isDownloaded = obj.getBoolean("downloaded"),
                        isActive = obj.getBoolean("active"),
                        recommended = obj.optBoolean("recommended", false),
                    )
                )
            }
            return result
        }
    }

    fun formattedSize(): String {
        return when {
            sizeBytes >= 1_073_741_824 -> "%.1f GB".format(sizeBytes / 1_073_741_824.0)
            sizeBytes >= 1_048_576 -> "%d MB".format(sizeBytes / 1_048_576)
            sizeBytes >= 1_024 -> "%d KB".format(sizeBytes / 1_024)
            else -> "%d B".format(sizeBytes)
        }
    }
}
