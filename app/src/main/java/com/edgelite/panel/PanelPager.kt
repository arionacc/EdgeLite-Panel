package com.edgelite.panel

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Pager horizontal sederhana untuk halaman panel. Geser ke samping untuk berpindah halaman,
 * sedangkan geser vertikal tetap diteruskan ke daftar di dalam halaman. Ukuran pager tidak
 * bergantung pada jumlah halaman, jadi ukuran panel tidak berubah.
 *
 * Rasanya meniru home screen: halaman mengikuti jari tanpa lompatan, di ujung ada tarikan karet,
 * dan saat jari dilepas halaman meluncur ke tujuannya dengan pegas yang melanjutkan kecepatan
 * lemparan jari. Pegas digerakkan per frame dengan waktu frame, jadi tidak bergantung pada
 * ValueAnimator dan pengaturan skala animasi sistem.
 */
class PanelPager(context: Context) : FrameLayout(context) {

    /** Dipanggil setiap halaman aktif berganti. */
    var onPageChanged: ((Int) -> Unit)? = null

    var page = 0
        private set

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val flingVelocity = config.scaledMinimumFlingVelocity * 6
    private val maxVelocity = config.scaledMaximumFlingVelocity.toFloat()

    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var dragging = false
    private var ignoring = false
    private var dragX = 0f
    private var tracker: VelocityTracker? = null

    // Pegas menuju dragX = 0 (halaman aktif di tengah).
    private var settling = false
    private var velocity = 0f
    private var lastFrame = 0L
    private val stepper = object : Runnable {
        override fun run() {
            if (!settling) return
            val now = AnimationUtils.currentAnimationTimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceIn(0.001f, MAX_DT)
            lastFrame = now
            val before = dragX
            val h = dt / 2f
            repeat(2) {
                velocity += (-STIFFNESS * dragX - DAMPING * velocity) * h
                dragX += velocity * h
            }
            // Berhenti tepat di tengah: tidak dibiarkan melewati nol supaya tidak ada celah di tepi.
            val crossed = (before > 0f && dragX < 0f) || (before < 0f && dragX > 0f)
            if (crossed || (abs(dragX) < 0.5f && abs(velocity) < 8f)) {
                dragX = 0f
                velocity = 0f
                settling = false
                position()
                return
            }
            position()
            postOnAnimation(this)
        }
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
            // Halaman yang menjauh dari tengah memudar dan sedikit mengecil, jadi halaman tujuan
            // muncul bertahap.
            val frac = if (w > 0f) (abs(offset) / w).coerceAtMost(1f) else 0f
            v.alpha = 1f - 0.5f * frac
            val sc = 1f - 0.04f * frac
            v.scaleX = sc
            v.scaleY = sc
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Selama halaman masih meluncur, sentuhan baru ditelan supaya tidak salah mengenai
        // ikon atau slider yang sedang bergeser.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoring = settling
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
                    startDrag(ev)
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
                    startDrag(ev)
                }
                if (dragging) {
                    val d = ev.x - startX
                    val w = width.toFloat()
                    val atStart = page == 0 && d > 0
                    val atEnd = page == childCount - 1 && d < 0
                    dragX = if (atStart || atEnd) rubber(d) else d.coerceIn(-w, w)
                    position()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    tracker?.addMovement(ev)
                    tracker?.computeCurrentVelocity(1000, maxVelocity)
                    val vx = if (ev.actionMasked == MotionEvent.ACTION_UP) tracker?.xVelocity ?: 0f else 0f
                    val w = width.toFloat()
                    var target = page
                    if ((dragX < -w / 4f || vx < -flingVelocity) && page < childCount - 1) target = page + 1
                    if ((dragX > w / 4f || vx > flingVelocity) && page > 0) target = page - 1
                    settleTo(target, vx)
                }
                dragging = false
                recycle()
            }
        }
        return true
    }

    /** Tarikan karet di ujung: makin jauh ditarik makin berat, dan tidak pernah melewati batas. */
    private fun rubber(d: Float): Float {
        val dim = width * 0.35f
        val r = (1f - 1f / (abs(d) * 0.55f / dim + 1f)) * dim
        return if (d < 0f) -r else r
    }

    private fun begin(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        dragging = false
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    /** Mulai menggeser halaman. Titik awal diambil di sini supaya halaman tidak melompat sejauh slop. */
    private fun startDrag(ev: MotionEvent) {
        dragging = true
        startX = ev.x
        stopSettle()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun recycle() {
        tracker?.recycle()
        tracker = null
    }

    private fun stopSettle() {
        settling = false
        velocity = 0f
        removeCallbacks(stepper)
    }

    /**
     * Pindah ke halaman [target] tanpa kedipan. Posisi halaman tujuan dipertahankan persis di tempat
     * ia terlihat sekarang, lalu pegas menariknya ke tengah dengan kecepatan awal [vx] dari jari.
     */
    private fun settleTo(target: Int, vx: Float) {
        val w = width.toFloat()
        dragX += (target - page) * w
        val changed = target != page
        page = target
        velocity = vx.coerceIn(-maxVelocity, maxVelocity)
        position()
        lastFrame = AnimationUtils.currentAnimationTimeMillis()
        if (!settling) {
            settling = true
            postOnAnimation(stepper)
        }
        if (changed) onPageChanged?.invoke(page)
    }

    override fun onDetachedFromWindow() {
        stopSettle()
        recycle()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Kekakuan dan redaman pegas (hampir kritis, sekitar 0,4 detik sampai berhenti). */
        const val STIFFNESS = 260f
        const val DAMPING = 30f

        /** Batas langkah waktu per frame (detik), supaya frame yang tertahan tidak melempar halaman. */
        const val MAX_DT = 1f / 30f
    }
}
