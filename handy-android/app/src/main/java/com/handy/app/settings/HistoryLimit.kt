package com.handy.app.settings

/**
 * Sprint 25b Phase C — UI display cap for the History scroll-pager.
 *
 * `cap == null` means UNLIMITED — `HistoryViewModel.loadMore()`
 * continues paginating until `nativeGetHistory` returns fewer rows
 * than `PAGE_SIZE`. `cap == N` clamps the visible list at N rows
 * across all pages (the SQLite DB itself is not trimmed; retention
 * is a separate cross-cut governed by [RetentionPeriod]).
 *
 * Persistent representation: stored as an `Int` in SharedPreferences
 * (0 = Unlimited, otherwise the slot index in [Entries]).
 * [ordinal] is reserved for that contract; UI code never iterates
 * `Entries` in display order (the dropdown is rendered in the order
 * declared here, not ordinal-based).
 */
enum class HistoryLimit(val cap: Int?) {
    Unlimited(null),
    Limited5(5),
    Limited10(10),
    Limited25(25),
    Limited50(50),
    Limited100(100),
    Limited250(250),
}
