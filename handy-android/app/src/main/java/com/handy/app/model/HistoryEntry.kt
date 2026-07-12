package com.handy.app.model

import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class HistoryEntry(
    val id: Long,
    val text: String,
    val postProcessedText: String?,
    val timestamp: Long,
    val isSaved: Boolean,
    val audioPath: String?,
) {
    companion object {
        fun fromJsonArray(json: String): List<HistoryEntry> {
            if (json.isBlank()) return emptyList()
            val result = mutableListOf<HistoryEntry>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    HistoryEntry(
                        id = obj.getLong("id"),
                        text = obj.getString("text"),
                        postProcessedText = obj.optString("post_processed_text", null),
                        timestamp = obj.getLong("timestamp"),
                        isSaved = obj.getBoolean("is_saved"),
                        audioPath = obj.optString("audio_path", null),
                    )
                )
            }
            return result
        }
    }

    fun formattedDate(): String {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val entryTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val daysDiff = ChronoUnit.DAYS.between(entryTime.toLocalDate(), now.toLocalDate())

        val timePart = entryTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        return when {
            daysDiff == 0L -> "Today $timePart"
            daysDiff == 1L -> "Yesterday $timePart"
            daysDiff < 7L -> "${entryTime.dayOfWeek.name.take(3)} $timePart"
            else -> entryTime.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }
}
