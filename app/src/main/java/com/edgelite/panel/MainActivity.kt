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
    private val openCategories = mutableSetOf("Umum")

    /** Orientasi yang sedang diedit di halaman pengaturan. */
    private var editOrient = Orient.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dm = resources.displayMetrics
        editOrient = orientOf(dm.widthPixels, dm.heightPixels)
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

    private fun setStatus(row: Row, active: Boolean, on: String = "Aktif", off: String = "Atur") {
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
            setStatus(row, state, "Hidup", "Mati")
            onChange(state)
        }
        setStatus(row, state, "Hidup", "Mati")
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
            text = "Panel tepi alternatif untuk One UI"
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
            pill("Aktifkan panel", true) { enablePanel() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) }
        )
        actions.addView(
            pill("Matikan", false) {
                EdgeAccessibilityService.stop(this)
                refreshStatus()
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(4) }
        )
        add(actions, 20)

        panelCaption = caps("")
        add(panelCaption, 22)

        // Izin
        add(caps("Izin"), 26)
        accessRow = permRow("Layanan aksesibilitas", "Wajib untuk menampilkan handle dan panel") {
            openAccessibilitySettings()
        }
        add(accessRow.view, 10)
        batteryRow = permRow("Abaikan optimasi baterai", "Agar panel tidak dimatikan sistem") {
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
        add(pill("Pilih aplikasi", false) { pickApps() }, 14)

        // Kustomisasi, dikelompokkan per kategori
        add(caps("Kustomisasi"), 26)

        add(category("Umum", "Mode buka dan mulai otomatis") { b ->
            b.addTop(caps("Mode buka default"), 6)
            b.addTop(
                chipGroup(listOf("Jendela", "Penuh"), if (prefs.mode == LaunchMode.WINDOW) 0 else 1) {
                    prefs.mode = if (it == 0) LaunchMode.WINDOW else LaunchMode.FULL
                }, 10
            )
            b.addTop(caps("Saat ponsel menyala"), 22)
            b.addTop(
                toggleRow(
                    "Mulai otomatis",
                    "Panel menyala lagi sendiri setelah ponsel restart",
                    prefs.autostart
                ) { prefs.autostart = it }, 10
            )
            b.addTop(note("Berlaku untuk panel yang aktif sebelum restart. Sistem menyalakan layanan aksesibilitas sendiri saat ponsel selesai menyala."), 10)
        }, 10)

        add(category("Tampilan panel", "Nama, ikon, kepekatan, sudut") { b ->
            b.addTop(caps("Nama aplikasi"), 6)
            b.addTop(
                chipGroup(listOf("Tampilkan nama", "Sembunyikan nama"), if (prefs.showLabels) 0 else 1) {
                    prefs.showLabels = it == 0
                    EdgeAccessibilityService.refresh(this)
                }, 10
            )
            b.addTop(slider("Ukuran ikon", 32f, 64f, prefs.iconSizeDp.toFloat(), " dp") {
                prefs.iconSizeDp = it
            }, 16)
            b.addTop(slider("Kepekatan panel", 50f, 100f, prefs.panelOpacity.toFloat(), " %") {
                prefs.panelOpacity = it
            }, 8)
            b.addTop(slider("Lengkung sudut", 0f, 40f, prefs.cornerDp.toFloat(), " dp") {
                prefs.cornerDp = it
            }, 8)
        }, 10)

        // Pengaturan per orientasi
        add(caps("Pengaturan per orientasi"), 26)
        add(
            chipGroup(listOf("Potret", "Lanskap"), if (editOrient == Orient.PORTRAIT) 0 else 1) {
                editOrient = if (it == 0) Orient.PORTRAIT else Orient.LANDSCAPE
                renderOrientSettings()
            }, 10
        )
        orientContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(orientContainer, 14)
        renderOrientSettings()

        // Cadangan pengaturan
        add(caps("Cadangan"), 26)
        add(category("Ekspor dan impor", "Simpan pengaturan sebelum memperbarui") { b ->
            b.addTop(
                note(
                    "Aplikasi ini belum memakai tanda tangan tetap, jadi memperbarui berarti mencopot " +
                        "versi lama dan semua pengaturan ikut hilang. Ekspor dulu sebelum mencopot, " +
                        "lalu impor setelah versi baru terpasang."
                ), 6
            )
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                pill("Ekspor", false) { exportLauncher.launch("EdgeLite-pengaturan.json") },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) }
            )
            row.addView(
                pill("Impor", false) { importLauncher.launch(arrayOf("*/*")) },
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
                if (o == Orient.PORTRAIT) "Berlaku saat ponsel dalam posisi potret."
                else "Berlaku saat ponsel dalam posisi lanskap."
            ), 0
        )

        c.addTop(category("Sisi dan handle", "Sisi layar, tinggi dan posisi handle") { b ->
            b.addTop(caps("Sisi layar"), 6)
            b.addTop(
                chipGroup(listOf("Kiri", "Kanan"), if (prefs.getSide(o) == EdgeSide.LEFT) 0 else 1) {
                    prefs.setSide(o, if (it == 0) EdgeSide.LEFT else EdgeSide.RIGHT)
                    EdgeAccessibilityService.refresh(this)
                }, 10
            )
            b.addTop(caps("Handle"), 22)
            b.addTop(slider("Tinggi handle", 60f, 240f, prefs.getHandleHeight(o).toFloat(), " dp") {
                prefs.setHandleHeight(o, it)
            }, 10)
            b.addTop(slider("Posisi handle dari atas", 10f, 90f, prefs.getHandleOffset(o).toFloat(), " %") {
                prefs.setHandleOffset(o, it)
            }, 8)
        }, 12)

        c.addTop(category("Ukuran panel", "Tinggi dan lebar panel") { b ->
            b.addTop(slider("Tinggi panel", 30f, 95f, prefs.getPanelH(o).toFloat(), " %") {
                prefs.setPanelH(o, it)
            }, 6)
            b.addTop(slider("Lebar panel", 64f, 140f, prefs.getPanelW(o).toFloat(), " dp") {
                prefs.setPanelW(o, it)
            }, 8)
        }, 10)

        c.addTop(category("Jendela mengambang", "Pratinjau, lebar dan tinggi jendela") { b ->
            val preview = WindowPreview(this).apply { update(prefs.getWinW(o), prefs.getWinH(o), o) }
            b.addTop(preview, 8)
            b.addTop(slider("Lebar jendela", 30f, 100f, prefs.getWinW(o).toFloat(), " %") {
                prefs.setWinW(o, it)
                preview.update(it, prefs.getWinH(o), o)
            }, 8)
            b.addTop(slider("Tinggi jendela", 30f, 100f, prefs.getWinH(o).toFloat(), " %") {
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
        panelCaption.text = if (prefs.enabled && accessOn) "PANEL (AKTIF)" else "PANEL (MATI)"
        renderPinned()
    }

    private fun renderPinned() {
        val pm = packageManager
        val pkgs = prefs.pinned.filter { pm.getLaunchIntentForPackage(it) != null }
        pinnedCaption.text = "APLIKASI PANEL (${pkgs.size})"
        pinnedRow.removeAllViews()
        if (pkgs.isEmpty()) {
            pinnedRow.addView(note("Belum ada aplikasi. Ketuk Pilih aplikasi untuk menambahkan."))
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
            menu.add(0, 0, 0, "Tutorial")
            menu.add(0, 1, 1, "Opsi Pengembang")
            menu.add(0, 3, 2, "Tentang")
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

    private fun tutorialItem(box: LinearLayout, title: String, body: String, top: Int) {
        box.addTop(TextView(this).apply {
            text = title
            textSize = 14f
            bold()
            setTextColor(Ui.TEXT)
        }, top)
        box.addTop(TextView(this).apply {
            text = body
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

        box.addTop(caps("Memulai"), 0)
        tutorialItem(box, "1. Nyalakan layanan",
            "Ketuk Aktifkan panel. Pengaturan Aksesibilitas akan terbuka. Pilih Aplikasi terinstal, " +
                "ketuk EdgeLite Panel, lalu nyalakan. Samsung menampilkan peringatan standar untuk semua " +
                "layanan aksesibilitas. Layanan ini tidak membaca isi layar.", 12)
        tutorialItem(box, "2. Pilih aplikasi",
            "Ketuk Pilih aplikasi, centang aplikasi yang ingin muncul di panel, lalu Simpan.", 12)
        tutorialItem(box, "3. Buka panel",
            "Geser handle di tepi layar ke dalam, atau ketuk handle. Ketuk ikon untuk membuka aplikasinya.", 12)
        tutorialItem(box, "4. Opsional, jaga dari penghemat baterai",
            "Ketuk baris Abaikan optimasi baterai sampai statusnya Aktif.", 12)

        box.addTop(caps("Layanan aksesibilitas"), 26)
        tutorialItem(box, "Kenapa dibutuhkan",
            "Android hanya mengizinkan aplikasi biasa menampilkan handle di atas aplikasi lain lewat " +
                "foreground service dengan notifikasi. Dengan layanan aksesibilitas, handle dan panel " +
                "tampil tanpa notifikasi dan tidak muncul di Periksa aktivitas latar belakang, seperti " +
                "fitur bawaan Samsung.", 12)
        tutorialItem(box, "Yang dilakukan dan tidak dilakukan",
            "EdgeLite hanya menggambar handle dan panel. Layanan ini tidak membaca isi layar, tidak " +
                "menerima event apa pun, dan tidak mengumpulkan data. Peringatan yang muncul saat " +
                "menyalakannya adalah peringatan standar Android untuk semua layanan aksesibilitas.", 14)
        tutorialItem(box, "Cara menyalakan",
            "Buka Pengaturan, Aksesibilitas, Aplikasi terinstal, ketuk EdgeLite Panel, nyalakan " +
                "tombolnya, lalu ketuk Izinkan. Jalan pintasnya: ketuk baris Layanan aksesibilitas di " +
                "halaman utama EdgeLite, atau tombol Aktifkan panel. Nama menu bisa sedikit berbeda " +
                "di tiap versi One UI.", 14)
        tutorialItem(box, "Cara mematikan",
            "Ketuk Matikan di EdgeLite untuk menyembunyikan handle saja. Untuk menonaktifkan layanannya " +
                "sepenuhnya, matikan tombol EdgeLite Panel di Aksesibilitas, Aplikasi terinstal. Bila " +
                "tombol itu abu-abu saat dinyalakan, lihat solusi di bagian Masalah dan solusi.", 14)

        box.addTop(caps("Memperbarui aplikasi"), 26)
        tutorialItem(box, "Menjaga pengaturan saat update",
            "Buka kategori Ekspor dan impor, ketuk Ekspor, lalu simpan berkasnya. Copot versi lama, pasang " +
                "versi baru, lalu ketuk Impor dan pilih berkas tadi. Nyalakan lagi layanan EdgeLite Panel " +
                "di Aksesibilitas karena pencopotan ikut mematikannya. Aplikasi panel yang belum terpasang " +
                "di ponsel dilewati.", 12)

        box.addTop(caps("Jendela mengambang"), 26)
        tutorialItem(box, "Mengaktifkan freeform di One UI",
            "Buka menu titik tiga, pilih Opsi Pengembang, lalu nyalakan Aktifkan jendela freeform dan " +
                "Paksa aktivitas dapat diubah ukurannya. Restart ponsel bila jendela belum berubah. " +
                "Opsi Pengembang yang belum tampil dibuka dengan mengetuk Nomor build 7 kali di " +
                "Pengaturan, Tentang ponsel, Informasi perangkat lunak.", 12)

        box.addTop(caps("Masalah dan solusi"), 26)
        tutorialItem(box, "Tombol layanan aksesibilitas abu-abu dan tidak bisa dinyalakan",
            "Android 13 ke atas membatasi aplikasi yang dipasang dari berkas APK. Buka Pengaturan, " +
                "Aplikasi, EdgeLite Panel, ketuk titik tiga di kanan atas, pilih Izinkan pengaturan " +
                "yang dibatasi, lalu coba nyalakan lagi. Bila menu itu belum muncul, ketuk tombol " +
                "layanan sekali lalu kembali dan cek lagi.", 12)
        tutorialItem(box, "Panel tidak menyala setelah restart",
            "Pastikan layanan EdgeLite Panel masih menyala di Aksesibilitas, Mulai otomatis di kategori " +
                "Umum menyala, dan label PANEL tertulis AKTIF sebelum ponsel direstart. Panel muncul " +
                "setelah ponsel selesai menyala dan dibuka kunci. Bila panel dimatikan lewat tombol " +
                "Matikan atau tile sebelum restart, panel memang tetap mati.", 14)
        tutorialItem(box, "Layanan aksesibilitas mati sendiri",
            "Jangan pakai Paksa berhenti di info aplikasi, karena Android lalu mematikan layanannya. " +
                "Aktifkan Abaikan optimasi baterai. Di Samsung, buka Pengaturan, Baterai, Batas " +
                "penggunaan di latar belakang, lalu keluarkan EdgeLite dari Aplikasi tidur dan masukkan " +
                "ke Aplikasi yang tidak pernah tidur. Nama menu bisa berbeda di tiap versi One UI.", 14)
        tutorialItem(box, "Aplikasi terbuka layar penuh, bukan jendela",
            "Cek Mode buka default di kategori Umum sudah Jendela. Pastikan dua opsi freeform di Opsi " +
                "Pengembang menyala. Sebagian aplikasi tidak mendukung perubahan ukuran dan akan mengabaikan " +
                "ukuran jendela, itu bukan kesalahan EdgeLite.", 14)
        tutorialItem(box, "Handle bentrok dengan gestur kembali",
            "Geser posisi handle ke atas atau ke bawah di kategori Sisi dan handle, atau pindahkan ke sisi " +
                "lain. Kamu juga bisa mengurangi sensitivitas gestur kembali di pengaturan navigasi Samsung " +
                "bila opsinya tersedia.", 14)
        tutorialItem(box, "Pengaturan tidak langsung berubah",
            "Slider menerapkan nilai saat jari dilepas dan terlihat setelah panel dibuka ulang. Bila masih " +
                "sama, ketuk Matikan lalu Aktifkan panel.", 14)
        tutorialItem(box, "Aplikasi tidak muncul di panel",
            "Buka Pilih aplikasi dan pastikan aplikasinya tercentang. Aplikasi yang sudah dihapus dari " +
                "ponsel otomatis dilewati.", 14)
        tutorialItem(box, "Tombol Aktifkan panel tidak memunculkan handle",
            "Periksa apakah layanan EdgeLite Panel sudah menyala di Aksesibilitas. Tanpa itu panel tidak " +
                "bisa ditampilkan.", 14)

        MaterialAlertDialogBuilder(this)
            .setTitle("Tutorial")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Tutup", null)
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
                ?: throw IllegalStateException("Tidak bisa menulis berkas")
            toast("Pengaturan disimpan")
        } catch (e: Exception) {
            toast("Gagal menyimpan pengaturan")
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
            toast("Berkas bukan hasil ekspor EdgeLite")
            return
        }
        setContentView(buildUi())
        EdgeAccessibilityService.refresh(this)
        refreshStatus()
        val missing = prefs.pinned.count { packageManager.getLaunchIntentForPackage(it) == null }
        toast(
            if (missing > 0) "Pengaturan diimpor. $missing aplikasi panel belum terpasang di ponsel ini"
            else "Pengaturan diimpor"
        )
    }

    private fun showAbout() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }
        box.addTop(note("Panel tepi alternatif untuk ponsel Samsung One UI."), 0)
        box.addTop(infoRow("Pembuat", "Arion"), 20)
        box.addTop(infoRow("Versi", "1.0.0"), 10)
        box.addTop(infoRow("Lisensi", "MIT"), 10)
        box.addTop(note(SOURCE_URL), 14)
        box.addTop(pill("Buka source code", false) { openSourceCode() }, 14)
        box.addTop(caps("Pernyataan"), 22)
        box.addTop(
            note(
                "EdgeLite adalah proyek independen dan tidak terafiliasi dengan, didukung oleh, " +
                    "atau disponsori oleh Samsung Electronics. Samsung, One UI, dan Edge Panel adalah " +
                    "merek dagang milik pemiliknya masing-masing, disebut di sini hanya untuk " +
                    "menjelaskan kompatibilitas."
            ), 8
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Tentang EdgeLite")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun openSourceCode() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
        } catch (e: ActivityNotFoundException) {
            toast("Tidak ada aplikasi untuk membuka tautan")
        }
    }

    private fun openAccessibilitySettings() {
        toast("Pilih Aplikasi terinstal, lalu EdgeLite Panel, lalu nyalakan")
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast("Tidak bisa membuka pengaturan aksesibilitas")
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
            toast("Opsi Pengembang belum aktif. Ketuk Nomor build 7 kali di Pengaturan, Tentang ponsel.")
        }
    }

    private fun pickApps() {
        val apps = AppRepo.launchable(this)
        val current = prefs.pinned.toMutableList()
        val checked = BooleanArray(apps.size) { apps[it].pkg in current }

        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih aplikasi panel")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, isChecked ->
                val pkg = apps[i].pkg
                if (isChecked) {
                    if (pkg !in current) current.add(pkg)
                } else {
                    current.remove(pkg)
                }
            }
            .setPositiveButton("Simpan") { _, _ ->
                prefs.pinned = current
                renderPinned()
                EdgeAccessibilityService.refresh(this)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
