package com.edgelite.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Slider berbentuk kapsul untuk volume dan kecerahan. Isi terang mengikuti jari, dan ikon berganti
 * warna di bagian yang tertutup isi supaya selalu terbaca. Bila [vertical], isi naik dari bawah dan
 * ikon ada di bawah; bila tidak, isi bergerak dari kiri dan ikon ada di kiri.
 * Slider mengambil seluruh sentuhan di atasnya, jadi geser horizontal tidak memindah halaman panel.
 */
class QuickSlider(context: Context, iconRes: Int, private val vertical: Boolean = false) : View(context) {

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
        val size: Int
        val left: Int
        val top: Int
        if (vertical) {
            // Isi naik dari bawah, ikon di bulatan bawah selebar kapsul.
            val edge = h * (1f - progress)
            canvas.drawRect(0f, edge, w, h, fill)
            size = (w * 0.5f).toInt()
            left = ((w - size) / 2f).toInt()
            top = (h - w / 2f - size / 2f).toInt()
            iconOnTrack.setBounds(left, top, left + size, top + size)
            iconOnFill.setBounds(left, top, left + size, top + size)
            canvas.save()
            canvas.clipRect(0f, 0f, w, edge)
            iconOnTrack.draw(canvas)
            canvas.restore()
            canvas.save()
            canvas.clipRect(0f, edge, w, h)
            iconOnFill.draw(canvas)
            canvas.restore()
        } else {
            val fillW = w * progress
            canvas.drawRect(0f, 0f, fillW, h, fill)
            size = (h * 0.5f).toInt()
            left = ((h - size) / 2f).toInt()
            top = ((h - size) / 2f).toInt()
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
        }

        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (onStart?.invoke() == false) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                move(e)
            }
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP -> {
                move(e)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun move(e: MotionEvent) {
        if (width <= 0 || height <= 0) return
        progress = if (vertical) 1f - e.y / height else e.x / width
        onChange?.invoke(progress)
    }
}
