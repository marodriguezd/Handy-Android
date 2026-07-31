package com.handy.android

// GENERATED FILE - do not edit manually.
// Source: src-tauri/src/catalog/catalog.json
// Generator: scripts/generate_android_model_catalog.py

/** The verified Android download bridge for a catalog entry. */
data class AndroidDownloadSpec(
    val id: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

/** A mobile-sized model from the desktop catalog and its Android status. */
data class ModelCatalogEntry(
    val id: String,
    val name: String,
    val parameters: String,
    val parameterCount: Long,
    val description: String,
    val languageCount: Int,
    val architecture: String,
    /** Size of the selected artifact shown by the storefront. */
    val downloadSizeBytes: Long,
    val androidDownload: AndroidDownloadSpec? = null,
) {
    val isAvailableOnAndroid: Boolean
        get() = androidDownload != null
}

object ModelCatalog {
    /** Source metadata copied from the desktop catalog snapshot. */
    const val SOURCE_CATALOG_VERSION = 2
    const val SOURCE_CATALOG_GENERATED_AT = "2026-07-23T10:15:59+00:00"
    const val MAX_PARAMETERS = 1200000000L

    /** All catalog entries at or below the Android mobile parameter limit. */
    val models: List<ModelCatalogEntry> = listOf(
        ModelCatalogEntry("handy-computer/parakeet-unified-en-0.6b-gguf", "Parakeet Unified EN 0.6B", "0.6B", 600000000L, "Fast, accurate live English transcription", 1, "parakeet", 731357568L),
        ModelCatalogEntry("handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf", "Nemotron Streaming 3.5", "0.6B", 600000000L, "Live multilingual transcription across 28 languages", 28, "parakeet", 751094240L),
        ModelCatalogEntry("handy-computer/canary-180m-flash-gguf", "Canary 180M Flash", "180M", 180000000L, "Tiny and instant, runs well on any hardware", 4, "canary", 218447552L),
        ModelCatalogEntry("handy-computer/whisper-medium-gguf", "Whisper Medium", "764M", 764000000L, "Broadest language, but may run a bit slow", 99, "whisper", 491782656L,
            AndroidDownloadSpec(
                id = "medium",
                fileName = "whisper-medium-q4_1.bin",
                url = "https://blob.handy.computer/whisper-medium-q4_1.bin",
                sha256 = "79283fc1f9fe12ca3248543fbd54b73292164d8df5a16e095e2bceeaaabddf57",
                sizeBytes = 491782656L,
            ),
        ),
        ModelCatalogEntry("handy-computer/parakeet-tdt-0.6b-v3-gguf", "Parakeet TDT 0.6B v3", "0.6B", 600000000L, "Fast and accurate. Supports 25 European languages", 25, "parakeet", 739508576L),
        ModelCatalogEntry("handy-computer/parakeet-tdt-0.6b-v2-gguf", "Parakeet TDT 0.6B v2", "0.6B", 600000000L, "English only. The best model for English speakers", 1, "parakeet", 729574912L),
        ModelCatalogEntry("handy-computer/Qwen3-ASR-0.6B-gguf", "Qwen3-ASR 0.6B", "782M", 782000000L, "Excellent multilingual model", 30, "qwen3_asr", 850423456L),
        ModelCatalogEntry("handy-computer/Fun-ASR-MLT-Nano-2512-gguf", "Fun-ASR Nano Multilingual", "985M", 985000000L, "A tiny multilingual model", 31, "funasr_nano", 891271232L),
        ModelCatalogEntry("handy-computer/canary-1b-flash-gguf", "Canary 1B Flash", "1B", 1000000000L, "4-language speech-to-text with translation.", 4, "canary", 769563424L),
        ModelCatalogEntry("handy-computer/canary-1b-v2-gguf", "Canary 1B v2", "1B", 1000000000L, "25-language speech-to-text with translation.", 25, "canary", 836664032L),
        ModelCatalogEntry("handy-computer/canary-1b-gguf", "Canary 1B", "1B", 1000000000L, "4-language speech-to-text with translation.", 4, "canary", 837694272L),
        ModelCatalogEntry("handy-computer/Fun-ASR-Nano-2512-gguf", "Fun-ASR Nano", "985M", 985000000L, "3-language speech-to-text.", 3, "funasr_nano", 891270912L),
        ModelCatalogEntry("handy-computer/gigaam-v3-ctc-gguf", "GigaAM v3 CTC", "221M", 221000000L, "Russian speech-to-text with token-level timestamps.", 1, "gigaam", 271803328L),
        ModelCatalogEntry("handy-computer/gigaam-v3-e2e-ctc-gguf", "GigaAM v3 E2E-CTC", "221M", 221000000L, "Russian speech-to-text with token-level timestamps.", 1, "gigaam", 272151136L),
        ModelCatalogEntry("handy-computer/gigaam-v3-rnnt-gguf", "GigaAM v3 RNN-T", "222M", 222000000L, "Russian speech-to-text with token-level timestamps.", 1, "gigaam", 273022880L),
        ModelCatalogEntry("handy-computer/gigaam-v3-e2e-rnnt-gguf", "GigaAM v3 E2E-RNN-T", "223M", 223000000L, "Russian speech-to-text with token-level timestamps.", 1, "gigaam", 273724832L),
        ModelCatalogEntry("handy-computer/medasr-gguf", "MedASR", "105M", 105000000L, "English speech-to-text with token-level timestamps.", 1, "medasr", 127712448L),
        ModelCatalogEntry("handy-computer/moonshine-streaming-tiny-gguf", "Moonshine Streaming Tiny", "44M", 44000000L, "English speech-to-text with streaming.", 1, "moonshine_streaming", 50462816L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-gguf", "Moonshine Tiny", "27M", 27000000L, "English speech-to-text.", 1, "moonshine", 35466912L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-ar-gguf", "Moonshine Tiny (Arabic)", "27M", 27000000L, "Arabic speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-ja-gguf", "Moonshine Tiny (Japanese)", "27M", 27000000L, "Japanese speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-ko-gguf", "Moonshine Tiny (Korean)", "27M", 27000000L, "Korean speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-uk-gguf", "Moonshine Tiny (Ukrainian)", "27M", 27000000L, "Ukrainian speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-vi-gguf", "Moonshine Tiny (Vietnamese)", "27M", 27000000L, "Vietnamese speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-tiny-zh-gguf", "Moonshine Tiny (Chinese)", "27M", 27000000L, "Chinese speech-to-text.", 1, "moonshine", 35466944L),
        ModelCatalogEntry("handy-computer/moonshine-base-gguf", "Moonshine Base", "62M", 62000000L, "English speech-to-text.", 1, "moonshine", 77476480L),
        ModelCatalogEntry("handy-computer/moonshine-base-ar-gguf", "Moonshine Base (Arabic)", "62M", 62000000L, "Arabic speech-to-text.", 1, "moonshine", 77476480L),
        ModelCatalogEntry("handy-computer/moonshine-base-ja-gguf", "Moonshine Base (Japanese)", "62M", 62000000L, "Japanese speech-to-text.", 1, "moonshine", 77476480L),
        ModelCatalogEntry("handy-computer/moonshine-base-ko-gguf", "Moonshine Base (Korean)", "62M", 62000000L, "Korean speech-to-text.", 1, "moonshine", 77476480L),
        ModelCatalogEntry("handy-computer/moonshine-base-uk-gguf", "Moonshine Base (Ukrainian)", "62M", 62000000L, "Ukrainian speech-to-text.", 1, "moonshine", 77476512L),
        ModelCatalogEntry("handy-computer/moonshine-base-vi-gguf", "Moonshine Base (Vietnamese)", "62M", 62000000L, "Vietnamese speech-to-text.", 1, "moonshine", 77476512L),
        ModelCatalogEntry("handy-computer/moonshine-base-zh-gguf", "Moonshine Base (Chinese)", "62M", 62000000L, "Chinese speech-to-text.", 1, "moonshine", 77476480L),
        ModelCatalogEntry("handy-computer/moonshine-streaming-small-gguf", "Moonshine Streaming Small", "140M", 140000000L, "English speech-to-text with streaming.", 1, "moonshine_streaming", 198506848L),
        ModelCatalogEntry("handy-computer/moonshine-streaming-medium-gguf", "Moonshine Streaming Medium", "266M", 266000000L, "English speech-to-text with streaming.", 1, "moonshine_streaming", 295793568L),
        ModelCatalogEntry("handy-computer/nemotron-speech-streaming-en-0.6b-gguf", "Nemotron Speech Streaming EN", "0.6B", 600000000L, "English speech-to-text with streaming, token-level timestamps.", 1, "parakeet", 729650176L),
        ModelCatalogEntry("handy-computer/parakeet-tdt_ctc-110m-gguf", "Parakeet TDT-CTC 110M", "110M", 110000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 135373280L),
        ModelCatalogEntry("handy-computer/multitalker-parakeet-streaming-0.6b-v1-gguf", "Multitalker Parakeet Streaming EN", "0.6B", 600000000L, "English speech-to-text with streaming, token-level timestamps.", 1, "parakeet", 734123712L),
        ModelCatalogEntry("handy-computer/parakeet-ctc-0.6b-gguf", "Parakeet CTC 0.6B", "0.6B", 600000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 722271424L),
        ModelCatalogEntry("handy-computer/parakeet-rnnt-0.6b-gguf", "Parakeet RNN-T 0.6B", "0.6B", 600000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 729687456L),
        ModelCatalogEntry("handy-computer/parakeet-ctc-1.1b-gguf", "Parakeet CTC 1.1B", "1.1B", 1100000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 928584736L),
        ModelCatalogEntry("handy-computer/parakeet-tdt-1.1b-gguf", "Parakeet TDT 1.1B", "1.1B", 1100000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 935758496L),
        ModelCatalogEntry("handy-computer/parakeet-rnnt-1.1b-gguf", "Parakeet RNN-T 1.1B", "1.1B", 1100000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 935755008L),
        ModelCatalogEntry("handy-computer/parakeet-tdt_ctc-1.1b-gguf", "Parakeet TDT-CTC 1.1B", "1.1B", 1100000000L, "English speech-to-text with token-level timestamps.", 1, "parakeet", 935758080L),
        ModelCatalogEntry("handy-computer/SenseVoiceSmall-gguf", "SenseVoice Small", "234M", 234000000L, "5-language speech-to-text with auto language detection.", 5, "sensevoice", 252684608L),
        ModelCatalogEntry("handy-computer/whisper-tiny-gguf", "Whisper Tiny", "38M", 38000000L, "99-language speech-to-text with translation, auto language detection, segment-level timestamps.", 99, "whisper", 45981088L),
        ModelCatalogEntry("handy-computer/whisper-tiny.en-gguf", "Whisper Tiny (English)", "38M", 38000000L, "English speech-to-text with segment-level timestamps.", 1, "whisper", 45904544L),
        ModelCatalogEntry("handy-computer/whisper-base-gguf", "Whisper Base", "73M", 73000000L, "99-language speech-to-text with translation, auto language detection, segment-level timestamps.", 99, "whisper", 84962880L),
        ModelCatalogEntry("handy-computer/whisper-base.en-gguf", "Whisper Base (English)", "73M", 73000000L, "English speech-to-text with segment-level timestamps.", 1, "whisper", 84886208L),
        ModelCatalogEntry("handy-computer/whisper-small.en-gguf", "Whisper Small (English)", "242M", 242000000L, "English speech-to-text with segment-level timestamps.", 1, "whisper", 269674144L),
        ModelCatalogEntry("handy-computer/whisper-small-gguf", "Whisper Small", "242M", 242000000L, "99-language speech-to-text with translation, auto language detection, segment-level timestamps.", 99, "whisper", 487587840L,
            AndroidDownloadSpec(
                id = "small",
                fileName = "ggml-small.bin",
                url = "https://blob.handy.computer/ggml-small.bin",
                sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
                sizeBytes = 487587840L,
            ),
        ),
        ModelCatalogEntry("handy-computer/whisper-medium.en-gguf", "Whisper Medium (English)", "764M", 764000000L, "English speech-to-text with segment-level timestamps.", 1, "whisper", 831460928L),
        ModelCatalogEntry("handy-computer/whisper-large-v3-turbo-gguf", "Whisper Large v3 Turbo", "809M", 809000000L, "100-language speech-to-text with auto language detection, segment-level timestamps.", 100, "whisper", 1624244224L,
            AndroidDownloadSpec(
                id = "turbo",
                fileName = "ggml-large-v3-turbo.bin",
                url = "https://blob.handy.computer/ggml-large-v3-turbo.bin",
                sha256 = "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69",
                sizeBytes = 1624244224L,
            ),
        ),
    ).filter { it.parameterCount <= MAX_PARAMETERS }

    val downloadableModels: List<ModelCatalogEntry>
        get() = models.filter { it.isAvailableOnAndroid }.distinctBy { it.androidDownload?.id }

    fun find(id: String): ModelCatalogEntry? = models.firstOrNull { it.id == id }
}

fun formatModelSize(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        "%.1f GB".format(java.util.Locale.US, megabytes / 1024.0)
    } else {
        "%.0f MB".format(java.util.Locale.US, megabytes)
    }
}
