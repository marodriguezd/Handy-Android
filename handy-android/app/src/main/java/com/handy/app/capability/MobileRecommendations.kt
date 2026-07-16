package com.handy.app.capability

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.json.JSONObject

/**
 * Recommendations for a single [DeviceTier]: one primary model plus a list of
 * alternative models that are promoted over the global catalog.
 */
data class TierRecommendations(
    val primary: String,
    val alternatives: List<String> = emptyList(),
)

/**
 * In-memory representation of the curated `mobile_recommended.json` asset.
 * Keys of the underlying map are [DeviceTier] names ("LOW", "MID", ...).
 */
data class MobileRecommendationsFile(
    val version: Int,
    val tiers: Map<DeviceTier, TierRecommendations>,
) {
    /** Recommendations for the given tier, or null if the asset lacks an entry. */
    fun forTier(tier: DeviceTier): TierRecommendations? = tiers[tier]

    /** True when [id] is the primary recommendation for [tier]. */
    fun isPrimaryFor(tier: DeviceTier, id: String): Boolean =
        tiers[tier]?.primary == id

    /** True when [id] is one of the alternatives for [tier]. */
    fun isAlternativeFor(tier: DeviceTier, id: String): Boolean =
        tiers[tier]?.alternatives?.contains(id) == true

    /** Priority bucket used by [ModelsViewModel.computeVisibleList] sort. */
    fun promotionBucket(tier: DeviceTier, id: String): Int = when {
        isPrimaryFor(tier, id) -> 0
        isAlternativeFor(tier, id) -> 1
        else -> 2
    }
}

/**
 * Loader for the curated `mobile_recommended.json` asset. Reads synchronously
 * on first access (file is <2 KB), caches in a process-wide singleton, and
 * is safe to call from any thread.
 *
 * Asset location: `handy-android/app/src/main/assets/mobile_recommended.json`.
 * Co-localized conceptually with `src-tauri/src/catalog/catalog.json`.
 */
object MobileRecommendations {
    private const val ASSET_PATH = "mobile_recommended.json"

    @Volatile
    private var cached: MobileRecommendationsFile? = null

    /**
     * Returns the parsed recommendations, loading + parsing the asset on the
     * first call. Concurrent callers after the cache is populated get the
     * same instance without re-reading the asset.
     */
    fun load(context: Context): MobileRecommendationsFile {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: parseAsset(context.applicationContext).also { cached = it }
        }
    }

    /** Test seam: clears the cache so a subsequent [load] re-parses. */
    @JvmStatic
    fun resetForTesting() {
        synchronized(this) { cached = null }
    }

    private fun parseAsset(context: Context): MobileRecommendationsFile =
        parseJson(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })

    /**
     * Parses a `mobile_recommended.json`-shaped string into an in-memory
     * [MobileRecommendationsFile]. Marked [VisibleForTesting] so the
     * test-only intent is lint-checkable — production callers must go
     * through [load] which transparently reads the bundled asset and
     * caches the parsed result.
     *
     * The annotation is documentation-grade: visibility is widened
     * because Android `app/src/test/` is its own source-set JAR under
     * AGP and `internal` Kotlin visibility does not cross that boundary.
     * The KDoc + `@VisibleForTesting` annotation together signal intent;
     * a future misuse from production code would be flagged by
     * AndroidLint's `VisibleForTesting` check.
     *
     * Missing tiers are silently skipped so the partial-asset case
     * degrades gracefully. Bad-JSON throws via [JSONObject] wrapping,
     * mirroring the production behavior under the `org.json:json`
     * testImplementation alias.
     */
    @VisibleForTesting
    fun parseJson(raw: String): MobileRecommendationsFile {
        val root = JSONObject(raw)
        val tiersObj = root.optJSONObject("tiers") ?: JSONObject()
        val out = mutableMapOf<DeviceTier, TierRecommendations>()
        for (tier in DeviceTier.entries) {
            val tierObj = tiersObj.optJSONObject(tier.name) ?: continue
            val primary = tierObj.optString("primary", "")
            if (primary.isEmpty()) continue
            val alts = mutableListOf<String>()
            tierObj.optJSONArray("alternatives")?.let { arr ->
                for (i in 0 until arr.length()) {
                    alts.add(arr.getString(i))
                }
            }
            out[tier] = TierRecommendations(primary = primary, alternatives = alts)
        }
        return MobileRecommendationsFile(
            version = root.optInt("version", 1),
            tiers = out,
        )
    }
}
