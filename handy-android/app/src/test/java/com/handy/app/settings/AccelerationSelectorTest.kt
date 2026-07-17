package com.handy.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 25b Phase E — 4 JVM tests for [AccelerationBackend] enum.
 *
 * Locks down the experimental-flag gate that drives
 * `HandySegmentedButton(enabled = state.experimentalEnabled)`. A
 * regression on which backends are user-shipping vs experimental
 * surfaces as a single red test rather than slipping into prod.
 */
class AccelerationSelectorTest {

    @Test
    fun `CPU is non-experimental and the only stable backend`() {
        assertEquals("CPU", AccelerationBackend.CPU.name)
        assertFalse("CPU must not be experimental", AccelerationBackend.CPU.isExperimental)
    }

    @Test
    fun `Vulkan and NNAPI are experimental backends`() {
        // Per BACKENDS.md: Vulkan is "next realistic GPU step", NNAPI
        // is "deprecated in Android 15, not recommended for new dev".
        // Until a backend ships testing green on Android, both stay
        // gated behind the experimentalEnabled switch.
        assertTrue(AccelerationBackend.Vulkan.isExperimental)
        assertTrue(AccelerationBackend.NNAPI.isExperimental)
    }

    @Test
    fun `all entries round-trip their name through valueOf`() {
        // SettingsStore.accelerationBackend reads the persisted String
        // and rehydrates the enum; rename-side would silently fall to
        // the runCatching default = CPU. This test catches renames.
        AccelerationBackend.entries.forEach { entry ->
            assertEquals(entry, AccelerationBackend.valueOf(entry.name))
        }
    }

    @Test
    fun `exactly one stable backend exists`() {
        // Sprint 29 may add Metal/Adreno. The invariant today (and
        // for the foreseeable future) is exactly ONE non-experimental
        // choice. If this test fails on a Sprint 29 wire-up, that's
        // a deliberate decision surface; do not paper over it.
        val stableCount = AccelerationBackend.entries
            .count { !it.isExperimental }
        assertEquals(
            "expected exactly 1 stable backend, found $stableCount",
            1,
            stableCount,
        )
    }
}
