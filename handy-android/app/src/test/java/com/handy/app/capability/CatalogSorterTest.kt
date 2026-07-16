package com.handy.app.capability

import com.handy.app.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [computeVisibleCatalog].
 *
 * These tests verify the catalog sort/filter contract on the JVM without
 * Android dependencies. Inputs are built with plain data classes.
 *
 * Coverage:
 *  - ACTIVE model floats to the top.
 *  - Tier-primary / tier-alternative / non-promoted ordering.
 *  - Voxtral-like EXTREME models (FIT/EXCEEDS) appear after TIER_RECOMMENDED.
 *  - Experimental models are hidden unless showExp=true.
 *  - Within the same bucket, smaller models come first.
 */
class CatalogSorterTest {

    // Snapshot for a FLAGSHIP device (7 GB RAM) so deviceMax = HEAVY.
    private val flagshipSnapshot = CapabilitySnapshot(
        totalMemBytes = 7_000_000_000L,
        availMemBytes = 3_000_000_000L,
        maxMemoryProcessBytes = 512L * 1024 * 1024,
        isLowRamDevice = false,
        memoryClassMb = 256,
        largeMemoryClassMb = 512,
        cpuCores = 8,
        sdkInt = 34,
    )

    // Recommendations for FLAGSHIP tier.
    private val flagshipRecs = MobileRecommendationsFile(
        version = 1,
        tiers = mapOf(
            DeviceTier.FLAGSHIP to TierRecommendations(
                primary = "handy-computer/whisper-large-v3-gguf",
                alternatives = listOf("handy-computer/granite-speech-4.1-2b-plus-gguf"),
            ),
        ),
    )

    // Empty recommendations (used to test global recommended flag).
    private val emptyRecs = MobileRecommendationsFile(version = 1, tiers = emptyMap())

    // ── helpers ─────────────────────────────────────────────────────────

    private fun model(
        id: String,
        sizeBytes: Long,
        isActive: Boolean = false,
        isDownloaded: Boolean = false,
        recommended: Boolean = false,
    ): ModelInfo = ModelInfo(
        id = id,
        displayName = id,
        sizeBytes = sizeBytes,
        language = "en",
        quant = "Q4_K_M",
        license = null,
        description = null,
        isDownloaded = isDownloaded,
        isActive = isActive,
        recommended = recommended,
    )

    private fun ids(result: List<Pair<ModelInfo, ModelCompatibility>>): List<String> =
        result.map { it.first.id }

    // ── status ordering ───────────────────────────────────────────────

    @Test
    fun `ACTIVE model appears first regardless of promotion`() {
        val active = model(id = "handy-computer/voxtral-small-24b-gguf", sizeBytes = 17_000_000_000L, isActive = true)
        val primary = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(active, primary),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
        )

        assertEquals(listOf(active.id, primary.id), ids(result))
    }

    @Test
    fun `EXTREME model is sorted after TIER_RECOMMENDED models`() {
        val voxtral = model(id = "handy-computer/voxtral-small-24b-gguf", sizeBytes = 17_000_000_000L)
        val whisper = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(voxtral, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
        )

        assertEquals(listOf(whisper.id, voxtral.id), ids(result))
        assertEquals(CompatibilityStatus.TIER_RECOMMENDED, result.first().second.status)
        assertEquals(CompatibilityStatus.FIT, result.last().second.status)
    }

    @Test
    fun `Voxtral Small 24B is not first when an ACTIVE lightweight model exists`() {
        // Regression guard: ensure an EXTREME model never floats above an
        // currently active lightweight model due to a broken sort.
        val active = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L, isActive = true)
        val voxtral = model(id = "handy-computer/voxtral-small-24b-gguf", sizeBytes = 17_000_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(voxtral, active),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
        )

        assertEquals(listOf(active.id, voxtral.id), ids(result))
        assertEquals(CompatibilityStatus.ACTIVE, result.first().second.status)
        assertEquals(CompatibilityStatus.FIT, result.last().second.status)
    }

    @Test
    fun `EXCEEDS model is sorted after FIT model on a MID device`() {
        // MID device can only safely run LIGHT models; HEAVY is off-by-one (FIT),
        // and EXTREME exceeds the tier.
        val midSnapshot = CapabilitySnapshot(
            totalMemBytes = 3_000_000_000L,
            availMemBytes = 1_500_000_000L,
            maxMemoryProcessBytes = 256L * 1024 * 1024,
            isLowRamDevice = false,
            memoryClassMb = 192,
            largeMemoryClassMb = 256,
            cpuCores = 8,
            sdkInt = 34,
        )
        val heavy = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)
        val extreme = model(id = "handy-computer/voxtral-small-24b-gguf", sizeBytes = 17_000_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(extreme, heavy),
            snapshot = midSnapshot,
            recs = emptyRecs,
            showExp = false,
        )

        assertEquals(listOf(heavy.id, extreme.id), ids(result))
        assertEquals(CompatibilityStatus.FIT, result.first().second.status)
        assertEquals(CompatibilityStatus.EXCEEDS, result.last().second.status)
    }

    // ── promotion ordering ────────────────────────────────────────────

    @Test
    fun `tier-primary appears before tier-alternative`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)
        val alternative = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf", sizeBytes = 1_500_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(alternative, primary),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
        )

        assertEquals(listOf(primary.id, alternative.id), ids(result))
    }

    @Test
    fun `promoted models appear before non-promoted models`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)
        val nonPromoted = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(nonPromoted, primary),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
        )

        assertEquals(listOf(primary.id, nonPromoted.id), ids(result))
    }

    // ── full sort chain ───────────────────────────────────────────────

    @Test
    fun `full sort chain respects status then promotion then size`() {
        // Build a diverse set that exercises all sort keys.
        val active = model(id = "handy-computer/voxtral-small-24b-gguf", sizeBytes = 17_000_000_000L, isActive = true)
        val primary = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)
        val alternative = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf", sizeBytes = 1_500_000_000L)
        val nonPromoted = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val heavy = model(id = "handy-computer/voxtral-mini-3b-gguf", sizeBytes = 3_000_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(heavy, nonPromoted, alternative, active, primary),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
        )

        assertEquals(
            listOf(active.id, primary.id, alternative.id, nonPromoted.id, heavy.id),
            ids(result),
        )
    }

    // ── size tie-breaker ──────────────────────────────────────────────

    @Test
    fun `within the same bucket smaller models come first`() {
        val small = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val large = model(id = "handy-computer/whisper-base-gguf", sizeBytes = 400_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(large, small),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
        )

        assertEquals(listOf(small.id, large.id), ids(result))
    }

    // ── experimental gating ─────────────────────────────────────────────

    @Test
    fun `experimental models are hidden when showExp is false`() {
        val experimental = model(id = "handy-computer/moonshine-base-zh-gguf", sizeBytes = 100_000_000L)

        val resultHidden = computeVisibleCatalog(
            raw = listOf(experimental),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
        )

        assertTrue(resultHidden.isEmpty())
    }

    @Test
    fun `experimental models are shown when showExp is true`() {
        val experimental = model(id = "handy-computer/moonshine-base-zh-gguf", sizeBytes = 100_000_000L)

        val resultShown = computeVisibleCatalog(
            raw = listOf(experimental),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = true,
        )

        assertEquals(listOf(experimental.id), ids(resultShown))
        assertTrue(resultShown.first().second.badges.contains(CompatibilityBadge.EXPERIMENTAL))
    }
}
