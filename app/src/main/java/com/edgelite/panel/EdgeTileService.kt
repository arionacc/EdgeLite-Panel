package com.edgelite.panel

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build

class EdgeTileService : TileService() {

    override fun onStartListening() {
        render()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                startActivityAndCollapse(i)
            }
            return
        }
        if (Prefs(this).enabled) EdgeService.stop(this) else EdgeService.start(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (Prefs(this).enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
