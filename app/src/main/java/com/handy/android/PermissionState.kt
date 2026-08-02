package com.handy.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Current state of every capability required before Handy can start its overlay. */
data class PermissionState(
    val microphone: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val accessibility: Boolean,
) {
    val ready: Boolean
        get() = microphone && notifications && overlay && accessibility
}

object PermissionChecker {
    /** Whether the RECORD_AUDIO permission is currently granted (required by both microphone foreground services). */
    fun hasMicrophonePermission(context: Context): Boolean =
        granted(context, Manifest.permission.RECORD_AUDIO)

    /**
     * Whether a caller may safely start (or re-deliver an intent to) a microphone foreground
     * service on Android 14+. Starting one requires RECORD_AUDIO unless the service is already
     * running — a running service only receives the re-delivered intent, so stop/control commands
     * must not be blocked when the permission was revoked mid-session.
     */
    fun canStartMicrophoneService(context: Context, serviceRunning: Boolean = false): Boolean =
        serviceRunning || hasMicrophonePermission(context)

    fun read(context: Context): PermissionState = PermissionState(
        microphone = granted(context, Manifest.permission.RECORD_AUDIO),
        notifications = notificationsGranted(context),
        overlay = Settings.canDrawOverlays(context),
        accessibility = accessibilityEnabled(context),
    )

    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS)

    private fun accessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context, AutoTypeAccessibilityService::class.java).flattenToString()
        return enabledServices.split(':').any { enabled ->
            ComponentName.unflattenFromString(enabled)?.flattenToString() == expected
        }
    }
}
