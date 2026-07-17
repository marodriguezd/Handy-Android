package com.handy.app.viewmodel

import com.handy.app.capability.TierRecommendations
import com.handy.app.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [OnboardingViewModel.pickTargetModel] companion function.
 *
 * Pure function resolved at JVM-test granularity: callers pre-resolve
 * tierRecs (via `mobileRecs.forTier(tier)`) and supply the `fitsAndSafe`
 * predicate separately, so neither DeviceCapabilityDetector nor
 * SettingsStore leak into the test surface.
 *
 * Coverage (8 tests covering the resolution priority chain and edge cases):
 *  1. Tier primary NOT downloaded + fitsAndSafe OK → primary wins.
 *  2. Tier primary already downloaded → tier alternative wins.
 *  3. Tier primary fails fitsAndSafe → tier alternative wins.
 *  4. Tier primary + alternatives all fail fitsAndSafe → global-recommended wins.
 *  5. All promoted + global-recommended fail fitsAndSafe → first not-downloaded that fits wins.
 *  6. Empty model list → null (no candidate).
 *  7. All candidates downloaded & no safe alternative → null (defer to manual).
 *  8. Null tierRecs + recommended → recommended wins; null tierRecs + no recommended → first safe.
 *
 * Total: 9 tests.
 */
class OnboardingTargetPickerTest {

    private val flagshipRecs = TierRecommendations(
        primary = "handy-computer/whisper-large-v3-gguf",
        alternatives = listOf(
            "handy-computer/granite-speech-4.1-2b-plus-gguf",
            "handy-computer/canary-qwen-2.5b-gguf",
        ),
    )

    private fun model(
        id: String,
        isDownloaded: Boolean = false,
        recommended: Boolean = false,
        sizeBytes: Long = 500_000_000L,
    ): ModelInfo = ModelInfo(
        id = id,
        displayName = id,
        sizeBytes = sizeBytes,
        language = "en",
        quant = "Q4_K_M",
        license = null,
        description = null,
        isDownloaded = isDownloaded,
        isActive = false,
        recommended = recommended,
    )

    /** Accept every model by default — overridden per-test where the chain needs to skip an entry. */
    private val allSafe: (ModelInfo) -> Boolean = { true }

    @Test
    fun `tier primary is chosen when it is not downloaded and fits`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf")
        val alt = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")
        val globalRec = model(id = "handy-computer/canary-180m-flash-gguf", recommended = true)
        val lastResort = model(id = "handy-computer/voxtral-mini-3b-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(lastResort, globalRec, alt, primary),
            tierRecs = flagshipRecs,
            fitsAndSafe = allSafe,
        )

        assertEquals(primary.id, target?.id)
    }

    @Test
    fun `tier alternative is chosen when tier primary is already downloaded`() {
        val primaryDownloaded = model(
            id = "handy-computer/whisper-large-v3-gguf",
            isDownloaded = true,
        )
        val alt = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")
        val lastResort = model(id = "handy-computer/canary-180m-flash-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(lastResort, alt, primaryDownloaded),
            tierRecs = flagshipRecs,
            fitsAndSafe = allSafe,
        )

        assertEquals(alt.id, target?.id)
    }

    @Test
    fun `tier alternative is chosen when tier primary fails fitsAndSafe`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf")
        val alt = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")
        // Primary is unsafe (e.g. Voxtral heavy-gate proxy); alt remains safe.
        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(primary, alt),
            tierRecs = flagshipRecs,
            fitsAndSafe = { m -> m.id != primary.id },
        )

        assertEquals(alt.id, target?.id)
    }

    @Test
    fun `global-recommended wins when tier primary and alternatives all fail fitsAndSafe`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf")
        val alt = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")
        val globalRec = model(id = "handy-computer/canary-180m-flash-gguf", recommended = true)
        val lastResort = model(id = "handy-computer/voxtral-mini-3b-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(lastResort, globalRec, alt, primary),
            tierRecs = flagshipRecs,
            // Only the global-recommended + last-resort are safe; promoted models are unsafe.
            fitsAndSafe = { m -> m.id == globalRec.id || m.id == lastResort.id },
        )

        assertEquals(globalRec.id, target?.id)
    }

    @Test
    fun `first not-downloaded safe model wins when promoted and global-recommended all fail fitsAndSafe`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf")
        val alt = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")
        val globalRec = model(id = "handy-computer/canary-180m-flash-gguf", recommended = true)
        val lastResort = model(id = "handy-computer/voxtral-mini-3b-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(lastResort, globalRec, alt, primary),
            tierRecs = flagshipRecs,
            // Only lastResort survives the safety guard.
            fitsAndSafe = { m -> m.id == lastResort.id },
        )

        assertEquals(lastResort.id, target?.id)
    }

    @Test
    fun `empty model list returns null`() {
        val target = OnboardingViewModel.pickTargetModel(
            models = emptyList(),
            tierRecs = flagshipRecs,
            fitsAndSafe = allSafe,
        )

        assertNull(target)
    }

    @Test
    fun `all candidates downloaded with no safe alternative returns null`() {
        val primary = model(
            id = "handy-computer/whisper-large-v3-gguf",
            isDownloaded = true,
        )
        val alt = model(
            id = "handy-computer/granite-speech-4.1-2b-plus-gguf",
            isDownloaded = true,
        )
        val globalRec = model(
            id = "handy-computer/canary-180m-flash-gguf",
            recommended = true,
            isDownloaded = true,
        )

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(primary, alt, globalRec),
            tierRecs = flagshipRecs,
            fitsAndSafe = allSafe,
        )

        assertNull(target)
    }

    @Test
    fun `null tierRecs with a recommended candidate falls back to that recommended model`() {
        val rec = model(id = "handy-computer/canary-180m-flash-gguf", recommended = true)
        val other = model(id = "handy-computer/voxtral-mini-3b-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            models = listOf(other, rec),
            tierRecs = null,
            fitsAndSafe = allSafe,
        )

        assertEquals(rec.id, target?.id)
    }

    @Test
    fun `null tierRecs with no recommended candidate falls back to first not-downloaded safe model`() {
        val firstSafe = model(id = "handy-computer/canary-180m-flash-gguf")
        val otherSafe = model(id = "handy-computer/whisper-base-gguf")

        val target = OnboardingViewModel.pickTargetModel(
            // `firstSafe` is intentionally placed FIRST in the list because
            // `pickTargetModel`'s last-resort branch uses `firstOrNull`, so
            // ordering within the input list directly drives the winner.
            models = listOf(firstSafe, otherSafe),
            tierRecs = null,
            fitsAndSafe = allSafe,
        )

        assertEquals(firstSafe.id, target?.id)
    }
}
