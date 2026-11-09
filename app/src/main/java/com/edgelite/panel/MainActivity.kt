package com.edgelite.panel

import android.content.ActivityNotFoundException
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Lengkung penuh untuk semua tombol, segmen, dan pil status. Kartu memakai [Ui.CARD_RADIUS_DP]. */
private const val CAPSULE_RADIUS = 1000f

private const val SOURCE_URL = "https://github.com/arionacc/EdgeLite-Panel"

private const val REQ_WIDGET_BIND = 7301
private const val REQ_WIDGET_CONFIG = 7302

/** Satu pilihan di kisi ikon widget: kode pilihan (lihat [WidgetIcons]) dan nama untuk pembaca layar. */
private class IconCell(val choice: String, val label: String)

/** Satu penyedia widget di daftar pilihan. Ukuran dalam sel kisi. */
private class WidgetEntry(
    val info: AppWidgetProviderInfo,
    val app: String,
    val label: String,
    val icon: Drawable?,
    val cols: Int,
    val rows: Int
)

class MainActivity : AppCompatActivity() {

    private class Row(val view: View, val status: TextView)

    private lateinit var prefs: Prefs

    // Kartu status di bagian atas
    private lateinit var heroDot: View
    private lateinit var heroTitle: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroPrimary: TextView
    private lateinit var heroSecondary: TextView

    // Izin
    private lateinit var permSummary: TextView
    private lateinit var accessRow: Row
    private lateinit var batteryRow: Row
    private lateinit var writeRow: Row
    private lateinit var writeDivider: View

    // Aplikasi panel
    private lateinit var pinnedCount: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var pinnedHint: TextView

    // Widget
    private lateinit var widgetSummary: TextView
    private lateinit var widgetManage: View
    private lateinit var widgetSize: View
    private lateinit var widgetIcon: View

    /** ID widget yang sedang ditambahkan, menunggu izin atau layar konfigurasinya selesai. */
    private var pendingWidgetId = -1

    // Bagian yang dibangun ulang saat orientasi yang diedit berganti
    private lateinit var layoutContainer: LinearLayout
    private lateinit var openContainer: LinearLayout

    private var panelPreview: PanelPreview? = null

    /** Apakah panel sedang berjalan (aktif di pengaturan dan layanan aksesibilitas menyala). */
    private var panelRunning = false

    private fun refreshPanelPreview() {
        panelPreview?.update(prefs, editOrient)
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) doExport(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) doImport(uri) }

    /** Judul kategori yang sedang terbuka. Disimpan agar tidak menutup sendiri saat dibangun ulang. */
    private val openCategories = mutableSetOf<String>()

    /** Orientasi yang sedang diedit di halaman pengaturan. */
    private var editOrient = Orient.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dm = resources.displayMetrics
        editOrient = orientOf(dm.widthPixels, dm.heightPixels)
        openCategories.add(getString(R.string.cat_display))
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // Daftar aplikasi dimuat lebih awal di latar supaya dialog Edit langsung terbuka.
        AppRepo.invalidate()
        AppRepo.load(this)
    }

    // ------------------------------------------------------------------ Helper tampilan

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun toast(@StringRes res: Int) = toast(getString(res))

    private fun TextView.bold() = setTypeface(typeface, Typeface.BOLD)

    private fun LinearLayout.addTop(v: View, top: Int) {
        addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) })
    }

    /** Latar polos tanpa garis tepi: kapsul bila [capsule], bila tidak sudut membulat [radiusDp]. */
    private fun bg(color: Int, radiusDp: Int = 0, capsule: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = if (capsule) CAPSULE_RADIUS else dp(radiusDp).toFloat()
    }

    /** Latar kapsul dengan efek sentuh yang tidak keluar dari bentuk kapsul. */
    private fun capsuleRipple(fill: Int): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = CAPSULE_RADIUS
        }
        return RippleDrawable(ColorStateList.valueOf(0x33808080), bg(fill, capsule = true), mask)
    }

    /** Efek sentuh persegi untuk baris di dalam kartu. Kartu memotong sudutnya lewat clipToOutline. */
    private fun rowRipple() = RippleDrawable(
        ColorStateList.valueOf(0x33808080), null, ColorDrawable(Color.WHITE)
    )

    private fun borderlessBackground(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return tv.resourceId
    }

    /** Kartu dengan latar sendiri, tanpa garis tepi. Isinya dipotong mengikuti sudut kartu. */
    private fun card(radiusDp: Int = Ui.CARD_RADIUS_DP) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(Ui.CARD, radiusDp)
        clipToOutline = true
    }

    /** Pembaca layar menyebut tampilan ini sebagai tombol. */
    private fun View.asButton() {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        })
    }

    private fun caps(t: String) = TextView(this).apply {
        text = t.uppercase()
        textSize = Ui.TEXT_CAPTION
        bold()
        letterSpacing = 0.06f
        setTextColor(Ui.MUTED)
        ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        textSize = Ui.TEXT_SECONDARY
        setLineSpacing(0f, 1.3f)
        setTextColor(Ui.MUTED_HI)
    }

    private fun stylePill(v: TextView, light: Boolean) {
        v.setTextColor(if (light) Ui.ON_LIGHT else Ui.TEXT)
        v.background = capsuleRipple(if (light) Ui.LIGHT else Ui.CONTROL)
    }

    /** Tombol kapsul: [light] untuk aksi utama, bila tidak untuk aksi sekunder. Tinggi minimal 48 dp. */
    private fun pill(t: String, light: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = Ui.TEXT_BODY
        bold()
        gravity = Gravity.CENTER
        minHeight = dp(Ui.CONTROL_HEIGHT_DP)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        stylePill(this, light)
        isClickable = true
        setOnClickListener { onClick() }
        asButton()
    }

    private fun divider() = View(this).apply { setBackgroundColor(Ui.DIVIDER) }

    private fun LinearLayout.addDivider(): View {
        val d = divider()
        addView(d, LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            marginStart = dp(22)
            marginEnd = dp(22)
        })
        return d
    }

    private fun permRow(title: String, desc: String, onClick: () -> Unit): Row {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.ROW_MIN_DP)
            setPadding(dp(22), dp(14), dp(16), dp(14))
            background = rowRipple()
            isClickable = true
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = Ui.TEXT_SECONDARY
            setLineSpacing(0f, 1.2f)
            setTextColor(Ui.MUTED_HI)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        // Pil status mengikuti ukuran huruf sistem, jadi tidak dikunci ke ukuran tetap.
        val status = TextView(this).apply {
            textSize = Ui.TEXT_CAPTION
            bold()
            gravity = Gravity.CENTER
            minWidth = dp(68)
            minHeight = dp(32)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        row.addView(
            status,
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) }
        )
        return Row(row, status)
    }

    /**
     * Izin yang sudah diberikan tampil tenang, izin yang belum diberikan tampil terang
     * karena itulah yang perlu diketuk.
     */
    private fun setStatus(row: Row, active: Boolean, on: String = getString(R.string.status_active), off: String = getString(R.string.status_set)) {
        row.status.text = if (active) on else off
        row.status.setTextColor(if (active) Ui.MUTED_HI else Ui.ON_LIGHT)
        row.status.background = bg(if (active) Ui.CONTROL else Ui.LIGHT, capsule = true)
    }

    /** Kontrol segmen: satu jalur kapsul dengan segmen berukuran sama yang membagi lebar secara rata. */
    private fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg(Ui.CONTROL, capsule = true)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val segments = mutableListOf<TextView>()

        fun style(sel: Int) {
            segments.forEachIndexed { i, c ->
                val on = i == sel
                c.background = capsuleRipple(if (on) Ui.LIGHT else Ui.CONTROL)
                c.setTextColor(if (on) Ui.ON_LIGHT else Ui.MUTED_HI)
                c.isSelected = on
            }
        }

        options.forEachIndexed { i, t ->
            val seg = TextView(this).apply {
                text = t
                textSize = Ui.TEXT_BODY
                bold()
                gravity = Gravity.CENTER
                minHeight = dp(Ui.CONTROL_HEIGHT_DP)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = true
                setOnClickListener {
                    style(i)
                    onSelect(i)
                }
                asButton()
            }
            segments.add(seg)
            track.addView(seg, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        style(selected)
        return track
    }

    /**
     * Slider dengan nilai di sebelah judul. Bila [previewFocus] diisi, bagian pratinjau panel
     * yang terkait menyala selama slider disentuh.
     */
    private fun slider(
        title: String,
        from: Float,
        to: Float,
        value: Float,
        unit: String,
        previewFocus: PanelPreview.Focus? = null,
        onValue: (Int) -> Unit
    ): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val name = TextView(this).apply {
            text = title
            textSize = Ui.TEXT_BODY
            setTextColor(Ui.MUTED_HI)
        }
        val valueTv = TextView(this).apply {
            text = "${value.toInt()}$unit"
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.LIGHT)
        }
        head.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(valueTv)

        val s = Slider(this).apply {
            valueFrom = from
            valueTo = to
            stepSize = 1f
            this.value = value.coerceIn(from, to)
            isTickVisible = false
            trackHeight = dp(6)
            labelBehavior = LabelFormatter.LABEL_GONE
            contentDescription = title
            thumbTintList = ColorStateList.valueOf(Ui.LIGHT)
            trackActiveTintList = ColorStateList.valueOf(Ui.LIGHT)
            trackInactiveTintList = ColorStateList.valueOf(Ui.CONTROL)
            haloTintList = ColorStateList.valueOf(0x33E6E6E6)
            addOnChangeListener { _, v, fromUser ->
                valueTv.text = "${v.toInt()}$unit"
                if (fromUser) onValue(v.toInt())
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    if (previewFocus != null) panelPreview?.setFocus(previewFocus)
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    EdgeAccessibilityService.refresh(this@MainActivity)
                    if (previewFocus != null) panelPreview?.flashFocus(previewFocus, 500L)
                }
            })
        }
        box.addView(head)
        box.addView(s, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        return box
    }

    /** Baris dengan sakelar. Mengetuk di mana saja pada baris membalik nilainya. */
    private fun switchRow(title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = rowRipple()
            isClickable = true
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = Ui.TEXT_SECONDARY
            setLineSpacing(0f, 1.2f)
            setTextColor(Ui.MUTED_HI)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val checkedState = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val sw = MaterialSwitch(this).apply {
            isChecked = initial
            contentDescription = title
            setThumbTintList(ColorStateList(checkedState, intArrayOf(Ui.ON_LIGHT, Ui.MUTED_HI)))
            setTrackTintList(ColorStateList(checkedState, intArrayOf(Ui.LIGHT, Ui.CONTROL)))
            setTrackDecorationTintList(ColorStateList(checkedState, intArrayOf(Color.TRANSPARENT, Ui.MUTED)))
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(sw, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) })
        row.setOnClickListener { sw.toggle() }
        return row
    }

    /** Kartu kategori yang bisa dilipat. Isinya dibangun oleh [build]. */
    private fun category(title: String, summary: String, build: (LinearLayout) -> Unit): View {
        val radius = dp(Ui.CARD_RADIUS_DP).toFloat()
        val cardView = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.ROW_MIN_DP)
            setPadding(dp(22), dp(14), dp(14), dp(14))
            background = RippleDrawable(
                ColorStateList.valueOf(0x33808080),
                null,
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = radius
                }
            )
            isClickable = true
            asButton()
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = Ui.TEXT_CARD_TITLE
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = summary
            textSize = Ui.TEXT_SECONDARY
            setTextColor(Ui.MUTED_HI)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        head.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_expand)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        head.addView(chevron, LinearLayout.LayoutParams(dp(32), dp(32)))
        cardView.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        build(body)
        cardView.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))

        fun setOpen(open: Boolean, animate: Boolean) {
            body.visibility = if (open) View.VISIBLE else View.GONE
            val target = if (open) 180f else 0f
            if (animate) chevron.animate().rotation(target).setDuration(160).start()
            else chevron.rotation = target
            ViewCompat.setStateDescription(
                head,
                getString(if (open) R.string.state_expanded else R.string.state_collapsed)
            )
        }
        setOpen(title in openCategories, false)
        head.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            if (open) openCategories.add(title) else openCategories.remove(title)
            setOpen(open, true)
        }
        return cardView
    }

    // ------------------------------------------------------------------ Susunan layar

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Ui.PAGE_PAD_DP), dp(28), dp(Ui.PAGE_PAD_DP), dp(64))
        }
        fun add(v: View, top: Int = 0) = root.addTop(v, top)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "EdgeLite"
            textSize = Ui.TEXT_TITLE
            bold()
            setTextColor(Ui.TEXT)
            ViewCompat.setAccessibilityHeading(this, true)
        })
        titles.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = Ui.TEXT_SECONDARY
            setTextColor(Ui.MUTED)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        val more = ImageView(this).apply {
            setImageResource(R.drawable.ic_more)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(borderlessBackground())
            contentDescription = getString(R.string.more_options)
            isClickable = true
            setOnClickListener { showMenu(it) }
            asButton()
        }
        header.addView(more, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(header)

        // Kartu status: keadaan panel dan satu aksi utama
        val hero = card(Ui.HERO_RADIUS_DP).apply { setPadding(dp(24), dp(24), dp(24), dp(24)) }
        val heroHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroDot = View(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        heroHead.addView(heroDot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(12) })
        heroTitle = TextView(this).apply {
            textSize = Ui.TEXT_HERO
            bold()
            setTextColor(Ui.TEXT)
            ViewCompat.setAccessibilityHeading(this, true)
        }
        heroHead.addView(heroTitle, LinearLayout.LayoutParams(0, WRAP, 1f))
        hero.addView(heroHead)
        heroDesc = TextView(this).apply {
            textSize = Ui.TEXT_BODY
            setLineSpacing(0f, 1.25f)
            setTextColor(Ui.MUTED_HI)
        }
        hero.addTop(heroDesc, 8)
        heroPrimary = pill(getString(R.string.enable_panel), true) {
            if (panelRunning) {
                EdgeAccessibilityService.stop(this)
                refreshStatus()
            } else {
                enablePanel()
            }
        }
        hero.addTop(heroPrimary, 20)
        // Hanya tampil saat panel sudah dinyalakan tetapi layanan aksesibilitas belum menyala.
        heroSecondary = pill(getString(R.string.disable_panel), false) {
            EdgeAccessibilityService.stop(this)
            refreshStatus()
        }
        hero.addTop(heroSecondary, 10)
        add(hero, 24)

        // Izin
        val permHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        permHead.addView(caps(getString(R.string.section_permissions)), LinearLayout.LayoutParams(0, WRAP, 1f))
        permSummary = TextView(this).apply {
            textSize = Ui.TEXT_CAPTION
            setTextColor(Ui.MUTED_HI)
        }
        permHead.addView(permSummary)
        add(permHead, Ui.SECTION_GAP_DP)

        val permCard = card()
        accessRow = permRow(getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc)) {
            openAccessibilitySettings()
        }
        permCard.addView(accessRow.view)
        permCard.addDivider()
        batteryRow = permRow(getString(R.string.perm_battery), getString(R.string.perm_battery_desc)) {
            requestBatteryExemption()
        }
        permCard.addView(batteryRow.view)
        // Hanya tampil bila ada pintasan yang butuh izin "Ubah pengaturan sistem".
        writeDivider = permCard.addDivider()
        writeRow = permRow(getString(R.string.perm_write), getString(R.string.perm_write_desc)) {
            requestWriteSettings()
        }
        permCard.addView(writeRow.view)
        add(permCard, 14)

        // Aplikasi panel
        val appsCard = card().apply { setPadding(dp(22), dp(16), dp(16), dp(20)) }
        val appsHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appsTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsTexts.addView(TextView(this).apply {
            text = getString(R.string.pinned_title)
            textSize = Ui.TEXT_CARD_TITLE
            bold()
            setTextColor(Ui.TEXT)
            ViewCompat.setAccessibilityHeading(this, true)
        })
        pinnedCount = TextView(this).apply {
            textSize = Ui.TEXT_SECONDARY
            setTextColor(Ui.MUTED_HI)
        }
        appsTexts.addView(pinnedCount, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        appsHead.addView(appsTexts, LinearLayout.LayoutParams(0, WRAP, 1f))
        appsHead.addView(
            pill(getString(R.string.apps_edit), false) { pickApps() }.apply {
                contentDescription = getString(R.string.pick_apps)
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) }
        )
        appsCard.addView(appsHead)
        pinnedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(pinnedRow)
        }
        appsCard.addTop(scroller, 16)
        pinnedHint = note(getString(R.string.pinned_hint))
        appsCard.addTop(pinnedHint, 12)
        add(appsCard, Ui.SECTION_GAP_DP)

        // Panel: pratinjau dan semua yang memengaruhinya berdekatan
        add(caps(getString(R.string.section_panel)), Ui.SECTION_GAP_DP)
        add(buildPreviewCard(), 14)

        add(category(getString(R.string.cat_display), getString(R.string.cat_display_sum)) { b ->
            b.addTop(note(getString(R.string.cat_display_note)), 10)
            b.addTop(caps(getString(R.string.app_names)), 24)
            b.addTop(
                segmented(listOf(getString(R.string.show_names), getString(R.string.hide_names)), if (prefs.showLabels) 0 else 1) {
                    prefs.showLabels = it == 0
                    EdgeAccessibilityService.refresh(this)
                    refreshPanelPreview()
                    panelPreview?.flashFocus(PanelPreview.Focus.ICONS)
                }, 14
            )
            b.addTop(slider(getString(R.string.icon_size), 32f, 64f, prefs.iconSizeDp.toFloat(), " dp", PanelPreview.Focus.ICONS) {
                prefs.iconSizeDp = it
                refreshPanelPreview()
            }, 28)
            b.addTop(slider(getString(R.string.panel_opacity), 50f, 100f, prefs.panelOpacity.toFloat(), " %", PanelPreview.Focus.PANEL) {
                prefs.panelOpacity = it
                refreshPanelPreview()
            }, 20)
            b.addTop(slider(getString(R.string.corner_radius), 0f, 40f, prefs.cornerDp.toFloat(), " dp", PanelPreview.Focus.PANEL) {
                prefs.cornerDp = it
                refreshPanelPreview()
            }, 20)
        }, Ui.CARD_GAP_DP)

        layoutContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(layoutContainer, Ui.CARD_GAP_DP)

        // Aplikasi dan aksi
        add(caps(getString(R.string.section_behavior)), Ui.SECTION_GAP_DP)
        openContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(openContainer, 14)
        renderOrientSettings()

        add(category(getString(R.string.cat_quick), getString(R.string.cat_quick_sum)) { b ->
            val summary = note("")
            fun refreshSummary() {
                val names = prefs.quickActions.map { getString(it.label) }
                summary.text = if (names.isEmpty()) getString(R.string.quick_none) else names.joinToString(", ")
            }
            refreshSummary()
            b.addTop(note(getString(R.string.quick_note)), 10)
            b.addTop(summary, 14)
            b.addTop(pill(getString(R.string.quick_pick), false) { pickQuickActions { refreshSummary() } }, 20)
            val recorderNote = note("")
            fun refreshRecorder() {
                val pkg = prefs.recorderPkg
                val name = if (pkg.isEmpty()) getString(R.string.recorder_auto) else
                    runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                        .getOrDefault(pkg)
                recorderNote.text = getString(R.string.recorder_current, name)
            }
            refreshRecorder()
            b.addTop(note(getString(R.string.recorder_note)), 28)
            b.addTop(recorderNote, 10)
            b.addTop(pill(getString(R.string.recorder_pick), false) { pickRecorder { refreshRecorder() } }, 20)
        }, Ui.CARD_GAP_DP)

        add(category(getString(R.string.cat_page_order), getString(R.string.cat_page_order_sum)) { b ->
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun render() {
                list.removeAllViews()
                val order = prefs.pageOrder
                order.forEachIndexed { i, code ->
                    list.addView(pageOrderRow(code, i, order.size) { dir ->
                        val next = order.toMutableList()
                        next[i] = next[i + dir].also { next[i + dir] = next[i] }
                        prefs.pageOrder = next
                        EdgeAccessibilityService.refresh(this)
                        render()
                    })
                }
            }
            b.addTop(note(getString(R.string.page_order_note)), 10)
            b.addTop(list, 14)
            render()
        }, Ui.CARD_GAP_DP)

        add(category(getString(R.string.cat_widgets), getString(R.string.cat_widgets_sum)) { b ->
            widgetSummary = note("")
            widgetManage = pill(getString(R.string.widgets_manage), false) { manageWidgets() }
            b.addTop(note(getString(R.string.widgets_note)), 10)
            b.addTop(widgetSummary, 14)
            b.addTop(pill(getString(R.string.widgets_add), false) { pickWidget() }, 20)
            widgetSize = pill(getString(R.string.widgets_size), false) { pickWidgetForSize() }
            b.addTop(widgetSize, 10)
            widgetIcon = pill(getString(R.string.widgets_icon), false) { pickWidgetForIcon() }
            b.addTop(widgetIcon, 10)
            b.addTop(widgetManage, 10)
            b.addTop(
                switchRow(
                    getString(R.string.widgets_close_tap), getString(R.string.widgets_close_tap_desc),
                    prefs.widgetCloseOnTap
                ) { prefs.widgetCloseOnTap = it },
                20
            )
            refreshWidgets()
        }, Ui.CARD_GAP_DP)

        // Sistem
        add(caps(getString(R.string.section_system)), Ui.SECTION_GAP_DP)
        add(category(getString(R.string.cat_startup), getString(R.string.cat_startup_sum)) { b ->
            b.addTop(
                switchRow(
                    getString(R.string.autostart),
                    getString(R.string.autostart_desc),
                    prefs.autostart
                ) { prefs.autostart = it }, 6
            )
            b.addTop(note(getString(R.string.autostart_note)), 10)
        }, 14)

        add(category(getString(R.string.cat_backup), getString(R.string.cat_backup_sum)) { b ->
            b.addTop(
                note(getString(R.string.backup_note)), 10
            )
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                pill(getString(R.string.export_action), false) { exportLauncher.launch(getString(R.string.export_filename)) },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) }
            )
            row.addView(
                pill(getString(R.string.import_action), false) { importLauncher.launch(arrayOf("*/*")) },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(4) }
            )
            b.addTop(row, 20)
        }, Ui.CARD_GAP_DP)

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
        }
    }

    /**
     * Pratinjau panel dengan pemilih orientasi di dalamnya. Dibangun sekali: berganti orientasi
     * hanya memperbarui pratinjau dan membangun ulang kategori yang bergantung orientasi.
     */
    private fun buildPreviewCard(): View {
        val previewCard = card().apply { setPadding(dp(16), dp(20), dp(16), dp(18)) }
        previewCard.addView(caps(getString(R.string.panel_preview_title)))
        previewCard.addTop(
            segmented(
                listOf(getString(R.string.portrait), getString(R.string.landscape)),
                if (editOrient == Orient.PORTRAIT) 0 else 1
            ) {
                editOrient = if (it == 0) Orient.PORTRAIT else Orient.LANDSCAPE
                refreshPanelPreview()
                renderOrientSettings()
            }, 14
        )
        val preview = PanelPreview(this).apply {
            contentDescription = getString(R.string.panel_preview_title)
            update(prefs, editOrient)
        }
        panelPreview = preview
        previewCard.addTop(preview, 14)
        previewCard.addTop(note(getString(R.string.panel_preview_note)), 12)
        return previewCard
    }

    /** Membangun ulang kategori yang bergantung pada orientasi (potret atau lanskap). */
    private fun renderOrientSettings() {
        val o = editOrient
        val orientNote = if (o == Orient.PORTRAIT) getString(R.string.orient_note_portrait)
        else getString(R.string.orient_note_landscape)

        layoutContainer.removeAllViews()
        layoutContainer.addTop(category(getString(R.string.cat_layout), getString(R.string.cat_layout_sum)) { b ->
            b.addTop(note(orientNote), 10)
            b.addTop(caps(getString(R.string.screen_side)), 24)
            b.addTop(
                segmented(listOf(getString(R.string.left), getString(R.string.right)), if (prefs.getSide(o) == EdgeSide.LEFT) 0 else 1) {
                    prefs.setSide(o, if (it == 0) EdgeSide.LEFT else EdgeSide.RIGHT)
                    EdgeAccessibilityService.refresh(this)
                    refreshPanelPreview()
                    panelPreview?.flashFocus(PanelPreview.Focus.PANEL)
                }, 14
            )
            b.addTop(caps(getString(R.string.cat_panel_size)), 32)
            b.addTop(slider(getString(R.string.panel_height), 30f, 95f, prefs.getPanelH(o).toFloat(), " %", PanelPreview.Focus.PANEL) {
                prefs.setPanelH(o, it)
                refreshPanelPreview()
            }, 14)
            b.addTop(slider(getString(R.string.panel_width), 64f, 140f, prefs.getPanelW(o).toFloat(), " dp", PanelPreview.Focus.PANEL) {
                prefs.setPanelW(o, it)
                refreshPanelPreview()
            }, 20)
            b.addTop(caps(getString(R.string.handle)), 32)
            b.addTop(slider(getString(R.string.handle_height), 60f, 240f, prefs.getHandleHeight(o).toFloat(), " dp", PanelPreview.Focus.HANDLE) {
                prefs.setHandleHeight(o, it)
                refreshPanelPreview()
            }, 14)
            b.addTop(slider(getString(R.string.handle_position), 10f, 90f, prefs.getHandleOffset(o).toFloat(), " %", PanelPreview.Focus.HANDLE) {
                prefs.setHandleOffset(o, it)
                refreshPanelPreview()
            }, 20)
        }, 0)

        openContainer.removeAllViews()
        openContainer.addTop(category(getString(R.string.cat_open), getString(R.string.cat_open_sum)) { b ->
            b.addTop(caps(getString(R.string.default_open_mode)), 10)
            b.addTop(
                segmented(listOf(getString(R.string.mode_window), getString(R.string.mode_full)), if (prefs.mode == LaunchMode.WINDOW) 0 else 1) {
                    prefs.mode = if (it == 0) LaunchMode.WINDOW else LaunchMode.FULL
                }, 14
            )
            b.addTop(caps(getString(R.string.cat_window)), 32)
            b.addTop(note(orientNote), 10)
            val winPreview = WindowPreview(this).apply { update(prefs.getWinW(o), prefs.getWinH(o), o, prefs.winFit) }
            b.addTop(winPreview, 14)
            b.addTop(slider(getString(R.string.window_width), 30f, 100f, prefs.getWinW(o).toFloat(), " %") {
                prefs.setWinW(o, it)
                winPreview.update(it, prefs.getWinH(o), o, prefs.winFit)
            }, 16)
            b.addTop(slider(getString(R.string.window_height), 30f, 100f, prefs.getWinH(o).toFloat(), " %") {
                prefs.setWinH(o, it)
                winPreview.update(prefs.getWinW(o), it, o, prefs.winFit)
            }, 20)
            b.addTop(caps(getString(R.string.window_fit)), 32)
            b.addTop(
                segmented(listOf(getString(R.string.fit_auto), getString(R.string.fit_exact)), if (prefs.winFit) 0 else 1) {
                    prefs.winFit = it == 0
                    winPreview.update(prefs.getWinW(o), prefs.getWinH(o), o, prefs.winFit)
                }, 14
            )
            b.addTop(note(getString(R.string.window_fit_note)), 14)
        }, 0)
    }

    // ------------------------------------------------------------------ Aksi

    private fun refreshStatus() {
        refreshPermissions()
        renderPinned()
        refreshPanelPreview()
    }

    /** Apakah ada pintasan terpilih yang butuh izin "Ubah pengaturan sistem". */
    private fun needsWriteSettings(): Boolean =
        prefs.quickActions.any { it == QuickAction.BRIGHTNESS || it == QuickAction.AUTO_ROTATE }

    /** Memperbarui baris izin, ringkasannya, dan kartu status di bagian atas. */
    private fun refreshPermissions() {
        val accessOn = EdgeAccessibilityService.isEnabledInSystem(this)
        setStatus(accessRow, accessOn)
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        setStatus(batteryRow, batteryOk)

        val showWrite = needsWriteSettings()
        val writeOk = Settings.System.canWrite(this)
        writeRow.view.visibility = if (showWrite) View.VISIBLE else View.GONE
        writeDivider.visibility = if (showWrite) View.VISIBLE else View.GONE
        setStatus(writeRow, writeOk)

        val missing = listOf(accessOn, batteryOk).count { !it } + if (showWrite && !writeOk) 1 else 0
        permSummary.text = if (missing == 0) getString(R.string.perm_summary_ok)
        else getString(R.string.perm_summary_missing, missing)

        panelRunning = prefs.enabled && accessOn
        val waiting = prefs.enabled && !accessOn
        heroTitle.text = getString(
            when {
                panelRunning -> R.string.hero_active_title
                waiting -> R.string.hero_access_title
                else -> R.string.hero_off_title
            }
        )
        heroDesc.text = getString(
            when {
                panelRunning -> R.string.hero_active_desc
                waiting -> R.string.hero_access_desc
                else -> R.string.hero_off_desc
            }
        )
        heroDot.background = if (panelRunning) {
            bg(Ui.LIGHT, capsule = true)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Ui.MUTED)
            }
        }
        heroPrimary.text = getString(if (panelRunning) R.string.disable_panel else R.string.enable_panel)
        stylePill(heroPrimary, !panelRunning)
        heroSecondary.visibility = if (waiting) View.VISIBLE else View.GONE
    }

    private fun renderPinned() {
        val pm = packageManager
        val pkgs = prefs.pinned.filter { pm.getLaunchIntentForPackage(it) != null }
        pinnedCount.text = resources.getQuantityString(R.plurals.pinned_count, pkgs.size, pkgs.size)
        pinnedRow.removeAllViews()
        pinnedHint.visibility = if (pkgs.isEmpty()) View.GONE else View.VISIBLE
        if (pkgs.isEmpty()) {
            pinnedRow.addView(note(getString(R.string.pinned_empty)))
            return
        }
        pkgs.forEach { pkg ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrElse { pkg }
            // Area ketuk 48 dp dengan ikon 40 dp di dalamnya.
            val icon = ImageView(this).apply {
                setImageDrawable(runCatching { pm.getApplicationIcon(pkg) }.getOrElse { pm.defaultActivityIcon })
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setBackgroundResource(borderlessBackground())
                contentDescription = label
                isClickable = true
                setOnClickListener { showShapeDialog(pkg) }
                asButton()
            }
            pinnedRow.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(6) })
        }
    }

    private fun requestWriteSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.toast_settings_failed))
        }
    }

    /** Memilih bentuk jendela untuk satu aplikasi, bila deteksi otomatis meleset. */
    private fun showShapeDialog(pkg: String) {
        val pm = packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrElse { pkg }
        val shapes = arrayOf(WinShape.AUTO, WinShape.PORTRAIT, WinShape.LANDSCAPE, WinShape.FREE)
        val names = arrayOf(
            getString(R.string.shape_auto), getString(R.string.shape_portrait),
            getString(R.string.shape_landscape), getString(R.string.shape_free)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.shape_title, label))
            .setSingleChoiceItems(names, shapes.indexOf(prefs.getAppShape(pkg))) { d, which ->
                prefs.setAppShape(pkg, shapes[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun enablePanel() {
        // Dicatat dulu agar handle langsung muncul begitu layanan dinyalakan di pengaturan.
        EdgeAccessibilityService.start(this)
        if (!EdgeAccessibilityService.isEnabledInSystem(this)) {
            openAccessibilitySettings()
        }
        refreshStatus()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 0, 0, getString(R.string.menu_tutorial))
            menu.add(0, 1, 1, getString(R.string.menu_developer))
            menu.add(0, 3, 2, getString(R.string.menu_about))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> showTutorial()
                    1 -> openDeveloperOptions()
                    3 -> showAbout()
                }
                true
            }
        }.show()
    }

    // ------------------------------------------------------------------ Dialog Tutorial dan Tentang

    /** Kartu untuk isi dialog, sedikit lebih terang dari latar dialog supaya terbaca sebagai satu kelompok. */
    private fun sheetCard(radiusDp: Int = 20) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(Ui.SHEET_CARD, radiusDp)
        clipToOutline = true
    }

    /** Label kecil berbentuk kapsul, misalnya "Opsional". */
    private fun chip(t: String) = TextView(this).apply {
        text = t
        textSize = Ui.TEXT_CAPTION
        bold()
        setTextColor(Ui.TEXT)
        setPadding(dp(10), dp(3), dp(10), dp(3))
        background = bg(Ui.CONTROL, capsule = true)
    }

    private fun tutorialItem(box: LinearLayout, @StringRes title: Int, @StringRes body: Int, top: Int) {
        box.addTop(TextView(this).apply {
            text = getString(title)
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        }, top)
        box.addTop(note(getString(body)), 4)
    }

    /** Satu langkah bernomor. Garis tipis di sisi kiri menyambungkan satu langkah ke langkah berikutnya. */
    private fun stepRow(
        number: Int,
        @StringRes title: Int,
        @StringRes body: Int,
        last: Boolean,
        optional: Boolean = false
    ): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        rail.addView(TextView(this).apply {
            text = number.toString()
            textSize = Ui.TEXT_SECONDARY
            bold()
            gravity = Gravity.CENTER
            setTextColor(if (optional) Ui.TEXT else Ui.ON_LIGHT)
            background = bg(if (optional) Ui.CONTROL else Ui.LIGHT, capsule = true)
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        if (!last) {
            rail.addView(View(this).apply { setBackgroundColor(Ui.CONTROL) },
                LinearLayout.LayoutParams(dp(2), 0, 1f).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                })
        }
        row.addView(rail, LinearLayout.LayoutParams(dp(28), MATCH))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, if (last) 0 else dp(20))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = getString(title)
            textSize = Ui.TEXT_CARD_TITLE
            bold()
            setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(WRAP, WRAP, 1f))
        if (optional) {
            head.addView(chip(getString(R.string.tut_optional)),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        }
        texts.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        texts.addView(note(getString(body)), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
        return row
    }

    /**
     * Baris yang bisa dilipat di dalam [card]. Garis pemisah ditambahkan otomatis dari baris kedua.
     * Isinya disembunyikan sampai judulnya diketuk, supaya tutorial tidak jadi dinding teks.
     */
    private fun addAccordion(card: LinearLayout, @StringRes title: Int, @StringRes body: Int, open: Boolean = false) {
        if (card.childCount > 0) {
            card.addView(View(this).apply { setBackgroundColor(Ui.OUTLINE) },
                LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                })
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.CONTROL_HEIGHT_DP + 4)
            setPadding(dp(16), dp(10), dp(10), dp(10))
            background = rowRipple()
            isClickable = true
            asButton()
        }
        head.addView(TextView(this).apply {
            text = getString(title)
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_expand)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        head.addView(chevron, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(8) })
        card.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val bodyView = note(getString(body)).apply { setPadding(dp(16), 0, dp(16), dp(16)) }
        card.addView(bodyView, LinearLayout.LayoutParams(MATCH, WRAP))

        fun setOpen(isOpen: Boolean, animate: Boolean) {
            bodyView.visibility = if (isOpen) View.VISIBLE else View.GONE
            val target = if (isOpen) 180f else 0f
            if (animate) chevron.animate().rotation(target).setDuration(160).start()
            else chevron.rotation = target
            ViewCompat.setStateDescription(
                head,
                getString(if (isOpen) R.string.state_expanded else R.string.state_collapsed)
            )
        }
        setOpen(open, false)
        head.setOnClickListener { setOpen(bodyView.visibility != View.VISIBLE, true) }
    }

    /** Kartu penegasan privasi layanan aksesibilitas: tiga hal yang tidak dilakukan, lalu catatannya. */
    private fun trustCard(): View {
        val c = sheetCard().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        c.addView(TextView(this).apply {
            text = getString(R.string.tut_a11y_does_t)
            textSize = Ui.TEXT_CARD_TITLE
            bold()
            setTextColor(Ui.TEXT)
        })
        for (res in listOf(R.string.tut_a11y_p1, R.string.tut_a11y_p2, R.string.tut_a11y_p3)) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(View(this).apply { background = bg(Ui.LIGHT, capsule = true) },
                LinearLayout.LayoutParams(dp(8), dp(8)))
            line.addView(TextView(this).apply {
                text = getString(res)
                textSize = Ui.TEXT_BODY
                setTextColor(Ui.TEXT)
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) })
            c.addView(line, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        }
        c.addView(note(getString(R.string.tut_a11y_does_b)),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        return c
    }

    private fun showTutorial() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(12))
        }
        box.addTop(note(getString(R.string.tut_intro)), 0)

        // Memulai: empat langkah bernomor
        box.addTop(caps(getString(R.string.tut_sec_start)), 26)
        val steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        steps.addView(stepRow(1, R.string.tut_start_1_t, R.string.tut_start_1_b, last = false))
        steps.addView(stepRow(2, R.string.tut_start_2_t, R.string.tut_start_2_b, last = false))
        steps.addView(stepRow(3, R.string.tut_start_3_t, R.string.tut_start_3_b, last = false))
        steps.addView(stepRow(4, R.string.tut_start_4_t, R.string.tut_start_4_b, last = true, optional = true))
        box.addTop(steps, 14)

        // Layanan aksesibilitas
        box.addTop(caps(getString(R.string.tut_sec_a11y)), 30)
        tutorialItem(box, R.string.tut_a11y_why_t, R.string.tut_a11y_why_b, 12)
        box.addTop(trustCard(), 16)
        val a11y = sheetCard()
        addAccordion(a11y, R.string.tut_a11y_enable_t, R.string.tut_a11y_enable_b, open = true)
        addAccordion(a11y, R.string.tut_a11y_disable_t, R.string.tut_a11y_disable_b)
        box.addTop(a11y, 12)

        // Pembaruan dan jendela mengambang
        box.addTop(caps(getString(R.string.tut_sec_update)), 30)
        val update = sheetCard()
        addAccordion(update, R.string.tut_update_keep_t, R.string.tut_update_keep_b)
        box.addTop(update, 12)

        box.addTop(caps(getString(R.string.tut_sec_freeform)), 30)
        val freeform = sheetCard()
        addAccordion(freeform, R.string.tut_freeform_enable_t, R.string.tut_freeform_enable_b)
        box.addTop(freeform, 12)

        box.addTop(caps(getString(R.string.tut_sec_widgets)), 30)
        val widgets = sheetCard()
        addAccordion(widgets, R.string.tut_widgets_t, R.string.tut_widgets_b)
        box.addTop(widgets, 12)

        // Masalah dan solusi
        box.addTop(caps(getString(R.string.tut_sec_trouble)), 30)
        val trouble = sheetCard()
        addAccordion(trouble, R.string.tr_gray_t, R.string.tr_gray_b)
        addAccordion(trouble, R.string.tr_reboot_t, R.string.tr_reboot_b)
        addAccordion(trouble, R.string.tr_dies_t, R.string.tr_dies_b)
        addAccordion(trouble, R.string.tr_fullscreen_t, R.string.tr_fullscreen_b)
        addAccordion(trouble, R.string.tr_gesture_t, R.string.tr_gesture_b)
        addAccordion(trouble, R.string.tr_apply_t, R.string.tr_apply_b)
        addAccordion(trouble, R.string.tr_missing_t, R.string.tr_missing_b)
        addAccordion(trouble, R.string.tr_nohandle_t, R.string.tr_nohandle_b)
        addAccordion(trouble, R.string.tr_widget_t, R.string.tr_widget_b)
        box.addTop(trouble, 12)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tut_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.CONTROL_HEIGHT_DP)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = Ui.TEXT_BODY
            setTextColor(Ui.MUTED_HI)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        })
        return row
    }

    /** Menambah baris info ke [card]. Bila [onClick] diberikan, seluruh baris bisa diketuk. */
    private fun addInfoRow(card: LinearLayout, label: String, value: String, onClick: (() -> Unit)? = null) {
        if (card.childCount > 0) {
            card.addView(View(this).apply { setBackgroundColor(Ui.OUTLINE) },
                LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                })
        }
        val row = infoRow(label, value)
        if (onClick != null) {
            row.background = rowRipple()
            row.isClickable = true
            row.setOnClickListener { onClick() }
            row.asButton()
        }
        card.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun doExport(uri: Uri) {
        try {
            val bytes = prefs.exportJson().toByteArray(Charsets.UTF_8)
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Cannot write file")
            toast(getString(R.string.toast_export_ok))
        } catch (e: Exception) {
            toast(getString(R.string.toast_export_failed))
        }
    }

    private fun doImport(uri: Uri) {
        val ok = try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            bytes.size in 1..200_000 && prefs.importJson(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            toast(getString(R.string.toast_import_invalid))
            return
        }
        setContentView(buildUi())
        EdgeAccessibilityService.refresh(this)
        refreshStatus()
        val missing = prefs.pinned.count { packageManager.getLaunchIntentForPackage(it) == null }
        toast(
            if (missing > 0) resources.getQuantityString(R.plurals.toast_import_missing, missing, missing)
            else getString(R.string.toast_import_ok)
        )
    }

    /** Versi dibaca dari paket terpasang agar selalu sama dengan versionName di build.gradle.kts. */
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun showAbout() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(12))
        }

        // Kepala: ikon aplikasi, ringkasan, dan versi
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val tile = FrameLayout(this).apply {
            background = bg(Ui.BG, 24)
            clipToOutline = true
        }
        tile.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER))
        hero.addView(tile, LinearLayout.LayoutParams(dp(84), dp(84)))
        hero.addView(note(getString(R.string.about_summary)).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        hero.addView(chip("v" + appVersionName()),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        box.addTop(hero, 4)

        // Info singkat
        val info = sheetCard()
        addInfoRow(info, getString(R.string.about_author), "Arion")
        addInfoRow(info, getString(R.string.about_license), "GPL-3.0")
        addInfoRow(info, getString(R.string.about_version), appVersionName())
        addInfoRow(info, getString(R.string.about_repo), SOURCE_URL.removePrefix("https://github.com/")) {
            openSourceCode()
        }
        box.addTop(info, 22)

        box.addTop(pill(getString(R.string.about_open_source), true) { openSourceCode() }, 16)
        box.addTop(pill(getString(R.string.about_view_license), false) { openLink("$SOURCE_URL/blob/main/LICENSE") }, 10)

        // Peringatan kompatibilitas dibuat menonjol karena memengaruhi apakah aplikasi bisa dipakai
        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg(Ui.SHEET_CARD, 20)
            clipToOutline = true
        }
        notice.addView(View(this).apply { setBackgroundColor(Ui.MUTED) }, LinearLayout.LayoutParams(dp(4), MATCH))
        val noticeBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        noticeBody.addView(caps(getString(R.string.about_compat_title)))
        noticeBody.addView(note(getString(R.string.about_compat_warning)),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        notice.addView(noticeBody, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addTop(notice, 26)

        box.addTop(caps(getString(R.string.about_disclaimer_title)), 26)
        box.addTop(
            note(getString(R.string.about_disclaimer)).apply {
                textSize = Ui.TEXT_CAPTION
                setTextColor(Ui.MUTED)
            }, 8
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openSourceCode() = openLink(SOURCE_URL)

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.toast_no_link_app))
        }
    }

    private fun openAccessibilitySettings() {
        toast(getString(R.string.toast_a11y_hint))
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.toast_a11y_failed))
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.toast_dev_inactive))
        }
    }

    private fun pickRecorder(onDone: () -> Unit) {
        AppRepo.load(this) { apps ->
            if (!isFinishing && !isDestroyed) showRecorderPicker(apps, onDone)
        }
    }

    private fun showRecorderPicker(apps: List<AppItem>, onDone: () -> Unit) {
        val names = (listOf(getString(R.string.recorder_auto)) + apps.map { it.label }).toTypedArray()
        val checked = (apps.indexOfFirst { it.pkg == prefs.recorderPkg } + 1).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recorder_pick_title)
            .setSingleChoiceItems(names, checked) { dialog, i ->
                prefs.recorderPkg = if (i == 0) "" else apps[i - 1].pkg
                onDone()
                EdgeAccessibilityService.refresh(this)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickQuickActions(onDone: () -> Unit) {
        val all = QuickAction.available()
        val current = prefs.quickActions.toMutableList()
        val checked = BooleanArray(all.size) { all[it] in current }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.quick_pick_title)
            .setMultiChoiceItems(all.map { getString(it.label) }.toTypedArray(), checked) { _, i, isChecked ->
                if (isChecked) {
                    if (all[i] !in current) current.add(all[i])
                } else {
                    current.remove(all[i])
                }
            }
            .setPositiveButton(R.string.save) { _, _ ->
                // Urutan tampil mengikuti urutan bawaan aksi, bukan urutan ketukan.
                prefs.quickActions = all.filter { it in current }
                onDone()
                refreshPermissions()
                refreshPanelPreview()
                EdgeAccessibilityService.refresh(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ Widget

    /** Nama halaman panel menurut kodenya (lihat [PAGE_CODES]). */
    private fun pageName(code: String): String = getString(
        when (code) {
            "a" -> R.string.page_apps
            "t" -> R.string.page_tools
            "v" -> R.string.page_sliders
            else -> R.string.page_widgets
        }
    )

    /** Satu baris di Urutan halaman: nomor, nama, dan tombol naik atau turun. [onMove] menerima -1 atau 1. */
    private fun pageOrderRow(code: String, index: Int, count: Int, onMove: (Int) -> Unit): View {
        val name = pageName(code)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.CONTROL_HEIGHT_DP)
        }
        row.addView(TextView(this).apply {
            text = "${index + 1}"
            textSize = Ui.TEXT_BODY
            setTextColor(Ui.MUTED_HI)
        }, LinearLayout.LayoutParams(dp(28), WRAP))
        row.addView(TextView(this).apply {
            text = name
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        fun arrow(res: Int, desc: String, enabled: Boolean, dir: Int) = ImageView(this).apply {
            setImageResource(res)
            setColorFilter(Ui.TEXT)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = desc
            alpha = if (enabled) 1f else 0.3f
            isEnabled = enabled
            if (enabled) {
                background = rowRipple()
                setOnClickListener { onMove(dir) }
            }
        }
        row.addView(arrow(R.drawable.ic_qa_arrow_up, getString(R.string.page_move_up, name), index > 0, -1),
            LinearLayout.LayoutParams(dp(Ui.CONTROL_HEIGHT_DP), dp(Ui.CONTROL_HEIGHT_DP)))
        row.addView(arrow(R.drawable.ic_qa_arrow_down, getString(R.string.page_move_down, name), index < count - 1, 1),
            LinearLayout.LayoutParams(dp(Ui.CONTROL_HEIGHT_DP), dp(Ui.CONTROL_HEIGHT_DP)))
        return row
    }

    /** Memperbarui ringkasan kartu Widget. ID yang sudah tidak dikenal sistem dibuang dari pengaturan. */
    private fun refreshWidgets() {
        val saved = prefs.widgets
        val valid = WidgetHost.validIds(this, saved)
        if (valid.size != saved.size) prefs.widgets = valid
        val pm = packageManager
        val names = valid.mapNotNull { id ->
            WidgetHost.info(this, id)?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }
        }
        widgetSummary.text =
            if (names.isEmpty()) getString(R.string.widgets_none) else names.joinToString(", ")
        widgetManage.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
        widgetSize.visibility = widgetManage.visibility
        widgetIcon.visibility = widgetManage.visibility
        prefs.pruneWidgetSizes(valid)
        prefs.pruneWidgetIcons(valid)
    }

    /** Memilih widget yang ukuran kartunya mau diatur. Tiap baris menampilkan ukuran saat ini. */
    private fun pickWidgetForSize() {
        val ids = WidgetHost.validIds(this, prefs.widgets)
        if (ids.isEmpty()) return
        val pm = packageManager
        val names = ids.map { id ->
            val label = WidgetHost.info(this, id)?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() } ?: id.toString()
            val size = prefs.getWidgetSize(id)
                ?.let { getString(R.string.widget_size_dp, it.first, it.second) }
                ?: getString(R.string.widget_size_auto)
            "$label ($size)"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_size_title)
            .setItems(names.toTypedArray()) { _, i -> showWidgetSizeDialog(ids[i]) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Mengatur ukuran kartu satu widget dengan pratinjau langsung. Widget asli digambar di panggung, diperkecil
     * bila perlu agar muat, dan ukurannya diubah dengan menyeret pegangan di sudut kanan bawah atau memilih
     * ukuran siap pakai. Ketukan pada pratinjau diblokir supaya tidak menjalankan aksi widget. Ukuran yang
     * ditampilkan sama dengan yang dipakai kartu di panel, kecuali dijepit ruang layar saat kartu dibuka.
     */
    @Suppress("ClickableViewAccessibility")
    private fun showWidgetSizeDialog(id: Int) {
        val info = WidgetHost.info(this, id) ?: return
        val label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault("")
        val d = resources.displayMetrics.density
        val growW = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val growH = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
        // Sama dengan ukuran otomatis di EdgeAccessibilityService.showWidgetCard.
        val autoW = maxOf(160, (if (growW) maxOf(info.minWidth / d, 260f) else info.minWidth / d).toInt()).coerceAtMost(WIDGET_W_MAX)
        val autoH = maxOf(110, (if (growH) maxOf(info.minHeight / d, 260f) else info.minHeight / d).toInt()).coerceAtMost(WIDGET_H_MAX)

        val saved = prefs.getWidgetSize(id)
        var auto = saved == null
        var w = saved?.first ?: autoW
        var h = saved?.second ?: autoH

        val night = createConfigurationContext(Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        })
        val view = runCatching { WidgetHost.get(this).createView(night, id, info) }.getOrNull()
        if (view == null) {
            toast(R.string.widget_open_failed)
            return
        }

        val stageH = dp(300)
        var stageW = 0
        var scale = 1f
        var dragging = false
        var anchorX = 0f
        var anchorY = 0f
        val handleSize = dp(44)

        val stage = FrameLayout(this).apply { background = bg(Ui.CARD, 20) }
        val holder = FrameLayout(this).apply {
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Ui.PANEL)
                setStroke(dp(1), Ui.OUTLINE)
                cornerRadius = dp(24).toFloat()
            }
        }
        holder.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        holder.addView(View(this).apply { isClickable = true }, FrameLayout.LayoutParams(MATCH, MATCH))
        stage.addView(holder, FrameLayout.LayoutParams(dp(w), dp(h)))

        val handle = View(this).apply {
            background = InsetDrawable(
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.LIGHT) }, dp(9)
            )
            contentDescription = getString(R.string.widget_size_handle)
        }
        stage.addView(handle, FrameLayout.LayoutParams(handleSize, handleSize))

        val readout = TextView(this).apply {
            textSize = Ui.TEXT_BODY
            bold()
            gravity = Gravity.CENTER
            setTextColor(Ui.TEXT)
        }

        // Skala dipilih supaya ada sisa ruang 25 persen untuk memperbesar, lalu dihitung ulang saat jari dilepas.
        fun fitScale() {
            if (stageW <= 0) return
            scale = minOf(1f, stageW / (dp(w) * 1.25f), stageH / (dp(h) * 1.25f))
        }

        fun place() {
            if (stageW <= 0) return
            val wp = dp(w)
            val hp = dp(h)
            holder.layoutParams = FrameLayout.LayoutParams(wp, hp)
            holder.pivotX = 0f
            holder.pivotY = 0f
            holder.scaleX = scale
            holder.scaleY = scale
            holder.x = if (dragging) anchorX else (stageW - wp * scale) / 2f
            holder.y = if (dragging) anchorY else (stageH - hp * scale) / 2f
            handle.x = holder.x + wp * scale - handleSize / 2f
            handle.y = holder.y + hp * scale - handleSize / 2f
            readout.text = getString(if (auto) R.string.widget_size_auto_dp else R.string.widget_size_dp, w, h)
            WidgetHost.applySize(view, w, h)
        }

        fun setSize(nw: Int, nh: Int, isAuto: Boolean) {
            auto = isAuto
            w = nw.coerceIn(WIDGET_W_MIN.coerceAtMost(nw), WIDGET_W_MAX)
            h = nh.coerceIn(WIDGET_H_MIN.coerceAtMost(nh), WIDGET_H_MAX)
            fitScale()
            place()
        }

        stage.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val nw = r - l
            if (nw > 0 && nw != stageW) {
                stageW = nw
                fitScale()
                place()
            }
        }

        var startW = 0
        var startH = 0
        var downX = 0f
        var downY = 0f
        handle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startW = w
                    startH = h
                    downX = e.rawX
                    downY = e.rawY
                    anchorX = holder.x
                    anchorY = holder.y
                    dragging = true
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val k = d * scale
                    // Batas atas dihitung dari ruang yang tersisa di panggung, supaya kartu tidak keluar dari panggung.
                    val maxW = maxOf(WIDGET_W_MIN, minOf(WIDGET_W_MAX, ((stageW - anchorX - dp(4)) / k).toInt()))
                    val maxH = maxOf(WIDGET_H_MIN, minOf(WIDGET_H_MAX, ((stageH - anchorY - dp(4)) / k).toInt()))
                    val nw = (startW + ((e.rawX - downX) / k).toInt()) / 5 * 5
                    val nh = (startH + ((e.rawY - downY) / k).toInt()) / 5 * 5
                    auto = false
                    w = nw.coerceIn(WIDGET_W_MIN, maxW)
                    h = nh.coerceIn(WIDGET_H_MIN, maxH)
                    place()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    fitScale()
                    place()
                    true
                }
                else -> false
            }
        }

        fun chip(text: String, onClick: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = Ui.TEXT_SECONDARY
            bold()
            gravity = Gravity.CENTER
            setTextColor(Ui.TEXT)
            minHeight = dp(40)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = bg(Ui.CONTROL, capsule = true)
            isClickable = true
            setOnClickListener { onClick() }
            asButton()
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            chip(getString(R.string.widget_size_auto)) { setSize(autoW, autoH, true) },
            chip(getString(R.string.widget_size_small)) { setSize(200, 120, false) },
            chip(getString(R.string.widget_size_medium)) { setSize(300, 200, false) },
            chip(getString(R.string.widget_size_large)) { setSize(400, 340, false) }
        ).forEachIndexed { i, c ->
            chips.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(8) })
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }
        box.addView(note(getString(R.string.widget_size_preview_note)))
        box.addView(stage, LinearLayout.LayoutParams(MATCH, stageH).apply { topMargin = dp(14) })
        box.addTop(readout, 12)
        box.addTop(chips, 12)

        val listening = WidgetHost.acquire(this)
        // Widget yang belum pernah diperbarui bisa tampil kosong sampai penyedianya diminta mengirim ulang konten.
        WidgetHost.requestUpdate(this, id, info)
        box.postDelayed({ WidgetHost.requestUpdate(this, id, info) }, 1500)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.widget_size_dialog_title, label))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.save) { _, _ -> prefs.setWidgetSize(id, if (auto) null else w to h) }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { if (listening) WidgetHost.release(this) }
            .show()
    }

    /** Memilih widget yang ikonnya mau diganti. Tiap baris menunjukkan apakah ikonnya sudah kustom. */
    private fun pickWidgetForIcon() {
        val ids = WidgetHost.validIds(this, prefs.widgets)
        if (ids.isEmpty()) return
        val pm = packageManager
        val names = ids.map { id ->
            val label = WidgetHost.info(this, id)?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() } ?: id.toString()
            val state = getString(
                if (prefs.getWidgetIcon(id) != null) R.string.widget_icon_state_custom else R.string.widget_icon_state_default
            )
            "$label ($state)"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_icon_title)
            .setItems(names.toTypedArray()) { _, i -> showWidgetIconDialog(ids[i]) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Mengganti ikon satu widget di halaman Widget panel. Sumbernya tiga: ikon bawaan EdgeLite (set ikon akses
     * cepat), ikon aplikasi terpasang, dan ikon dari paket ikon pihak ketiga (satu paket dipilih lewat tombol di
     * tab Paket ikon). Tab Aplikasi dan Paket ikon punya kolom cari. Tombol "Pakai ikon bawaan widget" kembali ke
     * ikon penyedia widget. Pratinjau di atas menampilkan pilihan saat ini, dan baru disimpan saat Simpan diketuk.
     * Daftar aplikasi dan paket ikon dimuat di latar, jadi tabnya bisa kosong sebentar bila belum siap.
     */
    private fun showWidgetIconDialog(id: Int) {
        val info = WidgetHost.info(this, id) ?: return
        val pm = packageManager
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault("")
        val providerIcon = runCatching { info.loadIcon(this, resources.displayMetrics.densityDpi) }.getOrNull()
            ?: pm.defaultActivityIcon

        var choice: String? = prefs.getWidgetIcon(id)
        var tab = when {
            WidgetIcons.packFor(choice) != null -> 2
            WidgetIcons.appFor(choice) != null -> 1
            else -> 0
        }
        var apps: List<AppItem> = emptyList()
        var packs: List<IconPacks.Pack> = emptyList()
        var packPkg: String? = WidgetIcons.packFor(choice)?.first
        var packIcons: List<String> = emptyList()
        var query = ""
        val cellIcon = dp(48)
        val previewIcon = dp(64)

        // Menggambar satu pilihan ke ImageView: ikon bawaan di lingkaran seperti di panel, ikon aplikasi dan ikon
        // paket apa adanya, dan tanpa pilihan (atau sumbernya sudah dihapus) ikon dari penyedia widget.
        fun draw(iv: ImageView, c: String?, size: Int) {
            iv.clearColorFilter()
            iv.background = null
            iv.setPadding(0, 0, 0, 0)
            val builtin = WidgetIcons.builtinFor(c)
            if (builtin != null) {
                val pad = (size * 0.26f).toInt()
                iv.setImageResource(builtin.icon)
                iv.setPadding(pad, pad, pad, pad)
                iv.setColorFilter(Ui.TEXT)
                iv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Ui.OUTLINE)
                }
            } else {
                val custom = WidgetIcons.appFor(c)?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
                    ?: WidgetIcons.packFor(c)?.let { IconPacks.load(this, it.first, it.second) }
                iv.setImageDrawable(custom ?: providerIcon)
            }
        }

        fun cells(): List<IconCell> = when (tab) {
            0 -> WidgetIcons.builtin().map { IconCell(WidgetIcons.builtinChoice(it), getString(it.label)) }
            1 -> apps.filter { it.label.contains(query, ignoreCase = true) }
                .map { IconCell(WidgetIcons.appChoice(it.pkg), it.label) }
            else -> packPkg?.let { pk ->
                packIcons.filter { it.contains(query, ignoreCase = true) }
                    .map { IconCell(WidgetIcons.packChoice(pk, it), it) }
            } ?: emptyList()
        }

        var data = cells()
        val preview = ImageView(this)
        draw(preview, choice, previewIcon)
        preview.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        val grid = GridView(this)
        val iconAdapter = object : BaseAdapter() {
            override fun getCount(): Int = data.size
            override fun getItem(position: Int): Any = data[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cell = data[position]
                val holder = (convertView as? FrameLayout) ?: FrameLayout(this@MainActivity).apply {
                    layoutParams = AbsListView.LayoutParams(MATCH, dp(64))
                    addView(ImageView(this@MainActivity), FrameLayout.LayoutParams(cellIcon, cellIcon, Gravity.CENTER))
                }
                draw(holder.getChildAt(0) as ImageView, cell.choice, cellIcon)
                holder.background = if (cell.choice == choice) bg(Ui.CONTROL, 16) else null
                holder.contentDescription = cell.label
                holder.isSelected = cell.choice == choice
                return holder
            }
        }
        grid.apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(72)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(4)
            horizontalSpacing = dp(4)
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            this.adapter = iconAdapter
            setOnItemClickListener { _, _, position, _ ->
                choice = data[position].choice
                draw(preview, choice, previewIcon)
                iconAdapter.notifyDataSetChanged()
            }
        }

        // Kolom cari (tab Aplikasi dan Paket ikon) dan tombol pilih paket (tab Paket ikon saja).
        val search = EditText(this).apply {
            hint = getString(R.string.widget_icon_search)
            textSize = Ui.TEXT_BODY
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            isSingleLine = true
            minHeight = dp(Ui.CONTROL_HEIGHT_DP)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = bg(Ui.CONTROL, capsule = true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val packNote = note(getString(R.string.widget_icon_no_packs))
        lateinit var packButton: TextView

        fun refreshPackBar() {
            val name = packs.firstOrNull { it.pkg == packPkg }?.label
            packButton.text = name ?: getString(R.string.widget_icon_pack_choose)
            packButton.visibility = if (tab == 2 && packs.isNotEmpty()) View.VISIBLE else View.GONE
            packNote.visibility = if (tab == 2 && packs.isEmpty()) View.VISIBLE else View.GONE
            search.visibility = if (tab == 0 || (tab == 2 && packs.isEmpty())) View.GONE else View.VISIBLE
        }

        fun reload() {
            data = cells()
            iconAdapter.notifyDataSetChanged()
            grid.setSelection(0)
        }

        // Ikon paket dibaca di thread latar karena paket besar memuat ribuan entri.
        fun loadPack(pkg: String) {
            packPkg = pkg
            packIcons = emptyList()
            refreshPackBar()
            reload()
            val app = applicationContext
            Thread {
                val list = IconPacks.icons(app, pkg)
                runOnUiThread {
                    if (packPkg == pkg && !isFinishing && !isDestroyed) {
                        packIcons = list
                        reload()
                    }
                }
            }.start()
        }

        packButton = pill("", false) {
            if (packs.isEmpty()) return@pill
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.widget_icon_pack_title)
                .setSingleChoiceItems(
                    packs.map { it.label }.toTypedArray(), packs.indexOfFirst { it.pkg == packPkg }
                ) { d, i ->
                    d.dismiss()
                    loadPack(packs[i].pkg)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                reload()
            }
        })

        AppRepo.load(this) { list ->
            apps = list
            if (tab == 1) reload()
        }
        val app = applicationContext
        Thread {
            val found = runCatching { IconPacks.installed(app) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                packs = found
                if (packPkg != null && packs.none { it.pkg == packPkg }) packPkg = null
                if (packPkg == null && tab == 2) packPkg = packs.firstOrNull()?.pkg
                refreshPackBar()
                packPkg?.let { if (tab == 2) loadPack(it) }
            }
        }.start()

        val useDefault = pill(getString(R.string.widget_icon_use_default), false) {
            choice = null
            draw(preview, null, previewIcon)
            iconAdapter.notifyDataSetChanged()
        }
        val tabs = segmented(
            listOf(
                getString(R.string.widget_icon_tab_icons),
                getString(R.string.widget_icon_tab_apps),
                getString(R.string.widget_icon_tab_packs)
            ), tab
        ) { i ->
            tab = i
            query = ""
            search.setText("")
            if (i == 2 && packPkg == null) packPkg = packs.firstOrNull()?.pkg
            refreshPackBar()
            val pk = packPkg
            if (i == 2 && pk != null && packIcons.isEmpty()) loadPack(pk) else reload()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(preview, LinearLayout.LayoutParams(previewIcon, previewIcon))
        top.addView(useDefault, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })

        // Tinggi kisi mengikuti tinggi layar supaya dialog tetap muat di mode lanskap.
        val gridH = (resources.displayMetrics.heightPixels * 0.28f).toInt().coerceIn(dp(130), dp(260))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }
        box.addView(note(getString(R.string.widget_icon_note)))
        box.addTop(top, 14)
        box.addTop(tabs, 14)
        box.addTop(packButton, 10)
        box.addTop(packNote, 10)
        box.addTop(search, 10)
        box.addView(grid, LinearLayout.LayoutParams(MATCH, gridH).apply { topMargin = dp(12) })
        refreshPackBar()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.widget_icon_dialog_title, label))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.setWidgetIcon(id, choice)
                EdgeAccessibilityService.refresh(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun appLabelOf(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)

    /** Membaca daftar penyedia widget di thread latar (lambat), lalu menampilkan dialog pilihan. */
    private fun pickWidget() {
        val app = applicationContext
        Thread {
            val pm = app.packageManager
            val dpi = app.resources.displayMetrics.densityDpi
            val density = app.resources.displayMetrics.density
            val entries = runCatching {
                AppWidgetManager.getInstance(app).installedProviders
                    .filter { it.profile == Process.myUserHandle() }
                    .map { info ->
                        WidgetEntry(
                            info,
                            appLabelOf(info.provider.packageName),
                            info.loadLabel(pm).toString(),
                            runCatching { info.loadIcon(app, dpi) }.getOrNull(),
                            WidgetHost.cells(info.minWidth, density),
                            WidgetHost.cells(info.minHeight, density)
                        )
                    }
                    .sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
            }.getOrDefault(emptyList())
            runOnUiThread { if (!isFinishing && !isDestroyed) showWidgetPicker(entries) }
        }.start()
    }

    private fun showWidgetPicker(entries: List<WidgetEntry>) {
        if (entries.isEmpty()) {
            toast(R.string.widgets_pick_empty)
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(12))
        }
        var dlg: android.app.Dialog? = null
        var lastApp: String? = null
        var card: LinearLayout? = null
        for (e in entries) {
            if (e.app != lastApp) {
                box.addTop(caps(e.app), if (lastApp == null) 8 else 22)
                val c = sheetCard()
                card = c
                box.addTop(c, 10)
                lastApp = e.app
            }
            addWidgetRow(card!!, e) {
                dlg?.dismiss()
                addWidget(e.info)
            }
        }
        dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_pick_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addWidgetRow(card: LinearLayout, e: WidgetEntry, onClick: () -> Unit) {
        if (card.childCount > 0) {
            card.addView(View(this).apply { setBackgroundColor(Ui.OUTLINE) },
                LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Ui.CONTROL_HEIGHT_DP + 8)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rowRipple()
            isClickable = true
            asButton()
        }
        row.addView(ImageView(this).apply { setImageDrawable(e.icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = e.label
            textSize = Ui.TEXT_BODY
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = getString(R.string.widget_size, e.cols, e.rows)
            textSize = Ui.TEXT_CAPTION
            setTextColor(Ui.MUTED_HI)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
        row.setOnClickListener { onClick() }
        card.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /**
     * Menambah widget. Aplikasi biasa tidak boleh mengikat widget sendiri, jadi pertama kali Android
     * menampilkan dialog izin. Setelah itu, bila widget punya layar konfigurasi, layar itu dibuka.
     */
    @Suppress("DEPRECATION")
    private fun addWidget(info: AppWidgetProviderInfo) {
        val host = WidgetHost.get(this)
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            toast(R.string.widgets_add_failed)
            return
        }
        pendingWidgetId = id
        val dens = resources.displayMetrics.density
        val opts = WidgetHost.hostOptions(
            maxOf(1, (info.minWidth / dens).toInt()), maxOf(1, (info.minHeight / dens).toInt())
        )
        val bound = runCatching {
            AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, info.provider, opts)
        }.getOrDefault(false)
        if (bound) {
            configureOrFinish(id)
            return
        }
        val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, opts)
        runCatching { startActivityForResult(ask, REQ_WIDGET_BIND) }.onFailure { cancelPendingWidget(true) }
    }

    private fun configureOrFinish(id: Int) {
        val info = WidgetHost.info(this, id)
        if (info?.configure == null) {
            finishAddWidget(id)
            return
        }
        @Suppress("DEPRECATION")
        val started = runCatching {
            WidgetHost.get(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_WIDGET_CONFIG, null)
        }.isSuccess
        if (!started) {
            // Layar pengaturan beberapa widget menolak dibuka oleh aplikasi lain. Widget tetap ditambahkan dengan
            // pengaturan bawaannya daripada gagal total.
            toast(R.string.widgets_config_failed)
            finishAddWidget(id)
        }
    }

    private fun finishAddWidget(id: Int) {
        prefs.widgets = prefs.widgets + id
        pendingWidgetId = -1
        refreshWidgets()
        EdgeAccessibilityService.refresh(this)
    }

    /** Membatalkan penambahan: ID yang sudah dipesan dilepas lagi supaya tidak menumpuk di sistem. */
    private fun cancelPendingWidget(showError: Boolean) {
        val id = pendingWidgetId
        pendingWidgetId = -1
        if (id != -1) runCatching { WidgetHost.get(this).deleteAppWidgetId(id) }
        if (showError) toast(R.string.widgets_add_failed)
    }

    @Deprecated("Dipakai karena layar konfigurasi widget hanya melapor lewat onActivityResult.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_WIDGET_BIND && requestCode != REQ_WIDGET_CONFIG) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it != -1 } ?: pendingWidgetId
        if (id == -1 || id != pendingWidgetId) return
        if (resultCode != RESULT_OK) {
            cancelPendingWidget(false)
            return
        }
        if (requestCode == REQ_WIDGET_BIND) configureOrFinish(id) else finishAddWidget(id)
    }

    /** Daftar widget di panel. Hilangkan centang lalu simpan untuk menghapusnya. */
    private fun manageWidgets() {
        val ids = WidgetHost.validIds(this, prefs.widgets)
        if (ids.isEmpty()) return
        val pm = packageManager
        val names = ids.map { id ->
            WidgetHost.info(this, id)?.let {
                "${runCatching { it.loadLabel(pm).toString() }.getOrDefault("")} (${appLabelOf(it.provider.packageName)})"
            } ?: id.toString()
        }
        val keep = BooleanArray(ids.size) { true }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widgets_manage_title)
            .setMultiChoiceItems(names.toTypedArray(), keep) { _, i, checked -> keep[i] = checked }
            .setPositiveButton(R.string.save) { _, _ ->
                val host = WidgetHost.get(this)
                ids.forEachIndexed { i, id -> if (!keep[i]) runCatching { host.deleteAppWidgetId(id) } }
                prefs.widgets = ids.filterIndexed { i, _ -> keep[i] }
                prefs.pruneWidgetSizes(prefs.widgets)
                prefs.pruneWidgetIcons(prefs.widgets)
                refreshWidgets()
                EdgeAccessibilityService.refresh(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickApps() {
        AppRepo.load(this) { apps ->
            if (!isFinishing && !isDestroyed) showAppPicker(apps)
        }
    }

    private fun showAppPicker(apps: List<AppItem>) {
        val current = prefs.pinned.toMutableList()
        val checked = BooleanArray(apps.size) { apps[it].pkg in current }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_apps_title)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, isChecked ->
                val pkg = apps[i].pkg
                if (isChecked) {
                    if (pkg !in current) current.add(pkg)
                } else {
                    current.remove(pkg)
                }
            }
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.pinned = current
                renderPinned()
                EdgeAccessibilityService.refresh(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
