package com.edgelite.panel

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Ukuran penanda halaman. Dipakai bersama oleh [PageDotsView] dan [PanelPreview] supaya pratinjau sama. */
object PageDotSpec {
    const val DOT_DP = 8f
    const val ACTIVE_DP = 18f
    const val GAP_DP = 4f

    /** Seberapa "aktif" titik ke-[i] pada posisi pager [pos]: 1 tepat di halamannya, 0 sejauh satu halaman atau lebih. */
    fun grow(pos: Float, i: Int): Float = max(0f, 1f - abs(pos - i))

    /** Lebar titik ke-[i] dalam dp: lingkaran [DOT_DP] yang melebar menjadi kapsul [ACTIVE_DP]. */
    fun widthDp(pos: Float, i: Int): Float = DOT_DP + (ACTIVE_DP - DOT_DP) * grow(pos, i)
}

/**
 * Penanda halaman yang berubah bentuk (morphing). Posisinya kontinu mengikuti pager, jadi saat halaman
 * digeser titik yang ditinggalkan menyusut menjadi lingkaran sementara titik tujuan melebar menjadi
 * kapsul, dengan warna ikut beralih dari redup ke terang.
 */
class PageDotsView(context: Context, private val count: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val argb = ArgbEvaluator()
    private var pos = 0f

    /** [p] adalah posisi pager: 0 di halaman pertama, 1 di halaman kedua, dan antara keduanya saat bergeser. */
    fun setPosition(p: Float) {
        val c = p.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
        if (c != pos) {
            pos = c
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val h = PageDotSpec.DOT_DP * d
        val gap = PageDotSpec.GAP_DP * d
        var total = (count - 1) * gap
        for (i in 0 until count) total += PageDotSpec.widthDp(pos, i) * d
        var x = (width - total) / 2f
        val top = (height - h) / 2f
        for (i in 0 until count) {
            val w = PageDotSpec.widthDp(pos, i) * d
            paint.color = argb.evaluate(PageDotSpec.grow(pos, i), Ui.MUTED, Ui.TEXT) as Int
            rect.set(x, top, x + w, top + h)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
            x += w + gap
        }
    }
}
