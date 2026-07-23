package com.handy.app.capability

import com.handy.app.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [computeCompatibility].
 *
 * Coverage:
 * - ACTIVE status when model.isActive = true
 * - TIER_RECOMMENDED / TIER_RECOMMENDED_DEEP when model fits device tier
 * - FIT / EXCEEDS status for off-by-one / heavy models
 * - Experimental gating (hidden when showExperimental = false)
 * - Badge generation (EXPERIMENTAL, HEAVY_GATE, EXCEEDS_RAM)
 * - Consent gate logic
 */
class CompatibilityResolverTest {

    private fun modelInfo(
        id: String = "handy-computer/canary-180m-flash-gguf",
        sizeBytes: Long = 139_000_000L,
        isActive: Boolean = false,
        recommended: Boolean = false,
    ) = ModelInfo(
        id = id,
        displayName = "Test Model",
        sizeBytes = sizeBytes,
        language = "en",
        quant = "Q4_K_M",
        license = null,
        description = null,
        isDownloaded = false,
        isActive = isActive,
        recommended = recommended,
    )

    private fun snapshot(totalMemBytes: Long = 4L * 1024 * 1024 * 1024) = CapabilitySnapshot(
        totalMemBytes = totalMemBytes,
        availMemBytes = totalMemBytes / 2,
        maxMemoryProcessBytes = totalMemBytes / 2,
        isLowRamDevice = false,
        memoryClassMb = 256,
        largeMemoryClassMb = 512,
        cpuCores = 8,
        sdkInt = 34,
    )

    @Test
    fun `active model returns ACTIVE status and no consent`() {
        val model = modelInfo(isActive = true, sizeBytes = 10_000_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = false)
        assertEquals(CompatibilityStatus.ACTIVE, compat.status)
        assertTrue(compat.badges.isEmpty())
        assertFalse(compat.requiresConsent)
        assertFalse(compat.hidden)
    }

    @Test
    fun `recommended model fitting device tier returns TIER_RECOMMENDED_DEEP`() {
        val model = modelInfo(recommended = true, sizeBytes = 139_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = false)
        assertEquals(CompatibilityStatus.TIER_RECOMMENDED_DEEP, compat.status)
    }

    @Test
    fun `non-recommended model fitting device tier returns TIER_RECOMMENDED`() {
        val model = modelInfo(sizeBytes = 139_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = false)
        assertEquals(CompatibilityStatus.TIER_RECOMMENDED, compat.status)
    }

    @Test
    fun `model one tier above device max returns FIT`() {
        // MID device max = LIGHT (500 MB). MEDIUM model is one tier above.
        val model = modelInfo(sizeBytes = 1_000_000_000L)
        val compat = computeCompatibility(model, snapshot(totalMemBytes = 3L * 1024 * 1024 * 1024), showExperimental = false)
        assertEquals(CompatibilityStatus.FIT, compat.status)
    }

    @Test
    fun `model two tiers above device max returns EXCEEDS`() {
        // MID device max = LIGHT (500 MB). HEAVY model is two tiers above.
        val model = modelInfo(sizeBytes = 2_000_000_000L)
        val compat = computeCompatibility(model, snapshot(totalMemBytes = 3L * 1024 * 1024 * 1024), showExperimental = false)
        assertEquals(CompatibilityStatus.EXCEEDS, compat.status)
        assertTrue(CompatibilityBadge.EXCEEDS_RAM in compat.badges)
    }

    @Test
    fun `experimental model is hidden when showExperimental is false`() {
        val model = modelInfo(id = "handy-computer/moonshine-base-gguf", sizeBytes = 100_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = false)
        assertTrue(compat.hidden)
        assertEquals(CompatibilityStatus.IMPOSSIBLE, compat.status)
    }

    @Test
    fun `experimental model is shown when showExperimental is true`() {
        val model = modelInfo(id = "handy-computer/moonshine-base-gguf", sizeBytes = 100_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = true)
        assertFalse(compat.hidden)
        assertTrue(CompatibilityBadge.EXPERIMENTAL in compat.badges)
    }

    @Test
    fun `heavy gate model adds badge and requires consent`() {
        val model = modelInfo(id = "handy-computer/Voxtral-Mini-3B-2507-gguf", sizeBytes = 3_000_000_000L)
        val compat = computeCompatibility(model, snapshot(), showExperimental = false)
        assertTrue(CompatibilityBadge.HEAVY_GATE in compat.badges)
        assertTrue(compat.requiresConsent)
    }

    @Test
    fun `extreme model on tablet requires consent and large heap badge`() {
        // TABLET tier max = EXTREME; extreme model on tablet requires consent
        val model = modelInfo(id = "handy-computer/Voxtral-Small-24B-2507-gguf", sizeBytes = 17_000_000_000L)
        val compat = computeCompatibility(
            model,
            snapshot(totalMemBytes = 16L * 1024 * 1024 * 1024),
            showExperimental = false,
        )
        assertTrue(CompatibilityBadge.LARGE_HEAP_REQUIRED in compat.badges)
        assertTrue(compat.requiresConsent)
    }
}
