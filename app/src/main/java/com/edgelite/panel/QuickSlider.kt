package com.edgelite.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Slider berbentuk kapsul untuk halaman akses cepat (volume, kecerahan). Isi terang mengikuti jari,
 * dan ikon di kiri berganti warna di bagian yang tertutup isi supaya selalu terbaca.
 * Slider mengambil seluruh sentuhan di atasnya, jadi geser horizontal tidak memindah halaman panel.
 */
class QuickSlider(context: Context, iconRes: Int) : View(context) {

    /** Nilai 0 sampai 1. */
    var progress = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    /** Dipanggil setiap nilai berubah karena jari. */
    var onChange: ((Float) -> Unit)? = null

    /** Dipanggil saat sentuhan mulai. Kembalikan false untuk membatalkan (misalnya izin belum ada). */
    var onStart: (() -> Boolean)? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.OUTLINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LIGHT }
    private val iconOnFill = context.getDrawable(iconRes)!!.mutate().apply { setTint(Ui.ON_LIGHT) }
    private val iconOnTrack = context.getDrawable(iconRes)!!.mutate().apply { setTint(Ui.TEXT) }
    private val clip = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect, track)
        val fillW = w * progress
        canvas.drawRect(0f, 0f, fillW, h, fill)

        val size = (h * 0.5f).toInt()
        val left = ((h - size) / 2f).toInt()
        val top = ((h - size) / 2f).toInt()
        iconOnTrack.setBounds(left, top, left + size, top + size)
        iconOnFill.setBounds(left, top, left + size, top + size)

        canvas.save()
        canvas.clipRect(fillW, 0f, w, h)
        iconOnTrack.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0f, 0f, fillW, h)
        iconOnFill.draw(canvas)
        canvas.restore()

        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (onStart?.invoke() == false) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP -> {
                move(e.x)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun move(x: Float) {
        if (width <= 0) return
        progress = x / width
        onChange?.invoke(progress)
    }
}
