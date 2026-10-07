package com.edgelite.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Pratinjau jendela mengambang terhadap layar ponsel ini untuk orientasi yang dipilih.
 * Ukurannya dihitung oleh [WindowBounds], yang juga dipakai saat aplikasi dibuka, jadi angka
 * piksel dan dp di sini sama dengan hasil sebenarnya. Garis putus-putus menunjukkan bentuk
 * yang didapat aplikasi yang hanya mendukung potret bila penyesuaian otomatis aktif.
 */
class WindowPreview(context: Context) : View(context) {

    private var wPct = 75
    private var hPct = 75
    private var orient = Orient.PORTRAIT
    private var fit = true

    private val density = resources.displayMetrics.density

    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.SURFACE }
    private val screenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Ui.OUTLINE
    }
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val windowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LIGHT }
    private val titleBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Ui.ON_LIGHT
        pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.ON_LIGHT
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        isFakeBoldText = true
    }
    private val subLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.ON_LIGHT
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }
    private val legend = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }

    fun update(widthPercent: Int, heightPercent: Int, o: Orient, fitToApp: Boolean) {
        wPct = widthPercent
        hPct = heightPercent
        orient = o
        fit = fitToApp
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (230 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val area = WindowBounds.areaFor(context, orient)
        val full = area.full
        val usable = area.usable

        val pad = 8 * density
        val legendRoom = if (fit) 16 * density else 0f
        val maxW = width - 2 * pad
        val maxH = height - 2 * pad - legendRoom
        val k = min(maxW / full.width(), maxH / full.height())
        val sw = full.width() * k
        val sh = full.height() * k
        val left = (width - sw) / 2f
        val top = pad + (maxH - sh) / 2f
        val screen = RectF(left, top, left + sw, top + sh)
        val r = 14 * density

        canvas.drawRoundRect(screen, r, r, screenFill)

        // Bar sistem yang tidak ikut dihitung sebagai ruang jendela
        val bars = Paint(barFill)
        if (usable.top > full.top) {
            canvas.drawRect(screen.left, screen.top, screen.right, screen.top + (usable.top - full.top) * k, bars)
        }
        if (usable.bottom < full.bottom) {
            canvas.drawRect(screen.left, screen.top + (usable.bottom - full.top) * k, screen.right, screen.bottom, bars)
        }
        canvas.drawRoundRect(screen, r, r, screenStroke)

        fun map(b: android.graphics.Rect) = RectF(
            screen.left + (b.left - full.left) * k, screen.top + (b.top - full.top) * k,
            screen.left + (b.right - full.left) * k, screen.top + (b.bottom - full.top) * k
        )

        val plain = WindowBounds.compute(area, wPct, hPct, AppShape(), false)
        val win = map(plain)
        val wr = 6 * density
        canvas.drawRoundRect(win, wr, wr, windowFill)
        canvas.drawRoundRect(
            RectF(win.left, win.top, win.right, win.top + min(14 * density, win.height())),
            wr, wr, titleBar
        )

        if (fit) {
            val portraitApp = WindowBounds.compute(
                area, wPct, hPct, AppShape(OrientLock.PORTRAIT), true
            )
            val differs = abs(portraitApp.width() - plain.width()) > 0.03f * plain.width() ||
                abs(portraitApp.height() - plain.height()) > 0.03f * plain.height()
            if (differs) {
                canvas.drawRoundRect(map(portraitApp), wr, wr, dashed)
            }
            canvas.drawText(
                context.getString(R.string.preview_portrait_app),
                width / 2f, height - 8 * density, legend
            )
        }

        val cy = win.centerY()
        canvas.drawText("${plain.width()} x ${plain.height()} px", win.centerX(), cy, label)
        canvas.drawText(
            "${area.toDp(plain.width())} x ${area.toDp(plain.height())} dp",
            win.centerX(), cy + subLabel.textSize + 2 * density, subLabel
        )
    }
}
