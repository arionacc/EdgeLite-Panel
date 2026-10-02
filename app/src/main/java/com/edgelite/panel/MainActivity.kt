package com.edgelite.panel

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
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

class MainActivity : AppCompatActivity() {

    private class Row(val view: View, val status: TextView)

    private lateinit var prefs: Prefs
    private lateinit var panelCaption: TextView
    private lateinit var pinnedCaption: TextView
    private lateinit var pinnedRow: LinearLayout
    private lateinit var orientContainer: LinearLayout
    private lateinit var overlayRow: Row
    private lateinit var batteryRow: Row

    /** Orientasi yang sedang diedit di halaman pengaturan. */
    private var editOrient = Orient.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dm = resources.displayMetrics
        editOrient = orientOf(dm.widthPixels, dm.heightPixels)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
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

    private fun setStatus(row: Row, active: Boolean) {
        row.status.text = if (active) "Aktif" else "Atur"
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
                    EdgeService.refresh(this@MainActivity)
                }
            })
        }
        box.addView(head)
        box.addView(s)
        return box
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
                EdgeService.stop(this)
                refreshStatus()
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(4) }
        )
        add(actions, 20)

        panelCaption = caps("")
        add(panelCaption, 22)

        // Izin
        add(caps("Izin"), 26)
        overlayRow = permRow("Tampil di atas aplikasi lain", "Wajib untuk handle dan panel") {
            openOverlaySettings()
        }
        add(overlayRow.view, 10)
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

        // Mode default (berlaku untuk semua orientasi)
        add(caps("Mode buka default"), 26)
        add(
            chipGroup(listOf("Jendela", "Penuh"), if (prefs.mode == LaunchMode.WINDOW) 0 else 1) {
                prefs.mode = if (it == 0) LaunchMode.WINDOW else LaunchMode.FULL
            }, 10
        )

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

        // Catatan
        add(caps("Catatan"), 26)
        add(
            note(
                "Jendela mengambang memakai fitur freeform Android. Di One UI, aktifkan " +
                    "\"Aktifkan jendela freeform\" dan \"Paksa aktivitas dapat diubah ukurannya\" " +
                    "lewat menu titik tiga di kanan atas, pilih Opsi Pengembang. " +
                    "Aplikasi yang tidak mendukung perubahan ukuran bisa mengabaikan ukuran jendela, " +
                    "dan jika gagal, aplikasi dibuka layar penuh."
            ), 10
        )

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
        }
    }

    /** Membangun ulang bagian pengaturan yang bergantung pada orientasi (potret atau lanskap). */
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

        c.addTop(caps("Sisi layar"), 20)
        c.addTop(
            chipGroup(listOf("Kiri", "Kanan"), if (prefs.getSide(o) == EdgeSide.LEFT) 0 else 1) {
                prefs.setSide(o, if (it == 0) EdgeSide.LEFT else EdgeSide.RIGHT)
                EdgeService.refresh(this)
            }, 10
        )

        c.addTop(caps("Jendela mengambang"), 26)
        val preview = WindowPreview(this).apply { update(prefs.getWinW(o), prefs.getWinH(o), o) }
        c.addTop(preview, 12)
        c.addTop(slider("Lebar jendela", 30f, 100f, prefs.getWinW(o).toFloat(), " %") {
            prefs.setWinW(o, it)
            preview.update(it, prefs.getWinH(o), o)
        }, 8)
        c.addTop(slider("Tinggi jendela", 30f, 100f, prefs.getWinH(o).toFloat(), " %") {
            prefs.setWinH(o, it)
            preview.update(prefs.getWinW(o), it, o)
        }, 8)

        c.addTop(caps("Handle"), 26)
        c.addTop(slider("Tinggi handle", 60f, 240f, prefs.getHandleHeight(o).toFloat(), " dp") {
            prefs.setHandleHeight(o, it)
        }, 10)
        c.addTop(slider("Posisi handle dari atas", 10f, 90f, prefs.getHandleOffset(o).toFloat(), " %") {
            prefs.setHandleOffset(o, it)
        }, 8)
    }

    // ------------------------------------------------------------------ Aksi

    private fun refreshStatus() {
        setStatus(overlayRow, Settings.canDrawOverlays(this))
        val pm = getSystemService(PowerManager::class.java)
        setStatus(batteryRow, pm.isIgnoringBatteryOptimizations(packageName))
        panelCaption.text = if (prefs.enabled) "PANEL (AKTIF)" else "PANEL (MATI)"
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
        if (!Settings.canDrawOverlays(this)) {
            toast("Izinkan tampil di atas aplikasi lain terlebih dahulu")
            openOverlaySettings()
            return
        }
        EdgeService.start(this)
        refreshStatus()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Opsi Pengembang")
            menu.add(0, 3, 1, "Tentang")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> openDeveloperOptions()
                    3 -> showAbout()
                }
                true
            }
        }.show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("EdgeLite")
            .setMessage("Versi 1.0.0\nPanel tepi alternatif untuk ponsel Samsung One UI.")
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
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
                EdgeService.refresh(this)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
