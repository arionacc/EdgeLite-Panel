package com.edgelite.panel

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.min

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

    /** Dipanggil setiap frame dengan posisi pager yang kontinu: 0 di halaman pertama, 1 di kedua, dan seterusnya. */
    var onScroll: ((Float) -> Unit)? = null

    var page = 0
        private set

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val flingVelocity = config.scaledMinimumFlingVelocity * 6
    private val maxVelocity = config.scaledMaximumFlingVelocity.toFloat()

    /** Kecepatan awal pegas dibatasi supaya lemparan sangat cepat tidak melempar halaman terlalu keras. */
    private val velocityCap = minOf(maxVelocity, 5000f)

    /** Selama halaman masih sejauh ini dari tengah, sentuhan baru ditelan. Lewat itu, sentuhan diterima. */
    private val ignoreDistance = 6f * resources.displayMetrics.density

    /** Jarak luncur item saat halaman berpindah (px). */
    private val itemShift = ITEM_SHIFT_DP * resources.displayMetrics.density

    /** Halaman yang item-itemnya sedang tidak netral, supaya dikembalikan ke keadaan semula sekali saja. */
    private val staggered = HashSet<View>()

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
    private var damping = DAMPING_PAGE
    private var bounce = false
    private var lastFrame = 0L
    private val stepper = object : Runnable {
        override fun run() {
            if (!settling) return
            val now = AnimationUtils.currentAnimationTimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceIn(0.001f, MAX_DT)
            lastFrame = now
            val before = dragX
            val h = dt / 4f
            repeat(4) {
                velocity += (-STIFFNESS * dragX - damping * velocity) * h
                dragX += velocity * h
            }
            // Pindah halaman berhenti tepat di tengah dan tidak melewati nol. Kembali ke halaman yang sama
            // (misalnya setelah tarikan karet di ujung) boleh memantul sedikit.
            val crossed = !bounce && ((before > 0f && dragX < 0f) || (before < 0f && dragX > 0f))
            if (crossed || (abs(dragX) < 0.5f && abs(velocity) < 8f)) {
                dragX = 0f
                velocity = 0f
                settling = false
                position()
                setPageLayers(false)
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
        if (w > 0f) onScroll?.invoke(page - dragX / w)
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val offset = (i - page) * w + dragX
            v.translationX = offset
            // Paralaks: isi halaman tertinggal sedikit dari halamannya dan terpotong di tepi halaman,
            // jadi isi halaman tujuan seolah muncul dari balik tepi, bukan ikut meluncur kaku.
            val lag = -offset * PARALLAX
            if (v is ViewGroup) {
                for (j in 0 until v.childCount) v.getChildAt(j).translationX = lag
            }
            // Item di halaman yang masuk atau keluar muncul berurutan mengikuti gerak halaman. Tidak
            // berlaku di ujung (tarikan karet), karena tidak ada halaman lain yang masuk.
            val hasNeighbor = (offset > 0f && i > 0) || (offset < 0f && i < childCount - 1)
            if (abs(offset) > 0.5f && hasNeighbor) {
                val f = 1f - (abs(offset) / w).coerceAtMost(1f)
                applyItems(v, f, if (offset > 0f) 1f else -1f, lag)
                staggered.add(v)
            } else if (staggered.remove(v)) {
                applyItems(v, 1f, 0f, lag)
            }
            // Halaman yang menjauh dari tengah memudar dan sedikit mengecil, jadi halaman tujuan
            // muncul bertahap.
            val frac = if (w > 0f) (abs(offset) / w).coerceAtMost(1f) else 0f
            v.alpha = 1f - 0.5f * frac
            val sc = 1f - 0.04f * frac
            v.scaleX = sc
            v.scaleY = sc
        }
    }

    /**
     * Mengatur tiap item halaman menurut seberapa dekat halaman ke tengah ([f]: 0 di luar layar, 1 di tengah).
     * Item di atas lebih dulu terlihat dan ikut meluncur dari arah [sgn], item di bawah menyusul, mengikuti
     * gerak jari dan pegas, jadi bisa dibalik bila geseran ditarik kembali. Pada [f] = 1 semua item netral.
     */
    private fun applyItems(page: View, f: Float, sgn: Float, lag: Float) {
        var container = page as? ViewGroup ?: return
        var sameAsLag = true
        if (page is ScrollView && page.childCount == 1) {
            val c = page.getChildAt(0)
            if (c is ViewGroup) {
                container = c
                sameAsLag = false
            }
        }
        for (j in 0 until container.childCount) {
            val c = container.getChildAt(j)
            val delay = ITEM_SPREAD * min(j, ITEM_MAX) / ITEM_MAX.toFloat()
            val tt = ((f - delay) / (1f - ITEM_SPREAD)).coerceIn(0f, 1f)
            c.alpha = tt
            c.translationX = (if (sameAsLag) lag else 0f) + sgn * (1f - tt) * itemShift
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Selama halaman masih meluncur, sentuhan baru ditelan supaya tidak salah mengenai
        // ikon atau slider yang sedang bergeser.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoring = settling && abs(dragX) > ignoreDistance
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
        // Bila halaman masih bergerak, geseran melanjutkan dari posisinya sekarang tanpa lompatan.
        startX = ev.x - dragX
        stopSettle()
        setPageLayers(true)
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun recycle() {
        tracker?.recycle()
        tracker = null
    }

    /**
     * Selama halaman bergerak, tiap halaman disimpan sebagai layer perangkat keras. Tanpa itu, memudarkan
     * halaman yang berisi banyak ikon harus menggambar ulang isinya di setiap frame, dan geseran
     * terasa kurang mulus.
     */
    private fun setPageLayers(on: Boolean) {
        val type = if (on) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE
        for (i in 0 until childCount) getChildAt(i).setLayerType(type, null)
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
        velocity = vx.coerceIn(-velocityCap, velocityCap)
        bounce = !changed
        damping = if (changed) DAMPING_PAGE else DAMPING_BOUNCE
        position()
        lastFrame = AnimationUtils.currentAnimationTimeMillis()
        if (!settling) {
            settling = true
            postOnAnimation(stepper)
        }
        if (changed) onPageChanged?.invoke(page)
    }

    override fun onDetachedFromWindow() {
        staggered.clear()
        stopSettle()
        setPageLayers(false)
        recycle()
        super.onDetachedFromWindow()
    }

    private companion object {
        /**
         * Kekakuan pegas dan dua redaman. Pindah halaman memakai redaman kritis (tanpa pantulan,
         * sekitar 0,6 detik sampai berhenti). Kembali ke halaman yang sama memakai redaman lebih
         * ringan sehingga memantul sedikit.
         */
        const val STIFFNESS = 120f
        const val DAMPING_PAGE = 22f
        const val DAMPING_BOUNCE = 16f

        /**
         * Item muncul berurutan saat berpindah halaman: jarak luncur (dp), porsi gerak halaman yang dipakai
         * untuk menyebar jeda antar item (0 sampai 1), dan jumlah item pertama yang diberi jeda bertingkat.
         */
        const val ITEM_SHIFT_DP = 18f
        const val ITEM_SPREAD = 0.4f
        const val ITEM_MAX = 8

        /** Seberapa jauh isi halaman tertinggal dari halamannya (pecahan dari jarak geser). */
        const val PARALLAX = 0.25f

        /** Batas langkah waktu per frame (detik), supaya frame yang tertahan tidak melempar halaman. */
        const val MAX_DT = 1f / 30f
    }
}
