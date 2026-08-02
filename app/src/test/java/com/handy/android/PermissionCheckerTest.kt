package com.handy.android

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionCheckerTest {

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun denyMicrophoneByDefault() {
        // Robolectric grants every runtime permission by default; establish the denied baseline.
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun hasMicrophonePermission_isFalseWhenRecordAudioDenied() {
        assertFalse(PermissionChecker.hasMicrophonePermission(app))
    }

    @Test
    fun hasMicrophonePermission_isTrueWhenRecordAudioGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(PermissionChecker.hasMicrophonePermission(app))
    }

    @Test
    fun canStartMicrophoneService_allowsRunningServiceWithoutPermission() {
        // A running service only receives the re-delivered intent, so control commands must not
        // be blocked even when RECORD_AUDIO was revoked mid-session.
        assertTrue(PermissionChecker.canStartMicrophoneService(app, serviceRunning = true))
    }

    @Test
    fun canStartMicrophoneService_requiresPermissionWhenServiceNotRunning() {
        assertFalse(PermissionChecker.canStartMicrophoneService(app))

        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(PermissionChecker.canStartMicrophoneService(app))
    }
}
