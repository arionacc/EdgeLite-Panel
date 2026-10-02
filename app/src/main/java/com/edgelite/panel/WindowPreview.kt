package com.edgelite.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Pratinjau proporsi jendela mengambang terhadap layar ponsel. */
class WindowPreview(context: Context) : View(context) {

    private var wPct = 75
    private var hPct = 75

    private val density = resources.displayMetrics.density

    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.SURFACE }
    private val screenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Ui.OUTLINE
    }
    private val windowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LIGHT }
    private val titleBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.ON_LIGHT
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        isFakeBoldText = true
    }

    fun update(widthPercent: Int, heightPercent: Int) {
        wPct = widthPercent
        hPct = heightPercent
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (190 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val dm = resources.displayMetrics
        val aspect = dm.heightPixels.toFloat() / dm.widthPixels.toFloat()
        val pad = 6 * density
        val maxW = width - 2 * pad
        val maxH = height - 2 * pad

        var sh = maxH
        var sw = sh / aspect
        if (sw > maxW) {
            sw = maxW
            sh = sw * aspect
        }
        val left = (width - sw) / 2f
        val top = (height - sh) / 2f
        val screen = RectF(left, top, left + sw, top + sh)
        val r = 14 * density
        canvas.drawRoundRect(screen, r, r, screenFill)
        canvas.drawRoundRect(screen, r, r, screenStroke)

        val ww = sw * wPct / 100f
        val wh = sh * hPct / 100f
        val win = RectF(
            screen.centerX() - ww / 2f, screen.centerY() - wh / 2f,
            screen.centerX() + ww / 2f, screen.centerY() + wh / 2f
        )
        val wr = 6 * density
        canvas.drawRoundRect(win, wr, wr, windowFill)
        canvas.drawRoundRect(
            RectF(win.left, win.top, win.right, win.top + minOf(14 * density, wh)),
            wr, wr, titleBar
        )

        val pxW = dm.widthPixels * wPct / 100
        val pxH = dm.heightPixels * hPct / 100
        canvas.drawText("$pxW x $pxH px", win.centerX(), win.centerY() + label.textSize / 3f, label)
    }
}
