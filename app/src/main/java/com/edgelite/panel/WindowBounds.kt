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

/**
 * Batasan bentuk jendela yang dideklarasikan sebuah aplikasi.
 * [resizable] null berarti tidak diketahui. Aspek 0 berarti tidak ada batas.
 * Rasio aspek selalu sisi panjang dibagi sisi pendek (1 atau lebih).
 */
data class AppShape(
    val lock: OrientLock = OrientLock.NONE,
    val resizable: Boolean? = null,
    val minAspect: Float = 0f,
    val maxAspect: Float = 0f
)

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
     * Membaca batasan bentuk aplikasi dari manifes. Orientasi dibaca lewat API publik.
     * Bisa tidaknya diubah ukuran dan rasio aspek dibaca lewat refleksi, dan bila gagal
     * dianggap tidak diketahui sehingga ukuran dipakai apa adanya.
     */
    fun shapeOf(pm: PackageManager, intent: Intent): AppShape {
        val ai: ActivityInfo = runCatching {
            intent.resolveActivityInfo(pm, PackageManager.GET_META_DATA)
        }.getOrNull() ?: return AppShape()

        val lock = when (ai.screenOrientation) {
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

        // Nilai 0 pada resizeMode berarti tidak bisa diubah ukuran. Nilai lain dianggap bisa.
        val resizable: Boolean? = runCatching {
            ActivityInfo::class.java.getField("resizeMode").getInt(ai) != 0
        }.getOrNull()

        val maxAspect = aspect(ai, "getMaxAspectRatio", "maxAspectRatio")
            .takeIf { it >= 1f }
            ?: (ai.metaData ?: ai.applicationInfo?.metaData)
                ?.getFloat("android.max_aspect", 0f)?.takeIf { it >= 1f }
            ?: 0f
        val minAspect = aspect(ai, "getMinAspectRatio", "minAspectRatio").takeIf { it >= 1f } ?: 0f

        return AppShape(lock, resizable, minAspect, maxAspect)
    }

    private fun aspect(ai: ActivityInfo, method: String, field: String): Float {
        runCatching { return (ActivityInfo::class.java.getMethod(method).invoke(ai) as Float) }
        runCatching { return ActivityInfo::class.java.getField(field).getFloat(ai) }
        return 0f
    }

    /**
     * Hitung kotak jendela dalam piksel layar. Persen dihitung dari area yang bisa dipakai
     * (tanpa bar sistem). Jendela selalu berada di tengah area itu.
     *
     * Bila [fit] aktif, bentuknya disesuaikan dengan aplikasi supaya sistem tidak perlu
     * mengubah ukurannya lagi: aplikasi potret saja tetap potret, aplikasi landskap saja
     * tetap landskap, dan rasio aspek yang dideklarasikan aplikasi dihormati. Hasilnya
     * selalu muat di dalam kotak w x h yang diminta pengguna.
     */
    fun compute(area: DisplayArea, wPct: Int, hPct: Int, shape: AppShape, fit: Boolean): Rect {
        val u = area.usable
        val aw = u.width().toFloat()
        val ah = u.height().toFloat()

        var w = aw * wPct / 100f
        var h = ah * hPct / 100f
        if (fit) {
            val screenRatio = max(area.full.width(), area.full.height()).toFloat() /
                min(area.full.width(), area.full.height())
            val r = fitToApp(w, h, screenRatio, shape)
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

    private fun fitToApp(w: Float, h: Float, screenRatio: Float, s: AppShape): Pair<Float, Float> {
        val boxRatio = max(w, h) / min(w, h)

        fun clampRatio(r: Float): Float {
            var x = r
            if (s.maxAspect >= 1f) x = min(x, s.maxAspect)
            if (s.minAspect >= 1f) x = max(x, s.minAspect)
            return max(x, 1f)
        }

        // Persegi panjang berrasio tetap (sisi panjang per sisi pendek) terbesar yang muat di kotak w x h.
        fun fitRatio(ratio: Float, portrait: Boolean): Pair<Float, Float> =
            if (portrait) {
                val nw = min(w, h / ratio)
                nw to nw * ratio
            } else {
                val nh = min(h, w / ratio)
                nh * ratio to nh
            }

        return when (s.lock) {
            OrientLock.PORTRAIT, OrientLock.LANDSCAPE -> {
                val portrait = s.lock == OrientLock.PORTRAIT
                val boxMatches = (h >= w) == portrait
                // Aplikasi yang bisa diubah ukuran dan orientasinya cocok dengan kotak: pakai rasio kotak.
                // Selain itu pakai rasio layar, yaitu bentuk yang diharapkan sistem untuk aplikasi seperti ini.
                val ratio = if (s.resizable == true && boxMatches) clampRatio(boxRatio) else clampRatio(screenRatio)
                fitRatio(ratio, portrait)
            }
            OrientLock.NONE ->
                if (s.resizable == false) fitRatio(clampRatio(boxRatio), h >= w) else w to h
        }
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
