package com.handy.android

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F1 regression tests: microphone foreground services (FloatingButtonService / LiveSubtitleService)
 * must not be started without RECORD_AUDIO on Android 14+ (SecurityException), while a running
 * service must still receive re-delivered control intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F1PermissionGateTest {

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        SettingsManager.setAutoStartOnBoot(app, false)
        // Assumption: no test here instantiates FloatingButtonService, so the JVM-static
        // FloatingButtonService.isRunning stays false and the gate tests are order-independent.
    }

    private fun nextStartedService(): Intent? = shadowOf(app).getNextStartedService()

    // --- BootReceiver -------------------------------------------------------------------------

    @Test
    fun bootReceiver_skipsAutostartWhenRecordAudioDenied() {
        SettingsManager.setAutoStartOnBoot(app, true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(nextStartedService())
    }

    @Test
    fun bootReceiver_autostartsWhenRecordAudioGranted() {
        SettingsManager.setAutoStartOnBoot(app, true)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        val started = nextStartedService()
        assertNotNull(started)
        assertEquals(FloatingButtonService::class.java.name, started!!.component?.className)
    }

    @Test
    fun bootReceiver_respectsAutostartSetting() {
        // autoStartOnBoot defaults to false (set in setUp) → no service even with permission.
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(nextStartedService())
    }

    // --- RemoteControlReceiver ----------------------------------------------------------------

    @Test
    fun remoteControl_toggleIgnoredWhenRecordAudioDenied() {
        RemoteControlReceiver().onReceive(app, Intent(RemoteControlReceiver.ACTION_TOGGLE_RECORDING))
        assertNull(nextStartedService())
    }

    @Test
    fun remoteControl_toggleStartsServiceWhenRecordAudioGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        RemoteControlReceiver().onReceive(app, Intent(RemoteControlReceiver.ACTION_TOGGLE_RECORDING))
        val started = nextStartedService()
        assertNotNull(started)
        assertEquals(FloatingButtonService.ACTION_TOGGLE, started!!.action)
    }

    @Test
    fun remoteControl_unknownActionIgnored() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        RemoteControlReceiver().onReceive(app, Intent("com.handy.android.UNKNOWN_ACTION"))
        assertNull(nextStartedService())
    }

    // --- HandyTileService ---------------------------------------------------------------------

    @Test
    fun tile_ignoredWhenRecordAudioDeniedAndServiceNotRunning() {
        val tile = Robolectric.buildService(HandyTileService::class.java).create().get()
        tile.onClick()
        assertNull(nextStartedService())
    }

    @Test
    fun tile_startsServiceWhenRecordAudioGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val tile = Robolectric.buildService(HandyTileService::class.java).create().get()
        tile.onClick()
        assertNotNull(nextStartedService())
    }
}
