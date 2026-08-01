package com.handy.android

import android.content.Context

/** Applies local, deterministic cleanup to text returned by Whisper. */
object PostProcessor {
    private val whitespace = Regex("\\s+")
    private val punctuationWithLeadingSpace = Regex("\\s+([,.;:!?])")
    private val punctuationWithoutTrailingSpace = Regex("([,;:!?])(?=[\\p{L}\\p{N}])")
    private val isolatedLowercaseI = Regex("(?<![\\p{L}\\p{N}_])i(?![\\p{L}\\p{N}_])")
    private val wordCharacter = Regex("[\\p{L}\\p{N}_]")

    /**
     * Processes a transcription using the settings stored for [context].
     *
     * Rules are entered one per line. A plain entry canonicalizes matching
     * text to that entry, while `key = value` replaces the key with value.
     */
    fun process(context: Context, text: String): String {
        if (!SettingsManager.postProcessingEnabled(context)) return text

        var result = applyCustomWords(text, SettingsManager.customWords(context))
        if (SettingsManager.punctuationCleanupEnabled(context)) {
            result = normalizePunctuation(result)
        }
        if (SettingsManager.autoCapitalizationEnabled(context)) {
            result = capitalizeSentences(result)
        }
        return result.trim()
    }

    private fun applyCustomWords(text: String, entries: List<String>): String {
        val rules = entries.mapNotNull(::parseRule)
            .distinctBy { it.key.lowercase() }
            .sortedByDescending { it.key.length }

        return rules.fold(text) { current, rule ->
            rule.pattern.replace(current) { rule.value }
        }
    }

    private fun parseRule(entry: String): ReplacementRule? {
        val line = entry.trim()
        if (line.isEmpty()) return null

        val separator = line.indexOf('=')
        val key: String
        val value: String
        if (separator < 0) {
            key = line
            value = line
        } else {
            key = line.substring(0, separator).trim()
            value = line.substring(separator + 1).trim()
        }
        if (key.isEmpty() || value.isEmpty()) return null

        // Unlike \b, these boundaries also work for phrases and terms such
        // as C++, C#, or R&D without matching them inside a larger token.
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}_])${Regex.escape(key)}(?![\\p{L}\\p{N}_])",
            setOf(RegexOption.IGNORE_CASE),
        )
        return ReplacementRule(key, value, pattern)
    }

    private fun normalizePunctuation(text: String): String = text
        .replace(whitespace, " ")
        .replace(punctuationWithLeadingSpace, "$1")
        .replace(punctuationWithoutTrailingSpace, "$1 ")
        .trim()

    private fun capitalizeSentences(text: String): String {
        val characters = text.toCharArray()
        var capitalizeNext = true

        characters.forEachIndexed { index, character ->
            if (character.isLetter()) {
                if (capitalizeNext) characters[index] = character.uppercaseChar()
                capitalizeNext = false
            }

            if (character == '!' || character == '?') {
                capitalizeNext = true
            } else if (character == '.' && isSentencePeriod(characters, index)) {
                capitalizeNext = true
            }
        }

        return String(characters).replace(isolatedLowercaseI) { "I" }
    }

    private fun isSentencePeriod(characters: CharArray, index: Int): Boolean {
        val nextIndex = characters.indexOfFirstNonWhitespace(index + 1)
        if (nextIndex < 0) return true

        // Do not treat decimal points or periods inside domains/identifiers as
        // sentence boundaries (for example, 3.14 or handy.com).
        val previous = characters.getOrNull(index - 1)
        val next = characters[nextIndex]
        return !next.isDigit() &&
            !(previous?.let(::isWordCharacter) == true && isWordCharacter(next) && nextIndex == index + 1)
    }

    private fun CharArray.indexOfFirstNonWhitespace(start: Int): Int {
        for (index in start until size) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }

    private fun isWordCharacter(character: Char): Boolean =
        wordCharacter.matches(character.toString())

    private data class ReplacementRule(
        val key: String,
        val value: String,
        val pattern: Regex,
    )
}
