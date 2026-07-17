package com.handy.app.capability

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MobileRecommendations].
 *
 * Strategy: tests run on the JVM (no Robolectric) by exercising the pure
 * [MobileRecommendations.parseJson] seam extracted for this purpose. The
 * parsing logic and the [MobileRecommendationsFile.promotionBucket] sort
 * priority are tested against inline JSON fixtures.
 *
 * Coverage:
 *  - parseJson happy path: all 5 tiers + alternatives persisted correctly.
 *  - parseJson partial: tiers missing from root are silently skipped.
 *  - parseJson malformed: throws [JSONException] via wrap.
 *  - promotionBucket for all 5 DeviceTier maps primary→0, alternative→1, others→2.
 *
 * Sprint 28d: LOW.primary swap whisper-base-gguf → canary-180m-flash-gguf
 * (see fixtures + LOW expectations updated accordingly).
 */
class MobileRecommendationsTest {

    private val fullFixture = """
        {
          "version": 1,
          "tiers": {
            "LOW": {
              "primary": "handy-computer/canary-180m-flash-gguf",
              "alternatives": [
                "handy-computer/whisper-tiny-gguf",
                "handy-computer/moonshine-streaming-tiny-gguf",
                "handy-computer/medasr-gguf"
              ]
            },
            "MID": {
              "primary": "handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf",
              "alternatives": [
                "handy-computer/canary-180m-flash-gguf",
                "handy-computer/parakeet-tdt-0.6b-v3-gguf",
                "handy-computer/whisper-medium-gguf",
                "handy-computer/whisper-small-gguf"
              ]
            },
            "HIGH": {
              "primary": "handy-computer/whisper-large-v3-turbo-gguf",
              "alternatives": [
                "handy-computer/Qwen3-ASR-1.7B-gguf",
                "handy-computer/canary-1b-v2-gguf",
                "handy-computer/whisper-large-v3-gguf"
              ]
            },
            "FLAGSHIP": {
              "primary": "handy-computer/whisper-large-v3-gguf",
              "alternatives": [
                "handy-computer/granite-speech-4.1-2b-plus-gguf",
                "handy-computer/canary-qwen-2.5b-gguf"
              ]
            },
            "TABLET": {
              "primary": "handy-computer/cohere-transcribe-03-2026-gguf",
              "alternatives": [
                "handy-computer/granite-speech-4.1-2b-gguf",
                "handy-computer/granite-4.0-1b-speech-gguf"
              ]
            }
          }
        }
    """.trimIndent()

    private val partialFixture = """
        {
          "version": 1,
          "tiers": {
            "LOW": {
              "primary": "handy-computer/canary-180m-flash-gguf",
              "alternatives": []
            }
          }
        }
    """.trimIndent()

    // ── parseJson happy path ─────────────────────────────────────────

    @Test
    fun `parseJson successfully loads all 5 tiers and alternatives from valid JSON`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        assertEquals(1, file.version)

        // Every tier must be present.
        for (tier in DeviceTier.entries) {
            assertNotNull(
                "Expected tier $tier to be present in full fixture",
                file.forTier(tier),
            )
        }

        // Sprint 28d: LOW primary is now canary-180m-flash-gguf
        // (multilingual default out-of-box).
        val low = file.forTier(DeviceTier.LOW)!!
        assertEquals("handy-computer/canary-180m-flash-gguf", low.primary)
        assertEquals(3, low.alternatives.size)
        assertTrue(low.alternatives.contains("handy-computer/medasr-gguf"))

        val flagship = file.forTier(DeviceTier.FLAGSHIP)!!
        assertEquals("handy-computer/whisper-large-v3-gguf", flagship.primary)
        assertEquals(2, flagship.alternatives.size)
    }

    // ── parseJson partial / graceful fallbacks ─────────────────────────

    @Test
    fun `parseJson silently skips tiers missing from the root tiers map`() {
        val file = MobileRecommendations.parseJson(partialFixture)

        // LOW was in the fixture → present (canary-180m-flash-gguf post-Sprint 28d swap)
        assertEquals("handy-computer/canary-180m-flash-gguf", file.forTier(DeviceTier.LOW)?.primary)

        // MID/HIGH/FLAGSHIP/TABLET were not in the fixture → null
        assertNull(file.forTier(DeviceTier.MID))
        assertNull(file.forTier(DeviceTier.HIGH))
        assertNull(file.forTier(DeviceTier.FLAGSHIP))
        assertNull(file.forTier(DeviceTier.TABLET))

        // And the silent skip must not leak empty buckets.
        assertEquals(1, file.tiers.size)
    }

    @Test
    fun `parseJson tolerates missing alternatives key for a tier`() {
        val raw = """
            {
              "version": 2,
              "tiers": {
                "MID": { "primary": "handy-computer/canary-180m-flash-gguf" }
              }
            }
        """.trimIndent()
        val file = MobileRecommendations.parseJson(raw)

        assertEquals(2, file.version)
        val mid = file.forTier(DeviceTier.MID)!!
        assertEquals("handy-computer/canary-180m-flash-gguf", mid.primary)
        assertTrue(mid.alternatives.isEmpty())
    }

    @Test
    fun `parseJson skips tier entries whose primary is blank`() {
        val raw = """
            {
              "tiers": {
                "LOW": { "primary": "", "alternatives": ["a"] },
                "MID": { "primary": "handy-computer/canary-180m-flash-gguf" }
              }
            }
        """.trimIndent()
        val file = MobileRecommendations.parseJson(raw)

        assertNull(file.forTier(DeviceTier.LOW))
        assertNotNull(file.forTier(DeviceTier.MID))
    }

    @Test(expected = JSONException::class)
    fun `parseJson throws on malformed JSON`() {
        MobileRecommendations.parseJson("not-valid-json-at-all")
    }

    @Test
    fun `parseJson returns empty file when tiers key is missing`() {
        // Defensive coverage for asset corruption: valid JSON shape but
        // missing the `tiers` key entirely. parseJson should not crash and
        // should fall back to an empty recommendation table.
        val file = MobileRecommendations.parseJson("""{"version": 3}""")
        assertEquals(3, file.version)
        assertTrue(file.tiers.isEmpty())
        for (tier in DeviceTier.entries) {
            assertNull("forTier($tier) must be null when tiers map empty", file.forTier(tier))
        }
    }

    // ── promotionBucket × 5 tiers × 3 buckets ─────────────────────────

    @Test
    fun `promotionBucket returns 0 (tier-primary) for primary recommendations across all 5 tiers`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        val expectations = mapOf(
            DeviceTier.LOW to "handy-computer/canary-180m-flash-gguf", // Sprint 28d swap
            DeviceTier.MID to "handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf",
            DeviceTier.HIGH to "handy-computer/whisper-large-v3-turbo-gguf",
            DeviceTier.FLAGSHIP to "handy-computer/whisper-large-v3-gguf",
            DeviceTier.TABLET to "handy-computer/cohere-transcribe-03-2026-gguf",
        )

        for ((tier, expectedPrimary) in expectations) {
            assertEquals(
                "promotionBucket for $tier.primary must be 0",
                0,
                file.promotionBucket(tier, expectedPrimary),
            )
            assertTrue(
                "isPrimaryFor for $tier.primary must hold",
                file.isPrimaryFor(tier, expectedPrimary),
            )
            assertFalse(
                "isAlternativeFor for $tier.primary must NOT hold",
                file.isAlternativeFor(tier, expectedPrimary),
            )
        }
    }

    @Test
    fun `promotionBucket returns 1 (tier-alternative) for alternative recommendations across all 5 tiers`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // One alternative per tier — covers the bucket=1 path.
        val expectations = mapOf(
            DeviceTier.LOW to "handy-computer/medasr-gguf",
            DeviceTier.MID to "handy-computer/canary-180m-flash-gguf", // dual role: also LOW primary
            DeviceTier.HIGH to "handy-computer/Qwen3-ASR-1.7B-gguf",
            DeviceTier.FLAGSHIP to "handy-computer/granite-speech-4.1-2b-plus-gguf",
            DeviceTier.TABLET to "handy-computer/granite-speech-4.1-2b-gguf",
        )

        for ((tier, alt) in expectations) {
            assertEquals(
                "promotionBucket for $tier alternative must be 1",
                1,
                file.promotionBucket(tier, alt),
            )
            assertTrue(
                "isAlternativeFor for $tier alternative must hold",
                file.isAlternativeFor(tier, alt),
            )
            assertFalse(
                "isPrimaryFor for $tier alternative must NOT hold",
                file.isPrimaryFor(tier, alt),
            )
        }
    }

    @Test
    fun `promotionBucket returns 2 (not promoted) for ids absent from the curated list`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // For each tier, an id that lives in a different tier must score 2.
        // Sprint 28d: whisper-base-gguf is no longer in any tier → scores 2
        // for every tier; using FLAGSHIP row here to assert the cross-tier
        // matrix still works after the swap.
        val crossTierMatrix = listOf(
            TierMatrix(DeviceTier.LOW, "handy-computer/parakeet-tdt-0.6b-v3-gguf"), // MID primary
            TierMatrix(DeviceTier.MID, "handy-computer/granite-speech-4.1-2b-gguf"), // TABLET alternative
            TierMatrix(DeviceTier.HIGH, "handy-computer/cohere-transcribe-03-2026-gguf"), // TABLET primary
            TierMatrix(DeviceTier.FLAGSHIP, "handy-computer/whisper-base-gguf"), // demoted post-Sprint 28d
            TierMatrix(DeviceTier.TABLET, "handy-computer/whisper-large-v3-gguf"), // FLAGSHIP primary
        )

        for (matrix in crossTierMatrix) {
            assertEquals(
                "promotionBucket for ${matrix.tier}/${matrix.id} must be 2",
                2,
                file.promotionBucket(matrix.tier, matrix.id),
            )
            assertFalse(
                "isPrimaryFor for ${matrix.tier}/${matrix.id} must NOT hold",
                file.isPrimaryFor(matrix.tier, matrix.id),
            )
            assertFalse(
                "isAlternativeFor for ${matrix.tier}/${matrix.id} must NOT hold",
                file.isAlternativeFor(matrix.tier, matrix.id),
            )
        }
    }

    @Test
    fun `promotionBucket distinguishes cross-tier lookup correctly (cross-tier evaluation yields different buckets)`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // A model's promotion bucket is RELEVANT TO THE DEVICE TIER, not the model itself.
        // `whisper-large-v3-gguf` is the FLAGSHIP primary, so under other tiers it must score 2.
        assertEquals(0, file.promotionBucket(DeviceTier.FLAGSHIP, "handy-computer/whisper-large-v3-gguf"))
        assertEquals(1, file.promotionBucket(
            DeviceTier.HIGH,
            "handy-computer/whisper-large-v3-gguf", // HIGH alternative, not primary
        ))
        assertEquals(2, file.promotionBucket(
            DeviceTier.LOW,
            "handy-computer/whisper-large-v3-gguf", // not LOW-promoted at all
        ))
    }

    // ── Sprint 28d: regression test for the LOW.primary swap ─────────

    @Test
    fun `Sprint 28d canary-180m-flash-gguf is the LOW primary (default out-of-box model)`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // The user's intentional default for first-install Android phones
        // (which typically resolve to DeviceTier.LOW per the
        // DeviceCapabilityDetector RAM + core-count heuristic).
        val low = file.forTier(DeviceTier.LOW)!!
        assertEquals(
            "Sprint 28d: LOW.primary must be canary-180m-flash-gguf",
            "handy-computer/canary-180m-flash-gguf",
            low.primary,
        )
        // Canary stays as a MID alternative for mid-range devices that
        // prefer multilingüe over the nemotron-0.6b primary.
        val mid = file.forTier(DeviceTier.MID)!!
        assertTrue(
            "canary-180m-flash-gguf must remain a MID alternative",
            mid.alternatives.contains("handy-computer/canary-180m-flash-gguf"),
        )
        // The previous LOW primary (whisper-base-gguf) is no longer in any
        // promoted bucket — discoverable only through the full catalog.
        for (tier in DeviceTier.entries) {
            val recs = file.forTier(tier)!!
            assertFalse(
                "whisper-base-gguf must NOT be ${tier.name}.primary post-Sprint 28d",
                recs.primary == "handy-computer/whisper-base-gguf",
            )
        }
    }

    private data class TierMatrix(val tier: DeviceTier, val id: String)
}
