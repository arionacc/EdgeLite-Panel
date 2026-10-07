package com.edgelite.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Pratinjau panel dengan proporsi sebenarnya terhadap layar ponsel ini.
 * Semua ukuran dihitung dari angka yang sama dengan yang dipakai [EdgeAccessibilityService],
 * lalu diperkecil dengan satu skala, jadi yang terlihat di sini sama dengan saat dipakai.
 */
class PanelPreview(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private lateinit var prefs: Prefs
    private var orient = Orient.PORTRAIT

    private var icons: List<Drawable> = emptyList()
    private var iconKey: List<String> = emptyList()

    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BG }
    private val screenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Ui.OUTLINE
    }
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val cardFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Ui.OUTLINE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88E6E6E6.toInt() }
    private val labelBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED }
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.OUTLINE }
    private val gearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.LIGHT
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        isFakeBoldText = true
    }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }

    fun update(p: Prefs, o: Orient) {
        prefs = p
        orient = o
        val pm = context.packageManager
        val pkgs = p.pinned.filter { pm.getLaunchIntentForPackage(it) != null }.take(MAX_ICONS)
        if (pkgs != iconKey) {
            iconKey = pkgs
            icons = pkgs.map {
                runCatching { pm.getApplicationIcon(it) }.getOrElse { pm.defaultActivityIcon }
            }
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (320 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (!::prefs.isInitialized) return

        val dm = resources.displayMetrics
        val long = max(dm.widthPixels, dm.heightPixels)
        val short = min(dm.widthPixels, dm.heightPixels)
        val screenW = if (orient == Orient.PORTRAIT) short else long
        val screenH = if (orient == Orient.PORTRAIT) long else short

        // Satu skala untuk semuanya: piksel nyata ke piksel pratinjau.
        val pad = 8 * density
        val maxW = width - 2 * pad
        val maxH = height - 2 * pad
        val k = min(maxW / screenW, maxH / screenH)
        val sw = screenW * k
        val sh = screenH * k
        val left = (width - sw) / 2f
        val top = (height - sh) / 2f
        val screen = RectF(left, top, left + sw, top + sh)
        val sr = 16 * density

        canvas.drawRoundRect(screen, sr, sr, screenFill)

        // Nilai yang sama dengan openPanel() di layanan.
        val right = prefs.getSide(orient) == EdgeSide.RIGHT
        val widthDp = prefs.getPanelW(orient)
        val cardWpx = widthDp * density
        val cardHpx = (screenH * prefs.getPanelH(orient) / 100f)
            .coerceIn(min(160 * density, screenH.toFloat()), screenH.toFloat())
        val iconDp = min(prefs.iconSizeDp, widthDp - 24).coerceAtLeast(24)
        val showLabels = prefs.showLabels
        val r = prefs.cornerDp * density

        // Latar gelap seperti saat panel terbuka
        val clip = Path().apply { addRoundRect(screen, sr, sr, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(screen, scrim)

        // Kartu panel
        val cw = cardWpx * k
        val ch = cardHpx * k
        val cLeft = if (right) screen.right - cw else screen.left
        val cTop = screen.centerY() - ch / 2f
        val card = RectF(cLeft, cTop, cLeft + cw, cTop + ch)
        val rr = r * k
        val radii = if (right) {
            floatArrayOf(rr, rr, 0f, 0f, 0f, 0f, rr, rr)
        } else {
            floatArrayOf(0f, 0f, rr, rr, rr, rr, 0f, 0f)
        }
        val cardPath = Path().apply { addRoundRect(card, radii, Path.Direction.CW) }
        val alpha = (prefs.panelOpacity * 255 / 100).coerceIn(0, 255)
        cardFill.color = (alpha shl 24) or (Ui.PANEL and 0x00FFFFFF)
        canvas.drawPath(cardPath, cardFill)
        canvas.drawPath(cardPath, cardStroke)

        // Isi kartu: ukuran dp yang sama dengan appItem() di layanan
        canvas.save()
        canvas.clipPath(cardPath)
        // Dengan akses cepat, penanda halaman memakai 10 dp di atas tombol pengaturan (dikurangi 6 dp padding).
        val hasQuick = prefs.quickActions.isNotEmpty()
        val bottomReserve = if (hasQuick) 8 + 44 else 8 + 40
        val inner = RectF(
            card.left + 8 * density * k, card.top + 12 * density * k,
            card.right - 8 * density * k, card.bottom - bottomReserve * density * k
        )
        canvas.clipRect(inner)
        val iconPx = iconDp * density * k
        val itemH = ((6 + 6) * density + iconDp * density +
            if (showLabels) 15 * density else 0f) * k
        var y = inner.top
        val count = if (icons.isEmpty()) PLACEHOLDERS else icons.size
        for (i in 0 until count) {
            if (y > inner.bottom) break
            val ix = card.centerX() - iconPx / 2f
            val iy = y + 6 * density * k
            val d = icons.getOrNull(i)
            if (d != null) {
                d.setBounds(ix.toInt(), iy.toInt(), (ix + iconPx).toInt(), (iy + iconPx).toInt())
                d.draw(canvas)
            } else {
                canvas.drawRoundRect(
                    RectF(ix, iy, ix + iconPx, iy + iconPx),
                    iconPx * 0.28f, iconPx * 0.28f, placeholder
                )
            }
            if (showLabels) {
                val bw = min(iconPx * 1.4f, inner.width() * 0.8f)
                val by = iy + iconPx + 4 * density * k
                canvas.drawRoundRect(
                    RectF(card.centerX() - bw / 2f, by, card.centerX() + bw / 2f, by + 5 * density * k),
                    3 * density * k, 3 * density * k, labelBar
                )
            }
            y += itemH
        }
        canvas.restore()

        // Penanda halaman di bawah daftar, di atas tombol pengaturan, hanya bila ada akses cepat.
        // Bentuknya sama dengan PageDotsView saat berada di halaman pertama: titik aktif berupa kapsul,
        // sisanya lingkaran, dengan jumlah titik sesuai jumlah halaman (aplikasi, pintasan, slider).
        if (hasQuick) {
            val pages = 1 +
                (if (prefs.quickActions.any { it.kind != QuickKind.SLIDER }) 1 else 0) +
                (if (prefs.quickActions.any { it.kind == QuickKind.SLIDER }) 1 else 0)
            val dotH = max(2.4f * density, PageDotSpec.DOT_DP * density * k)
            val s = dotH / (PageDotSpec.DOT_DP * density)
            val gap = PageDotSpec.GAP_DP * density * s
            var total = (pages - 1) * gap
            for (i in 0 until pages) total += PageDotSpec.widthDp(0f, i) * density * s
            val dotY = card.bottom - (8 + 34 + 4) * density * k
            var dx = card.centerX() - total / 2f
            for (i in 0 until pages) {
                val dw = PageDotSpec.widthDp(0f, i) * density * s
                canvas.drawRoundRect(
                    RectF(dx, dotY - dotH / 2f, dx + dw, dotY + dotH / 2f),
                    dotH / 2f, dotH / 2f, if (i == 0) text else gearPaint
                )
                dx += dw + gap
            }
        }

        // Tombol pengaturan di dasar kartu
        canvas.drawCircle(
            card.centerX(), card.bottom - (8 + 20) * density * k,
            max(2f * density, 7f * density * k), gearPaint
        )
        canvas.restore()

        // Handle di tepi layar
        val handleH = prefs.getHandleHeight(orient) * density
        val handleTop = (screenH - handleH) * prefs.getHandleOffset(orient) / 100f
        val hw = max(2f * density, 4f * density * k)
        val hx = if (right) screen.right - 2 * density else screen.left + 2 * density
        val handle = RectF(
            if (right) hx - hw else hx,
            screen.top + (handleTop + handleH * 0.1f) * k,
            if (right) hx else hx + hw,
            screen.top + (handleTop + handleH * 0.9f) * k
        )
        canvas.drawRoundRect(handle, hw, hw, handlePaint)

        canvas.drawRoundRect(screen, sr, sr, screenStroke)

        // Ukuran nyata di area kosong layar
        val freeLeft = if (right) screen.left else card.right
        val freeRight = if (right) card.left else screen.right
        val cx = (freeLeft + freeRight) / 2f
        val cy = screen.centerY()
        canvas.drawText("$widthDp dp", cx, cy - 2 * density, text)
        canvas.drawText(
            "x ${(cardHpx / density).toInt()} dp", cx, cy + subText.textSize + 2 * density, subText
        )
    }

    private companion object {
        const val MAX_ICONS = 14
        const val PLACEHOLDERS = 5
    }
}
