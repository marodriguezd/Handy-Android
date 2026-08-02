package com.handy.android

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class HandyTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Allow a running service to be stopped even if RECORD_AUDIO was revoked mid-session.
        if (!PermissionChecker.canStartMicrophoneService(this, FloatingButtonService.isRunning)) {
            AppLog.record(this, "E", "HandyTile", "RECORD_AUDIO missing, ignoring tile tap")
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, FloatingButtonService::class.java).setAction(FloatingButtonService.ACTION_TOGGLE),
        )
        Handler(Looper.getMainLooper()).post { updateTile() }
    }

    private fun updateTile() {
        qsTile?.let {
            it.state = if (FloatingButtonService.isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.label = getString(R.string.quick_settings_tile_label)
            it.updateTile()
        }
    }
}
