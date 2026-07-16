package com.handy.app.capability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ModelCapability.isHeavyGate] and [ModelCapability.isExperimental].
 *
 * These methods depend on the private `slugOf` helper which strips the
 * `handy-computer/` prefix and `-gguf` suffix before matching curated slug
 * sets. We test through the public API because the slug-normalization logic
 * is an implementation detail.
 *
 * Coverage:
 *  - 3 Voxtral variants → heavyGate = true (catalog IDs match curated slugs)
 *  - Whisper / Parakeet / Canary / Granite / FunASR variants → false
 *  - 7 Moonshine Base monolingual variants → experimental = true
 *  - Moonshine Tiny (not Base) variants → false (not in experimental set)
 *  - Slug-idempotence: bare slugs (without prefix/suffix) still match.
 */
class ModelCapabilityTest {

    // ── helpers ────────────────────────────────────────────────────────

    /** Build a `ModelInfo.id` in the canonical catalog format used at runtime. */
    private fun catalogId(slug: String): String = "handy-computer/$slug-gguf"

    // ── isHeavyGate: Voxtral ────────────────────────────────────────────

    @Test
    fun `isHeavyGate returns true for Voxtral Small 24B`() {
        assertTrue(
            "Voxtral-Small-24B-2507 should be heavyGate",
            ModelCapability.isHeavyGate(catalogId("Voxtral-Small-24B-2507")),
        )
    }

    @Test
    fun `isHeavyGate returns true for Voxtral Mini 4B Realtime`() {
        assertTrue(
            "Voxtral-Mini-4B-Realtime-2602 should be heavyGate",
            ModelCapability.isHeavyGate(catalogId("Voxtral-Mini-4B-Realtime-2602")),
        )
    }

    @Test
    fun `isHeavyGate returns true for Voxtral Mini 3B`() {
        assertTrue(
            "Voxtral-Mini-3B-2507 should be heavyGate",
            ModelCapability.isHeavyGate(catalogId("Voxtral-Mini-3B-2507")),
        )
    }

    @Test
    fun `isHeavyGate returns false for unrelated tiny and base models`() {
        // Negative parity: Whisper / Parakeet / Canary / Granite / FunASR / Cohere must NOT trigger.
        val nonHeavyGate = listOf(
            "whisper-base",
            "whisper-tiny",
            "whisper-large-v3-turbo",
            "parakeet-tdt-0.6b-v3",
            "canary-180m-flash",
            "granite-speech-4.1-2b-plus",
            "Fun-ASR-MLT-Nano-2512",
            "cohere-transcribe-03-2026",
            "Qwen3-ASR-1.7B",
            "Breeze-ASR-25",
            "medasr",
        )
        for (slug in nonHeavyGate) {
            assertFalse(
                "Expected $slug to NOT be heavyGate",
                ModelCapability.isHeavyGate(catalogId(slug)),
            )
        }
    }

    @Test
    fun `isHeavyGate is robust against unrelated non-prefixed ids`() {
        // Defensive: a random non-Voxtral, non-prefixed id must not match
        // the curated slug set even though slugOf is a no-op without the
        // catalog prefix and `-gguf` suffix.
        assertFalse(
            "Unrelated non-prefixed id must not match heavyGate",
            ModelCapability.isHeavyGate("some-random-non-voxtral-model"),
        )
    }

    @Test
    fun `isHeavyGate matches Voxtral slug even when the id is already bare`() {
        // Positive parity test: proves slugOf is idempotent for already-
        // slugified strings — when the input has no catalog prefix and no
        // `-gguf` suffix, slugOf becomes a no-op, and the resulting slug
        // is still the right shape for matching heavyGateSlugs.
        assertTrue(
            "Bare Voxtral-Small-24B-2507 (no prefix/suffix) should match heavyGate",
            ModelCapability.isHeavyGate("Voxtral-Small-24B-2507"),
        )
        assertTrue(
            "Bare Voxtral-Mini-3B-2507 should match heavyGate",
            ModelCapability.isHeavyGate("Voxtral-Mini-3B-2507"),
        )
    }

    // ── isExperimental: Moonshine Base ───────────────────────────────────

    @Test
    fun `isExperimental returns true for moonshine base English`() {
        assertTrue(
            "moonshine-base (English) should be experimental",
            ModelCapability.isExperimental(catalogId("moonshine-base")),
        )
    }

    @Test
    fun `isExperimental returns true for all 6 moonshine-base monolingual variants`() {
        // Every non-English Moonshine Base variant should be hidden by default.
        val monolingual = listOf("ar", "ko", "uk", "ja", "vi", "zh")
        for (lang in monolingual) {
            assertTrue(
                "moonshine-base-$lang should be experimental",
                ModelCapability.isExperimental(catalogId("moonshine-base-$lang")),
            )
        }
    }

    @Test
    fun `isExperimental returns false for moonshine tiny variants`() {
        // Moonshine Tiny (NOT -base) is supported and should NOT be experimental.
        val tinyVariants = listOf("", "-vi", "-uk", "-ko", "-zh", "-ar", "-ja", "-streaming-tiny")
        for (suffix in tinyVariants) {
            assertFalse(
                "moonshine-tiny$suffix should NOT be experimental",
                ModelCapability.isExperimental(catalogId("moonshine-tiny$suffix")),
            )
        }
    }

    @Test
    fun `isExperimental returns false for unrelated models`() {
        val unrelated = listOf(
            "whisper-base",
            "parakeet-tdt-0.6b-v3",
            "canary-180m-flash",
            "nemotron-3.5-asr-streaming-0.6b",
            "Qwen3-ASR-0.6B",
            "Voxtral-Mini-4B-Realtime-2602", // heavy-gate, NOT experimental
        )
        for (slug in unrelated) {
            assertFalse(
                "Expected $slug to NOT be experimental",
                ModelCapability.isExperimental(catalogId(slug)),
            )
        }
    }

    @Test
    fun `isExperimental matches moonshine-base slug even when the id is already bare`() {
        // Same idempotence property for experimentalSlugs.
        assertTrue(
            "Bare moonshine-base-zh should match experimental",
            ModelCapability.isExperimental("moonshine-base-zh"),
        )
    }
}
