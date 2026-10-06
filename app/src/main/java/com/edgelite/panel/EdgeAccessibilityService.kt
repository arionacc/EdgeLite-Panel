package com.edgelite.panel

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Handle dan panel digambar sebagai jendela TYPE_ACCESSIBILITY_OVERLAY dari layanan aksesibilitas.
 * Cara ini tidak butuh foreground service, notifikasi, maupun izin "tampil di atas aplikasi lain",
 * sehingga EdgeLite tidak muncul di "Periksa aktivitas latar belakang". Sistem juga menyalakan
 * layanan ini sendiri setiap ponsel selesai boot selama masih aktif di pengaturan aksesibilitas.
 * Layanan ini tidak membaca isi layar dan tidak menerima event apa pun.
 */
class EdgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Tinggi slider volume dan kecerahan di halaman slider (dp). */
        private const val SLIDER_DP = 176

        /** Tinggi tombol panah dan kapsul pilihan di halaman slider (dp), serta langkah kecerahan (0 sampai 255). */
        private const val ARROW_DP = 32
        private const val SEL_DP = 32
        private const val BRIGHTNESS_STEP = 17
        private val HANDLE_COLOR = 0x80E6E6E6.toInt()

        /** Perekam layar yang dikenal. Hanya yang terpasang dan bisa dibuka yang dipakai. */
        private val RECORDER_PACKAGES = listOf(
            "com.samsung.android.app.screenrecorder",
            "com.miui.screenrecorder",
            "com.coloros.screenrecorder",
            "com.hecorat.screenrecorder.free",
            "com.kimcy929.screenrecorder",
            "com.recorder.screenrecorder",
            "com.inshot.screenrecorder"
        )

        @Volatile
        private var instance: EdgeAccessibilityService? = null

        /** Apakah pengguna sudah menyalakan layanan ini di Pengaturan, Aksesibilitas. */
        fun isEnabledInSystem(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, EdgeAccessibilityService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        private fun currentBoot(ctx: Context): Int =
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

        fun start(ctx: Context) {
            val p = Prefs(ctx)
            p.enabled = true
            p.lastBoot = currentBoot(ctx)
            instance?.refresh()
        }

        fun stop(ctx: Context) {
            Prefs(ctx).enabled = false
            instance?.refresh()
        }

        /** Terapkan ulang pengaturan (sisi, ukuran handle, daftar aplikasi) bila panel aktif. */
        @Suppress("UNUSED_PARAMETER")
        fun refresh(ctx: Context) {
            instance?.refresh()
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var ui: Context

    private var handle: View? = null
    private var panelRoot: FrameLayout? = null
    private var panelCard: View? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var panelCardLp: FrameLayout.LayoutParams? = null
    private var heightAnim: ValueAnimator? = null
    private var volumeStepMode = false
    private var volumeTarget = -1
    private var volumeWarned = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null

    // Akses cepat: status sakelar dan ikon sakelar yang sedang tampil di panel terbuka.
    private var torchId: String? = null
    private var torchOn = false
    private var torchRegistered = false
    private val toggleIcons = HashMap<QuickAction, ImageView>()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                refreshToggles()
            }
        }
    }

    private val audio: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    /** Handle dan panel tidak ditampilkan selama layar terkunci (keyguard aktif). */
    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isKeyguardLocked
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // Lepas segera agar tidak sempat terlihat saat layar kunci menyala lagi.
                        mainHandler.removeCallbacksAndMessages(null)
                        closePanel(animated = false)
                        removeHandle()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        refresh()
                        // Status keyguard bisa terlambat berubah, cek ulang sebentar kemudian.
                        mainHandler.postDelayed({ refresh() }, 400)
                    }
                    Intent.ACTION_USER_PRESENT -> refresh()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(r, filter)
        screenReceiver = r
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        screenReceiver = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        ui = ContextThemeWrapper(this, R.style.Theme_EdgeLite)
        registerScreenReceiver()
        registerTorch()

        // Sambungan pertama setelah ponsel menyala: hormati pilihan Mulai otomatis.
        val boot = currentBoot(this)
        if (boot != -1) {
            if (prefs.lastBoot != -1 && prefs.lastBoot != boot && !prefs.autostart) {
                prefs.enabled = false
            }
            prefs.lastBoot = boot
        }
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::wm.isInitialized) refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        unregisterScreenReceiver()
        unregisterTorch()
        if (::wm.isInitialized) {
            closePanel(animated = false)
            removeHandle()
        }
    }

    /** Cari kamera belakang yang punya lampu kilat dan pantau statusnya, tanpa izin kamera. */
    private fun registerTorch() {
        if (torchRegistered) return
        runCatching {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.registerTorchCallback(torchCallback, mainHandler)
            torchRegistered = true
        }
    }

    private fun unregisterTorch() {
        if (!torchRegistered) return
        runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).unregisterTorchCallback(torchCallback) }
        torchRegistered = false
    }

    /** Bangun ulang handle sesuai pengaturan terbaru, atau lepas bila panel dimatikan. */
    private fun refresh() {
        closePanel(animated = false)
        removeHandle()
        if (prefs.enabled && !isDeviceLocked()) addHandle()
    }

    // ---------------------------------------------------------------- Handle tepi

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun orient(): Orient {
        val (w, h) = screenSize()
        return orientOf(w, h)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle() {
        val o = orient()
        val right = prefs.getSide(o) == EdgeSide.RIGHT
        val h = dp(prefs.getHandleHeight(o))
        val (_, sh) = screenSize()

        val container = FrameLayout(ui)
        val pill = View(ui).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(HANDLE_COLOR)
            }
        }
        val pillLp = FrameLayout.LayoutParams(
            dp(4), (h * 0.8f).toInt(),
            (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        ).apply {
            if (right) marginEnd = dp(2) else marginStart = dp(2)
        }
        container.addView(pill, pillLp)

        container.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var triggered = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        triggered = false
                    }
                    MotionEvent.ACTION_MOVE -> if (!triggered) {
                        val dx = e.rawX - downX
                        val inward = if (right) -dx else dx
                        if (inward > dp(16)) {
                            triggered = true
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            openPanel()
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!triggered && abs(e.rawX - downX) < dp(8)) {
                        openPanel()
                    }
                }
                return true
            }
        })

        if (Build.VERSION.SDK_INT >= 29) {
            container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height))
            }
        }

        val lp = WindowManager.LayoutParams(
            dp(24), h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = (if (right) Gravity.END else Gravity.START) or Gravity.TOP
        lp.x = 0
        lp.y = ((sh - h) * prefs.getHandleOffset(o) / 100)

        wm.addView(container, lp)
        handle = container
    }

    private fun removeHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        handle = null
    }

    // ---------------------------------------------------------------- Panel

    private fun openPanel() {
        if (panelRoot != null || isDeviceLocked()) return

        val (_, sh) = screenSize()
        val o = orient()
        val right = prefs.getSide(o) == EdgeSide.RIGHT
        val textColor = Ui.TEXT

        val alpha = (prefs.panelOpacity * 255 / 100).coerceIn(0, 255)
        val cardColor = (alpha shl 24) or (Ui.PANEL and 0x00FFFFFF)

        // Ukuran panel tetap: tidak bergantung pada jumlah aplikasi. Daftar digulir bila penuh.
        val widthDp = prefs.getPanelW(o)
        val cardW = dp(widthDp)
        val cardH = (sh * prefs.getPanelH(o) / 100).coerceIn(min(dp(160), sh), sh)
        val iconDp = min(prefs.iconSizeDp, widthDp - 24).coerceAtLeast(24)
        val showLabels = prefs.showLabels

        val pm = packageManager
        val pkgs = prefs.pinned.filter { pm.getLaunchIntentForPackage(it) != null }

        val card = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(dp(8), dp(12), dp(8), dp(8))
            val r = dp(prefs.cornerDp).toFloat()
            background = GradientDrawable().apply {
                setColor(cardColor)
                setStroke(dp(1), Ui.OUTLINE)
                cornerRadii = if (right) {
                    floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                } else {
                    floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                }
            }
            elevation = dp(8).toFloat()
        }

        // Daftar aplikasi
        val list = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        if (pkgs.isEmpty()) {
            list.addView(TextView(ui).apply {
                text = getString(R.string.panel_empty_hint)
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Ui.MUTED)
                setPadding(0, dp(16), 0, dp(16))
            })
        }
        pkgs.forEach { list.addView(appItem(it, textColor, iconDp, showLabels)) }
        val scroll = ScrollView(ui).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }
        val actions = prefs.quickActions
        if (actions.isEmpty()) {
            card.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        } else {
            // Halaman 1: aplikasi. Halaman 2: pintasan akses cepat (bila ada). Halaman 3: slider volume
            // dan kecerahan (bila ada), dipisah supaya halaman pintasan tidak gendut. Geser ke samping
            // untuk berpindah. Ukuran panel tetap sama.
            toggleIcons.clear()
            val sliders = actions.filter { it.kind == QuickKind.SLIDER }
            val shortcuts = actions.filter { it.kind != QuickKind.SLIDER }
            val pager = PanelPager(ui)
            pager.addPage(scroll)
            if (shortcuts.isNotEmpty()) {
                val quickList = LinearLayout(ui).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                shortcuts.forEach { quickList.addView(quickItem(it, textColor, iconDp, showLabels)) }
                pager.addPage(ScrollView(ui).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(quickList)
                })
            }
            // Halaman slider tidak perlu setinggi panel: kartu mengecil seukuran isi halaman lalu kembali
            // ke tinggi pengaturan saat pindah ke halaman lain. Slider mengecil bila panel pendek.
            val labelsH = if (showLabels) dp(32) else 0
            val controlsH = dp(12 + ARROW_DP + 6) + (if (sliders.size > 1) dp(SEL_DP + 8) else 0)
            val fixedH = dp(12 + 8) + controlsH + labelsH + dp(10) + dp(44)
            val sliderH = min(dp(SLIDER_DP), cardH - fixedH).coerceAtLeast(dp(72))
            val compactH = (fixedH + sliderH).coerceAtMost(cardH)
            if (sliders.isNotEmpty()) pager.addPage(sliderPage(sliders, textColor, showLabels, sliderH))
            // Penanda halaman ada di bawah, di antara halaman dan tombol pengaturan, jadi tidak menutupi ikon.
            val dots = pageDots(pager.childCount)
            val sliderIdx = if (sliders.isNotEmpty()) pager.childCount - 1 else -1
            pager.onPageChanged = {
                setActiveDot(dots, it)
                animateCardHeight(if (it == sliderIdx) compactH else cardH)
            }
            card.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f))
            card.addView(dots, LinearLayout.LayoutParams(MATCH, dp(8)).apply { topMargin = dp(2) })
        }

        // Tombol pengaturan
        val gear = ImageView(ui).apply {
            setImageResource(R.drawable.ic_tune)
            setPadding(dp(14), if (actions.isEmpty()) dp(10) else dp(4), dp(14), dp(6))
            setOnClickListener {
                this@EdgeAccessibilityService.startActivity(
                    Intent(this@EdgeAccessibilityService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                closePanel()
            }
        }
        card.addView(gear, LinearLayout.LayoutParams(MATCH, WRAP))

        val root = FrameLayout(ui)
        root.setBackgroundColor(0x66000000)
        root.setOnClickListener { closePanel() }
        val cardLp = FrameLayout.LayoutParams(
            cardW, cardH,
            (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        )
        panelCardLp = cardLp
        root.addView(card, cardLp)

        val lp = WindowManager.LayoutParams(
            MATCH, MATCH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = dp(20)
        }

        wm.addView(root, lp)
        panelRoot = root
        panelCard = card
        panelLp = lp

        // Animasi masuk dari tepi layar
        card.translationX = (if (right) 1 else -1) * cardW.toFloat()
        card.animate().translationX(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180).start()
    }

    /** Satu aksi akses cepat di halaman kedua, dengan ukuran dan susunan yang sama dengan item aplikasi. */
    private fun quickItem(a: QuickAction, textColor: Int, iconDp: Int, showLabels: Boolean): View {
        val item = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val tv = TypedValue()
        if (ui.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            item.setBackgroundResource(tv.resourceId)
        }
        val pad = (dp(iconDp) * 0.26f).toInt()
        val icon = ImageView(ui).apply {
            setImageResource(a.icon)
            setPadding(pad, pad, pad, pad)
        }
        styleQuickIcon(icon, a.kind == QuickKind.TOGGLE && isActive(a))
        if (a.kind == QuickKind.TOGGLE) toggleIcons[a] = icon
        item.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        if (showLabels) item.addView(quickLabel(a, textColor), labelLp())
        item.contentDescription = getString(a.label)
        item.setOnClickListener { onQuickClick(a) }
        return item
    }

    /**
     * Halaman slider, dari atas: tombol panah naik dan turun, kapsul pilihan (Volume atau Kecerahan,
     * hanya bila keduanya dipilih), lalu slider vertikal berdampingan. Panah mengatur slider yang
     * sedang dipilih; menyentuh sebuah slider juga memilihnya. Satu slider saja ditaruh di tengah
     * dengan lebar yang sama seperti bila ada dua.
     */
    private fun sliderPage(list: List<QuickAction>, textColor: Int, showLabels: Boolean, sliderH: Int): View {
        val views = LinkedHashMap<QuickAction, QuickSlider>()
        val cols = HashMap<QuickAction, View>()
        val segments = HashMap<QuickAction, ImageView>()
        var selected = list.first()

        fun refreshSelection() {
            segments.forEach { (a, iv) -> styleSegment(iv, a == selected) }
            if (list.size > 1) cols.forEach { (a, v) -> v.alpha = if (a == selected) 1f else 0.55f }
        }

        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        if (list.size == 1) row.addView(View(ui), LinearLayout.LayoutParams(0, WRAP, 0.5f))
        list.forEach { a ->
            val col = sliderColumn(a, textColor, showLabels, sliderH, views)
            cols[a] = col
            views[a]?.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN && selected != a) {
                    selected = a
                    refreshSelection()
                }
                false
            }
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        if (list.size == 1) row.addView(View(ui), LinearLayout.LayoutParams(0, WRAP, 0.5f))

        val page = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        // Tombol panah: tahan untuk mengulang.
        val arrows = LinearLayout(ui).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(R.drawable.ic_qa_arrow_up to 1, R.drawable.ic_qa_arrow_down to -1).forEachIndexed { i, (icon, dir) ->
            val btn = ImageView(ui).apply {
                setImageResource(icon)
                setColorFilter(Ui.TEXT)
                val pad = dp(5)
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ARROW_DP).toFloat()
                    setColor(Ui.OUTLINE)
                }
                contentDescription = getString(if (dir > 0) R.string.cd_increase else R.string.cd_decrease)
            }
            bindRepeat(btn) { nudge(selected, views[selected], dir) }
            arrows.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(ARROW_DP), 1f).apply { if (i == 0) rightMargin = dp(4) }
            )
        }
        page.addView(arrows, LinearLayout.LayoutParams(MATCH, dp(ARROW_DP)).apply { bottomMargin = dp(6) })

        // Kapsul pilihan
        if (list.size > 1) {
            val selector = LinearLayout(ui).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(SEL_DP).toFloat()
                    setColor(Ui.SURFACE)
                    setStroke(dp(1), Ui.OUTLINE)
                }
            }
            list.forEach { a ->
                val seg = ImageView(ui).apply {
                    setImageResource(a.icon)
                    val pad = dp(6)
                    setPadding(pad, pad, pad, pad)
                    contentDescription = getString(a.label)
                    setOnClickListener {
                        selected = a
                        refreshSelection()
                    }
                }
                segments[a] = seg
                selector.addView(seg, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            page.addView(selector, LinearLayout.LayoutParams(MATCH, dp(SEL_DP)).apply { bottomMargin = dp(8) })
        }

        page.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        refreshSelection()
        return page
    }

    private fun styleSegment(iv: ImageView, on: Boolean) {
        iv.setColorFilter(if (on) Ui.ON_LIGHT else Ui.TEXT)
        iv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(SEL_DP).toFloat()
            setColor(if (on) Ui.LIGHT else 0)
        }
    }

    /** Satu langkah naik (dir 1) atau turun (dir -1) untuk slider terpilih. False bila tidak bisa lanjut. */
    private fun nudge(a: QuickAction, slider: QuickSlider?, dir: Int): Boolean {
        slider ?: return false
        if (a == QuickAction.VOLUME) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = currentVolume().let { if (it < 0) (slider.progress * max).roundToInt() else it }
            val v = (cur + dir).coerceIn(0, max)
            slider.progress = v / max.toFloat()
            setVolume(v)
        } else {
            if (slider.onStart?.invoke() == false) return false
            val cur = runCatching {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrDefault((slider.progress * 255).roundToInt())
            val v = (cur + dir * BRIGHTNESS_STEP).coerceIn(1, 255)
            slider.progress = v / 255f
            runCatching { Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v) }
        }
        return true
    }

    /** Menjalankan [action] saat disentuh, lalu mengulang tiap 90 ms selama ditahan. */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindRepeat(v: View, action: () -> Boolean) {
        val repeat = object : Runnable {
            override fun run() {
                if (panelRoot == null) return
                if (action()) mainHandler.postDelayed(this, 90)
            }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.6f
                    if (action()) mainHandler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
                    mainHandler.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    private fun sliderColumn(
        a: QuickAction, textColor: Int, showLabels: Boolean, sliderH: Int,
        views: MutableMap<QuickAction, QuickSlider>
    ): View {
        val col = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), 0, dp(3), 0)
        }
        val slider = QuickSlider(ui, a.icon, vertical = true)
        slider.contentDescription = getString(a.label)
        views[a] = slider
        var last = -1
        when (a) {
            QuickAction.VOLUME -> {
                val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                slider.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()
                slider.onChange = { p ->
                    val v = (p * maxVol).roundToInt()
                    if (v != last) {
                        last = v
                        setVolume(v)
                    }
                }
            }
            else -> {
                val cur = runCatching {
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                }.getOrDefault(128)
                slider.progress = cur / 255f
                slider.onStart = {
                    val ok = ensureWriteSettings()
                    if (ok) {
                        // Kecerahan otomatis akan menimpa pilihan jari, jadi pindah ke mode manual.
                        runCatching {
                            Settings.System.putInt(
                                contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                            )
                        }
                    }
                    ok
                }
                slider.onChange = { p ->
                    val v = max(1, (p * 255).roundToInt())
                    if (v != last) {
                        last = v
                        runCatching { Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v) }
                    }
                }
            }
        }
        col.addView(slider, LinearLayout.LayoutParams(MATCH, sliderH))
        if (showLabels) col.addView(quickLabel(a, textColor), labelLp())
        return col
    }

    private fun quickLabel(a: QuickAction, textColor: Int) = TextView(ui).apply {
        text = getString(a.label)
        textSize = 10f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(textColor)
    }

    /**
     * Mengatur volume media. Beberapa ROM (misalnya ColorOS) bisa mengabaikan setStreamVolume tanpa
     * error, jadi hasilnya diperiksa sebentar setelah nilai terakhir dikirim.
     */
    private fun setVolume(v: Int) {
        volumeTarget = v
        applyVolume(v)
        mainHandler.postDelayed({ if (volumeTarget == v) verifyVolume(v) }, 150)
    }

    /** Mengatur volume media. Setelah ROM terbukti mengabaikan setStreamVolume, langsung memakai langkah. */
    private fun applyVolume(v: Int) {
        if (volumeStepMode) stepVolumeTo(v)
        else runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
    }

    private fun currentVolume(): Int =
        runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)

    /** Cadangan: naik atau turun satu langkah demi satu lewat adjustStreamVolume. */
    private fun stepVolumeTo(target: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        repeat(max + 1) {
            val cur = currentVolume()
            if (cur < 0 || cur == target) return
            val dir = if (target > cur) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0) }
            if (currentVolume() == cur) return
        }
    }

    private fun verifyVolume(v: Int) {
        if (currentVolume() == v) return
        if (!volumeStepMode) {
            volumeStepMode = true
            stepVolumeTo(v)
        }
        if (currentVolume() != v && !volumeWarned) {
            volumeWarned = true
            Toast.makeText(ui, R.string.toast_volume_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateCardHeight(target: Int) {
        val lp = panelCardLp ?: return
        val card = panelCard ?: return
        heightAnim?.cancel()
        heightAnim = ValueAnimator.ofInt(lp.height, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.height = it.animatedValue as Int
                card.requestLayout()
            }
            start()
        }
    }

    private fun labelLp() = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) }

    /** Bulatan abu gelap bila biasa, bulatan terang bila sakelar sedang menyala. */
    private fun styleQuickIcon(icon: ImageView, active: Boolean) {
        icon.setColorFilter(if (active) Ui.ON_LIGHT else Ui.TEXT)
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Ui.LIGHT else Ui.OUTLINE)
        }
    }

    private fun isActive(a: QuickAction): Boolean = when (a) {
        QuickAction.FLASHLIGHT -> torchOn
        QuickAction.AUTO_ROTATE -> runCatching {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
        }.getOrDefault(false)
        QuickAction.VIBRATE -> audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        else -> false
    }

    private fun refreshToggles() {
        toggleIcons.forEach { (a, icon) -> styleQuickIcon(icon, isActive(a)) }
    }

    private fun onQuickClick(a: QuickAction) {
        when (a.kind) {
            QuickKind.GLOBAL -> runQuickAction(a)
            QuickKind.ONESHOT -> sendMediaKey(a)
            QuickKind.LAUNCH -> openRecorder()
            QuickKind.TOGGLE -> toggle(a)
            QuickKind.SLIDER -> Unit
        }
    }

    private fun toggle(a: QuickAction) {
        when (a) {
            QuickAction.FLASHLIGHT -> {
                val id = torchId
                if (id == null) {
                    toast(R.string.toast_no_flash)
                } else {
                    // Status baru datang lewat torchCallback, yang juga memperbarui ikon.
                    runCatching {
                        (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(id, !torchOn)
                    }.onFailure { toast(R.string.toast_action_failed) }
                }
            }
            QuickAction.AUTO_ROTATE -> {
                if (!ensureWriteSettings()) return
                val now = isActive(a)
                runCatching {
                    Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (now) 0 else 1)
                }.onFailure { toast(R.string.toast_action_failed) }
                refreshToggles()
            }
            QuickAction.VIBRATE -> {
                // Mode senyap butuh akses Jangan Ganggu, jadi sakelar ini hanya bolak-balik normal dan getar.
                runCatching {
                    audio.ringerMode = if (isActive(a)) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE
                }.onFailure { toast(R.string.toast_action_failed) }
                refreshToggles()
            }
            else -> Unit
        }
    }

    /**
     * Buka aplikasi perekam layar: pilihan pengguna dulu, lalu aplikasi perekam yang dikenal dan terpasang.
     * Samsung One UI tidak punya aplikasi perekam yang bisa dibuka langsung (perekamnya ada di panel
     * pengaturan cepat), jadi bila tidak ada yang cocok, panel pengaturan cepat sistem yang dibuka.
     */
    private fun openRecorder() {
        val pm = packageManager
        val candidates = listOf(prefs.recorderPkg) + RECORDER_PACKAGES
        val intent = candidates.firstNotNullOfOrNull { pkg ->
            if (pkg.isBlank()) null else pm.getLaunchIntentForPackage(pkg)
        }
        if (intent != null) {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            closePanel()
            return
        }
        toast(R.string.toast_no_recorder)
        closePanel()
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) }, 350)
    }

    /** Putar atau jeda, lagu berikutnya, lagu sebelumnya: dikirim sebagai tombol media, tanpa izin. */
    private fun sendMediaKey(a: QuickAction) {
        val key = when (a) {
            QuickAction.MEDIA_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            QuickAction.MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }.onFailure { toast(R.string.toast_action_failed) }
    }

    /** Izin khusus "Ubah pengaturan sistem". Bila belum ada, buka layar izinnya dan batalkan aksi. */
    private fun ensureWriteSettings(): Boolean {
        if (Settings.System.canWrite(this)) return true
        toast(R.string.toast_need_write_settings)
        closePanel()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    /** Penanda halaman: titik kecil, yang aktif lebih terang. */
    private fun pageDots(count: Int): LinearLayout {
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(count) {
            val d = View(ui)
            row.addView(d, LinearLayout.LayoutParams(dp(5), dp(5)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        setActiveDot(row, 0)
        return row
    }

    private fun setActiveDot(row: LinearLayout, active: Int) {
        for (i in 0 until row.childCount) {
            row.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == active) Ui.TEXT else Ui.MUTED)
            }
        }
    }

    private fun runQuickAction(a: QuickAction) {
        closePanel()
        // Beri waktu agar panel dan latar buramnya hilang dulu, terutama untuk tangkapan layar dan kunci layar.
        mainHandler.postDelayed({
            if (!performGlobalAction(a.globalAction)) {
                Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show()
            }
        }, 350)
    }

    private fun appItem(pkg: String, textColor: Int, iconDp: Int, showLabels: Boolean): View {
        val pm = packageManager
        val item = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val tv = TypedValue()
        if (ui.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            item.setBackgroundResource(tv.resourceId)
        }

        val icon = ImageView(ui).apply {
            setImageDrawable(runCatching { pm.getApplicationIcon(pkg) }.getOrElse { pm.defaultActivityIcon })
        }
        item.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))

        if (showLabels) {
            val label = TextView(ui).apply {
                text = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg)
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            item.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
        }

        item.setOnClickListener {
            val o = orient()
            AppLauncher.launch(this, pkg, prefs.mode, prefs.getWinW(o), prefs.getWinH(o), prefs.winFit, prefs.getAppShape(pkg))
            closePanel()
        }
        return item
    }

    private fun closePanel(animated: Boolean = true) {
        val root = panelRoot ?: return
        val card = panelCard
        toggleIcons.clear()
        heightAnim?.cancel()
        heightAnim = null
        panelRoot = null
        panelCard = null
        panelCardLp = null
        panelLp = null

        if (!animated || card == null || root.visibility != View.VISIBLE) {
            runCatching { wm.removeView(root) }
            return
        }
        val right = prefs.getSide(orient()) == EdgeSide.RIGHT
        card.animate()
            .translationX((if (right) 1 else -1) * card.width.toFloat())
            .setDuration(160)
            .withEndAction { runCatching { wm.removeView(root) } }
            .start()
        root.animate().alpha(0f).setDuration(160).start()
    }
}
