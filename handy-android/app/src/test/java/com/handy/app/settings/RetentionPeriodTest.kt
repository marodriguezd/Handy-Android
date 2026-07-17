package com.handy.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sprint 25b Phase E — 2 JVM tests for [RetentionPeriod] enum.
 *
 * Verifies the day-count contract (`null` = Never, otherwise N>=1)
 * and the ascending enum contract used by the dropdown options.
 */
class RetentionPeriodTest {

    @Test
    fun `Never has null days and every other entry has a positive day count`() {
        assertNull(RetentionPeriod.Never.days)
        RetentionPeriod.entries.forEach { entry ->
            if (entry != RetentionPeriod.Never) {
                requireNotNull(entry.days)
                assertEquals(
                    "days must be > 0 for ${entry.name}",
                    true,
                    entry.days!! > 0,
                )
            }
        }
    }

    @Test
    fun `day values match the documented options in ascending order`() {
        // Locks the dropdown ordering so a refactor that adds a new
        // bucket in the middle surfaces here (and prompts an explicit
        // UI review by the test author).
        val expected = listOf(
            RetentionPeriod.Never,
            RetentionPeriod.OneDay,
            RetentionPeriod.OneWeek,
            RetentionPeriod.OneMonth,
            RetentionPeriod.OneYear,
        )
        assertEquals(expected, RetentionPeriod.entries.toList())

        val expectedDays = listOf(1L, 7L, 30L, 365L)
        val actualDays = expected
            .drop(1)
            .map { it.days!! }
        assertEquals(expectedDays, actualDays)
    }
}
