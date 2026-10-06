package com.edgelite.panel

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Pager horizontal sederhana untuk halaman panel. Geser ke samping untuk berpindah halaman,
 * sedangkan geser vertikal tetap diteruskan ke daftar di dalam halaman. Ukuran pager tidak
 * bergantung pada jumlah halaman, jadi ukuran panel tidak berubah.
 */
class PanelPager(context: Context) : FrameLayout(context) {

    /** Dipanggil setiap halaman aktif berganti. */
    var onPageChanged: ((Int) -> Unit)? = null

    var page = 0
        private set

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val flingVelocity = config.scaledMinimumFlingVelocity * 6

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var ignoring = false
    private var dragX = 0f
    private var tracker: VelocityTracker? = null
    private var settle: ValueAnimator? = null

    private companion object {
        /** Lama animasi pindah halaman (ms). Selama animasi, sentuhan baru ditelan. */
        const val SETTLE_MS = 320L
    }

    fun addPage(v: View) {
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        position()
    }

    private fun position() {
        val w = width.toFloat()
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val offset = (i - page) * w + dragX
            v.translationX = offset
            // Halaman yang bergeser menjauh dari tengah memudar dan sedikit mengecil, jadi perpindahan
            // terasa lebih lembut dan halaman tujuan muncul bertahap.
            val frac = if (w > 0f) (abs(offset) / w).coerceAtMost(1f) else 0f
            v.alpha = 1f - 0.6f * frac
            val sc = 1f - 0.05f * frac
            v.scaleX = sc
            v.scaleY = sc
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Selama animasi pindah halaman, sentuhan baru ditelan supaya tidak salah mengenai
        // ikon atau slider yang sedang bergeser.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoring = settle?.isRunning == true
        }
        if (ignoring) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                // Mulai menggeser halaman hanya bila gerakannya jelas lebih horizontal daripada vertikal.
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.2f && childCount > 1) {
                    dragging = true
                    settle?.cancel()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) recycle()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ignoring) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                ignoring = false
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                begin(ev)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dx = ev.x - downX
                if (!dragging && abs(dx) > slop && abs(dx) > abs(ev.y - downY) * 1.2f && childCount > 1) {
                    dragging = true
                    settle?.cancel()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val atStart = page == 0 && dx > 0
                    val atEnd = page == childCount - 1 && dx < 0
                    dragX = if (atStart || atEnd) dx / 3f else dx
                    position()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    tracker?.addMovement(ev)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    val w = width.toFloat()
                    var target = page
                    if ((dragX < -w / 4f || vx < -flingVelocity) && page < childCount - 1) target = page + 1
                    if ((dragX > w / 4f || vx > flingVelocity) && page > 0) target = page - 1
                    settleTo(target)
                }
                dragging = false
                recycle()
            }
        }
        return true
    }

    private fun begin(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        dragging = false
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    private fun recycle() {
        tracker?.recycle()
        tracker = null
    }

    /** Pindah ke halaman [target] tanpa kedipan: offset geser dipertahankan lalu dianimasikan ke nol. */
    private fun settleTo(target: Int) {
        val w = width.toFloat()
        dragX += (page - target) * w
        val changed = target != page
        page = target
        position()
        settle?.cancel()
        settle = ValueAnimator.ofFloat(dragX, 0f).apply {
            duration = SETTLE_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                dragX = it.animatedValue as Float
                position()
            }
            start()
        }
        if (changed) onPageChanged?.invoke(page)
    }

    override fun onDetachedFromWindow() {
        settle?.cancel()
        recycle()
        super.onDetachedFromWindow()
    }
}
