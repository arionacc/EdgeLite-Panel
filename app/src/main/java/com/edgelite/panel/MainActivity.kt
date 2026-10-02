package com.edgelite.panel

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var pinnedSummary: TextView
    private lateinit var enableSwitch: MaterialSwitch

    private val switchListener = CompoundButton.OnCheckedChangeListener { button, checked ->
        if (checked) {
            if (!Settings.canDrawOverlays(this)) {
                button.isChecked = false
                toast("Izinkan tampil di atas aplikasi lain terlebih dahulu")
                openOverlaySettings()
            } else {
                EdgeService.start(this)
            }
        } else {
            EdgeService.stop(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }

        fun add(v: View, top: Int = 0) {
            root.addView(
                v,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(top) }
            )
        }

        fun label(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = t
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        fun button(t: String, onClick: () -> Unit) = MaterialButton(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        add(label("EdgeLite Panel", 28f, true))
        add(
            label(
                "Panel tepi alternatif untuk ponsel Samsung One UI. Geser dari tepi layar untuk " +
                    "membuka aplikasi favorit dalam mode penuh, split screen, atau jendela.",
                14f
            ), 4
        )

        // 1. Izin
        add(label("1. Izin dan layanan", 18f, true), 24)
        overlayStatus = label("", 14f)
        add(overlayStatus, 8)
        add(button("Izin tampil di atas aplikasi lain") { openOverlaySettings() }, 4)

        a11yStatus = label("", 14f)
        add(a11yStatus, 12)
        add(button("Aktifkan layanan aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, 4)

        batteryStatus = label("", 14f)
        add(batteryStatus, 12)
        add(button("Abaikan optimasi baterai") { requestBatteryExemption() }, 4)

        // 2. Panel
        add(label("2. Panel", 18f, true), 28)
        enableSwitch = MaterialSwitch(this).apply {
            text = "Aktifkan panel tepi"
            isChecked = prefs.enabled
            setOnCheckedChangeListener(switchListener)
        }
        add(enableSwitch, 8)

        pinnedSummary = label("", 14f)
        add(pinnedSummary, 8)
        add(button("Pilih aplikasi panel") { pickApps() }, 4)

        add(label("Sisi layar", 14f, true), 16)
        add(
            radioGroup(listOf("Kanan", "Kiri"), if (prefs.side == EdgeSide.RIGHT) 0 else 1) {
                prefs.side = if (it == 0) EdgeSide.RIGHT else EdgeSide.LEFT
                EdgeService.refresh(this)
            }
        )

        add(label("Mode buka aplikasi default", 14f, true), 12)
        add(
            radioGroup(
                listOf("Layar penuh", "Split screen", "Jendela mengambang"),
                prefs.mode.ordinal
            ) { prefs.mode = LaunchMode.values()[it] }
        )

        add(slider("Tinggi handle", 60f, 240f, prefs.handleHeightDp.toFloat(), " dp") {
            prefs.handleHeightDp = it
        }, 16)
        add(slider("Posisi handle (dari atas)", 10f, 90f, prefs.handleOffsetPercent.toFloat(), " %") {
            prefs.handleOffsetPercent = it
        }, 8)
        add(slider("Ukuran jendela mengambang", 50f, 95f, prefs.windowSizePercent.toFloat(), " %") {
            prefs.windowSizePercent = it
        }, 8)

        // 3. Mode jendela
        add(label("3. Catatan mode jendela", 18f, true), 28)
        add(
            label(
                "Jendela mengambang memakai fitur freeform Android. Di One UI, aktifkan " +
                    "\"Aktifkan jendela freeform\" dan \"Paksa aktivitas dapat diubah ukurannya\" " +
                    "di Opsi Pengembang. Jika gagal, aplikasi akan dibuka layar penuh.",
                13f
            ), 8
        )
        add(button("Buka Opsi Pengembang") { openDeveloperOptions() }, 4)

        return ScrollView(this).apply { addView(root) }
    }

    private fun radioGroup(
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): RadioGroup {
        val group = RadioGroup(this)
        options.forEachIndexed { i, text ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                isChecked = i == selected
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            onSelect(g.indexOfChild(g.findViewById(checkedId)))
        }
        return group
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
        val caption = TextView(this).apply {
            textSize = 14f
            text = "$title: ${value.toInt()}$unit"
        }
        val s = Slider(this).apply {
            valueFrom = from
            valueTo = to
            stepSize = 1f
            this.value = value.coerceIn(from, to)
            addOnChangeListener { _, v, fromUser ->
                caption.text = "$title: ${v.toInt()}$unit"
                if (fromUser) onValue(v.toInt())
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    EdgeService.refresh(this@MainActivity)
                }
            })
        }
        box.addView(caption)
        box.addView(s)
        return box
    }

    // ------------------------------------------------------------------ Aksi

    private fun refreshStatus() {
        overlayStatus.text = if (Settings.canDrawOverlays(this)) {
            "✅ Izin overlay aktif"
        } else {
            "❌ Izin overlay belum aktif (wajib)"
        }
        a11yStatus.text = if (EdgeAccessibilityService.instance != null) {
            "✅ Layanan aksesibilitas aktif"
        } else {
            "⚪ Belum aktif (dibutuhkan untuk split screen otomatis)"
        }
        val pm = getSystemService(PowerManager::class.java)
        batteryStatus.text = if (pm.isIgnoringBatteryOptimizations(packageName)) {
            "✅ Optimasi baterai dikecualikan"
        } else {
            "⚪ Disarankan dikecualikan agar panel tidak dimatikan sistem"
        }
        pinnedSummary.text = "${prefs.pinned.size} aplikasi dipilih"

        enableSwitch.setOnCheckedChangeListener(null)
        enableSwitch.isChecked = prefs.enabled
        enableSwitch.setOnCheckedChangeListener(switchListener)
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
            toast("Opsi Pengembang belum aktif. Ketuk Nomor build 7 kali di Pengaturan > Tentang ponsel.")
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
                pinnedSummary.text = "${current.size} aplikasi dipilih"
                EdgeService.refresh(this)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
