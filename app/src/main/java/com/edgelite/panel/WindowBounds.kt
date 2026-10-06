package com.edgelite.panel

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class OrientLock { NONE, PORTRAIT, LANDSCAPE }

/** Orientasi yang dikunci sebuah aplikasi. Sistem menukar lebar dan tinggi jendela bila tidak cocok. */
data class AppShape(val lock: OrientLock = OrientLock.NONE)

/** Area layar dalam piksel. [usable] adalah layar tanpa bar status, bar navigasi, dan takik. */
class DisplayArea(val full: Rect, val usable: Rect, val densityDpi: Int) {
    fun toDp(px: Int): Int = (px * 160f / densityDpi).roundToInt()
}

/**
 * Satu sumber hitungan ukuran jendela untuk peluncur dan pratinjau, jadi angka di pratinjau
 * sama dengan yang dipakai saat aplikasi dibuka.
 */
object WindowBounds {

    /** Sisi terpendek jendela. Sama dengan batas minimum jendela mengambang bawaan Android. */
    private const val MIN_SIDE_DP = 220

    /** Ukuran layar dan densitas saat ini, dibaca setiap kali agar ikut berubah bila resolusi atau ukuran tampilan diganti. */
    fun displayArea(ctx: Context): DisplayArea {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = ctx.resources.configuration.densityDpi
            .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT

        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.maximumWindowMetrics
            val full = Rect(m.bounds)
            val usable = Rect(full)
            runCatching {
                m.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            }.getOrNull()?.let {
                usable.set(full.left + it.left, full.top + it.top, full.right - it.right, full.bottom - it.bottom)
            }
            // Jaga-jaga bila inset tidak masuk akal: pakai layar penuh.
            if (usable.width() < full.width() / 2 || usable.height() < full.height() / 2) usable.set(full)
            return DisplayArea(full, usable, density)
        }

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val full = Rect(0, 0, dm.widthPixels, dm.heightPixels)
        return DisplayArea(full, Rect(full), density)
    }

    /**
     * Area layar untuk orientasi [o]. Bila berbeda dari orientasi sekarang, layar diputar
     * dan inset atas dan bawah dipertahankan sebagai perkiraan.
     */
    fun areaFor(ctx: Context, o: Orient): DisplayArea {
        val cur = displayArea(ctx)
        if (orientOf(cur.full.width(), cur.full.height()) == o) return cur
        val w = cur.full.height()
        val h = cur.full.width()
        val top = cur.usable.top - cur.full.top
        val bottom = cur.full.bottom - cur.usable.bottom
        return DisplayArea(Rect(0, 0, w, h), Rect(0, top, w, h - bottom), cur.densityDpi)
    }

    /**
     * Membaca orientasi yang dikunci aplikasi dari manifes, lewat API publik. Activity peluncur
     * diperiksa lebih dulu. Bila tidak mengunci, seluruh activity yang dideklarasikan diperiksa:
     * aplikasi yang sepertiga atau lebih activity-nya terkunci ke satu orientasi, tanpa satu pun
     * yang terkunci ke orientasi lain, dianggap hanya mendukung orientasi itu. Aplikasi yang
     * mengunci orientasi lewat kode tidak terbaca di sini, untuk itu ada pilihan per aplikasi.
     */
    fun shapeOf(pm: PackageManager, intent: Intent): AppShape {
        val ai: ActivityInfo = runCatching {
            intent.resolveActivityInfo(pm, 0)
        }.getOrNull() ?: return AppShape()

        val launcher = lockOf(ai.screenOrientation)
        if (launcher != OrientLock.NONE) return AppShape(launcher)

        val activities = runCatching {
            pm.getPackageInfo(ai.packageName, PackageManager.GET_ACTIVITIES).activities
        }.getOrNull()
        if (activities.isNullOrEmpty()) return AppShape()

        var portrait = 0
        var landscape = 0
        for (a in activities) {
            when (lockOf(a.screenOrientation)) {
                OrientLock.PORTRAIT -> portrait++
                OrientLock.LANDSCAPE -> landscape++
                OrientLock.NONE -> {}
            }
        }
        return AppShape(
            when {
                portrait * 3 >= activities.size && landscape == 0 -> OrientLock.PORTRAIT
                landscape * 3 >= activities.size && portrait == 0 -> OrientLock.LANDSCAPE
                else -> OrientLock.NONE
            }
        )
    }

    private fun lockOf(orientation: Int): OrientLock = when (orientation) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> OrientLock.PORTRAIT

        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> OrientLock.LANDSCAPE

        else -> OrientLock.NONE
    }

    /**
     * Hitung kotak jendela dalam piksel layar. Persen dihitung dari area yang bisa dipakai
     * (tanpa bar sistem). Jendela selalu berada di tengah area itu.
     *
     * Bila [fit] aktif dan aplikasi mengunci orientasi, kotaknya disesuaikan supaya sistem tidak
     * perlu menukar lebar dan tinggi (yang membuat jendela melewati ukuran yang diatur). Hasilnya
     * selalu muat di dalam kotak w x h yang diminta pengguna.
     */
    fun compute(area: DisplayArea, wPct: Int, hPct: Int, shape: AppShape, fit: Boolean): Rect {
        val u = area.usable
        val aw = u.width().toFloat()
        val ah = u.height().toFloat()

        var w = aw * wPct / 100f
        var h = ah * hPct / 100f
        if (fit) {
            val r = fitToApp(w, h, shape.lock)
            w = r.first
            h = r.second
        }
        val limited = enforceLimits(w, h, aw, ah, MIN_SIDE_DP * area.densityDpi / 160f)

        val bw = limited.first.roundToInt().coerceIn(1, u.width())
        val bh = limited.second.roundToInt().coerceIn(1, u.height())
        val left = u.left + (u.width() - bw) / 2
        val top = u.top + (u.height() - bh) / 2
        return Rect(left, top, left + bw, top + bh)
    }

    /**
     * Sistem hanya mengharuskan arah jendela cocok dengan aplikasi (potret: tinggi minimal sama
     * dengan lebar). Ukurannya bebas, aplikasi menyesuaikan isinya sendiri. Bila kotak pengguna
     * sudah searah, dipakai apa adanya. Bila tidak, dipakai persegi seukuran sisi terpendek kotak,
     * yaitu bentuk searah terbesar yang masih muat di dalam kotak.
     */
    private fun fitToApp(w: Float, h: Float, lock: OrientLock): Pair<Float, Float> {
        if (lock == OrientLock.NONE) return w to h
        val matches = if (lock == OrientLock.PORTRAIT) h >= w else w > h
        if (matches) return w to h
        val side = min(w, h)
        return side to side
    }

    /**
     * Batasi ke area yang bisa dipakai, lalu naikkan ke ukuran minimum sistem hanya bila hasilnya
     * masih muat. Bila tidak muat, ukuran pengguna dipertahankan dan sistem yang menyesuaikan.
     * Rasio tidak pernah diubah.
     */
    private fun enforceLimits(w: Float, h: Float, aw: Float, ah: Float, minPx: Float): Pair<Float, Float> {
        var nw = w
        var nh = h
        if (nw > aw || nh > ah) {
            val k = min(aw / nw, ah / nh)
            nw *= k
            nh *= k
        }
        val shortSide = min(nw, nh)
        if (shortSide in 1f..minPx) {
            val k = minPx / shortSide
            if (k <= min(aw / nw, ah / nh)) {
                nw *= k
                nh *= k
            }
        }
        return nw to nh
    }
}
