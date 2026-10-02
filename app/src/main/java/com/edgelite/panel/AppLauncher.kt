package com.edgelite.panel

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.widget.Toast

/**
 * Membuka aplikasi dalam jendela mengambang (ukuran lebar dan tinggi bisa diatur)
 * atau layar penuh. Jika jendela gagal, aplikasi tetap dibuka layar penuh.
 */
object AppLauncher {

    fun launch(ctx: Context, pkg: String, mode: LaunchMode, widthPct: Int, heightPct: Int) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(ctx, "Aplikasi tidak ditemukan")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            when (mode) {
                LaunchMode.FULL -> ctx.startActivity(intent)
                LaunchMode.WINDOW -> launchWindow(ctx, intent, widthPct, heightPct)
            }
        } catch (e: Exception) {
            toast(ctx, "Gagal membuka aplikasi")
        }
    }

    private fun launchWindow(ctx: Context, intent: Intent, widthPct: Int, heightPct: Int) {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            toast(ctx, "Mode jendela butuh freeform aktif di Opsi Pengembang")
        }
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val bw = (w * widthPct / 100).coerceIn(1, w)
        val bh = (h * heightPct / 100).coerceIn(1, h)
        val left = (w - bw) / 2
        val top = (h - bh) / 2

        val opts = ActivityOptions.makeBasic()
        opts.setLaunchBounds(Rect(left, top, left + bw, top + bh))
        try {
            // Windowing mode 5 = freeform. API tersembunyi, jadi dibungkus try/catch.
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(opts, 5)
        } catch (_: Throwable) {
        }
        try {
            ctx.startActivity(intent, opts.toBundle())
        } catch (e: SecurityException) {
            ctx.startActivity(intent)
        }
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
