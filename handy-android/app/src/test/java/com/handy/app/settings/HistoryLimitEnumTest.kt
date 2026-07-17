package com.handy.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sprint 25b Phase E — 2 JVM tests for [HistoryLimit] enum.
 *
 * Verifies the cap contract (`null` = Unlimited, otherwise the Int
 * ceiling) so a regression on either side of the contract is
 * spotted instantly.
 */
class HistoryLimitEnumTest {

    @Test
    fun `Unlimited's cap is null and every other entry has a positive cap`() {
        assertNull(HistoryLimit.Unlimited.cap)
        HistoryLimit.entries.forEach { entry ->
            if (entry != HistoryLimit.Unlimited) {
                requireNotNull(entry.cap)
                assertEquals(
                    "cap must be > 0 for ${entry.name}",
                    true,
                    entry.cap!! > 0,
                )
            }
        }
    }

    @Test
    fun `cap values match the documented options in ascending order`() {
        // The dropdown on Advanced Settings reads this list verbatim.
        // Reordering this data class is a UI-change-grade event; the
        // test locks the contract so any rename surfaces immediately.
        val expected = listOf(
            HistoryLimit.Unlimited,
            HistoryLimit.Limited5,
            HistoryLimit.Limited10,
            HistoryLimit.Limited25,
            HistoryLimit.Limited50,
            HistoryLimit.Limited100,
            HistoryLimit.Limited250,
        )
        assertEquals(expected, HistoryLimit.entries.toList())

        // And ascending cap values (excluding Unlimited which is null).
        val ascendingCaps = listOf(5, 10, 25, 50, 100, 250)
        val actualCaps = expected
            .drop(1) // skip Unlimited
            .map { it.cap!! }
        assertEquals(ascendingCaps, actualCaps)
    }
}
