package com.handy.app.capability

import androidx.annotation.StringRes
import com.handy.app.R

enum class DeviceTier(@StringRes val displayKey: Int) {
    LOW(R.string.header_tier_low),
    MID(R.string.header_tier_mid),
    HIGH(R.string.header_tier_high),
    FLAGSHIP(R.string.header_tier_flagship),
    TABLET(R.string.header_tier_tablet);

    /** Max model capability this device tier supports safely. */
    val maxRecommendedModelCapability: ModelCapability
        get() = when (this) {
            LOW -> ModelCapability.ULTRA_LIGHT
            MID -> ModelCapability.LIGHT
            HIGH -> ModelCapability.MEDIUM
            FLAGSHIP -> ModelCapability.HEAVY
            TABLET -> ModelCapability.EXTREME
        }
}
