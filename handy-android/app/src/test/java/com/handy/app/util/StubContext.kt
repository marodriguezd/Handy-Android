package com.handy.app.util

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * Test-only [ContextWrapper] that returns a hand-rolled [InMemorySharedPreferences]
 * from `getSharedPreferences(...)` regardless of the requested `name`/`mode`.
 * No Robolectric, no platform `SharedPreferencesImpl`. Passes-through everything
 * else to the wrapped base context (which is `null` here — fine because
 * [com.handy.app.SettingsStore] only invokes `getSharedPreferences`).
 *
 * Pattern mirrors Sprint 23 `testOptions { unitTests.isReturnDefaultValues = true }`
 * (the rest of the JVM unit tests in this repo) so the existing test
 * infrastructure picks up this class without further `app/build.gradle.kts`
 * tweaks.
 */
class StubContext(
    private val backingPrefs: SharedPreferences
) : ContextWrapper(null) {

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        backingPrefs

    /** Convenience constructor that auto-builds a fresh in-memory prefs as the backing. */
    constructor() : this(InMemorySharedPreferences())
}
