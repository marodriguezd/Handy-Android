package com.handy.app.corrector

import java.util.regex.Pattern

/**
 * WordCorrector handles post-ASR text cleanup and custom word replacements.
 *
 * It combines Levenshtein edit distance and Soundex phonetic matching to
 * map misrecognized spoken tokens (e.g. names, technical terms) to custom user-defined
 * words, while preserving surrounding punctuation and capitalization.
 */
class WordCorrector(
    customWords: List<String>,
    private val threshold: Double = 0.35
) {
    private val entries: List<NormalizedEntry>

    init {
        val list = mutableListOf<NormalizedEntry>()
        for (word in customWords) {
            if (word.isBlank()) continue
            val wordCount = word.trim().split(Regex("\\s+")).size
            val normalized = normalize(word)
            if (normalized.isNotEmpty()) {
                list.add(NormalizedEntry(normalized, word, soundex(normalized), wordCount))
            }
            if (word.contains("&")) {
                val expanded = normalizeExpanded(word)
                if (expanded.isNotEmpty() && expanded != normalized) {
                    list.add(NormalizedEntry(expanded, word, soundex(expanded), wordCount))
                }
            }
        }
        this.entries = list
    }

    /**
     * Applies custom word replacements to the input text using N-gram (n=3..1) matching.
     *
     * IMPORTANT: a custom entry with `wordCount = k` is only ever compared against a
     * token window of the same size. Single-word entries (e.g. "Parakeet") no longer
     * match multi-token windows such as "probando el modelo", which previously caused
     * greedy over-substitution.
     */
    fun applyCustomWords(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()
        val tokens = text.trim().split(Regex("\\s+")).toTypedArray()
        val availableNs = entries.map { it.wordCount }.toSortedSet().sortedDescending()
        val result = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            if (result.isNotEmpty()) result.append(' ')
            var matched = false
            for (n in availableNs) {
                if (i + n > tokens.size) continue
                val joined = joinWithoutPunct(tokens, i, n)
                val best = findBestMatch(joined, n)
                if (best != null) {
                    val raw = StringBuilder(tokens[i])
                    for (j in i + 1 until i + n) {
                        raw.append(' ').append(tokens[j])
                    }
                    val replaced = applyCaseAndPunctuation(best.original, raw.toString())
                    result.append(replaced)
                    i += n
                    matched = true
                    break
                }
            }
            if (!matched) {
                result.append(tokens[i])
                i++
            }
        }
        return result.toString()
    }

    private fun normalize(word: String): String {
        return word.lowercase().replace(Regex("[^a-z0-9&]"), "")
    }

    private fun normalizeExpanded(word: String): String {
        return word.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
    }

    private fun joinWithoutPunct(tokens: Array<String>, start: Int, n: Int): String {
        val sb = StringBuilder()
        for (i in start until start + n) {
            sb.append(tokens[i])
        }
        return sb.toString().lowercase().replace(Regex("[^a-z0-9&]"), "")
    }

    private fun findBestMatch(joined: String, wordCount: Int): BestMatch? {
        if (joined.isEmpty()) return null
        var best: BestMatch? = null
        var bestScore = threshold
        val joinedSoundex = soundex(joined)

        for (entry in entries) {
            if (entry.wordCount != wordCount) continue
            val maxLen = maxOf(joined.length, entry.length)
            if (maxLen == 0) continue
            val lenDiff = Math.abs(joined.length - entry.length).toDouble() / maxLen.toDouble()
            if (lenDiff >= threshold) continue

            val levDist = levenshtein(joined, entry.normalized).toDouble()
            val levScore = levDist / maxLen.toDouble()

            val combinedScore = if (joinedSoundex.isNotEmpty() && joinedSoundex == entry.soundexCode) {
                levScore * 0.3
            } else {
                levScore
            }

            if (combinedScore < bestScore) {
                bestScore = combinedScore
                best = BestMatch(entry.original, combinedScore)
            }
        }
        return best
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    private fun soundex(s: String): String {
        if (s.isEmpty()) return ""
        val first = s[0].uppercaseChar()
        val tail = s.substring(1)
        val encoded = StringBuilder().append(first)
        var lastCode = soundexCode(first)

        for (ch in tail) {
            val lower = ch.lowercaseChar()
            if (lower in listOf('a', 'e', 'i', 'o', 'u', 'y')) {
                lastCode = '0'
                continue
            }
            val code = soundexCode(ch)
            if (code != '0' && code != lastCode) {
                encoded.append(code)
                lastCode = code
            }
        }
        return encoded.toString().padEnd(4, '0').take(4)
    }

    private fun soundexCode(c: Char): Char {
        return when (c.uppercaseChar()) {
            'B', 'F', 'P', 'V' -> '1'
            'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> '2'
            'D', 'T' -> '3'
            'L' -> '4'
            'M', 'N' -> '5'
            'R' -> '6'
            else -> '0'
        }
    }

    private fun applyCaseAndPunctuation(replacement: String, original: String): String {
        var start = 0
        var end = original.length
        val lead = StringBuilder()
        val trail = StringBuilder()

        while (start < end && !Character.isLetterOrDigit(original[start])) {
            lead.append(original[start])
            start++
        }
        while (end > start && !Character.isLetterOrDigit(original[end - 1])) {
            trail.insert(0, original[end - 1])
            end--
        }

        val core = original.substring(start, end)
        val resultCore = when {
            isAllUpperCase(core) -> replacement.uppercase()
            isCapitalized(core) -> replacement.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> replacement
        }
        return lead.toString() + resultCore + trail.toString()
    }

    private fun isAllUpperCase(s: String): Boolean {
        if (s.isEmpty()) return false
        return s.all { !it.isLetter() || it.isUpperCase() }
    }

    private fun isCapitalized(s: String): Boolean {
        if (s.isEmpty()) return false
        return s[0].isUpperCase()
    }

    private data class NormalizedEntry(
        val normalized: String,
        val original: String,
        val soundexCode: String,
        val wordCount: Int
    ) {
        val length: Int = normalized.length
    }

    private data class BestMatch(
        val original: String,
        val score: Double
    )

    companion object {
        private val ES_FILLER_PATTERN = Pattern.compile(
            "\\b(?:ehm|mmm|este|o sea|eh|ah|pues|bueno)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val EN_FILLER_PATTERN = Pattern.compile(
            "\\b(?:uh|um|ah|er|like|hmm|you know|i mean)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val REPEATED_WORD_PATTERN = Pattern.compile(
            "\\b(\\w+)(?:\\s+\\1){2,}\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val MULTI_SPACE_PATTERN = Pattern.compile("\\s{2,}")

        /**
         * Cleans transcription output by removing filler words (if enabled), collapsing stutters,
         * and removing redundant spaces.
         */
        @JvmStatic
        @JvmOverloads
        fun filterTranscriptionOutput(
            text: String?,
            lang: String?,
            enableFillerRemoval: Boolean = true
        ): String {
            if (text.isNullOrEmpty()) return text.orEmpty()

            var result = if (enableFillerRemoval) {
                removeFillerWords(text, lang)
            } else {
                text
            }
            result = REPEATED_WORD_PATTERN.matcher(result).replaceAll("$1")
            result = MULTI_SPACE_PATTERN.matcher(result).replaceAll(" ").trim()
            if (lang?.lowercase()?.startsWith("zh") == true) {
                result = normalizeChinesePunctuation(result)
            }
            return result
        }

        /**
         * Normalizes half-width ASCII punctuation into Chinese full-width punctuation marks.
         */
        @JvmStatic
        fun normalizeChinesePunctuation(text: String): String {
            if (text.isEmpty()) return text
            return text
                .replace(",", "，")
                .replace(".", "。")
                .replace("?", "？")
                .replace("!", "！")
                .replace(":", "：")
                .replace(";", "；")
        }

        private fun removeFillerWords(text: String, lang: String?): String {
            val pattern = if (lang?.lowercase()?.startsWith("es") == true) {
                ES_FILLER_PATTERN
            } else {
                EN_FILLER_PATTERN
            }
            return pattern.matcher(text).replaceAll("")
        }
    }
}
