package com.handy.app.capability

import com.handy.app.model.ModelInfo

enum class CompatibilityStatus { ACTIVE, TIER_RECOMMENDED, TIER_RECOMMENDED_DEEP, FIT, EXCEEDS, IMPOSSIBLE }
enum class CompatibilityBadge { EXPERIMENTAL, HEAVY_GATE, EXCEEDS_RAM, LARGE_HEAP_REQUIRED }

data class ModelCompatibility(
    val tier: ModelCapability,
    val status: CompatibilityStatus,
    val badges: List<CompatibilityBadge>,
    val requiresConsent: Boolean,
    val hidden: Boolean,
)

fun computeCompatibility(
    model: ModelInfo,
    snapshot: CapabilitySnapshot,
    showExperimental: Boolean,
): ModelCompatibility {
    val tier = ModelCapability.fromModel(model)
    val deviceTier = snapshot.toTier()
    val deviceMax = deviceTier.maxRecommendedModelCapability

    // 1) Experimental gating — hide unless user opted in
    if (ModelCapability.isExperimental(model.id) && !showExperimental) {
        return ModelCompatibility(
            tier = tier,
            status = CompatibilityStatus.IMPOSSIBLE,
            badges = emptyList(),
            requiresConsent = false,
            hidden = true,
        )
    }

    // 2) Compute status
    val status: CompatibilityStatus = when {
        model.isActive -> CompatibilityStatus.ACTIVE
        model.recommended && tier.ordinal <= deviceMax.ordinal -> CompatibilityStatus.TIER_RECOMMENDED_DEEP
        tier.ordinal <= deviceMax.ordinal -> CompatibilityStatus.TIER_RECOMMENDED
        tier.ordinal == deviceMax.ordinal + 1 -> CompatibilityStatus.FIT // off-by-one is borderline
        else -> CompatibilityStatus.EXCEEDS
    }

    // 3) Compute badges
    val badges = buildList {
        if (ModelCapability.isExperimental(model.id)) add(CompatibilityBadge.EXPERIMENTAL)
        if (ModelCapability.isHeavyGate(model.id)) add(CompatibilityBadge.HEAVY_GATE)
        if (tier.ordinal > deviceMax.ordinal + 1) {
            add(CompatibilityBadge.EXCEEDS_RAM)
            if (tier == ModelCapability.EXTREME) add(CompatibilityBadge.LARGE_HEAP_REQUIRED)
        }
    }

    // 4) Consent gate for heavy / extreme models
    val requiresConsent = ModelCapability.isHeavyGate(model.id) ||
        (tier == ModelCapability.EXTREME && deviceTier == DeviceTier.TABLET)

    return ModelCompatibility(
        tier = tier,
        status = status,
        badges = badges,
        requiresConsent = requiresConsent,
        hidden = false,
    )
}
