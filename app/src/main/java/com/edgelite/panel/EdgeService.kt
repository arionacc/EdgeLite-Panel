package com.edgelite.panel

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.min

class EdgeService : Service() {

    companion object {
        const val ACTION_OPEN = "com.edgelite.panel.OPEN"
        const val ACTION_STOP = "com.edgelite.panel.STOP"
        private const val CHANNEL_ID = "edgelite_channel"
        private const val NOTIF_ID = 1
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val ACCENT = 0xFF3E91FF.toInt()
        private val HANDLE_COLOR = 0x80E6E6E6.toInt()

        fun start(ctx: Context) {
            Prefs(ctx).enabled = true
            ContextCompat.startForegroundService(ctx, Intent(ctx, EdgeService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs(ctx).enabled = false
            ctx.stopService(Intent(ctx, EdgeService::class.java))
        }

        /** Terapkan ulang pengaturan (sisi, ukuran handle, daftar aplikasi) bila panel aktif. */
        fun refresh(ctx: Context) {
            if (Prefs(ctx).enabled) start(ctx)
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var ui: Context

    private var handle: View? = null
    private var panelRoot: FrameLayout? = null
    private var panelCard: View? = null
    private var panelLp: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        ui = ContextThemeWrapper(this, R.style.Theme_EdgeLite)
        val channel = NotificationChannel(CHANNEL_ID, "Layanan panel tepi", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        closePanel(animated = false)
        removeHandle()
        addHandle()
        if (intent?.action == ACTION_OPEN) openPanel()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closePanel(animated = false)
        removeHandle()
        if (Settings.canDrawOverlays(this)) addHandle()
    }

    override fun onDestroy() {
        closePanel(animated = false)
        removeHandle()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Notifikasi

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val openPanel = PendingIntent.getService(
            this, 1, Intent(this, EdgeService::class.java).setAction(ACTION_OPEN),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, EdgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("EdgeLite Panel aktif")
            .setContentText("Geser dari tepi layar untuk membuka panel")
            .setContentIntent(open)
            .addAction(0, "Buka panel", openPanel)
            .addAction(0, "Matikan", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        if (panelRoot != null) return

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
                text = "Pilih aplikasi di pengaturan"
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
        card.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Tombol pengaturan
        val gear = ImageView(ui).apply {
            setImageResource(R.drawable.ic_tune)
            setPadding(dp(14), dp(10), dp(14), dp(6))
            setOnClickListener {
                this@EdgeService.startActivity(
                    Intent(this@EdgeService, MainActivity::class.java)
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            AppLauncher.launch(this, pkg, prefs.mode, prefs.getWinW(o), prefs.getWinH(o))
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
