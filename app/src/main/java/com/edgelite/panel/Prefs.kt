package com.edgelite.panel

import android.content.Context

enum class LaunchMode { FULL, WINDOW }
enum class EdgeSide { LEFT, RIGHT }

/** Orientasi layar. Pengaturan panel dan jendela disimpan terpisah untuk masing-masing. */
enum class Orient(val key: String) { PORTRAIT("p"), LANDSCAPE("l") }

fun orientOf(width: Int, height: Int): Orient =
    if (width > height) Orient.LANDSCAPE else Orient.PORTRAIT

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("edgelite", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    /** Mulai otomatis: panel yang aktif menyala lagi setelah ponsel restart atau aplikasi diperbarui. */
    var autostart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) { sp.edit().putBoolean("autostart", v).apply() }

    /** Nomor boot terakhir yang dilihat layanan. Dipakai untuk mengenali sambungan pertama setelah ponsel menyala. */
    var lastBoot: Int
        get() = sp.getInt("last_boot", -1)
        set(v) { sp.edit().putInt("last_boot", v).apply() }

    /** Mode buka default. Nilai lama (misalnya SPLIT) otomatis jatuh ke jendela mengambang. */
    var mode: LaunchMode
        get() = runCatching { LaunchMode.valueOf(sp.getString("mode", null) ?: "WINDOW") }
            .getOrDefault(LaunchMode.WINDOW)
        set(v) { sp.edit().putString("mode", v.name).apply() }

    /** Daftar paket aplikasi yang dipasang di panel, urutannya sesuai pilihan. */
    var pinned: List<String>
        get() = (sp.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) { sp.edit().putString("pinned", v.joinToString(",")).apply() }

    // ------------------------------------------------------------ Per orientasi
    // Nilai lama (sebelum dipisah) dipakai sebagai nilai awal supaya pengaturan tidak hilang.

    fun getSide(o: Orient): EdgeSide = runCatching {
        EdgeSide.valueOf(sp.getString("side_${o.key}", null) ?: sp.getString("side", null) ?: "RIGHT")
    }.getOrDefault(EdgeSide.RIGHT)

    fun setSide(o: Orient, v: EdgeSide) {
        sp.edit().putString("side_${o.key}", v.name).apply()
    }

    fun getHandleHeight(o: Orient): Int =
        sp.getInt("handle_h_${o.key}", sp.getInt("handle_h", 120))

    fun setHandleHeight(o: Orient, v: Int) {
        sp.edit().putInt("handle_h_${o.key}", v).apply()
    }

    fun getHandleOffset(o: Orient): Int =
        sp.getInt("handle_y_${o.key}", sp.getInt("handle_y", 40))

    fun setHandleOffset(o: Orient, v: Int) {
        sp.edit().putInt("handle_y_${o.key}", v).apply()
    }

    /** Lebar jendela mengambang, persen dari lebar layar pada orientasi tersebut. */
    fun getWinW(o: Orient): Int = sp.getInt(
        "win_w_${o.key}",
        if (o == Orient.PORTRAIT) sp.getInt("win_w", sp.getInt("win_size", 75)) else 45
    )

    fun setWinW(o: Orient, v: Int) {
        sp.edit().putInt("win_w_${o.key}", v).apply()
    }

    /** Tinggi jendela mengambang, persen dari tinggi layar pada orientasi tersebut. */
    fun getWinH(o: Orient): Int = sp.getInt(
        "win_h_${o.key}",
        if (o == Orient.PORTRAIT) sp.getInt("win_h", sp.getInt("win_size", 75)) else 80
    )

    fun setWinH(o: Orient, v: Int) {
        sp.edit().putInt("win_h_${o.key}", v).apply()
    }

    // ------------------------------------------------------------ Tampilan panel

    /** Tinggi panel tetap, persen dari tinggi layar pada orientasi tersebut. */
    fun getPanelH(o: Orient): Int =
        sp.getInt("panel_h_${o.key}", if (o == Orient.PORTRAIT) 65 else 85)

    fun setPanelH(o: Orient, v: Int) {
        sp.edit().putInt("panel_h_${o.key}", v).apply()
    }

    /** Lebar panel dalam dp. */
    fun getPanelW(o: Orient): Int = sp.getInt("panel_w_${o.key}", 88)

    fun setPanelW(o: Orient, v: Int) {
        sp.edit().putInt("panel_w_${o.key}", v).apply()
    }

    var iconSizeDp: Int
        get() = sp.getInt("icon_dp", 48)
        set(v) { sp.edit().putInt("icon_dp", v).apply() }

    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) { sp.edit().putBoolean("labels", v).apply() }

    /** Kepekatan latar panel, 100 berarti pekat penuh. */
    var panelOpacity: Int
        get() = sp.getInt("opacity", 95)
        set(v) { sp.edit().putInt("opacity", v).apply() }

    var cornerDp: Int
        get() = sp.getInt("corner_dp", 28)
        set(v) { sp.edit().putInt("corner_dp", v).apply() }
}
