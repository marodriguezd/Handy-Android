package com.handy.app.capability

data class CapabilitySnapshot(
    val totalMemBytes: Long,
    val availMemBytes: Long,
    val maxMemoryProcessBytes: Long,
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val cpuCores: Int,
    val sdkInt: Int,
) {
    val totalMemGbReport: Float get() = totalMemBytes / (1024f * 1024f * 1024f)

    /** True if device tier permits at least HEAVY models. */
    val canRunHeavyModels: Boolean get() = toTier().ordinal >= DeviceTier.FLAGSHIP.ordinal

    fun toTier(): DeviceTier {
        val gb = totalMemBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return when {
            // LOW: <=1.5 GB OR low-ram device up to 2 GB
            gb <= 1.5 || (isLowRamDevice && gb <= 2.0) -> DeviceTier.LOW
            gb <= 3.5 -> DeviceTier.MID
            gb <= 6.5 -> DeviceTier.HIGH
            gb <= 12.5 -> DeviceTier.FLAGSHIP
            else -> DeviceTier.TABLET
        }
    }
}
