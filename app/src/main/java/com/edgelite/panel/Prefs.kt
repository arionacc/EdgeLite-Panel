package com.edgelite.panel

import android.content.Context

enum class LaunchMode { FULL, SPLIT, WINDOW }
enum class EdgeSide { LEFT, RIGHT }

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("edgelite", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var side: EdgeSide
        get() = runCatching { EdgeSide.valueOf(sp.getString("side", null) ?: "RIGHT") }
            .getOrDefault(EdgeSide.RIGHT)
        set(v) { sp.edit().putString("side", v.name).apply() }

    var mode: LaunchMode
        get() = runCatching { LaunchMode.valueOf(sp.getString("mode", null) ?: "FULL") }
            .getOrDefault(LaunchMode.FULL)
        set(v) { sp.edit().putString("mode", v.name).apply() }

    var handleHeightDp: Int
        get() = sp.getInt("handle_h", 120)
        set(v) { sp.edit().putInt("handle_h", v).apply() }

    var handleOffsetPercent: Int
        get() = sp.getInt("handle_y", 40)
        set(v) { sp.edit().putInt("handle_y", v).apply() }

    var windowSizePercent: Int
        get() = sp.getInt("win_size", 75)
        set(v) { sp.edit().putInt("win_size", v).apply() }

    /** Daftar paket aplikasi yang dipasang di panel, urutannya sesuai pilihan. */
    var pinned: List<String>
        get() = (sp.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) { sp.edit().putString("pinned", v.joinToString(",")).apply() }
}
