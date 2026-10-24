package com.edgelite.panel

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    }

    // ------------------------------------------------------------------ Helper tampilan

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

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

    private fun tutorialItem(box: LinearLayout, @StringRes title: Int, @StringRes body: Int, top: Int) {
        box.addTop(TextView(this).apply {
            text = getString(title)
            textSize = 14f
            bold()
            setTextColor(Ui.TEXT)
        }, top)
        box.addTop(TextView(this).apply {
            text = getString(body)
            textSize = 13f
            setLineSpacing(0f, 1.15f)
            setTextColor(Ui.MUTED)
        }, 4)
    }

    private fun showTutorial() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        box.addTop(caps(getString(R.string.tut_sec_start)), 0)
        tutorialItem(box, R.string.tut_start_1_t, R.string.tut_start_1_b, 12)
        tutorialItem(box, R.string.tut_start_2_t, R.string.tut_start_2_b, 12)
        tutorialItem(box, R.string.tut_start_3_t, R.string.tut_start_3_b, 12)
        tutorialItem(box, R.string.tut_start_4_t, R.string.tut_start_4_b, 12)

        box.addTop(caps(getString(R.string.tut_sec_a11y)), 26)
        tutorialItem(box, R.string.tut_a11y_why_t, R.string.tut_a11y_why_b, 12)
        tutorialItem(box, R.string.tut_a11y_does_t, R.string.tut_a11y_does_b, 14)
        tutorialItem(box, R.string.tut_a11y_enable_t, R.string.tut_a11y_enable_b, 14)
        tutorialItem(box, R.string.tut_a11y_disable_t, R.string.tut_a11y_disable_b, 14)

        box.addTop(caps(getString(R.string.tut_sec_update)), 26)
        tutorialItem(box, R.string.tut_update_keep_t, R.string.tut_update_keep_b, 12)

        box.addTop(caps(getString(R.string.tut_sec_freeform)), 26)
        tutorialItem(box, R.string.tut_freeform_enable_t, R.string.tut_freeform_enable_b, 12)

        box.addTop(caps(getString(R.string.tut_sec_trouble)), 26)
        tutorialItem(box, R.string.tr_gray_t, R.string.tr_gray_b, 12)
        tutorialItem(box, R.string.tr_reboot_t, R.string.tr_reboot_b, 14)
        tutorialItem(box, R.string.tr_dies_t, R.string.tr_dies_b, 14)
        tutorialItem(box, R.string.tr_fullscreen_t, R.string.tr_fullscreen_b, 14)
        tutorialItem(box, R.string.tr_gesture_t, R.string.tr_gesture_b, 14)
        tutorialItem(box, R.string.tr_apply_t, R.string.tr_apply_b, 14)
        tutorialItem(box, R.string.tr_missing_t, R.string.tr_missing_b, 14)
        tutorialItem(box, R.string.tr_nohandle_t, R.string.tr_nohandle_b, 14)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tut_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun infoRow(label: String, value: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Ui.MUTED)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            bold()
            setTextColor(Ui.TEXT)
        })
        return row
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
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }
        box.addTop(note(getString(R.string.about_summary)), 0)
        box.addTop(infoRow(getString(R.string.about_author), "Arion"), 20)
        box.addTop(infoRow(getString(R.string.about_version), appVersionName()), 10)
        box.addTop(infoRow(getString(R.string.about_license), "GPL-3.0"), 10)
        box.addTop(note(SOURCE_URL), 14)
        box.addTop(pill(getString(R.string.about_open_source), false) { openSourceCode() }, 14)
        box.addTop(pill(getString(R.string.about_view_license), false) { openLink("$SOURCE_URL/blob/main/LICENSE") }, 10)
        box.addTop(caps(getString(R.string.about_disclaimer_title)), 22)
        box.addTop(
            note(getString(R.string.about_disclaimer)), 8
        )
        box.addTop(
            note(getString(R.string.about_compat_warning)), 14
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
        val apps = AppRepo.launchable(this)
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

    private fun pickApps() {
        val apps = AppRepo.launchable(this)
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
