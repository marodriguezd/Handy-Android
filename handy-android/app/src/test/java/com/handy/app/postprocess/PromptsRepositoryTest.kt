package com.handy.app.postprocess

import android.content.Context
import android.content.SharedPreferences
import com.handy.app.util.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [PromptsRepository].
 *
 * Uses a temporary folder as the files directory and an in-memory
 * SharedPreferences implementation so the tests run without Android
 * framework or Robolectric.
 */
class PromptsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: PromptsRepository
    private lateinit var prefs: SharedPreferences

    private inner class FakeContext : ContextWrapper(null) {
        override fun getFilesDir(): java.io.File = tempFolder.root
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    @Before
    fun setUp() {
        prefs = InMemorySharedPreferences()
        repository = PromptsRepository(FakeContext())
    }

    @Test
    fun `getPrompts includes built-in prompt`() {
        val prompts = repository.getPrompts()
        assertEquals(1, prompts.size)
        assertEquals(Prompt.BUILTIN_ID, prompts[0].id)
    }

    @Test
    fun `savePrompt adds custom prompt and preserves built-in`() {
        repository.savePrompt(Prompt(id = "1", name = "Test", body = "Fix \${output}"))

        val prompts = repository.getPrompts()
        assertEquals(2, prompts.size)
        assertEquals(Prompt.BUILTIN_ID, prompts[0].id)
        assertEquals("1", prompts[1].id)
    }

    @Test
    fun `setActivePromptId returns active prompt`() {
        repository.setActivePromptId("1")
        assertEquals("1", repository.getActivePrompt().id)
    }

    @Test
    fun `active prompt falls back to built-in when id not found`() {
        repository.setActivePromptId("missing")
        assertEquals(Prompt.BUILTIN_ID, repository.getActivePrompt().id)
    }

    @Test
    fun `deletePrompt removes custom prompt but not built-in`() {
        repository.savePrompt(Prompt(id = "1", name = "Test", body = "body"))
        repository.deletePrompt("1")

        val prompts = repository.getPrompts()
        assertEquals(1, prompts.size)
        assertEquals(Prompt.BUILTIN_ID, prompts[0].id)
    }

    @Test
    fun `exportToJson and importFromJson round-trip custom prompts`() {
        repository.savePrompt(Prompt(id = "1", name = "One", body = "body1"))
        repository.savePrompt(Prompt(id = "2", name = "Two", body = "body2"))

        val json = repository.exportToJson()
        assertTrue(json.contains("One"))
        assertTrue(json.contains("Two"))
        assertFalse(json.contains(Prompt.BUILTIN_ID))

        // Clear and re-import
        repository.deletePrompt("1")
        repository.deletePrompt("2")

        val imported = repository.importFromJson(json)
        assertTrue(imported)

        val prompts = repository.getPrompts().filter { it.id != Prompt.BUILTIN_ID }
        assertEquals(2, prompts.size)
    }

    @Test
    fun `importFromJson returns false for invalid JSON`() {
        assertFalse(repository.importFromJson("not-json"))
    }

    @Test
    fun `importFromJson returns false when all prompts are invalid`() {
        val json = """[{"id":"x","name":"","body":""}]"""
        assertFalse(repository.importFromJson(json))
    }
}
