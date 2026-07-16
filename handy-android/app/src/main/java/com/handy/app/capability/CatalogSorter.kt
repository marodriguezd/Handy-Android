package com.handy.app.capability

import com.handy.app.model.ModelInfo

/**
 * Pure function that filters and sorts the raw model catalog for the catalog UI.
 *
 * No Android dependencies: every input is passed explicitly, so the function is
 * trivial to unit-test on a pure JVM harness.
 *
 * Pipeline (cheapest predicates first, capability + sort last):
 *   1. **User filters** — search query (displayName / id / description,
 *      case-insensitive); language tag (split on `,`, trim, case-insensitive);
 *      `onlyRecommended` (boolean).
 *   2. **Capability annotation** — `computeCompatibility(model, snapshot, showExp)`
 *      assigns status + badges for the current device.
 *   3. **Experimental / hidden gating** — models flagged `hidden` (e.g.
 *      heavy-gate / large-heap requires-consent) are removed.
 *   4. **Sort** — `compareBy` over
 *        (a) `CompatibilityStatus.ordinal` — ACTIVE first, then TIER_RECOMMENDED,
 *            TIER_RECOMMENDED_DEEP, FIT, EXCEEDS, IMPOSSIBLE.
 *        (b) `MobileRecommendationsFile.promotionBucket` for the device tier —
 *            0 = primary, 1 = alternative, 2 = not promoted.
 *        (c) `ModelInfo.recommended` flag — global recommendations float up.
 *        (d) `ModelInfo.sizeBytes` — smallest first within the same bucket.
 *
 * The three user-filter parameters default to "no filter" so that the 10
 * tests written for Sprint 16 still compile and pass without modification.
 *
 * @param raw Raw list of models as returned by the JNI catalog.
 * @param snapshot Device capability snapshot used to compute compatibility.
 * @param recs Parsed mobile recommendations for the current device tier.
 * @param showExp Whether experimental models should be shown (`hidden=false`).
 * @param query Free-text search across `displayName`, `id`, and `description`.
 *               Blank / whitespace-only matches everything.
 * @param languageFilter Required language code (e.g. `"en"`, `"es"`); `null`
 *                       removes the filter. Matches comma-split tags
 *                       case-insensitively.
 * @param onlyRecommended When `true`, filters out models with
 *                        `recommended=false`.
 * @return Filtered, annotated, and sorted list of models with their
 *         compatibility info.
 */
fun computeVisibleCatalog(
    raw: List<ModelInfo>,
    snapshot: CapabilitySnapshot,
    recs: MobileRecommendationsFile,
    showExp: Boolean,
    query: String = "",
    languageFilter: String? = null,
    onlyRecommended: Boolean = false,
): List<Pair<ModelInfo, ModelCompatibility>> {
    if (raw.isEmpty()) return emptyList()

    val tier = snapshot.toTier()
    val q = query.trim()

    return raw.asSequence()
        // ── 1. Cheap user filters (no compat computation needed) ──
        .filter { model ->
            q.isBlank() ||
                model.displayName.contains(q, ignoreCase = true) ||
                model.id.contains(q, ignoreCase = true) ||
                (model.description?.contains(q, ignoreCase = true) ?: false)
        }
        .filter { model ->
            languageFilter == null ||
                model.language.split(",")
                    .map(String::trim)
                    .any { it.equals(languageFilter, ignoreCase = true) }
        }
        .filter { model -> !onlyRecommended || model.recommended }
        // ── 2. Capability annotation ──
        .map { it to computeCompatibility(it, snapshot, showExp) }
        // ── 3. Hidden / heavy-gate gating ──
        .filterNot { it.second.hidden }
        // ── 4. Sort ──
        .sortedWith(
            compareBy<Pair<ModelInfo, ModelCompatibility>>(
                { it.second.status.ordinal },
                { recs.promotionBucket(tier, it.first.id) },
                { if (it.first.recommended) 0 else 1 },
                { it.first.sizeBytes },
            )
        )
        .toList()
}
