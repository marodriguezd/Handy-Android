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
         * proceeding. Stored as bare slugs (no quant suffix, no asset prefix)
         * and matched against [ModelInfo.id] after slug normalization, so the
         * set stays in sync with the catalog across quantization variants.
         *
         * Includes all Voxtral variants — Voxtral Mini 4B Realtime is
         * technically stream-capable but still large, while Voxtral Small 24B
         * is impractical on phones.
         */
        val heavyGateSlugs: Set<String> = setOf(
            "Voxtral-Small-24B-2507",
            "Voxtral-Mini-4B-Realtime-2602",
            "Voxtral-Mini-3B-2507",
        )

        /**
         * Moonshine Base monolingual variants that have not been verified
         * end-to-end with transcribe-cpp on Android. Hidden by default,
         * exposed only when the user toggles "Show experimental".
         */
        val experimentalSlugs: Set<String> = setOf(
            "moonshine-base-ar",
            "moonshine-base-ko",
            "moonshine-base-uk",
            "moonshine-base-ja",
            "moonshine-base-vi",
            "moonshine-base-zh",
            "moonshine-base",
        )

        /**
         * Catalog-side path prefix used by every entry in the desktop
         * `src-tauri/src/catalog/catalog.json` file. Centralizing it here
         * makes the slug normalization dependency explicit if the catalog
         * ever migrates to a different namespace (e.g., an org slug).
         */
        private const val CATALOG_ID_PREFIX = "handy-computer/"

        /**
         * Artifact extension suffix appended by the GGUF catalog pipeline.
         * Stripped during slug normalization alongside [CATALOG_ID_PREFIX].
         */
        private const val CATALOG_ID_SUFFIX = "-gguf"

        /**
         * Strips the catalog path prefix and `-gguf` artifact suffix from
         * a [ModelInfo.id] so the resulting slug can be compared against
         * the curated heavy-gate / experimental sets below.
         *
         * Example: `"handy-computer/Voxtral-Mini-3B-2507-gguf"` →
         *          `"Voxtral-Mini-3B-2507"`.
         */
        private fun slugOf(modelId: String): String =
            modelId.removePrefix(CATALOG_ID_PREFIX).removeSuffix(CATALOG_ID_SUFFIX)

        fun isExperimental(id: String): Boolean = slugOf(id) in experimentalSlugs
        fun isHeavyGate(id: String): Boolean = slugOf(id) in heavyGateSlugs
    }
}
