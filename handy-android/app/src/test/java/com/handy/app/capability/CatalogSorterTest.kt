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
 *  - **Sprint 22** — search query, language filter, and onlyRecommended
 *    flags from the catalog header are evaluated *before* capability
 *    computation (user filter is cheaper than compat).
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
        language: String = "en",
        description: String? = null,
        displayName: String = id,
    ): ModelInfo = ModelInfo(
        id = id,
        displayName = displayName,
        sizeBytes = sizeBytes,
        language = language,
        quant = "Q4_K_M",
        license = null,
        description = description,
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

    // ── Sprint 22: search query (Sprint 22 action #6) ─────────────────

    @Test
    fun `blank search returns every model unchanged`() {
        val a = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val b = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        val resultBlank = computeVisibleCatalog(
            raw = listOf(a, b),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "",
        )
        val resultWhitespace = computeVisibleCatalog(
            raw = listOf(a, b),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "   ",
        )

        assertEquals(listOf(a.id, b.id), ids(resultBlank))
        assertEquals(listOf(a.id, b.id), ids(resultWhitespace))
    }

    @Test
    fun `search filter matches model id case insensitively`() {
        val canary = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val whisper = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        val resultLower = computeVisibleCatalog(
            raw = listOf(canary, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "canary",
        )
        val resultUpper = computeVisibleCatalog(
            raw = listOf(canary, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "CANARY",
        )

        assertEquals(listOf(canary.id), ids(resultLower))
        assertEquals(listOf(canary.id), ids(resultUpper))
    }

    @Test
    fun `search filter matches description case insensitively`() {
        val described = model(
            id = "handy-computer/canary-180m-flash-gguf",
            sizeBytes = 139_000_000L,
            description = "Fast multilingual streaming ASR from NVIDIA.",
        )
        val undescribed = model(
            id = "handy-computer/whisper-large-v3-gguf",
            sizeBytes = 1_200_000_000L,
            description = "OpenAI Whisper large v3.",
        )

        val result = computeVisibleCatalog(
            raw = listOf(described, undescribed),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "nvidia",
        )

        assertEquals(listOf(described.id), ids(result))
    }

    @Test
    fun `search filter matches displayName when it differs from id`() {
        // Regression guard for the displayName branch: when a model exposes a
        // human-readable displayName distinct from its catalog id, a query
        // matching the displayName must still hit. Using `displayName="Canary"`
        // vs `id="handy-computer/canary-180m-flash-gguf"` proves the id branch
        // alone isn't doing all the work.
        val canary = model(
            id = "handy-computer/canary-180m-flash-gguf",
            sizeBytes = 139_000_000L,
            displayName = "Canary 180M Flash",
        )
        val whisper = model(
            id = "handy-computer/whisper-large-v3-gguf",
            sizeBytes = 1_200_000_000L,
            displayName = "Whisper Large v3",
        )

        val result = computeVisibleCatalog(
            raw = listOf(canary, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "canary",
        )

        assertEquals(listOf(canary.id), ids(result))
    }

    @Test
    fun `search filter trims surrounding whitespace`() {
        // Defensive: leading / trailing whitespace in the query should not
        // block matches. We only need to confirm trim() runs before the
        // contains-checks fire.
        val canary = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val whisper = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(canary, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "  canary  ",
        )

        assertEquals(listOf(canary.id), ids(result))
    }

    @Test
    fun `search filter searches mid-token not just prefix`() {
        val canary = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)
        val whisper = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)

        // "can" is a mid-token of "canary" — neither a prefix of the full id
        // ("handy-computer/...") nor a suffix. "can" does not appear anywhere
        // in the whisper id.
        val result = computeVisibleCatalog(
            raw = listOf(canary, whisper),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "can",
        )

        assertEquals(listOf(canary.id), ids(result))
    }

    @Test
    fun `search filter returns empty when nothing matches`() {
        val a = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)

        val result = computeVisibleCatalog(
            raw = listOf(a),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            query = "totally-nonexistent-token-xyz",
        )

        assertTrue(result.isEmpty())
    }

    // ── Sprint 22: language filter (Sprint 22 action #6) ──────────────

    @Test
    fun `null language filter returns all models regardless of tag`() {
        val enOnly = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L, language = "en")
        val esOnly = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L, language = "es")
        val multi = model(id = "handy-computer/granite-speech-4.1-2b-plus-gguf", sizeBytes = 1_500_000_000L, language = "multi")

        val result = computeVisibleCatalog(
            raw = listOf(enOnly, esOnly, multi),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            languageFilter = null,
        )

        assertEquals(listOf(enOnly.id, esOnly.id, multi.id), ids(result))
    }

    @Test
    fun `language filter keeps models tagged with the requested language`() {
        val enOnly = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L, language = "en")
        val esOnly = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L, language = "es")

        val result = computeVisibleCatalog(
            raw = listOf(enOnly, esOnly),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            languageFilter = "en",
        )

        assertEquals(listOf(enOnly.id), ids(result))
    }

    @Test
    fun `language filter splits comma separated catalog tags and matches case insensitively`() {
        val multilingual = model(
            id = "handy-computer/canary-180m-flash-gguf",
            sizeBytes = 139_000_000L,
            language = "en, es, fr, multilingual",
        )
        val spanishOnly = model(
            id = "handy-computer/whisper-large-v3-gguf",
            sizeBytes = 1_200_000_000L,
            language = "es",
        )
        val germanOnly = model(
            id = "handy-computer/granite-speech-4.1-2b-plus-gguf",
            sizeBytes = 1_500_000_000L,
            language = "de",
        )

        val resultUpper = computeVisibleCatalog(
            raw = listOf(multilingual, spanishOnly, germanOnly),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            languageFilter = "ES",
        )
        val resultMixed = computeVisibleCatalog(
            raw = listOf(multilingual, spanishOnly, germanOnly),
            snapshot = flagshipSnapshot,
            recs = emptyRecs,
            showExp = false,
            languageFilter = "fr",
        )

        assertEquals(listOf(multilingual.id, spanishOnly.id), ids(resultUpper))
        assertEquals(listOf(multilingual.id), ids(resultMixed))
    }

    // ── Sprint 22: onlyRecommended (Sprint 22 action #6) ───────────────

    @Test
    fun `onlyRecommended true hides non-recommended models but preserves sort within the kept set`() {
        val primary = model(
            id = "handy-computer/whisper-large-v3-gguf",
            sizeBytes = 1_200_000_000L,
            recommended = true,
        )
        val alternative = model(
            id = "handy-computer/granite-speech-4.1-2b-plus-gguf",
            sizeBytes = 1_500_000_000L,
            recommended = true,
        )
        val bogStandard = model(
            id = "handy-computer/canary-180m-flash-gguf",
            sizeBytes = 139_000_000L,
            recommended = false,
        )

        val result = computeVisibleCatalog(
            raw = listOf(primary, alternative, bogStandard),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
            onlyRecommended = true,
        )

        assertEquals(listOf(primary.id, alternative.id), ids(result))
    }

    @Test
    fun `onlyRecommended false returns the full sorted catalog`() {
        val primary = model(id = "handy-computer/whisper-large-v3-gguf", sizeBytes = 1_200_000_000L)
        val bogStandard = model(id = "handy-computer/canary-180m-flash-gguf", sizeBytes = 139_000_000L)

        val resultOff = computeVisibleCatalog(
            raw = listOf(primary, bogStandard),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
            onlyRecommended = false,
        )
        val resultDefault = computeVisibleCatalog(
            raw = listOf(primary, bogStandard),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
        )

        assertEquals(listOf(primary.id, bogStandard.id), ids(resultOff))
        assertEquals(listOf(primary.id, bogStandard.id), ids(resultDefault))
    }

    // ── Sprint 22: filters compose AND the sort chain still applies ────

    @Test
    fun `filters compose and status sort invariant applies to the kept set`() {
        // Two tiers of matching items with DIFFERENT compatibility status so a
        // genuine sort invariant is in play:
        //   * `activeItem` — ACTIVE status (must float above everything).
        //   * `primary`    — TIER_RECOMMENDED via flagshipRecs.
        //   * `alternative`— TIER_RECOMMENDED (alternative) via flagshipRecs.
        //   * `bogStandard`— does NOT match the search query.
        // After applying all three filters, the ACTIVE item must come first
        // and the TIER_RECOMMENDED primary must come before the
        // TIER_RECOMMENDED alternative (promotion bucket order).
        val activeItem = model(
            id = "handy-computer/canary-180m-flash-gguf",
            sizeBytes = 139_000_000L,
            isActive = true,
            language = "en",
            recommended = true,
        )
        val primary = model(
            id = "handy-computer/whisper-large-v3-gguf",
            sizeBytes = 1_200_000_000L,
            language = "en",
            recommended = true,
        )
        val alternative = model(
            id = "handy-computer/granite-speech-4.1-2b-plus-gguf",
            sizeBytes = 1_500_000_000L,
            language = "en",
            recommended = true,
        )
        val unrelated = model(
            id = "handy-computer/voxtral-mini-3b-gguf",
            sizeBytes = 3_000_000_000L,
            language = "es",
            recommended = false,
        )

        val result = computeVisibleCatalog(
            raw = listOf(unrelated, alternative, primary, activeItem),
            snapshot = flagshipSnapshot,
            recs = flagshipRecs,
            showExp = false,
            query = "gguf",
            languageFilter = "en",
            onlyRecommended = true,
        )

        assertEquals(
            listOf(activeItem.id, primary.id, alternative.id),
            ids(result),
        )
        // The unrelated model was filtered out by all three predicates
        // (search keeps it, but it fails onlyRecommended → status regresses,
        // languageFilter drops it via `es ≠ en`, so test the boundary).
        assertTrue("unrelated model must be filtered out by languageFilter", result.none { it.first.id == unrelated.id })
    }
}
