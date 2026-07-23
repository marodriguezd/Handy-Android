package com.handy.app.injection

import com.handy.app.SettingsStore
import com.handy.app.util.StubContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InjectorRouter].
 *
 * Coverage:
 * - IME injector is preferred when available
 * - Clipboard injector is used when IME is unavailable
 * - Fallback to clipboard when selected strategy fails
 */
class InjectorRouterTest {

    private class FakeInjector(
        private val available: Boolean,
        private val succeed: Boolean = true,
        val name: String = "Fake",
    ) : InjectorStrategy {
        override val displayName: String get() = name
        var injectedText: String? = null
        override fun isAvailable(): Boolean = available
        override suspend fun inject(text: String): Result<Unit> {
            injectedText = text
            return if (succeed) Result.success(Unit) else Result.failure(Exception("fail"))
        }
    }

    private fun settingsStore() = SettingsStore(StubContext())

    @Test
    fun `prefers IME when available`(): Unit = runBlocking {
        val ime = FakeInjector(available = true, name = "IME")
        val shizuku = FakeInjector(available = true, name = "Shizuku")
        val clipboard = FakeInjector(available = true, name = "Clipboard")
        val router = InjectorRouter(shizuku, clipboard, settingsStore())
        router.setImeInjector(ime)

        router.inject("hello")

        assertEquals("hello", ime.injectedText)
        assertEquals(null, clipboard.injectedText)
    }

    @Test
    fun `falls back to clipboard when IME unavailable`(): Unit = runBlocking {
        val ime = FakeInjector(available = false, name = "IME")
        val shizuku = FakeInjector(available = true, name = "Shizuku")
        val clipboard = FakeInjector(available = true, name = "Clipboard")
        val router = InjectorRouter(shizuku, clipboard, settingsStore())
        router.setImeInjector(ime)

        router.inject("hello")

        assertEquals(null, ime.injectedText)
        assertEquals("hello", clipboard.injectedText)
    }

    @Test
    fun `falls back to clipboard when IME injection fails`(): Unit = runBlocking {
        val ime = FakeInjector(available = true, succeed = false, name = "IME")
        val shizuku = FakeInjector(available = true, name = "Shizuku")
        val clipboard = FakeInjector(available = true, name = "Clipboard")
        val router = InjectorRouter(shizuku, clipboard, settingsStore())
        router.setImeInjector(ime)

        val result = router.inject("hello")

        assertTrue(result.isSuccess)
        assertEquals("hello", ime.injectedText)
        assertEquals("hello", clipboard.injectedText)
    }
}
