package com.handy.app.viewmodel

import com.handy.app.capability.TierRecommendations
import com.handy.app.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [OnboardingViewModel.computePromotionLabel] companion function.
 *
 * Pure function — verified on the JVM without any Android dependency.
 *
 * Coverage (4 promotion outcomes + 2 null-tierRecs matrix cells = 6 tests):
 *  - `tier-primary`: model exactly matches the curated primary for the tier.
 *  - `tier-alternative`: model is one of the curated alternatives.
 *  - `global-recommended`: tierRecs exists but the model is not promoted;
 *    falls back to the catalog's `recommended` flag.
 *  - `fallback`: tierRecs exists, model is not promoted, no `recommended` flag.
 *  - Null tierRecs + recommended=true degrades to `global-recommended`.
 *  - Null tierRecs + recommended=false degrades to `fallback`.
 */
class OnboardingPromotionLabelTest {

    private fun model(id: String, recommended: Boolean = false): ModelInfo =
        ModelInfo(
            id = id,
            displayName = id,
            sizeBytes = 500_000_000L,
            language = "en",
            quant = "Q4_K_M",
            license = null,
            description = null,
            isDownloaded = false,
            isActive = false,
            recommended = recommended,
        )

    private val flagshipRecs = TierRecommendations(
        primary = "handy-computer/whisper-large-v3-gguf",
        alternatives = listOf("handy-computer/granite-speech-4.1-2b-plus-gguf"),
    )

    @Test
    fun `tier-primary label fires when target matches the curated primary id`() {
        val target = model(id = "handy-computer/whisper-large-v3-gguf")

        val label = OnboardingViewModel.computePromotionLabel(target, flagshipRecs)

        assertEquals("tier-primary", label)
    }

    @Test
    fun `tier-alternative label fires when target id is in alternatives but not primary`() {
        val target = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf")

        val label = OnboardingViewModel.computePromotionLabel(target, flagshipRecs)

        assertEquals("tier-alternative", label)
    }

    @Test
    fun `global-recommended label fires when target is not promoted but carries the recommended flag`() {
        val target = model(
            id = "handy-computer/canary-180m-flash-gguf",
            recommended = true,
        )

        val label = OnboardingViewModel.computePromotionLabel(target, flagshipRecs)

        assertEquals("global-recommended", label)
    }

    @Test
    fun `fallback label fires when target is not promoted and has no recommended flag`() {
        val target = model(
            id = "handy-computer/canary-180m-flash-gguf",
            recommended = false,
        )

        val label = OnboardingViewModel.computePromotionLabel(target, flagshipRecs)

        assertEquals("fallback", label)
    }

    @Test
    fun `null tierRecs and recommended target returns global-recommended`() {
        val target = model(
            id = "handy-computer/canary-180m-flash-gguf",
            recommended = true,
        )

        val label = OnboardingViewModel.computePromotionLabel(target, null)

        assertEquals("global-recommended", label)
    }

    @Test
    fun `null tierRecs and non-recommended target returns fallback`() {
        val target = model(
            id = "handy-computer/canary-180m-flash-gguf",
            recommended = false,
        )

        val label = OnboardingViewModel.computePromotionLabel(target, null)

        assertEquals("fallback", label)
    }
}
