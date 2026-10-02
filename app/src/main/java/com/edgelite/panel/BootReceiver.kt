package com.edgelite.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs(context).enabled &&
            Settings.canDrawOverlays(context)
        ) {
            runCatching { EdgeService.start(context) }
        }
    }
}
