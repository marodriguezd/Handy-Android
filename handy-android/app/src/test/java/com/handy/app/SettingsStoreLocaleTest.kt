package com.handy.app

import com.handy.app.util.StubContext
import com.handy.app.util.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: **SharedPreferences ↔ StateFlow invariant** for
 * [SettingsStore.setAppLanguage].
 *
 * On-device in this session we confirmed via `cmd locale get-app-locales` +
 * `run-as cat shared_prefs/handy_settings.xml` that BOTH layers (the
 * `appLanguageFlow.value` and the on-disk `app_language` SharedPreferences
 * key) carry the same value across `force-stop` + `cold-launch` + `install
 * -r`. If a future refactor accidentally decouples them — e.g., the setter
 * updates only the flow but skips the prefs `editor.apply()` call, or writes
 * the prefs but doesn't wire the new value back into the flow — this test
 * fails immediately at JVM time, saving the on-device verification cost.
 *
 * Pure JVM. No Robolectric. The injected [StubContext] returns the same
 * [InMemorySharedPreferences] instance from `getSharedPreferences(...)` so
 * post-restart reads (a fresh SettingsStore wrapping the same in-memory
 * backing) exercise the exact `MutableStateFlow(prefs.getString(...))`
 * seed path that runs on the device at process launch.
 *
 * **Readback convention**: every assertion reads `.value` directly off the
 * StateFlow (NOT `.first()` or a `runTest` collect). `MutableStateFlow.value`
 * is updated synchronously inside the setter, so direct `.value` reads are
 * both deterministic and dispatcher-free; using `.first()` would require a
 * `Main` dispatcher which would throw `Module with the Main dispatcher is
 * missing` in pure JVM. Locked in by code-reviewer of `2c7141a` follow-up.
 */
class SettingsStoreLocaleTest {

    @Test
    fun `setAppLanguage en writes both flow and prefs atomically`() {
        val spy = InMemorySharedPreferences(initial = mapOf("app_language" to "auto"))
        val store = SettingsStore(StubContext(spy))

        // Initial read sees prior state on both layers.
        assertEquals("auto", spy.getString("app_language", null))
        assertEquals("auto", store.appLanguageFlow.value)

        // Mutate through the public setter.
        store.appLanguage = "en"

        // Both layers MUST reflect the new state. This is the core invariant:
        // a regression where the setter updates only the flow but skips the
        // prefs edit (or vice versa) is caught here before reaching users.
        assertEquals("en", store.appLanguageFlow.value)
        assertEquals("en", spy.getString("app_language", null))
    }

    @Test
    fun `setAppLanguage null removes the prefs key and resets the flow`() {
        val spy = InMemorySharedPreferences(initial = mapOf("app_language" to "es"))
        val store = SettingsStore(StubContext(spy))

        assertEquals("es", store.appLanguageFlow.value)
        assertTrue(spy.contains("app_language"))

        store.appLanguage = null

        // The on-device sanity probe specifically covered this case: when the
        // user toggles back to "Predeterminado del sistema" (System default),
        // the user-facing behavior is "follow Locale.getDefault()", which the
        // SettingsStore implements by writing `null` into the prefs. If the
        // setter instead wrote the literal string "null" or any other value,
        // the next launch would re-load a (probably bogus) appLanguage.
        assertNull(store.appLanguageFlow.value)
        assertFalse(spy.contains("app_language"))
        assertNull(spy.getString("app_language", null))
    }

    @Test
    fun `default flow reads the prefs initial seed`() {
        // Without an explicit set, MutableStateFlow(prefs.getString(...))
        // constructor must read the backing. Verifies the read-from-prefs
        // path on first process launch.
        val spy = InMemorySharedPreferences(initial = mapOf("app_language" to "fr"))
        val store = SettingsStore(StubContext(spy))

        assertEquals("fr", store.appLanguageFlow.value)
    }

    @Test
    fun `after force-stop-like restart StateFlow re-reads prefs`() {
        // Simulate a process restart (force-stop + cold-launch on device)
        // by constructing a fresh SettingsStore around the SAME in-memory
        // prefs as a previous instance.
        val spy = InMemorySharedPreferences()
        val initialStore = SettingsStore(StubContext(spy))
        initialStore.appLanguage = "en"

        val restartStore = SettingsStore(StubContext(spy))
        // The freshly-constructed SettingsStore reads from the same prefs and
        // seeds its MutableStateFlow accordingly.
        assertEquals("en", restartStore.appLanguageFlow.value)
        assertEquals("en", spy.getString("app_language", null))
    }

    @Test
    fun `setAppLanguage twice in a row leaves both layers at the second value`() {
        val spy = InMemorySharedPreferences()
        val store = SettingsStore(StubContext(spy))

        store.appLanguage = "en"
        assertEquals("en", store.appLanguageFlow.value)
        assertEquals("en", spy.getString("app_language", null))

        store.appLanguage = "es"
        assertEquals("es", store.appLanguageFlow.value)
        assertEquals("es", spy.getString("app_language", null))
    }
}
