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
 * Sprint 28e: LOW.primary AND MID.primary flipped from canary-180m-flash-gguf
 *             to parakeet-tdt-0.6b-v3-gguf (NVIDIA Parakeet TDT 0.6B v3 —
 *             English-only, ~600 MB, "el bueno, bonito y barato" per user
 *             preference; canary "se queda corto" en calidad).
 *             Canary-180m-flash-gguf kept as alternative in BOTH LOW.alternatives
 *             AND MID.alternatives for multilingual users (es/de/en/otros).
 *             Total promoted slots: 19 (5 LOW + 4 MID + 4 HIGH + 3 FLAGSHIP + 3 TABLET).
 * Sprint 28d+ (history, reverted in 28e): MID.primary was canary-180m-flash-gguf.
 * Sprint 28d  (history, reverted in 28e): LOW.primary was canary-180m-flash-gguf.
 * HIGH/FLAGSHIP/TABLET unchanged.
 */
class MobileRecommendationsTest {

    private val fullFixture = """
        {
          "version": 1,
          "tiers": {
            "LOW": {
              "primary": "handy-computer/parakeet-tdt-0.6b-v3-gguf",
              "alternatives": [
                "handy-computer/whisper-tiny-gguf",
                "handy-computer/moonshine-streaming-tiny-gguf",
                "handy-computer/medasr-gguf",
                "handy-computer/canary-180m-flash-gguf"
              ]
            },
            "MID": {
              "primary": "handy-computer/parakeet-tdt-0.6b-v3-gguf",
              "alternatives": [
                "handy-computer/canary-180m-flash-gguf",
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
              "primary": "handy-computer/parakeet-tdt-0.6b-v3-gguf",
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

        // Sprint 28e: LOW primary is parakeet-tdt-0.6b-v3-gguf (NVIDIA, English).
        val low = file.forTier(DeviceTier.LOW)!!
        assertEquals("handy-computer/parakeet-tdt-0.6b-v3-gguf", low.primary)
        assertEquals(4, low.alternatives.size)
        assertTrue(low.alternatives.contains("handy-computer/medasr-gguf"))
        assertTrue(
            "canary-180m-flash-gguf must be in LOW.alternatives post-Sprint 28e",
            low.alternatives.contains("handy-computer/canary-180m-flash-gguf"),
        )

        // Sprint 28e: MID primary is also parakeet-tdt-0.6b-v3-gguf.
        val mid = file.forTier(DeviceTier.MID)!!
        assertEquals("handy-computer/parakeet-tdt-0.6b-v3-gguf", mid.primary)
        assertEquals(3, mid.alternatives.size)
        assertTrue(
            "canary-180m-flash-gguf must be in MID.alternatives post-Sprint 28e",
            mid.alternatives.contains("handy-computer/canary-180m-flash-gguf"),
        )
        assertFalse(
            "parakeet must NOT appear in MID.alternatives (it's the primary post-Sprint 28e)",
            mid.alternatives.contains("handy-computer/parakeet-tdt-0.6b-v3-gguf"),
        )

        val flagship = file.forTier(DeviceTier.FLAGSHIP)!!
        assertEquals("handy-computer/whisper-large-v3-gguf", flagship.primary)
        assertEquals(2, flagship.alternatives.size)
    }

    // ── parseJson partial / graceful fallbacks ─────────────────────────

    @Test
    fun `parseJson silently skips tiers missing from the root tiers map`() {
        val file = MobileRecommendations.parseJson(partialFixture)

        // LOW was in the fixture → present (parakeet-tdt-0.6b-v3-gguf post-Sprint 28e).
        assertEquals("handy-computer/parakeet-tdt-0.6b-v3-gguf", file.forTier(DeviceTier.LOW)?.primary)

        // MID/HIGH/FLAGSHIP/TABLET were not in the fixture → null.
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
                "MID": { "primary": "handy-computer/parakeet-tdt-0.6b-v3-gguf" }
              }
            }
        """.trimIndent()
        val file = MobileRecommendations.parseJson(raw)

        assertEquals(2, file.version)
        val mid = file.forTier(DeviceTier.MID)!!
        assertEquals("handy-computer/parakeet-tdt-0.6b-v3-gguf", mid.primary)
        assertTrue(mid.alternatives.isEmpty())
    }

    @Test
    fun `parseJson skips tier entries whose primary is blank`() {
        val raw = """
            {
              "tiers": {
                "LOW": { "primary": "", "alternatives": ["a"] },
                "MID": { "primary": "handy-computer/parakeet-tdt-0.6b-v3-gguf" }
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
            DeviceTier.LOW to "handy-computer/parakeet-tdt-0.6b-v3-gguf", // Sprint 28e
            DeviceTier.MID to "handy-computer/parakeet-tdt-0.6b-v3-gguf", // Sprint 28e
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
        // Sprint 28e: canary-180m-flash-gguf is now BOTH LOW AND MID alternative
        // (multilingual fallback in both tiers).
        val expectations = mapOf(
            DeviceTier.LOW to "handy-computer/canary-180m-flash-gguf", // Sprint 28e
            DeviceTier.MID to "handy-computer/canary-180m-flash-gguf", // Sprint 28e
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

        // Sprint 28e: nemotron-3.5-asr-streaming-0.6b-gguf is no longer promoted
        // in any tier (was MID primary pre-Sprint 28d+, then demoted in 28d+, still
        // absent in 28e). Likewise whisper-base-gguf (was LOW primary pre-Sprint 28d,
        // demoted, still absent). canary-180m-flash-gguf is bucket=2 in HIGH because
        // it's only LOW + MID alt, never HIGH.
        val crossTierMatrix = listOf(
            TierMatrix(DeviceTier.HIGH, "handy-computer/canary-180m-flash-gguf"), // LOW + MID alt, NOT HIGH
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

        // Sprint 28e: parakeet-tdt-0.6b-v3-gguf is the LOW + MID primary.
        // Under HIGH/FLAGSHIP/TABLET it must score 2 (not promoted there).
        assertEquals(0, file.promotionBucket(DeviceTier.LOW, "handy-computer/parakeet-tdt-0.6b-v3-gguf"))
        assertEquals(0, file.promotionBucket(DeviceTier.MID, "handy-computer/parakeet-tdt-0.6b-v3-gguf"))
        assertEquals(2, file.promotionBucket(DeviceTier.HIGH, "handy-computer/parakeet-tdt-0.6b-v3-gguf"))
        assertEquals(2, file.promotionBucket(DeviceTier.FLAGSHIP, "handy-computer/parakeet-tdt-0.6b-v3-gguf"))
        assertEquals(2, file.promotionBucket(DeviceTier.TABLET, "handy-computer/parakeet-tdt-0.6b-v3-gguf"))
    }

    // ── Sprint 28e regression test: parakeet is the LOW + MID primary ─────────

    @Test
    fun `Sprint 28e parakeet-tdt-0-6b-v3-gguf is the LOW + MID primary (the bueno bonito barato default)`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // Sprint 28e: parakeet-tdt-0.6b-v3-gguf is THE recommended default for
        // both LOW and MID tier devices (per user preference: "el bueno, bonito y
        // barato" — NVIDIA's best English-only STT at 0.6B scale, supersedes
        // canary-180m-flash-gguf which "se queda corto" en calidad).
        assertEquals(
            "Sprint 28e: LOW.primary must be parakeet-tdt-0.6b-v3-gguf",
            "handy-computer/parakeet-tdt-0.6b-v3-gguf",
            file.forTier(DeviceTier.LOW)!!.primary,
        )
        assertEquals(
            "Sprint 28e: MID.primary must be parakeet-tdt-0.6b-v3-gguf",
            "handy-computer/parakeet-tdt-0.6b-v3-gguf",
            file.forTier(DeviceTier.MID)!!.primary,
        )

        // Canary (the previous LOW + MID primary, Sprint 28d/28d+) is moved to
        // BOTH alts so multilingual users still see it as a tier-alternative.
        val low = file.forTier(DeviceTier.LOW)!!
        assertTrue(
            "canary-180m-flash-gguf must remain in LOW.alternatives (Sprint 28e invariant)",
            low.alternatives.contains("handy-computer/canary-180m-flash-gguf"),
        )
        val mid = file.forTier(DeviceTier.MID)!!
        assertTrue(
            "canary-180m-flash-gguf must remain in MID.alternatives (Sprint 28e invariant)",
            mid.alternatives.contains("handy-computer/canary-180m-flash-gguf"),
        )

        // Parakeet must NOT appear in MID.alternatives (it's the MID primary —
        // symmetric with the Sprint 28d+ invariant that canary was removed from
        // MID.alts when promoted to MID.primary).
        assertFalse(
            "parakeet-tdt-0.6b-v3-gguf must NOT appear in MID.alternatives (it's the primary)",
            mid.alternatives.contains("handy-computer/parakeet-tdt-0.6b-v3-gguf"),
        )

        // HIGH/FLAGSHIP/TABLET unchanged by Sprint 28e.
        assertEquals(
            "HIGH.primary must remain whisper-large-v3-turbo-gguf (unchanged by Sprint 28e)",
            "handy-computer/whisper-large-v3-turbo-gguf",
            file.forTier(DeviceTier.HIGH)!!.primary,
        )
        assertEquals(
            "FLAGSHIP.primary must remain whisper-large-v3-gguf (unchanged by Sprint 28e)",
            "handy-computer/whisper-large-v3-gguf",
            file.forTier(DeviceTier.FLAGSHIP)!!.primary,
        )
        assertEquals(
            "TABLET.primary must remain cohere-transcribe-03-2026-gguf (unchanged by Sprint 28e)",
            "handy-computer/cohere-transcribe-03-2026-gguf",
            file.forTier(DeviceTier.TABLET)!!.primary,
        )

        // Total promoted slot count: 5 LOW (1 primary + 4 alts) + 4 MID (1 + 3) +
        // 4 HIGH + 3 FLAGSHIP + 3 TABLET = 19. Up from 18 in Sprint 28d+ because
        // we added canary back to LOW.alts (and into MID.alts, which was already
        // missing canary 28d+ even though it was the primary 28d+).
        var totalSlots = 0
        for (tier in DeviceTier.entries) {
            val recs = file.forTier(tier)!!
            totalSlots += 1 + recs.alternatives.size
        }
        assertEquals(
            "Total promoted slots must be 19 post-Sprint 28e (5+4+4+3+3)",
            19,
            totalSlots,
        )
    }

    // ── Sprint 28d historical regression: LOW.primary was canary (now reverted) ─────────

    @Test
    fun `Sprint 28d historical LOW primary canary was reverted by Sprint 28e`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // Sprint 28d established canary-180m-flash-gguf as the LOW primary;
        // Sprint 28e reverted that in favor of parakeet. Canary remains
        // discoverable as a LOW.alternative (multilingual fallback).
        assertEquals(
            "Sprint 28e revert: LOW.primary is no longer canary-180m-flash-gguf",
            "handy-computer/parakeet-tdt-0.6b-v3-gguf",
            file.forTier(DeviceTier.LOW)!!.primary,
        )
        // The pre-Sprint-28d LOW primary (whisper-base-gguf) is no longer primary
        // in any tier — discoverable only through the full catalog.
        for (tier in DeviceTier.entries) {
            val recs = file.forTier(tier)!!
            assertFalse(
                "whisper-base-gguf must NOT be ${tier.name}.primary (demoted since pre-Sprint 28d)",
                recs.primary == "handy-computer/whisper-base-gguf",
            )
        }
    }

    // ── Sprint 28d+ historical regression: MID.primary was canary (now reverted) ─────────

    @Test
    fun `Sprint 28d+ historical MID primary canary was reverted by Sprint 28e`() {
        val file = MobileRecommendations.parseJson(fullFixture)

        // Sprint 28d+ flipped MID.primary to canary-180m-flash-gguf (multilingual);
        // Sprint 28e reverted that in favor of parakeet-tdt-0.6b-v3-gguf. Canary
        // remains discoverable as a MID.alternative (multilingual fallback).
        assertEquals(
            "Sprint 28e revert: MID.primary is no longer canary-180m-flash-gguf",
            "handy-computer/parakeet-tdt-0.6b-v3-gguf",
            file.forTier(DeviceTier.MID)!!.primary,
        )

        // nemotron-3.5-asr-streaming-0.6b-gguf is no longer primary in any tier
        // (was the original MID primary, demoted post-Sprint 28d+, still absent
        // post-Sprint 28e).
        for (tier in DeviceTier.entries) {
            val recs = file.forTier(tier)!!
            assertFalse(
                "nemotron-3.5-asr-streaming-0.6b-gguf must NOT be ${tier.name}.primary",
                recs.primary == "handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf",
            )
        }
    }

    private data class TierMatrix(val tier: DeviceTier, val id: String)
}
