package com.edgelite.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Menyalakan panel lagi setelah ponsel restart (atau setelah aplikasi diperbarui)
 * bila panel aktif sebelumnya, Mulai otomatis menyala, dan izin overlay masih ada.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        if (!relevant) return

        val prefs = Prefs(context)
        if (prefs.enabled && prefs.autostart && Settings.canDrawOverlays(context)) {
            runCatching { EdgeService.start(context) }
        }
    }
}
