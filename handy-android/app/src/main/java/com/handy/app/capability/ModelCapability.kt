package com.handy.app.capability

import com.handy.app.model.ModelInfo

enum class ModelCapability(val maxSizeMb: Int) {
    ULTRA_LIGHT(100),
    LIGHT(500),
    MEDIUM(1536),
    HEAVY(3072),
    EXTREME(Int.MAX_VALUE);

    companion object {
        fun fromModel(model: ModelInfo): ModelCapability {
            val mb = model.sizeBytes / (1024 * 1024)
            return entries.first { mb <= it.maxSizeMb }
        }

        /**
         * Models whose download is mandatory to warn the user about before
         * proceeding. Includes all Voxtral variants — Voxtral Mini 4B
         * Realtime is technically stream-capable but still large, while
         * Voxtral Small 24B is impractical on phones.
         */
        val heavyGateIds: Set<String> = setOf(
            "Voxtral-Small-24B-2507-Q5_K_M",
            "Voxtral-Mini-4B-Realtime-2602-Q4_K_M",
            "Voxtral-Mini-3B-2507-Q5_K_M",
        )

        /**
         * Moonshine Base monolingual variants that have not been verified
         * end-to-end with transcribe-cpp on Android. Hidden by default,
         * exposed only when the user toggles "Show experimental".
         */
        val experimentalIds: Set<String> = setOf(
            "moonshine-base-ar-Q8_0",
            "moonshine-base-ko-Q8_0",
            "moonshine-base-uk-Q8_0",
            "moonshine-base-ja-Q8_0",
            "moonshine-base-vi-Q8_0",
            "moonshine-base-zh-Q8_0",
            "moonshine-base-Q8_0",
        )

        fun isExperimental(id: String): Boolean = id in experimentalIds
        fun isHeavyGate(id: String): Boolean = id in heavyGateIds
    }
}
