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

    fun launch(ctx: Context, pkg: String, mode: LaunchMode, widthPct: Int, heightPct: Int, fit: Boolean = true, shape: WinShape = WinShape.AUTO) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(ctx, ctx.getString(R.string.toast_app_not_found))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            when (mode) {
                LaunchMode.FULL -> ctx.startActivity(intent)
                LaunchMode.WINDOW -> launchWindow(ctx, intent, widthPct, heightPct, fit, shape)
            }
        } catch (e: Exception) {
            toast(ctx, ctx.getString(R.string.toast_launch_failed))
        }
    }

    private fun launchWindow(ctx: Context, intent: Intent, widthPct: Int, heightPct: Int, fit: Boolean, choice: WinShape) {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            toast(ctx, ctx.getString(R.string.toast_freeform_needed))
        }
        // Ukuran layar, bar sistem, dan densitas dibaca saat ini juga, jadi mengikuti resolusi
        // dan ukuran tampilan yang sedang dipakai. Bentuk jendela disesuaikan dengan aplikasinya.
        val area = WindowBounds.displayArea(ctx)
        // Pilihan pengguna per aplikasi menang atas deteksi dan atas pengaturan umum.
        val useFit: Boolean
        val shape: AppShape
        when (choice) {
            WinShape.PORTRAIT -> { useFit = true; shape = AppShape(OrientLock.PORTRAIT, resizable = false) }
            WinShape.LANDSCAPE -> { useFit = true; shape = AppShape(OrientLock.LANDSCAPE, resizable = false) }
            WinShape.FREE -> { useFit = false; shape = AppShape() }
            WinShape.AUTO -> {
                useFit = fit
                shape = if (fit) WindowBounds.shapeOf(ctx.packageManager, intent) else AppShape()
            }
        }
        val bounds: Rect = WindowBounds.compute(area, widthPct, heightPct, shape, useFit)

        val opts = ActivityOptions.makeBasic()
        opts.setLaunchBounds(bounds)
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
