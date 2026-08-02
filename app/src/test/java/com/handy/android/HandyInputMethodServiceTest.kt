package com.handy.android

import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for the Material 3 Compose IME input view.
 *
 * The critical regression this guards: an InputMethodService window is not attached to an
 * Activity, so Compose 1.11 throws ("Composed into the View which doesn't propagate
 * ViewTreeLifecycleOwner!") unless the service provides the ViewTree owners itself. If a
 * future refactor drops [HandyInputMethodService.onCreateInputView]'s owner wiring, these
 * assertions fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HandyInputMethodServiceTest {

    @Test
    fun `onCreateInputView returns a Compose input view with ViewTree owners wired`() {
        val service = Robolectric.buildService(HandyInputMethodService::class.java).create().get()
        try {
            val view = service.onCreateInputView()
            assertNotNull("onCreateInputView must return an input view", view)
            assertTrue("IME input view should be backed by Compose", view is ComposeView)

            // Compose requires these in windows without an Activity (IME, dialogs, overlays).
            assertNotNull("ViewTreeLifecycleOwner must be provided for the IME window", ViewTreeOwnerBridge.lifecycleOwner(view))
            assertNotNull("ViewTreeSavedStateRegistryOwner must be provided for the IME window", ViewTreeOwnerBridge.savedStateRegistryOwner(view))

            // A second call must not crash and must keep producing a usable view.
            assertNotNull(service.onCreateInputView())
        } finally {
            service.onDestroy()
        }
    }
}
