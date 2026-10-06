package com.edgelite.panel

import android.content.Context
import org.json.JSONObject

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

    /** Aksi akses cepat yang tampil di atas panel, urutannya mengikuti [QuickAction]. Bawaan kosong. */
    var quickActions: List<QuickAction>
        get() = QuickAction.fromCodes(sp.getString("quick_actions", "") ?: "").filter { it.supported }
        set(v) { sp.edit().putString("quick_actions", v.joinToString(",") { it.code }).apply() }

    /** Aplikasi perekam layar pilihan pengguna. Kosong berarti dicari otomatis. */
    var recorderPkg: String
        get() = sp.getString("recorder_pkg", "") ?: ""
        set(v) { sp.edit().putString("recorder_pkg", v).apply() }

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

    /**
     * Bentuk jendela per aplikasi yang dipilih pengguna. Berguna untuk aplikasi yang mengunci
     * orientasi lewat kode, sehingga tidak terbaca dari manifes. AUTO tidak disimpan.
     */
    fun getAppShape(pkg: String): WinShape = appShapes[pkg] ?: WinShape.AUTO

    fun setAppShape(pkg: String, shape: WinShape) {
        val m = appShapes.toMutableMap()
        if (shape == WinShape.AUTO) m.remove(pkg) else m[pkg] = shape
        sp.edit().putString("app_shapes", m.entries.joinToString(",") { "${it.key}=${it.value.code}" }).apply()
    }

    private val appShapes: Map<String, WinShape>
        get() = (sp.getString("app_shapes", "") ?: "").split(",")
            .mapNotNull { e ->
                val i = e.lastIndexOf('=')
                if (i <= 0) return@mapNotNull null
                val s = WinShape.values().firstOrNull { it.code == e.substring(i + 1) && it != WinShape.AUTO }
                    ?: return@mapNotNull null
                e.substring(0, i) to s
            }.toMap()

    /**
     * Penyesuaian jendela dengan bentuk aplikasi (potret saja, tidak bisa diubah ukuran, rasio aspek).
     * Bila mati, angka lebar dan tinggi dipakai apa adanya.
     */
    var winFit: Boolean
        get() = sp.getBoolean("win_fit", true)
        set(v) { sp.edit().putBoolean("win_fit", v).apply() }

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

    // ------------------------------------------------------------ Ekspor dan impor

    /** Seluruh pengaturan sebagai JSON, memakai nilai efektif (termasuk bawaan) agar berkas lengkap. */
    fun exportJson(): String {
        val d = JSONObject()
        d.put("enabled", enabled)
        d.put("autostart", autostart)
        d.put("mode", mode.name)
        d.put("pinned", pinned.joinToString(","))
        d.put("icon_dp", iconSizeDp)
        d.put("labels", showLabels)
        d.put("win_fit", winFit)
        d.put("quick_actions", sp.getString("quick_actions", "") ?: "")
        d.put("app_shapes", sp.getString("app_shapes", "") ?: "")
        d.put("recorder_pkg", recorderPkg)
        d.put("opacity", panelOpacity)
        d.put("corner_dp", cornerDp)
        for (o in Orient.values()) {
            val k = o.key
            d.put("side_$k", getSide(o).name)
            d.put("handle_h_$k", getHandleHeight(o))
            d.put("handle_y_$k", getHandleOffset(o))
            d.put("win_w_$k", getWinW(o))
            d.put("win_h_$k", getWinH(o))
            d.put("panel_h_$k", getPanelH(o))
            d.put("panel_w_$k", getPanelW(o))
        }
        return JSONObject()
            .put("app", "EdgeLite")
            .put("format", 1)
            .put("settings", d)
            .toString(2)
    }

    /**
     * Memuat pengaturan dari JSON hasil [exportJson]. Hanya kunci yang dikenal yang diterima,
     * dan angka dibatasi ke rentang slider. Mengembalikan false bila berkas tidak sesuai.
     */
    fun importJson(text: String): Boolean {
        val root = JSONObject(text)
        if (root.optString("app") != "EdgeLite") return false
        val data = root.optJSONObject("settings") ?: return false
        val ed = sp.edit()
        var count = 0
        data.keys().forEach { k ->
            when (val v = clean(k, data.opt(k))) {
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is String -> ed.putString(k, v)
                else -> return@forEach
            }
            count++
        }
        if (count == 0) return false
        ed.apply()
        return true
    }

    private fun clean(key: String, v: Any?): Any? {
        fun int(lo: Int, hi: Int) = (v as? Number)?.toInt()?.coerceIn(lo, hi)
        val perOrient = key.endsWith("_p") || key.endsWith("_l")
        if (!perOrient) {
            return when (key) {
                "enabled", "autostart", "labels", "win_fit" -> v as? Boolean
                "mode" -> (v as? String)?.takeIf { it == "WINDOW" || it == "FULL" }
                "pinned" -> (v as? String)?.takeIf { Regex("[A-Za-z0-9_.,]*").matches(it) }
                "quick_actions" -> (v as? String)?.takeIf { Regex("([a-z](,[a-z])*)?").matches(it) }
                "app_shapes" -> (v as? String)?.takeIf {
                    it.isEmpty() || Regex("[A-Za-z0-9_.]+=[PLF](,[A-Za-z0-9_.]+=[PLF])*").matches(it)
                }
                "recorder_pkg" -> (v as? String)?.takeIf { Regex("[A-Za-z0-9_.]*").matches(it) }
                "icon_dp" -> int(32, 64)
                "opacity" -> int(50, 100)
                "corner_dp" -> int(0, 40)
                else -> null
            }
        }
        return when (key.dropLast(2)) {
            "side" -> (v as? String)?.takeIf { it == "LEFT" || it == "RIGHT" }
            "handle_h" -> int(60, 240)
            "handle_y" -> int(10, 90)
            "win_w", "win_h" -> int(30, 100)
            "panel_h" -> int(30, 95)
            "panel_w" -> int(64, 140)
            else -> null
        }
    }
}

/** Pilihan bentuk jendela per aplikasi. */
enum class WinShape(val code: String) {
    AUTO("A"), PORTRAIT("P"), LANDSCAPE("L"), FREE("F")
}
