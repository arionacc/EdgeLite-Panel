package com.edgelite.panel

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Semua kontrol berbentuk kapsul memakai tinggi dan lengkung yang sama. */
private const val CAPSULE_RADIUS = 1000f
private const val CAPSULE_HEIGHT_DP = 48
private const val ROW_HEIGHT_DP = 64
private const val STATUS_WIDTH_DP = 68
private const val STATUS_HEIGHT_DP = 32

private const val SOURCE_URL = "https://github.com/arionacc/EdgeLite-Panel"

class MainActivity : AppCompatActivity() {

    private class Row(val view: View, val status: TextView)

    private lateinit var prefs: Prefs
    private lateinit var panelCaption: TextView
    private lateinit var pinnedCaption: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var orientContainer: LinearLayout
    private lateinit var accessRow: Row
    private lateinit var batteryRow: Row

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
        openCategories.add(getString(R.string.cat_general))
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

    private fun shape(fill: Int, stroke: Int = Ui.OUTLINE) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = CAPSULE_RADIUS
        setStroke(dp(1), stroke)
    }

    private fun ripple(fill: Int, stroke: Int = Ui.OUTLINE): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = CAPSULE_RADIUS
        }
        return RippleDrawable(ColorStateList.valueOf(0x33808080), shape(fill, stroke), mask)
    }

    private fun caps(t: String) = TextView(this).apply {
        text = t.uppercase()
        textSize = 11f
        bold()
        letterSpacing = 0.06f
        setTextColor(Ui.MUTED)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        textSize = 12.5f
        setLineSpacing(0f, 1.15f)
        setTextColor(Ui.MUTED)
    }

    private fun pill(t: String, light: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 14f
        bold()
        gravity = Gravity.CENTER
        minHeight = dp(CAPSULE_HEIGHT_DP)
        setPadding(dp(20), 0, dp(20), 0)
        setTextColor(if (light) Ui.ON_LIGHT else Ui.TEXT)
        background = if (light) ripple(Ui.LIGHT, Ui.LIGHT) else ripple(Ui.SURFACE)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun permRow(title: String, desc: String, onClick: () -> Unit): Row {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
            setPadding(dp(24), dp(10), dp(16), dp(10))
            background = ripple(Ui.SURFACE)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 14f
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Ui.MUTED)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val status = TextView(this).apply {
            textSize = 12f
            bold()
            gravity = Gravity.CENTER
        }
        row.addView(
            status,
            LinearLayout.LayoutParams(dp(STATUS_WIDTH_DP), dp(STATUS_HEIGHT_DP)).apply {
                marginStart = dp(12)
            }
        )
        return Row(row, status)
    }

    private fun setStatus(row: Row, active: Boolean, on: String = getString(R.string.status_active), off: String = getString(R.string.status_set)) {
        row.status.text = if (active) on else off
        row.status.setTextColor(if (active) Ui.ON_LIGHT else Ui.TEXT)
        row.status.background = shape(
            if (active) Ui.LIGHT else Ui.BG,
            if (active) Ui.LIGHT else Ui.OUTLINE
        )
    }

    /** Segmen kapsul berukuran sama yang membagi lebar baris secara rata. */
    private fun chipGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<TextView>()

        fun style(sel: Int) {
            chips.forEachIndexed { i, c ->
                val on = i == sel
                c.background = shape(if (on) Ui.LIGHT else Ui.SURFACE, if (on) Ui.LIGHT else Ui.OUTLINE)
                c.setTextColor(if (on) Ui.ON_LIGHT else Ui.MUTED)
            }
        }

        options.forEachIndexed { i, t ->
            val chip = TextView(this).apply {
                text = t
                textSize = 14f
                bold()
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    style(i)
                    onSelect(i)
                }
            }
            chips.add(chip)
            val lp = LinearLayout.LayoutParams(0, dp(CAPSULE_HEIGHT_DP), 1f)
            if (i < options.lastIndex) lp.marginEnd = dp(8)
            row.addView(chip, lp)
        }
        style(selected)
        return row
    }

    private fun slider(
        title: String,
        from: Float,
        to: Float,
        value: Float,
        unit: String,
        onValue: (Int) -> Unit
    ): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val name = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Ui.MUTED)
        }
        val valueTv = TextView(this).apply {
            text = "${value.toInt()}$unit"
            textSize = 13f
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
            thumbTintList = ColorStateList.valueOf(Ui.LIGHT)
            trackActiveTintList = ColorStateList.valueOf(Ui.LIGHT)
            trackInactiveTintList = ColorStateList.valueOf(Ui.OUTLINE)
            haloTintList = ColorStateList.valueOf(0x33E6E6E6)
            addOnChangeListener { _, v, fromUser ->
                valueTv.text = "${v.toInt()}$unit"
                if (fromUser) onValue(v.toInt())
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    EdgeAccessibilityService.refresh(this@MainActivity)
                }
            })
        }
        box.addView(head)
        box.addView(s)
        return box
    }

    /** Baris dengan pil Hidup atau Mati. Mengetuk baris membalik nilainya. */
    private fun toggleRow(title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        var state = initial
        lateinit var row: Row
        row = permRow(title, desc) {
            state = !state
            setStatus(row, state, getString(R.string.status_on), getString(R.string.status_off))
            onChange(state)
        }
        setStatus(row, state, getString(R.string.status_on), getString(R.string.status_off))
        return row.view
    }

    /** Kartu kategori yang bisa dilipat. Isinya dibangun oleh [build]. */
    private fun category(title: String, summary: String, build: (LinearLayout) -> Unit): View {
        val radius = dp(20).toFloat()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.PANEL)
                cornerRadius = radius
                setStroke(dp(1), Ui.OUTLINE)
            }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
            setPadding(dp(20), dp(10), dp(12), dp(10))
            background = RippleDrawable(
                ColorStateList.valueOf(0x33808080),
                null,
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = radius
                }
            )
            isClickable = true
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            bold()
            setTextColor(Ui.TEXT)
        })
        texts.addView(TextView(this).apply {
            text = summary
            textSize = 12f
            setTextColor(Ui.MUTED)
        })
        head.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val chevron = ImageView(this).apply { setImageResource(R.drawable.ic_expand) }
        head.addView(chevron, LinearLayout.LayoutParams(dp(32), dp(32)))
        card.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(18))
        }
        build(body)
        card.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))

        fun setOpen(open: Boolean, animate: Boolean) {
            body.visibility = if (open) View.VISIBLE else View.GONE
            val target = if (open) 180f else 0f
            if (animate) chevron.animate().rotation(target).setDuration(160).start()
            else chevron.rotation = target
        }
        setOpen(title in openCategories, false)
        head.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            if (open) openCategories.add(title) else openCategories.remove(title)
            setOpen(open, true)
        }
        return card
    }

    // ------------------------------------------------------------------ Susunan layar

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
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
            textSize = 28f
            bold()
            setTextColor(Ui.TEXT)
        })
        titles.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 12f
            bold()
            setTextColor(Ui.MUTED)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        val more = ImageView(this).apply {
            setImageResource(R.drawable.ic_more)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showMenu(it) }
        }
        header.addView(more, LinearLayout.LayoutParams(dp(40), dp(40)))
        add(header)

        // Tombol utama
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            pill(getString(R.string.enable_panel), true) { enablePanel() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) }
        )
        actions.addView(
            pill(getString(R.string.disable_panel), false) {
                EdgeAccessibilityService.stop(this)
                refreshStatus()
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(4) }
        )
        add(actions, 20)

        panelCaption = caps("")
        add(panelCaption, 22)

        // Izin
        add(caps(getString(R.string.section_permissions)), 26)
        accessRow = permRow(getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc)) {
            openAccessibilitySettings()
        }
        add(accessRow.view, 10)
        batteryRow = permRow(getString(R.string.perm_battery), getString(R.string.perm_battery_desc)) {
            requestBatteryExemption()
        }
        add(batteryRow.view, 8)

        // Aplikasi panel
        pinnedCaption = caps("")
        add(pinnedCaption, 26)
        pinnedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(pinnedRow)
        }
        add(scroller, 12)
        add(pill(getString(R.string.pick_apps), false) { pickApps() }, 14)

        // Kustomisasi, dikelompokkan per kategori
        add(caps(getString(R.string.section_customize)), 26)

        add(category(getString(R.string.cat_general), getString(R.string.cat_general_sum)) { b ->
            b.addTop(caps(getString(R.string.default_open_mode)), 6)
            b.addTop(
                chipGroup(listOf(getString(R.string.mode_window), getString(R.string.mode_full)), if (prefs.mode == LaunchMode.WINDOW) 0 else 1) {
                    prefs.mode = if (it == 0) LaunchMode.WINDOW else LaunchMode.FULL
                }, 10
            )
            b.addTop(caps(getString(R.string.section_on_boot)), 22)
            b.addTop(
                toggleRow(
                    getString(R.string.autostart),
                    getString(R.string.autostart_desc),
                    prefs.autostart
                ) { prefs.autostart = it }, 10
            )
            b.addTop(note(getString(R.string.autostart_note)), 10)
        }, 10)

        add(category(getString(R.string.cat_display), getString(R.string.cat_display_sum)) { b ->
            b.addTop(caps(getString(R.string.app_names)), 6)
            b.addTop(
                chipGroup(listOf(getString(R.string.show_names), getString(R.string.hide_names)), if (prefs.showLabels) 0 else 1) {
                    prefs.showLabels = it == 0
                    EdgeAccessibilityService.refresh(this)
                }, 10
            )
            b.addTop(slider(getString(R.string.icon_size), 32f, 64f, prefs.iconSizeDp.toFloat(), " dp") {
                prefs.iconSizeDp = it
            }, 16)
            b.addTop(slider(getString(R.string.panel_opacity), 50f, 100f, prefs.panelOpacity.toFloat(), " %") {
                prefs.panelOpacity = it
            }, 8)
            b.addTop(slider(getString(R.string.corner_radius), 0f, 40f, prefs.cornerDp.toFloat(), " dp") {
                prefs.cornerDp = it
            }, 8)
        }, 10)

        // Pengaturan per orientasi
        add(caps(getString(R.string.section_per_orient)), 26)
        add(
            chipGroup(listOf(getString(R.string.portrait), getString(R.string.landscape)), if (editOrient == Orient.PORTRAIT) 0 else 1) {
                editOrient = if (it == 0) Orient.PORTRAIT else Orient.LANDSCAPE
                renderOrientSettings()
            }, 10
        )
        orientContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(orientContainer, 14)
        renderOrientSettings()

        // Cadangan pengaturan
        add(caps(getString(R.string.section_backup)), 26)
        add(category(getString(R.string.cat_backup), getString(R.string.cat_backup_sum)) { b ->
            b.addTop(
                note(getString(R.string.backup_note)), 6
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
            b.addTop(row, 14)
        }, 10)

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
        }
    }

    /** Membangun ulang kategori yang bergantung pada orientasi (potret atau lanskap). */
    private fun renderOrientSettings() {
        val o = editOrient
        val c = orientContainer
        c.removeAllViews()

        c.addTop(
            note(
                if (o == Orient.PORTRAIT) getString(R.string.orient_note_portrait)
                else getString(R.string.orient_note_landscape)
            ), 0
        )

        c.addTop(category(getString(R.string.cat_side), getString(R.string.cat_side_sum)) { b ->
            b.addTop(caps(getString(R.string.screen_side)), 6)
            b.addTop(
                chipGroup(listOf(getString(R.string.left), getString(R.string.right)), if (prefs.getSide(o) == EdgeSide.LEFT) 0 else 1) {
                    prefs.setSide(o, if (it == 0) EdgeSide.LEFT else EdgeSide.RIGHT)
                    EdgeAccessibilityService.refresh(this)
                }, 10
            )
            b.addTop(caps(getString(R.string.handle)), 22)
            b.addTop(slider(getString(R.string.handle_height), 60f, 240f, prefs.getHandleHeight(o).toFloat(), " dp") {
                prefs.setHandleHeight(o, it)
            }, 10)
            b.addTop(slider(getString(R.string.handle_position), 10f, 90f, prefs.getHandleOffset(o).toFloat(), " %") {
                prefs.setHandleOffset(o, it)
            }, 8)
        }, 12)

        c.addTop(category(getString(R.string.cat_panel_size), getString(R.string.cat_panel_size_sum)) { b ->
            b.addTop(slider(getString(R.string.panel_height), 30f, 95f, prefs.getPanelH(o).toFloat(), " %") {
                prefs.setPanelH(o, it)
            }, 6)
            b.addTop(slider(getString(R.string.panel_width), 64f, 140f, prefs.getPanelW(o).toFloat(), " dp") {
                prefs.setPanelW(o, it)
            }, 8)
        }, 10)

        c.addTop(category(getString(R.string.cat_window), getString(R.string.cat_window_sum)) { b ->
            val preview = WindowPreview(this).apply { update(prefs.getWinW(o), prefs.getWinH(o), o) }
            b.addTop(preview, 8)
            b.addTop(slider(getString(R.string.window_width), 30f, 100f, prefs.getWinW(o).toFloat(), " %") {
                prefs.setWinW(o, it)
                preview.update(it, prefs.getWinH(o), o)
            }, 8)
            b.addTop(slider(getString(R.string.window_height), 30f, 100f, prefs.getWinH(o).toFloat(), " %") {
                prefs.setWinH(o, it)
                preview.update(prefs.getWinW(o), it, o)
            }, 8)
        }, 10)
    }

    // ------------------------------------------------------------------ Aksi

    private fun refreshStatus() {
        val accessOn = EdgeAccessibilityService.isEnabledInSystem(this)
        setStatus(accessRow, accessOn)
        val pm = getSystemService(PowerManager::class.java)
        setStatus(batteryRow, pm.isIgnoringBatteryOptimizations(packageName))
        panelCaption.text = getString(if (prefs.enabled && accessOn) R.string.panel_on else R.string.panel_off)
        renderPinned()
    }

    private fun renderPinned() {
        val pm = packageManager
        val pkgs = prefs.pinned.filter { pm.getLaunchIntentForPackage(it) != null }
        pinnedCaption.text = getString(R.string.pinned_caption, pkgs.size)
        pinnedRow.removeAllViews()
        if (pkgs.isEmpty()) {
            pinnedRow.addView(note(getString(R.string.pinned_empty)))
            return
        }
        pkgs.forEach { pkg ->
            val icon = ImageView(this).apply {
                setImageDrawable(runCatching { pm.getApplicationIcon(pkg) }.getOrElse { pm.defaultActivityIcon })
            }
            pinnedRow.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
        }
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
        box.addTop(infoRow(getString(R.string.about_license), "MIT"), 10)
        box.addTop(note(SOURCE_URL), 14)
        box.addTop(pill(getString(R.string.about_open_source), false) { openSourceCode() }, 14)
        box.addTop(caps(getString(R.string.about_disclaimer_title)), 22)
        box.addTop(
            note(getString(R.string.about_disclaimer)), 8
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openSourceCode() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
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
