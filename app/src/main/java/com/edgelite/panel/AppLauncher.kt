package com.edgelite.panel

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Membuka aplikasi dalam tiga mode: layar penuh, split screen, atau jendela mengambang.
 * Setiap mode punya fallback supaya aplikasi tidak pernah gagal total.
 */
object AppLauncher {

    private val main = Handler(Looper.getMainLooper())

    fun launch(
        ctx: Context,
        pkg: String,
        mode: LaunchMode,
        windowPercent: Int,
        done: () -> Unit
    ) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(ctx, "Aplikasi tidak ditemukan")
            done()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            when (mode) {
                LaunchMode.FULL -> {
                    ctx.startActivity(intent)
                    done()
                }
                LaunchMode.SPLIT -> launchSplit(ctx, intent, done)
                LaunchMode.WINDOW -> {
                    launchWindow(ctx, intent, windowPercent)
                    done()
                }
            }
        } catch (e: Exception) {
            toast(ctx, "Gagal membuka aplikasi")
            done()
        }
    }

    private fun launchSplit(ctx: Context, intent: Intent, done: () -> Unit) {
        val adjacent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val acc = EdgeAccessibilityService.instance

        if (acc == null) {
            toast(ctx, "Aktifkan layanan aksesibilitas agar split screen lebih andal")
            ctx.startActivity(adjacent)
            done()
            return
        }

        if (acc.appWindowCount() >= 2) {
            // Sudah dalam split screen: cukup buka di sisi lain.
            ctx.startActivity(adjacent)
            done()
        } else {
            // Masuk split screen dulu, lalu buka aplikasi kedua di sisi sebelahnya.
            acc.toggleSplitScreen()
            main.postDelayed({
                try {
                    ctx.startActivity(adjacent)
                } catch (e: Exception) {
                    toast(ctx, "Gagal membuka aplikasi di split screen")
                }
                done()
            }, 700)
        }
    }

    private fun launchWindow(ctx: Context, intent: Intent, percent: Int) {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            toast(ctx, "Mode jendela butuh freeform aktif di Opsi Pengembang")
        }
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val bw = w * percent / 100
        val bh = h * percent / 100
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
