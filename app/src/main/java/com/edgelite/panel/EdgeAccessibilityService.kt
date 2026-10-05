package com.edgelite.panel

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import kotlin.math.min

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
        private val HANDLE_COLOR = 0x80E6E6E6.toInt()

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null

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
        if (::wm.isInitialized) {
            closePanel(animated = false)
            removeHandle()
        }
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
            // Halaman 1: aplikasi. Halaman 2: akses cepat. Geser ke samping untuk berpindah.
            // Ukuran panel tetap sama, penanda halaman ditumpuk di atas halaman, di atas ikon pertama.
            val quickList = LinearLayout(ui).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            actions.forEach { quickList.addView(quickItem(it, textColor, iconDp, showLabels)) }
            val quickScroll = ScrollView(ui).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(quickList)
            }
            val pager = PanelPager(ui).apply {
                addPage(scroll)
                addPage(quickScroll)
            }
            val dots = pageDots(2)
            pager.onPageChanged = { setActiveDot(dots, it) }
            val body = FrameLayout(ui)
            body.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))
            body.addView(
                dots,
                FrameLayout.LayoutParams(WRAP, dp(5), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    .apply { topMargin = dp(1) }
            )
            card.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }

        // Tombol pengaturan
        val gear = ImageView(ui).apply {
            setImageResource(R.drawable.ic_tune)
            setPadding(dp(14), dp(10), dp(14), dp(6))
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
        root.addView(
            card,
            FrameLayout.LayoutParams(
                cardW, cardH,
                (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            )
        )

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
            setColorFilter(Ui.TEXT)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.OUTLINE)
            }
        }
        item.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        if (showLabels) {
            val label = TextView(ui).apply {
                text = getString(a.label)
                textSize = 10f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            item.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
        }
        item.contentDescription = getString(a.label)
        item.setOnClickListener { runQuickAction(a) }
        return item
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
        panelRoot = null
        panelCard = null
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
