package com.handy.app.capability

import com.handy.app.model.ModelInfo

/**
 * Pure function that annotates, filters, and sorts the raw model catalog
 * for the catalog UI.
 *
 * The function has no Android dependencies; all inputs are passed explicitly,
 * making it trivial to unit-test on the JVM.
 *
 * Sort priority (ascending):
 *   1. [ModelCompatibility.status.ordinal] — ACTIVE first, then TIER_RECOMMENDED,
 *      TIER_RECOMMENDED_DEEP, FIT, EXCEEDS, IMPOSSIBLE.
 *   2. [MobileRecommendationsFile.promotionBucket] for the device tier —
 *      0 = primary, 1 = alternative, 2 = not promoted.
 *   3. Global `recommended` flag — recommended models float up.
 *   4. Model size (bytes) — smallest first within the same bucket.
 *
 * @param raw Raw list of models as returned by the JNI catalog.
 * @param snapshot Device capability snapshot used to compute compatibility.
 * @param recs Parsed mobile recommendations for the current device tier.
 * @param showExp Whether experimental models should be shown.
 * @return Filtered and sorted list of models with their compatibility info.
 */
fun computeVisibleCatalog(
    raw: List<ModelInfo>,
    snapshot: CapabilitySnapshot,
    recs: MobileRecommendationsFile,
    showExp: Boolean,
): List<Pair<ModelInfo, ModelCompatibility>> {
    if (raw.isEmpty()) return emptyList()

    val tier = snapshot.toTier()
    return raw
        .map { it to computeCompatibility(it, snapshot, showExp) }
        .filterNot { it.second.hidden }
        .sortedWith(
            compareBy<Pair<ModelInfo, ModelCompatibility>>(
                { it.second.status.ordinal },
                { recs.promotionBucket(tier, it.first.id) },
                { if (it.first.recommended) 0 else 1 },
                { it.first.sizeBytes },
            )
        )
}
