package com.handy.app.settings

/**
 * Sprint 25b Phase C — pure-function parser for the `custom_words`
 * user setting.
 *
 * Source format: free-form text the user types into a multi-line
 * `OutlinedTextField`. Tokens may be separated by COMMA OR newline.
 * Whitespace per token is trimmed. Empty tokens are filtered.
 * Duplicate tokens are kept case-sensitively (deliberate — Whisper
 * case-folds its prompt internally, so emitting `iPhone` / `IPHONE`
 * / `iphone` as distinct tokens biases each separately rather than
 * collapsing into one normalized form).
 *
 * Size contracts:
 *  - `maxChars` — defensive cap on the raw input length. When the
 *    raw input exceeds this, the function returns `emptyList()`
 *    because we cannot reliably parse a runaway log without
 *    protecting the disk-side SharedPreferences writer.
 *  - `maxEntries` — bound the resulting list so a user pasting 200
 *    words does not flood the JSON-encoded Whisper hot-word prompt.
 *    Standard Whisper prompts should be <50 tokens.
 *
 * Pure-JVM, no Android dependencies — pinned for testing in
 * `CustomWordsParserTest.kt`.
 */
internal fun String.parseCustomWords(
    maxChars: Int = 500,
    maxEntries: Int = 50,
): List<String> {
    if (length > maxChars) return emptyList()
    return split(',', '\n')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(maxEntries)
        .toList()
}
